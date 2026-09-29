package com.finai.app.domain.scenario

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.PayCycle
import com.finai.app.domain.TransactionType
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Um período da projeção: um ciclo entre salários (quando há renda principal) ou um mês.
 * [floorCents] é o menor saldo de fim de dia do período, a mesma medida do "Livre até o
 * salário" ([PayCycle.floor]); negativo = alguma saída fica sem dinheiro.
 */
data class PeriodState(
    val start: LocalDate,
    /** Último dia do período (véspera do salário, ou fim do mês). */
    val end: LocalDate,
    val incomeCents: Long,
    val floorCents: Long,
    val floorDate: LocalDate,
    /** Entradas − saídas do período inteiro (a sobra do ciclo). */
    val endBalanceCents: Long,
    /** O salário deste período não está lançado: o app repetiu o último como estimativa. */
    val estimatedIncome: Boolean = false,
    /** Parcelas de dívida já existentes que vencem no período. */
    val debtInstallmentsCents: Long = 0,
)

/**
 * Leva o fluxo central adiante, período por período, para dizer o que acontece nos ciclos
 * seguintes — [PayCycle] sozinho só responde pelo ciclo de hoje.
 *
 * Usa o próprio [PayCycle.of] em cada ciclo futuro, com "hoje" no dia do salário que abre o
 * ciclo. Para isso o retrato é "rolado" até lá, sem gravar nada: conta pendente que venceu
 * antes vira paga (já foi contada no ciclo dela), parcela de dívida que venceu antes sai da
 * projeção ([DebtSchedule.afterPayment]) e, se o salário do ciclo não está lançado (quem lança
 * o salário avulso, mês a mês), entra uma cópia em memória do último, marcada como estimada —
 * a mesma estimativa que a [com.finai.app.domain.SavingsProjection] já faz por mês.
 *
 * Sem renda principal não há ciclo: os períodos são meses, com a mesma conta do [MonthCashFlow].
 * Como no ciclo, a sobra de um período não passa para o seguinte.
 */
object PeriodProjection {

    /** Mais do que isso vira ruído (e parcelamento maior que 12x é raro para compra do dia a dia). */
    const val MAX_PERIODS = 13

    fun hasCycle(s: FinanceSnapshot, today: LocalDate): Boolean =
        runCatching { PayCycle.of(s.contas, s.transacoes, s.dividas, today) }.getOrNull() != null

    /** Períodos de hoje até o que contém [until], com [extra] (o cenário) somado aos lançamentos. */
    fun periods(s: FinanceSnapshot, extra: List<TransacaoEntity>, today: LocalDate, until: LocalDate): List<PeriodState> =
        cycles(s, extra, today, until) ?: months(s, extra, today, until)

    /** Datas de início dos ciclos (o dia de cada salário) — iguais com ou sem o cenário. */
    fun nextPayday(s: FinanceSnapshot, today: LocalDate): LocalDate? =
        runCatching { PayCycle.of(s.contas, s.transacoes, s.dividas, today) }.getOrNull()?.proximo

    private fun cycles(s: FinanceSnapshot, extra: List<TransacaoEntity>, today: LocalDate, until: LocalDate): List<PeriodState>? {
        val first = PayCycle.of(s.contas, s.transacoes + extra, s.dividas, today) ?: return null
        val out = mutableListOf(first.toState(estimated = false, debts = debtsIn(s.dividas, first.inicio ?: today, first.proximo.minusDays(1), today)))
        val lastAvulso = PayCycle.salariesOf(s.transacoes).filterNot { it.recorrente }.maxByOrNull { it.data }
        val synthetic = mutableListOf<TransacaoEntity>()
        var prev = first
        while (!prev.proximo.isAfter(until) && out.size < MAX_PERIODS) {
            val start = prev.proximo
            val estimated = prev.proximoEstimado
            if (estimated) {
                val base = lastAvulso ?: break
                synthetic += base.copy(id = -1000L - synthetic.size, data = start.toEpochMillis())
            }
            val contas = rollContas(s.contas, start)
            val dividas = rollDebts(s.dividas, today, start)
            val c = PayCycle.of(contas, s.transacoes + synthetic + extra, dividas, start) ?: break
            if (!c.proximo.isAfter(start)) break
            out += c.toState(estimated, debtsIn(dividas, start, c.proximo.minusDays(1), start))
            prev = c
        }
        return out
    }

