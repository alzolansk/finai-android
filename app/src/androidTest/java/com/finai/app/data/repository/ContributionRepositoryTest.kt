package com.finai.app.data.repository

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FINAI_DB_VERSION
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CardPayment
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.toEpochMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Aporte e desfazer com o Room de verdade: transferência e objetivo andam juntos, ou nada muda. */
class ContributionRepositoryTest {
    private val today = LocalDate.now()
    private fun aporte(goalId: Long, cents: Long) = TransacaoEntity(data = today.toEpochMillis(), descricao = "Aporte",
        valorCentavos = cents, categoria = "Transferência", contaOrigem = "Itaú", recorrente = false, origem = "manual",
        tipo = "Transferencia", objetivoId = goalId)

    @Test fun contributionAndUndoAreAtomicAndSurviveReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "contribution-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
        try {
            val repository = FinanceRepository(db)
            val goalId = repository.salvarObjetivo(ObjetivoEntity(tipo = "Reserva", nome = "Reserva de emergência", valorAlvoCentavos = 20_000_00,
                valorGuardadoCentavos = 12_400_00, prazo = today.plusYears(1).toEpochMillis(), prioridade = 1))
            val receipt = repository.registrarAporte(aporte(goalId, 500_00))
            assertEquals(12_400_00L, receipt.goal!!.beforeCents)
            assertEquals(12_900_00L, receipt.goal!!.afterCents)
            db.close()
            db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
            val reopened = FinanceRepository(db)
            val t = db.transacaoDao().getAll().single()
            assertEquals(goalId, t.objetivoId); assertEquals("Reserva de emergência", t.contaDestino)
            assertEquals(12_900_00L, db.objetivoDao().getAll().single().valorGuardadoCentavos)
            // Sai do livre do mês uma vez; não é gasto.
            assertEquals(-500_00L, MonthCashFlow.of(emptyList(), listOf(t), emptyList(), YearMonth.from(today), today).saldoCents)
            reopened.desfazer(receipt)
            assertTrue(db.transacaoDao().getAll().isEmpty())
            assertEquals(12_400_00L, db.objetivoDao().getAll().single().valorGuardadoCentavos)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun contributionToDeletedGoalWritesNothing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FinaiDatabase::class.java).build()
        try {
            val repository = FinanceRepository(db)
            val before = repository.exportar(FINAI_DB_VERSION, 0)
            try { repository.registrarAporte(aporte(42, 100_00)); fail("objetivo inexistente") } catch (_: IllegalArgumentException) { }
            assertEquals(before, repository.exportar(FINAI_DB_VERSION, 0))
        } finally { db.close() }
    }

    @Test fun undoingACardPurchaseRemovesItsInvoicesAndPaidInvoiceBlocksIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FinaiDatabase::class.java).build()
        try {
            val repository = FinanceRepository(db)
            val entry = TransacaoEntity(data = today.toEpochMillis(), descricao = "Cadeira", valorCentavos = 1_000_00,
                categoria = "Compras", contaOrigem = "Nubank", recorrente = false, origem = "manual")
            val due = today.plusDays(10)
            val receipt = repository.salvarCompraCartao(entry, CardPayment("Nubank", due, 4))
            assertEquals(4, receipt.createdInvoiceIds.size)
            val paid = db.contaDao().getAll().first()
            repository.salvarConta(paid.copy(status = "pago"))
            val snapshot = repository.exportar(FINAI_DB_VERSION, 0)
            try { repository.desfazer(receipt); fail("fatura paga bloqueia o desfazer") } catch (_: IllegalArgumentException) { }
            assertEquals(snapshot, repository.exportar(FINAI_DB_VERSION, 0))
            repository.salvarConta(paid.copy(status = "pendente"))
            repository.desfazer(receipt)
            assertTrue(db.transacaoDao().getAll().isEmpty())
            assertTrue(db.faturaCartaoDao().getAll().isEmpty())
            assertTrue(db.contaDao().getAll().isEmpty())
        } finally { db.close() }
    }
}
