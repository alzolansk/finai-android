package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.util.formatBrl0
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth

/** O aporte mensal que uma meta com alvo pede hoje: o que falta ÷ meses até o prazo. */
data class GoalNeed(
    val id: Long,
    val name: String,
    val neededCents: Long,
    val missingCents: Long,
    val monthsLeft: Long,
    val prazo: LocalDate,
    val prioridade: Int,
    val targetCents: Long,
    val description: String,
)

/**
 * A soma do que as metas pedem por mês, posta ao lado do que sobra por mês. É a pergunta que a
 * tela de Objetivos não respondia: "isso tudo cabe?".
 *
 * Três números separados, que não se misturam:
 * - **necessário** ([totalNeededCents]): falta ÷ meses até o prazo, por meta com alvo, em
 *   andamento e com prazo no futuro. Muda todo mês (cai com aporte, sobe se o mês passa sem).
 *   Meta concluída, reserva contínua e meta de prazo vencido ficam fora (a vencida não tem "por
 *   mês" que alcance a data; aparece à parte em [overdueNames]).
 * - **planejado** ([reservesPlannedCents] e [recommendedNowCents]): o aporte que o usuário
 *   escolheu para as reservas contínuas, e o que o plano central recomenda guardar nas metas até
 *   o salário. Recomendação não é gasto e não desconta do livre.
 * - **capacidade** ([averageSurplusCents]): média da sobra projetada mês a mês
 *   ([SavingsProjection]) nos próximos [monthsInAverage] meses, antes de qualquer aporte. Nulo
 *   sem renda cadastrada: sem renda não há como dizer o que sobra, e zero seria um palpite.
 */
data class GoalsOverview(
    val needs: List<GoalNeed>,
    val totalNeededCents: Long,
    val reservesPlannedCents: Long,
    val reservesBalanceCents: Long,
    val overdueNames: List<String>,
    val recommendedNowCents: Long,
    val averageSurplusCents: Long?,
    val lowestSurplus: MonthProjection?,
    val surplusUsesEstimatedSalary: Boolean,
    val monthsInAverage: Int,
) {
    val totalMonthlyCents: Long get() = totalNeededCents + reservesPlannedCents

    /** Positivo: quanto falta por mês para cobrir tudo. Negativo: folga. Nulo sem capacidade conhecida. */
    val gapCents: Long? get() = averageSurplusCents?.let { totalMonthlyCents - it }
}

enum class InsightSeverity { Info, Attention, Risk }

/** Para onde o botão de um ponto de atenção leva. Cada um é uma ação que já existe no app. */
enum class InsightAction { None, EditGoal, SimulateGoal, ComparePlan, OpenDebts, OpenAgenda, AddIncome }

/**
 * Um ponto que pode mudar uma decisão. Sai de regra determinística, com números calculados aqui;
 * a IA não cria nenhum deles.
 */
data class GoalInsight(
    val id: String,
    val severity: InsightSeverity,
    val title: String,
    val detail: String,
    val action: InsightAction,
    val actionLabel: String,
    /** Meta que a ação abre (editar prazo, simular). */
    val actionGoalId: Long? = null,
    /** Metas a que o ponto se refere: é o que o mostra também nos detalhes de cada uma. */
    val goalIds: Set<Long> = emptySet(),
    /** Vale para o planejamento todo (lista do topo) ou só para os detalhes da meta. */
    val global: Boolean = true,
    /**
     * Se for verdadeiro, a dispensa vale para sempre (o usuário decidiu, como "manter metas
     * separadas"). Se for falso, o ponto volta quando a situação muda, porque a chave inclui o título
     * com os números.
     */
    val permanentDismiss: Boolean = false,
    val dismissLabel: String = "Dispensar",
) {
    /** O que fica gravado quando o usuário dispensa este ponto. */
    val dismissKey: String get() = if (permanentDismiss) id else "$id|$title"
}

object GoalPlanning {
    /** Meses na média da sobra: um ano, o mesmo horizonte mínimo da projeção. */
    const val AVERAGE_MONTHS = 12

