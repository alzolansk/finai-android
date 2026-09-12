package com.finai.app.domain.importer

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Os formatos que uma fatura/planilha brasileira de verdade joga em cima do parser. */
class BrazilianStatementFormatsTest {

    private val reference = LocalDate.of(2026, 3, 20)

    @Test
    fun `le datas nos formatos comuns`() {
        assertEquals(LocalDate.of(2026, 3, 12), BrazilianStatementFormats.parseDate("12/03", reference))
        assertEquals(LocalDate.of(2025, 12, 5), BrazilianStatementFormats.parseDate("05/12/2025", reference))
        assertEquals(LocalDate.of(2025, 12, 5), BrazilianStatementFormats.parseDate("05/12/25", reference))
        assertEquals(LocalDate.of(2026, 1, 8), BrazilianStatementFormats.parseDate("08-01-2026", reference))
        assertEquals(LocalDate.of(2026, 2, 28), BrazilianStatementFormats.parseDate("28.02.2026", reference))
        assertEquals(LocalDate.of(2026, 3, 1), BrazilianStatementFormats.parseDate("2026-03-01", reference))
        assertEquals(LocalDate.of(2026, 3, 9), BrazilianStatementFormats.parseDate("09 MAR", reference))
        assertEquals(LocalDate.of(2026, 1, 15), BrazilianStatementFormats.parseDate("15 de janeiro", reference))
    }

    @Test
    fun `data sem ano no futuro proximo vira ano anterior`() {
        // Fatura de dezembro aberta em janeiro: 20/12 é do ano passado, não do que vem.
        val janeiro = LocalDate.of(2026, 1, 5)
        assertEquals(LocalDate.of(2025, 12, 20), BrazilianStatementFormats.parseDate("20/12", janeiro))
    }

    @Test
    fun `rejeita data impossivel`() {
        assertNull(BrazilianStatementFormats.parseDate("31/02/2026", reference))
        assertNull(BrazilianStatementFormats.parseDate("sem data aqui", reference))
    }

    @Test
    fun `le valores em real`() {
        assertEquals(123_456L, BrazilianStatementFormats.parseAmountCents("R$ 1.234,56"))
        assertEquals(18_990L, BrazilianStatementFormats.parseAmountCents("189,90"))
        assertEquals(-1_200L, BrazilianStatementFormats.parseAmountCents("-R$ 12,00"))
        assertEquals(-8_990L, BrazilianStatementFormats.parseAmountCents("89,90-"))
        assertEquals(-3_590L, BrazilianStatementFormats.parseAmountCents("(35,90)"))
        assertEquals(-12_300L, BrazilianStatementFormats.parseAmountCents("123,00 CR"))
        assertEquals(123_456L, BrazilianStatementFormats.parseAmountCents("1234.56"))
        assertEquals(123_400L, BrazilianStatementFormats.parseAmountCents("1.234"))
        assertEquals(5_000L, BrazilianStatementFormats.parseAmountCents("50"))
    }

    @Test
    fun `compra internacional usa o valor em real`() {
        val amounts = BrazilianStatementFormats.findAmounts("AMAZON MKTPLACE USD 12,00 R$ 62,40")
        assertEquals(2, amounts.size)
        assertEquals(6_240L, amounts.last().cents)
    }

    @Test
    fun `hifen separador nao vira sinal negativo`() {
        val amounts = BrazilianStatementFormats.findAmounts("MERCADO 189,90 - SAO PAULO")
        assertEquals(18_990L, amounts.first().cents)
    }

    @Test
    fun `le parcelas`() {
        assertEquals(Installment(3, 10), BrazilianStatementFormats.findInstallment("MAGAZINE LUIZA 03/10"))
        assertEquals(Installment(3, 10), BrazilianStatementFormats.findInstallment("MAGALU PARC 3/10"))
        assertEquals(Installment(2, 6), BrazilianStatementFormats.findInstallment("PARCELA 2 DE 6"))
        assertEquals(Installment(1, 12), BrazilianStatementFormats.findInstallment("CELULAR (01/12)"))
        assertNull(BrazilianStatementFormats.findInstallment("PADARIA DO ZE"))
    }
}
