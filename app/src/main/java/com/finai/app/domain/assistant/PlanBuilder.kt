package com.finai.app.domain.assistant

import com.finai.app.data.local.entity.AcompanhamentoEntity
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.TransactionType
import com.finai.app.domain.balance.FreeToSpend
import com.finai.app.domain.plans.DebtPrepayment
import com.finai.app.domain.plans.GoalConflictResolver
import com.finai.app.domain.projection.CashFlowProjection
import com.finai.app.domain.projection.PaymentWindow
import com.finai.app.domain.projection.VariableProvision
import com.finai.app.domain.spending.SpendingAnalysis
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.transactionsInMonth
import java.time.LocalDate
import java.time.YearMonth

/**
 * Plano do mês como foi aceito. Texto simples, uma chave por linha, para o domínio ler sem JSON:
 * `renda=…`, `fixos=…`, `variavel=…`, `meta:<id>:<nome>=…`, `limite:<categoria>=…`.
 */
data class SavedPlan(
    val month: YearMonth,
    val incomeCents: Long,
    val fixedCents: Long,
    val variableCents: Long,
    val goals: List<Triple<Long, String, Long>>,
    val limits: Map<String, Long>,
) {
    fun encode(): String = buildString {
        appendLine("mes=$month"); appendLine("renda=$incomeCents"); appendLine("fixos=$fixedCents"); appendLine("variavel=$variableCents")
        goals.forEach { (id, name, cents) -> appendLine("meta:$id:${name.replace('=', ' ').replace('\n', ' ')}=$cents") }
        limits.forEach { (cat, cents) -> appendLine("limite:$cat=$cents") }
    }.trim()

    companion object {
        fun decode(text: String): SavedPlan? {
            val map = text.lines().mapNotNull { l -> l.substringBeforeLast('=', "").takeIf { it.isNotBlank() }?.let { it to l.substringAfterLast('=') } }
            val month = map.firstOrNull { it.first == "mes" }?.second?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: return null
            fun num(key: String) = map.firstOrNull { it.first == key }?.second?.toLongOrNull() ?: 0L
            return SavedPlan(
                month, num("renda"), num("fixos"), num("variavel"),
                goals = map.filter { it.first.startsWith("meta:") }.mapNotNull { (k, v) ->
                    val parts = k.split(':', limit = 3)
                    Triple(parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null, parts.getOrNull(2).orEmpty(), v.toLongOrNull() ?: return@mapNotNull null)
                },
                limits = map.filter { it.first.startsWith("limite:") }.mapNotNull { (k, v) -> v.toLongOrNull()?.let { k.removePrefix("limite:") to it } }.toMap(),
            )
        }

        /** O plano do mês de hoje, pelos números atuais: é isto que "Aceitar este plano" guarda. */
        fun of(s: AssistantSnapshot): SavedPlan {
            val month = YearMonth.from(s.today)
            val flow = MonthCashFlow.of(s.contas, s.transacoes, s.dividas, month, s.today)
            val fixed = flow.payable.filter { it.recorrente }.sumOf { it.valorCentavos } + flow.recorrentes.filter { it.tipo == TransactionType.Gasto.name }.sumOf { it.valorCentavos } +
                flow.debtsDue.sumOf { it.valorCentavos }
            val variable = maxOf(s.capacity.variableAverageCents, VariableProvision.variable(flow))
            val fits = s.capacity.cabeGuardarCents
            var left = fits
            val goals = GoalConflictResolver.asks(s.objetivos, s.today).sortedBy { it.priority }.map { ask ->
                val give = minOf(ask.monthlyCents, left).coerceAtLeast(0); left -= give
                Triple(ask.id, ask.name, give)
            }.filter { it.third > 0 }
            return SavedPlan(month, flow.toGetCents, fixed, variable, goals, s.orcamentos.filter { it.mesReferencia == month.toString() }.associate { it.categoria to it.limiteMensalCentavos })
        }
    }
}

