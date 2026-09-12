package com.finai.app.data.ai

import android.content.Context
import android.util.Log
import com.finai.app.data.prefs.AiKeyStore

/**
 * The Fase 3 fallback layer (planning.md §7.3/§9): the single [AiProvider]
 * every ViewModel actually talks to. Tries each configured provider in the
 * fixed order below, skipping one that has no key or hit its rate limit
 * earlier today, and only reports [AiResponse.Unavailable] once every
 * provider with a key has failed — the "degradação graciosa" planning.md §4
 * calls for: the app's local numbers stay correct, only the language layer
 * goes quiet.
 *
 * Never falls back to a paid tier or paid model: every adapter below hard-codes
 * a free-tier model id (see each adapter's `DEFAULT_MODEL` doc comment), and
 * there is no path in this class that changes model or tier based on failure —
 * a provider that fails is skipped, never retried on a "better" paid model.
 *
 * [usageStore] and [adapters] are constructor parameters (not built inline)
 * purely so `AiRouterTest` can exercise the fallback/exhaustion/cache logic
 * with fakes instead of a real Context/DataStore/network — production code
 * always goes through the secondary `Context` constructor.
 */
class AiRouter(
    private val usageStore: UsageTracker,
    private val adapters: List<Pair<ProviderId, AiProvider>>,
) : AiProvider {

    constructor(context: Context) : this(ProviderUsageStore.get(context), defaultAdapters(context))

    override suspend fun generate(request: AiRequest): AiResponse {
        AiResponseCache.get(request)?.let { return AiResponse.Success(it) }

        var anyKeyConfigured = false
        var lastReason: String? = null

        for ((providerId, adapter) in adapters) {
            if (usageStore.isExhaustedToday(providerId)) {
                Log.i(TAG, "${providerId.displayName} pulado: cota esgotada hoje.")
                continue
            }

            usageStore.recordAttempt(providerId)
            when (val response = adapter.generate(request)) {
                is AiResponse.Success -> {
                    AiResponseCache.put(request, response.text)
                    return response
                }
                is AiResponse.Unavailable -> {
                    Log.i(TAG, "${providerId.displayName} indisponível (${response.kind}): ${response.reason}")
                    if (response.kind != AiFailureKind.NO_KEY) anyKeyConfigured = true
                    if (response.kind == AiFailureKind.RATE_LIMITED) usageStore.markExhaustedToday(providerId)
                    lastReason = response.reason
                }
            }
        }

        return if (!anyKeyConfigured) {
            AiResponse.Unavailable(
                "Nenhum provedor de IA configurado ainda. Toque no ícone de engrenagem no topo para adicionar uma chave " +
                    "gratuita (Gemini, Groq, OpenRouter, Mistral ou Cerebras).",
                AiFailureKind.NO_KEY,
            )
        } else {
            AiResponse.Unavailable(
                "Todos os provedores de IA configurados estão indisponíveis ou sem cota agora (${lastReason ?: "motivo desconhecido"}). " +
                    "Os números continuam calculados normalmente; o texto explicativo volta assim que algum provedor renovar a cota.",
                AiFailureKind.UNKNOWN,
            )
        }
    }

    companion object {
        private const val TAG = "AiRouter"

        private fun defaultAdapters(context: Context): List<Pair<ProviderId, AiProvider>> {
            val keyStore = AiKeyStore.get(context)
            return listOf(
                ProviderId.GEMINI to GeminiAiProvider(keyStore),
                ProviderId.GROQ to GroqAiProvider(keyStore),
                ProviderId.OPENROUTER to OpenRouterAiProvider(keyStore),
                ProviderId.MISTRAL to MistralAiProvider(keyStore),
                ProviderId.CEREBRAS to CerebrasAiProvider(keyStore),
            )
        }
    }
}
