package com.finai.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.ai.AiProvider
import com.finai.app.data.ai.AiRequest
import com.finai.app.data.ai.AiResponse
import com.finai.app.data.ai.AiRouter
import com.finai.app.data.ai.AiTask
import com.finai.app.data.ai.AiText
import com.finai.app.data.ai.AiFailureKind
import com.finai.app.data.ai.ProviderUsageStore
import com.finai.app.data.ai.ProviderId
import com.finai.app.data.model.Budget
import com.finai.app.domain.BehaviorPattern
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's AI layer (planning.md §9 Fase 2/3): every screen that needs
 * language generated over numbers [FinanceViewModel] already computed —
 * goal insight, purchase-simulator verdict, debt negotiation script, home
 * "Decisões para você" — goes through [AiProvider] here, never straight to
 * any specific provider. [AiRouter] is the single implementation used: it
 * tries the five free-tier providers from planning.md §7.2 in order, with
 * per-provider daily-quota tracking and response caching, and only reports
 * unavailability once all of them have failed.
 *
 * Each `ensureX` call is memoized by a cache key built from the inputs that
 * would change the answer, so a recomposition or a Room re-emit that doesn't
 * actually change those numbers doesn't re-spend API quota.
 */
sealed interface ConnectionTest {
    data object Running : ConnectionTest
    data object Ok : ConnectionTest
    data class Failed(val reason: String) : ConnectionTest
}

private const val CONNECTION_TEST_TIMEOUT_MS = 20_000L

private val CONNECTION_TEST_REQUEST = AiRequest(
    task = AiTask.CHAT,
    systemInstruction = "Responda apenas com a palavra OK.",
    prompt = "Teste de conexão.",
)

