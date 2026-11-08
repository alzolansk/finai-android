package com.finai.app.domain.balance

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CashFlowTimeline
import com.finai.app.domain.CycleFloor
import com.finai.app.domain.CycleShortfall
import com.finai.app.domain.CycleSource
import com.finai.app.domain.TimelineMovement
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Saldo previsto no fim de um dia. */
data class DayBalance(val date: LocalDate, val cents: Long)

/**
 * Regra 2 do planning-v2: o **livre** até a véspera da próxima renda.
 *
 * ```
 * saldoDia(d) = em conta hoje + tudo que ainda vai acontecer até o fim de d
 * livre       = menor saldoDia (de hoje até a véspera) − margem de segurança
 * por dia     = livre ÷ dias até a próxima renda
 * ```
 *
 * O saldo é o contínuo ([AccountBalance]): a sobra do período anterior **não zera** no salário.
 * Os movimentos são os mesmos da Agenda ([CashFlowTimeline.monthMovements]): fatura pelo
 * vencimento, conta paga adiantada já está no saldo, aporte é saída, transferência entre contas é
 * neutra. Conta a pagar vencida e não paga sai hoje; entrada atrasada (ainda não recebida) não
 * conta, porque não há como saber quando cai.
 *
 * Falta: se o saldo previsto fica **negativo** em algum dia, o livre é zero e [shortfall] diz
 * quanto, quando e por quê. Ficar entre zero e a margem não é falta: o livre é zero e
 * [insideMargin] avisa que a margem está sendo usada.
 *
 * Recomendação do plano (dívida, metas) **nunca** diminui o livre: ele só muda quando o
 * pagamento ou o aporte é registrado.
 */
