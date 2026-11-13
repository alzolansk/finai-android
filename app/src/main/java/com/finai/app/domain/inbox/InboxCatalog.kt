package com.finai.app.domain.inbox

import com.finai.app.data.local.entity.*
import com.finai.app.domain.*
import com.finai.app.util.formatBrl
import java.time.LocalDate
import java.time.YearMonth

/** Catálogo único para a tela e o worker. Não altera os cálculos nem grava lançamentos. */
object InboxCatalog {
    fun build(
        contas: List<ContaEntity>, transacoes: List<TransacaoEntity>, dividas: List<DividaEntity>,
        plan: FinancialPlan, budgets: List<BudgetProgress>, insights: List<GoalInsight>,
        suggestions: List<SugestaoLancamentoEntity>, today: LocalDate, coach: BehaviorPattern? = null,
        legacyAlerts: List<FinanceAlert> = emptyList(),
    ): List<InboxItem> = buildList {
        suggestions.filter { it.estado == "pendente" }.forEach { s ->
            add(InboxItem("captura-${s.id}", "captura", s.descricao.ifBlank { "Movimentação encontrada" },
                listOfNotNull(s.appNome, s.contaSugerida, s.categoria, EntryOptions.dayMonth(s.data.toLocalDate())).joinToString(" · "),
                s.valorCentavos ?: 0, InboxGroup.Confirm, suggestion = s))
        }
        // Contas antigas vencidas continuam acionáveis mesmo fora do mês da Agenda.
        val movements = CashFlowTimeline.monthMovements(contas, transacoes, dividas, today, 2) +
            contas.filter { it.status != "pago" && it.vencimento.toLocalDate().isBefore(YearMonth.from(today).atDay(1)) }
                .map { c -> TimelineMovement(c.vencimento.toLocalDate(), c.nome,
                    (if (c.tipo == "a_receber") 1 else -1) * c.valorCentavos,
                    MovementStatus.Overdue, null, CycleSource.Conta(c)) }
        movements.filter { m ->
            m.status !in setOf(MovementStatus.Done, MovementStatus.Paid, MovementStatus.Received) &&
                !m.date.isAfter(today.plusDays(if (m.cents > 0) 7 else 3)) &&
                (m.cents < 0 || !m.date.isBefore(today))
        }.forEach { m ->
            val source = m.source
            val id = when (source) {
                is CycleSource.Conta -> "conta-${source.conta.id}-${YearMonth.from(m.date)}"
                is CycleSource.Parcela -> "parcela-${source.parcela.divida.id}-${source.parcela.numero ?: YearMonth.from(m.date)}"
                is CycleSource.Lancamento -> "lancamento-${source.transacao.serieId ?: source.transacao.id}-${YearMonth.from(m.date)}"
                null -> return@forEach
            }
            val overdue = m.date.isBefore(today)
            val title = m.descricao + when {
                overdue -> " está atrasada"
                m.date == today -> if (m.cents < 0) " vence hoje" else " prevista hoje"
                else -> " · ${EntryOptions.dayMonth(m.date)}"
            }
            val body = if (m.cents < 0) "Já está na previsão do seu saldo. Confirme quando sair da conta."
                else "Entrada prevista; confirme na Agenda quando receber."
            val kind = if (m.cents < 0) "conta" else "entrada"
            val value = kotlin.math.abs(m.cents)
            val alert = FinanceAlert(id, if (m.cents < 0) AlertSeverity.Urgent else AlertSeverity.Positive,
                title, body, if (m.cents < 0) AlertKind.Bill else AlertKind.Income, value)
            add(InboxItem(id, kind, title, body, value, if (m.cents < 0) InboxGroup.Act else InboxGroup.Watch,
                m.date, if (overdue) 1 else 0, alert = alert, movement = m))
        }
        budgets.filter { it.limitCents > 0 && (it.tone != BudgetTone.Ok || it.spentCents * 10 >= it.limitCents * 9) }.forEach { b ->
            val over = b.spentCents >= b.limitCents
            val id = "limite-${b.categoria}-${YearMonth.from(today)}"
            val title = "${b.categoria} ${if (over) "atingiu o limite" else "perto do limite"}"
            val body = "${formatBrl(b.spentCents / 100.0)} de ${formatBrl(b.limitCents / 100.0)}. " +
                (b.projectedEndOfMonthCents?.let { "No ritmo atual: ${formatBrl(it / 100.0)} no mês." } ?: "Revise seus gastos do mês.")
            add(InboxItem(id, "limite", title, body, b.spentCents, level = if (over) 1 else 0,
                alert = FinanceAlert(id, AlertSeverity.Warning, title, body, AlertKind.Budget, b.spentCents)))
        }
        // A falta já aparece em um único aviso, não novamente no conjunto de metas.
        plan.shortfall?.let { f ->
            val id = "falta-${plan.until}"
            val body = "Faltam ${formatBrl(f.cents / 100.0)} em ${EntryOptions.dayMonth(f.date)}. Reveja os compromissos antes de aportar."
            add(InboxItem(id, "falta", "Saldo previsto abaixo de zero", body, f.cents, InboxGroup.Act, f.date,
                alert = FinanceAlert(id, AlertSeverity.Urgent, "Saldo previsto abaixo de zero", body, AlertKind.Bill, f.cents)))
        }
        insights.filter { it.id != "falta" }.forEach { insight ->
            val value = when {
                insight.id == "divida-primeiro" -> plan.recommendations.firstOrNull { it.target == RecommendationTarget.Debt }?.cents ?: 0
                insight.id == "vencimento" -> plan.livreCents.coerceAtLeast(0)
                insight.id == GoalPlanning.CONFLICT_ID -> plan.projection.months.take(12).let { months ->
                    val capacity = if (months.isEmpty()) 0 else months.sumOf { it.balanceCents } / months.size
                    (plan.goals.sumOf { it.monthlyContributionNeededCents } - capacity).coerceAtLeast(0)
                }
                insight.actionGoalId != null -> plan.goals.firstOrNull { it.objetivo.id == insight.actionGoalId }
                    ?.let { if (insight.id.startsWith("vencida-")) it.missingCents else it.monthlyContributionNeededCents } ?: 0
                else -> 0
            }
            val id = "meta-${insight.id}"
            add(InboxItem(id, "meta", insight.title, insight.detail, value, insight = insight,
                alert = FinanceAlert(id, AlertSeverity.Warning, insight.title, insight.detail, AlertKind.Goal, value)))
        }
        coach?.let { pattern ->
            add(InboxItem(pattern.id, "coach", pattern.title, pattern.detail,
                alert = FinanceAlert(pattern.id, AlertSeverity.Warning, pattern.title, pattern.detail, AlertKind.Budget)))
        }
        legacyAlerts.filter { it.kind == AlertKind.Subscription || it.id.startsWith("entrada-confirmada:") }.forEach { alert ->
            val id = "${alert.id}-${YearMonth.from(today)}"
            val amount = if (alert.kind == AlertKind.Income) contas.firstOrNull { "entrada-confirmada:${it.id}" == alert.id }?.valorCentavos ?: 0 else alert.valueCents
            add(InboxItem(id, if (alert.kind == AlertKind.Subscription) "assinatura" else "entrada-confirmada",
                alert.title, alert.body, amount, alert = alert.copy(id = id, valueCents = amount)))
        }
    }.distinctBy { it.id }.sortedWith(compareBy<InboxItem> { it.group.ordinal }.thenBy { it.due ?: LocalDate.MAX })
}
