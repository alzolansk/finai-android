package com.finai.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.ai.AiProvider
import com.finai.app.data.ai.AiResponse
import com.finai.app.data.ai.GeminiAiProvider
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import com.finai.app.data.prefs.AiKeyStore
import com.finai.app.domain.AiPromptBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the state behind the overlays and widgets that float above whatever
 * screen is on-screen (quick-action sheet, buy simulator, chat, notifications)
 * plus the couple of bits of screen-local state simple enough not to warrant
 * their own ViewModel yet (agenda month cursor, import stage).
 *
 * One shared ViewModel for now is a deliberate Phase 0 shortcut — it mirrors
 * the prototype's single `state` object exactly. Split per screen (as
 * planning.md §5's MVVM pattern calls for) once Phase 1 gives each screen its
 * own Room-backed data to own.
 *
 * Owns the chat's real AI call (Fase 2, planning.md §9) through the
 * [AiProvider] abstraction — [AiViewModel] owns every other AI touchpoint
 * (goal insight, purchase verdict, debt negotiation, decisions); chat stays
 * here because the rest of its state (open/closed, draft, message list)
 * already lives in this ViewModel.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val aiProvider: AiProvider = GeminiAiProvider(AiKeyStore.get(application))

    private val _uiState = MutableStateFlow(FinaiUiState())
    val uiState: StateFlow<FinaiUiState> = _uiState

    private var chatReplyJob: kotlinx.coroutines.Job? = null
    private var importJob: kotlinx.coroutines.Job? = null

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

    // ── import flow (simulated stages — real OCR/parsing lands in Phase 4) ──
    fun runImportStep() {
        val stage = _uiState.value.importStage
        if (stage == 2) return // caller should navigate to Agenda instead
        _uiState.update { it.copy(importStage = 1) }
        importJob?.cancel()
        importJob = viewModelScope.launch {
            delay(1400)
            _uiState.update { it.copy(importStage = 2) }
        }
    }

    fun resetImport() {
        importJob?.cancel()
        _uiState.update { it.copy(importStage = 0) }
    }

    // ── chat ────────────────────────────────────────────────────
    fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text) }

    /** [financeSummary] is the minimal aggregated context — see [com.finai.app.state.toAiSummaryText]. */
    fun sendMessage(text: String, financeSummary: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        chatReplyJob?.cancel()
        val historyBeforeReply = _uiState.value.messages
        _uiState.update {
            it.copy(
                chatOpen = true,
                addOpen = false,
                simOpen = false,
                thinking = true,
                draft = "",
                messages = it.messages + ChatMessage(ChatRole.Me, trimmed),
            )
        }
        chatReplyJob = viewModelScope.launch {
            val request = AiPromptBuilder.chat(financeSummary, historyBeforeReply, trimmed)
            val reply = when (val response = aiProvider.generate(request)) {
                is AiResponse.Success -> response.text
                is AiResponse.Unavailable -> FinaiFixtures.offlineReply(trimmed, response.reason)
            }
            _uiState.update {
                it.copy(
                    thinking = false,
                    messages = it.messages + ChatMessage(ChatRole.Ai, reply),
                )
            }
        }
    }

    fun sendDraft(financeSummary: String) = sendMessage(_uiState.value.draft, financeSummary)
}
