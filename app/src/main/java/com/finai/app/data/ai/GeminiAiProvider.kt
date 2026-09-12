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
 * Calls the Gemini API's `generateContent` REST endpoint directly from the
 * app, with no backend in between (planning.md §1/§7.2). Uses plain
 * `HttpURLConnection`/`org.json` (already part of the Android SDK) rather
 * than adding a networking dependency — [AiRouter] is the only caller that
 * matters across all five adapters, so a shared client wasn't worth pulling
 * in for Fase 3 either.
 *
 * The only adapter with a bespoke request/response shape (Gemini's
 * `system_instruction`/`contents`, not OpenAI-style `messages`) — the other
 * four providers share [OpenAiCompatibleAiProvider] instead.
 */
class GeminiAiProvider(
    private val keyStore: AiKeyStore,
    private val model: String = DEFAULT_MODEL,
) : AiProvider {

    override suspend fun generate(request: AiRequest): AiResponse {
        val apiKey = keyStore.currentKey(ProviderId.GEMINI)?.takeIf { it.isNotBlank() }
            ?: return AiResponse.Unavailable(
                "Nenhuma chave do Gemini configurada ainda. Toque no ícone de engrenagem no topo para adicionar a sua.",
                AiFailureKind.NO_KEY,
            )

        return withContext(Dispatchers.IO) {
            try {
                // Chave vai no header `x-goog-api-key`, nunca na query string
                // (Fase 6): `HttpURLConnection` repete a URL chamada na mensagem
                // de várias exceções de IO, então `?key=...` acabaria no logcat
                // e em qualquer bug report do aparelho. O header é a forma
                // documentada e equivalente de autenticar na Gemini API.
                val connection = (URL("$ENDPOINT_BASE/$model:generateContent").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-goog-api-key", apiKey)
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }

                val body = JSONObject().apply {
                    put(
                        "system_instruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.systemInstruction))),
                    )
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", request.prompt))),
                        ),
                    )
                    put("generationConfig", JSONObject().put("temperature", 0.4).put("maxOutputTokens", 500))
                }

                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                if (status !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    FinaiLog.w(TAG, "Gemini respondeu HTTP $status")
                    FinaiLog.debugBody(TAG, "Corpo do erro do Gemini", error)
                    return@withContext AiResponse.Unavailable(unavailableMessageFor(status), failureKindFor(status))
                }

                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val text = extractText(payload)
                if (text.isNullOrBlank()) {
                    AiResponse.Unavailable("O Gemini não retornou texto para esta solicitação.", AiFailureKind.EMPTY_RESPONSE)
                } else {
                    AiResponse.Success(text.trim())
                }
            } catch (e: SocketTimeoutException) {
                FinaiLog.w(TAG, "Timeout ao chamar o Gemini", e)
                AiResponse.Unavailable("O Gemini demorou demais para responder.", AiFailureKind.TIMEOUT)
            } catch (e: Exception) {
                FinaiLog.w(TAG, "Falha ao chamar o Gemini", e)
                AiResponse.Unavailable("Não foi possível falar com o Gemini agora. Verifique sua conexão.", AiFailureKind.NETWORK_ERROR)
            }
        }
    }

    private fun extractText(payload: String): String? {
        val candidate = JSONObject(payload).optJSONArray("candidates")?.optJSONObject(0) ?: return null
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return null
        return (0 until parts.length()).joinToString("") { i -> parts.optJSONObject(i)?.optString("text").orEmpty() }
    }

    private fun unavailableMessageFor(status: Int): String = when (status) {
        400 -> "Chave do Gemini inválida ou requisição rejeitada. Confira a chave nas configurações."
        403 -> "Chave do Gemini sem permissão para este modelo."
        404 -> "Modelo do Gemini configurado (\"$model\") não encontrado — pode ter sido descontinuado. Atualize DEFAULT_MODEL em GeminiAiProvider.kt."
        429 -> "Cota gratuita do Gemini esgotada por hoje."
        else -> "O Gemini está indisponível agora (HTTP $status)."
    }

    private fun failureKindFor(status: Int): AiFailureKind = when (status) {
        400, 401, 403 -> AiFailureKind.AUTH_ERROR
        404 -> AiFailureKind.MODEL_UNAVAILABLE
        429 -> AiFailureKind.RATE_LIMITED
        else -> AiFailureKind.UNKNOWN
    }

    companion object {
        private const val TAG = "GeminiAiProvider"
        private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        /**
         * Free-tier Gemini model as of this writing (confirmed against
         * https://ai.google.dev/gemini-api/docs/pricing — 2026-09-11: 10 RPM /
         * 250 RPD on the free tier). If Google retires it or moves it behind
         * billing, change only this constant — check the pricing page above
         * first, since names and free-tier status change without notice
         * (planning.md §7.2). Nothing else in the app depends on a specific
         * model name.
         */
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    }
}
