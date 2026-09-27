package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class InvoiceAndTimelineTest {
    private fun tx(desc: String, cents: Long, date: LocalDate = LocalDate.of(2026, 9, 13), cat: String = "Transporte",
                   tipo: String = "Gasto", extra: Boolean = false, faturaId: Long? = 1, id: Long = 0) = TransacaoEntity(
        id = id, data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = cat,
        contaOrigem = "Nubank", recorrente = false, origem = "importado", tipo = tipo, faturaId = faturaId, extra = extra)

    @Test fun timelineTakesExtraIncomeButNotRegularSalary() {
        val contas = listOf(ContaEntity(nome = "Restituição IR", valorCentavos = 90_000, vencimento = LocalDate.of(2026, 7, 1).toEpochMillis(),
            status = "pendente", tipo = "a_receber", recorrente = false))
        val transacoes = listOf(
            tx("13º", 300_000, LocalDate.of(2026, 12, 20), "Outros", "Receita", extra = true, faturaId = null),
            tx("Salario", 255_000, LocalDate.of(2026, 9, 26), "Outros", "Receita", faturaId = null),
            tx("Bônus 2025", 100_000, LocalDate.of(2025, 3, 1), "Outros", "Receita", extra = true, faturaId = null),
        )
        val extras = ExtraIncomeTimeline.forYear(contas, transacoes, 2026)
        assertEquals(listOf("Restituição IR", "13º"), extras.map { it.label })
        assertEquals(390_000L, extras.sumOf { it.cents })
    }

    @Test fun installmentMarkersAreParsed() {
        assertEquals(1 to 2, InvoiceSummary.installmentOf("Jim.Com* Joao Vitor A (01/02)"))
        assertEquals(3 to 10, InvoiceSummary.installmentOf("LOJA X PARC 3/10"))
        assertEquals(2 to 6, InvoiceSummary.installmentOf("Magalu parcela 2 de 6"))
        assertNull(InvoiceSummary.installmentOf("Pix no Crédito - 65.033.041 EDERSON"))
        assertEquals("Hna*Oboticario", InvoiceSummary.cleanName("Hna*Oboticario (02/02)"))
    }

    @Test fun summaryGroupsCategoriesMerchantsRefundsAndDays() {
        val items = listOf(
            tx("Dl*Uberrides", 1_800, LocalDate.of(2026, 9, 26), id = 1),
            tx("Dl*Uberrides", 2_300, LocalDate.of(2026, 9, 16), id = 2),
            tx("DL*UBERRIDES", 1_000, LocalDate.of(2026, 9, 16), id = 3),
            tx("Estorno de pagamento", -2_200, LocalDate.of(2026, 9, 23), cat = "Outros", id = 4),
            tx("Jim.Com* Joao (01/02)", 21_100, LocalDate.of(2026, 9, 6), cat = "Compras", id = 5),
            tx("Hna*Oboticario (02/02)", 5_600, LocalDate.of(2026, 9, 5), cat = "Compras", id = 6),
        )
        val s = InvoiceSummary.of(items)
        assertEquals(29_600L, s.totalCents)
        assertEquals(31_800L, s.comprasCents)
        assertEquals(2_200L, s.estornosCents)
        assertEquals(1, s.estornosCount)
        assertEquals(listOf("Compras", "Transporte"), s.categorias.map { it.categoria })
        val uber = s.merchants.first { it.name.equals("Dl*Uberrides", ignoreCase = true) }
        assertEquals(3, uber.count); assertEquals(5_100L, uber.cents)
        assertEquals(listOf(1, 0), s.parcelas.map { it.restantes })
        assertEquals(LocalDate.of(2026, 9, 26), s.porDia.first().first)
        assertEquals(2, s.porDia.first { it.first == LocalDate.of(2026, 9, 16) }.second.size)
    }
}
