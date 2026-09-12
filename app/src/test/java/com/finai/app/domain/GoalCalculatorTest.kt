package com.finai.app.domain

import com.finai.app.data.local.entity.ObjetivoEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalCalculatorTest {

    private val today = LocalDate.of(2026, 1, 1)

    @Test
    fun `goal within capacity is on track`() {
        val goal = ObjetivoEntity(
            tipo = "Reserva", nome = "Reserva", valorAlvoCentavos = 1_200_000, valorGuardadoCentavos = 0,
            prazo = today.plusMonths(12).toEpochMillis(), prioridade = 1,
        )
        val plans = GoalCalculator.plan(listOf(goal), monthlyCapacityCents = 200_000, today = today)
        assertEquals(GoalStatus.OnTrack, plans.single().status)
        assertEquals(100_000L, plans.single().monthlyContributionNeededCents) // 1_200_000 / 12
    }

    @Test
    fun `second priority goal is reassessed when capacity partially used`() {
        val g1 = ObjetivoEntity(tipo = "A", nome = "A", valorAlvoCentavos = 600_000, valorGuardadoCentavos = 0, prazo = today.plusMonths(6).toEpochMillis(), prioridade = 1)
        val g2 = ObjetivoEntity(tipo = "B", nome = "B", valorAlvoCentavos = 600_000, valorGuardadoCentavos = 0, prazo = today.plusMonths(6).toEpochMillis(), prioridade = 2)
        // each needs 100_000/mo; capacity covers g1 fully (100_000) but leaves only 50_000 for g2
        val plans = GoalCalculator.plan(listOf(g2, g1), monthlyCapacityCents = 150_000, today = today)
        assertEquals(GoalStatus.OnTrack, plans[0].status) // g1, higher priority, evaluated first
        assertEquals(GoalStatus.Reassess, plans[1].status) // g2
    }

    @Test
    fun `goal gets priority status when capacity already exhausted`() {
        val g1 = ObjetivoEntity(tipo = "A", nome = "A", valorAlvoCentavos = 1_200_000, valorGuardadoCentavos = 0, prazo = today.plusMonths(6).toEpochMillis(), prioridade = 1)
        val g2 = ObjetivoEntity(tipo = "B", nome = "B", valorAlvoCentavos = 600_000, valorGuardadoCentavos = 0, prazo = today.plusMonths(6).toEpochMillis(), prioridade = 2)
        val plans = GoalCalculator.plan(listOf(g1, g2), monthlyCapacityCents = 200_000, today = today)
        assertEquals(GoalStatus.OnTrack, plans[0].status) // needs 200_000/mo, exactly matches capacity
        assertEquals(GoalStatus.Priority, plans[1].status) // nothing left
    }

    @Test
    fun `progress is clamped between 0 and 1`() {
        val overfunded = ObjetivoEntity(tipo = "A", nome = "A", valorAlvoCentavos = 100_000, valorGuardadoCentavos = 500_000, prazo = today.plusMonths(1).toEpochMillis(), prioridade = 1)
        val plan = GoalCalculator.plan(listOf(overfunded), monthlyCapacityCents = 0, today = today).single()
        assertEquals(1f, plan.progress)
    }
}
