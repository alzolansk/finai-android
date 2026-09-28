package com.finai.app.data.ai

import com.finai.app.data.prefs.AiKeyStore
import org.json.JSONObject

/**
 * Groq's OpenAI-compatible endpoint (planning.md §7.2) — free developer tier,
 * gated by rate limits only (no credit card, no expiring trial credits).
 * Second in the fallback order after Gemini.
 */
class GroqAiProvider(
    keyStore: AiKeyStore,
    model: String = DEFAULT_MODEL,
) : OpenAiCompatibleAiProvider(keyStore, ProviderId.GROQ, ENDPOINT, model) {

    /**
     * gpt-oss raciocina antes de responder, e o raciocínio conta no limite de tokens: sem isto
     * a resposta podia vir vazia. Esforço baixo basta para os textos curtos do app, e o
     * raciocínio não volta no corpo (só a resposta final interessa). `reasoning_format` não é
     * aceito pelo gpt-oss e `include_reasoning` exclui ele — por isso só estes dois campos
     * (https://console.groq.com/docs/reasoning, conferido em 28/09/2026).
     */
    override fun extraBody(body: JSONObject) {
        body.put("reasoning_effort", "low")
        body.put("include_reasoning", false)
    }

    override val maxTokens: Int = 1_200

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

        /**
         * Modelo do plano gratuito. Em 28/09/2026 o `llama-3.1-8b-instant` (usado até então)
         * saiu do plano Free e ficou só no Enterprise; os de texto gratuitos passaram a ser
         * `openai/gpt-oss-120b`, `openai/gpt-oss-20b` e `qwen/qwen3.8-27b`, todos com
         * 1K requisições e 200K tokens por dia (https://console.groq.com/docs/rate-limits).
         * O 120B é o de melhor texto com o mesmo limite. Confira a página antes de trocar:
         * o catálogo gratuito do Groq muda sem aviso.
         */
        const val DEFAULT_MODEL = "openai/gpt-oss-120b"
    }
}
