package com.finai.app.domain

import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.model.GoalBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A situação do card de meta: guardado, prazo e capacidade juntos, nunca só o percentual. */
class GoalPaceTest {
    private val today = LocalDate.of(2026, 10, 10)

    private fun meta(guardado: Long, prazo: LocalDate, alvo: Long = 1_200_000) = ObjetivoEntity(
        id = 1, tipo = "Viagem", nome = "Portugal", valorAlvoCentavos = alvo, valorGuardadoCentavos = guardado,
        prazo = prazo.toEpochMillis(), prioridade = 1)

    private fun plan(goal: ObjetivoEntity, capacity: Long) = GoalCalculator.plan(listOf(goal), capacity, today).single()

    @Test fun nothingSavedIsNotStartedEvenWhenTheMoneyWouldFit() {
        val p = plan(meta(0, today.plusMonths(12)), capacity = 500_000)
        assertEquals(GoalStatus.OnTrack, p.status)
        assertEquals(GoalPace.NotStarted, GoalPace.of(p, today))
        assertEquals(GoalBadge.NotStarted, p.toUiGoal(today = today).badge)
    }

    @Test fun savingWithCapacityIsOnTrack() {
        val p = plan(meta(200_000, today.plusMonths(12)), capacity = 500_000)
        assertEquals(GoalPace.OnTrack, GoalPace.of(p, today))
        // No ritmo não precisa de frase: o selo e os números bastam.
        assertEquals("", p.toUiGoal(today = today).headline)
    }

    @Test fun nothingSavedWithTheDeadlineNextMonthNeedsAttention() {
        val p = plan(meta(0, today.plusMonths(1), alvo = 100_000), capacity = 500_000)
        assertEquals(GoalPace.Attention, GoalPace.of(p, today))
    }

    @Test fun partialCapacityNeedsAttention() {
        val p = plan(meta(100_000, today.plusMonths(10)), capacity = 50_000)
        assertEquals(GoalStatus.Reassess, p.status)
        assertEquals(GoalPace.Attention, GoalPace.of(p, today))
    }

    @Test fun noCapacityIsLate() {
        val p = plan(meta(100_000, today.plusMonths(10)), capacity = 0)
        assertEquals(GoalPace.Late, GoalPace.of(p, today))
        assertEquals(GoalBadge.Late, p.toUiGoal(today = today).badge)
    }

    @Test fun aPassedDeadlineIsLateWhateverTheCapacity() {
        val p = plan(meta(100_000, today.minusMonths(1)), capacity = 5_000_000)
        assertEquals(GoalPace.Late, GoalPace.of(p, today))
        val ui = p.toUiGoal(today = today)
        assertTrue(ui.headline, ui.headline.startsWith("Prazo vencido"))
        assertTrue(ui.note, ui.note.contains("já passou"))
    }

    @Test fun reachedTargetIsDone() {
        val p = plan(meta(1_200_000, today.plusMonths(3)), capacity = 0)
        assertEquals(GoalPace.Done, GoalPace.of(p, today))
    }

    @Test fun cardCarriesTheMonthlyNeedAndThePlanRecommendation() {
        val p = plan(meta(0, today.plusMonths(12)), capacity = 500_000)
        val ui = p.toUiGoal(planNote = "", recommendedCents = 30_000, today = today)
        assertEquals(1_000.0, ui.monthlyNeeded, 0.0) // R$ 12.000 em 12 meses
        assertEquals(12_000.0, ui.missing, 0.0)
        assertEquals(300.0, ui.recommended, 0.0)
    }

    // Sem renda cadastrada, a projeção só tem saídas: "sem sobra" seria falta de dado.
    @Test fun withoutIncomeNothingSavedIsNotStartedNotLate() {
        val p = plan(meta(0, today.plusMonths(12)), capacity = 0)
        assertEquals(GoalStatus.Priority, p.status)
        assertEquals(GoalPace.NotStarted, GoalPace.of(p, today, capacityKnown = false))
    }

    @Test fun withoutIncomeSavingIsNeverOnTrack() {
        val p = plan(meta(200_000, today.plusMonths(12)), capacity = 500_000)
        assertEquals(GoalPace.Attention, GoalPace.of(p, today, capacityKnown = false))
        val ui = p.toUiGoal(today = today, capacityKnown = false)
        assertEquals(GoalBadge.Attention, ui.badge)
        assertTrue(ui.headline, ui.headline.startsWith("Sem renda cadastrada"))
        // Sem renda, a "sobra prevista até o prazo" não é mostrada como se fosse conhecida.
        assertEquals(null, ui.projectedAvailable)
    }

    @Test fun withoutIncomeAPassedDeadlineIsStillLate() {
        val p = plan(meta(100_000, today.minusMonths(1)), capacity = 0)
        assertEquals(GoalPace.Late, GoalPace.of(p, today, capacityKnown = false))
    }
}
