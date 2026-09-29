package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

/** Para onde o plano recomenda levar uma parte do dinheiro livre. */
enum class RecommendationTarget { Debt, Goal }

data class Recommendation(
    val target: RecommendationTarget,
    /** Id da dívida ou do objetivo no Room. */
    val id: Long,
    val name: String,
    val cents: Long,
    /** O porquê em uma frase, já no formato que a tela e a IA usam. */
    val reason: String,
)

/**
 * O plano financeiro do app: a única resposta para "quanto está livre" e "para onde vale levar
 * esse dinheiro", calculada uma vez e lida por todas as telas (Início, Objetivos, Dívidas,
 * simulador, sino/rotina diária) e pela IA. Antes cada tela fazia a própria conta, e a mesma
 * sobra aparecia como aporte possível em várias metas e, ao mesmo tempo, na dívida.
 *
 * Três camadas, sempre separadas:
 * - **Comprometido e reservado** ([comprometidoCents], [reservadoFuturoCents]): o que ainda vai
 *   sair até o salário e a parte do saldo de hoje que precisa ficar parada porque uma saída
 *   vence antes da entrada que a cobriria (saldo de hoje − menor saldo previsto). Dinheiro que
 *   só chega depois do dia mais apertado não é reserva ([aindaVaiEntrarCents]).
 * - **Livre** ([livreCents]): o menor saldo de fim de dia previsto de hoje até a véspera do
 *   salário, contando todas as entradas lançadas nesse intervalo (inclusive extras, como uma
 *   rescisão) e todas as saídas.
 * - **Destino recomendado** ([recommendations]): para onde o plano sugere levar o livre, nesta
 *   ordem: dívida cara (juros ≥ [EXPENSIVE_DEBT_BASIS_POINTS] ao mês), da maior taxa para a
 *   menor; depois metas por prioridade, cada uma até o aporte mensal que pede. Cada real é
 *   recomendado para um destino só, então nenhuma tela sugere os mesmos R$ 114 duas vezes.
 *
 * **Recomendação não é gasto.** Ela nunca reduz o livre: o valor continua livre até o usuário
 * registrar o pagamento ou o aporte (que aí vira lançamento e entra na conta como saída).
 * Com falta prevista não há recomendação — a prioridade é cobrir a falta.
 *
 * Tudo local e determinístico (planning.md §6). A IA recebe este plano pronto e só explica.
 */