    /** Quanto adiar no cenário alternativo, em meses. */
    const val POSTPONE_MONTHS = 6L

    /** Dívida que termina dentro deste número de meses vira oportunidade. */
    const val OPPORTUNITY_MONTHS = 6L

    /** Quantos pontos o topo mostra. Mais que isso vira relatório. */
    const val MAX_GLOBAL = 3

    fun needsOf(goals: List<GoalPlan>, today: LocalDate): List<GoalNeed> = goals
        .filter { !it.objetivo.isOpenReserve && !Completion.isDone(it.objetivo) && it.missingCents > 0 }
        .filterNot { it.objetivo.prazo.toLocalDate().isBefore(today) }
        .map { g ->
            GoalNeed(
                id = g.objetivo.id, name = g.objetivo.nome, neededCents = g.monthlyContributionNeededCents,
                missingCents = g.missingCents, monthsLeft = monthsUntil(g.objetivo.prazo.toLocalDate(), today),
                prazo = g.objetivo.prazo.toLocalDate(), prioridade = g.objetivo.prioridade,
                targetCents = g.objetivo.valorAlvoCentavos, description = g.objetivo.descricao,
            )
        }

    fun overview(plan: FinancialPlan, hasIncome: Boolean, today: LocalDate): GoalsOverview {
        val needs = needsOf(plan.goals, today)
        val reserves = plan.goals.map { it.objetivo }.filter { it.isOpenReserve }
        val overdue = plan.goals
            .filter { !it.objetivo.isOpenReserve && it.missingCents > 0 && it.objetivo.prazo.toLocalDate().isBefore(today) }
            .map { it.objetivo.nome }
        val months = plan.projection.months.take(AVERAGE_MONTHS)
        val average = if (hasIncome && months.isNotEmpty()) months.sumOf { it.balanceCents } / months.size else null
        return GoalsOverview(
            needs = needs,
            totalNeededCents = needs.sumOf { it.neededCents },
            reservesPlannedCents = reserves.sumOf { it.aporteMensalCentavos },
            reservesBalanceCents = reserves.sumOf { it.valorGuardadoCentavos },
            overdueNames = overdue,
            recommendedNowCents = plan.recommendations.filter { it.target == RecommendationTarget.Goal }.sumOf { it.cents },
            averageSurplusCents = average,
            lowestSurplus = if (hasIncome) months.minByOrNull { it.balanceCents } else null,
            surplusUsesEstimatedSalary = months.any { it.estimatedSalaryCents > 0 },
            monthsInAverage = months.size,
        )
    }

    /** O aporte mensal se o prazo andar [extraMonths] meses. */
    fun neededWithExtraMonths(need: GoalNeed, extraMonths: Long): Long = need.missingCents / (need.monthsLeft + extraMonths)

    /**
     * Os pontos de atenção, do mais grave para o menos. Cada regra só dispara quando há algo que
     * muda uma decisão: nada de "meta concluída com sucesso" ou "continue guardando". Sem
     * nenhum, a lista vem vazia e a tela não mostra a seção.
     */
    /** Chave da sobreposição entre duas metas, igual nos dois sentidos. */
    fun overlapId(a: Long, b: Long) = "sobreposicao-${minOf(a, b)}-${maxOf(a, b)}"

    /** Pares de metas que o usuário confirmou serem separadas, tirados das chaves dispensadas. */
    fun confirmedSeparate(dismissed: Set<String>): Set<Pair<Long, Long>> = dismissed.mapNotNull { key ->
        key.removePrefix("sobreposicao-").takeIf { it != key }?.split("-")?.takeIf { it.size == 2 }
            ?.let { (a, b) -> a.toLongOrNull()?.let { x -> b.toLongOrNull()?.let { y -> x to y } } }
    }.toSet()

