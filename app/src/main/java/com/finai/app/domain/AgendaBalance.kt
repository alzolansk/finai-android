package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.balance.AccountBalance
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
 * regras do mês ([MonthCashFlow]) que a Agenda mensal e a sequencial usam. Aqui só entram a
 * ordem e a soma. O saldo de hoje é o contínuo de [AccountBalance] (planning-v2, Fase 1): soma
 * tudo o que foi efetivado desde o primeiro lançamento, sem recomeçar no mês nem no salário. É o
 * "saldo do app" (com os ajustes de conciliação), não o do banco.
 */
data class AgendaBalance(
    val today: LocalDate,
    /** Já efetivados no mês de hoje (pagos, recebidos, lançados até hoje). */
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
    /** Menor saldo previsto de hoje em diante, e o dia dele. */
    val lowest: LowestBalance = LowestBalance(todayCents, today),
    /** Saldo no último dia de cada mês, do mês de hoje em diante (o saldo de um é o inicial do seguinte). */
    val monthEnds: List<MonthEnd> = emptyList(),
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
            // Mês anterior ao de hoje: só o que de fato aconteceu; conta em aberto e parcela de meses
            // passados não entram (a Agenda só olha para frente).
            val (past, ahead) = all
                .filter { AccountBalance.settled(it, today) || YearMonth.from(it.date) >= current }
                .partition { AccountBalance.settled(it, today) }
            val todayCents = past.sumOf { it.cents }
            var running = todayCents
            val future = ahead.map { m -> running += m.cents; m.copy(balanceAfterCents = running) }
            val starts = linkedMapOf<YearMonth, Long>()
            var before = todayCents
            future.forEach { m ->
                starts.getOrPut(YearMonth.from(m.date)) { before }
                before = m.balanceAfterCents ?: before
            }
            val horizon = (0 until months).map { current.plusMonths(it.toLong()) }
            // Saldo no último dia de cada mês: o último movimento até ali, ou o saldo que veio do mês anterior.
            var carry = todayCents
            val ends = horizon.map { m ->
                val last = future.lastOrNull { YearMonth.from(it.date) <= m }
                carry = last?.balanceAfterCents ?: todayCents
                MonthEnd(m, carry)
            }
            val next = current.plusMonths(1)
            val endNext = ends.firstOrNull { it.month == next }?.cents ?: todayCents
            val low = (listOf(LowestBalance(todayCents, today)) +
                future.filter { YearMonth.from(it.date) <= horizon.last() }.map { LowestBalance(it.balanceAfterCents ?: todayCents, it.date) })
                .minBy { it.cents }
            val shownPast = past.filter { YearMonth.from(it.date) == current }.map { it.copy(balanceAfterCents = null) }
            return AgendaBalance(today, shownPast, future, todayCents, starts, endNext, start, low, ends)
        }
    }
}

/** Saldo no último dia de um mês. */
data class MonthEnd(val month: YearMonth, val cents: Long)

/** O menor saldo previsto e o dia em que acontece. */
data class LowestBalance(val cents: Long, val date: LocalDate)