    private fun PayCycle.toState(estimated: Boolean, debts: Long): PeriodState {
        val f = floor
        return PeriodState(
            start = inicio ?: today,
            end = proximo.minusDays(1),
            incomeCents = entradasCents,
            floorCents = f.cents,
            floorDate = f.date,
            endBalanceCents = livreCents,
            estimatedIncome = estimated,
            debtInstallmentsCents = debts,
        )
    }

    private fun months(s: FinanceSnapshot, extra: List<TransacaoEntity>, today: LocalDate, until: LocalDate): List<PeriodState> {
        val tx = s.transacoes + extra
        val last = maxOf(YearMonth.from(until), YearMonth.from(today))
        return generateSequence(YearMonth.from(today)) { it.plusMonths(1) }
            .takeWhile { it <= last }
            .take(MAX_PERIODS)
            .map { month ->
                val from = if (month == YearMonth.from(today)) today else month.atDay(1)
                val contas = if (from == today) s.contas else rollContas(s.contas, from)
                val dividas = if (from == today) s.dividas else rollDebts(s.dividas, today, from)
                val flow = MonthCashFlow.of(contas, tx, dividas, month, from)
                val entries = buildList {
                    flow.payable.forEach { add(it.vencimento.toLocalDate() to -it.valorCentavos) }
                    flow.receivable.forEach { add(it.vencimento.toLocalDate() to it.valorCentavos) }
                    (flow.recorrentes + flow.avulsas).filter { it.tipo != TransactionType.Transferencia.name }.forEach {
                        add(it.data.toLocalDate() to if (it.tipo == TransactionType.Receita.name) it.valorCentavos else -it.valorCentavos)
                    }
                    flow.debtsDue.forEach { add(maxOf(it.vencimento ?: from, from) to -it.valorCentavos) }
                }
                var running = entries.filter { it.first.isBefore(from) }.sumOf { it.second }
                val byDay = entries.filter { !it.first.isBefore(from) }.groupBy { it.first }.toSortedMap()
                running += byDay.remove(from)?.sumOf { it.second } ?: 0L
                var floor = running
                var floorDate = from
                byDay.forEach { (d, list) ->
                    running += list.sumOf { it.second }
                    if (running < floor) { floor = running; floorDate = d }
                }
                PeriodState(
                    start = from,
                    end = month.atEndOfMonth(),
                    incomeCents = entries.filter { it.second > 0 }.sumOf { it.second },
                    floorCents = floor,
                    floorDate = floorDate,
                    endBalanceCents = entries.sumOf { it.second },
                    debtInstallmentsCents = flow.debtsDue.sumOf { it.valorCentavos },
                )
            }.toList()
    }

    /** Conta pendente que vence antes de [start] é do ciclo dela — no retrato rolado, já foi paga. */
    fun rollContas(contas: List<ContaEntity>, start: LocalDate): List<ContaEntity> = contas.map {
        if (it.status != "pago" && it.vencimento.toLocalDate().isBefore(start)) it.copy(status = "pago") else it
    }

    /** Parcelas que vencem antes de [start] saem da projeção, como se "Paguei a parcela" tivesse sido tocado. */
    fun rollDebts(dividas: List<DividaEntity>, today: LocalDate, start: LocalDate): List<DividaEntity> = dividas.map { d ->
        if (d.proximoVencimento == null) {
            // Sem vencimento a parcela fica ancorada no mês de "hoje": desconta os meses já andados.
            val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(start)).toInt()
            d.copy(parcelasRestantes = (d.parcelasRestantes - months).coerceAtLeast(0))
        } else {
            var x = d
            var guard = 0
            while (x.parcelasRestantes > 0 && x.proximoVencimento?.toLocalDate()?.isBefore(start) == true && guard++ < 600) {
                x = DebtSchedule.afterPayment(x, start)
            }
            x
        }
    }

    private fun debtsIn(dividas: List<DividaEntity>, start: LocalDate, end: LocalDate, today: LocalDate): Long =
        generateSequence(YearMonth.from(start)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(end) }
            .flatMap { m -> DebtSchedule.installmentsInMonth(dividas, m, today).asSequence().map { m to it } }
            .filter { (m, p) -> (p.vencimento ?: maxOf(m.atDay(1), start)).let { !it.isBefore(start) && !it.isAfter(end) } }
            .sumOf { it.second.valorCentavos }
}
