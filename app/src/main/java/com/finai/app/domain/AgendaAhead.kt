package com.finai.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * As linhas da Agenda "A seguir" (planning-v2, fase 4, quadro `v2/Agenda`), em ordem, a partir de
 * um [AgendaBalance] já calculado. Só decide **o que aparece e onde**: marca de hoje com o saldo em
 * conta, divisor de mês "começa com R$ X · nada zera", cabeçalho do dia da renda, o menor saldo
 * antes da próxima renda e o estado de cada linha. Nenhum número novo é calculado aqui.
 */
object AgendaAhead {

    sealed interface Row {
        /** "HOJE · DOM 08/11" + "em conta R$ X". */
        data class Today(val date: LocalDate, val accountCents: Long) : Row
        /** Virada de mês: o saldo atravessa, não zera. */
        data class MonthStart(val month: YearMonth, val startCents: Long) : Row
        /** Cabeçalho do dia em que cai a renda ("SEX 20/11 · adiantamento"). */
        data class IncomeDay(val date: LocalDate, val label: String) : Row
        data class Move(
            val movement: TimelineMovement,
            val kind: MoveKind,
            /** Onde o saldo chega ao menor valor antes da próxima renda (o mesmo da Hoje). */
            val lowest: Boolean = false,
        ) : Row
    }

    enum class MoveKind {
        /** Ainda vai acontecer: entra no saldo, com "fica R$ X". */
        Counted,
        /** Conta já paga/recebida antes do vencimento: já está no saldo de hoje; tocar reabre. */
        SettledAhead,
        /** Entrada atrasada ainda não recebida: aparece hoje, mas não entra no saldo. */
        Unconfirmed,
    }

    fun rows(balance: AgendaBalance): List<Row> {
        val today = balance.today
        val out = mutableListOf<Row>(Row.Today(today, balance.todayCents))
        balance.unconfirmedInflows.forEach { out += Row.Move(it.copy(date = today, movedFrom = it.date), MoveKind.Unconfirmed) }

        val low = balance.lowestBeforeIncome
        // Marca a última linha do dia mais apertado, só se o saldo de fato desce abaixo do de hoje.
        val lowestMove = if (low.cents < balance.todayCents) balance.future.lastOrNull { it.date == low.date } else null

        val merged = (balance.future.map { it to MoveKind.Counted } + balance.settledAhead.map { it to MoveKind.SettledAhead })
            .sortedWith(compareBy<Pair<TimelineMovement, MoveKind>> { it.first.date }.thenByDescending { it.first.cents })
        var running = balance.todayCents
        var month = YearMonth.from(today)
        merged.forEach { (m, kind) ->
            val mMonth = YearMonth.from(m.date)
            if (mMonth != month) {
                out += Row.MonthStart(mMonth, running)
                month = mMonth
            }
            if (kind == MoveKind.Counted && m.cents > 0 && m.date != today && isIncomeDay(m)) {
                out += Row.IncomeDay(m.date, m.descricao.ifBlank { "renda" }.lowercase())
            }
            out += Row.Move(m, kind, lowest = m === lowestMove)
            if (kind == MoveKind.Counted) running = m.balanceAfterCents ?: running
        }
        return out
    }

    /** Renda que abre um período: a principal ou uma parte do salário dividido. */
    private fun isIncomeDay(m: TimelineMovement): Boolean {
        val t = (m.source as? CycleSource.Lancamento)?.transacao ?: return false
        return PayCycle.isMainIncome(t) || t.rendaParte != null
    }
}

/**
 * A aba do mês da Agenda (fase 4): os movimentos do mês pelas regras de [MonthCashFlow] (via
 * [CashFlowTimeline.monthMovements], as mesmas da "A seguir"), mais as transferências entre
 * contas, que não mexem no saldo mas precisam aparecer para editar ou excluir.
 */
object AgendaMonth {
    fun movements(
        contas: List<com.finai.app.data.local.entity.ContaEntity>,
        transacoes: List<com.finai.app.data.local.entity.TransacaoEntity>,
        dividas: List<com.finai.app.data.local.entity.DividaEntity>,
        month: YearMonth,
        today: LocalDate,
    ): List<TimelineMovement> {
        val flow = MonthCashFlow.of(contas, transacoes, dividas, month, today)
        val transfers = (flow.recorrentes + flow.avulsas).filter { it.freeCashSign == 0 }.map { t ->
            val d = t.data.toLocalDate()
            TimelineMovement(
                d, t.descricao, 0, if (d.isAfter(today)) MovementStatus.Expected else MovementStatus.Done,
                balanceAfterCents = null, source = CycleSource.Lancamento(t),
            )
        }
        return (CashFlowTimeline.monthMovements(contas, transacoes, dividas, today, 1, from = month) + transfers)
            .sortedWith(compareBy<TimelineMovement> { it.date }.thenByDescending { it.cents })
    }
}
