package com.finai.app.domain.assistant

import com.finai.app.domain.projection.CashFlowProjection
import com.finai.app.domain.projection.VariableProvision
import java.time.YearMonth

/** Aporte pontual: tira o valor na data pedida e protege os dias seguintes e a margem. */
internal object SavingsAnswer {
    fun run(s: AssistantSnapshot, args: Map<String, String>): ToolResult {
        val requested = args["data"]
        val date = if (requested.isNullOrBlank()) s.today else ActionPlanner.parseFutureDate(requested, s.today)
        if (date == null || date < s.today || date > s.today.plusMonths(CashFlowProjection.MAX_MONTHS)) {
            val answer = AssistantAnswer(AnswerKind.Question, "Data do aporte", headline = "Para qual data futura você quer calcular o aporte? Posso projetar até ${Fmt.date(s.today.plusMonths(CashFlowProjection.MAX_MONTHS), s.today)}.")
            return ToolResult("data do aporte", answer.plainText(), answer)
        }
        val end = minOf(YearMonth.from(date).plusMonths(3).atEndOfMonth(), s.today.plusMonths(CashFlowProjection.MAX_MONTHS))
        val provision = VariableProvision.of(s.contas, s.transacoes, s.dividas, s.today)
        val p = CashFlowProjection.of(s.contas, s.transacoes, s.dividas, s.today, end, provision = provision)
        val floor = p.lowestFrom(date)!!
        val capacity = (floor.cents - s.marginCents).coerceAtLeast(0)
        val priorGap = p.series.filter { it.date < date }.minByOrNull { it.cents }?.takeIf { it.cents < 0 }
        val available = if (priorGap == null) capacity else 0
        val whenLabel = Fmt.date(date, s.today)
        val headline = when {
            priorGap != null -> "Antes de guardar em $whenLabel, é preciso cobrir a falta prevista de ${Fmt.money(-priorGap.cents)} em ${Fmt.date(priorGap.date, s.today)}."
            provision == null -> "Com os lançamentos atuais, o teto para guardar em $whenLabel é ${Fmt.money(available)}; falta estimar seus gastos do dia a dia."
            available == 0L -> "Pela tendência atual, não há sobra para guardar em $whenLabel preservando a margem."
            else -> "Pela tendência atual, você poderia guardar até ${Fmt.money(available)} em $whenLabel."
        }
        val answer = AssistantAnswer(AnswerKind.Decision, "Quanto guardar em $whenLabel", headline = headline,
            metrics = listOf(
                AnswerMetric(if (provision == null) "Teto sem estimar o dia a dia" else "Aporte estimado", Fmt.money(available), if (available > 0 && provision != null) Tone.Good else Tone.Warn),
                AnswerMetric("Saldo previsto nessa data", Fmt.money(p.balanceOn(date))),
                AnswerMetric("Margem preservada", Fmt.money(s.marginCents)),
            ),
            evidence = listOf("Menor saldo de $whenLabel a ${Fmt.date(p.end, s.today)}, antes do aporte: ${Fmt.money(floor.cents)} em ${Fmt.date(floor.date, s.today)}.",
                "Aporte = menor saldo dos dias seguintes menos a margem, limitado a zero."),
            notes = listOf(
                provision?.let { "Inclui ${Fmt.money(it.monthlyAverageCents)}/mês para gastos do dia a dia, pela média de ${it.sampleMonths} meses. É uma estimativa, não uma garantia." }
                    ?: "Sem pelo menos dois meses de gastos para estimar o dia a dia. Esse teto pode diminuir; não é uma recomendação de aporte.",
                "Considerei contas, faturas, parcelas, receitas previstas e lançamentos até ${Fmt.date(p.end, s.today)}. Entradas futuras precisam se confirmar.",
                "Reserva já guardada não entra como dinheiro disponível. Outros aportes planejados ainda não registrados não foram descontados.",
                "Esta é uma consulta: nenhum aporte foi registrado.",
            ))
        return ToolResult("capacidade de aporte em $whenLabel", answer.plainText(), answer)
    }
}
