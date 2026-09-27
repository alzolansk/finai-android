package com.finai.app.domain.importer

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvoiceMetadataParserTest {
    @Test fun `extracts explicitly declared card dates without inventing fields`() {
        val result = InvoiceMetadataParser.parse(
            listOf("Cartão: Banco FinAI Platinum", "Fechamento: 20/03/2026", "Vencimento: 27/03/2026"),
            LocalDate.of(2026, 3, 1),
        )

        assertEquals("Banco FinAI Platinum", result.reference)
        assertEquals(LocalDate.of(2026, 3, 20), result.closingDate)
        assertEquals(LocalDate.of(2026, 3, 27), result.dueDate)
    }

    @Test fun `leaves absent metadata empty`() {
        val result = InvoiceMetadataParser.parse(listOf("12/03 MERCADO 19,90"), LocalDate.of(2026, 3, 1))

        assertNull(result.reference)
        assertNull(result.closingDate)
        assertNull(result.dueDate)
    }
}
