package com.finai.app.state

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Bill
import com.finai.app.data.model.Budget
import com.finai.app.data.model.Debt
import com.finai.app.data.model.Goal
import com.finai.app.data.model.Subscription
import com.finai.app.data.model.TimelineEntry
import com.finai.app.data.model.WeekBill
import com.finai.app.domain.BehaviorPattern
import com.finai.app.domain.FinanceAlert
import com.finai.app.domain.SafeToSpendResult

/**
 * Real, Room-backed financial state — everything a screen needs that used to
 * come from `FinaiFixtures`. Built by [FinanceViewModel] from the
 * calculators in `domain/` — still no AI call in this ViewModel (planning.md
 * §6): the personalized "why"/"leitura" prose that used to be a Fase 1
 * placeholder is now requested from [AiViewModel] by the screens that show
 * it, keyed off these same numbers.
 */
data class FinanceUiState(
    val loading: Boolean = true,
    val greeting: String = "",
    val subGreeting: String = "",

    // Raw entities, kept around for edit dialogs and screens that need more than the mapped UI shape.
    val rawContas: List<ContaEntity> = emptyList(),
    val rawFaturasCartao: List<FaturaCartaoEntity> = emptyList(),
    val rawObjetivos: List<ObjetivoEntity> = emptyList(),
    val rawDividas: List<DividaEntity> = emptyList(),
    val rawOrcamentos: List<OrcamentoCategoriaEntity> = emptyList(),
    val rawTransacoesDoMes: List<TransacaoEntity> = emptyList(),
    /** Todas as transações, sem filtro de mês — usado pela Agenda para o mês que o usuário estiver navegando (planning.md §3.2/§3.6). */
    val rawTransacoes: List<TransacaoEntity> = emptyList(),

    // Home
    /** Metas em andamento. As concluídas ficam em [completedGoals]. */
    val goals: List<Goal> = emptyList(),
    val completedGoals: List<Goal> = emptyList(),
    /** Dívidas quitadas — histórico na tela Dívidas, fora de toda conta. */
    val paidDebts: List<com.finai.app.data.model.PaidDebt> = emptyList(),
    val safeToday: SafeToSpendResult? = null,
    val safeTodayLabel: String = "",
    val safeNote: String = "",
    /** Topo da Início: frase de situação, livre até o salário e a ação pertinente (Fase 7, item 3). */
    val situation: com.finai.app.domain.HomeSituation? = null,
    /** De onde vem o "Pode gastar hoje" — folha "Entenda este valor". */
    val spendExplanation: com.finai.app.domain.SpendExplanation? = null,
    /** Ciclo entre salários ([com.finai.app.domain.PayCycle]); nulo sem receita recorrente cadastrada. */
    val payCycle: com.finai.app.domain.PayCycle? = null,
    /** Balanço do mês corrente — recebimentos − contas a pagar, a mesma conta da Agenda (MonthCashFlow). */
    val saldoCents: Long = 0,
    val saldoLabel: String = "",
    val nextWeekBills: List<WeekBill> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val timelineNote: String = "",
    /** Padrão de gasto mais relevante do mês, calculado por [com.finai.app.domain.BehaviorCoach] — null se nenhum padrão passou dos limiares. */
    val behaviorPattern: BehaviorPattern? = null,

    // Objetivos
    val monthlyCapacityCents: Long = 0,
    val monthlyCapacityLabel: String = "",
    /** Soma do aporte mensal que as metas pedem, e quanto disso cabe na capacidade. */
    val goalsNeededCents: Long = 0,
    /** Balanço projetado mês a mês até o prazo da meta mais distante. */
    val savingsProjection: com.finai.app.domain.SavingsProjection? = null,
    val goalsFundedCents: Long = 0,

    // Agenda
    val allBillsAndIncome: List<ContaEntity> = emptyList(),

    // Dívidas
    val debts: List<Debt> = emptyList(),
    val debtTotalLabel: String = "",
    val debtInterestLabel: String = "",
    val debtFreeLabel: String = "",
    val debtStrategyNote: String = "",
    val negotiationTitle: String = "",

    // Orçamentos
    val budgets: List<Budget> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),

    /** Cada dívida separada, na ordem de ataque — o que a IA recebe ([com.finai.app.domain.AiContext]). */
    val aiDebtsBlock: String = "",
    /** Metas com a divisão da sobra por prioridade — o que a IA recebe ([com.finai.app.domain.AiContext]). */
    val aiGoalsBlock: String = "",

    /**
     * O plano financeiro central ([com.finai.app.domain.FinancialPlan]): disponível de verdade e
     * o destino único da sobra. Início, Objetivos, Dívidas, simulador e IA leem daqui.
     */
    val plan: com.finai.app.domain.FinancialPlan? = null,
    /** O plano como a IA recebe ([com.finai.app.domain.FinancialPlan.aiBlock]). */
    val aiPlanBlock: String = "",
    /** "Decisões para você", tiradas do plano, sem IA. */
    val decisions: List<com.finai.app.domain.AiReplyFormat.Decision> = emptyList(),

    /** Avisos do sino da topbar — planning.md §3.9, calculados por [com.finai.app.domain.AlertCalculator]. */
    val alerts: List<FinanceAlert> = emptyList(),
)

