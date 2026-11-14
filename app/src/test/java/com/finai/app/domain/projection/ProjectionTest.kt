package com.finai.app.domain.projection

import com.finai.app.domain.CycleSource
import com.finai.app.domain.assistant.PlanBuilder
import com.finai.app.domain.assistant.SavedPlan
import com.finai.app.domain.assistant.T
import com.finai.app.domain.assistant.T.d
import com.finai.app.domain.assistant.T.snap
import com.finai.app.domain.assistant.T.tx
import com.finai.app.domain.assistant.WatchRules
import com.finai.app.domain.balance.DayBalance
import com.finai.app.domain.balance.FreeToSpend
import com.finai.app.domain.balance.NextIncomeFinder
import com.finai.app.data.local.entity.AcompanhamentoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Projeção única, janela de pagamento, cenários, plano salvo e acompanhamentos (plano §5.2, §5.6, §5.9). */
class ProjectionTest {
    private val today = T.TODAY

    private fun base(account: Long = 100_000) = listOf(tx(1, today, account, "Saldo", "Receita", "Receita"))

    // ── a projeção é a mesma do FreeToSpend ─────────────────────────────

    @Test fun freeToSpendIsTheSameSeriesAsTheProjection() {
        val trans = listOf(T.salary(start = d(2026, 9, 28))) + base(500_000) + tx(3, d(2026, 11, 18), 80_000, "Mercado", "Alimentação")
        val contas = listOf(T.conta(10, "Luz", 30_000, d(2026, 11, 20)))
        val next = NextIncomeFinder.find(contas, trans, emptyList(), today)!!
        val free = FreeToSpend.build(contas, trans, emptyList(), today, next, 30_000)
        val proj = CashFlowProjection.of(contas, trans, emptyList(), today, free.windowEnd)
        assertEquals(proj.series, free.series)
        assertEquals(proj.accountCents, free.accountCents)
        assertEquals(proj.lowest, free.lowest)
        assertEquals((proj.lowest.cents - 30_000).coerceAtLeast(0), free.freeCents)
    }

    @Test fun projectionMarksTruncationBeyondTheCap() {
        val p = CashFlowProjection.of(emptyList(), base(), emptyList(), today, today.plusMonths(40))
        assertTrue(p.truncated)
        assertEquals(today.plusMonths(CashFlowProjection.MAX_MONTHS), p.end)
    }

    // ── janela de pagamento ─────────────────────────────────────────────

    private fun fixed(vararg cents: Long): CashFlowProjection {
        val series = cents.mapIndexed { i, c -> DayBalance(today.plusDays(i.toLong()), c) }
        return CashFlowProjection(today, cents.first(), series.last().date, series, emptyList(), com.finai.app.domain.CycleFloor(series.minByOrNull { it.cents }!!.date, cents.min()), false, null)
    }

    @Test fun paymentWindowLooksAtEveryLaterDayNotJustThePaymentDay() {
        val p = fixed(500, 100, 700, 700)
        // Pagar 150 hoje deixaria o dia 1 em -50: só cabe quando o pior dia seguinte aguenta.
        val w = PaymentWindow.of(p, 150, 0)
        assertEquals(today.plusDays(2), w.firstViable)
        assertEquals(today.plusDays(2), w.firstWithMargin)
        assertEquals(550, w.lowestAfter!!.cents)
        val withBigMargin = PaymentWindow.of(p, 150, 600)
        assertEquals(today.plusDays(2), withBigMargin.firstViable)
        assertNull("nenhum dia deixa 600 de margem", withBigMargin.firstWithMargin)
        assertNull(PaymentWindow.of(p, 5_000, 0).firstViable)
    }

    // ── cenários ────────────────────────────────────────────────────────

    private fun run(vararg events: ScenarioEvent, trans: List<com.finai.app.data.local.entity.TransacaoEntity> = base(), margin: Long = 30_000, dividas: List<com.finai.app.data.local.entity.DividaEntity> = emptyList()) =
        ScenarioEngine.run(emptyList(), trans, dividas, today, today.plusMonths(6), margin, events.toList())

    @Test fun scenarioVerdictsComeFromTheLowestDay() {
        assertEquals(ScenarioVerdict.Fits, run(ScenarioEvent.OneOff(today.plusDays(5), -50_000, "TV")).verdict)
        assertEquals(ScenarioVerdict.Tight, run(ScenarioEvent.OneOff(today.plusDays(5), -80_000, "TV")).verdict)
        assertEquals(ScenarioVerdict.DoesNotFit, run(ScenarioEvent.OneOff(today.plusDays(5), -150_000, "TV")).verdict)
        // O plano base já era negativo: o problema não é da decisão nova.
        val broke = base(100_000) + tx(2, today, 500_000, "Dívida paga", "Outros")
        assertEquals(ScenarioVerdict.AlreadyShort, run(ScenarioEvent.OneOff(today.plusDays(5), -10_000, "x"), trans = broke).verdict)
    }

