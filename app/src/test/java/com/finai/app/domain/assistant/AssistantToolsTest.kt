package com.finai.app.domain.assistant

import com.finai.app.domain.assistant.T.d
import com.finai.app.domain.assistant.T.snap
import com.finai.app.domain.assistant.T.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Consultas locais (plano §5.3): argumentos, períodos, cartões montados pelo app e linha "consultei". */
class AssistantToolsTest {
    private val today = T.TODAY
    private val s = snap(
        transacoes = listOf(
            tx(1, today.minusDays(1), 4_590, "iFood", "Alimentação"),
            tx(2, today.minusDays(2), 3_210, "IFOOD", "Alimentação"),
            tx(3, d(2026, 10, 12), 6_000, "iFood", "Alimentação"),
            tx(4, today.minusDays(3), 2_300, "Uber", "Transporte"),
        ),
    )

    @Test fun parsesTheJsonLine() {
        val call = AssistantTools.parseCall("Vou consultar.\n{\"consulta\":\"buscar_lancamentos\",\"texto\":\"ifood\",\"periodo\":\"mes_atual\"}")!!
        assertEquals("buscar_lancamentos", call.name)
        assertEquals("ifood", call.args["texto"])
        assertNull(AssistantTools.parseCall("Você gastou bastante com delivery."))
        assertNull("fora do catálogo", AssistantTools.parseCall("{\"consulta\":\"apagar_tudo\"}"))
    }

    @Test fun searchFindsAccentAndCaseInsensitiveAndTheCardCarriesTheTotal() {
        val r = AssistantTools.run(ToolCall("buscar_lancamentos", mapOf("texto" to "ifood", "periodo" to "mes_atual")), s)
        assertTrue(r.facts, r.facts.contains("R\$ 78"))
        assertNotNull(r.answer)
        assertTrue(r.label, r.label.contains("ifood"))
    }

    @Test fun categoryAnalysisAndComparison() {
        val spent = AssistantTools.run(ToolCall("analisar_gastos", mapOf("categoria" to "Alimentação", "periodo" to "mes_passado")), s)
        assertTrue(spent.facts, spent.facts.contains("R\$ 60"))
        val cmp = AssistantTools.run(ToolCall("comparar_periodos", mapOf("categoria" to "Alimentação", "a" to "mes_passado", "b" to "mes_atual")), s)
        assertTrue(cmp.facts, cmp.facts.contains("R\$ 78") && cmp.facts.contains("R\$ 60"))
    }

    @Test fun periods() {
        assertEquals(LocalDate.of(2026, 10, 1), AssistantTools.period("mes_passado", today).first)
        assertEquals(today.minusDays(29), AssistantTools.period("ultimos_30_dias", today).first)
        assertEquals(YearMonth.of(2026, 9).atEndOfMonth(), AssistantTools.period("2026-09", today).second)
        assertEquals(LocalDate.of(2026, 1, 1), AssistantTools.period("ano", today).first)
    }

    @Test fun readToolsNeverNeedTheModelToConcludeFits() {
        val r = AssistantTools.run(ToolCall("simular_cenario", mapOf("tipo" to "gasto", "valor" to "1200.00")), s)
        assertNotNull(r.answer)
        // O veredito vem do cartão calculado pelo app.
        assertTrue(r.answer!!.metrics.isNotEmpty() || r.answer!!.headline.isNotBlank())
    }

    @Test fun consultedLineRoundTrips() {
        val text = AssistantLoop.withConsulted(listOf("\"ifood\" em novembro", "situação"), "Resposta.")
        val (consulted, body) = AssistantLoop.splitConsulted(text)
        assertEquals("\"ifood\" em novembro · situação", consulted)
        assertEquals("Resposta.", body)
        assertNull(AssistantLoop.splitConsulted("Oi").first)
        assertEquals("Oi", AssistantLoop.withConsulted(emptyList(), "Oi"))
    }

    @Test fun memoryGoesToThePromptCappedAtTen() {
        val facts = (1..12).map { "Fato $it" }
        val request = com.finai.app.domain.AiPromptBuilder.chat("Resumo.", emptyList(), "Oi", memory = facts)
        assertTrue(request.prompt.contains("Fato 10"))
        assertTrue(!request.prompt.contains("Fato 11"))
    }
}