/**
 * Plano conjunto (§5.2/§5.6): uma ordem de ações em que cada real é destinado uma vez. Tudo
 * determinístico, sobre o mesmo retrato: falta prevista e atrasos primeiro, depois dívida cara,
 * depois metas por prioridade até o que cabe guardar, por último limites estourados.
 */
object PlanBuilder {
    private fun brl(c: Long) = Fmt.money(c)
    private fun monthName(m: YearMonth) = MONTH_NAMES_PT[m.monthValue - 1].lowercase()

    fun build(s: AssistantSnapshot, windfallCents: Long? = null): ToolResult {
        val today = s.today
        val free = FreeToSpend.of(s.contas, s.transacoes, s.dividas, today, s.marginCents)
        val steps = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var pool = windfallCents ?: 0L
        val usingWindfall = windfallCents != null && windfallCents > 0

        // 1. Falta prevista.
        val short = free?.shortfall
        if (short != null) {
            val cover = if (usingWindfall) minOf(pool, short.cents) else 0L
            pool -= cover
            steps += if (cover > 0) "Cobrir a falta de ${brl(short.cents)} prevista para ${Fmt.dm(short.date)}: ${brl(cover)} do valor extra ficam em conta."
            else "Resolver a falta de ${brl(short.cents)} prevista para ${Fmt.dm(short.date)}" + (short.causa?.let { " (${Fmt.clean(it)})" } ?: "") + " antes de qualquer aporte: adiar um gasto ou negociar a data."
        }
        // 2. Atrasados.
        val projection = CashFlowProjection.of(s.contas, s.transacoes, s.dividas, today, today.plusDays(45))
        val overdue = projection.movements.filter { it.cents < 0 && it.movedFrom != null }
        if (overdue.isNotEmpty()) steps += "Pagar o que está atrasado: " + overdue.take(4).joinToString("; ") { "${Fmt.clean(it.descricao)} ${brl(-it.cents)} (venceu ${Fmt.dm(it.movedFrom!!)})" } + "."

        // 3. Dívida cara.
        val expensive = s.openDebts.filter { it.taxaJurosMensalBasisPoints >= DebtPrepayment.EXPENSIVE_BASIS_POINTS }.sortedByDescending { it.taxaJurosMensalBasisPoints }
        expensive.firstOrNull()?.let { d ->
            val rate = "%.1f".format(d.taxaJurosMensalBasisPoints / 100.0).replace('.', ',')
            if (usingWindfall && pool > 0) {
                val use = minOf(pool, d.valorAbertoCentavos); pool -= use
                val saved = DebtPrepayment.opportunity(d, use)
                steps += "Usar ${brl(use)} do valor extra para amortizar \"${Fmt.clean(d.nome)}\" ($rate% ao mês)" + (saved?.let { ": elimina ${it.installmentsCut} parcela(s) e evita cerca de ${brl(it.savedCents)} em juros (estimativa)." } ?: ".")
            } else {
                val end = (DebtSchedule.lastInstallmentMonth(d, today) ?: YearMonth.from(today).plusMonths(6)).atEndOfMonth()
                val w = PaymentWindow.of(CashFlowProjection.of(s.contas, s.transacoes, s.dividas.filter { it.id != d.id }, today, maxOf(end, today.plusMonths(6))), d.valorAbertoCentavos, s.marginCents)
                steps += "Priorizar \"${Fmt.clean(d.nome)}\" ($rate% ao mês, ${brl(d.valorAbertoCentavos)} em aberto) antes de aumentar aportes: " +
                    (w.firstWithMargin?.let { "a quitação cabe com a margem a partir de ${Fmt.date(it, today)}." } ?: "a quitação inteira ainda não cabe; antecipar parcelas quando sobrar livre.")
            }
        }
        // 4. Metas.
        val asks = GoalConflictResolver.asks(s.objetivos, today).sortedBy { it.priority }
        val fits = s.capacity.cabeGuardarCents
        val asked = asks.sumOf { it.monthlyCents }
        if (asks.isNotEmpty()) {
            if (short != null && !usingWindfall) notes += "Com falta prevista, o plano não sugere aporte neste mês."
            else {
                var left = if (usingWindfall) pool else fits
                asks.forEach { ask ->
                    val give = minOf(ask.monthlyCents, left).coerceAtLeast(0); left -= give
                    if (usingWindfall) { if (give > 0) steps += "Guardar ${brl(give)} do valor extra em \"${Fmt.clean(ask.name)}\"." }
                    else steps += if (give >= ask.monthlyCents) "Guardar ${brl(give)} por mês em \"${Fmt.clean(ask.name)}\"" + (ask.deadline?.let { " (prazo ${monthName(it)}/${it.year})." } ?: ".")
                    else "\"${Fmt.clean(ask.name)}\" pede ${brl(ask.monthlyCents)} por mês e só cabem ${brl(give)}: " +
                        (GoalConflictResolver.resolve(s.objetivos, fits, today)?.takeIf { it.goalId == ask.id }?.newDeadline?.let { "adiando o prazo para ${monthName(it)}/${it.year}, todas as metas cabem." } ?: "rever o prazo ou o valor.")
                }
                if (usingWindfall) pool = left
            }
            notes += "Metas pedem ${brl(asked)} por mês; cabe guardar ${brl(fits)} por mês (média dos próximos 6 meses" + (if (s.capacity.usesEstimatedSalary) ", com salário estimado" else "") + ")."
        }
        // 5. Limites.
        val over = AssistantTools.budgets(s).filter { it.spentCents > it.limitCents && it.limitCents > 0 }
        if (over.isNotEmpty()) steps += "Segurar " + over.joinToString(", ") { "${it.categoria} (${brl(it.spentCents)} de ${brl(it.limitCents)})" } + " até o fim do mês: já passou do limite."
        if (usingWindfall && pool > 0) steps += "Sobram ${brl(pool)} do valor extra: ficam em conta, como folga."
        if (steps.isEmpty()) steps += "Nada pede ação agora: sem falta prevista, sem atraso e sem dívida cara. O livre continua disponível para o dia a dia."
        if (free == null) notes += "Sem renda cadastrada, não dá para calcular o livre nem a sobra dos próximos meses."
        notes += "Recomendação não mexe no saldo: o valor só sai quando o pagamento ou o aporte for registrado."

        val title = if (usingWindfall) "Como usar ${brl(windfallCents!!)}" else "Plano para ${monthName(YearMonth.from(today))}"
        val facts = buildString {
            appendLine("$title, montado pelo app (cada real destinado uma vez; ordem: falta, atrasos, dívida cara, metas por prioridade, limites):")
            steps.forEachIndexed { i, st -> appendLine("${i + 1}. $st") }
            notes.forEach { appendLine(it) }
        }.trim()
        val answer = AssistantAnswer(
            AnswerKind.Plan, title, headline = free?.let { if (it.shortfall != null) "Primeiro: resolver a falta de ${brl(it.shortfall.cents)}" else "Livre até ${Fmt.dm(it.windowEnd)}: ${brl(it.freeCents)}" } ?: "",
            evidence = steps.mapIndexed { i, st -> "${i + 1}. $st" }, notes = notes,
            options = if (usingWindfall) emptyList() else listOf(AnswerOption("Aceitar este plano", "Aceito este plano, salve para acompanhar")),
        )
        return ToolResult(title.replaceFirstChar { it.lowercase() }, facts, answer)
    }

