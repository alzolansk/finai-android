package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.balance.AccountBalance
import com.finai.app.domain.balance.NextIncomeFinder
import java.time.LocalDate
import java.time.YearMonth

/** Cor do saldo corrente de um item da Agenda: cinza normal, âmbar abaixo de R$ 1.000, vermelho se negativo. */
enum class BalanceTone { Normal, Low, Negative }

fun balanceTone(cents: Long): BalanceTone = when {
    cents < 0 -> BalanceTone.Negative
    cents < AgendaBalance.LOW_BALANCE_CENTS -> BalanceTone.Low
    else -> BalanceTone.Normal
}

/**
 * Agenda com foco no futuro: o que já aconteceu no mês fica à parte ([past]) e o que ainda vai
 * acontecer vem em ordem ([future]), cada item com o saldo corrente depois dele. O saldo
 * atravessa os meses sem recomeçar.
 *
 * **Sem conta própria:** os movimentos vêm de [CashFlowTimeline.monthMovements], as mesmas
 * regras do mês ([MonthCashFlow]). O saldo de hoje é o contínuo de [AccountBalance] (planning-v2,
 * regra 1) e o que vem depois segue as regras do livre ([com.finai.app.domain.balance.FreeToSpend]),
 * para o "fica R$ X" e o menor saldo da Agenda baterem com a Hoje (fase 4):
 * - pagamento atrasado, de qualquer mês, cai **hoje** (`movedFrom` = vencimento);
 * - entrada atrasada (ou conta a receber de hoje ainda não recebida) aparece, mas **não entra no
 *   saldo** ([unconfirmedInflows]) até ser marcada recebida;
 * - conta paga (ou recebida) adiantada já está no saldo de hoje e fica no lugar dela ([settledAhead]),
 *   para tocar de novo e reabrir.
 */
