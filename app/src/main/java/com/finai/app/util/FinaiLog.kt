package com.finai.app.util

import android.util.Log
import com.finai.app.BuildConfig

/**
 * O único lugar do app que escreve no logcat (Fase 6 — endurecimento,
 * planning.md §9).
 *
 * Três regras, todas por causa de uma coisa só: em release o logcat é legível
 * por qualquer app com permissão de leitura no aparelho do usuário e acaba em
 * bug report anexado a chamado de suporte.
 *
 * 1. [d]/[i] (diagnóstico de fluxo: qual provedor foi pulado, que nada havia
 *    para notificar) **não existem em release** — a chamada some no build
 *    minificado porque o corpo é guardado por `BuildConfig.DEBUG`, que o R8
 *    resolve como constante.
 * 2. [w]/[e] continuam em release, porque são o que explica um problema real
 *    relatado pelo usuário — mas só com mensagem própria e a *classe* da
 *    exceção. Nunca o `Throwable` inteiro: uma stack trace de
 *    `HttpURLConnection` carrega a URL chamada, e uma mensagem de exceção de
 *    IO carrega o caminho do arquivo importado.
 * 3. Corpo de resposta HTTP de provedor de IA e qualquer texto que possa
 *    conter chave ou dado financeiro só saem no log em debug, via [debugBody].
 *
 * Nada aqui recebe chave de API por construção: nenhum chamador passa a chave
 * adiante. [redactKeys] existe como rede de segurança para texto vindo de
 * fora (corpo de erro de provedor, mensagem de exceção) que possa ecoar a
 * chave enviada.
 */
object FinaiLog {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, compose(message, error))
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, compose(message, error))
    }

    /**
     * Corpo de resposta/payload cru: só em debug, e ainda assim com qualquer
     * coisa que pareça uma chave de API mascarada. Em release não chega ao
     * logcat de forma alguma.
     */
    fun debugBody(tag: String, label: String, body: String?) {
        if (!BuildConfig.DEBUG || body.isNullOrBlank()) return
        Log.d(tag, "$label: ${redactKeys(body).take(MAX_BODY_CHARS)}")
    }

    /**
     * Em release, uma exceção vira só o nome da classe: `SocketTimeoutException`
     * diz o que houve sem publicar a URL (que carregaria a chave, no formato
     * antigo do Gemini) nem o caminho do arquivo que o usuário importou. Em
     * debug a mensagem original ajuda, já redigida.
     */
    private fun compose(message: String, error: Throwable?): String {
        if (error == null) return message
        val detail = if (BuildConfig.DEBUG) {
            "${error::class.java.simpleName}: ${redactKeys(error.message.orEmpty())}"
        } else {
            error::class.java.simpleName
        }
        return "$message [$detail]"
    }

    /**
     * Mascara o que tiver cara de chave de API num texto de terceiro:
     * `key=...`/`api_key=...` em query string, `Bearer <token>` e chave do
     * Google AI Studio (`AIza...`). Não é validação de segurança — a garantia
     * de verdade é que nenhum chamador loga a chave; isto é o cinto de
     * segurança para texto que o provedor devolve ecoando a requisição.
     */
    fun redactKeys(text: String): String = text
        .replace(QUERY_KEY_REGEX, "$1=<redacted>")
        .replace(BEARER_REGEX, "Bearer <redacted>")
        .replace(GOOGLE_KEY_REGEX, "<redacted>")

    private const val MAX_BODY_CHARS = 500
    private val QUERY_KEY_REGEX = Regex("(?i)\\b(key|api_key|apikey|access_token)=[^&\\s\"']+")
    private val BEARER_REGEX = Regex("(?i)Bearer\\s+[A-Za-z0-9._\\-]+")
    private val GOOGLE_KEY_REGEX = Regex("AIza[0-9A-Za-z._\\-]{10,}")
}
