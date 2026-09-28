package com.finai.app.domain

import com.finai.app.util.formatBrl0
import java.time.LocalDate

/**
 * O contexto de dívidas e metas que a IA recebe, montado a partir do que os calculators já
 * decidiram (planning.md §6: conta é local, a IA só explica).
 *
 * Antes o resumo levava só o total das dívidas ("R$ 7.655, juros R$ 337/mês") e cada leitura
 * de objetivo ia isolada, com a sobra do mês inteira. A IA tratava as dívidas como uma só
 * ("priorize a dívida que gera R$ 337 de juros" — que era a soma de todas) e mandava a mesma
 * sobra para cada meta. Aqui cada dívida vai separada, na ordem de ataque, e as metas vão
 * juntas, com a divisão da sobra que o app já fez por prioridade.
 */
object AiContext {

    /** Uma linha por dívida, na ordem de ataque (maior juro primeiro). */
    fun debtLines(plans: List<DebtPlan>, today: LocalDate = LocalDate.now()): List<String> =
        plans.map { p ->
            val d = p.divida
            val taxa = "%.1f".format(java.util.Locale("pt", "BR"), d.taxaJurosMensalBasisPoints / 100.0)
            buildString {
                append("${p.rank}. \"${d.nome}\": ${brl(d.valorAbertoCentavos)} em aberto, $taxa% ao mês")
                append(" (≈ ${brl(p.monthlyInterestCents)} de juros por mês)")
                when {
                    d.parcelasRestantes > 0 && d.valorParcelaCentavos > 0 -> {
                        append(", parcela de ${brl(d.valorParcelaCentavos)}, faltam ${d.parcelasRestantes}")
                        DebtSchedule.lastInstallmentMonth(d, today)?.let { append(", última em ${formatMonthYearShort(it.atDay(1))}") }
                    }
                    else -> append(", sem parcela fixa (o saldo só cai com pagamento extra)")
                }
                append(".")
            }
        }

    /**
     * Uma linha por meta, na ordem de prioridade, com o aporte que ela pede e a parte da sobra
     * deste mês que o app reservou para ela ([GoalPlan.monthlyContributionFundedCents]).
     */
    fun goalLines(plans: List<GoalPlan>): List<String> =
        plans.map { p ->
            val o = p.objetivo
            buildString {
                append("Prioridade ${o.prioridade}: \"${o.nome}\" (${o.tipo}), faltam ${brl(p.missingCents)} até ${p.etaLabel}")
                append("; pede ${brl(p.monthlyContributionNeededCents)}/mês")
                append("; reservado deste mês: ${brl(p.monthlyContributionFundedCents)}")
                append("; situação: ${statusLabel(p.status)}.")
            }
        }

    /** Fecha a divisão: quanto da sobra ficou sem destino depois das metas. */
    fun allocationSummary(plans: List<GoalPlan>, monthlyCapacityCents: Long): String {
        val reserved = plans.sumOf { it.monthlyContributionFundedCents }
        val free = (monthlyCapacityCents - reserved).coerceAtLeast(0)
        return "Divisão da sobra deste mês (${brl(monthlyCapacityCents.coerceAtLeast(0))}) feita pelo app, pela prioridade: " +
            "${brl(reserved)} reservados para as metas" + if (free > 0) ", ${brl(free)} sem destino." else "."
    }

    /** O bloco completo das metas, com a regra de não repetir a sobra inteira em cada meta. */
    fun goalsBlock(plans: List<GoalPlan>, monthlyCapacityCents: Long): String = buildString {
        if (plans.isEmpty()) return@buildString
        appendLine("Metas (a mesma sobra é dividida entre todas; não recomende a sobra inteira para uma só):")
        goalLines(plans).forEach { appendLine("- $it") }
        append(allocationSummary(plans, monthlyCapacityCents))
    }

    /** O bloco completo das dívidas, com a regra de tratar cada uma separadamente. */
    fun debtsBlock(plans: List<DebtPlan>, today: LocalDate = LocalDate.now()): String = buildString {
        if (plans.isEmpty()) return@buildString
        appendLine("Dívidas, cada uma separada, na ordem de ataque calculada pelo app (maior juro primeiro):")
        debtLines(plans, today).forEach { appendLine("- $it") }
        append(
            "Ao falar de uma dívida, cite o nome dela e os números dela. Os totais são soma de todas: " +
                "nunca apresente o total ou os juros somados como se fossem de uma dívida só.",
        )
    }

    private fun statusLabel(s: GoalStatus): String = when (s) {
        GoalStatus.OnTrack -> "no ritmo"
        GoalStatus.Reassess -> "precisa reavaliar (a sobra projetada até o prazo não cobre tudo)"
        GoalStatus.Priority -> "sem dinheiro previsto até o prazo"
    }

    private fun brl(cents: Long) = formatBrl0(cents / 100.0)
}
