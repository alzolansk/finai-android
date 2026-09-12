package com.finai.app.state

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
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
    val rawObjetivos: List<ObjetivoEntity> = emptyList(),
    val rawDividas: List<DividaEntity> = emptyList(),
    val rawOrcamentos: List<OrcamentoCategoriaEntity> = emptyList(),
    val rawTransacoesDoMes: List<TransacaoEntity> = emptyList(),
    /** Todas as transações, sem filtro de mês — usado pela Agenda para o mês que o usuário estiver navegando (planning.md §3.2/§3.6). */
    val rawTransacoes: List<TransacaoEntity> = emptyList(),

    // Home
    val goals: List<Goal> = emptyList(),
    val safeToday: SafeToSpendResult? = null,
    val safeTodayLabel: String = "",
    val safeNote: String = "",
    val nextWeekBills: List<WeekBill> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val timelineNote: String = "",
    /** Padrão de gasto mais relevante do mês, calculado por [com.finai.app.domain.BehaviorCoach] — null se nenhum padrão passou dos limiares. */
    val behaviorPattern: BehaviorPattern? = null,

    // Objetivos
    val monthlyCapacityCents: Long = 0,
    val monthlyCapacityLabel: String = "",

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

    /** Avisos do sino da topbar — planning.md §3.9, calculados por [com.finai.app.domain.AlertCalculator]. */
    val alerts: List<FinanceAlert> = emptyList(),
)

/**
 * Minimal-context summary sent to the AI for free-form chat (planning.md
 * §4/§6/§9 Fase 2) — aggregated numbers/labels already on screen, never raw
 * transactions or account nicknames.
 */
fun FinanceUiState.toAiSummaryText(): String = buildString {
    appendLine("Pode gastar hoje: $safeTodayLabel. $safeNote")
    appendLine("Capacidade de poupança mensal: $monthlyCapacityLabel.")
    if (debts.isNotEmpty()) {
        appendLine("Dívidas: total em aberto $debtTotalLabel, juros $debtInterestLabel/mês, livre em $debtFreeLabel.")
    }
    if (goals.isNotEmpty()) {
        appendLine("Objetivos ativos: " + goals.joinToString { "${it.name} (${(it.progress * 100).toInt()}%, ${it.badge.label})" } + ".")
    }
    val over = budgets.filter { it.spent > it.limit }
    if (over.isNotEmpty()) {
        appendLine("Categorias de orçamento estouradas: " + over.joinToString { it.name } + ".")
    }
}
