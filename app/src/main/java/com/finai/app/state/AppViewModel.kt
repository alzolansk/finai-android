package com.finai.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import com.finai.app.data.model.RecommendationSurface
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
 */
class AppViewModel : ViewModel() {

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

    // ── decisions ───────────────────────────────────────────────
    fun pickSurface(surface: RecommendationSurface) = _uiState.update { it.copy(surface = surface) }

    fun dismissDecision(id: String) =
        _uiState.update { it.copy(dismissedDecisionIds = it.dismissedDecisionIds + id) }

    // ── agenda ──────────────────────────────────────────────────
    fun prevMonth() = _uiState.update { it.copy(monthIndex = (it.monthIndex + 11) % 12) }
    fun nextMonth() = _uiState.update { it.copy(monthIndex = (it.monthIndex + 1) % 12) }

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

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        chatReplyJob?.cancel()
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
            delay(1100)
            _uiState.update {
                it.copy(
                    thinking = false,
                    messages = it.messages + ChatMessage(ChatRole.Ai, FinaiFixtures.canned(trimmed)),
                )
            }
        }
    }

    fun sendDraft() = sendMessage(_uiState.value.draft)
}