    /**
     * Os pontos que sobram depois do que o usuário dispensou, e quantos ficaram escondidos (para
     * a tela oferecer "Mostrar dispensados").
     */
    fun visible(all: List<GoalInsight>, dismissed: Set<String>): Pair<List<GoalInsight>, Int> {
        val shown = all.filterNot { it.dismissKey in dismissed }
        return shown to (all.size - shown.size)
    }

    fun insights(
        plan: FinancialPlan,
        overview: GoalsOverview,
        dividas: List<DividaEntity>,
        hasIncome: Boolean,
        today: LocalDate,
    ): List<GoalInsight> {
        val out = mutableListOf<GoalInsight>()
        val needs = overview.needs
        val total = overview.totalMonthlyCents
        val wantsMoney = total > 0
        val gap = overview.gapCents
        val activeGoals = plan.goals.filterNot { Completion.isDone(it.objetivo) }
        if (activeGoals.isEmpty()) return emptyList()

        // 1. Falta prevista antes do salário: qualquer aporte agora piora o buraco.
        plan.shortfall?.let { f ->
            out += GoalInsight(
                id = "falta", severity = InsightSeverity.Risk,
                title = "Vai faltar ${brl(f.cents)} em ${dm(f.date)}: segure os aportes",
                detail = (f.causa?.let { "\"$it\" vence" } ?: "Uma saída vence") +
                    " antes da próxima entrada. Até ${dm(plan.until)}, qualquer aporte aumenta essa falta.",
                action = InsightAction.OpenAgenda, actionLabel = "Ver compromissos",
            )
        }

        // 2. Sem renda, nenhuma conclusão sobre "cabe" é possível.
        if (!hasIncome) {
            out += GoalInsight(
                id = "sem-renda", severity = InsightSeverity.Attention,
                title = "Cadastre sua renda para saber se as metas cabem",
                detail = "Sem renda lançada, o app não sabe quanto sobra por mês. Os aportes necessários continuam calculados, mas a comparação com o orçamento fica de fora.",
                action = InsightAction.AddIncome, actionLabel = "Lançar renda",
            )
        }

        // 3. As metas pedem mais do que sobra.
        val average = overview.averageSurplusCents
        val conflict = wantsMoney && average != null && gap != null && gap > 0
        if (conflict && average != null && gap != null) {
            val estimated = if (overview.surplusUsesEstimatedSalary) " A projeção usa salário estimado nos meses sem salário lançado." else ""
            out += if (average <= 0) GoalInsight(
                id = CONFLICT_ID, severity = InsightSeverity.Risk,
                title = "Sem sobra prevista para as metas",
                detail = "A sobra média dos próximos ${overview.monthsInAverage} meses é ${brl(average)}/mês, antes de qualquer aporte. " +
                    "Os ${brl(total)}/mês das metas só cabem mudando receitas, despesas ou prazos.$estimated",
                action = InsightAction.ComparePlan, actionLabel = "Comparar cenários",
            ) else GoalInsight(
                id = CONFLICT_ID, severity = InsightSeverity.Attention,
                title = "Metas pedem ${brl(total)}/mês; sobram ${brl(average)}/mês",
                detail = "Faltam ${brl(gap)}/mês na média dos próximos ${overview.monthsInAverage} meses. " +
                    "Do jeito que está, nem todas as metas chegam no prazo.$estimated",
                action = InsightAction.ComparePlan, actionLabel = "Comparar cenários",
            )
        }

        // 4. Dívida cara vem antes de meta (mesma regra do plano central).
        plan.recommendations.firstOrNull()?.takeIf { it.target == RecommendationTarget.Debt && wantsMoney }?.let { r ->
            out += GoalInsight(
                id = "divida-primeiro", severity = InsightSeverity.Attention,
                title = "Priorize \"${r.name}\" antes de aumentar os aportes",
                detail = "${r.reason.replaceFirstChar { it.uppercase() }}. O plano recomenda ${brl(r.cents)} do livre até ${dm(plan.until)} para amortizar; o valor continua livre até você registrar o pagamento.",
                action = InsightAction.OpenDebts, actionLabel = "Ver dívidas",
            )
        }

        // 5. Pagamento antes da próxima entrada com o livre menor que os aportes pedidos.
        val cycle = plan.cycle
        if (cycle != null && plan.shortfall == null && plan.reservadoFuturoCents > 0 && wantsMoney && plan.livreCents < total) {
            cycle.entries
                .filter { it.cents < 0 && !it.done && it.date.isAfter(today) && it.date.isBefore(cycle.proximo) }
                .minByOrNull { it.date }
                ?.let { e ->
                    out += GoalInsight(
                        id = "vencimento", severity = InsightSeverity.Attention,
                        title = "Aporte no máximo ${brl(plan.livreCents.coerceAtLeast(0))} até ${dm(cycle.proximo)}",
                        detail = "\"${e.descricao}\" (${brl(-e.cents)}) vence em ${dm(e.date)}, antes da próxima entrada. " +
                            "Aportar mais que o livre agora compromete esse pagamento.",
                        action = InsightAction.OpenAgenda, actionLabel = "Ver compromissos",
                    )
                }
        }

        // 6. Cenário alternativo: adiar a meta de menor prioridade.
        if (conflict && gap != null) {
            needs.sortedWith(compareByDescending<GoalNeed> { it.prioridade }.thenByDescending { it.neededCents }).firstOrNull()?.let { n ->
                val newNeed = neededWithExtraMonths(n, POSTPONE_MONTHS)
                val newTotal = total - n.neededCents + newNeed
                val fits = average != null && newTotal <= average
                out += GoalInsight(
                    id = "adiar-${n.id}", severity = InsightSeverity.Attention,
                    title = "Adiar \"${n.name}\" $POSTPONE_MONTHS meses: ${brl(n.neededCents)} → ${brl(newNeed)}/mês",
                    detail = "Novo prazo: ${formatMonthYearLong(n.prazo.plusMonths(POSTPONE_MONTHS))}. O total das metas cai para ${brl(newTotal)}/mês" +
                        if (fits) " e passa a caber na sobra prevista." else ", e ainda faltariam ${brl(newTotal - (average ?: 0))}/mês.",
                    action = InsightAction.EditGoal, actionLabel = "Mudar prazo", actionGoalId = n.id, goalIds = setOf(n.id),
                )
            }
        }

        // 7. Duas metas que parecem o mesmo gasto (contagem dupla no total).
        overlaps(needs).forEach { (a, b) ->
            out += GoalInsight(
                id = overlapId(a.id, b.id), severity = InsightSeverity.Attention,
                title = "\"${a.name}\" e \"${b.name}\" podem ser o mesmo gasto",
                detail = "Se \"${b.name}\" já está no valor de \"${a.name}\", o total de ${brl(total)}/mês conta ${brl(b.neededCents)}/mês duas vezes. " +
                    "Escreva na descrição se ela fica fora, ou reduza o alvo.",
                action = InsightAction.EditGoal, actionLabel = "Revisar meta", actionGoalId = b.id, goalIds = setOf(a.id, b.id),
                permanentDismiss = true, dismissLabel = "Manter separadas",
            )
        }

        // 8. Prazo vencido: não existe aporte mensal que alcance a data.
        plan.goals.filter { !it.objetivo.isOpenReserve && it.missingCents > 0 && it.objetivo.prazo.toLocalDate().isBefore(today) }
            .forEach { g ->
                out += GoalInsight(
                    id = "vencida-${g.objetivo.id}", severity = InsightSeverity.Attention,
                    title = "O prazo de \"${g.objetivo.nome}\" já passou",
                    detail = "Faltam ${brl(g.missingCents)}. Com uma nova data, o app recalcula o aporte mensal e a meta volta para o total.",
                    action = InsightAction.EditGoal, actionLabel = "Mudar prazo", actionGoalId = g.objetivo.id, goalIds = setOf(g.objetivo.id),
                )
            }

        // 9. Parcela que acaba logo: margem nova nos próximos meses.
        if (wantsMoney && (conflict || overview.recommendedNowCents < overview.totalNeededCents)) {
            val current = YearMonth.from(today)
            dividas.filterNot(Completion::isPaid)
                .filter { it.parcelasTotais > 0 && it.valorParcelaCentavos > 0 }
                .mapNotNull { d -> DebtSchedule.lastInstallmentMonth(d, today)?.let { d to it } }
                .filter { (_, last) -> last >= current && last <= current.plusMonths(OPPORTUNITY_MONTHS) }
                .minByOrNull { it.second }
                ?.let { (d, last) ->
                    val freeFrom = last.plusMonths(1)
                    out += GoalInsight(
                        id = "oportunidade-${d.id}", severity = InsightSeverity.Info,
                        title = "Em ${mesAno(freeFrom)} sobram +${brl(d.valorParcelaCentavos)}/mês",
                        detail = "A última parcela de \"${d.nome}\" vence em ${mesAno(last)}. A partir daí esse valor pode ir para as metas sem apertar o orçamento (a sobra prevista já conta com isso).",
                        action = InsightAction.OpenDebts, actionLabel = "Ver dívidas",
                    )
                }
        }

        // Por meta, só nos detalhes: quanto o aporte cai com mais prazo, para metas que não cabem.
        activeGoals.filter { it.status != GoalStatus.OnTrack }.forEach { g ->
            val n = needs.firstOrNull { it.id == g.objetivo.id } ?: return@forEach
            if (out.any { it.id == "adiar-${n.id}" }) return@forEach
            val six = neededWithExtraMonths(n, POSTPONE_MONTHS)
            val twelve = neededWithExtraMonths(n, 2 * POSTPONE_MONTHS)
            out += GoalInsight(
                id = "prazo-${n.id}", severity = InsightSeverity.Info,
                title = "Com mais prazo: ${brl(six)}/mês (+6 meses) ou ${brl(twelve)}/mês (+12 meses)",
                detail = "Hoje são ${brl(n.neededCents)}/mês até ${formatMonthYearLong(n.prazo)}.",
                action = InsightAction.EditGoal, actionLabel = "Mudar prazo", actionGoalId = n.id, goalIds = setOf(n.id), global = false,
            )
        }

        return out.sortedByDescending { it.severity.ordinal }
    }

