package com.finai.app.domain.assistant

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toEpochMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Fase 9 (P1): consultas locais e o laço com a IA, com fakes no lugar do provedor. */
class AssistantToolsTest {
    private val today = LocalDate.of(2026, 11, 8)
    private fun g(day: LocalDate, cents: Long, desc: String, cat: String) = TransacaoEntity(
        data = day.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = cat, contaOrigem = "Nubank", recorrente = false, origem = "manual")
    private val data = AssistantData(
        contas = emptyList(),
        transacoes = listOf(
            g(today.minusDays(1), 4_590, "iFood", "Alimentação"),
            g(today.minusDays(2), 3_210, "IFOOD", "Alimentação"),
            g(LocalDate.of(2026, 10, 12), 6_000, "iFood", "Alimentação"),
            g(today.minusDays(3), 2_300, "Uber", "Transporte"),
        ),
        dividas = emptyList(),
        marginCents = 30_000,
    )

    @Test fun parsesTheJsonLine() {
        val call = AssistantTools.parseCall("Vou consultar.\n{\"consulta\":\"buscar_lancamentos\",\"texto\":\"ifood\",\"periodo\":\"mes_atual\"}")!!
        assertEquals("buscar_lancamentos", call.name)
        assertEquals("ifood", call.args["texto"])
        assertNull(AssistantTools.parseCall("Você gastou bastante com delivery."))
        assertNull(AssistantTools.parseCall("{\"consulta\":\"apagar_tudo\"}"))
    }

    @Test fun searchFindsAccentAndCaseInsensitive() {
        val r = AssistantTools.run(ToolCall("buscar_lancamentos", mapOf("texto" to "ifood", "periodo" to "mes_atual")), data, today)
        assertTrue(r.facts, r.facts.startsWith("2 gasto(s) com \"ifood\" em novembro, somando R$ 78."))
        assertEquals("\"ifood\" em novembro", r.label)
    }

    @Test fun totalByCategory() {
        val r = AssistantTools.run(ToolCall("total_por_categoria", mapOf("categoria" to "alimentação", "periodo" to "mes_passado")), data, today)
        assertEquals("Alimentação em outubro: R$ 60 em 1 lançamento(s).", r.facts)
    }

    @Test fun comparePeriods() {
        val r = AssistantTools.run(ToolCall("comparar_periodos", mapOf("categoria" to "Alimentação", "a" to "mes_passado", "b" to "mes_atual")), data, today)
        assertTrue(r.facts, r.facts.contains("Diferença: R$ 18 a mais em novembro"))
    }

    @Test fun periods() {
        assertEquals(LocalDate.of(2026, 10, 1), AssistantTools.period("mes_passado", today).first)
        assertEquals(today.minusDays(29), AssistantTools.period("ultimos_30_dias", today).first)
        assertEquals(YearMonth.of(2026, 9).atEndOfMonth(), AssistantTools.period("2026-09", today).second)
        assertEquals(LocalDate.of(2026, 1, 1), AssistantTools.period("ano", today).first)
    }

    @Test fun purchaseSimulationWithoutValueSaysSo() {
        assertEquals("Faltou o valor da compra para simular.", AssistantTools.run(ToolCall("simular_compra", emptyMap()), data, today).facts)
    }

    @Test fun loopRunsToolsAndStopsAtTheLimit() = runBlocking {
        var asks = 0
        val outcome = AssistantLoop.answer(
            ask = { asks++; "{\"consulta\":\"livre_hoje\"}" },
            run = { ToolResult("livre de hoje", "Livre: R$ 100.") },
        )
        assertEquals(AssistantTools.MAX_CALLS, outcome.consulted.size)
        assertEquals(AssistantTools.MAX_CALLS + 1, asks)
        // Ainda pedindo consulta depois do limite: vale o resultado, não o JSON.
        val reply = AssistantLoop.reply(outcome, "")!!
        assertTrue(reply, reply.startsWith(AssistantLoop.CONSULTED_PREFIX))
        assertTrue(reply.contains("Livre: R$ 100."))
    }

    @Test fun replyWithToolResultPassesTheGuard() = runBlocking {
        var first = true
        val outcome = AssistantLoop.answer(
            ask = { results -> if (first) { first = false; "{\"consulta\":\"buscar_lancamentos\",\"texto\":\"ifood\"}" } else "Foram R$ 78 em ${results.size} consulta." },
            run = { call -> AssistantTools.run(call, data, today) },
        )
        val reply = AssistantLoop.reply(outcome, "Resumo sem valores.")!!
        val (consulted, body) = AssistantLoop.splitConsulted(reply)
        assertEquals("\"ifood\" em novembro", consulted)
        assertEquals("Foram R$ 78 em 1 consulta.", body)
    }

    @Test fun inventedNumberFallsBackToTheResults() = runBlocking {
        var first = true
        val outcome = AssistantLoop.answer(
            ask = { if (first) { first = false; "{\"consulta\":\"buscar_lancamentos\",\"texto\":\"ifood\"}" } else "Foram R$ 9.999 com delivery." },
            run = { call -> AssistantTools.run(call, data, today) },
        )
        val reply = AssistantLoop.reply(outcome, "")!!
        assertTrue(reply, !reply.contains("9.999"))
        assertTrue(reply, reply.contains("somando R$ 78"))
    }

    @Test fun noToolMeansTheReplyAsItCame() = runBlocking {
        val outcome = AssistantLoop.answer(ask = { "Oi! Posso ajudar com seus gastos." }, run = { error("não deveria consultar") })
        assertEquals("Oi! Posso ajudar com seus gastos.", AssistantLoop.reply(outcome, ""))
        assertNull(AssistantLoop.splitConsulted("Oi").first)
    }

    @Test fun unavailableProviderGivesNull() = runBlocking {
        val outcome = AssistantLoop.answer(ask = { null }, run = { error("não deveria consultar") })
        assertNull(AssistantLoop.reply(outcome, ""))
    }

    @Test fun memoryGoesToThePromptCappedAtTen() {
        val facts = (1..12).map { "Fato $it" }
        val request = com.finai.app.domain.AiPromptBuilder.chat("Resumo.", emptyList(), "Oi", memory = facts)
        assertTrue(request.prompt.contains("Fato 10"))
        assertTrue(!request.prompt.contains("Fato 11"))
    }
}
