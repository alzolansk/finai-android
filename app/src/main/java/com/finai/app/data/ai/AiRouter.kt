package com.finai.app.data.ai

import android.content.Context
import com.finai.app.data.prefs.AiKeyStore
import com.finai.app.util.FinaiLog
import kotlinx.coroutines.CancellationException

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

    /**
     * Nunca lança (Fase 6). Todo chamador — inclusive o
     * [com.finai.app.data.work.FinanceCheckWorker], que roda com o app fechado —
     * pode tratar o resultado como "texto ou motivo", sem `try`. Os adaptadores
     * já convertem falha de rede em [AiResponse.Unavailable]; o `catch` aqui
     * cobre o inesperado (DataStore, OOM ao montar um prompt grande) para que a
     * camada de linguagem falhe do jeito que a planning.md §4 manda: em silêncio
     * e com os números locais intactos.
     */
    override suspend fun generate(request: AiRequest): AiResponse = try {
        route(request)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        FinaiLog.e(TAG, "Falha inesperada no roteador de IA", t)
        AiResponse.Unavailable(
            "Não foi possível gerar o texto explicativo agora. Os números continuam calculados normalmente.",
            AiFailureKind.UNKNOWN,
        )
    }

    private suspend fun route(request: AiRequest): AiResponse {
        AiResponseCache.get(request)?.let { return AiResponse.Success(it) }

        var anyKeyConfigured = false
        // O motivo mais útil entre os provedores que falharam, não o último da
        // fila (Fase 6). Como a ordem termina nos provedores que o usuário
        // costuma não configurar, "o último" quase sempre era "Nenhuma chave do
        // Cerebras configurada" — escondendo que o provedor principal tinha
        // respondido 401 ou 429, que é o que ele precisa saber para agir.
        var bestReason: String? = null
        var bestRank = Int.MAX_VALUE

        for ((providerId, adapter) in adapters) {
            if (usageStore.isExhaustedToday(providerId)) {
                FinaiLog.i(TAG, "${providerId.displayName} pulado: cota esgotada hoje.")
                continue
            }

            usageStore.recordAttempt(providerId)
            when (val response = adapter.generate(request)) {
                is AiResponse.Success -> {
                    AiResponseCache.put(request, response.text)
                    return response
                }
                is AiResponse.Unavailable -> {
                    FinaiLog.i(TAG, "${providerId.displayName} indisponível (${response.kind}).")
                    if (response.kind != AiFailureKind.NO_KEY) anyKeyConfigured = true
                    if (response.kind == AiFailureKind.RATE_LIMITED) usageStore.markExhaustedToday(providerId)
                    val rank = reasonRank(response.kind)
                    if (rank < bestRank) {
                        bestRank = rank
                        bestReason = response.reason
                    }
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
                "Todos os provedores de IA configurados estão indisponíveis ou sem cota agora (${bestReason ?: "motivo desconhecido"}). " +
                    "Os números continuam calculados normalmente; o texto explicativo volta assim que algum provedor renovar a cota.",
                AiFailureKind.UNKNOWN,
            )
        }
    }

    /**
     * Quanto o motivo de uma falha ajuda o usuário a resolvê-la. Menor é
     * melhor: uma chave inválida ou cota estourada é acionável; "não configurei
     * esse provedor" é ruído quando outro provedor de fato falhou.
     */
    private fun reasonRank(kind: AiFailureKind): Int = when (kind) {
        AiFailureKind.AUTH_ERROR -> 0
        AiFailureKind.RATE_LIMITED -> 1
        AiFailureKind.MODEL_UNAVAILABLE -> 2
        AiFailureKind.NETWORK_ERROR, AiFailureKind.TIMEOUT, AiFailureKind.BUSY -> 3
        AiFailureKind.EMPTY_RESPONSE, AiFailureKind.UNKNOWN -> 4
        AiFailureKind.NO_KEY -> 5
    }

    companion object {
        private const val TAG = "AiRouter"

        private fun defaultAdapters(context: Context): List<Pair<ProviderId, AiProvider>> =
            ProviderId.entries.map { it to adapterFor(context, it) }

        /**
         * O adaptador de um provedor só, fora do roteador — usado pelo "Testar
         * conexão" das Configurações, que precisa saber se *aquela* chave
         * funciona, sem fallback nem cache escondendo a resposta.
         */
        fun adapterFor(context: Context, id: ProviderId): AiProvider {
            val keyStore = AiKeyStore.get(context)
            return when (id) {
                ProviderId.GEMINI -> GeminiAiProvider(keyStore)
                ProviderId.GROQ -> GroqAiProvider(keyStore)
                ProviderId.OPENROUTER -> OpenRouterAiProvider(keyStore)
                ProviderId.MISTRAL -> MistralAiProvider(keyStore)
                ProviderId.CEREBRAS -> CerebrasAiProvider(keyStore)
            }
        }
    }
}