    /** "Estou seguindo o plano?": o realizado contra o plano salvo naquele dia, sem refazer o previsto. */
    fun follow(s: AssistantSnapshot): ToolResult {
        val today = s.today
        val month = YearMonth.from(today)
        val row = s.planos.filter { it.mes == month.toString() }.maxByOrNull { it.criadoEm } ?: s.planos.maxByOrNull { it.criadoEm }
        val plan = row?.let { SavedPlan.decode(it.conteudo) }
            ?: return ToolResult("acompanhamento do plano", "Não há plano aceito salvo. Sem ele não existe \"previsto\" para comparar: o app não reconstrói o plano de antes com os dados de hoje.",
                AssistantAnswer(AnswerKind.Question, "Ainda não há plano salvo", headline = "Para comparar o realizado, preciso de um plano aceito.", options = listOf(AnswerOption("Montar um plano", "Monte um plano para este mês"))))
        val saved = row.criadoEm.toLocalDate()
        val entries = s.transacoes.transactionsInMonth(plan.month.atDay(1)).filter { !it.data.toLocalDate().isAfter(today) }
        val income = entries.filter { it.tipo == TransactionType.Receita.name }.sumOf { it.valorCentavos }
        val variable = entries.filter { SpendingAnalysis.isConsumption(it) && !it.recorrente }.sumOf { it.valorCentavos }
        val goalRows = plan.goals.map { (id, name, planned) -> Triple(name, planned, entries.filter { it.objetivoId == id }.sumOf { it.valorCentavos }) }
        val byCat = entries.filter(SpendingAnalysis::isConsumption).groupBy { it.categoria }.mapValues { e -> e.value.sumOf { it.valorCentavos } }
        val over = plan.limits.filter { (cat, limit) -> (byCat[cat] ?: 0) > limit }
        val closed = plan.month < month
        val facts = buildString {
            appendLine("Plano de ${monthName(plan.month)} salvo em ${Fmt.date(saved, today)}, comparado com o realizado até ${if (closed) "o fim do mês" else Fmt.dm(today)}:")
            appendLine("- Renda: prevista ${brl(plan.incomeCents)}, recebida ${brl(income)}.")
            appendLine("- Gasto do dia a dia: previsto ${brl(plan.variableCents)}, realizado ${brl(variable)}.")
            goalRows.forEach { (name, planned, done) -> appendLine("- Meta \"${Fmt.clean(name)}\": planejado ${brl(planned)}, guardado ${brl(done)}.") }
            over.forEach { (cat, limit) -> appendLine("- Limite de $cat (${brl(limit)}) passado: ${brl(byCat[cat] ?: 0)}.") }
            if (!closed) append("O mês ainda não acabou: gasto abaixo do previsto não quer dizer sobra garantida.")
        }.trim()
        val onTrack = variable <= plan.variableCents && over.isEmpty()
        val answer = AssistantAnswer(
            AnswerKind.Plan, "Plano de ${monthName(plan.month)} × realizado",
            headline = if (onTrack) "Dentro do plano até aqui" else "Fora do plano em ${listOfNotNull("gasto do dia a dia".takeIf { variable > plan.variableCents }, "limites".takeIf { over.isNotEmpty() }).joinToString(" e ")}",
            metrics = listOf(
                AnswerMetric("Renda", "${brl(income)} de ${brl(plan.incomeCents)}"),
                AnswerMetric("Dia a dia", "${brl(variable)} de ${brl(plan.variableCents)}", if (variable > plan.variableCents) Tone.Bad else Tone.Good),
            ) + goalRows.map { (name, planned, done) -> AnswerMetric(Fmt.clean(name), "${brl(done)} de ${brl(planned)}", if (done >= planned) Tone.Good else Tone.Neutral) },
            evidence = over.map { (cat, limit) -> "$cat: ${brl(byCat[cat] ?: 0)} de ${brl(limit)}" },
            notes = listOfNotNull("Plano salvo em ${Fmt.date(saved, today)}; o previsto é o daquele dia.", "O mês ainda não acabou.".takeIf { !closed }),
        )
        return ToolResult("plano × realizado", facts, answer)
    }
}

