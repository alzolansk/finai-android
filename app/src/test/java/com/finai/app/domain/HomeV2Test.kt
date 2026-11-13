package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HomeV2Test {
    private val today = LocalDate.of(2026, 11, 8)
    private fun bill(paid: Boolean = false) = ContaEntity(id = 1, nome = "Internet", valorCentavos = 12_000, vencimento = today.plusDays(7).toEpochMillis(), status = if (paid) "pago" else "pendente", tipo = "a_pagar", recorrente = false)
    private fun expense(date: LocalDate, cents: Long, category: String = "Alimentação", name: String = "Mercado") = TransacaoEntity(data = date.toEpochMillis(), descricao = name, valorCentavos = cents, categoria = category, contaOrigem = "Conta", recorrente = false, tipo = "Gasto", origem = "manual")
    private fun history(cents: Long) = (1L..3L).map { expense(today.minusMonths(it), cents) }

    @Test fun paidBillStaysInTheWeekToReopen() {
        val pending = HomeWeek.movements(listOf(bill()), emptyList(), emptyList(), today).single()
        val paid = HomeWeek.movements(listOf(bill(true)), emptyList(), emptyList(), today).single()
        assertEquals(pending.date, paid.date)
        assertEquals(today.plusDays(7), paid.date)
        assertEquals(MovementStatus.Paid, paid.status)
    }
    @Test fun invoiceItemsAreNotRepeatedAlongsideTheBill() {
        val item = expense(today.plusDays(2), 12_000).copy(faturaId = 1)
        assertEquals(1, HomeWeek.movements(listOf(bill()), listOf(item), emptyList(), today).size)
    }
    @Test fun weekCrossesMonthAndIgnoresAdjustments() {
        val end = LocalDate.of(2026, 11, 28)
        val dec = bill().copy(vencimento = LocalDate.of(2026, 12, 2).toEpochMillis())
        val adjustment = expense(end, 100).copy(tipo = ADJUSTMENT_TYPE)
        assertEquals(listOf("Internet"), HomeWeek.movements(listOf(dec), listOf(adjustment), emptyList(), end).map { it.descricao })
    }
    @Test fun noCoachAtOrBelowOnePointSixTimesAverage() {
        assertNull(HomeCoach.detect(history(10_000) + expense(today, 16_000), today))
    }
    @Test fun coachUsesThreeMonthsRatherThanOnlyLastMonth() {
        val h = listOf(expense(today.minusMonths(1), 30_000), expense(today.minusMonths(2), 6_000), expense(today.minusMonths(3), 9_000))
        assertNotNull(HomeCoach.detect(h + expense(today, 31_000), today))
        assertNull(HomeCoach.detect(h + expense(today, 24_000), today))
    }
    @Test fun futureExpensesDoNotCreateNovelty() {
        assertNull(HomeCoach.detect(history(10_000) + expense(today.plusDays(1), 90_000), today))
    }
    @Test fun noHistoryDoesNotInventBaseline() {
        assertNull(HomeCoach.detect(listOf(expense(today, 90_000)), today))
    }
    @Test fun dismissedPatternsStaySilentEvenWhenAmountsChange() {
        val ts = history(10_000) + expense(today, 30_000)
        val dismissed = mutableSetOf<String>()
        // Estabelecimento e categoria são padrões distintos, cada um tem chave estável.
        while (true) { val next = HomeCoach.detect(ts, today, dismissed) ?: break; dismissed += next.id }
        assertNull(HomeCoach.detect(ts + expense(today, 60_000), today, dismissed))
    }
    @Test fun debtPaymentsAndSystemCategoriesDoNotCreateCoach() {
        val payments = history(10_000).map { it.copy(origem = DebtSchedule.PAYMENT_ORIGIN) } + expense(today, 90_000).copy(origem = DebtSchedule.PAYMENT_ORIGIN)
        assertNull(HomeCoach.detect(payments, today))
    }
}