data class AgendaBalance(
    val today: LocalDate,
    /** Já efetivados no mês de hoje (pagos, recebidos, lançados até hoje), exceto contas que vencem de hoje em diante. */
    val past: List<TimelineMovement>,
    /** O que ainda vai acontecer, com o saldo depois de cada um. */
    val future: List<TimelineMovement>,
    /** Saldo de hoje: tudo o que já foi efetivado desde o início da contagem ([startMonth]). */
    val todayCents: Long,
    /** Saldo com que cada mês do [future] começa. */
    val monthStart: Map<YearMonth, Long>,
    /** Saldo no último dia do mês seguinte. */
    val endOfNextMonthCents: Long,
    /** De onde o saldo é contado: o mês do lançamento mais antigo ([AccountBalance.startMonthFor]). */
    val startMonth: YearMonth = YearMonth.from(today),
    /** Menor saldo previsto de hoje até o fim do horizonte, e o dia dele. */
    val lowest: LowestBalance = LowestBalance(todayCents, today),
    /** Saldo no último dia de cada mês, do mês de hoje em diante (o saldo de um é o inicial do seguinte). */
    val monthEnds: List<MonthEnd> = emptyList(),
    /** Contas já pagas/recebidas que vencem de hoje em diante: já estão em [todayCents]. */
    val settledAhead: List<TimelineMovement> = emptyList(),
    /** Entradas atrasadas ainda não recebidas: aparecem hoje, sem mexer no saldo. */
    val unconfirmedInflows: List<TimelineMovement> = emptyList(),
    /** Próxima renda (regra 3): fecha a janela do [lowestBeforeIncome]. */
    val nextIncomeDate: LocalDate? = null,
    /**
     * Menor saldo de fim de dia de hoje até a véspera da próxima renda: o mesmo "menor saldo" da
     * Hoje. Sem renda, o menor do horizonte.
     */
    val lowestBeforeIncome: LowestBalance = lowest,
) {
    companion object {
        const val LOW_BALANCE_CENTS = 100_000L
        /** Meses mostrados: o atual e os três seguintes. */
        const val HORIZON_MONTHS = 4

        /** De onde o saldo é contado: [AccountBalance.startMonthFor], o mesmo do "livre" (Fase 1). */
        fun startMonthFor(transacoes: List<TransacaoEntity>, contas: List<ContaEntity>, today: LocalDate): YearMonth =
            AccountBalance.startMonthFor(transacoes, contas, today)

        fun build(
            transacoes: List<TransacaoEntity>,
            contas: List<ContaEntity>,
            dividas: List<DividaEntity>,
            today: LocalDate,
            months: Int = HORIZON_MONTHS,
        ): AgendaBalance {
            val current = YearMonth.from(today)
            val start = startMonthFor(transacoes, contas, today)
            val span = (start.until(current, java.time.temporal.ChronoUnit.MONTHS)).toInt() + months
            val all = CashFlowTimeline.monthMovements(contas, transacoes, dividas, today, span, from = start)
            val (settled, open) = all.partition { AccountBalance.settled(it, today) }
            val todayCents = settled.sumOf { it.cents }

            // Mesmas regras do livre: pagamento atrasado cai hoje; entrada atrasada não conta.
            val (counted, unconfirmed) = open
                .map { m -> if (m.date.isBefore(today) && m.cents < 0) m.copy(date = today, movedFrom = m.date) else m }
                .partition { m -> !(m.cents > 0 && (m.date.isBefore(today) || (m.date == today && m.source is CycleSource.Conta))) }
            var running = todayCents
            val future = counted
                .sortedWith(compareBy<TimelineMovement> { it.date }.thenByDescending { it.cents })
                .map { m -> running += m.cents; m.copy(balanceAfterCents = running) }

            val starts = linkedMapOf<YearMonth, Long>()
            var before = todayCents
            future.forEach { m ->
                starts.getOrPut(YearMonth.from(m.date)) { before }
                before = m.balanceAfterCents ?: before
            }
            val horizon = (0 until months).map { current.plusMonths(it.toLong()) }
            // Saldo no último dia de cada mês: o último movimento até ali, ou o saldo que veio do mês anterior.
            val ends = horizon.map { m ->
                val last = future.lastOrNull { YearMonth.from(it.date) <= m }
                MonthEnd(m, last?.balanceAfterCents ?: todayCents)
            }
            val next = current.plusMonths(1)
            val endNext = ends.firstOrNull { it.month == next }?.cents ?: todayCents
            val inHorizon = future.filter { YearMonth.from(it.date) <= horizon.last() }
            val low = lowestOf(todayCents, today, inHorizon)

            val nextIncome = runCatching { NextIncomeFinder.find(contas, transacoes, dividas, today) }.getOrNull()
            val lowBeforeIncome = nextIncome
                ?.let { income -> lowestOf(todayCents, today, inHorizon.filter { it.date.isBefore(income.date) }) }
                ?: low

            // Conta paga adiantada (ou paga hoje, vencendo hoje) continua no lugar dela; o resto do
            // que foi efetivado no mês vai para o bloco recolhido.
            fun aheadConta(m: TimelineMovement) = m.source is CycleSource.Conta && !m.date.isBefore(today)
            val settledAhead = settled
                .filter { aheadConta(it) && YearMonth.from(it.date) <= horizon.last() }
                .map { it.copy(balanceAfterCents = null) }
            val shownPast = settled
                .filter { YearMonth.from(it.date) == current && !aheadConta(it) }
                .map { it.copy(balanceAfterCents = null) }
            return AgendaBalance(
                today, shownPast, future, todayCents, starts, endNext, start, low, ends,
                settledAhead = settledAhead,
                unconfirmedInflows = unconfirmed.map { it.copy(balanceAfterCents = null) },
                nextIncomeDate = nextIncome?.date,
                lowestBeforeIncome = lowBeforeIncome,
            )
        }

        /** Menor saldo de fim de dia; empate fica com o primeiro dia. Hoje conta mesmo sem movimento. */
        private fun lowestOf(todayCents: Long, today: LocalDate, moves: List<TimelineMovement>): LowestBalance {
            val dayEnds = moves.groupBy { it.date }.map { (d, ms) -> LowestBalance(ms.last().balanceAfterCents ?: todayCents, d) }
            val todayEnd = dayEnds.firstOrNull { it.date == today } ?: LowestBalance(todayCents, today)
            return (listOf(todayEnd) + dayEnds.filter { it.date != today })
                .minWith(compareBy<LowestBalance> { it.cents }.thenBy { it.date })
        }
    }
}

/** Saldo no último dia de um mês. */
data class MonthEnd(val month: YearMonth, val cents: Long)

/** O menor saldo previsto e o dia em que acontece. */
data class LowestBalance(val cents: Long, val date: LocalDate)
