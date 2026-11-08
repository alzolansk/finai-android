package com.finai.app.domain.balance

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CashFlowTimeline
import com.finai.app.domain.CycleSource
import com.finai.app.domain.TimelineMovement
import com.finai.app.domain.toLocalDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Regra 1 do planning-v2: o **saldo em conta hoje** é a soma de tudo o que já foi efetivado (receita
 * recebida, gasto feito, conta paga, aporte, ajuste de conciliação), sem recomeçar no 1º dia do
 * mês nem no dia do salário. A sobra de outubro aparece em novembro.
 *
 * O ajuste de conciliação ([com.finai.app.domain.ADJUSTMENT_TYPE]) já é um lançamento com sinal
 * dentro dessa soma: ele reancora o saldo no valor do banco sem que a conta precise saber qual
 * foi a conciliação mais recente. Excluir o ajuste volta ao saldo de antes.
 *
 * Sem regra própria de movimento: tudo vem de [CashFlowTimeline.monthMovements], as mesmas regras
 * do mês ([com.finai.app.domain.MonthCashFlow]) que a Agenda usa. Aqui só se decide o que conta
 * como efetivado e desde quando.
 */
object AccountBalance {
    /** Quanto o saldo olha para trás: o histórico mais antigo que isso não é somado. */
    const val MAX_LOOKBACK_MONTHS = 24L

    /**
     * O primeiro mês somado: o do lançamento ou conta mais antigo (limitado a [MAX_LOOKBACK_MONTHS]).
     * Conta em aberto também conta: é ela que mostra um pagamento atrasado de meses atrás.
     * Sem nenhum registro, o mês de hoje.
     */
    fun startMonthFor(transacoes: List<TransacaoEntity>, contas: List<ContaEntity>, today: LocalDate): YearMonth {
        val current = YearMonth.from(today)
        val oldest = (transacoes.map { YearMonth.from(it.data.toLocalDate()) } +
            contas.map { YearMonth.from(it.vencimento.toLocalDate()) })
            .filter { it <= current }
            .minOrNull() ?: return current
        return maxOf(oldest, current.minusMonths(MAX_LOOKBACK_MONTHS))
    }

    /** Já efetivado: lançamento com data até hoje, ou conta paga/recebida. Parcela em aberto nunca. */
    fun settled(m: TimelineMovement, today: LocalDate): Boolean = when (val s = m.source) {
        is CycleSource.Conta -> s.conta.status == "pago"
        is CycleSource.Parcela -> false
        else -> !m.date.isAfter(today)
    }

    /** Saldo em conta no fim de hoje. */
    fun todayCents(
        contas: List<ContaEntity>,
        transacoes: List<TransacaoEntity>,
        dividas: List<DividaEntity>,
        today: LocalDate,
    ): Long {
        val current = YearMonth.from(today)
        val start = startMonthFor(transacoes, contas, today)
        val months = ChronoUnit.MONTHS.between(start, current).toInt() + 1
        return CashFlowTimeline.monthMovements(contas, transacoes, dividas, today, months, from = start)
            .filter { settled(it, today) }
            .sumOf { it.cents }
    }
}
