package com.finai.app.data.ai

import android.util.Log
import com.finai.app.data.prefs.AiKeyStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * First (and, for Fase 2, only) [AiProvider]: calls the Gemini API's
 * `generateContent` REST endpoint directly from the app, with no backend in
 * between (planning.md §1/§7.2). Uses plain `HttpURLConnection`/`org.json`
 * (already part of the Android SDK) rather than adding a networking
 * dependency for a single endpoint — Fase 3's router can swap this for
 * Retrofit/Ktor if a multi-provider client benefits from it.
 *
 * Only talks to Gemini; the fallback across providers from planning.md §7.3
 * is explicitly Fase 3 scope, not implemented here.
 */
class GeminiAiProvider(
    private val keyStore: AiKeyStore,
    private val model: String = DEFAULT_MODEL,
) : AiProvider {

    override suspend fun generate(request: AiRequest): AiResponse {
        val apiKey = keyStore.apiKey.first()?.takeIf { it.isNotBlank() }
            ?: return AiResponse.Unavailable(
                "Nenhuma chave do Gemini configurada ainda. Toque no ícone de engrenagem no topo para adicionar a sua.",
            )

        return withContext(Dispatchers.IO) {
            try {
                val connection = (URL("$ENDPOINT_BASE/$model:generateContent?key=$apiKey").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15_000
                    readTimeout = 30_000
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
                    Log.w(TAG, "Gemini HTTP $status: $error")
                    return@withContext AiResponse.Unavailable(unavailableMessageFor(status))
                }

                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val text = extractText(payload)
                if (text.isNullOrBlank()) AiResponse.Unavailable("O Gemini não retornou texto para esta solicitação.")
                else AiResponse.Success(text.trim())
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao chamar o Gemini", e)
                AiResponse.Unavailable("Não foi possível falar com a IA agora. Verifique sua conexão e tente de novo.")
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
        429 -> "Cota gratuita do Gemini esgotada por hoje. Tente novamente amanhã."
        else -> "O Gemini está indisponível agora (HTTP $status)."
    }

    companion object {
        private const val TAG = "GeminiAiProvider"
        private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * Free-tier-eligible Gemini model as of this writing (planning.md
         * §7.2). If Google retires it, change only this constant — nothing
         * else in the app depends on a specific model name.
         */
        const val DEFAULT_MODEL = "gemini-3.7-flash"
    }
}
