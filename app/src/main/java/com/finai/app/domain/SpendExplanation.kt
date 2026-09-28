package com.finai.app.domain

import com.finai.app.util.formatBrl0
import java.time.LocalDate

/** Uma linha da conta: sinal no próprio valor (+ entra, − sai); [total] marca um resultado parcial. */
data class ExplainStep(val label: String, val cents: Long, val detail: String? = null, val total: Boolean = false)

/** Um compromisso que ainda vai sair antes do salário. */
data class ExplainItem(val date: LocalDate, val label: String, val cents: Long)

/**
 * "Entenda este valor" — de onde vem o "Pode gastar hoje", em passos que a pessoa consegue
 * conferir: período, o que entrou, o que vai entrar, o que saiu, o que falta pagar, o dia
 * mais apertado, a reserva das metas e a divisão pelos dias. Também diz o que o app está
 * supondo ([assumptions]) e o que falta cadastrar para o número ficar mais fiel ([missing]).
 *
 * Só reorganiza números que [PayCycle] e [SafeToSpendCalculator] já calcularam; nenhuma
 * regra nova de dinheiro mora aqui.
 */
data class SpendExplanation(
    val period: String,
    val steps: List<ExplainStep>,
    val upcoming: List<ExplainItem>,
    val perDayCents: Long,
    val days: Int,
    val shortfall: CycleShortfall?,
    val assumptions: List<String>,
    val missing: List<String>,
) {
    companion object {
        private const val NOT_BANK = "Não é o saldo do banco: é a soma do que você lançou no app."

        fun of(safe: SafeToSpendResult, cycle: PayCycle?, today: LocalDate): SpendExplanation =
            if (cycle != null) fromCycle(safe, cycle) else fromMonth(safe, today)

        private fun fromCycle(safe: SafeToSpendResult, c: PayCycle): SpendExplanation {
            val period = "De hoje (${dm(c.today)}) até ${dm(c.proximo.minusDays(1))}, véspera do salário — ${c.diasAteProximo} dia(s)"
            val saidas = c.entries.count { it.cents < 0 && it.done }
            val steps = buildList {
                add(ExplainStep(
                    "Já entrou no ciclo", c.entradasCents - c.aReceberCents,
                    c.inicio?.let { "Desde o salário de ${dm(it)}" } ?: "O primeiro salário ainda não caiu",
                ))
                if (c.aReceberCents > 0) add(ExplainStep("Ainda vai entrar antes do salário", c.aReceberCents))
                add(ExplainStep("Já saiu", -c.jaSaiuCents, "$saidas lançamento(s) e conta(s) pagas no ciclo"))
                add(ExplainStep("Ainda vai sair até o salário", -c.comprometidoCents, "Contas, parcelas e gastos lançados com data"))
                add(ExplainStep("Sobra no fim do ciclo", c.livreCents, total = true))
                val floor = safe.floor
                if (floor != null && floor.cents < c.livreCents) {
                    add(ExplainStep(
                        "Ajuste do dia mais apertado", floor.cents - c.livreCents,
                        "Em ${dm(floor.date)} o saldo previsto é ${formatBrl0(floor.cents / 100.0)}, porque uma saída vem antes " +
                            "de uma entrada. Dá para gastar hoje só o que sobra nesse dia.",
                    ))
                }
                if (safe.reservedForGoalsCents > 0) {
                    add(ExplainStep("Reservado para as metas", -safe.reservedForGoalsCents, "A parte do aporte deste mês que cabe"))
                }
                add(ExplainStep(
                    "Livre até o salário", safe.slackThisMonthCents.coerceAtLeast(0),
                    safe.shortfall?.let { "Nada: no dia mais apertado faltam ${formatBrl0(it.cents / 100.0)}" },
                    total = true,
                ))
            }
            val upcoming = c.entries.filter { it.cents < 0 && !it.done }
                .sortedBy { it.date }
                .map { ExplainItem(it.date, it.descricao.ifBlank { "Sem descrição" }, -it.cents) }
            val assumptions = buildList {
                add(NOT_BANK)
                add("O que sobrou do ciclo anterior não entra. Se sobrou dinheiro, ele não aparece aqui.")
                add("Compras no cartão contam pela fatura, no vencimento, e não no dia da compra.")
                if (c.proximoEstimado) add("O próximo salário foi estimado em ${dm(c.proximo)}, um mês depois do último.")
            }
            val missing = buildList {
                if (!c.salarioPeloNome) {
                    add("O app deduziu que \"${c.salarioNome}\" é seu salário (a maior receita recorrente). Se não for, lance o salário com \"Salário\" na descrição.")
                }
                if (c.proximoEstimado) add("Lance o próximo salário com a data certa para o período ficar exato.")
                if (c.inicio == null) add("Nenhum salário antes de hoje: o que vence até ${dm(c.proximo)} aparece sem dinheiro para cobrir.")
            }
            return SpendExplanation(period, steps, upcoming, safe.safeTodayCents, c.diasAteProximo, safe.shortfall, assumptions, missing)
        }

        private fun fromMonth(safe: SafeToSpendResult, today: LocalDate): SpendExplanation {
            val beforeGoals = safe.monthIncomeCents - safe.monthSpentCents - safe.monthBillsCents
            val steps = buildList {
                add(ExplainStep("Receitas do mês", safe.monthIncomeCents))
                add(ExplainStep("Gastos lançados no mês", -safe.monthSpentCents, "Receitas lançadas pelo + já vêm descontadas aqui"))
                add(ExplainStep("Contas a pagar ainda em aberto", -safe.monthBillsCents))
                if (beforeGoals < 0) {
                    // Mostra o buraco como ele é: "R$ 0" esconderia que as contas não fecham.
                    add(ExplainStep("Falta para cobrir as contas", beforeGoals, total = true))
                } else {
                    if (safe.reservedForGoalsCents > 0) add(ExplainStep("Reservado para as metas", -safe.reservedForGoalsCents))
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
                missing = listOf("Lance seu salário (uma receita com \"Salário\" na descrição) para o app calcular até o próximo pagamento."),
            )
        }

        private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }
}
