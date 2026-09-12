package com.finai.app.data.ai

import com.finai.app.data.prefs.AiKeyStore

/**
 * OpenRouter's chat-completions endpoint, restricted to a model whose id
 * carries the `:free` suffix (planning.md §7.2/§9 — "nunca faça fallback
 * para modelo pago"). Third in the fallback order.
 *
 * `HTTP-Referer`/`X-Title` are OpenRouter's optional attribution headers
 * (https://openrouter.ai/docs) — static app-identifying strings, no user
 * data, sent only to OpenRouter.
 */
class OpenRouterAiProvider(
    keyStore: AiKeyStore,
    model: String = DEFAULT_MODEL,
) : OpenAiCompatibleAiProvider(keyStore, ProviderId.OPENROUTER, ENDPOINT, model) {

    override fun extraHeaders(): Map<String, String> = mapOf(
        "HTTP-Referer" to "https://github.com/finai-app",
        "X-Title" to "FinAI",
    )

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

        /**
         * A currently free (`:free`, $0 prompt/completion price) general-purpose
         * instruct model, confirmed live against
         * `GET https://openrouter.ai/api/v1/models` on 2026-09-11. OpenRouter's
         * free roster rotates often — if this id 404s or stops being free,
         * pick another `:free` entry from that endpoint (filter for
         * `pricing.prompt == "0"` and `id` ending in `:free`) and update only
         * this constant.
         */
        const val DEFAULT_MODEL = "google/gemma-4-31b-it:free"
    }
}
