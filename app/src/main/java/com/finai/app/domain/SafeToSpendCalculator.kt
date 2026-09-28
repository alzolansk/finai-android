package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth

/** Result of today's "Pode gastar hoje" calculation — planning.md §3.1/§6. */
data class SafeToSpendResult(
    val safeTodayCents: Long,
    val slackThisMonthCents: Long,
    val daysRemaining: Int,
    val monthProgressFraction: Float,
    val lastDayOfMonth: Int,
    /** Falta prevista antes do salário. Com ela, [safeTodayCents] é zero e a falta vem primeiro. */
    val shortfall: CycleShortfall? = null,
    /** Menor saldo previsto até o salário; nulo no cálculo pelo mês (sem ciclo). */
    val floor: CycleFloor? = null,
    /** Quanto do aporte das metas coube no dinheiro disponível. */
    val reservedForPlanCents: Long = 0,
    /** Só no cálculo pelo mês: as parcelas da folga, para explicar o valor. */
    val monthIncomeCents: Long = 0,
    val monthSpentCents: Long = 0,
    val monthBillsCents: Long = 0,
)

/**
 * "Saldo seguro do dia" — the money left to spend today without borrowing
 * from bills already due or from what the user already set aside for goals
 * this month.
 *
 * folga do mês = renda do mês (contas a receber com vencimento no mês)
 *              − gastos já lançados no mês
 *              − contas a pagar do mês ainda não pagas
 *              − aporte mensal comprometido com objetivos
 * saldo seguro hoje = max(0, folga do mês / dias restantes no mês)
 */
object SafeToSpendCalculator {
    fun calculate(
        contas: List<ContaEntity>,
        transacoes: List<TransacaoEntity>,
        reservedCents: Long,
        today: LocalDate = LocalDate.now(),
    ): SafeToSpendResult {
        val month = YearMonth.from(today)
        val range = monthRangeMillis(today)

        val rendaDoMes = contas
            .filter { it.tipo == "a_receber" && it.data() in range }
            .sumOf { it.valorCentavos }

        val gastosDoMes = transacoes.transactionsInMonth(today)
            .sumOf { when (it.tipo) {
                "Receita" -> -it.valorCentavos
                "Transferencia" -> 0L
                else -> it.valorCentavos
            } }

        val contasAPagarRestantes = contas
            .filter { it.tipo == "a_pagar" && it.status != "pago" && it.data() in range }
            .sumOf { it.valorCentavos }

        val slack = rendaDoMes - gastosDoMes - contasAPagarRestantes - reservedCents

        val lastDay = month.lengthOfMonth()
        val daysRemaining = (lastDay - today.dayOfMonth + 1).coerceAtLeast(1)
        val safeToday = (slack / daysRemaining).coerceAtLeast(0)
        val monthProgress = (lastDay - daysRemaining).toFloat() / lastDay

        return SafeToSpendResult(
            safeTodayCents = safeToday,
            slackThisMonthCents = slack,
            daysRemaining = daysRemaining,
            monthProgressFraction = monthProgress.coerceIn(0f, 1f),
            lastDayOfMonth = lastDay,
            reservedForPlanCents = reservedCents.coerceAtMost((slack + reservedCents).coerceAtLeast(0)),
            monthIncomeCents = rendaDoMes,
            monthSpentCents = gastosDoMes,
            monthBillsCents = contasAPagarRestantes,
            // Sem ciclo não há dia a dia: a falta é a do mês inteiro, já hoje.
            shortfall = (slack + reservedCents).takeIf { it < 0 }?.let { CycleShortfall(today, -it) },
        )
    }

    /**
     * Com salário cadastrado, o dinheiro precisa durar até o próximo salário, não até o fim
     * do mês. A folga parte do **menor saldo previsto** até lá ([PayCycle.floor]), não da
     * sobra final do ciclo: gastar a sobra final hoje deixaria descoberto um compromisso que
     * vence antes da próxima entrada. Com falta prevista, não há nada para gastar e o aporte
     * das metas não é reservado. [SafeToSpendResult.lastDayOfMonth] vira o dia do salário.
     */
    fun fromCycle(cycle: PayCycle, reservedCents: Long): SafeToSpendResult {
        val floor = cycle.floor
        val available = floor.cents.coerceAtLeast(0)
        val slack = floor.cents - reservedCents
        return SafeToSpendResult(
            safeTodayCents = (slack / cycle.diasAteProximo).coerceAtLeast(0),
            slackThisMonthCents = slack,
            daysRemaining = cycle.diasAteProximo,
            monthProgressFraction = cycle.progress,
            lastDayOfMonth = cycle.proximo.dayOfMonth,
            shortfall = cycle.shortfall,
            floor = floor,
            reservedForPlanCents = reservedCents.coerceAtMost(available),
        )
    }

    /** [ContaEntity.vencimento] is what "the month" is measured against here. */
    private fun ContaEntity.data(): Long = vencimento
}
