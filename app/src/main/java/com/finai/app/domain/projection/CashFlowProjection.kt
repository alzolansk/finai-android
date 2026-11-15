package com.finai.app.domain.projection

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CashFlowTimeline
import com.finai.app.domain.CycleFloor
import com.finai.app.domain.CycleSource
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.MovementStatus
import com.finai.app.domain.TimelineMovement
import com.finai.app.domain.TransactionType
import com.finai.app.domain.balance.AccountBalance
import com.finai.app.domain.balance.DayBalance
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Movimento hipotético de um cenário: só existe na memória da projeção, nunca é gravado. */
data class ExtraMovement(val date: LocalDate, val cents: Long, val label: String)

/**
 * Estimativa do gasto variável que ainda não foi lançado (mercado, transporte…): sem ela a
 * projeção trataria como zero o que só não tem lançamento futuro. A regra é a mesma do "cabe
 * guardar" ([com.finai.app.domain.plans.SavingsCapacity]): média do gasto que não se repete
 * sozinho nos últimos meses fechados, menos o que já está lançado para o mês. Com menos de
 * [MIN_SAMPLE] meses de histórico não há estimativa: o app diz que falta cobertura.
 */
data class VariableProvision(val monthlyAverageCents: Long, val sampleMonths: Int) {
    companion object {
        const val MIN_SAMPLE = 2
        const val LOOKBACK = 3L

        fun variable(flow: MonthCashFlow): Long =
            flow.avulsas.filter { it.tipo == TransactionType.Gasto.name }.sumOf { it.valorCentavos } +
                flow.payable.filter { !it.recorrente }.sumOf { it.valorCentavos }

        fun of(contas: List<ContaEntity>, transacoes: List<TransacaoEntity>, dividas: List<DividaEntity>, today: LocalDate): VariableProvision? {
            val current = YearMonth.from(today)
            val past = (1L..LOOKBACK).map { variable(MonthCashFlow.of(contas, transacoes, dividas, current.minusMonths(it), today)) }.filter { it > 0 }
            if (past.size < MIN_SAMPLE) return null
            // Um mês fora da curva (acima de 2× a mediana) não puxa a média: entra pelo valor da mediana.
            val median = past.sorted()[past.size / 2]
            val trimmed = past.map { if (it > median * 2) median else it }
            return VariableProvision(trimmed.sum() / trimmed.size, past.size)
        }
    }
}

/**
 * O fluxo de caixa dia a dia, de hoje até [end]: a única projeção do app (planejamento do
 * assistente, §5.2). Mesmas regras do saldo em conta ([AccountBalance]) e do livre
 * ([com.finai.app.domain.balance.FreeToSpend], que é esta projeção até a véspera da renda):
 * o saldo é contínuo, conta a pagar vencida sai hoje, entrada atrasada não conta, conta paga
 * entra no dia do pagamento. Cenários somam [ExtraMovement] em memória; nada é gravado.
 */