data class FinancialPlan(
    val today: LocalDate,
    val cycle: PayCycle?,
    /** Último dia coberto: véspera do próximo salário, ou fim do mês sem salário cadastrado. */
    val until: LocalDate,
    val saldoDoMesCents: Long,
    val comprometidoCents: Long,
    val reservadoFuturoCents: Long,
    val livreCents: Long,
    /**
     * Quanto o ciclo ainda ganha depois do dia mais apertado (sobra final − livre): entradas que
     * ainda não caíram. Não é reserva e não está livre hoje; vira livre quando entrar.
     */
    val aindaVaiEntrarCents: Long = 0,
    val capacidadeMensalCents: Long,
    val shortfall: CycleShortfall?,
    val recommendations: List<Recommendation>,
    /** Metas com [GoalPlan.monthlyContributionFundedCents] = o que o plano recomenda para cada uma. */
    val goals: List<GoalPlan>,
    /** Só dívidas em aberto (quitadas ficam fora de toda conta). */
    val debts: DebtSummary,
    val safe: SafeToSpendResult,
    val projection: SavingsProjection,
) {
    val recomendacao: Recommendation? get() = recommendations.firstOrNull()
    val recomendadoCents: Long get() = recommendations.sumOf { it.cents }

    fun forGoal(id: Long): Long = recommendations.filter { it.target == RecommendationTarget.Goal && it.id == id }.sumOf { it.cents }
    fun forDebt(id: Long): Long = recommendations.filter { it.target == RecommendationTarget.Debt && it.id == id }.sumOf { it.cents }

    /** "até o salário de 28/10" ou "até o fim do mês (31/10)". */
    val untilLabel: String get() =
        if (cycle != null) "até o salário de ${dm(cycle.proximo)}" else "até o fim do mês (${dm(until)})"

    /** A frase de cada meta sobre o que o plano recomenda para ela, coerente com as outras telas. */
    fun goalNote(goal: GoalPlan): String {
        if (goal.missingCents <= 0L) return ""
        val got = forGoal(goal.objetivo.id)
        val falta = shortfall
        return when {
            falta != null ->
                "Sem recomendação de aporte $untilLabel: antes, é preciso cobrir a falta de ${brl(falta.cents)} prevista para ${dm(falta.date)}."
            got > 0 && got < goal.monthlyContributionNeededCents ->
                "Recomendação $untilLabel: guardar ${brl(got)} nesta meta (o ideal seria ${brl(goal.monthlyContributionNeededCents)}/mês)."
            got > 0 -> "Recomendação $untilLabel: guardar ${brl(got)} nesta meta."
            livreCents <= 0 -> "Nada livre $untilLabel depois dos compromissos."
            else -> {
                val debt = recommendations.firstOrNull { it.target == RecommendationTarget.Debt }
                if (debt != null) "O plano recomenda usar o livre $untilLabel primeiro em \"${debt.name}\" (${debt.reason}). Esta meta vem depois."
                else "O livre $untilLabel já está recomendado para metas de maior prioridade."
            }
        }
    }

    /** Frase da dívida que o plano recomenda amortizar; nulo para as outras. */
    fun debtNote(divida: DividaEntity): String? =
        forDebt(divida.id).takeIf { it > 0 }?.let { "Recomendação $untilLabel: usar ${brl(it)} do livre para amortizar." }

    /** "Livre até o salário de 28/10: R$ 114. Recomendação: R$ 114 para "X"." */
    val recommendationSummary: String get() = when {
        shortfall != null -> "Nada livre $untilLabel: primeiro é preciso cobrir a falta de ${brl(shortfall.cents)}."
        livreCents <= 0 -> "Nada livre $untilLabel depois dos compromissos."
        recommendations.isEmpty() -> "${brl(livreCents)} livres $untilLabel, sem dívida cara ou meta para recomendar."
        else -> "${brl(livreCents)} livres $untilLabel. Recomendação: " +
            recommendations.joinToString("; ") { "${brl(it.cents)} para \"${it.name}\"" } + "."
    }

    /**
     * "Decisões para você", sem IA: a ordem e os valores vêm do plano. Orçamento estourado e
     * assinatura parada entram depois, até 3 itens.
     */
    fun decisions(budgetsOver: List<String>, unusedSubscriptions: List<String>): List<AiReplyFormat.Decision> = buildList {
        shortfall?.let { f ->
            add(AiReplyFormat.Decision(
                "Cobrir a falta de ${brl(f.cents)} prevista para ${dm(f.date)}",
                (f.causa?.let { "\"$it\" vence" } ?: "Uma saída vence") + " antes da entrada que a cobriria. Nada é livre $untilLabel.",
            ))
        }
        recommendations.forEach { r ->
            add(when (r.target) {
                RecommendationTarget.Debt -> AiReplyFormat.Decision(
                    "Amortizar ${brl(r.cents)} em \"${r.name}\"",
                    "${r.reason.replaceFirstChar { it.uppercase() }}. O valor continua livre até você registrar o pagamento.",
                )
                RecommendationTarget.Goal -> AiReplyFormat.Decision(
                    "Guardar ${brl(r.cents)} para \"${r.name}\"",
                    "${r.reason.replaceFirstChar { it.uppercase() }}. O valor continua livre até você registrar o aporte.",
                )
            })
        }
        budgetsOver.forEach { add(AiReplyFormat.Decision("Segurar os gastos em $it", "O limite da categoria já estourou este mês.")) }
        unusedSubscriptions.forEach { add(AiReplyFormat.Decision("Cancelar $it", "Assinatura sem uso recente.")) }
    }.take(3)

    /**
     * O plano como a IA recebe: curto, com as três camadas. É a única fonte de "quanto está
     * livre" e "para onde levar" no prompt; a IA não decide destino nem valor.
     */
    fun aiBlock(): String = buildString {
        appendLine("Plano financeiro calculado pelo app, $untilLabel (a IA explica; não muda valores nem a recomendação):")
        appendLine("- Comprometido (ainda vai sair $untilLabel): ${brl(comprometidoCents)}.")
        if (reservadoFuturoCents > 0) {
            appendLine("- Reservado (já está no ciclo, mas uma saída vence antes da próxima entrada): ${brl(reservadoFuturoCents)}.")
        }
        if (aindaVaiEntrarCents > 0) {
            appendLine("- Ainda vai entrar depois do dia mais apertado (não é livre hoje; vira livre quando cair): ${brl(aindaVaiEntrarCents)}.")
        }
        shortfall?.let { appendLine("- Falta prevista: ${brl(it.cents)} a partir de ${dm(it.date)}" + (it.causa?.let { c -> " ($c)" } ?: "") + ".") }
        appendLine("- Livre $untilLabel (\"folga\", o que o usuário pode usar): ${brl(livreCents.coerceAtLeast(0))}.")
        if (recommendations.isEmpty()) appendLine("- Recomendação para o livre: nenhuma.")
        else recommendations.forEach { appendLine("- Recomendação para o livre: ${brl(it.cents)} para \"${it.name}\" (${it.reason}).") }
        appendLine("- Balanço do mês na Agenda (outra janela de tempo, não é o livre): ${brl(saldoDoMesCents)}.")
        val semAporte = goals.filter { it.missingCents > 0 && forGoal(it.objetivo.id) == 0L }.map { "\"${it.objetivo.nome}\"" }
        if (semAporte.isNotEmpty()) appendLine("- Metas sem recomendação de aporte agora: ${semAporte.joinToString()}.")
        append(
            "Regras: recomendação não é gasto nem reserva, o livre só diminui quando o usuário registra o pagamento. " +
                "Não recomende o mesmo dinheiro para outro destino; se o usuário quiser mudar, diga o que deixa de receber.",
        )
    }

    companion object {
        /** 3% ao mês (≈ 42% ao ano): acima disso, amortizar rende mais do que guardar para meta. */
        const val EXPENSIVE_DEBT_BASIS_POINTS = 300

        private fun brl(cents: Long) = formatBrl0(cents / 100.0)
        private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
        private fun rate(bp: Int) = "%.1f".format(java.util.Locale("pt", "BR"), bp / 100.0)

        /**
         * Divide [livreCents] em recomendações para dívidas caras e metas. Cada real aparece em uma
         * recomendação só — é o que impede a mesma sobra de ser sugerida em dois lugares.
         */
        fun recommend(livreCents: Long, activeDebts: List<DividaEntity>, goals: List<GoalPlan>): List<Recommendation> {
            var left = livreCents.coerceAtLeast(0)
            val out = mutableListOf<Recommendation>()
            activeDebts
                .filter { it.taxaJurosMensalBasisPoints >= EXPENSIVE_DEBT_BASIS_POINTS && it.valorAbertoCentavos > 0 }
                .sortedByDescending { it.taxaJurosMensalBasisPoints }
                .forEach { d ->
                    val amount = minOf(left, d.valorAbertoCentavos)
                    if (amount <= 0) return@forEach
                    out += Recommendation(RecommendationTarget.Debt, d.id, d.nome, amount, "juros de ${rate(d.taxaJurosMensalBasisPoints)}% ao mês, mais caro do que qualquer rendimento de meta")
                    left -= amount
                }
            goals.filter { it.missingCents > 0 }.sortedBy { it.objetivo.prioridade }.forEach { g ->
                val amount = minOf(left, g.monthlyContributionNeededCents)
                if (amount <= 0) return@forEach
                out += Recommendation(RecommendationTarget.Goal, g.objetivo.id, g.objetivo.nome, amount, "prioridade ${g.objetivo.prioridade} entre as metas")
                left -= amount
            }
            return out
        }

        fun build(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            objetivos: List<ObjetivoEntity>,
            today: LocalDate,
            onCycleError: (Throwable) -> Unit = {},
        ): FinancialPlan {
            val capacity = SavingsCapacityCalculator.monthlyCapacityCents(contas, transacoes, dividas, today)
            val projection = SavingsProjection.of(contas, transacoes, dividas, SavingsProjection.horizonFor(objetivos, today), today)
            val rawGoals = GoalCalculator.plan(objetivos, capacity, today, projection)
            val cycle = runCatching { PayCycle.of(contas, transacoes, dividas, today) }.onFailure(onCycleError).getOrNull()
            val activeDebts = dividas.filterNot(Completion::isPaid)
            val flow = MonthCashFlow.of(contas, transacoes, dividas, YearMonth.from(today), today)

            val comprometido: Long
            val reservado: Long
            var depois = 0L
            val livre: Long
            val shortfall: CycleShortfall?
            val until: LocalDate
            val safe: SafeToSpendResult
            if (cycle != null) {
                // Simulação dia a dia: o livre é o menor saldo de fim de dia até a véspera do salário.
                livre = cycle.floor.cents.coerceAtLeast(0)
                // Reserva = dinheiro que já está no ciclo hoje mas precisa ficar para uma saída que
                // vence antes da próxima entrada. O que só chega depois (sobra final − livre)
                // não é reserva: ainda não entrou ([aindaVaiEntrarCents]).
                reservado = (cycle.saldoHojeCents - cycle.floor.cents).coerceAtLeast(0)
                depois = (cycle.livreCents - cycle.floor.cents).coerceAtLeast(0)
                comprometido = cycle.comprometidoCents
                shortfall = cycle.shortfall
                until = cycle.proximo.minusDays(1)
                safe = SafeToSpendCalculator.fromCycle(cycle, 0)
            } else {
                // Sem renda cadastrada não há como saber quando o dinheiro entra: a conta é do
                // mês inteiro, e fica com o menor entre a folga do mês e o balanço da Agenda
                // (que também desconta as parcelas de dívida).
                val month = SafeToSpendCalculator.calculate(contas, transacoes, 0, today)
                val base = minOf(month.slackThisMonthCents, flow.saldoCents)
                livre = base.coerceAtLeast(0)
                reservado = 0
                comprometido = month.monthBillsCents + flow.debtsDue.sumOf { it.valorCentavos }
                shortfall = month.shortfall ?: if (base < 0) CycleShortfall(today, -base) else null
                until = today.withDayOfMonth(today.lengthOfMonth())
                safe = month.copy(
                    slackThisMonthCents = base,
                    safeTodayCents = (base / month.daysRemaining).coerceAtLeast(0),
                    shortfall = shortfall,
                )
            }

            val recommendations = if (shortfall != null) emptyList()
            else recommend(livre, activeDebts, rawGoals.filterNot { Completion.isDone(it.objetivo) })
            val goals = rawGoals.map { g ->
                g.copy(monthlyContributionFundedCents = recommendations.filter { it.target == RecommendationTarget.Goal && it.id == g.objetivo.id }.sumOf { it.cents })
            }

            return FinancialPlan(
                today = today,
                cycle = cycle,
                until = until,
                saldoDoMesCents = flow.saldoCents,
                comprometidoCents = comprometido,
                reservadoFuturoCents = reservado,
                livreCents = livre,
                aindaVaiEntrarCents = depois,
                capacidadeMensalCents = capacity,
                shortfall = shortfall,
                recommendations = recommendations,
                goals = goals,
                debts = DebtCalculator.summarize(activeDebts, today),
                safe = safe,
                projection = projection,
            )
        }
    }
}
