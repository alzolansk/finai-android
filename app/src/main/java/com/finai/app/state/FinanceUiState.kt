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
import com.finai.app.domain.SafeToSpendResult

/**
 * Real, Room-backed financial state — everything a screen needs that used to
 * come from `FinaiFixtures`. Built by [FinanceViewModel] from the
 * calculators in `domain/`. Text fields that would require IA to write
 * (personalized "why"/"leitura" prose) are plain, honest placeholders that
 * say so, per planning.md §6/§9 — Fase 1 has no AI call anywhere.
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

    // Home
    val goals: List<Goal> = emptyList(),
    val safeToday: SafeToSpendResult? = null,
    val safeTodayLabel: String = "",
    val safeNote: String = "",
    val nextWeekBills: List<WeekBill> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val timelineNote: String = "",
    val coachTitle: String = "",
    val coachBody: String = "",

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
)