/**
 * Minimal-context summary sent to the AI for free-form chat (planning.md
 * §4/§6/§9 Fase 2) — aggregated numbers/labels already on screen, never raw
 * transactions or account nicknames.
 */
fun FinanceUiState.toAiSummaryText(): String = buildString {
    // A data deixa explícito que é um retrato de agora: números citados em
    // mensagens antigas podem ser de outro momento.
    appendLine("Números calculados hoje, ${java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}.")
    // Mesmo número e mesmo rótulo do topo da Início: o livre até o salário, e o valor por dia como apoio.
    situation?.let { sit ->
        val periodo = sit.label.substringAfter("·", "").trim().let { if (it.isBlank()) "fim do mês" else "salário de $it" }
        if (sit.shortfall) {
            appendLine("Situação: ${sit.headline}. Faltam ${com.finai.app.util.formatBrl0(sit.mainCents / 100.0)} até o $periodo. $safeNote")
        } else {
            appendLine(
                "Situação: ${sit.headline}. Livre até o $periodo: ${com.finai.app.util.formatBrl0(sit.mainCents / 100.0)} " +
                    "(cerca de $safeTodayLabel por dia, ${sit.days} dia(s)), depois do destino da sobra.",
            )
        }
    } ?: appendLine("Livre por dia: $safeTodayLabel. $safeNote")
    // O plano central traz balanço, comprometido, reservado, disponível e o destino da sobra:
    // é a única fonte de "quanto sobra e para onde vai" que a IA recebe.
    if (aiPlanBlock.isNotBlank()) appendLine(aiPlanBlock)
    savingsProjection?.let { p ->
        val next12 = p.months.take(12)
        appendLine(
            "Sobra projetada nos próximos ${next12.size} meses (balanço de cada mês somado): " +
                "${com.finai.app.util.formatBrl0(next12.sumOf { it.balanceCents } / 100.0)}. Serve para julgar prazos de metas, não para gastar agora.",
        )
        p.estimatedSalaryCents?.let { s ->
            appendLine("Nos meses sem salário lançado, a projeção repete o último salário (${com.finai.app.util.formatBrl0(s / 100.0)}) como estimativa.")
        }
    }
    if (debts.isNotEmpty()) {
        appendLine("Soma de todas as dívidas: $debtTotalLabel em aberto, $debtInterestLabel de juros por mês somados, livre de todas em $debtFreeLabel.")
        if (aiDebtsBlock.isNotBlank()) appendLine(aiDebtsBlock)
    }
    if (aiGoalsBlock.isNotBlank()) appendLine(aiGoalsBlock)
    else if (goals.isNotEmpty()) {
        appendLine("Objetivos ativos: " + goals.joinToString { "${it.name} (${(it.progress * 100).toInt()}%, ${it.badge.label})" } + ".")
    }
    if (completedGoals.isNotEmpty()) appendLine("Metas já concluídas: " + completedGoals.joinToString { it.name } + ".")
    if (paidDebts.isNotEmpty()) appendLine("Dívidas já quitadas (fora das contas): " + paidDebts.joinToString { it.name } + ".")
    val over = budgets.filter { it.spent > it.limit }
    if (over.isNotEmpty()) {
        appendLine("Categorias de orçamento estouradas: " + over.joinToString { it.name } + ".")
    }
}
