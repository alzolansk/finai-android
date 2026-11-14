package com.finai.app.domain.assistant

import com.finai.app.domain.scenario.ScenarioReplyGuard

/**
 * O laço do assistente (planning-v2, fase 9): a IA pode pedir até [AssistantTools.MAX_CALLS]
 * consultas locais por pergunta, no protocolo de linha JSON ([AssistantTools.parseCall]); o app
 * executa, devolve o resultado e a IA responde. Kotlin puro: a chamada de IA entra como função.
 *
 * Quando houve consulta, a resposta final passa pelo [ScenarioReplyGuard] contra os fatos (resumo
 * + resultados). Se citar um valor ou data fora deles, vale o texto dos resultados, nunca o da IA.
 */
object AssistantLoop {
    /** Prefixo da linha "consultei: …" que o chat mostra acima da resposta. */
    const val CONSULTED_PREFIX = "consultei: "

    data class Outcome(
        /** Texto final da IA; nulo quando ela ficou indisponível. */
        val text: String?,
        val consulted: List<ToolResult>,
    )

    /**
     * [ask] recebe os resultados acumulados e devolve o texto da IA (nulo = indisponível).
     * [run] executa uma consulta pedida.
     */
    suspend fun answer(ask: suspend (List<ToolResult>) -> String?, run: suspend (ToolCall) -> ToolResult): Outcome {
        val consulted = mutableListOf<ToolResult>()
        var text = ask(consulted) ?: return Outcome(null, consulted)
        while (consulted.size < AssistantTools.MAX_CALLS) {
            val call = AssistantTools.parseCall(text) ?: break
            consulted += run(call)
            text = ask(consulted.toList()) ?: return Outcome(null, consulted)
        }
        return Outcome(text, consulted)
    }

    /**
     * O que vai para o chat. Sem consulta, a resposta da IA como veio. Com consulta, a linha
     * "consultei: …" e a resposta conferida; se ela não passar na trava (ou ainda for um pedido
     * de consulta), os próprios resultados.
     */
    fun reply(outcome: Outcome, baseFacts: String): String? {
        val text = outcome.text ?: return outcome.consulted.takeIf { it.isNotEmpty() }?.let(::fallback)
        if (outcome.consulted.isEmpty()) return text.takeIf { AssistantTools.parseCall(it) == null }
        val facts = baseFacts + "\n" + outcome.consulted.joinToString("\n") { it.facts }
        val accepted = AssistantTools.parseCall(text) == null && ScenarioReplyGuard.accepts(text, facts)
        return if (accepted) consultedLine(outcome.consulted) + "\n" + text.trim() else fallback(outcome.consulted)
    }

    fun consultedLine(results: List<ToolResult>): String = CONSULTED_PREFIX + results.joinToString(" · ") { it.label }

    private fun fallback(results: List<ToolResult>): String =
        consultedLine(results) + "\n" + results.joinToString("\n") { it.facts.trim() }

    /** Separa a linha "consultei: …" do resto da mensagem gravada; nula quando não houve consulta. */
    fun splitConsulted(message: String): Pair<String?, String> {
        if (!message.startsWith(CONSULTED_PREFIX)) return null to message
        val line = message.substringBefore('\n')
        return line.removePrefix(CONSULTED_PREFIX) to message.substringAfter('\n', "")
    }
}
