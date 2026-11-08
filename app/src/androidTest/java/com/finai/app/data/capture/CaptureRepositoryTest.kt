package com.finai.app.data.capture

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.TesteNotificacaoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.capture.CapturedNotification
import com.finai.app.domain.capture.NOTIFICATION_ORIGIN
import com.finai.app.domain.capture.NotificationParser
import com.finai.app.domain.capture.ParseOutcome
import com.finai.app.domain.capture.SuggestionOrigin
import com.finai.app.domain.capture.SuggestionRules
import com.finai.app.domain.capture.SuggestionState
import com.finai.app.domain.toEpochMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sugestões no Room de verdade: sobrevivem a fechar o banco, a confirmação grava o lançamento e
 * marca a sugestão juntos, dois toques não gravam duas vezes, falha deixa tudo como estava, teste
 * nunca toca nas finanças, e "Apagar todos os dados" / "Limpar testes" limpam só o que devem.
 */
class CaptureRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "capture-repository-test.db"
    private lateinit var db: FinaiDatabase
    private val day = LocalDate.now()
    private val posted = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before fun open() { context.deleteDatabase(name); db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build() }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    private fun notification(text: String, key: String = "0|com.banco|1|null|10", title: String = "Banco") =
        CapturedNotification("com.banco", "Banco", key, posted, title, text)

    private suspend fun capture(repo: CaptureRepository, text: String, key: String = "0|com.banco|1|null|10",
                                origin: SuggestionOrigin = SuggestionOrigin.Notification): List<CaptureRepository.Registered> {
        val n = notification(text, key)
        val movements = NotificationParser.parse(n).filterIsInstance<ParseOutcome.Movement>()
        return repo.register(n, movements, origin, testId = null, now = posted)
    }

    @Test fun suggestionSurvivesReopeningTheDatabase_withLimitedText() = runBlocking {
        val id = (capture(CaptureRepository(db), "Compra no débito de R$ 18,50 aprovada na Padaria.").single() as CaptureRepository.Registered.Created).id
        db.close()
        db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
        val s = requireNotNull(CaptureRepository(db).get(id))
        assertEquals(SuggestionState.Pending.key, s.estado)
        assertEquals(1850L, s.valorCentavos)
        assertEquals("Padaria", s.descricao)
        assertTrue(s.textoOriginal!!.length <= CaptureRepository.MAX_TEXT_CHARS)
        // Retenção: passado o prazo, o texto some e o resto fica.
        CaptureRepository(db).purge(posted + (CaptureRepository.TEXT_RETENTION_DAYS + 1) * 86_400_000L)
        val after = requireNotNull(CaptureRepository(db).get(id))
        assertNull(after.textoOriginal)
        assertEquals(1850L, after.valorCentavos)
    }

    @Test fun repostAndUpdateNeverCreateAnotherSuggestion_ignoredStaysIgnored() = runBlocking {
        val repo = CaptureRepository(db)
        val text = "Compra no crédito de R$ 42,90 aprovada no Mercado."
        val id = (capture(repo, text).single() as CaptureRepository.Registered.Created).id
        assertTrue(capture(repo, text).single() is CaptureRepository.Registered.Repeated)
        assertTrue(capture(repo, "$text Limite disponível: R$ 1.257,10.").single() is CaptureRepository.Registered.Updated)
        assertEquals(4290L, repo.get(id)!!.valorCentavos)
        repo.ignore(id)
        val again = capture(repo, "$text Limite disponível: R$ 1.000,00.").single()
        assertEquals(CaptureRepository.Registered.Repeated(id, resolved = true), again)
        assertEquals(SuggestionState.Ignored.key, repo.get(id)!!.estado)
        assertEquals(1, repo.suggestions.first().size)
    }

    @Test fun debitConfirmationIsAtomicAndIdempotent() = runBlocking {
        val repo = CaptureRepository(db)
        val id = (capture(repo, "Compra no débito de R$ 18,50 aprovada na Padaria.").single() as CaptureRepository.Registered.Created).id
        val draft = SuggestionRules.draftOf(repo.get(id)!!.toMovement()).copy(account = "Itaú", category = "Alimentação")
        // Dois toques ao mesmo tempo (ou duas telas): um grava, o outro recebe "já foi salva".
        val results = listOf(async { runCatching { repo.confirm(id, draft, null) } }, async { runCatching { repo.confirm(id, draft, null) } }).awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is SuggestionUnavailableException)
        val saved = db.transacaoDao().getAll()
        assertEquals(1, saved.size)
        assertEquals(NOTIFICATION_ORIGIN, saved.single().origem)
        assertEquals("Itaú", saved.single().contaOrigem)
        val s = repo.get(id)!!
        assertEquals(SuggestionState.Confirmed.key, s.estado)
        assertEquals(saved.single().id.toString(), s.lancamentoIds)
        assertNull(s.textoOriginal)
        // Desfazer tira o lançamento e devolve a sugestão para "Para confirmar".
        repo.undo(id, results.single { it.isSuccess }.getOrThrow())
        assertTrue(db.transacaoDao().getAll().isEmpty())
        assertEquals(SuggestionState.Pending.key, repo.get(id)!!.estado)
    }

    @Test fun creditWithInstallmentsGoesThroughTheInvoiceRule() = runBlocking {
        val finance = FinanceRepository(db)
        val repo = CaptureRepository(db, finance)
        val id = (capture(repo, "Compra no crédito aprovada na Loja Eletro: total de R$ 300,00 em 3 parcelas de R$ 100,00.").single()
            as CaptureRepository.Registered.Created).id
        val due = day.plusMonths(1).withDayOfMonth(10)
        val draft = SuggestionRules.draftOf(repo.get(id)!!.toMovement()).copy(card = "Nubank", category = "Compras", dueDate = due, dueConfirmed = true)
        val cardDue = EntryOptions.resolveCardDue("Nubank", draft.date, emptyList(), emptyList())
        val receipt = repo.confirm(id, draft, cardDue)
        assertEquals(3, receipt.transactionIds.size)
        val items = db.transacaoDao().getAll()
        assertEquals(listOf(10000L, 10000L, 10000L), items.sortedBy { it.parcelaNumero }.map { it.valorCentavos })
        assertTrue("data da compra, não o vencimento", items.all { it.data == day.toEpochMillis() })
        assertEquals(3, db.faturaCartaoDao().getAll().size)
        assertTrue(items.all { it.faturaId != null && it.contaOrigem == "Nubank" })
    }

    @Test fun failedSaveLeavesTheSuggestionPendingAndNothingWritten() = runBlocking {
        val finance = FinanceRepository(db)
        val repo = CaptureRepository(db, finance)
        val due = day.plusMonths(1).withDayOfMonth(10)
        // Fatura já paga nesse vencimento: salvarCompraCartao recusa.
        finance.salvarFatura(
            ContaEntity(nome = "Fatura Nubank", valorCentavos = 500, vencimento = due.toEpochMillis(), status = "pago", tipo = "a_pagar", recorrente = false),
            FaturaCartaoEntity(contaId = 0, referencia = "Nubank", fechamento = null, vencimento = due.toEpochMillis()),
            listOf(TransacaoEntity(data = day.toEpochMillis(), descricao = "x", valorCentavos = 500, categoria = "Compras", contaOrigem = "Nubank", recorrente = false, origem = "manual")),
        )
        val before = db.transacaoDao().getAll().size
        val id = (capture(repo, "Compra no crédito de R$ 42,90 aprovada no Mercado.").single() as CaptureRepository.Registered.Created).id
        val draft = SuggestionRules.draftOf(repo.get(id)!!.toMovement()).copy(card = "Nubank", category = "Alimentação", dueDate = due, dueConfirmed = true)
        try { repo.confirm(id, draft, null); fail("fatura paga deve recusar") } catch (_: IllegalArgumentException) { }
        assertEquals(before, db.transacaoDao().getAll().size)
        assertEquals(SuggestionState.Pending.key, repo.get(id)!!.estado)
    }

    @Test fun testSuggestionConfirmsWithoutTouchingFinances_andClearTestsKeepsRealOnes() = runBlocking {
        val repo = CaptureRepository(db)
        val real = (capture(repo, "Compra no débito de R$ 18,50 aprovada na Padaria.").single() as CaptureRepository.Registered.Created).id
        val testRun = repo.insertTest(TesteNotificacaoEntity(exemplo = 2, etapa = "Envio", modo = "notificacao", titulo = "t", texto = "x",
            notificacaoId = 60001, status = TestStatus.Waiting.name, detalhe = "", sugestaoId = null, criadoEm = posted, atualizadoEm = posted))
        val test = (capture(repo, "Compra no débito de R$ 9,90 aprovada na Cafeteria.", key = "0|com.finai.app|60001|null|10",
            origin = SuggestionOrigin.TestNotification).single() as CaptureRepository.Registered.Created).id
        val draft = SuggestionRules.draftOf(repo.get(test)!!.toMovement()).copy(account = "Itaú", category = "Alimentação")
        val receipt = repo.confirm(test, draft, null)
        assertTrue(receipt.transactionIds.isEmpty())
        assertTrue("teste não grava lançamento", db.transacaoDao().getAll().isEmpty())
        assertEquals(SuggestionState.Confirmed.key, repo.get(test)!!.estado)
        repo.clearTests()
        assertNull(repo.get(test))
        assertNull(repo.testRun(testRun))
        assertEquals(SuggestionState.Pending.key, repo.get(real)!!.estado)
        // Backup: só a real, sem texto.
        val backup = FinanceRepository(db).exportar(15, 0)
        assertEquals(listOf(real), backup.sugestoes.map { it.id })
        assertNull(backup.sugestoes.single().textoOriginal)
        // Apagar todos os dados limpa sugestões e testes.
        FinanceRepository(db).apagarTodosOsDados()
        assertTrue(repo.suggestions.first().isEmpty())
        assertTrue(repo.testRuns.first().isEmpty())
    }
}
