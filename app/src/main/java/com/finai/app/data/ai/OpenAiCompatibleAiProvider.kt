package com.finai.app.data.ai

import com.finai.app.data.prefs.AiKeyStore
import com.finai.app.util.FinaiLog
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared implementation for every free-tier provider that speaks the OpenAI
 * "chat completions" wire format (`POST .../chat/completions` with a
 * `messages: [{role, content}]` array and Bearer auth) — [GroqAiProvider],
 * [OpenRouterAiProvider], [MistralAiProvider] and [CerebrasAiProvider] all
 * differ only in endpoint URL, model id, and (for OpenRouter) a couple of
 * optional extra headers. [GeminiAiProvider] is the one adapter that doesn't
 * fit this shape and implements [AiProvider] directly.
 */
abstract class OpenAiCompatibleAiProvider(
    private val keyStore: AiKeyStore,
    private val providerId: ProviderId,
    private val endpointUrl: String,
    private val model: String,
) : AiProvider {

    /** Extra headers beyond `Authorization`/`Content-Type` — only [OpenRouterAiProvider] needs this. */
    protected open fun extraHeaders(): Map<String, String> = emptyMap()

    /** Campos extras no corpo da requisição (ex.: controle de raciocínio do gpt-oss no Groq). */
    protected open fun extraBody(body: JSONObject) {}

    /** Teto de tokens da resposta. Modelos que raciocinam antes de responder precisam de mais. */
    protected open val maxTokens: Int = 500

    override suspend fun generate(request: AiRequest): AiResponse {
        val apiKey = keyStore.currentKey(providerId)?.takeIf { it.isNotBlank() }
            ?: return AiResponse.Unavailable(
                "Nenhuma chave do ${providerId.displayName} configurada.",
                AiFailureKind.NO_KEY,
            )

        return withContext(Dispatchers.IO) {
            try {
                val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    extraHeaders().forEach { (name, value) -> setRequestProperty(name, value) }
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }

                val body = JSONObject().apply {
                    put("model", model)
                    put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", request.systemInstruction))
                            .put(JSONObject().put("role", "user").put("content", request.prompt)),
                    )
                    put("temperature", 0.4)
                    put("max_tokens", maxTokens)
                    extraBody(this)
                }

                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                if (status !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    FinaiLog.w(TAG, "${providerId.displayName} respondeu HTTP $status")
                    FinaiLog.debugBody(TAG, "Corpo do erro de ${providerId.displayName}", error)
                    val kind = failureKindFor(status, error)
                    return@withContext AiResponse.Unavailable(unavailableMessageFor(status, kind), kind)
                }

                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val text = extractText(payload)
                if (text.isNullOrBlank()) {
                    AiResponse.Unavailable("${providerId.displayName} não retornou texto para esta solicitação.", AiFailureKind.EMPTY_RESPONSE)
                } else {
                    AiResponse.Success(text.trim())
                }
            } catch (e: SocketTimeoutException) {
                FinaiLog.w(TAG, "Timeout ao chamar ${providerId.displayName}", e)
                AiResponse.Unavailable("${providerId.displayName} demorou demais para responder.", AiFailureKind.TIMEOUT)
            } catch (e: Exception) {
                FinaiLog.w(TAG, "Falha ao chamar ${providerId.displayName}", e)
                AiResponse.Unavailable("Não foi possível falar com o ${providerId.displayName} agora.", AiFailureKind.NETWORK_ERROR)
            }
        }
    }

    private fun extractText(payload: String): String? {
        val choice = JSONObject(payload).optJSONArray("choices")?.optJSONObject(0) ?: return null
        return choice.optJSONObject("message")?.optString("content")
    }

    private fun unavailableMessageFor(status: Int, kind: AiFailureKind): String = when {
        status == 401 || status == 403 -> "Chave do ${providerId.displayName} inválida ou sem permissão. Confira a chave nas configurações."
        status == 404 -> "Modelo configurado para ${providerId.displayName} não encontrado — pode ter sido descontinuado."
        kind == AiFailureKind.RATE_LIMITED -> "Cota gratuita do ${providerId.displayName} esgotada por hoje."
        kind == AiFailureKind.BUSY -> "O ${providerId.displayName} está com muitos pedidos agora (limite por minuto ou fila do modelo gratuito). Tente de novo em alguns minutos; a chave está certa."
        else -> "${providerId.displayName} está indisponível agora (HTTP $status)."
    }

    private fun failureKindFor(status: Int, body: String?): AiFailureKind = when (status) {
        401, 403 -> AiFailureKind.AUTH_ERROR
        404 -> AiFailureKind.MODEL_UNAVAILABLE
        429 -> RateLimitClassifier.kindFor429(body)
        else -> AiFailureKind.UNKNOWN
    }

    companion object {
        private const val TAG = "OpenAiCompatibleAiProvider"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