    /** Id do ponto "metas pedem mais do que sobra"; a tela já mostra isso no card do topo. */
    const val CONFLICT_ID = "conflito"

    /**
     * Pontos que tratam do livre até o salário (falta prevista, dívida antes das metas, pagamento
     * antes da próxima entrada). O mais grave deles vira a única recomendação do card do topo.
     */
    fun isPlanTip(insight: GoalInsight): Boolean = insight.id in PLAN_TIP_IDS

    private val PLAN_TIP_IDS = setOf("falta", "divida-primeiro", "vencimento")

    /** Os pontos do topo: os mais graves, até [MAX_GLOBAL]. */
    fun topInsights(all: List<GoalInsight>): List<GoalInsight> = all.filter { it.global }.take(MAX_GLOBAL)

    fun insightsFor(all: List<GoalInsight>, goalId: Long): List<GoalInsight> = all.filter { goalId in it.goalIds }

    // ── sobreposição ───────────────────────────────────────────────────

    /** Palavras genéricas que não identificam um gasto ("reserva", "viagem"...). */
    private val GENERIC = setOf(
        "reserva", "meta", "objetivo", "viagem", "compra", "entrada", "fundo", "geral", "novo", "nova",
        "minha", "meu", "para", "com", "sem", "dos", "das", "emergencia", "guardar", "poupanca",
    )

