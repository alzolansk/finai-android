package com.finai.app.domain.notify

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CycleSource
import com.finai.app.domain.MovementStatus
import com.finai.app.domain.TimelineMovement
import com.finai.app.domain.inbox.InboxItem
import com.finai.app.domain.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Notificações com botões: texto, valor e ação vêm do item da Caixa, nunca calculados aqui. */
class NotificationPlannerTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val conta = ContaEntity(id = 7, nome = "Internet", valorCentavos = 12_000, vencimento = today.plusDays(1).toEpochMillis(),
        status = "pendente", tipo = "a_pagar", recorrente = true)
    private fun bill(due: LocalDate) = InboxItem(
        id = "conta-7-$due", kind = "conta", title = "Internet vence", body = "", valueCents = 12_000, due = due,
        movement = TimelineMovement(due, "Internet", -12_000, MovementStatus.Expected, null, CycleSource.Conta(conta)),
    )

    @Test fun billTomorrowHasPayAndSnooze() {
        val n = NotificationPlanner.forInbox(bill(today.plusDays(1)), today)!!
        assertEquals("VENCE AMANHÃ", n.eyebrow)
        assertEquals("R$ 120,00", n.amount)
        assertEquals(listOf(NotifyAction.PayBill, NotifyAction.Snooze), n.buttons.map { it.action })
        assertEquals(7L, n.targetId)
        assertEquals(NotifyLook.Bill, n.look)
    }

    @Test fun overdueBillCountsTheDays() {
        val n = NotificationPlanner.forInbox(bill(today.minusDays(3)), today)!!
        assertEquals("ATRASADA HÁ 3 DIAS", n.eyebrow)
        assertEquals(NotifyLook.Overdue, n.look)
        assertEquals("ATRASADA HÁ 1 DIA", NotificationPlanner.forInbox(bill(today.minusDays(1)), today)!!.eyebrow)
    }

    @Test fun limitShowsProgressAgainstTheLimit() {
        val item = InboxItem("limite-Lazer-2026-11", "limite", "Lazer perto do limite", "R$ 270,00 de R$ 300,00. Revise seus gastos do mês.", 27_000)
        val n = NotificationPlanner.forInbox(item, today)!!
        assertEquals(90, n.progress)
        assertEquals("de R$ 300,00.", n.amountLabel)
        assertTrue(n.title.startsWith("Lazer"))
        assertEquals(NotifyAction.OpenSpending, n.tap)
    }

    @Test fun captureItemsHaveTheirOwnNotification() =
        assertNull(NotificationPlanner.forInbox(InboxItem("captura-1", "captura", "x", "y"), today))

    @Test fun captureSavesDirectlyOnlyWhenNothingIsMissing() {
        assertEquals(NotifyAction.CaptureSave, NotificationPlanner.forCapture(1, "iFood", 4_590, "Nubank", true, false).buttons.first().action)
        val check = NotificationPlanner.forCapture(1, "", null, "Nubank", false, false)
        assertEquals(NotifyAction.OpenSuggestion, check.buttons.first().action)
        assertEquals("Valor a confirmar", check.amount)
        assertEquals("Movimentação encontrada", check.title)
    }

    private fun t(day: LocalDate, recorrente: Boolean = false) = TransacaoEntity(data = day.toEpochMillis(), descricao = "x", valorCentavos = 100,
        categoria = "Lazer", contaOrigem = "Nubank", recorrente = recorrente, origem = "manual")

    @Test fun streakCountsDaysWithEntriesOrNothingToday() {
        val list = listOf(t(today), t(today.minusDays(1)), t(today.minusDays(3)))
        assertEquals(2, NotificationPlanner.streak(list, emptySet(), today))
        assertEquals(4, NotificationPlanner.streak(list, setOf(today.minusDays(2).toString()), today))
        // Hoje ainda sem nada: conta até ontem.
        assertEquals(1, NotificationPlanner.streak(listOf(t(today.minusDays(1))), emptySet(), today))
        // Recorrente não é registro do dia.
        assertEquals(0, NotificationPlanner.streak(listOf(t(today, recorrente = true)), emptySet(), today))
    }

    @Test fun dailyReminderOnlyWhenNothingWasLogged() {
        assertTrue(NotificationPlanner.loggedToday(listOf(t(today)), emptySet(), today))
        assertTrue(NotificationPlanner.loggedToday(emptyList(), setOf(today.toString()), today))
        assertFalse(NotificationPlanner.loggedToday(listOf(t(today.minusDays(1))), emptySet(), today))
    }

    @Test fun dailyReminderWithStreak() {
        val n = NotificationPlanner.daily(today, 5, 122_000)
        assertEquals("5 DIAS SEGUIDOS", n.eyebrow)
        assertEquals("R$ 1.220", n.amount)
        assertEquals("faltam 2 para uma semana inteira", n.progressLabel)
        assertEquals(listOf(NotifyAction.OpenEntry, NotifyAction.NothingToday), n.buttons.map { it.action })
        assertNull(NotificationPlanner.daily(today, 0, 0).amount)
    }

    @Test fun closingInvite() {
        val n = NotificationPlanner.closing("Outubro", -5_000, "2026-10")
        assertEquals("Outubro fechou apertado", n.title)
        assertEquals("faltando", n.amountLabel)
        assertEquals("fechamento-2026-10", n.key)
    }

    @Test fun samplesNeverChangeData() {
        val samples = NotificationPlanner.samples(today)
        assertEquals(3, samples.size)
        assertTrue(samples.flatMap { it.buttons }.all { it.action.opensApp })
    }
}
