package com.finai.app.domain.scenario

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CashFlowTimeline
import com.finai.app.domain.CycleSource
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.balance.AccountBalance
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToLong

enum class PurchaseVerdictV2(val label: String) { Cabe("Cabe"), Apertado("Apertado"), NaoCabe("Não cabe") }

/** Uma forma de pagar: 0 parcelas = Pix/débito hoje; N ≥ 1 = N× no cartão. */
data class PurchaseWay(val installments: Int) {
    val isPix: Boolean get() = installments == 0
    val label: String get() = if (isPix) "Pix" else "${installments}×"
}

data class MonthLow(val month: YearMonth, val withoutCents: Long, val withCents: Long)

data class PurchaseOption(
    val way: PurchaseWay,
    /** O que a compra custa nesta forma (com desconto no Pix, com juros no parcelado). */
    val totalCents: Long,
    val installmentCents: Long,
    val firstDue: LocalDate,
    /** Menor saldo previsto em cada mês, sem e com a compra. */
    val months: List<MonthLow>,
    val worst: MonthLow,
    val verdict: PurchaseVerdictV2,
) {
    /** Folga depois da margem no pior mês. */
    fun slackCents(marginCents: Long): Long = worst.withCents - marginCents
}

data class PurchaseSimulation(
    val valueCents: Long,
    val marginCents: Long,
    val options: List<PurchaseOption>,
    /** A forma com menos parcelas entre as que cabem; nula se nenhuma cabe. */
    val best: PurchaseWay?,
    /** Meses de atraso nas metas = valor ÷ quanto cabe guardar; igual para qualquer forma. */
    val goalDelayMonths: Int?,
) {
    fun option(way: PurchaseWay): PurchaseOption? = options.firstOrNull { it.way == way }

    fun sentence(o: PurchaseOption): String {
        val month = MONTH_ABBREV_PT[o.worst.month.monthValue - 1].lowercase()
        val low = formatBrl0(o.worst.withCents / 100.0)
        return when (o.verdict) {
            PurchaseVerdictV2.Cabe -> "O menor saldo fica em $low em $month, acima da margem."
            PurchaseVerdictV2.Apertado -> "Cabe, mas o menor saldo fica em $low em $month, perto da margem."
            PurchaseVerdictV2.NaoCabe -> if (o.worst.withCents < 0) "Faltariam ${formatBrl0(-o.worst.withCents / 100.0)} em $month."
            else "O saldo de $month ficaria em $low, abaixo da sua margem de ${formatBrl0(marginCents / 100.0)}."
        }
    }

    /** O que a IA pode citar (o guard barra qualquer número fora daqui). */
    fun factsBlock(name: String): String = buildString {
        appendLine("Simulação de compra${if (name.isNotBlank()) " ($name)" else ""}: ${formatBrl0(valueCents / 100.0)}. Margem de segurança: ${formatBrl0(marginCents / 100.0)}.")
        options.forEach { o ->
            appendLine("- ${o.way.label}: ${o.verdict.label}. ${if (o.way.installments > 1) "${o.way.installments} parcelas de ${formatBrl0(o.installmentCents / 100.0)}. " else "Total ${formatBrl0(o.totalCents / 100.0)}. "}${sentence(o)}")
        }
        best?.let { appendLine("Melhor forma (menos parcelas entre as que cabem): ${it.label}.") } ?: appendLine("Nenhuma forma cabe hoje.")
        goalDelayMonths?.let { appendLine("Efeito nas metas: cerca de $it ${if (it == 1) "mês" else "meses"} de atraso, igual em qualquer forma de pagamento.") }
    }
}

/**
 * Regra 6 (planning-v2, fase 8): projeção de 6 meses a partir do saldo em conta contínuo
 * ([AccountBalance]) com as saídas já previstas, **sem** descontar aportes planejados das metas.
 * Para cada forma de pagamento, o menor saldo de cada mês com e sem a compra.
 */
object PurchaseSimulatorV2 {
    const val HORIZON_MONTHS = 6
    const val TIGHT_CENTS = 30_000L
    val WAYS = listOf(0, 1, 3, 6, 10).map(::PurchaseWay)

