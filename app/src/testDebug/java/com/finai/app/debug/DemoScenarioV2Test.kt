package com.finai.app.debug

import com.finai.app.domain.FinancialPlan
import com.finai.app.domain.HomeSituation
import com.finai.app.domain.SpendExplanation
import com.finai.app.domain.balance.FreeToSpend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Os números do protótipo v2 (planning-v2 §6, Fase 1): R$ 3.480 em conta, menor saldo R$ 1.520 em
 * 18/11, livre R$ 1.220 com margem R$ 300 e R$ 101/dia. Se o quadro `v2/Hoje` mudar, muda aqui.
 */
class DemoScenarioV2Test {
    private val s = DemoScenarioV2
    private val free = FreeToSpend.of(s.contas, s.transacoes, s.dividas, s.today)!!

    @Test fun prototypeNumbers() {
        assertEquals(348_000L, free.accountCents)
        assertEquals(152_000L, free.lowest.cents)
        assertEquals(LocalDate.of(2026, 11, 18), free.lowest.date)
        assertEquals(122_000L, free.freeCents)
        assertEquals(101L, free.perDayCents / 100)
        assertEquals(12, free.days)
        assertEquals(LocalDate.of(2026, 11, 20), free.nextIncome.date)
        assertEquals("adiantamento", free.nextIncome.noun)
        assertNull(free.shortfall)
    }

    @Test fun everyScreenGetsTheSameFree() {
        val plan = FinancialPlan.build(s.contas, s.transacoes, s.dividas, s.objetivos, s.today)
        assertEquals(free.freeCents, plan.livreCents)
        assertEquals(free.freeCents, plan.safe.slackThisMonthCents)
        assertEquals(free.perDayCents, plan.safe.safeTodayCents)
        val home = HomeSituation.of(plan.safe, plan.cycle, plan.free?.nextIncome)
        assertEquals(free.freeCents, home.mainCents)
        assertEquals("LIVRE ATÉ O ADIANTAMENTO · 20/11", home.label)
        assertTrue(plan.aiBlock().contains("Livre"))
        // A conta de "Entenda este valor" fecha: em conta − vai sair − margem = livre.
        val e = SpendExplanation.of(plan.safe, plan.cycle, s.today, free = plan.free)
        assertEquals(free.freeCents, e.steps.last().cents)
        assertEquals(e.steps.last().cents, e.steps.filterNot { it.total }.sumOf { it.cents })
    }

    @Test fun prototypeOutflowsMatchTheActualReference() {
        assertEquals(listOf(-132_000L, -11_000L, -12_000L, -41_000L), free.movements.map { it.cents })
        assertEquals(listOf(10, 12, 15, 18), free.movements.map { it.date.dayOfMonth })
        assertTrue(free.movements.last().descricao.contains("Picpay"))
        assertEquals(196_000L, free.outflowsCents)
    }
}
