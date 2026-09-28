package com.finai.app.domain

import com.finai.app.util.formatBrl0
import java.time.LocalDate

/** Uma linha da conta: sinal no próprio valor (+ entra, − sai); [total] marca um resultado parcial. */
data class ExplainStep(val label: String, val cents: Long, val detail: String? = null, val total: Boolean = false)

/** Um compromisso que ainda vai sair antes do salário. */
data class ExplainItem(val date: LocalDate, val label: String, val cents: Long)

/**
 * "Entenda este valor" — de onde vem o livre até o salário, em passos que a pessoa consegue
 * conferir: saldo atual do ciclo, entradas previstas até o salário (com data), compromissos
 * (com data), a reserva necessária para um dia apertado e o livre. A recomendação do plano
 * aparece à parte ([recommendation]) porque não desconta nada do livre. Também diz o que o app
 * está supondo ([assumptions]) e o que falta cadastrar ([missing]).
 *
 * Só reorganiza números que [PayCycle] e [SafeToSpendCalculator] já calcularam; nenhuma
 * regra nova de dinheiro mora aqui.
 */
data class SpendExplanation(
    val period: String,
    val steps: List<ExplainStep>,
    /** Saídas até o salário, por data. */
    val upcoming: List<ExplainItem>,
    val perDayCents: Long,
    val days: Int,
    val shortfall: CycleShortfall?,
    val assumptions: List<String>,
    val missing: List<String>,
    /** Entradas previstas até o salário, por data (inclusive extras, como uma rescisão). */
    val incoming: List<ExplainItem> = emptyList(),
    /** Recomendação do plano central para o livre ([FinancialPlan.recommendationSummary]). */
    val recommendation: String? = null,
) {
    companion object {
        private const val NOT_BANK = "Não é o saldo do banco: é a soma do que você lançou no app."

        fun of(safe: SafeToSpendResult, cycle: PayCycle?, today: LocalDate, recommendation: String? = null): SpendExplanation =
            if (cycle != null) fromCycle(safe, cycle, recommendation) else fromMonth(safe, today, recommendation)

        private fun fromCycle(safe: SafeToSpendResult, c: PayCycle, recommendation: String?): SpendExplanation {
            val period = "De hoje (${dm(c.today)}) até ${dm(c.proximo.minusDays(1))}, véspera do salário — ${c.diasAteProximo} dia(s)"
            val entrou = c.entradasCents - c.aReceberCents
            val saidas = c.entries.count { it.cents < 0 && it.done }
            val incoming = c.entries.filter { it.cents > 0 && !it.done }
                .sortedBy { it.date }
                .map { ExplainItem(it.date, it.descricao.ifBlank { "Sem descrição" }, it.cents) }
            val upcoming = c.entries.filter { it.cents < 0 && !it.done }
                .sortedBy { it.date }
                .map { ExplainItem(it.date, it.descricao.ifBlank { "Sem descrição" }, -it.cents) }
            val floor = safe.floor ?: c.floor
            val steps = buildList {
                add(ExplainStep(
                    "Saldo atual do ciclo", entrou - c.jaSaiuCents,
                    (c.inicio?.let { "Entrou ${formatBrl0(entrou / 100.0)} desde o salário de ${dm(it)}" } ?: "O primeiro salário ainda não caiu") +
                        "; saiu ${formatBrl0(c.jaSaiuCents / 100.0)} ($saidas lançamento(s) e conta(s) pagas)",
                ))
                add(ExplainStep(
                    "Entradas previstas até o salário", c.aReceberCents,
                    if (incoming.isEmpty()) "Nenhuma lançada" else "${incoming.size} entrada(s), listadas abaixo com a data",
                ))
                add(ExplainStep(
                    "Compromissos até o salário", -c.comprometidoCents,
                    if (upcoming.isEmpty()) "Nenhum lançado" else "Contas, parcelas e gastos com data, listados abaixo",
                ))
                add(ExplainStep("Sobra no fim do ciclo", c.livreCents, total = true))
                if (floor.cents < c.livreCents) {
                    add(ExplainStep(
                        "Reserva necessária", floor.cents - c.livreCents,
                        "Em ${dm(floor.date)} o saldo previsto cai para ${formatBrl0(floor.cents / 100.0)}, porque uma saída vem " +
                            "antes de uma entrada. Essa parte fica guardada até lá.",
                    ))
                }
                if (safe.reservedForPlanCents > 0) add(ExplainStep("Reservado", -safe.reservedForPlanCents))
                if (safe.shortfall != null) {
                    add(ExplainStep("Falta no dia mais apertado", safe.slackThisMonthCents, total = true))
                } else {
                    add(ExplainStep("Livre até o salário", safe.slackThisMonthCents, total = true))
                }
            }
            val assumptions = buildList {
                add(NOT_BANK)
                add("O que sobrou do ciclo anterior não entra. Se sobrou dinheiro, ele não aparece aqui.")
                add("Compras no cartão contam pela fatura, no vencimento, e não no dia da compra.")
                add("O saldo é simulado dia a dia: uma entrada e uma saída no mesmo dia se compensam.")
                add("A recomendação do plano não desconta do livre: o valor só muda quando você registra o pagamento ou o aporte.")
                if (c.proximoEstimado) add("O próximo salário foi estimado em ${dm(c.proximo)}, um mês depois do último.")
            }
            val missing = buildList {
                if (!c.salarioDefinido) {
                    add("O app deduziu que \"${c.salarioNome}\" é sua renda principal (a maior receita recorrente). Confirme na Agenda com \"Marcar como renda principal\", ou lance a renda certa com essa opção ligada.")
                }
                if (c.proximoEstimado) add("Lance o próximo salário com a data certa para o período ficar exato.")
                if (c.inicio == null) add("Nenhum salário antes de hoje: o que vence até ${dm(c.proximo)} aparece sem dinheiro para cobrir.")
            }
            return SpendExplanation(
                period, steps, upcoming, safe.safeTodayCents, c.diasAteProximo, safe.shortfall, assumptions, missing,
                incoming = incoming, recommendation = recommendation,
            )
        }

        private fun fromMonth(safe: SafeToSpendResult, today: LocalDate, recommendation: String?): SpendExplanation {
            val beforeGoals = safe.monthIncomeCents - safe.monthSpentCents - safe.monthBillsCents
            // O plano usa o menor entre a folga e o balanço da Agenda, que também desconta parcelas.
            val base = safe.slackThisMonthCents + safe.reservedForPlanCents
            val steps = buildList {
                add(ExplainStep("Receitas do mês", safe.monthIncomeCents))
                add(ExplainStep("Gastos lançados no mês", -safe.monthSpentCents, "Receitas lançadas pelo + já vêm descontadas aqui"))
                add(ExplainStep("Contas a pagar ainda em aberto", -safe.monthBillsCents))
                if (base < beforeGoals) {
                    add(ExplainStep("Ajuste pelo balanço da Agenda", base - beforeGoals, "Parcelas de dívida e contas já pagas do mês"))
                }
                if (base < 0) {
                    // Mostra o buraco como ele é: "R$ 0" esconderia que as contas não fecham.
                    add(ExplainStep("Falta para cobrir as contas", base, total = true))
                } else {
                    if (safe.reservedForPlanCents > 0) add(ExplainStep("Reservado", -safe.reservedForPlanCents))
                    add(ExplainStep("Livre até o fim do mês", safe.slackThisMonthCents.coerceAtLeast(0), total = true))
                }
            }
            return SpendExplanation(
                period = "De hoje (${dm(today)}) até ${dm(today.withDayOfMonth(today.lengthOfMonth()))}, fim do mês — ${safe.daysRemaining} dia(s)",
                steps = steps,
                upcoming = emptyList(),
                perDayCents = safe.safeTodayCents,
                days = safe.daysRemaining,
                shortfall = safe.shortfall,
                assumptions = listOf(
                    NOT_BANK,
                    "Sem salário identificado, a conta vai até o fim do mês e não enxerga um aperto no meio dele.",
                ),
                missing = listOf("Informe sua renda principal (uma receita com \"É minha renda principal\" ligado) para o app calcular até o próximo pagamento."),
                recommendation = recommendation,
            )
        }

        private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }
}
