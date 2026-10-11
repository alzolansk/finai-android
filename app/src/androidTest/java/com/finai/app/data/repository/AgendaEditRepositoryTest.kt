package com.finai.app.data.repository

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.EditScope
import com.finai.app.domain.EntryChange
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.transactionsInMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Edição pela Agenda com o Room de verdade: recorrente "somente este mês" e "deste mês em
 * diante", avulso, item de fatura e parcela de dívida, conferidos depois de fechar e reabrir o
 * banco (o que o app faz ao ser fechado e aberto de novo).
 */
class AgendaEditRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(name: String) = Room.databaseBuilder(context, FinaiDatabase::class.java, name).addMigrations(*FinaiDatabase.MIGRATIONS).build()
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    private fun internet() = TransacaoEntity(
        data = LocalDate.of(2026, 1, 10).toEpochMillis(), descricao = "Internet", valorCentavos = 120_00,
        categoria = "Serviços", contaOrigem = "Nubank", recorrente = true, origem = "manual",
    )

    @Test fun recurringEditsSurviveReopenWithoutDuplicates() = runBlocking {
        val name = "agenda-edit-test.db"
        context.deleteDatabase(name)
        var db = open(name)
        try {
            val repo = FinanceRepository(db)
            val id = repo.salvarTransacao(internet())
            repo.editarLancamento(id, ym(2026, 11), EntryChange(LocalDate.of(2026, 11, 10), 135_00, "Internet", "Serviços", "Nubank"), EditScope.OnlyThis)
            repo.editarLancamento(id, ym(2027, 1), EntryChange(LocalDate.of(2027, 1, 10), 140_00, "Internet", "Serviços", "Nubank"), EditScope.ThisAndFollowing)
            db.close()

            db = open(name)
            val all = db.transacaoDao().getAll()
            fun month(y: Int, m: Int) = all.transactionsInMonth(LocalDate.of(y, m, 1))
            assertEquals(2, all.size) // série original (até dez/26) e o trecho novo
            assertEquals(120_00L, month(2026, 10).single().valorCentavos)
            assertEquals(135_00L, month(2026, 11).single().valorCentavos)
            assertEquals(120_00L, month(2026, 12).single().valorCentavos)
            assertEquals(140_00L, month(2027, 1).single().valorCentavos)
            assertEquals(140_00L, month(2027, 9).single().valorCentavos)
            (0L..30L).forEach { i -> val m = ym(2026, 1).plusMonths(i); assertEquals("uma cobrança em $m", 1, month(m.year, m.monthValue).size) }
            // Totais do mês acompanham.
            assertEquals(-135_00L, MonthCashFlow.of(emptyList(), all, emptyList(), ym(2026, 11), LocalDate.of(2026, 11, 1)).saldoCents)
            assertEquals(-140_00L, MonthCashFlow.of(emptyList(), all, emptyList(), ym(2027, 2), LocalDate.of(2027, 2, 1)).saldoCents)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun singleAndInvoiceItemEdits() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinaiDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db)
            val mercado = repo.salvarTransacao(TransacaoEntity(data = LocalDate.of(2026, 10, 3).toEpochMillis(), descricao = "Mercado",
                valorCentavos = 80_00, categoria = "Alimentação", contaOrigem = "Itaú", recorrente = false, origem = "manual"))
            repo.editarLancamento(mercado, ym(2026, 10), EntryChange(LocalDate.of(2026, 10, 4), 89_90, "Mercado SJ", "Alimentação", "Itaú"), null)
            val edited = db.transacaoDao().getAll().single { it.id == mercado }
            assertEquals(89_90L, edited.valorCentavos); assertEquals(LocalDate.of(2026, 10, 4), edited.data.toLocalDate())

            val fatura = repo.salvarFatura(
                ContaEntity(nome = "Fatura Nubank", valorCentavos = 300_00, vencimento = LocalDate.of(2026, 11, 10).toEpochMillis(),
                    status = "pendente", tipo = "a_pagar", recorrente = false),
                FaturaCartaoEntity(contaId = 0, referencia = "Nubank", fechamento = null, vencimento = LocalDate.of(2026, 11, 10).toEpochMillis()),
                listOf(
                    TransacaoEntity(data = LocalDate.of(2026, 10, 5).toEpochMillis(), descricao = "Loja", valorCentavos = 100_00,
                        categoria = "Compras", contaOrigem = "Nubank", recorrente = false, origem = "importado"),
                    TransacaoEntity(data = LocalDate.of(2026, 10, 6).toEpochMillis(), descricao = "Posto", valorCentavos = 200_00,
                        categoria = "Transporte", contaOrigem = "Nubank", recorrente = false, origem = "importado"),
                ),
            )
            val item = db.transacaoDao().getAll().first { it.descricao == "Loja" }
            repo.editarLancamento(item.id, ym(2026, 10), EntryChange(LocalDate.of(2026, 10, 5), 125_00, "Loja", "Compras", "Itaú"), null)
            val conta = db.contaDao().getAll().single { it.id == fatura.contaId }
            assertEquals(325_00L, conta.valorCentavos)
            assertEquals("Nubank", db.transacaoDao().getAll().single { it.id == item.id }.contaOrigem)
            // Fatura paga: o valor não muda mais por aqui, e nada é gravado.
            db.contaDao().update(conta.copy(status = "pago"))
            try {
                repo.editarLancamento(item.id, ym(2026, 10), EntryChange(LocalDate.of(2026, 10, 5), 1_00, "Loja", "Compras", "Nubank"), null)
                fail("fatura paga")
            } catch (_: IllegalArgumentException) { }
            assertEquals(125_00L, db.transacaoDao().getAll().single { it.id == item.id }.valorCentavos)
        } finally { db.close() }
    }

    @Test fun installmentDateEditSurvivesReopen() = runBlocking {
        val name = "agenda-parcela-test.db"
        context.deleteDatabase(name)
        val today = LocalDate.of(2026, 4, 1)
        var db = open(name)
        try {
            val repo = FinanceRepository(db)
            val id = repo.salvarDivida(DividaEntity(nome = "Empréstimo", valorOriginalCentavos = 6_000_00, valorAbertoCentavos = 3_000_00,
                taxaJurosMensalBasisPoints = 200, parcelasRestantes = 6, valorParcelaCentavos = 500_00, parcelasTotais = 12,
                proximoVencimento = LocalDate.of(2026, 4, 30).toEpochMillis(), diaVencimento = 30))
            val d = db.dividaDao().getAll().single { it.id == id }
            repo.salvarDivida(DebtSchedule.withInstallmentDate(d, ym(2026, 5), LocalDate.of(2026, 5, 31), today))
            db.close()

            db = open(name)
            val reopened = db.dividaDao().getAll().single()
            fun due(m: Int) = DebtSchedule.installmentInMonth(reopened, ym(2026, m), today)!!.vencimento
            assertEquals(LocalDate.of(2026, 4, 30), due(4))
            assertEquals(LocalDate.of(2026, 5, 31), due(5))
            assertEquals(LocalDate.of(2026, 6, 30), due(6))
            assertEquals(30, reopened.diaVencimento)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
