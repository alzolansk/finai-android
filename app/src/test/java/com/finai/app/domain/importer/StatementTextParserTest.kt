package com.finai.app.domain.importer

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O parser de texto corrido é o caminho de foto e de PDF: em ambos o texto
 * chega como uma lista de linhas vinda do OCR. Estes testes usam exatamente o
 * formato que [com.finai.app.data.importer.OcrEngine.toReadingOrderLines]
 * produz (uma linha visual por item, colunas separadas por espaço).
 */
class StatementTextParserTest {

    private val reference = LocalDate.of(2026, 3, 25)

    /** Fatura de cartão como sai do OCR de um PDF digital. */
    private val faturaPdf = """
        BANCO EXEMPLO  FATURA DO CARTÃO
        Vencimento 10/04/2026
        Limite total  R$ 8.000,00
        Data  Descrição  Valor
        12/03  SUPERMERCADO EXTRA LOJA
               2233 SAO PAULO BR              189,90
        14/03  NETFLIX.COM                     55,90
        15/03  MAGAZINE LUIZA PARC 03/10      129,90
        16/03  UBER * TRIP SAO PAULO           23,45
        18/03  ESTORNO COMPRA DUPLICADA        89,90-
        20/03  AMAZON MKTPLACE USD 12,00    R$ 62,40
        TOTAL DESTA FATURA                  1.234,56
        Página 1 de 2
    """.trimIndent().lines()

    @Test
    fun `extrai os lancamentos e ignora cabecalho e totais`() {
        val result = StatementTextParser.parse(faturaPdf, reference)

        assertEquals(6, result.entries.size)
        assertTrue(result.entries.none { it.description.contains("TOTAL", true) })
        assertTrue(result.entries.none { it.description.contains("Limite", true) })
    }

    @Test
    fun `junta descricao quebrada em duas linhas`() {
        val mercado = StatementTextParser.parse(faturaPdf, reference).entries.first()

        assertEquals(LocalDate.of(2026, 3, 12), mercado.date)
        assertEquals(18_990L, mercado.amountCents)
        assertTrue(mercado.description.contains("SUPERMERCADO EXTRA"))
        assertTrue(mercado.description.contains("2233"))
    }

    @Test
    fun `le parcela e tira do texto da descricao`() {
        val parcelado = StatementTextParser.parse(faturaPdf, reference).entries
            .first { it.description.contains("MAGAZINE", true) }

        assertEquals(Installment(3, 10), parcelado.installment)
        assertEquals(12_990L, parcelado.amountCents)
        assertTrue("parcela não deveria sobrar na descrição", !parcelado.description.contains("03/10"))
    }

    @Test
    fun `estorno vira valor negativo`() {
        val estorno = StatementTextParser.parse(faturaPdf, reference).entries
            .first { it.description.contains("ESTORNO", true) }

        assertEquals(-8_990L, estorno.amountCents)
        assertTrue(estorno.isRefund)
    }

    @Test
    fun `compra internacional usa o valor em reais`() {
        val amazon = StatementTextParser.parse(faturaPdf, reference).entries
            .first { it.description.contains("AMAZON", true) }

        assertEquals(6_240L, amazon.amountCents)
    }

    @Test
    fun `foto torta com ruido de ocr ainda rende lancamentos`() {
        // Como sai do OCR de uma foto: sem cabeçalho de tabela, espaçamento irregular.
        val foto = listOf(
            "FATURA MARCO 2026",
            "05/03 PADARIA DO ZE   18,50",
            "07/03 POSTO IPIRANGA     210,00",
            "09/03  DROGARIA SAO PAULO",
            "   RUA AUGUSTA            47,80",
            "rodapé ilegível ~~~",
        )

        val result = StatementTextParser.parse(foto, reference)

        assertEquals(3, result.entries.size)
        assertEquals(1_850L, result.entries[0].amountCents)
        assertEquals(21_000L, result.entries[1].amountCents)
        assertEquals(4_780L, result.entries[2].amountCents)
    }

    @Test
    fun `linha com data mas sem valor conta como ignorada`() {
        val result = StatementTextParser.parse(listOf("12/03 COMPRA SEM VALOR LEGIVEL"), reference)

        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.ignoredLines)
    }

    @Test
    fun `texto sem nenhuma data nao inventa lancamento`() {
        val result = StatementTextParser.parse(listOf("obrigado por usar o cartao", "fale conosco"), reference)

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `data e valor sao obrigatorios juntos`() {
        val parsed = StatementTextParser.parse(listOf("14/03  NETFLIX.COM  55,90"), reference).entries.single()

        assertNotNull(parsed.date)
        assertNull(parsed.installment)
        assertEquals(5_590L, parsed.amountCents)
    }
}
