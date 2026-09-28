package com.finai.app.domain

import com.finai.app.data.local.entity.ObjetivoEntity
import java.time.LocalDate
import java.time.YearMonth

enum class GoalStatus { OnTrack, Reassess, Priority }

data class GoalPlan(
    val objetivo: ObjetivoEntity,
    val progress: Float,
    val monthlyContributionNeededCents: Long,
    /**
     * Aporte recomendado agora para esta meta. O [FinancialPlan] sobrescreve com a recomendação
     * dele (livre até o salário, depois de dívida cara e metas de maior prioridade). É só
     * recomendação: não desconta do livre.
     */
    val monthlyContributionFundedCents: Long = monthlyContributionNeededCents,
    val status: GoalStatus,
    val etaLabel: String,
    /** Quanto falta guardar. */
    val missingCents: Long = 0,
    /**
     * Sobra projetada do mês corrente até o prazo ([SavingsProjection]), já
     * descontado o que as metas de maior prioridade usam. Nulo sem projeção.
     */
    val projectedAvailableCents: Long? = null,
)

/**
 * Progresso de metas — planning.md §3.3/§6. No AI here, só comparação
 * determinística.
 *
 * Com [SavingsProjection], o status compara o que falta guardar com a sobra
 * projetada de todos os meses até o prazo (depois das metas de maior
 * prioridade). É o que responde "até 2028 eu consigo?". Sem projeção (testes
 * antigos), compara o aporte mensal com a capacidade do mês corrente.
 *
 * O aporte recomendado ([GoalPlan.monthlyContributionFundedCents]) é definido
 * pelo [FinancialPlan]; aqui fica só um valor inicial pela capacidade do mês.
 */
object GoalCalculator {
    fun plan(
        objetivos: List<ObjetivoEntity>,
        monthlyCapacityCents: Long,
        today: LocalDate = LocalDate.now(),
        projection: SavingsProjection? = null,
    ): List<GoalPlan> {
        var remainingCapacity = monthlyCapacityCents
        var consumedByHigherPriority = 0L
        return objetivos.sortedBy { it.prioridade }.map { goal ->
            val prazo = goal.prazo.toLocalDate()
            val months = monthsUntil(prazo, today)
            val missing = (goal.valorAlvoCentavos - goal.valorGuardadoCentavos).coerceAtLeast(0)
            val needed = missing / months

            val available = projection?.let { it.cumulativeThrough(YearMonth.from(prazo)) - consumedByHigherPriority }
            val status = if (available != null) {
                when {
                    missing == 0L || available >= missing -> GoalStatus.OnTrack
                    available > 0 -> GoalStatus.Reassess
                    else -> GoalStatus.Priority
                }
            } else {
                when {
                    remainingCapacity >= needed -> GoalStatus.OnTrack
                    remainingCapacity > 0 -> GoalStatus.Reassess
                    else -> GoalStatus.Priority
                }
            }
            if (available != null) consumedByHigherPriority += missing.coerceAtMost(available.coerceAtLeast(0))
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
                missingCents = missing,
                projectedAvailableCents = available,
            )
        }
    }
}
