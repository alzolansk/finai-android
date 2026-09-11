package com.finai.app.state

import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.RecommendationSurface

/**
 * Single source of truth for cross-screen UI state — overlays, the chat
 * thread, the "Posso comprar?" simulator, the agenda month cursor.
 *
 * This is a direct port of the prototype's `state` object in
 * `FinAI Mobile.dc.html`. It is intentionally UI-only (no persistence, no
 * real calculation) per planning.md's Phase 0 scope; AppViewModel is where
 * this gets backed by Room/real logic starting Phase 1.
 */
data class FinaiUiState(
    val surface: RecommendationSurface = RecommendationSurface.Cards,
    val addOpen: Boolean = false,
    val simOpen: Boolean = false,
    val chatOpen: Boolean = false,
    val notifsOpen: Boolean = false,
    val showCoach: Boolean = FinaiFixtures.showCoachDefault,
    val monthIndex: Int = FinaiFixtures.defaultMonthIndex,
    val simAmount: Double = FinaiFixtures.defaultSimAmount,
    val dismissedDecisionIds: Set<String> = emptySet(),
    val draft: String = "",
    val thinking: Boolean = false,
    val importStage: Int = 0,
    val messages: List<ChatMessage> = FinaiFixtures.initialMessages,
) {
    val decisions get() = FinaiFixtures.decisions.filter { it.id !in dismissedDecisionIds }
}
