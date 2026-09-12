package com.finai.app.data.ai

import com.finai.app.data.prefs.AiKeyStore

/**
 * Mistral's "La Plateforme" chat-completions endpoint, on the free
 * ("Experiment") tier — rate-limited access to the open-weight model family,
 * no card required for that tier (planning.md §7.2). Fourth in the fallback
 * order, treated purely as a reserve since Mistral doesn't publish exact
 * free-tier RPD numbers.
 */
class MistralAiProvider(
    keyStore: AiKeyStore,
    model: String = DEFAULT_MODEL,
) : OpenAiCompatibleAiProvider(keyStore, ProviderId.MISTRAL, ENDPOINT, model) {

    companion object {
        private const val ENDPOINT = "https://api.mistral.ai/v1/chat/completions"

        /**
         * `-latest` alias for Mistral's open-weight "Small" model — Mistral
         * keeps these aliases pointing at the current generation
         * (confirmed the "Small" line is still open-weight, not Premier-only,
         * against https://docs.mistral.ai/getting-started/models/models_overview/
         * on 2026-09-11; the exact model behind the alias moves on without
         * notice). Check that page — and that a paid plan isn't required for
         * it — before changing this constant.
         */
        const val DEFAULT_MODEL = "mistral-small-latest"
    }
}
