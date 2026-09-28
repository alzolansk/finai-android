package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AlertSeverity { Urgent, Warning, Positive }

/** Do que o aviso trata — decide o ícone e para qual tela o painel de avisos leva. */
enum class AlertKind { Bill, Budget, Subscription, Income, Goal }

data class FinanceAlert(
    val id: String,
    val severity: AlertSeverity,
    val title: String,
    val body: String,
    val kind: AlertKind,
)

/**
 * Os avisos que o sino da topbar mostra e que a rotina da Fase 5
 * (`FinanceCheckWorker`) usa para decidir se vale notificar — planning.md
 * §3.9/§9 ("fatura vencendo, categoria perto do limite, entrada extra
 * confirmada, alterações relevantes em metas").
 *
 * Tudo aqui é regra determinística sobre o que já está no Room (planning.md
 * §6): nenhuma chamada de IA e nenhum texto fixo de protótipo — a IA só entra
 * depois, redigindo o texto final da notificação
 * ([com.finai.app.data.notifications.NotificationContentBuilder]), nunca
 * decidindo se um evento existe. [FinanceAlert.id] é estável por evento (não
 * inclui a data) — é a chave que a Fase 5 usa para não notificar duas vezes o
 * mesmo evento no mesmo dia.
 */
object AlertCalculator {

    private const val DUE_SOON_DAYS = 3L
    private const val INCOME_HORIZON_DAYS = 7L
    private const val INCOME_CONFIRMED_LOOKBACK_DAYS = 3L

