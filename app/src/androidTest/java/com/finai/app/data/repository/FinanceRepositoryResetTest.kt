package com.finai.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.MensagemChatEntity
import com.finai.app.data.local.entity.NotificacaoEnviadaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Apagar todos os dados" das Configurações — o pedido explícito era "deixar
 * o app no mesmo estado de uma instalação nova". Este teste popula todas as
 * tabelas que guardam dado financeiro/derivado do usuário, chama
 * [FinanceRepository.apagarTodosOsDados] e confere que cada uma volta a
 * ficar vazia, na mesma transação.
 *
 * Roda em aparelho/emulador: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class FinanceRepositoryResetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "finance-repository-reset-test.db"
    private lateinit var db: FinaiDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        db = Room.databaseBuilder(context, FinaiDatabase::class.java, dbName).build()
        repository = FinanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun apagarTodosOsDadosEsvaziaTodasAsTabelas() = runBlocking {
        repository.salvarTransacao(
            TransacaoEntity(data = 0L, descricao = "Mercado", valorCentavos = 1_000, categoria = "Alimentação", contaOrigem = "Carteira", recorrente = false, origem = "manual"),
        )
        repository.salvarConta(
            ContaEntity(nome = "Aluguel", valorCentavos = 100_000, vencimento = 0L, status = "pendente", tipo = "a_pagar", recorrente = true),
        )
        repository.salvarObjetivo(
            ObjetivoEntity(tipo = "Viagem", nome = "Praia", valorAlvoCentavos = 500_000, valorGuardadoCentavos = 10_000, prazo = 0L, prioridade = 1),
        )
        repository.salvarDivida(
            DividaEntity(nome = "Cartão", valorOriginalCentavos = 200_000, valorAbertoCentavos = 100_000, taxaJurosMensalBasisPoints = 500, parcelasRestantes = 5, valorParcelaCentavos = 20_000),
        )
        repository.salvarLimiteOrcamento(OrcamentoCategoriaEntity("Alimentação", 100_000, "2026-03"))
        repository.salvarAssinatura(AssinaturaEntity(nome = "Streaming", valorCentavos = 2_000, ultimoUso = 0L, status = "ativa"))
        db.mensagemChatDao().insert(MensagemChatEntity(papel = "usuario", texto = "Oi", timestamp = 0L))
        db.notificacaoEnviadaDao().upsert(NotificacaoEnviadaEntity("evento-teste", "2026-03-21"))

        assertTrue(repository.transacoes.first().isNotEmpty())
        assertTrue(repository.contas.first().isNotEmpty())
        assertTrue(repository.objetivos.first().isNotEmpty())
        assertTrue(repository.dividas.first().isNotEmpty())
        assertTrue(repository.assinaturas.first().isNotEmpty())
        assertTrue(db.mensagemChatDao().observeAll().first().isNotEmpty())
        assertTrue(db.notificacaoEnviadaDao().find("evento-teste") != null)

        repository.apagarTodosOsDados()

        assertTrue(repository.transacoes.first().isEmpty())
        assertTrue(repository.contas.first().isEmpty())
        assertTrue(repository.objetivos.first().isEmpty())
        assertTrue(repository.dividas.first().isEmpty())
        assertTrue(repository.orcamentosDoMes("2026-03").first().isEmpty())
        assertTrue(repository.assinaturas.first().isEmpty())
        assertTrue(db.mensagemChatDao().observeAll().first().isEmpty())
        assertTrue(db.notificacaoEnviadaDao().find("evento-teste") == null)
    }
}
