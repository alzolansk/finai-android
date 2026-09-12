package com.finai.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.ai.AiProvider
import com.finai.app.data.ai.AiResponse
import com.finai.app.data.ai.AiRouter
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.model.ChatRole
import com.finai.app.data.repository.ChatRepository
import com.finai.app.domain.AiPromptBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val aiProvider: AiProvider = AiRouter(application)
    private val chatRepository = ChatRepository(FinaiDatabase.get(application))

    private val _uiState = MutableStateFlow(FinaiUiState())
    val uiState: StateFlow<FinaiUiState> = _uiState

    private var chatReplyJob: kotlinx.coroutines.Job? = null

    init {
        // Room é a fonte de verdade da conversa (planning.md §5): a lista da tela
        // é sempre o que está gravado, não um acumulador em memória.
        viewModelScope.launch {
            chatRepository.mensagens.collect { mensagens ->
                _uiState.update { it.copy(messages = mensagens) }
            }
        }
    }

    // ── overlays ────────────────────────────────────────────────
    fun toggleAddMenu() = _uiState.update { it.copy(addOpen = !it.addOpen) }
    fun closeAddMenu() = _uiState.update { it.copy(addOpen = false) }

    fun openSimulator() = _uiState.update { it.copy(simOpen = true, addOpen = false) }
    fun closeSimulator() = _uiState.update { it.copy(simOpen = false) }

    fun openChat() = _uiState.update {
        it.copy(chatOpen = true, addOpen = false, simOpen = false, notifsOpen = false)
    }
    fun closeChat() = _uiState.update { it.copy(chatOpen = false) }

    fun openNotifications() = _uiState.update { it.copy(notifsOpen = true) }
    fun closeNotifications() = _uiState.update { it.copy(notifsOpen = false) }

    fun closeAllOverlays() = _uiState.update {
        it.copy(addOpen = false, simOpen = false, notifsOpen = false)
    }

    fun toggleCoach() = _uiState.update { it.copy(showCoach = !it.showCoach) }

    // ── agenda ──────────────────────────────────────────────────
    fun showAgendaDate(date: java.time.LocalDate) = _uiState.update {
        it.copy(monthIndex = date.monthValue - 1, agendaYear = date.year)
    }

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
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        chatReplyJob?.cancel()
        val historyBeforeReply = _uiState.value.messages
        _uiState.update {
            it.copy(chatOpen = true, addOpen = false, simOpen = false, thinking = true, draft = "")
        }
        chatReplyJob = viewModelScope.launch {
            chatRepository.registrar(ChatRole.Me, trimmed)
            val request = AiPromptBuilder.chat(financeSummary, historyBeforeReply, trimmed)
            val reply = when (val response = aiProvider.generate(request)) {
                is AiResponse.Success -> response.text
                is AiResponse.Unavailable -> FinaiFixtures.offlineReply(trimmed, response.reason)
            }
            chatRepository.registrar(ChatRole.Ai, reply)
            _uiState.update { it.copy(thinking = false) }
        }
    }

    fun sendDraft(financeSummary: String) = sendMessage(_uiState.value.draft, financeSummary)
}
