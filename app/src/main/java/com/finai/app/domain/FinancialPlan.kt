package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

/** Para onde vai uma parte do dinheiro livre. */
enum class AllocationTarget { Debt, Goal }

data class Allocation(
    val target: AllocationTarget,
    /** Id da dívida ou do objetivo no Room. */
    val id: Long,
    val name: String,
    val cents: Long,
    /** O porquê em uma frase, já no formato que a tela e a IA usam. */
    val reason: String,
)

/**
 * O plano financeiro do app: **a** resposta para "para onde vai o dinheiro livre", calculada
 * uma vez e lida por todas as telas (Início, Objetivos, Dívidas, simulador, sino/rotina diária)
 * e pela IA. Antes cada tela fazia a própria conta: a mesma sobra de R$ 114 aparecia como
 * aporte possível em três metas e, ao mesmo tempo, a IA sugeria mandá-la para a dívida.
 *
 * Quatro conceitos, sempre separados:
 * - [saldoDoMesCents]: o balanço do mês na Agenda ([MonthCashFlow]), informativo.
 * - [comprometidoCents]: o que ainda vai sair até [until] (contas, fatura pelo vencimento,
 *   parcelas, gastos com data), já decidido — não é dinheiro livre.
 * - [reservadoFuturoCents]: a parte da sobra final que precisa ficar parada porque uma
 *   obrigação vence **antes** da entrada que a cobriria. Vem da simulação dia a dia do ciclo
 *   ([PayCycle.floor]): se entra R$ 1.900 no dia 10 e sai R$ 600 no dia 12, a ordem importa.
 * - [disponivelCents]: o que está realmente livre — o menor saldo previsto até [until].
 *
 * Só o disponível é destinado ([allocations]), e cada real vai para um destino só, nesta
 * ordem: (1) com falta prevista, nada é destinado — a prioridade é cobrir a falta; (2) dívida
 * cara (juros ≥ [EXPENSIVE_DEBT_BASIS_POINTS] ao mês), da maior taxa para a menor, porque
 * nenhuma meta rende isso; (3) metas por prioridade, cada uma até o aporte mensal que pede.
 * O que sobra fica livre para o dia a dia. O teto do que é destinado é a sobra do mês
 * ([capacidadeMensalCents]): uma entrada extra não vira compromisso de aporte recorrente.
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
    val disponivelCents: Long,
    val capacidadeMensalCents: Long,
    val shortfall: CycleShortfall?,
    val allocations: List<Allocation>,
    /** Metas com [GoalPlan.monthlyContributionFundedCents] = o que o plano destinou a cada uma. */
    val goals: List<GoalPlan>,
    /** Só dívidas em aberto (quitadas ficam fora de toda conta). */
    val debts: DebtSummary,
    val safe: SafeToSpendResult,
    val projection: SavingsProjection,
) {
    val alocadoCents: Long get() = allocations.sumOf { it.cents }
    val livreParaGastarCents: Long get() = (disponivelCents - alocadoCents).coerceAtLeast(0)
    val destino: Allocation? get() = allocations.firstOrNull()

    fun forGoal(id: Long): Long = allocations.filter { it.target == AllocationTarget.Goal && it.id == id }.sumOf { it.cents }
    fun forDebt(id: Long): Long = allocations.filter { it.target == AllocationTarget.Debt && it.id == id }.sumOf { it.cents }

    /** "até o salário de 30/10" ou "até o fim do mês (31/10)". */
    val untilLabel: String get() =
        if (cycle != null) "até o salário de ${dm(cycle.proximo)}" else "até o fim do mês (${dm(until)})"

    /**
     * A frase de cada meta sobre o que o plano fez com ela neste período. É o que substitui a
     * recomendação isolada por meta: se a sobra foi para a dívida, a meta diz isso.
     */
    fun goalNote(goal: GoalPlan): String {
        if (goal.missingCents <= 0L) return ""
        val got = forGoal(goal.objetivo.id)
        val falta = shortfall
        return when {
            falta != null ->
                "Sem aporte $untilLabel: antes, é preciso cobrir a falta de ${brl(falta.cents)} prevista para ${dm(falta.date)}."
            got > 0 && got < goal.monthlyContributionNeededCents ->
                "Plano $untilLabel: ${brl(got)} para esta meta (o ideal seria ${brl(goal.monthlyContributionNeededCents)}/mês)."
            got > 0 -> "Plano $untilLabel: ${brl(got)} para esta meta."
            disponivelCents <= 0 ->
                "Sem valor adicional $untilLabel: não sobra dinheiro livre depois das contas."
            capacidadeMensalCents <= 0 ->
                "Sem valor adicional $untilLabel: o balanço do mês não deixa sobra para aporte."
            else -> {
                val debt = allocations.firstOrNull { it.target == AllocationTarget.Debt }
                if (debt != null) "Sem valor adicional $untilLabel: a sobra vai para \"${debt.name}\" (${debt.reason})."
                else "Sem valor adicional $untilLabel: a sobra já foi para metas de maior prioridade."
            }
        }
    }

    /** Frase da dívida que recebe parte da sobra; nulo para as outras. */
    fun debtNote(divida: DividaEntity): String? =
        forDebt(divida.id).takeIf { it > 0 }?.let { "Destino da sobra $untilLabel: ${brl(it)} a mais para amortizar." }

    /** Resumo do plano para as telas: "R$ 114 livres até 29/10 → Cartão R$ 114". */
    val allocationSummary: String get() = when {
        shortfall != null -> "Nada é destinado $untilLabel: primeiro é preciso cobrir a falta de ${brl(shortfall.cents)}."
        allocations.isEmpty() && disponivelCents <= 0 -> "Não sobra dinheiro livre $untilLabel depois das contas."
        allocations.isEmpty() -> "${brl(disponivelCents)} disponíveis $untilLabel, sem meta ou dívida cara para receber."
        else -> "${brl(disponivelCents)} disponíveis $untilLabel: " +
            allocations.joinToString("; ") { "${brl(it.cents)} para \"${it.name}\"" } +
            (livreParaGastarCents.takeIf { it > 0 }?.let { "; ${brl(it)} para o dia a dia" } ?: "") + "."
    }

    /**
     * "Decisões para você", sem IA: o destino do dinheiro vem do plano, então a ordem e os
     * valores também. Orçamento estourado e assinatura parada entram depois, até 3 itens.
     */
    fun decisions(budgetsOver: List<String>, unusedSubscriptions: List<String>): List<AiReplyFormat.Decision> = buildList {
        shortfall?.let { f ->
            add(AiReplyFormat.Decision(
                "Cobrir a falta de ${brl(f.cents)} prevista para ${dm(f.date)}",
                (f.causa?.let { "\"$it\" vence" } ?: "Uma saída vence") + " antes da entrada que a cobriria. Nada é livre $untilLabel.",
            ))
        }
        allocations.forEach { a ->
            add(when (a.target) {
                AllocationTarget.Debt -> AiReplyFormat.Decision("Amortizar ${brl(a.cents)} em \"${a.name}\"", "${a.reason.replaceFirstChar { it.uppercase() }}. É o destino da sobra $untilLabel.")
                AllocationTarget.Goal -> AiReplyFormat.Decision("Guardar ${brl(a.cents)} para \"${a.name}\"", "${a.reason.replaceFirstChar { it.uppercase() }}. Cabe no livre $untilLabel.")
            })
        }
        budgetsOver.forEach { add(AiReplyFormat.Decision("Segurar os gastos em $it", "O limite da categoria já estourou este mês.")) }
        unusedSubscriptions.forEach { add(AiReplyFormat.Decision("Cancelar $it", "Assinatura sem uso recente.")) }
    }.take(3)

    /**
     * O plano como a IA recebe: curto, com os quatro conceitos e o destino. É a única fonte de
     * números de dinheiro livre no prompt; a IA não decide destino nem valor.
     */
    fun aiBlock(): String = buildString {
        appendLine("Plano financeiro calculado pelo app, $untilLabel (a IA explica; não muda destino nem valores):")
        appendLine("- Balanço do mês na Agenda: ${brl(saldoDoMesCents)}.")
        appendLine("- Comprometido (ainda vai sair $untilLabel): ${brl(comprometidoCents)}.")
        if (reservadoFuturoCents > 0) {
            appendLine("- Reservado para obrigações que vencem antes da próxima entrada: ${brl(reservadoFuturoCents)}.")
        }
        appendLine("- Realmente disponível: ${brl(disponivelCents)}.")
        shortfall?.let { appendLine("- Falta prevista: ${brl(it.cents)} a partir de ${dm(it.date)}" + (it.causa?.let { c -> " ($c)" } ?: "") + ".") }
        if (allocations.isEmpty()) appendLine("- Destino: nenhum.")
        else allocations.forEach { appendLine("- Destino: ${brl(it.cents)} para \"${it.name}\" (${it.reason}).") }
        appendLine("- Livre para o dia a dia: ${brl(livreParaGastarCents)}.")
        val semAporte = goals.filter { it.missingCents > 0 && forGoal(it.objetivo.id) == 0L }.map { "\"${it.objetivo.nome}\"" }
        if (semAporte.isNotEmpty()) appendLine("- Metas sem aporte neste período: ${semAporte.joinToString()}.")
        append("Regra: cada real tem um destino só. Não recomende o mesmo dinheiro para outro destino; se o usuário quiser mudar, diga o que deixa de receber.")
    }

    companion object {
        /** 3% ao mês (≈ 42% ao ano): acima disso, amortizar rende mais do que guardar para meta. */
        const val EXPENSIVE_DEBT_BASIS_POINTS = 300

        private fun brl(cents: Long) = formatBrl0(cents / 100.0)
        private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
        private fun rate(bp: Int) = "%.1f".format(java.util.Locale("pt", "BR"), bp / 100.0)

        /**
         * Divide [allocatableCents] entre dívidas caras e metas. Cada real sai de [allocatableCents]
         * uma vez só — é o que impede a mesma sobra de aparecer em dois lugares.
         */
        fun allocate(allocatableCents: Long, activeDebts: List<DividaEntity>, goals: List<GoalPlan>): List<Allocation> {
            var left = allocatableCents.coerceAtLeast(0)
            val out = mutableListOf<Allocation>()
            activeDebts
                .filter { it.taxaJurosMensalBasisPoints >= EXPENSIVE_DEBT_BASIS_POINTS && it.valorAbertoCentavos > 0 }
                .sortedByDescending { it.taxaJurosMensalBasisPoints }
                .forEach { d ->
                    val amount = minOf(left, d.valorAbertoCentavos)
                    if (amount <= 0) return@forEach
                    out += Allocation(AllocationTarget.Debt, d.id, d.nome, amount, "juros de ${rate(d.taxaJurosMensalBasisPoints)}% ao mês, mais caro do que qualquer rendimento de meta")
                    left -= amount
                }
            goals.filter { it.missingCents > 0 }.sortedBy { it.objetivo.prioridade }.forEach { g ->
                val amount = minOf(left, g.monthlyContributionNeededCents)
                if (amount <= 0) return@forEach
                out += Allocation(AllocationTarget.Goal, g.objetivo.id, g.objetivo.nome, amount, "prioridade ${g.objetivo.prioridade} entre as metas")
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
            val disponivel: Long
            val shortfall: CycleShortfall?
            val until: LocalDate
            var monthBase = 0L
            if (cycle != null) {
                // Simulação dia a dia: o disponível é o menor saldo previsto até a véspera do salário.
                val floor = cycle.floor.cents
                disponivel = floor.coerceAtLeast(0)
                reservado = (cycle.livreCents.coerceAtLeast(0) - disponivel).coerceAtLeast(0)
                comprometido = cycle.comprometidoCents
                shortfall = cycle.shortfall
                until = cycle.proximo.minusDays(1)
            } else {
                // Sem renda cadastrada não há como saber quando o dinheiro entra: a conta é do
                // mês inteiro, e fica com o menor entre a folga do mês e o balanço da Agenda
                // (que também desconta as parcelas de dívida).
                val month = SafeToSpendCalculator.calculate(contas, transacoes, 0, today)
                val base = minOf(month.slackThisMonthCents, flow.saldoCents)
                monthBase = base
                disponivel = base.coerceAtLeast(0)
                reservado = 0
                comprometido = month.monthBillsCents + flow.debtsDue.sumOf { it.valorCentavos }
                shortfall = month.shortfall ?: if (base < 0) CycleShortfall(today, -base) else null
                until = today.withDayOfMonth(today.lengthOfMonth())
            }

            val allocatable = if (shortfall != null) 0L else minOf(disponivel, capacity.coerceAtLeast(0))
            val allocations = allocate(allocatable, activeDebts, rawGoals.filterNot { Completion.isDone(it.objetivo) })
            val alocado = allocations.sumOf { it.cents }
            val goals = rawGoals.map { g ->
                g.copy(monthlyContributionFundedCents = allocations.filter { it.target == AllocationTarget.Goal && it.id == g.objetivo.id }.sumOf { it.cents })
            }
            val safe = cycle?.let { SafeToSpendCalculator.fromCycle(it, alocado) }
                ?: SafeToSpendCalculator.calculate(contas, transacoes, alocado, today).let { s ->
                    // Mesma base do disponível: o "pode gastar" nunca passa do que o plano deixou livre.
                    val slack = monthBase - alocado
                    s.copy(
                        slackThisMonthCents = slack,
                        safeTodayCents = (slack / s.daysRemaining).coerceAtLeast(0),
                        reservedForPlanCents = alocado,
                        shortfall = shortfall,
                    )
                }

            return FinancialPlan(
                today = today,
                cycle = cycle,
                until = until,
                saldoDoMesCents = flow.saldoCents,
                comprometidoCents = comprometido,
                reservadoFuturoCents = reservado,
                disponivelCents = disponivel,
                capacidadeMensalCents = capacity,
                shortfall = shortfall,
                allocations = allocations,
                goals = goals,
                debts = DebtCalculator.summarize(activeDebts, today),
                safe = safe,
                projection = projection,
            )
        }
    }
}
