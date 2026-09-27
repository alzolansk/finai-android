package com.finai.app.domain

import com.finai.app.data.local.entity.ObjetivoEntity
import java.time.LocalDate

enum class GoalStatus { OnTrack, Reassess, Priority }

data class GoalPlan(
    val objetivo: ObjetivoEntity,
    val progress: Float,
    val monthlyContributionNeededCents: Long,
    /**
     * Parte do aporte necessário que cabe na capacidade de poupança (depois das
     * metas de maior prioridade). É o que "Pode gastar hoje" reserva: meta que não
     * cabe não pode virar uma falta de dinheiro para as contas.
     */
    val monthlyContributionFundedCents: Long = monthlyContributionNeededCents,
    val status: GoalStatus,
    val etaLabel: String,
)

/**
 * Progresso de metas — planning.md §3.3/§6. No AI here: status is a
 * deterministic comparison between what each goal needs per month (to hit
 * its own deadline) and the monthly savings capacity left after
 * higher-priority goals already claimed their share.
 */
object GoalCalculator {
    fun plan(
        objetivos: List<ObjetivoEntity>,
        monthlyCapacityCents: Long,
        today: LocalDate = LocalDate.now(),
    ): List<GoalPlan> {
        var remainingCapacity = monthlyCapacityCents
        return objetivos.sortedBy { it.prioridade }.map { goal ->
            val prazo = goal.prazo.toLocalDate()
            val months = monthsUntil(prazo, today)
            val missing = (goal.valorAlvoCentavos - goal.valorGuardadoCentavos).coerceAtLeast(0)
            val needed = missing / months

            val status = when {
                remainingCapacity >= needed -> GoalStatus.OnTrack
                remainingCapacity > 0 -> GoalStatus.Reassess
                else -> GoalStatus.Priority
            }
            val funded = needed.coerceAtMost(remainingCapacity.coerceAtLeast(0))
            remainingCapacity = (remainingCapacity - needed).coerceAtLeast(0)

            GoalPlan(
                objetivo = goal,
                progress = if (goal.valorAlvoCentavos <= 0) 0f
                else (goal.valorGuardadoCentavos.toFloat() / goal.valorAlvoCentavos).coerceIn(0f, 1f),
                monthlyContributionNeededCents = needed,
                monthlyContributionFundedCents = funded,
                status = status,
                etaLabel = formatMonthYearLong(prazo),
            )
        }
    }
}
