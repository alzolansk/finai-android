package com.finai.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.ai.AiProvider
import com.finai.app.data.ai.AiResponse
import com.finai.app.data.ai.AiRouter
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.model.ChatRole
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.repository.ChatRepository
import com.finai.app.domain.AiPromptBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.finai.app.util.FinaiLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import com.finai.app.data.local.entity.ConversaResumo
import com.finai.app.data.model.ChatMessage
import com.finai.app.domain.AssistantTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds the state behind the overlays and widgets that float above whatever
 * screen is on-screen (quick-action sheet, buy simulator, chat, notifications)
 * plus the couple of bits of screen-local state simple enough not to warrant
 * their own ViewModel yet (agenda month cursor). A importação de fatura tem o
 * seu próprio [ImportViewModel] desde a Fase 4, porque ali há trabalho real
 * (OCR, parsing, persistência) em vez de estado de overlay.
 *
 * One shared ViewModel for now is a deliberate Phase 0 shortcut — it mirrors
 * the prototype's single `state` object exactly. Split per screen (as
 * planning.md §5's MVVM pattern calls for) once Phase 1 gives each screen its
 * own Room-backed data to own.
 *
 * Owns the chat's real AI call (Fase 2/3, planning.md §9) through the
 * [AiProvider] abstraction — [AiViewModel] owns every other AI touchpoint
 * (goal insight, purchase verdict, debt negotiation, decisions); chat stays
 * here because the rest of its state (open/closed, draft, message list)
 * already lives in this ViewModel. Uses its own [AiRouter] instance — the
 * router is stateless glue over the shared [com.finai.app.data.ai.AiKeyStore]/
 * [com.finai.app.data.ai.ProviderUsageStore]/[com.finai.app.data.ai.AiResponseCache]
 * singletons, so two instances stay consistent with each other.
 */

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val aiProvider: AiProvider = AiRouter(application)
    private val chatRepository = ChatRepository(FinaiDatabase.get(application))
    private val prefs = FinaiPreferences(application)

    private val _uiState = MutableStateFlow(FinaiUiState())
    val uiState: StateFlow<FinaiUiState> = _uiState

    /**
     * Telas cujas dicas já foram vistas ([com.finai.app.domain.TipScript]); `null` enquanto o
     * DataStore não respondeu, para não abrir uma dica por um frame em quem já as viu.
     * Substitui o antigo tour em tela cheia (`onboardingComplete`, que só sobrevive como
     * migração em [FinaiPreferences.tipsSeen]).
     */
    val tipsSeen: StateFlow<Set<String>?> =
        prefs.tipsSeen
            .map<Set<String>, Set<String>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markTipsSeen(screen: String) = viewModelScope.launch { prefs.markTipsSeen(screen) }

    /** Usado tanto por "Rever tour guiado" quanto depois de "Apagar todos os dados". */
    fun restartOnboarding() = viewModelScope.launch { prefs.resetTips() }

    /**
     * Configuração inicial de dados ([com.finai.app.ui.components.InitialSetupWizard]),
     * mostrada uma única vez na primeira abertura (`null` enquanto o
     * DataStore não respondeu, mesma convenção de [tipsSeen]).
     * Deliberadamente não reaberta por "Rever tour guiado" — quem já tem
     * dados cadastrados não deve ser levado de volta a um assistente de
     * cadastro inicial; só "Apagar todos os dados" a reabre, junto com o tour.
     */
    val initialSetupComplete: StateFlow<Boolean?> =
        prefs.initialSetupComplete
            .map<Boolean, Boolean?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun completeInitialSetup() = viewModelScope.launch { prefs.setInitialSetupComplete(true) }
    fun restartInitialSetup() = viewModelScope.launch { prefs.setInitialSetupComplete(false) }

    /** Nomes de conta/cartão digitados na configuração inicial — ver [FinaiPreferences.knownAccounts]. */
    val knownAccounts: StateFlow<Set<String>> =
        prefs.knownAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Configurações → Aparência. O tema em si é aplicado por `MainActivity`, que lê o mesmo DataStore. */
    val themeMode: StateFlow<com.finai.app.data.prefs.ThemeMode> =
        prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.finai.app.data.prefs.ThemeMode.LIGHT)

    fun setThemeMode(mode: com.finai.app.data.prefs.ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }

    fun addKnownAccounts(names: Set<String>) = viewModelScope.launch { prefs.addKnownAccounts(names) }
    fun removeKnownAccount(name: String) = viewModelScope.launch { prefs.removeKnownAccount(name) }

    private var chatReplyJob: kotlinx.coroutines.Job? = null

    /**
     * Conversa aberta no chat. Um id novo só vira linha no Room quando a
     * primeira mensagem é gravada — "Nova conversa" sem escrever nada não
     * deixa conversa vazia na lista.
     */
    private val conversationId = MutableStateFlow(newConversationId())

    /** Contexto invisível da conversa atual (card que a abriu), lido do Room. */
    private var topicContext: String? = null

    /** Conversa que está esperando resposta da IA — o "pensando" só aparece nela. */
    private var pendingConversationId: Long? = null

    // ── assistente (planejamento de 14/11/2026) ─────────────────
    // O retrato é lido do Room a cada mensagem, numa transação: as consultas e as ações não
    // dependem do que a tela já carregou. Tudo que grava passa pelo executor (recibo, idempotência).
    private val db = FinaiDatabase.get(application)
    private val snapshots = com.finai.app.data.assistant.FinancialSnapshotRepository(db, prefs)
    private val executor = com.finai.app.data.assistant.AssistantActionExecutor(db, com.finai.app.data.repository.FinanceRepository(db))
    private val sessionStore = com.finai.app.data.assistant.RoomSessionStore(db)

    /** O cartão "Lançamento pronto" foi salvo pelo [FinanceViewModel]: mostra "Salvo" e Desfazer. */
    fun onEntrySaved(receipt: com.finai.app.domain.EntryReceipt) =
        _uiState.update { it.copy(pendingEntry = null, savedEntry = receipt) }

    fun onEntryUndone() {
        val conv = conversationId.value
        _uiState.update { it.copy(savedEntry = null) }
        viewModelScope.launch { runCatching { chatRepository.registrar(conv, ChatRole.Ai, "Desfeito: o lançamento saiu da Agenda.") } }
    }

    fun discardPendingEntry() = _uiState.update { it.copy(pendingEntry = null, savedEntry = null) }

    val conversations: StateFlow<List<ConversaResumo>> = chatRepository.conversas
        .catch { t -> FinaiLog.e(TAG, "Falha ao ler a lista de conversas", t); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Room é a fonte de verdade da conversa (planning.md §5): a lista da tela
        // é sempre o que está gravado, não um acumulador em memória.
        viewModelScope.launch {
            conversationId
                .flatMapLatest { id -> chatRepository.conversa(id) }
                .catch { t ->
                    // Fase 6: sem isto, uma falha de leitura do Room cancelaria a
                    // coroutine com exceção não tratada e derrubaria o app na
                    // abertura. A conversa fica vazia; o resto do app segue inteiro.
                    FinaiLog.e(TAG, "Falha ao ler o histórico do chat", t)
                }
                .collect { conversa ->
                    if (conversa.id != conversationId.value) return@collect
                    topicContext = conversa.contexto
                    _uiState.update { it.copy(messages = conversa.mensagens, conversationId = conversa.id) }
                }
        }
    }

    // ── overlays ────────────────────────────────────────────────
    fun toggleAddMenu() = _uiState.update { it.copy(addOpen = !it.addOpen) }
    fun closeAddMenu() = _uiState.update { it.copy(addOpen = false) }

    fun openSimulator() = _uiState.update { it.copy(simOpen = true, addOpen = false) }
    fun closeSimulator() = _uiState.update { it.copy(simOpen = false) }

    fun openSpendExplanation() = _uiState.update { it.copy(explainOpen = true, addOpen = false) }
    fun closeSpendExplanation() = _uiState.update { it.copy(explainOpen = false) }

    /**
     * Ícone do assistente na topbar: continua a conversa mais recente se ela
     * teve mensagem hoje; senão começa uma nova — o assunto de ontem não
     * deveria se misturar com a pergunta de hoje.
     */
    fun openChat() {
        val latest = conversations.value.firstOrNull()
        val current = conversationId.value
        val target = when {
            latest != null && isToday(latest.ultima) -> latest.conversaId
            conversations.value.none { it.conversaId == current } -> current // já é uma conversa nova, vazia
            else -> newConversationId()
        }
        switchConversation(target)
        _uiState.update { it.copy(chatOpen = true, addOpen = false, simOpen = false, notifsOpen = false) }
    }

    fun newConversation() {
        if (conversations.value.none { it.conversaId == conversationId.value }) return // a atual já está vazia
        switchConversation(newConversationId())
    }

    fun openConversation(id: Long) = switchConversation(id)

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            try {
                chatRepository.apagarConversa(id)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FinaiLog.e(TAG, "Falha ao apagar a conversa", t)
            }
        }
        viewModelScope.launch { runCatching { sessionStore.delete(id) } }
        if (id == conversationId.value) switchConversation(newConversationId())
    }

    /**
     * Botões contextuais ("Conversar sobre isso", "Ensaiar a ligação",
     * "Simular", "Perguntar", decisões): abre uma conversa nova e já envia a
     * pergunta, com o contexto do card indo junto no prompt.
     */
    fun askAbout(topic: AssistantTopic, financeSummary: String) {
        switchConversation(newConversationId())
        send(topic.question, financeSummary, history = emptyList(), context = topic.context, isFirstMessage = true)
    }

    private fun switchConversation(id: Long) {
        if (id == conversationId.value) return
        topicContext = null
        conversationId.value = id
        _uiState.update {
            it.copy(conversationId = id, messages = emptyList(), draft = "", thinking = pendingConversationId == id, pendingEntry = null, savedEntry = null, undoableActionId = null)
        }
        // A conversa retomada lembra a última ação que ainda pode ser desfeita.
        viewModelScope.launch {
            val last = runCatching { sessionStore.load(id).lastActionId?.takeIf { executor.undoable(it) } }.getOrNull()
            if (last != null && conversationId.value == id) _uiState.update { it.copy(undoableActionId = last) }
        }
    }
    fun closeChat() = _uiState.update { it.copy(chatOpen = false) }

    fun openNotifications() = _uiState.update { it.copy(notifsOpen = true) }
    fun closeNotifications() = _uiState.update { it.copy(notifsOpen = false) }

    fun closeAllOverlays() = _uiState.update {
        it.copy(addOpen = false, simOpen = false, notifsOpen = false)
    }

    fun toggleCoach() = _uiState.update { it.copy(showCoach = !it.showCoach) }

    // ── agenda ──────────────────────────────────────────────────
    /** Abre a Agenda no mês da data — na visão mensal, que é a que tem mês. */
    fun showAgendaDate(date: java.time.LocalDate) = _uiState.update {
        it.copy(monthIndex = date.monthValue - 1, agendaYear = date.year, agendaSequential = false)
    }

    fun setAgendaSequential(sequential: Boolean) = _uiState.update { it.copy(agendaSequential = sequential) }

    fun prevMonth() = _uiState.update {
        if (it.monthIndex == 0) it.copy(monthIndex = 11, agendaYear = it.agendaYear - 1)
        else it.copy(monthIndex = it.monthIndex - 1)
    }

    fun nextMonth() = _uiState.update {
        if (it.monthIndex == 11) it.copy(monthIndex = 0, agendaYear = it.agendaYear + 1)
        else it.copy(monthIndex = it.monthIndex + 1)
    }

    // ── buy simulator ───────────────────────────────────────────
    fun setSimAmount(amount: Double) = _uiState.update { it.copy(simAmount = amount) }

    // ── chat ────────────────────────────────────────────────────
    fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text) }

    /** [financeSummary] is the minimal aggregated context — see [com.finai.app.state.toAiSummaryText]. */
    fun sendMessage(text: String, financeSummary: String) {
        val history = _uiState.value.messages
        send(text, financeSummary, history, context = topicContext, isFirstMessage = history.isEmpty())
    }

    private fun send(text: String, financeSummary: String, history: List<ChatMessage>, context: String?, isFirstMessage: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        chatReplyJob?.cancel()
        val convId = conversationId.value
        pendingConversationId = convId
        if (isFirstMessage) topicContext = context
        _uiState.update {
            it.copy(chatOpen = true, addOpen = false, simOpen = false, thinking = true, draft = "", pendingEntry = null, savedEntry = null)
        }
        chatReplyJob = viewModelScope.launch {
            try {
                val messageId = chatRepository.registrar(convId, ChatRole.Me, trimmed, contexto = context.takeIf { isFirstMessage })
                var unavailable: String? = null
                var memory: List<String> = emptyList()
                val orchestrator = com.finai.app.domain.assistant.AssistantOrchestrator(
                    snapshot = { snapshots.read().also { s -> memory = s.memoria.sortedByDescending { it.criadoEm }.map { it.texto } } },
                    executor = executor,
                    sessions = sessionStore,
                    ask = { results, focus ->
                        val request = AiPromptBuilder.chat(
                            financeSummary, history, trimmed, context, memory,
                            toolCatalog = com.finai.app.domain.assistant.AssistantTools.CATALOG,
                            toolResults = results.map { it.facts },
                            actionCatalog = com.finai.app.domain.assistant.ActionPlanner.CATALOG,
                            focus = focus.map { "${it.type} \"${it.label}\"" },
                        )
                        when (val response = aiProvider.generate(request)) {
                            is AiResponse.Success -> response.text
                            is AiResponse.Unavailable -> { unavailable = response.reason; null }
                        }
                    },
                    unavailable = { unavailable },
                )
                val reply = withContext(Dispatchers.Default) { orchestrator.handle(com.finai.app.domain.assistant.Turn(convId, messageId, trimmed)) }
                chatRepository.registrar(convId, ChatRole.Ai, com.finai.app.domain.assistant.AssistantLoop.withConsulted(reply.consulted, reply.text), answer = reply.answer)
                pendingConversationId = null
                val undoable = reply.answer?.action?.takeIf { it.canUndo && it.status == com.finai.app.domain.assistant.ActionStatus.Applied }?.actionId
                val current = convId == conversationId.value
                _uiState.update {
                    it.copy(
                        thinking = false, pendingEntry = reply.pendingEntry?.takeIf { current },
                        undoableActionId = if (!current) it.undoableActionId else if (reply.answer?.action != null) undoable else it.undoableActionId,
                    )
                }
            } catch (e: CancellationException) {
                // Cancelamento aqui só acontece quando o usuário manda outra
                // mensagem, e essa chamada já ligou o "pensando" de novo. Desligar
                // no caminho de cancelamento apagaria o indicador da resposta que
                // acabou de começar — por isso este `catch` não mexe no estado.
                throw e
            } catch (t: Throwable) {
                // Fase 6. Os adaptadores de IA já devolvem Unavailable em vez de
                // lançar, então chegar aqui significa falha de escrita no Room ou
                // algo inesperado — nos dois casos o chat tem que continuar usável
                // em vez de derrubar o app, e sem deixar o indicador de "pensando"
                // girando para sempre.
                FinaiLog.e(TAG, "Falha ao responder no chat", t)
                pendingConversationId = null
                _uiState.update { it.copy(thinking = false) }
            }
        }
    }

    fun sendDraft(financeSummary: String) = sendMessage(_uiState.value.draft, financeSummary)

    /** "Desfazer" no cartão de uma ação do assistente: restaura os registros pelo recibo e avisa na conversa. */
    fun undoAction(actionId: Long) {
        val convId = conversationId.value
        viewModelScope.launch {
            try {
                val orchestrator = com.finai.app.domain.assistant.AssistantOrchestrator({ snapshots.read() }, executor, sessionStore, { _, _ -> null })
                val reply = orchestrator.undo(com.finai.app.domain.assistant.Turn(convId, 0, ""), sessionStore.load(convId), actionId)
                chatRepository.registrar(convId, ChatRole.Ai, reply.text, answer = reply.answer)
                _uiState.update { it.copy(undoableActionId = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FinaiLog.e(TAG, "Falha ao desfazer a ação do assistente", t)
            }
        }
    }

    /** Pausar, retomar ou cancelar um acompanhamento pela lista do chat. */
    fun setWatchState(rule: com.finai.app.data.local.entity.AcompanhamentoEntity, state: String?) = viewModelScope.launch {
        runCatching {
            if (state == null) db.acompanhamentoDao().delete(rule.id)
            // Retomado: volta a "aguardando", para avisar na próxima vez que a condição valer.
            else db.acompanhamentoDao().upsert(rule.copy(estado = state, ultimoResultado = com.finai.app.domain.assistant.WatchRules.WAITING))
        }.onFailure { FinaiLog.e(TAG, "Falha ao atualizar o acompanhamento", it) }
    }

    private companion object {
        const val TAG = "AppViewModel"

        fun newConversationId(): Long = System.currentTimeMillis()

        fun isToday(millis: Long): Boolean =
            java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() ==
                java.time.LocalDate.now()
    }
}
