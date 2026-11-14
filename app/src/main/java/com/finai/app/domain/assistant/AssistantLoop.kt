package com.finai.app.domain.assistant

/**
 * A linha "consultei: …" que o chat mostra acima de uma resposta que usou consultas locais. O
 * laço em si mora em [AssistantOrchestrator]; aqui fica só o formato gravado na mensagem.
 */
object AssistantLoop {
    const val CONSULTED_PREFIX = "consultei: "

    fun withConsulted(labels: List<String>, text: String): String =
        if (labels.isEmpty()) text else CONSULTED_PREFIX + labels.joinToString(" · ") + "\n" + text.trim()

    /** Separa a linha "consultei: …" do resto da mensagem gravada; nula quando não houve consulta. */
    fun splitConsulted(message: String): Pair<String?, String> {
        if (!message.startsWith(CONSULTED_PREFIX)) return null to message
        val line = message.substringBefore('\n')
        return line.removePrefix(CONSULTED_PREFIX) to message.substringAfter('\n', "")
    }
}
