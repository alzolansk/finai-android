package com.finai.app.domain

import java.time.LocalDate

/** A única ação em destaque no bloco de situação da Início. */
enum class SituationAction(val label: String) {
    /** Falta prevista: o que resolve é ver o que vence e quando. */
    SeeCommitments("Ver compromissos"),
    /** Sem renda principal cadastrada, o número vai só até o fim do mês. */
    AddIncome("Informar renda"),
    CanIBuy("Posso comprar?"),
}

/**
 * O topo da Início (planning.md §9 Fase 7, item 3): uma frase de situação, **um** número com
 * o período explícito e uma ação. Junta o antigo cartão "Até o próximo salário" e o antigo
 * "Pode gastar hoje", que mostravam dois valores parecidos e confundiam.
 *
 * O número principal é o livre até o salário ([SafeToSpendResult.slackThisMonthCents], o dia
 * mais apertado menos a reserva das metas) — o mesmo da linha "Livre até o salário" da folha
 * "Entenda este valor". O valor por dia vira apoio. Com falta prevista, o número vira a falta.
 *
 * Cálculo local, sem IA (planning.md §6): só reorganiza o que [PayCycle] e
 * [SafeToSpendCalculator] já calcularam.
 */
data class HomeSituation(
    val headline: String,
    /** Rótulo com o período, ex.: "LIVRE ATÉ O SALÁRIO · 30/10". */
    val label: String,
    /** Livre até o fim do período, ou a falta (com [shortfall] = true). Nunca negativo. */
    val mainCents: Long,
    val shortfall: Boolean,
    val perDayCents: Long,
    val days: Int,
    val action: SituationAction,
    /** Data do próximo salário estimada (nenhum salário futuro lançado). */
    val estimated: Boolean,
) {
    companion object {
        fun of(safe: SafeToSpendResult, cycle: PayCycle?): HomeSituation {
            val falta = safe.shortfall
            val label = when {
                cycle == null -> "LIVRE ATÉ O FIM DO MÊS"
                else -> "LIVRE ATÉ O SALÁRIO · ${dm(cycle.proximo)}" + if (cycle.proximoEstimado) " (ESTIMADO)" else ""
            }
            val headline = when {
                falta != null && cycle != null -> "Falta dinheiro antes do próximo salário"
                falta != null -> "Falta dinheiro para as contas deste mês"
                safe.slackThisMonthCents < 0 -> "As contas estão cobertas, mas as metas não cabem inteiras"
                cycle == null -> "Informe sua renda para saber até quando o dinheiro precisa durar"
                else -> "Seu dinheiro cobre os próximos compromissos"
            }
            return HomeSituation(
                headline = headline,
                label = if (falta != null) label.replaceFirst("LIVRE", "FALTA") else label,
                mainCents = falta?.cents ?: safe.slackThisMonthCents.coerceAtLeast(0),
                shortfall = falta != null,
                perDayCents = safe.safeTodayCents,
                days = safe.daysRemaining,
                action = when {
                    falta != null -> SituationAction.SeeCommitments
                    cycle == null -> SituationAction.AddIncome
                    else -> SituationAction.CanIBuy
                },
                estimated = cycle?.proximoEstimado == true,
            )
        }

        private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }
}