/** Resultado de avaliar um acompanhamento num retrato. */
data class WatchOutcome(val rule: AcompanhamentoEntity, val met: Boolean, val title: String, val detail: String)

/**
 * Acompanhamentos pedidos no chat (§5.9). Cada regra é reavaliada contra o retrato atual; o aviso
 * só sai quando a condição passa a valer, e a regra pode ser pausada ou cancelada.
 */
object WatchRules {
    const val PAYOFF = "quitacao"
    const val PURCHASE = "compra"
    const val CATEGORY = "categoria"
    const val BALANCE = "saldo"
    const val ACTIVE = "ativo"
    const val PAUSED = "pausado"
    const val MET = "atendido"
    const val WAITING = "aguardando"

    fun describe(r: AcompanhamentoEntity): String = when (r.tipo) {
        PAYOFF -> "Avisar quando couber quitar ${r.alvo}"
        PURCHASE -> "Avisar quando couber ${Fmt.money(r.valorCentavos)}" + (r.alvo.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
        CATEGORY -> "Avisar quando ${r.alvo} passar de ${Fmt.money(r.valorCentavos)} no mês"
        else -> "Avisar se o saldo previsto ficar abaixo de ${Fmt.money(r.valorCentavos)}"
    }

    fun evaluate(r: AcompanhamentoEntity, s: AssistantSnapshot): WatchOutcome {
        val today = s.today
        return when (r.tipo) {
            PAYOFF -> {
                val debt = s.openDebts.firstOrNull { it.id == r.alvoId } ?: s.openDebts.firstOrNull { Fmt.fold(it.nome) == Fmt.fold(r.alvo) }
                if (debt == null) WatchOutcome(r, false, "", "A dívida não está mais em aberto.")
                else {
                    val amount = if (r.valorCentavos > 0) r.valorCentavos else debt.valorAbertoCentavos
                    val p = CashFlowProjection.of(s.contas, s.transacoes, s.dividas.filter { it.id != debt.id }, today, today.plusMonths(6))
                    val fits = PaymentWindow.of(p, amount, s.marginCents).firstWithMargin == today
                    WatchOutcome(r, fits, "Já dá para quitar ${debt.nome}", "Pagar ${Fmt.money(amount)} hoje mantém a margem de ${Fmt.money(s.marginCents)} nos próximos 6 meses, sem tocar em reserva ou metas.")
                }
            }
            PURCHASE -> {
                val p = CashFlowProjection.of(s.contas, s.transacoes, s.dividas, today, today.plusMonths(6))
                val fits = PaymentWindow.of(p, r.valorCentavos, s.marginCents).firstWithMargin == today
                WatchOutcome(r, fits, "Já cabe: ${r.alvo.ifBlank { Fmt.money(r.valorCentavos) }}", "${Fmt.money(r.valorCentavos)} cabem hoje mantendo a margem de ${Fmt.money(s.marginCents)}.")
            }
            CATEGORY -> {
                val spent = SpendingAnalysis.monthEntries(s.transacoes, YearMonth.from(today)).filter { it.categoria == r.alvo && !it.data.toLocalDate().isAfter(today) }.sumOf { it.valorCentavos }
                // A chave do mês faz o aviso valer de novo no mês seguinte.
                WatchOutcome(r, spent > r.valorCentavos, "${r.alvo} passou de ${Fmt.money(r.valorCentavos)}", "Já são ${Fmt.money(spent)} em ${r.alvo} neste mês.")
            }
            else -> {
                val free = FreeToSpend.of(s.contas, s.transacoes, s.dividas, today, s.marginCents)
                val low = free?.lowest?.cents ?: CashFlowProjection.of(s.contas, s.transacoes, s.dividas, today, today.plusDays(30)).lowest.cents
                WatchOutcome(r, low < r.valorCentavos, "Saldo previsto abaixo de ${Fmt.money(r.valorCentavos)}", "O menor saldo previsto é ${Fmt.money(low)}.")
            }
        }
    }

    /** Chave do resultado: muda quando o aviso deve sair de novo (outro mês na regra de categoria). */
    fun resultKey(o: WatchOutcome, today: LocalDate): String =
        if (!o.met) WAITING else if (o.rule.tipo == CATEGORY) "$MET:${YearMonth.from(today)}" else MET

    /** Regras ativas cuja condição passou a valer agora (antes não valia, ou valia em outro mês). */
    fun newlyMet(s: AssistantSnapshot): List<WatchOutcome> =
        s.acompanhamentos.filter { it.estado == ACTIVE }.map { evaluate(it, s) }.filter { it.met && it.rule.ultimoResultado != resultKey(it, s.today) }
}
