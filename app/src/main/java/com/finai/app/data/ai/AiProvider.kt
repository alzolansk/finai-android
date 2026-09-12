package com.finai.app.data.ai

/**
 * The one seam between the app and any external language model —
 * planning.md §7.3's `enviarPrompt(tarefa, contexto): Resposta`, named in
 * English to match the rest of `domain`/`state`. ViewModels depend on this
 * interface only, never on a concrete provider; [GeminiAiProvider] is the
 * first implementation (Fase 2 — planning.md §9). Fase 3 adds a router that
 * tries several [AiProvider]s behind this same interface, with per-provider
 * quota tracking and fallback — nothing above this interface should need to
 * change then.
 */
interface AiProvider {
    suspend fun generate(request: AiRequest): AiResponse
}

/** What kind of language the request is for — carried through for future per-task routing/telemetry (Fase 3). */
enum class AiTask { CHAT, GOAL_INSIGHT, PURCHASE_VERDICT, DECISIONS, DEBT_NEGOTIATION }

/**
 * [prompt] holds only aggregated numbers/labels already computed locally and
 * short user-typed text (a chat question) — never a raw entity, document, or
 * PII field (planning.md §4). Callers build this via
 * [com.finai.app.domain.AiPromptBuilder].
 */
data class AiRequest(
    val task: AiTask,
    val systemInstruction: String,
    val prompt: String,
)

sealed interface AiResponse {
    data class Success(val text: String) : AiResponse

    /** No key configured, network/HTTP failure, or the provider's free quota ran out — [reason] is user-facing pt-BR text. */
    data class Unavailable(val reason: String) : AiResponse
}

/** UI-friendly mirror of [AiResponse] plus the "not requested yet" state, for screens rendering AI text inline. */
sealed interface AiText {
    data object Loading : AiText
    data class Ready(val text: String) : AiText
    data class Unavailable(val reason: String) : AiText
}