    fun simulate(
        contas: List<ContaEntity>,
        transacoes: List<TransacaoEntity>,
        dividas: List<DividaEntity>,
        today: LocalDate,
        valueCents: Long,
        marginCents: Long,
        /** Vencimento da próxima fatura do cartão escolhido; nulo = um mês depois de hoje. */
        firstCardDue: LocalDate? = null,
        /** Desconto à vista, em %, só no Pix. */
        pixDiscountPercent: Double = 0.0,
        /** Juros do parcelamento, em % ao mês; 0 = sem juros. */
        monthlyInterestPercent: Double = 0.0,
        fitsToSaveCents: Long? = null,
        ways: List<PurchaseWay> = WAYS,
    ): PurchaseSimulation {
        val current = YearMonth.from(today)
        val end = current.plusMonths(HORIZON_MONTHS.toLong()).atEndOfMonth()
        val start = AccountBalance.startMonthFor(transacoes, contas, today)
        val monthsSpan = java.time.temporal.ChronoUnit.MONTHS.between(start, YearMonth.from(end)).toInt() + 1
        val all = CashFlowTimeline.monthMovements(contas, transacoes, dividas, today, monthsSpan, from = start)
        val account = all.filter { AccountBalance.settled(it, today) }.sumOf { it.cents }
        // As mesmas regras do livre: pagamento atrasado cai hoje; entrada atrasada não conta.
        val ahead = all.filterNot { AccountBalance.settled(it, today) }.mapNotNull { m ->
            when {
                m.date.isAfter(end) -> null
                m.date.isBefore(today) -> if (m.cents < 0) today to m.cents else null
                m.date == today && m.cents > 0 && m.source is CycleSource.Conta -> null
                else -> m.date to m.cents
            }
        }
        val baseByDay = ahead.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val days = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()

        fun lows(extra: Map<LocalDate, Long>): Map<YearMonth, Long> {
            var level = account
            val out = LinkedHashMap<YearMonth, Long>()
            days.forEach { d ->
                level += (baseByDay[d] ?: 0L) - (extra[d] ?: 0L)
                val m = YearMonth.from(d)
                out[m] = minOf(out[m] ?: Long.MAX_VALUE, level)
            }
            return out
        }
        val without = lows(emptyMap())
        val due = (firstCardDue?.takeIf { !it.isBefore(today) } ?: today.plusMonths(1))

        val options = ways.map { way ->
            val (total, installment, payments) = when {
                way.isPix -> {
                    val t = (valueCents * (1 - pixDiscountPercent.coerceIn(0.0, 90.0) / 100.0)).roundToLong()
                    Triple(t, t, mapOf(today to t))
                }
                else -> {
                    val n = way.installments
                    val i = monthlyInterestPercent / 100.0
                    val pmt = if (i <= 0 || n == 1) ceil(valueCents.toDouble() / n).toLong()
                    else (valueCents * i / (1 - (1 + i).pow(-n))).roundToLong()
                    val t = if (i <= 0 || n == 1) valueCents else pmt * n
                    val pays = HashMap<LocalDate, Long>()
                    (0 until n).forEach { k ->
                        val d = due.plusMonths(k.toLong()).let { if (it.isAfter(end)) end else it }
                        // Parcelas depois do horizonte pesam no último dia dele: não somem da conta.
                        pays[d] = (pays[d] ?: 0L) + if (i <= 0 && k == n - 1) valueCents - pmt * (n - 1) else pmt
                    }
                    Triple(t, pmt, pays as Map<LocalDate, Long>)
                }
            }
            val with = lows(payments)
            val months = without.keys.map { MonthLow(it, without.getValue(it), with.getValue(it)) }
            val worst = months.minByOrNull { it.withCents }!!
            val slack = worst.withCents - marginCents
            val verdict = when {
                slack < 0 -> PurchaseVerdictV2.NaoCabe
                slack < TIGHT_CENTS -> PurchaseVerdictV2.Apertado
                else -> PurchaseVerdictV2.Cabe
            }
            PurchaseOption(way, total, installment, if (way.isPix) today else due, months, worst, verdict)
        }
        val best = options.filter { it.verdict == PurchaseVerdictV2.Cabe }.minByOrNull { it.way.installments }?.way
            ?: options.filter { it.verdict == PurchaseVerdictV2.Apertado }.minByOrNull { it.way.installments }?.way
        val delay = fitsToSaveCents?.takeIf { it > 0 }?.let { ceil(valueCents.toDouble() / it).toInt() }
        return PurchaseSimulation(valueCents, marginCents, options, best, delay)
    }
}
