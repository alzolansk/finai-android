package com.finai.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.ai.AiProvider
import com.finai.app.data.ai.AiRequest
import com.finai.app.data.ai.AiResponse
import com.finai.app.data.ai.AiText
import com.finai.app.data.ai.GeminiAiProvider
import com.finai.app.data.model.Budget
import com.finai.app.data.model.Debt
import com.finai.app.data.model.Goal
import com.finai.app.data.model.GoalBadge
import com.finai.app.data.model.Subscription
import com.finai.app.data.prefs.AiKeyStore
import com.finai.app.domain.AiPromptBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The app's AI layer (planning.md §9 Fase 2): every screen that needs
 * language generated over numbers [FinanceViewModel] already computed —
 * goal insight, purchase-simulator verdict, debt negotiation script, home
 * "Decisões para você" — goes through [AiProvider] here, never straight to
 * Gemini. [GeminiAiProvider] is the only implementation for now; Fase 3's
 * router replaces the single `provider` val with several behind the same
 * interface, and nothing above this class should need to change then.
 *
 * Each `ensureX` call is memoized by a cache key built from the inputs that
 * would change the answer, so a recomposition or a Room re-emit that doesn't
 * actually change those numbers doesn't re-spend API quota.
 */
class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStore = AiKeyStore.get(application)
    private val provider: AiProvider = GeminiAiProvider(keyStore)

    val hasApiKey: StateFlow<Boolean> = keyStore.apiKey
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), !keyStore.apiKey.value.isNullOrBlank())

    fun setApiKey(key: String) = keyStore.setGeminiApiKey(key)
    fun clearApiKey() = keyStore.clearGeminiApiKey()

    // ── objetivos: "leitura da IA" por objetivo ─────────────────────────
    private val _goalInsights = MutableStateFlow<Map<String, AiText>>(emptyMap())
    val goalInsights: StateFlow<Map<String, AiText>> = _goalInsights

    private val goalInsightKeys = mutableMapOf<String, String>()
    private val goalInsightJobs = mutableMapOf<String, Job>()

    fun ensureGoalInsight(goal: Goal, monthlyCapacityLabel: String, otherActiveGoals: Int) {
        val cacheKey = listOf(goal.name, goal.kind, goal.saved, goal.target, goal.eta, goal.badge.name, monthlyCapacityLabel, otherActiveGoals)
            .joinToString("|")
        if (goalInsightKeys[goal.id] == cacheKey) return
        goalInsightKeys[goal.id] = cacheKey
        goalInsightJobs[goal.id]?.cancel()
        _goalInsights.update { it + (goal.id to AiText.Loading) }
        goalInsightJobs[goal.id] = viewModelScope.launch {
            val text = requestText(AiPromptBuilder.goalInsight(goal, monthlyCapacityLabel, otherActiveGoals))
            _goalInsights.update { it + (goal.id to text) }
        }
    }

    // ── simulador: veredito ──────────────────────────────────────────────
    private val _purchaseVerdict = MutableStateFlow<AiText?>(null)
    val purchaseVerdict: StateFlow<AiText?> = _purchaseVerdict
    private var purchaseVerdictKey: String? = null
    private var purchaseVerdictJob: Job? = null

    fun ensurePurchaseVerdict(
        amountLabel: String,
        verdictLabel: String,
        monthlyCapacityLabel: String,
        slackAfterLabel: String,
        topGoalName: String?,
        topGoalAffected: Boolean,
    ) {
        val cacheKey = listOf(amountLabel, verdictLabel, monthlyCapacityLabel, slackAfterLabel, topGoalName, topGoalAffected).joinToString("|")
        if (purchaseVerdictKey == cacheKey) return
        purchaseVerdictKey = cacheKey
        purchaseVerdictJob?.cancel()
        _purchaseVerdict.value = AiText.Loading
        purchaseVerdictJob = viewModelScope.launch {
            _purchaseVerdict.value = requestText(
                AiPromptBuilder.purchaseVerdict(amountLabel, verdictLabel, monthlyCapacityLabel, slackAfterLabel, topGoalName, topGoalAffected),
            )
        }
    }

    // ── dívidas: roteiro de negociação ────────────────────────────────────
    private val _debtNegotiation = MutableStateFlow<AiText?>(null)
    val debtNegotiation: StateFlow<AiText?> = _debtNegotiation
    private var debtNegotiationKey: String? = null
    private var debtNegotiationJob: Job? = null

    fun ensureDebtNegotiation(debts: List<Debt>) {
        val cacheKey = debts.joinToString("|") { "${it.id}:${it.amount}:${it.rate}" }
        if (debtNegotiationKey == cacheKey) return
        debtNegotiationKey = cacheKey
        debtNegotiationJob?.cancel()
        _debtNegotiation.value = AiText.Loading
        debtNegotiationJob = viewModelScope.launch {
            _debtNegotiation.value = requestText(AiPromptBuilder.debtNegotiation(debts))
        }
    }

    // ── início: "decisões para você" ──────────────────────────────────────
    private val _decisions = MutableStateFlow<AiText?>(null)
    val decisions: StateFlow<AiText?> = _decisions
    private var decisionsKey: String? = null
    private var decisionsJob: Job? = null

    fun ensureDecisions(
        topDebt: Debt?,
        budgets: List<Budget>,
        subscriptions: List<Subscription>,
        goals: List<Goal>,
        safeNote: String,
    ) {
        val budgetsOver = budgets.filter { it.spent > it.limit }
        val unusedSubs = subscriptions.filter { it.cta == "Cancelar" }
        val reassessGoals = goals.filter { it.badge == GoalBadge.Reassess }
        val cacheKey = listOf(topDebt?.id, budgetsOver.map { it.name }, unusedSubs.map { it.name }, reassessGoals.map { it.id }, safeNote).toString()
        if (decisionsKey == cacheKey) return
        decisionsKey = cacheKey
        decisionsJob?.cancel()
        _decisions.value = AiText.Loading
        decisionsJob = viewModelScope.launch {
            _decisions.value = requestText(AiPromptBuilder.decisions(topDebt, budgetsOver, unusedSubs, reassessGoals, safeNote))
        }
    }

    private suspend fun requestText(request: AiRequest): AiText = when (val response = provider.generate(request)) {
        is AiResponse.Success -> AiText.Ready(response.text)
        is AiResponse.Unavailable -> AiText.Unavailable(response.reason)
    }
}
