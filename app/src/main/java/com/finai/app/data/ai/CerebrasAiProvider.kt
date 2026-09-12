package com.finai.app.data.ai

import com.finai.app.data.prefs.AiKeyStore

/**
 * Cerebras Cloud's chat-completions endpoint — free tier is a rate-limited
 * daily quota on an account that never added billing (planning.md §7.2).
 * Last in the fallback order, per planning.md §7.3.
 */
class CerebrasAiProvider(
    keyStore: AiKeyStore,
    model: String = DEFAULT_MODEL,
) : OpenAiCompatibleAiProvider(keyStore, ProviderId.CEREBRAS, ENDPOINT, model) {

    companion object {
        private const val ENDPOINT = "https://api.cerebras.ai/v1/chat/completions"

        /**
         * Confirmed live against Cerebras's public, unauthenticated model
         * catalog (`GET https://api.cerebras.ai/public/v1/models`) on
         * 2026-09-11, which currently lists exactly two models: this one and
         * `qwen-3.8-27b`. GPT-OSS 120B is the better general-purpose pick.
         * Note the catalog shows a non-zero per-token price — that's
         * Cerebras's usage-based rate for accounts *with* billing attached;
         * an account with no payment method on file simply gets rate-limited
         * (HTTP 429) once the free daily quota is used, never charged. Re-check
         * that endpoint before changing this constant.
         */
        const val DEFAULT_MODEL = "gpt-oss-120b"
    }
}