    /** O usuário já explicou na descrição que um gasto fica fora do outro. */
    private val CLARIFIED = listOf("fora", "separad", "nao inclui", "não inclui", "a parte", "à parte")

    private fun fold(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun tokens(name: String): Set<String> =
        fold(name).split(Regex("[^a-z]+")).filter { it.length >= 4 && it !in GENERIC }.toSet()

    /** Pares de metas que dividem uma palavra própria do nome, a maior primeiro. */
    fun overlaps(needs: List<GoalNeed>): List<Pair<GoalNeed, GoalNeed>> {
        val out = mutableListOf<Pair<GoalNeed, GoalNeed>>()
        for (i in needs.indices) for (j in i + 1 until needs.size) {
            val a = needs[i]
            val b = needs[j]
            if ((tokens(a.name) intersect tokens(b.name)).isEmpty()) continue
            val clarified = listOf(a.description, b.description).any { d -> CLARIFIED.any { fold(d).contains(fold(it)) } }
            if (clarified) continue
            out += if (a.targetCents >= b.targetCents) a to b else b to a
        }
        return out
    }

    // ── fatos para a IA ───────────────────────────────────────────────

    /**
     * Tudo o que a IA pode citar, já calculado. É também a lista de números que a trava
     * ([com.finai.app.domain.scenario.ScenarioReplyGuard]) aceita: valor ou data fora daqui
     * descarta a resposta. Sem nome completo, conta ou documento (planning.md §4).
     */
    fun factsBlock(
        plan: FinancialPlan,
        overview: GoalsOverview,
        insights: List<GoalInsight>,
        dividas: List<DividaEntity>,
        hasIncome: Boolean,
        today: LocalDate,
        dismissed: Set<String> = emptySet(),
    ): String = buildString {
        appendLine("Hoje: ${dm(today)}/${today.year}.")
        appendLine("CONFIRMADO (lançado pelo usuário ou calculado das datas):")
        val cycle = plan.cycle
        if (cycle != null) {
            appendLine("- Próxima renda principal: \"${cycle.salarioNome}\" em ${dm(cycle.proximo)}, ${brl(cycle.salarioCents)}" +
                (if (cycle.proximoEstimado) " (data estimada: o próximo salário não está lançado)." else "."))
        } else if (!hasIncome) {
            appendLine("- Nenhuma renda cadastrada.")
        }
        plan.shortfall?.let { appendLine("- Falta prevista: ${brl(it.cents)} em ${dm(it.date)}" + (it.causa?.let { c -> " (\"$c\")" } ?: "") + ".") }
        appendLine("- Livre até ${dm(plan.until)}: ${brl(plan.livreCents.coerceAtLeast(0))} (o que pode sair sem faltar para os compromissos até lá).")
        if (cycle != null) {
            val upcoming = cycle.entries.filter { it.cents < 0 && !it.done && it.date.isAfter(today) && it.date.isBefore(cycle.proximo) }
                .sortedBy { it.date }.take(8)
            if (upcoming.isNotEmpty()) {
                appendLine("- Pagamentos até a próxima renda: " + upcoming.joinToString("; ") { "${dm(it.date)} \"${it.descricao}\" ${brl(-it.cents)}" } + ".")
            }
        }
        plan.recommendations.forEach { appendLine("- Recomendação do plano até ${dm(plan.until)}: ${brl(it.cents)} para \"${it.name}\" (${it.reason}).") }
        val active = dividas.filterNot(Completion::isPaid)
        if (active.isNotEmpty()) {
            appendLine("- Dívidas em aberto:")
            active.forEach { d ->
                val last = DebtSchedule.lastInstallmentMonth(d, today)
                append("  - \"${d.nome}\": saldo ${brl(d.valorAbertoCentavos)}")
                if (d.valorParcelaCentavos > 0) append(", parcela ${brl(d.valorParcelaCentavos)}")
                if (d.parcelasRestantes > 0) append(", ${d.parcelasRestantes} parcela(s) restante(s)")
                if (last != null) append(", última em ${mesAno(last)}")
                if (d.taxaJurosMensalBasisPoints > 0) append(", juros ${"%.1f".format(java.util.Locale("pt", "BR"), d.taxaJurosMensalBasisPoints / 100.0)}% ao mês")
                appendLine(".")
            }
        }
        appendLine("- Metas (prioridade 1 = mais alta; necessário = falta ÷ meses até o prazo, recalculado todo mês):")
        plan.goals.filterNot { Completion.isDone(it.objetivo) }.sortedBy { it.objetivo.prioridade }.forEach { g ->
            val o = g.objetivo
            if (o.isOpenReserve) {
                appendLine("  - Reserva contínua \"${o.nome}\" (sem valor-alvo, não é concluída): saldo ${brl(o.valorGuardadoCentavos)}" +
                    (if (o.aporteMensalCentavos > 0) ", aporte planejado ${brl(o.aporteMensalCentavos)}/mês" else ", sem aporte planejado") + ".")
                return@forEach
            }
            val n = overview.needs.firstOrNull { it.id == o.id }
            append("  - P${o.prioridade} \"${o.nome}\" (${o.tipo}): guardado ${brl(o.valorGuardadoCentavos)} de ${brl(o.valorAlvoCentavos)}, prazo ${formatMonthYearLong(o.prazo.toLocalDate())}")
            if (n != null) {
                append(", necessário ${brl(n.neededCents)}/mês por ${n.monthsLeft} mês(es)")
                append(", com +6 meses ${brl(neededWithExtraMonths(n, POSTPONE_MONTHS))}/mês, com +12 meses ${brl(neededWithExtraMonths(n, 2 * POSTPONE_MONTHS))}/mês")
            } else if (g.missingCents > 0) append(", prazo vencido, faltam ${brl(g.missingCents)}")
            append(", recomendação do plano agora ${brl(plan.forGoal(o.id))}")
            if (o.descricao.isNotBlank()) append(". Descrição do usuário: ${o.descricao.trim()}")
            appendLine(".")
        }
        appendLine("- Total necessário por mês: ${brl(overview.totalNeededCents)}" +
            (if (overview.reservesPlannedCents > 0) " + ${brl(overview.reservesPlannedCents)} planejados nas reservas contínuas = ${brl(overview.totalMonthlyCents)}" else "") + ".")
        appendLine("PROJEÇÃO (estimativa, pode mudar):")
        val average = overview.averageSurplusCents
        if (average != null) {
            appendLine("- Sobra média por mês nos próximos ${overview.monthsInAverage} meses, antes de aportes: ${brl(average)}" +
                (overview.gapCents?.let { if (it > 0) " (faltam ${brl(it)}/mês para todas as metas)" else " (sobram ${brl(-it)}/mês depois das metas)" } ?: "") + ".")
            appendLine("- Sobra por mês: " + plan.projection.months.take(AVERAGE_MONTHS).joinToString("; ") { "${mesAno(it.month)} ${brl(it.balanceCents)}" } + ".")
            if (overview.surplusUsesEstimatedSalary) appendLine("- Meses sem salário lançado usam o último salário como estimativa.")
        } else {
            appendLine("- Sem renda cadastrada: não há projeção de sobra.")
        }
        appendLine("DADOS QUE O APP NÃO TEM: saldo em banco; histórico de aportes (só o total guardado em cada meta); rendimento das reservas.")
        appendLine("REGRA: dinheiro guardado em metas e reservas não é livre; só entra em outro objetivo se o usuário pedir.")
        val names = plan.goals.associate { it.objetivo.id to it.objetivo.nome }
        confirmedSeparate(dismissed).forEach { (a, b) ->
            val na = names[a] ?: return@forEach
            val nb = names[b] ?: return@forEach
            appendLine("DECISÃO DO USUÁRIO: \"$na\" e \"$nb\" são metas separadas, sem contagem dupla. Não sugira juntar nem questione isso.")
        }
        val shown = insights.filter { it.global }
        if (shown.isNotEmpty()) {
            appendLine("O USUÁRIO JÁ VÊ ESTES PONTOS NA TELA (não repita; aprofunde ou priorize):")
            shown.forEach { appendLine("- ${it.title}") }
        }
    }.trimEnd()

    private fun brl(cents: Long) = formatBrl0(cents / 100.0)
    private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    fun mesAno(m: YearMonth) = "${MONTH_NAMES_PT[m.monthValue - 1].take(3).lowercase()}/${m.year}"
}
