package com.finai.app.data.repository

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.assistant.AssistantActionExecutor
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.assistant.*
import com.finai.app.domain.toEpochMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** O recibo deve restaurar uma divisão inteira e a repetição não pode criar outras parcelas. */
class AssistantIncomeActionsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.of(2026, 11, 14)
    private val due = LocalDate.of(2026, 12, 20).toEpochMillis()
    private fun open(name: String) = Room.databaseBuilder(context, FinaiDatabase::class.java, name).addMigrations(*FinaiDatabase.MIGRATIONS).build()
    private fun request() = ActionRequest(ActionPlanner.SPLIT_INCOME, mapOf("texto" to "decimo", "primeira_data" to "30/11"))
    private fun snapshot(tx: List<TransacaoEntity> = emptyList(), bills: List<ContaEntity> = emptyList()) = AssistantSnapshot(bills, tx, emptyList(), 30_000, today)

    @Test fun transactionSplitSurvivesReopenIsIdempotentAndUndoRestoresOriginal() = runBlocking {
        val name = "assistant-income-tx-test.db"
        context.deleteDatabase(name)
        var db = open(name)
        try {
            val row = TransacaoEntity(data = due, descricao = "Décimo terceiro", valorCentavos = 230_001, categoria = "Receita", contaOrigem = "Itaú", recorrente = false, tipo = "Receita", origem = "manual", extra = true)
            val id = db.transacaoDao().upsert(row)
            val original = row.copy(id = id)
            val plan = ActionPlanner.plan(request(), snapshot(listOf(original)), confirmed = true)
            assertEquals(PlanState.Ready, plan.state)
            val executor = AssistantActionExecutor(db, FinanceRepository(db))
            val result = executor.apply(plan, "split:1", 1)
            assertEquals(ActionStatus.Applied, result.status)
            assertEquals(ActionStatus.AlreadyApplied, executor.apply(plan, "split:1", 1).status)
            db.close()
            db = open(name)
            val rows = db.transacaoDao().getAll()
            assertEquals(2, rows.size)
            assertEquals(original.valorCentavos, rows.sumOf { it.valorCentavos })
            assertTrue(rows.any { it.id == id })
            val undone = AssistantActionExecutor(db, FinanceRepository(db)).undo(1, result.actionId)
            assertEquals(ActionStatus.Undone, undone.status)
            assertEquals(listOf(original), db.transacaoDao().getAll())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun receivableSplitAndUndoPreserveOtherBillsAndRejectAStaleSource() = runBlocking {
        val name = "assistant-income-bill-test.db"
        context.deleteDatabase(name)
        val db = open(name)
        try {
            val row = ContaEntity(nome = "13º salário", valorCentavos = 115_001, vencimento = due, status = "pendente", tipo = "a_receber", recorrente = false)
            val original = row.copy(id = db.contaDao().upsert(row))
            val other = row.copy(id = 0, nome = "Conta de luz", tipo = "a_pagar")
            val untouched = other.copy(id = db.contaDao().upsert(other))
            val plan = ActionPlanner.plan(request(), snapshot(bills = listOf(original, untouched)), confirmed = true)
            val executor = AssistantActionExecutor(db, FinanceRepository(db))
            db.contaDao().upsert(original.copy(valorCentavos = 100_000))
            assertEquals(ActionStatus.Conflict, executor.apply(plan, "stale", 1).status)
            assertEquals(2, db.contaDao().getAll().size)
            db.contaDao().upsert(original)
            val result = executor.apply(plan, "split:2", 1)
            assertEquals(ActionStatus.Applied, result.status)
            assertEquals(3, db.contaDao().getAll().size)
            assertEquals(ActionStatus.Undone, executor.undo(1, result.actionId).status)
            assertEquals(setOf(original, untouched), db.contaDao().getAll().toSet())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