    @Test fun installmentsAreSpreadMonthlyAndNeverWritten() {
        val r = run(ScenarioEvent.Installments(today.plusDays(10), 3, 30_000, "Cadeira"))
        val hypothetical = r.after.movements.filter { it.source == null }
        assertEquals(3, hypothetical.size)
        assertTrue(hypothetical.all { it.cents == -30_000L })
        assertEquals(listOf(today.plusDays(10), today.plusDays(10).plusMonths(1), today.plusDays(10).plusMonths(2)), hypothetical.map { it.date })
        assertTrue("a base não vê o cenário", r.base.movements.none { it.source == null })
    }

    @Test fun payingOffADebtRemovesTheLaterInstallmentsButKeepsTheEarlierOnes() {
        val debt = T.debt(1, "Picpay", remaining = 6, eachCents = 20_000, next = d(2026, 11, 20))
        val r = run(ScenarioEvent.PayOffDebt(1, 60_000, d(2026, 12, 25)), dividas = listOf(debt), trans = base(1_000_000))
        // As parcelas anteriores à quitação entram como movimentos do cenário; a dívida em si não gera mais nenhuma.
        assertTrue(r.after.movements.none { (it.source as? CycleSource.Parcela)?.parcela?.divida?.id == 1L })
        val parcelas = r.after.movements.filter { it.source == null && it.cents == -20_000L }
        assertEquals(listOf(d(2026, 11, 20), d(2026, 12, 20)), parcelas.map { it.date })
        assertTrue(r.after.movements.any { it.descricao.startsWith("Quitação") && it.cents == -60_000L })
    }

    @Test fun incomeDropReducesOnlyIncome() {
        val trans = base(100_000) + T.salary(start = d(2026, 9, 28), cents = 500_000)
        val r = run(ScenarioEvent.IncomeChange(percent = -20, cents = null, months = 3), trans = trans)
        assertTrue(r.lowestDeltaCents <= 0)
        assertTrue(r.after.movements.filter { it.source == null }.all { it.cents < 0 })
    }

    // ── plano salvo e acompanhamentos ───────────────────────────────────

    @Test fun savedPlanEncodesAndDecodesWithoutLosingTheGoals() {
        val plan = SavedPlan(java.time.YearMonth.of(2026, 11), 585_000, 200_000, 90_000, listOf(Triple(1L, "Viagem", 30_000L)), mapOf("Lazer" to 40_000L))
        assertEquals(plan, SavedPlan.decode(plan.encode()))
        assertNull(SavedPlan.decode("lixo"))
    }

    @Test fun followingThePlanWithoutASavedOneSaysSoInsteadOfRebuildingThePast() {
        val r = PlanBuilder.follow(snap(listOf(T.salary(start = d(2026, 9, 28)))))
        assertTrue(r.facts, r.facts.contains("plano", ignoreCase = true))
    }

    @Test fun purchaseWatchIsMetOnlyWhenTheAmountFitsWithMargin() {
        val s = snap(base(500_000))
        fun rule(cents: Long) = AcompanhamentoEntity(tipo = WatchRules.PURCHASE, alvo = "TV", valorCentavos = cents, descricao = "", estado = WatchRules.ACTIVE, criadoEm = 0)
        assertTrue(WatchRules.evaluate(rule(100_000), s).met)
        assertFalse(WatchRules.evaluate(rule(900_000), s).met)
        // Pausado não avisa.
        val paused = rule(100_000).copy(estado = WatchRules.PAUSED)
        assertTrue(WatchRules.newlyMet(s.copy(acompanhamentos = listOf(paused))).isEmpty())
        // Só avisa quando passa a valer: o resultado já gravado como "atendido" não repete.
        val fresh = rule(100_000).copy(id = 1)
        assertEquals(1, WatchRules.newlyMet(s.copy(acompanhamentos = listOf(fresh.copy(ultimoResultado = WatchRules.WAITING)))).size)
        val key = WatchRules.resultKey(WatchRules.evaluate(fresh, s), today)
        assertTrue(WatchRules.newlyMet(s.copy(acompanhamentos = listOf(fresh.copy(ultimoResultado = key)))).isEmpty())
    }

    @Test fun payoffWatchSeesAMissingDebtAsNotMet() {
        val s = snap(base())
        val rule = AcompanhamentoEntity(tipo = WatchRules.PAYOFF, alvo = "Picpay", alvoId = 9, valorCentavos = 0, descricao = "", estado = WatchRules.ACTIVE, criadoEm = 0)
        assertFalse(WatchRules.evaluate(rule, s).met)
        assertNotNull(WatchRules.describe(rule))
    }
}
