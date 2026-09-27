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
        aporteMensalMetasCents: Long,
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

        val slack = rendaDoMes - gastosDoMes - contasAPagarRestantes - aporteMensalMetasCents

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
        )
    }

    /**
     * Com salário recorrente cadastrado, o dinheiro precisa durar até o próximo salário,
     * não até o fim do mês: folga = livre do ciclo − aporte das metas, dividida pelos dias
     * que faltam para ele cair. [SafeToSpendResult.lastDayOfMonth] vira o dia do salário.
     */
    fun fromCycle(cycle: PayCycle, aporteMensalMetasCents: Long): SafeToSpendResult {
        val slack = cycle.livreCents - aporteMensalMetasCents
        return SafeToSpendResult(
            safeTodayCents = (slack / cycle.diasAteProximo).coerceAtLeast(0),
            slackThisMonthCents = slack,
            daysRemaining = cycle.diasAteProximo,
            monthProgressFraction = cycle.progress,
            lastDayOfMonth = cycle.proximo.dayOfMonth,
        )
    }

    /** [ContaEntity.vencimento] is what "the month" is measured against here. */
    private fun ContaEntity.data(): Long = vencimento
}
