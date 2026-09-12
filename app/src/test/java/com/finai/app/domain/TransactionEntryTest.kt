package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TransactionEntryTest {
    @Test fun centsSequenceAndErase() {
        var cents = TransactionEntry.key("", "4")
        assertEquals("4", cents)
        cents = TransactionEntry.key(cents, "3")
        assertEquals("43", cents)
        cents = TransactionEntry.key(cents, "00")
        assertEquals("4300", cents)
        cents = TransactionEntry.key(cents, "erase")
        assertEquals("430", cents)
        repeat(3) { cents = TransactionEntry.key(cents, "erase") }
        assertEquals("", cents)
        assertEquals("", TransactionEntry.key(cents, "erase"))
    }
    @Test fun zerosLimitAndValidation() {
        assertEquals("", TransactionEntry.key("", "00"))
        assertEquals("123456789", TransactionEntry.key("123456789", "1"))
        assertEquals("12345678", TransactionEntry.key("12345678", "00"))
        assertFalse(TransactionEntry.canSave("", "Alimentação"))
        assertFalse(TransactionEntry.canSave("0", "Alimentação"))
        assertFalse(TransactionEntry.canSave("4300", ""))
        assertTrue(TransactionEntry.canSave("4300", "Alimentação"))
    }
    private val day = LocalDate.of(2026, 1, 31)
    private fun entry(type: String, recurring: Boolean = false) = TransacaoEntity(
        id = 7, data = day.toEpochMillis(), descricao = "Teste", valorCentavos = 4300,
        categoria = "Alimentação", contaOrigem = "Carteira", recorrente = recurring, origem = "manual", tipo = type)

    @Test fun incomeAndTransferDoNotBecomeExpenses() {
        val income = entry("Receita")
        val transfer = entry("Transferencia")
        val expense = entry("Gasto")
        assertEquals(4300L, SafeToSpendCalculator.calculate(emptyList(), listOf(income), 0, day).slackThisMonthCents)
        assertEquals(0L, SafeToSpendCalculator.calculate(emptyList(), listOf(transfer), 0, day).slackThisMonthCents)
        assertEquals(-4300L, SafeToSpendCalculator.calculate(emptyList(), listOf(expense), 0, day).slackThisMonthCents)
        val budgets = BudgetCalculator.forCategories(listOf(OrcamentoCategoriaEntity("Alimentação", 10000, "2026-01")),
            listOf(income, transfer, expense), day)
        assertEquals(4300L, budgets.single().spentCents)
    }
    @Test fun recurrenceClampsMonthEndWithoutDuplicatingOrChangingIdentity() {
        val entries = listOf(entry("Gasto", true))
        assertEquals(1, entries.transactionsInMonth(day).size)
        assertTrue(entries.transactionsInMonth(day.minusMonths(1)).isEmpty())
        val february = entries.transactionsInMonth(LocalDate.of(2026, 2, 1)).single()
        assertEquals(LocalDate.of(2026, 2, 28), february.data.toLocalDate())
        assertEquals(7L, february.id)
        assertEquals(day, entries.single().data.toLocalDate())
        assertTrue(listOf(entry("Gasto")).transactionsInMonth(day.plusMonths(1)).isEmpty())
    }
}
