package com.finai.app.data.ai

import com.finai.app.data.prefs.AiKeyStore

/**
 * Groq's OpenAI-compatible endpoint (planning.md §7.2) — free developer tier,
 * gated by rate limits only (no credit card, no expiring trial credits).
 * Second in the fallback order after Gemini.
 */
class GroqAiProvider(
    keyStore: AiKeyStore,
    model: String = DEFAULT_MODEL,
) : OpenAiCompatibleAiProvider(keyStore, ProviderId.GROQ, ENDPOINT, model) {

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

        /**
         * Free-tier model as of this writing (confirmed against
         * https://console.groq.com/docs/models — 2026-09-11). Llama 3.1 8B is
         * Groq's most generously rate-limited free model; check the docs page
         * above before changing, since Groq's free catalog and limits shift
         * often.
         */
        const val DEFAULT_MODEL = "llama-3.1-8b-instant"
    }
}