data class FreeToSpend(
    val today: LocalDate,
    /** Saldo em conta hoje ([AccountBalance]): o que já foi efetivado. */
    val accountCents: Long,
    val nextIncome: NextIncome,
    /** Último dia coberto: a véspera da próxima renda. */
    val windowEnd: LocalDate,
    /** Dias a partir de hoje em que o dinheiro precisa durar (o dia da renda já é do próximo período). */
    val days: Int,
    val marginCents: Long,
    /** Menor saldo previsto no período, e o dia em que acontece. */
    val lowest: CycleFloor,
    /** Saldo no fim de cada dia, de hoje até [windowEnd]. */
    val series: List<DayBalance>,
    /** O que ainda vai acontecer até [windowEnd], em ordem, com o saldo depois de cada movimento. */
    val movements: List<TimelineMovement>,
    val shortfall: CycleShortfall?,
) {
    /** O livre: menor saldo previsto menos a margem; nunca negativo. Zero com falta. */
    val freeCents: Long get() = if (shortfall != null) 0 else (lowest.cents - marginCents).coerceAtLeast(0)

    /** Quanto dá por dia, em centavos inteiros (arredonda para baixo). */
    val perDayCents: Long get() = freeCents / days.coerceAtLeast(1)

    /** "Vai sair": o quanto o saldo de hoje desce até o dia mais apertado. */
    val goingOutCents: Long get() = (accountCents - lowest.cents).coerceAtLeast(0)

    /** Saídas previstas (ainda não pagas) no período, somadas. */
    val outflowsCents: Long get() = movements.filter { it.cents < 0 }.sumOf { -it.cents }

    /** Entradas previstas (ainda não recebidas) no período, somadas. */
    val inflowsCents: Long get() = movements.filter { it.cents > 0 }.sumOf { it.cents }

    /** Saldo previsto na véspera da próxima renda. */
    val endCents: Long get() = series.lastOrNull()?.cents ?: accountCents

    /** Quanto o período ainda ganha depois do dia mais apertado: entradas que ainda não caíram. */
    val laterInflowCents: Long get() = (endCents - lowest.cents).coerceAtLeast(0)

    /** O saldo desceu abaixo da margem sem ficar negativo: não é falta, mas não sobra livre. */
    val insideMargin: Boolean get() = shortfall == null && lowest.cents < marginCents

    /** O bloco que a IA recebe: tudo calculado aqui, a IA só lê. */
    fun aiBlock(maxMovements: Int = 14): String = buildString {
        appendLine(
            "Saldo e livre calculados pelo app (o saldo é contínuo: a sobra do período anterior não zera no salário). " +
                "Não é saldo de banco; é a soma do que foi lançado e já efetivado.",
        )
        appendLine("- Em conta hoje (${dm(today)}): ${signed(accountCents)}.")
        appendLine(
            "- Próxima renda: ${nextIncome.noun} de ${brl(nextIncome.cents)} em ${dm(nextIncome.date)}" +
                (if (nextIncome.estimated) " (data estimada)" else "") +
                ". O dinheiro precisa durar até ${dm(windowEnd)} ($days dia(s)).",
        )
        if (movements.isEmpty()) {
            appendLine("- Nada previsto até lá.")
        } else {
            appendLine("- Até lá, em ordem (saldo depois de cada movimento):")
            movements.take(maxMovements).forEach {
                appendLine("  ${dm(it.date)} ${clean(it.descricao)}: ${signed(it.cents)} → saldo ${signed(it.balanceAfterCents ?: 0)}")
            }
            if (movements.size > maxMovements) appendLine("  (mais ${movements.size - maxMovements} movimentos)")
        }
        appendLine("- Menor saldo previsto: ${signed(lowest.cents)} em ${dm(lowest.date)}.")
        appendLine("- Margem de segurança (separada, não é livre): ${brl(marginCents)}.")
        val falta = shortfall
        if (falta != null) {
            appendLine("- Falta prevista: ${brl(falta.cents)} a partir de ${dm(falta.date)}" + (falta.causa?.let { " (${clean(it)})" } ?: "") + ". Não há livre.")
        } else {
            appendLine(
                "- Livre até ${dm(windowEnd)}: ${brl(freeCents)}, cerca de ${brl(perDayCents)} por dia." +
                    if (insideMargin) " (O saldo desce para dentro da margem.)" else "",
            )
        }
        append(
            "Regras: um gasto novo hoje cabe se não passar do livre. A recomendação do plano não desconta do livre; " +
                "o valor só diminui quando o pagamento ou o aporte é registrado.",
        )
    }

    companion object {
        /** Margem de segurança padrão (decisão de 08/11/2026): R$ 300 fixos, ajustável. */
        const val DEFAULT_MARGIN_CENTS = 30_000L

        /** Teto de dias do período, para uma data estimada errada não gerar uma série enorme. */
        private const val MAX_DAYS = 62L

        fun build(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            today: LocalDate,
            nextIncome: NextIncome,
            marginCents: Long = DEFAULT_MARGIN_CENTS,
        ): FreeToSpend {
            val windowEnd = maxOf(minOf(nextIncome.date.minusDays(1), today.plusDays(MAX_DAYS)), today)
            val start = AccountBalance.startMonthFor(transacoes, contas, today)
            val months = ChronoUnit.MONTHS.between(start, YearMonth.from(windowEnd)).toInt() + 1
            val all = CashFlowTimeline.monthMovements(contas, transacoes, dividas, today, months, from = start)

            val account = all.filter { AccountBalance.settled(it, today) }.sumOf { it.cents }
            // O que ainda vai acontecer: pagamento atrasado cai hoje; entrada atrasada não conta.
            val ahead = all.filterNot { AccountBalance.settled(it, today) }.mapNotNull { m ->
                when {
                    m.date.isAfter(windowEnd) -> null
                    m.date.isBefore(today) -> if (m.cents < 0) m.copy(date = today, movedFrom = m.date) else null
                    m.date == today && m.cents > 0 && m.source is CycleSource.Conta -> null
                    else -> m
                }
            }.sortedWith(compareBy<TimelineMovement> { it.date }.thenByDescending { it.cents })

            var running = account
            val movements = ahead.map { m -> running += m.cents; m.copy(balanceAfterCents = running) }
            val byDay = movements.groupBy { it.date }
            var level = account
            val series = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(windowEnd) }.map { d ->
                level += byDay[d]?.sumOf { it.cents } ?: 0L
                DayBalance(d, level)
            }.toList()

            val min = series.minOf { it.cents }
            // Empate: o primeiro dia em que o saldo chega ao menor valor.
            val lowest = series.first { it.cents == min }.let { CycleFloor(it.date, it.cents) }
            val shortfall = if (lowest.cents < 0) {
                val firstBelow = movements.firstOrNull { (it.balanceAfterCents ?: 0) < 0 && it.cents < 0 }
                CycleShortfall(
                    date = firstBelow?.date ?: lowest.date,
                    cents = -lowest.cents,
                    causa = firstBelow?.descricao?.ifBlank { null },
                )
            } else null

            return FreeToSpend(
                today = today,
                accountCents = account,
                nextIncome = nextIncome,
                windowEnd = windowEnd,
                days = ChronoUnit.DAYS.between(today, nextIncome.date).toInt().coerceAtLeast(1),
                marginCents = marginCents.coerceAtLeast(0),
                lowest = lowest,
                series = series,
                movements = movements,
                shortfall = shortfall,
            )
        }

        /** [build] achando a próxima renda sozinho; nulo sem nenhuma renda (o livre vai até o fim do mês). */
        fun of(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            today: LocalDate,
            marginCents: Long = DEFAULT_MARGIN_CENTS,
        ): FreeToSpend? {
            val next = NextIncomeFinder.find(contas, transacoes, dividas, today) ?: return null
            return build(contas, transacoes, dividas, today, next, marginCents)
        }

        private fun brl(cents: Long) = formatBrl0(cents / 100.0)
        private fun signed(cents: Long) = if (cents < 0) "-" + brl(-cents) else brl(cents)
        private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
        private fun clean(text: String) = text.replace(Regex("""\d{4,}"""), "").replace(Regex("""\s+"""), " ").trim().take(32)
    }
}
