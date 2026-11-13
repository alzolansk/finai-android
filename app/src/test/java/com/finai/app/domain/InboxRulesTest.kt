package com.finai.app.domain

import com.finai.app.data.local.entity.AvisoEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.domain.inbox.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InboxRulesTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val item = InboxItem("limite-Lazer-2026-11", "limite", "Lazer perto do limite", "", 18_000)
    private fun dismissed(i: InboxItem = item) = AvisoEntity(i.id, i.stateType, "dispensado", i.valueCents,
        vistoEm = today.toString(), dispensadoEm = today.toString())

    @Test fun dismissedDoesNotReturnOnTextChangeOrExactlyTwentyPercent() {
        assertTrue(InboxRules.visible(listOf(item.copy(title = "Novo texto", valueCents = 21_600)), listOf(dismissed()), today.plusDays(1)).isEmpty())
    }
    @Test fun reopensAboveTwentyPercentAndResetsSeen() {
        val changed = item.copy(valueCents = 21_601)
        val state = InboxRules.reconcile(listOf(changed), listOf(dismissed()), today).single()
        assertEquals("novo", state.estado); assertNull(state.vistoEm)
        assertEquals(21_601, state.valorRef)
    }
    @Test fun hittingLimitReopensEvenWithLessThanTwentyPercent() {
        assertEquals(1, InboxRules.visible(listOf(item.copy(valueCents = 20_000, level = 1)), listOf(dismissed()), today).size)
    }
    @Test fun dismissedAlreadyOverLimitStaysHidden() {
        val over = item.copy(level = 1)
        assertTrue(InboxRules.visible(listOf(over), listOf(dismissed(over)), today.plusDays(1)).isEmpty())
    }
    @Test fun becomingOverdueReopensBill() {
        val bill = item.copy(kind = "conta", due = today)
        assertEquals(1, InboxRules.visible(listOf(bill.copy(level = 1)), listOf(dismissed(bill)), today.plusDays(1)).size)
    }
    @Test fun snoozeWaitsForTheDateAndReappearsNew() {
        val state = dismissed().copy(estado = "adiado", adiadoAte = today.plusDays(2).toString())
        assertTrue(InboxRules.visible(listOf(item), listOf(state), today.plusDays(1)).isEmpty())
        assertEquals("novo", InboxRules.reconcile(listOf(item), listOf(state), today.plusDays(2)).single().estado)
    }
    @Test fun resolvedStaysHiddenEvenIfValueChanges() {
        assertTrue(InboxRules.visible(listOf(item.copy(valueCents = 50_000)), listOf(dismissed().copy(estado = "resolvido")), today).isEmpty())
    }
    @Test fun eventThatDisappearsBecomesResolved() {
        val state = AvisoEntity(item.id, item.stateType)
        val next = InboxRules.reconcile(emptyList(), listOf(state), today).single()
        assertEquals("resolvido", next.estado); assertEquals(today.toString(), next.resolvidoEm)
    }
    @Test fun newlySeenIsNoLongerNew() {
        assertTrue(InboxRules.isNew(item, emptyList()))
        assertFalse(InboxRules.isNew(item, listOf(dismissed().copy(estado = "visto"))))
    }
    @Test fun absoluteVariationAlsoCoversDecreaseAndZeroBaseline() {
        assertTrue(InboxRules.worsened(item.copy(valueCents = 14_399), dismissed()))
        assertFalse(InboxRules.worsened(item.copy(valueCents = 14_400), dismissed()))
        assertTrue(InboxRules.worsened(item, dismissed().copy(valorRef = 0)))
    }
    @Test fun catalogKeepsBillIdWhenDueBecomesOverdue() {
        val conta = ContaEntity(1, "Fatura", 132_000, today.plusDays(1).toEpochMillis(), "pendente", "a_pagar", false)
        fun catalog(day: LocalDate) = InboxCatalog.build(listOf(conta), emptyList(), emptyList(),
            FinancialPlan.build(listOf(conta), emptyList(), emptyList(), emptyList(), day),
            emptyList(), emptyList(), emptyList(), day).filter { it.movement != null }.single()
        val before = catalog(today); val after = catalog(today.plusDays(2))
        assertEquals(before.id, after.id); assertEquals(1, after.level)
        assertEquals(1, InboxRules.visible(listOf(after), listOf(dismissed(before)), today.plusDays(2)).size)
    }
    @Test fun catalogBudgetIdIsMonthlyAndDoesNotDependOnTitle() {
        fun catalog(day: LocalDate, cents: Long) = InboxCatalog.build(emptyList(), emptyList(), emptyList(),
            FinancialPlan.build(emptyList(), emptyList(), emptyList(), emptyList(), day),
            listOf(BudgetProgress("Lazer", cents, 20_000, BudgetTone.Warn, 60_000)), emptyList(), emptyList(), day).single()
        assertEquals(catalog(today, 18_000).id, catalog(today.plusDays(1), 19_000).id)
        assertNotEquals(catalog(today, 18_000).id, catalog(today.plusMonths(1), 18_000).id)
    }
}
