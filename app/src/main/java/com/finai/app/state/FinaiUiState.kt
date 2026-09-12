package com.finai.app.state

import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.ChatMessage
import java.time.LocalDate

/**
 * Overlay-only UI state — sheets, the chat thread, the "Posso comprar?"
 * simulator draft amount, the agenda month cursor. Real financial data
 * (goals, bills, debts, budgets) lives in [FinanceViewModel]/[FinanceUiState]
 * as of Fase 1; this is what's left of the Fase 0 single-state-object that
 * has nothing to do with persistence (planning.md §9). [messages] is the one
 * exception: it mirrors the chat history [AppViewModel] reads from Room, so
 * the conversation survives a restart (planning.md §5).
 */
data class FinaiUiState(
    val addOpen: Boolean = false,
    val simOpen: Boolean = false,
    val chatOpen: Boolean = false,
    val notifsOpen: Boolean = false,
    val showCoach: Boolean = true,
    val monthIndex: Int = LocalDate.now().monthValue - 1,
    val agendaYear: Int = LocalDate.now().year,
    val simAmount: Double = FinaiFixtures.defaultSimAmount,
    val draft: String = "",
    val thinking: Boolean = false,
    /** Espelho do histórico gravado no Room — ver [AppViewModel]; começa vazio, não com uma mensagem fixa. */
    val messages: List<ChatMessage> = emptyList(),
)