class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStore = AiKeyStore.get(application)
    private val provider: AiProvider = AiRouter(application)

    /** Which providers currently have a key configured — drives the settings dialog's per-row status. */
    val configuredProviders: StateFlow<Set<ProviderId>> = combine(
        ProviderId.entries.map { id -> keyStore.keyFlow(id) },
    ) { keys ->
        ProviderId.entries.filterIndexed { i, _ -> !keys[i].isNullOrBlank() }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setProviderKey(id: ProviderId, key: String) {
        keyStore.setKey(id, key)
        _connectionTests.update { it - id }
        // Chave nova começa sem a marca de "sem cota" que a anterior possa ter deixado.
        viewModelScope.launch { usageStore.clearExhausted(id) }
    }

    fun clearProviderKey(id: ProviderId) {
        keyStore.clearKey(id)
        _connectionTests.update { it - id }
    }

    private val usageStore = ProviderUsageStore.get(application)

    /** Provedores que responderam 429 hoje — o roteador os pula até a meia-noite. */
    val exhaustedToday: StateFlow<Set<ProviderId>> = usageStore.exhaustedTodayFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _connectionTests = MutableStateFlow<Map<ProviderId, ConnectionTest>>(emptyMap())
    val connectionTests: StateFlow<Map<ProviderId, ConnectionTest>> = _connectionTests

    /**
     * "Testar conexão" das Configurações: uma chamada mínima direto no
     * adaptador do provedor, sem roteador nem cache — o que se quer saber é se
     * *esta* chave responde. O prompt não carrega nenhum dado financeiro. Um
     * 429 aqui marca o provedor como esgotado do dia, igual ao roteador faria.
     */
    fun testProvider(id: ProviderId) {
        if (_connectionTests.value[id] == ConnectionTest.Running) return
        _connectionTests.update { it + (id to ConnectionTest.Running) }
        viewModelScope.launch {
            val adapter = AiRouter.adapterFor(getApplication(), id)
            usageStore.recordAttempt(id)
            // O timeout do HttpURLConnection não cobre a resolução de DNS: sem
            // rede de verdade, a chamada pode ficar bloqueada por minutos. O
            // `await` é cancelável mesmo com a thread de IO presa, então a
            // tela sempre recebe um resultado.
            val call = async { adapter.generate(CONNECTION_TEST_REQUEST) }
            val result = when (val response = withTimeoutOrNull(CONNECTION_TEST_TIMEOUT_MS) { call.await() }) {
                null -> {
                    call.cancel()
                    ConnectionTest.Failed("Sem resposta do ${id.displayName} em 20 segundos. Confira a conexão com a internet.")
                }
                is AiResponse.Success -> {
                    usageStore.clearExhausted(id)
                    ConnectionTest.Ok
                }
                is AiResponse.Unavailable -> {
                    if (response.kind == AiFailureKind.RATE_LIMITED) usageStore.markExhaustedToday(id)
                    ConnectionTest.Failed(response.reason)
                }
            }
            _connectionTests.update { it + (id to result) }
        }
    }

    // ── objetivos: "leitura da IA" por objetivo ─────────────────────────
    private val _goalInsights = MutableStateFlow<Map<String, AiText>>(emptyMap())
    val goalInsights: StateFlow<Map<String, AiText>> = _goalInsights

    private val goalInsightKeys = mutableMapOf<String, String>()
    private val goalInsightJobs = mutableMapOf<String, Job>()

    fun ensureGoalInsight(goal: Goal, monthlyCapacityLabel: String, otherActiveGoals: Int) {
        val cacheKey = listOf(goal.name, goal.kind, goal.description, goal.projectionNote, goal.saved, goal.target, goal.eta, goal.badge.name, monthlyCapacityLabel, otherActiveGoals)
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

    // ── início: "coach de comportamento" (Fase 5) ──────────────────────────
    private val _coachInsight = MutableStateFlow<AiText?>(null)
    val coachInsight: StateFlow<AiText?> = _coachInsight
    private var coachInsightKey: String? = null
    private var coachInsightJob: Job? = null

    /**
     * [pattern] já foi decidido por [com.finai.app.domain.BehaviorCoach] — esta função só pede à
     * IA para redigir [pattern.detail] em linguagem mais natural (planning.md §9's "coach
     * comportamental"). `null` (nenhum padrão relevante este mês) limpa o estado sem chamar IA.
     */
    fun ensureCoachInsight(pattern: BehaviorPattern?) {
        if (pattern == null) {
            coachInsightJob?.cancel()
            coachInsightKey = null
            _coachInsight.value = null
            return
        }
        if (coachInsightKey == pattern.id) return
        coachInsightKey = pattern.id
        coachInsightJob?.cancel()
        _coachInsight.value = AiText.Loading
        coachInsightJob = viewModelScope.launch {
            _coachInsight.value = requestText(AiPromptBuilder.behaviorCoach(pattern))
        }
    }

    private suspend fun requestText(request: AiRequest): AiText =
        when (val response = provider.generate(request)) {
            is AiResponse.Success -> AiText.Ready(response.text)
            is AiResponse.Unavailable -> AiText.Unavailable(response.reason)
        }

    /**
     * "Apagar todos os dados" (Configurações): descarta todo texto de IA
     * memoizado sobre números que acabaram de deixar de existir — sem isto,
     * uma tela reaberta antes de a próxima chamada terminar poderia mostrar
     * por um instante a "leitura da IA" de um objetivo já apagado, porque a
     * chave de cache é o id do objetivo, não seu conteúdo. Não cancela chaves
     * de provedor nem cota — [com.finai.app.data.prefs.AiKeyStore] fica fora
     * de propósito.
     */
    fun resetMemoizedState() {
        goalInsightJobs.values.forEach { it.cancel() }
        goalInsightJobs.clear()
        goalInsightKeys.clear()
        _goalInsights.value = emptyMap()

        purchaseVerdictJob?.cancel()
        purchaseVerdictJob = null
        purchaseVerdictKey = null
        _purchaseVerdict.value = null

        debtNegotiationJob?.cancel()
        debtNegotiationJob = null
        debtNegotiationKey = null
        _debtNegotiation.value = null

        decisionsJob?.cancel()
        decisionsJob = null
        decisionsKey = null
        _decisions.value = null

        coachInsightJob?.cancel()
        coachInsightJob = null
        coachInsightKey = null
        _coachInsight.value = null
    }
}
