package com.finai.app.domain

import com.finai.app.data.local.entity.ObjetivoEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Se a sobra projetada até o prazo cobre o que falta ([GoalCalculator]). É uma leitura de
 * capacidade, não de andamento: diz se o dinheiro existe, não se a meta está sendo alimentada.
 * Alertas e IA usam este; a tela usa [GoalPace].
 */
enum class GoalStatus { OnTrack, Reassess, Priority }

/**
 * Situação da meta como o usuário a vê no card. Junta a capacidade ([GoalStatus]) com o que
 * a capacidade sozinha não mostra: quanto já foi guardado e quanto tempo resta. Antes o card
 * mostrava [GoalStatus] direto, e uma meta com R$ 0 guardados aparecia "No ritmo" só porque a
 * sobra projetada cobriria o valor.
 *
 * Não há data de criação da meta nem histórico de aportes no Room, então não dá para comparar
 * o guardado com o "esperado até hoje". Por isso a regra não chuta ritmo: com nada guardado, a
 * meta é [NotStarted] (ou [Attention] se o prazo está perto), nunca [OnTrack].
 */
enum class GoalPace {
    NotStarted, OnTrack, Attention, Late, Done,

    /** Reserva contínua: sem alvo, não tem progresso nem conclusão. */
    Open;

    companion object {
        /** Com nada guardado e prazo a até este número de meses, a meta pede atenção. */
        const val NEAR_DEADLINE_MONTHS = 2L

        /**
         * [capacityKnown] falso quando não há renda cadastrada: a projeção só tem saídas, e o
         * [GoalStatus] sairia "sem sobra" por falta de dado, não por falta de dinheiro. Nesse
         * caso a meta não é julgada pela capacidade: nada guardado continua [NotStarted] e meta
         * com valor guardado fica em [Attention] (não dá para confirmar o ritmo), nunca [OnTrack]
         * nem [Late].
         */
        fun of(plan: GoalPlan, today: LocalDate, capacityKnown: Boolean = true): GoalPace {
            // Antes, alvo zero dava "falta zero" e a reserva aparecia como concluída.
            if (plan.objetivo.isOpenReserve) return Open
            if (plan.missingCents <= 0L) return Done
            val prazo = plan.objetivo.prazo.toLocalDate()
            if (prazo.isBefore(today)) return Late
            val nearDeadline = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(prazo)) < NEAR_DEADLINE_MONTHS
            if (!capacityKnown) {
                return if (plan.objetivo.valorGuardadoCentavos > 0 || nearDeadline) Attention else NotStarted
            }
            return when (plan.status) {
                // Sem sobra prevista até o prazo: do jeito que está, não chega a tempo.
                GoalStatus.Priority -> Late
                GoalStatus.Reassess -> Attention
                GoalStatus.OnTrack -> when {
                    plan.objetivo.valorGuardadoCentavos > 0 -> OnTrack
                    nearDeadline -> Attention
                    else -> NotStarted
                }
            }
        }
    }
}

/**
 * Reserva sem valor-alvo (cofre contínuo): só tem saldo e, se o usuário quiser, um aporte
 * mensal planejado. Não tem percentual, prazo que importe nem status "Concluído", e o saldo
 * dela não é dinheiro livre para outros objetivos.
 */
val ObjetivoEntity.isOpenReserve: Boolean get() = valorAlvoCentavos <= 0

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
