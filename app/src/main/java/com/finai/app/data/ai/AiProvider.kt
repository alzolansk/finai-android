package com.finai.app.data.ai

/**
 * The one seam between the app and any external language model —
 * planning.md §7.3's `enviarPrompt(tarefa, contexto): Resposta`, named in
 * English to match the rest of `domain`/`state`. ViewModels depend on this
 * interface only, never on a concrete provider. Every per-provider adapter
 * ([GeminiAiProvider], [GroqAiProvider], [OpenRouterAiProvider],
 * [MistralAiProvider], [CerebrasAiProvider]) implements it, and so does
 * [AiRouter] — the Fase 3 fallback/quota/cache layer that every ViewModel
 * actually depends on. Nothing above this interface needs to know a router
 * or multiple providers exist.
 */
interface AiProvider {
    suspend fun generate(request: AiRequest): AiResponse
}

/** What kind of language the request is for — carried through for prompt building and cache keys. */
enum class AiTask {
    CHAT,
    GOAL_INSIGHT,
    PURCHASE_VERDICT,
    DEBT_NEGOTIATION,

    /** Fase 4: categoria de lançamento importado que a regra local não reconheceu. */
    IMPORT_CATEGORY,

    /** Fase 4: se um lançamento importado é assinatura/recorrência, quando a regra local ficou na dúvida. */
    IMPORT_RECURRENCE,

    /** Fase 4: se um lançamento importado é repetição de outro, quando a regra local ficou na dúvida. */
    IMPORT_DUPLICATE,

    /** Fase 5: texto final de uma notificação proativa sobre evento(s) já detectado(s) localmente ([com.finai.app.domain.FinanceAlert]). */
    PROACTIVE_ALERT,

    /** Fase 5: explicação em linguagem natural de um padrão de comportamento já detectado localmente ([com.finai.app.domain.BehaviorPattern]). */
    BEHAVIOR_COACH,
}

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

/**
 * Why a provider call didn't produce text — [AiRouter] uses this to decide
 * whether to fall back to the next provider and, for [RATE_LIMITED], whether
 * to mark that provider exhausted for the rest of the day (planning.md §7.3)
 * so later requests skip it without spending a network call.
 */
enum class AiFailureKind {
    /**
     * 429 que não é a cota do dia: limite por minuto, ou a fila compartilhada dos modelos
     * gratuitos cheia ("temporarily rate-limited upstream" no OpenRouter). O roteador passa
     * para o próximo provedor nesta chamada, mas **não** marca este como esgotado do dia.
     */
    BUSY,

    /** No API key configured for this provider yet. */
    NO_KEY,

    /** Invalid/revoked key or key lacking permission for the model (HTTP 401/403, or Gemini's 400 "invalid key"). */
    AUTH_ERROR,

    /** Free-tier quota/rate limit hit (HTTP 429). */
    RATE_LIMITED,

    /** The configured model id doesn't exist or was retired (HTTP 404, or an unknown-model 400). */
    MODEL_UNAVAILABLE,

    /** The request timed out before the provider responded. */
    TIMEOUT,

    /** Any other network-level failure (DNS, connection reset, no connectivity). */
    NETWORK_ERROR,

    /** The provider responded 2xx but with no usable text. */
    EMPTY_RESPONSE,

    /** Anything else (unexpected HTTP status, malformed payload). */
    UNKNOWN,
}

sealed interface AiResponse {
    data class Success(val text: String) : AiResponse

    /** [reason] is user-facing pt-BR text; [kind] is what [AiRouter] uses to decide fallback/exhaustion. */
    data class Unavailable(val reason: String, val kind: AiFailureKind = AiFailureKind.UNKNOWN) : AiResponse
}

/** The five free-tier providers from planning.md §7.2, in the §7.3 fallback order. */
enum class ProviderId(val displayName: String) {
    GEMINI("Gemini"),
    GROQ("Groq"),
    OPENROUTER("OpenRouter"),
    MISTRAL("Mistral"),
    CEREBRAS("Cerebras"),
}

/** UI-friendly mirror of [AiResponse] plus the "not requested yet" state, for screens rendering AI text inline. */
sealed interface AiText {
    data object Loading : AiText
    data class Ready(val text: String) : AiText
    data class Unavailable(val reason: String) : AiText
}

/**
 * Diz se um 429 é a cota do **dia** acabando ou só um limite momentâneo. Antes todo 429 virava
 * "esgotado até a meia-noite": uma conta nova do OpenRouter aparecia como "Sem cota hoje" logo
 * no teste de conexão, porque a fila dos modelos `:free` estava cheia naquele minuto.
 * Na dúvida, trata como momentâneo — o custo é uma tentativa a mais, não um dia sem o provedor.
 */
object RateLimitClassifier {
    private val dailyMarkers = listOf(
        "per-day", "per day", "perday", "daily", "requests per day", "tokens per day",
        "(rpd)", "(tpd)", "free-models-per-day", "quota exceeded for today", "tokens per month", "monthly",
    )

    fun isDailyLimit(body: String?): Boolean {
        val text = body?.lowercase() ?: return false
        return dailyMarkers.any { it in text }
    }

    fun kindFor429(body: String?): AiFailureKind =
        if (isDailyLimit(body)) AiFailureKind.RATE_LIMITED else AiFailureKind.BUSY
}
