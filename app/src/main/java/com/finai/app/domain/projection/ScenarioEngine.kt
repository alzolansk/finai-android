package com.finai.app.domain.projection

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CycleSource
import com.finai.app.domain.TransactionType
import com.finai.app.domain.balance.DayBalance
import java.time.LocalDate
import java.time.YearMonth

/** Uma hipótese do cenário. Tudo vira movimento em memória sobre a projeção base. */
sealed interface ScenarioEvent {
    /** Entrada (positivo) ou saída (negativo) num dia. */
    data class OneOff(val date: LocalDate, val cents: Long, val label: String) : ScenarioEvent

    /** Compra parcelada: [count] saídas mensais de [eachCents], a primeira em [first]. */
    data class Installments(val first: LocalDate, val count: Int, val eachCents: Long, val label: String) : ScenarioEvent

    /** Valor que se repete todo mês por [months] meses, a partir de [from] (negativo = gasto novo). */
    data class Monthly(val from: LocalDate, val months: Int, val cents: Long, val label: String) : ScenarioEvent

    /** A renda recorrente muda em [percent] (−20 = cai 20%) ou em [cents] por recebimento, por [months] meses. */
    data class IncomeChange(val percent: Int?, val cents: Long?, val months: Int) : ScenarioEvent

    /** Quitar a dívida em [date] por [amountCents]: as parcelas seguintes deixam de existir. */
    data class PayOffDebt(val debtId: Long, val amountCents: Long, val date: LocalDate) : ScenarioEvent
}

enum class ScenarioVerdict(val label: String) {
    Fits("Cabe"), Tight("Apertado"), DoesNotFit("Não cabe"),
    /** O plano base já ficava negativo: o problema existe sem a decisão nova. */
    AlreadyShort("Já faltava antes"),
}

data class ScenarioMonth(val month: YearMonth, val beforeCents: Long, val afterCents: Long)

/**
 * Cenário comparado com a base, no mesmo ponto de partida e pelas mesmas regras. [months] traz o
 * menor saldo de cada mês antes e depois; o veredito olha o pior dia de todo o horizonte.
 */
data class ScenarioResult(
    val base: CashFlowProjection,
    val after: CashFlowProjection,
    val marginCents: Long,
    val months: List<ScenarioMonth>,
) {
    val lowestAfter: DayBalance get() = DayBalance(after.lowest.date, after.lowest.cents)
    val lowestBefore: DayBalance get() = DayBalance(base.lowest.date, base.lowest.cents)

    val verdict: ScenarioVerdict
        get() = when {
            after.lowest.cents >= 0 && after.lowest.cents >= marginCents -> ScenarioVerdict.Fits
            after.lowest.cents >= 0 -> ScenarioVerdict.Tight
            base.lowest.cents < 0 && after.lowest.cents >= base.lowest.cents -> ScenarioVerdict.AlreadyShort
            base.lowest.cents < 0 -> ScenarioVerdict.AlreadyShort
            else -> ScenarioVerdict.DoesNotFit
        }

    /** Quanto o cenário tira (negativo) ou acrescenta ao pior dia. */
    val lowestDeltaCents: Long get() = after.lowest.cents - base.lowest.cents

    /** Efeito médio por mês nos primeiros seis meses: o que muda no "cabe guardar". */
    val monthlyDeltaCents: Long
        get() {
            val window = after.movements.filter { it.source == null }.filter { !it.date.isAfter(base.today.plusMonths(6)) }.sumOf { it.cents }
            return window / 6
        }
}

object ScenarioEngine {
    fun run(
        contas: List<ContaEntity>,
        transacoes: List<TransacaoEntity>,
        dividas: List<DividaEntity>,
        today: LocalDate,
        end: LocalDate,
        marginCents: Long,
        events: List<ScenarioEvent>,
        provision: VariableProvision? = null,
    ): ScenarioResult {
        val base = CashFlowProjection.of(contas, transacoes, dividas, today, end, provision = provision)
        val paidOff = events.filterIsInstance<ScenarioEvent.PayOffDebt>().map { it.debtId }.toSet()
        val extra = events.flatMap { e ->
            when (e) {
                is ScenarioEvent.OneOff -> listOf(ExtraMovement(e.date, e.cents, e.label))
                is ScenarioEvent.Installments -> (0 until e.count.coerceIn(1, 60)).map { i ->
                    ExtraMovement(monthly(e.first, i), -e.eachCents, "${e.label} · parcela ${i + 1} de ${e.count}")
                }
                is ScenarioEvent.Monthly -> (0 until e.months.coerceIn(1, 60)).map { i -> ExtraMovement(monthly(e.from, i), e.cents, e.label) }
                is ScenarioEvent.IncomeChange -> {
                    val until = today.plusMonths(e.months.coerceIn(1, 60).toLong())
                    base.movements.filter { m ->
                        val t = (m.source as? CycleSource.Lancamento)?.transacao
                        m.cents > 0 && t != null && t.tipo == TransactionType.Receita.name && !t.extra && (t.recorrente || t.rendaPrincipal) && !m.date.isAfter(until)
                    }.map { m ->
                        val delta = e.cents ?: (m.cents * (e.percent ?: 0) / 100)
                        ExtraMovement(m.date, delta.coerceAtLeast(-m.cents), "Mudança na renda")
                    }
                }
                is ScenarioEvent.PayOffDebt -> {
                    val debt = dividas.firstOrNull { it.id == e.debtId }
                    // As parcelas que venceriam antes da quitação continuam sendo pagas.
                    val before = base.movements.filter { m -> (m.source as? CycleSource.Parcela)?.parcela?.divida?.id == e.debtId && m.date.isBefore(e.date) }
                        .map { ExtraMovement(it.date, it.cents, it.descricao) }
                    before + ExtraMovement(e.date, -e.amountCents, "Quitação" + (debt?.let { " · ${it.nome}" } ?: ""))
                }
            }
        }
        val remaining = dividas.filter { it.id !in paidOff }
        val after = CashFlowProjection.of(contas, transacoes, remaining, today, end, extra, provision)
        val before = base.monthLows().toMap()
        val months = after.monthLows().map { (m, low) -> ScenarioMonth(m, before[m]?.cents ?: base.accountCents, low.cents) }
        return ScenarioResult(base, after, marginCents, months)
    }

    /** Mesmo dia do mês, [i] meses depois; dia 29–31 cai no último dia quando o mês é mais curto. */
    private fun monthly(first: LocalDate, i: Int): LocalDate {
        val m = YearMonth.from(first).plusMonths(i.toLong())
        return m.atDay(first.dayOfMonth.coerceAtMost(m.lengthOfMonth()))
    }
}