    fun alerts(
        contas: List<ContaEntity>,
        budgets: List<BudgetProgress>,
        subscriptions: List<SubscriptionInsight>,
        goals: List<GoalPlan> = emptyList(),
        today: LocalDate = LocalDate.now(),
        shortfall: CycleShortfall? = null,
    ): List<FinanceAlert> {
        val result = mutableListOf<FinanceAlert>()

        // A falta prevista no ciclo é o aviso mais importante: vem antes das contas uma a uma.
        shortfall?.let { falta ->
            val quando = if (falta.date.isAfter(today)) "em ${formatDayMonthPt(falta.date)}" else "desde ${formatDayMonthPt(falta.date)}"
            result += FinanceAlert(
                id = "falta-ciclo",
                kind = AlertKind.Bill,
                severity = AlertSeverity.Urgent,
                title = "Vai faltar dinheiro antes do salário",
                body = "Faltam até ${formatBrl0(centsToReais(falta.cents))} $quando" +
                    (falta.causa?.let { ", quando sai $it" } ?: "") + ". Vale adiar um gasto ou rever a data de uma conta.",
            )
        }

        val aPagarEmAberto = contas.filter { it.tipo == "a_pagar" && it.status != "pago" }

        aPagarEmAberto
            .filter { BillStatusCalculator.of(it, today) == EffectiveBillStatus.ATRASADO }
            .sortedBy { it.vencimento }
            .forEach { conta ->
                val dias = ChronoUnit.DAYS.between(conta.vencimento.toLocalDate(), today)
                result += FinanceAlert(
                    id = "atrasado:${conta.id}",
                    kind = AlertKind.Bill,
                    severity = AlertSeverity.Urgent,
                    title = "${conta.nome} está atrasada",
                    body = "${formatBrl0(centsToReais(conta.valorCentavos))} venceu há $dias dia(s). Juros e multa correm enquanto não for paga.",
                )
            }

        aPagarEmAberto
            .filter {
                val dias = ChronoUnit.DAYS.between(today, it.vencimento.toLocalDate())
                dias in 0..DUE_SOON_DAYS
            }
            .sortedBy { it.vencimento }
            .forEach { conta ->
                val dias = ChronoUnit.DAYS.between(today, conta.vencimento.toLocalDate())
                result += FinanceAlert(
                    id = "vencendo:${conta.id}",
                    kind = AlertKind.Bill,
                    severity = AlertSeverity.Urgent,
                    title = if (dias == 0L) "${conta.nome} vence hoje" else "${conta.nome} vence em $dias dia(s)",
                    body = "${formatBrl0(centsToReais(conta.valorCentavos))} a pagar em ${formatDayMonthPt(conta.vencimento.toLocalDate())}.",
                )
            }

        budgets.filter { it.limitCents > 0 }.forEach { budget ->
            when (budget.tone) {
                BudgetTone.Over -> result += FinanceAlert(
                    id = "orcamento-estourado:${budget.categoria}",
                    kind = AlertKind.Budget,
                    severity = AlertSeverity.Warning,
                    title = "${budget.categoria} passou do limite",
                    body = "Você já gastou ${formatBrl0(centsToReais(budget.spentCents))} de " +
                        "${formatBrl0(centsToReais(budget.limitCents))} neste mês.",
                )
                BudgetTone.Warn -> {
                    val projetado = budget.projectedEndOfMonthCents ?: return@forEach
                    result += FinanceAlert(
                        id = "orcamento-ritmo:${budget.categoria}",
                        kind = AlertKind.Budget,
                        severity = AlertSeverity.Warning,
                        title = "${budget.categoria} perto do limite",
                        body = "No ritmo atual o mês fecha em ${formatBrl0(centsToReais(projetado))}, " +
                            "${formatBrl0(centsToReais(projetado - budget.limitCents))} acima do limite.",
                    )
                }
                BudgetTone.Ok -> Unit
            }
        }

        subscriptions.filter { it.looksUnused }.forEach { insight ->
            val quando = insight.daysSinceLastUse?.let { "sem uso há $it dias" } ?: "sem registro de uso"
            result += FinanceAlert(
                id = "assinatura:${insight.assinatura.id}",
                kind = AlertKind.Subscription,
                severity = AlertSeverity.Warning,
                title = "${insight.assinatura.nome} $quando",
                body = "${formatBrl0(centsToReais(insight.assinatura.valorCentavos))}/mês continuam saindo. Vale revisar em Limites.",
            )
        }

        contas
            .filter { it.tipo == "a_receber" && it.status != "pago" }
            .filter {
                val dias = ChronoUnit.DAYS.between(today, it.vencimento.toLocalDate())
                dias in 0..INCOME_HORIZON_DAYS
            }
            .sortedBy { it.vencimento }
            .forEach { conta ->
                result += FinanceAlert(
                    id = "entrada:${conta.id}",
                    kind = AlertKind.Income,
                    severity = AlertSeverity.Positive,
                    title = "${conta.nome} previsto para ${formatDayMonthPt(conta.vencimento.toLocalDate())}",
                    body = "+ ${formatBrl0(centsToReais(conta.valorCentavos))} entram nos próximos dias.",
                )
            }

        // "Entrada extra confirmada" (planning.md §3.9): diferente do aviso acima (previsão), este é uma
        // conta a receber avulsa (não recorrente — salário mensal não é "extra") que já foi marcada como
        // recebida (status "pago") recentemente. Hoje isso só acontece via edição manual da conta — não há
        // ainda uma ação de "marcar como recebido" na Agenda além do botão de contas a pagar.
        contas
            .filter { it.tipo == "a_receber" && !it.recorrente && it.status == "pago" }
            .filter {
                val dias = ChronoUnit.DAYS.between(it.vencimento.toLocalDate(), today)
                dias in 0..INCOME_CONFIRMED_LOOKBACK_DAYS
            }
            .sortedBy { it.vencimento }
            .forEach { conta ->
                result += FinanceAlert(
                    id = "entrada-confirmada:${conta.id}",
                    kind = AlertKind.Income,
                    severity = AlertSeverity.Positive,
                    title = "${conta.nome} confirmada",
                    body = "+ ${formatBrl0(centsToReais(conta.valorCentavos))} entraram. Vale decidir o destino desse valor " +
                        "— um objetivo ou a reserva de emergência, por exemplo.",
                )
            }

        goals.filter { it.status != GoalStatus.OnTrack }.forEach { plan ->
            val nome = plan.objetivo.nome
            when (plan.status) {
                GoalStatus.Reassess -> result += FinanceAlert(
                    id = "meta:${plan.objetivo.id}:reavaliar",
                    kind = AlertKind.Goal,
                    severity = AlertSeverity.Warning,
                    title = "\"$nome\" precisa de atenção",
                    body = "Esse objetivo está dividindo a capacidade de poupança do mês com outro(s). " +
                        "Vale reavaliar o prazo ou redirecionar um valor extra para ele.",
                )
                GoalStatus.Priority -> result += FinanceAlert(
                    id = "meta:${plan.objetivo.id}:prioridade",
                    kind = AlertKind.Goal,
                    severity = AlertSeverity.Warning,
                    title = "\"$nome\" sem poupança disponível este mês",
                    body = "Os outros objetivos já usaram toda a capacidade de poupança mensal. " +
                        "Sem um aporte extra, o prazo desse objetivo deve atrasar.",
                )
                GoalStatus.OnTrack -> Unit
            }
        }

        return result.sortedBy { it.severity.ordinal }
    }

    private fun formatDayMonthPt(date: LocalDate): String =
        "%02d/%02d".format(date.dayOfMonth, date.monthValue)
}
