package com.finai.app.domain

import com.finai.app.domain.AiReplyFormat.Block
import com.finai.app.domain.AiReplyFormat.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReplyFormatTest {

    private fun text(block: Block) = block.spans.joinToString("") { it.text }

    @Test
    fun `negrito vira enfase e nenhum asterisco sobra na tela`() {
        val reply = AiReplyFormat.parse("Você tem **R$ 337 de juros** por mês e *isso* pesa.")
        val spans = reply.blocks.single().spans
        assertEquals("Você tem R$ 337 de juros por mês e isso pesa.", spans.joinToString("") { it.text })
        assertTrue(spans.filter { it.strong }.joinToString("") { it.text } == "R$ 337 de juros")
        assertFalse(spans.any { '*' in it.text })
    }

    @Test
    fun `valores em reais e percentuais sao marcados, negativos a parte`() {
        val spans = AiReplyFormat.parse("Folga de R$ 1.234,56, capacidade de R$ -4.011 e juros de 2,5% ao mês.").blocks.single().spans
        assertEquals(Kind.Money, spans.single { it.text == "R$ 1.234,56" }.kind)
        assertEquals(Kind.NegativeMoney, spans.single { it.text == "R$ -4.011" }.kind)
        assertEquals(Kind.Percent, spans.single { it.text == "2,5%" }.kind)
    }

    @Test
    fun `hifen separador nao torna o valor negativo`() {
        val spans = AiReplyFormat.parse("Aluguel - R$ 1.200").blocks.single().spans
        assertEquals(Kind.Money, spans.single { it.text.contains("1.200") }.kind)
    }

    @Test
    fun `titulos somem e marcadores viram itens de lista`() {
        val reply = AiReplyFormat.parse("## Resumo\nPrimeiro parágrafo.\n\n* quitar o cartão\n- cancelar a assinatura\n1. guardar R$ 200")
        assertEquals(4, reply.blocks.size)
        assertEquals("Resumo\nPrimeiro parágrafo.", text(reply.blocks[0]))
        assertTrue(reply.blocks.drop(1).all { it is Block.Bullet })
        assertEquals("quitar o cartão", text(reply.blocks[1]))
        assertEquals("guardar R$ 200", text(reply.blocks[3]))
    }

    @Test
    fun `destaques sao extraidos e retirados do texto`() {
        val reply = AiReplyFormat.parse("{{Juros por mês|R$ 337}}\n{{Folga|-R$ 120}}\nComece pelo cartão.")
        assertEquals(listOf("Juros por mês", "Folga"), reply.highlights.map { it.label })
        assertFalse(reply.highlights[0].negative)
        assertTrue(reply.highlights[1].negative)
        assertEquals("Comece pelo cartão.", text(reply.blocks.single()))
    }

    @Test
    fun `texto sem marcacao passa intacto`() {
        val raw = "Oi! Posso ajudar com gastos, dívidas ou objetivos."
        assertEquals(raw, AiReplyFormat.plain(raw))
    }

    @Test
    fun `plain remove marcacao para notificacao`() {
        assertEquals("Juros: R$ 337\nPague o **cartão** hoje.".replace("**", ""), AiReplyFormat.plain("{{Juros|R$ 337}} Pague o **cartão** hoje."))
    }
}