data class CashFlowProjection(
    val today: LocalDate,
    /** Saldo em conta hoje: o que já foi efetivado. */
    val accountCents: Long,
    val end: LocalDate,
    /** Saldo no fim de cada dia, de hoje até [end]. */
    val series: List<DayBalance>,
    /** O que ainda vai acontecer, em ordem, com o saldo depois de cada movimento. */
    val movements: List<TimelineMovement>,
    val lowest: CycleFloor,
    /** O pedido ia além do teto de [MAX_MONTHS]: o que vem depois de [end] não foi verificado. */
    val truncated: Boolean,
    /** Estimativa de gasto variável somada à série; nula quando a projeção só usa o que está lançado. */
    val provision: VariableProvision?,
) {
    fun balanceOn(date: LocalDate): Long = series.lastOrNull { !it.date.isAfter(date) }?.cents ?: accountCents

    /** Menor saldo de [from] até o fim da projeção. */
    fun lowestFrom(from: LocalDate): DayBalance? = series.filter { !it.date.isBefore(from) }.minByOrNull { it.cents }

    /** Menor saldo de cada mês. */
    fun monthLows(): List<Pair<YearMonth, DayBalance>> =
        series.groupBy { YearMonth.from(it.date) }.map { (m, days) -> m to days.minByOrNull { it.cents }!! }.sortedBy { it.first }

    /** Primeiro dia em que o saldo fica negativo; nulo se nunca. */
    val firstNegative: DayBalance? get() = series.firstOrNull { it.cents < 0 }

    companion object {
        const val MAX_MONTHS = 24L

        fun of(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            today: LocalDate,
            end: LocalDate,
            extra: List<ExtraMovement> = emptyList(),
            provision: VariableProvision? = null,
        ): CashFlowProjection {
            val limit = today.plusMonths(MAX_MONTHS)
            val windowEnd = maxOf(minOf(end, limit), today)
            val start = AccountBalance.startMonthFor(transacoes, contas, today)
            val months = ChronoUnit.MONTHS.between(start, YearMonth.from(windowEnd)).toInt() + 1
            val all = CashFlowTimeline.monthMovements(contas, transacoes, dividas, today, months, from = start, paidOnPaymentDay = true)

            val account = all.filter { AccountBalance.settled(it, today) }.sumOf { it.cents }
            // O que ainda vai acontecer: pagamento atrasado cai hoje; entrada atrasada não conta.
            val planned = all.filterNot { AccountBalance.settled(it, today) }.mapNotNull { m ->
                when {
                    m.date.isAfter(windowEnd) -> null
                    m.date.isBefore(today) -> if (m.cents < 0) m.copy(date = today, movedFrom = m.date) else null
                    m.date == today && m.cents > 0 && m.source is CycleSource.Conta -> null
                    else -> m
                }
            }
            val hypothetical = extra.filter { it.cents != 0L && !it.date.isAfter(windowEnd) }.map { e ->
                TimelineMovement(maxOf(e.date, today), e.label, e.cents, MovementStatus.Expected, balanceAfterCents = null, source = null)
            }
            val ahead = (planned + hypothetical).sortedWith(compareBy<TimelineMovement> { it.date }.thenByDescending { it.cents })

            var running = account
            val movements = ahead.map { m -> running += m.cents; m.copy(balanceAfterCents = running) }
            val byDay = movements.groupBy { it.date }
            val reserve = provision?.let { provisionByDay(it, contas, transacoes, dividas, today, windowEnd) }.orEmpty()
            var level = account
            val series = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(windowEnd) }.map { d ->
                level += (byDay[d]?.sumOf { it.cents } ?: 0L) - (reserve[d] ?: 0L)
                DayBalance(d, level)
            }.toList()
            val min = series.minOf { it.cents }
            val lowest = series.first { it.cents == min }.let { CycleFloor(it.date, it.cents) }
            return CashFlowProjection(today, account, windowEnd, series, movements, lowest, truncated = end.isAfter(limit), provision = provision)
        }

        /**
         * Quanto da média variável ainda não está lançado em cada mês, espalhado pelos dias que
         * faltam (a partir de amanhã no mês atual). O que já foi lançado ou já está numa fatura
         * do mês desconta da média, para não contar duas vezes.
         */
        private fun provisionByDay(
            p: VariableProvision, contas: List<ContaEntity>, transacoes: List<TransacaoEntity>, dividas: List<DividaEntity>,
            today: LocalDate, end: LocalDate,
        ): Map<LocalDate, Long> {
            val out = HashMap<LocalDate, Long>()
            var month = YearMonth.from(today)
            while (!month.atDay(1).isAfter(end)) {
                val known = VariableProvision.variable(MonthCashFlow.of(contas, transacoes, dividas, month, today))
                val missing = (p.monthlyAverageCents - known).coerceAtLeast(0)
                val first = if (month == YearMonth.from(today)) today.plusDays(1) else month.atDay(1)
                val days = generateSequence(first) { it.plusDays(1) }.takeWhile { YearMonth.from(it) == month }.toList()
                if (missing > 0 && days.isNotEmpty()) {
                    val perDay = missing / days.size
                    days.forEachIndexed { i, d -> if (!d.isAfter(end)) out[d] = perDay + if (i == days.lastIndex) missing - perDay * days.size else 0 }
                }
                month = month.plusMonths(1)
            }
            return out
        }
    }
}

/**
 * "Quando posso pagar X?": o primeiro dia em que tirar [amountCents] do saldo não deixa nenhum
 * dia seguinte negativo ([firstViable]) e o primeiro em que ainda sobra a margem
 * ([firstWithMargin]). Olha a projeção inteira depois do dia, não só o dia do pagamento.
 */
data class PaymentWindow(
    val amountCents: Long,
    val marginCents: Long,
    val firstViable: LocalDate?,
    val firstWithMargin: LocalDate?,
    /** Menor saldo depois de pagar no primeiro dia com margem (ou viável), e quando. */
    val lowestAfter: DayBalance?,
    val horizonEnd: LocalDate,
    val truncated: Boolean,
) {
    companion object {
        fun of(projection: CashFlowProjection, amountCents: Long, marginCents: Long, notBefore: LocalDate = projection.today): PaymentWindow {
            val s = projection.series
            // Menor saldo de cada dia em diante.
            val suffix = LongArray(s.size)
            for (i in s.indices.reversed()) suffix[i] = if (i == s.lastIndex) s[i].cents else minOf(s[i].cents, suffix[i + 1])
            fun first(floor: Long): Int = s.indices.firstOrNull { !s[it].date.isBefore(notBefore) && suffix[it] - amountCents >= floor } ?: -1
            val viable = first(0)
            val safe = first(marginCents)
            val pick = if (safe >= 0) safe else viable
            val lowest = if (pick >= 0) s.drop(pick).minByOrNull { it.cents }?.let { DayBalance(it.date, it.cents - amountCents) } else null
            return PaymentWindow(
                amountCents, marginCents, s.getOrNull(viable)?.date, s.getOrNull(safe)?.date, lowest, projection.end, projection.truncated,
            )
        }
    }
}
