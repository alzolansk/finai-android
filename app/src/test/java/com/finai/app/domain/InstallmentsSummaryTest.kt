package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InstallmentsSummaryTest {
    private val contas = mutableListOf<ContaEntity>()
    private val faturas = mutableListOf<FaturaCartaoEntity>()
    private val transacoes = mutableListOf<TransacaoEntity>()

    /** Monta o que `FinanceRepository.salvarCompraCartao` grava: uma fatura por vencimento, compartilhada. */
    private fun purchase(name: String, totalCents: Long, n: Int, firstDue: LocalDate, card: String = "Nubank") {
        val entry = TransacaoEntity(data = firstDue.minusDays(5).toEpochMillis(), descricao = name, valorCentavos = totalCents,
            categoria = "Compras", contaOrigem = "", recorrente = false, origem = "manual")
        CardPurchase.schedule(entry, CardPayment(card, firstDue, n)).forEach { (due, item) ->
            val invoice = faturas.firstOrNull { it.referencia == card && it.vencimento.toLocalDate() == due } ?: run {
                val conta = ContaEntity(id = contas.size + 1L, nome = "Fatura $card", valorCentavos = 0,
                    vencimento = due.toEpochMillis(), status = "pendente", tipo = "a_pagar", recorrente = false)
                contas += conta
                FaturaCartaoEntity(id = faturas.size + 1L, contaId = conta.id, referencia = card, fechamento = null,
                    vencimento = due.toEpochMillis()).also { faturas += it }
            }
            transacoes += item.copy(id = transacoes.size + 1L, faturaId = invoice.id)
        }
    }

    private fun pay(due: LocalDate) {
        val invoice = faturas.filter { it.vencimento.toLocalDate() == due }.map { it.contaId }
        contas.replaceAll { if (it.id in invoice) it.copy(status = "pago") else it }
    }

    @Test fun groupsOpenInstallmentsByInvoiceMonthWithoutCountingTwice() {
        purchase("Geladeira", 289_900, 10, LocalDate.of(2026, 5, 10))
        purchase("Tênis", 63_990, 3, LocalDate.of(2026, 10, 10))
        (5..9).forEach { pay(LocalDate.of(2026, it, 10)) }

        val s = InstallmentsSummary.of(transacoes, faturas, contas)
        assertEquals(2, s.active.size)
        // Geladeira: 5 de 10 pagas; Tênis: nenhuma.
        val geladeira = s.active.first { it.name == "Geladeira" }
        assertEquals(5, geladeira.paidCount)
        assertEquals(6, geladeira.nextNumber)
        assertEquals(144_950L, geladeira.openCents)
        assertEquals(YearMonth.of(2027, 2), geladeira.lastDue)
        assertEquals(s.active.sumOf { it.openCents }, s.openCents)
        assertEquals(s.openByMonth.sumOf { it.cents }, s.openCents)
        assertEquals(8, s.openCount)
        assertEquals(YearMonth.of(2027, 3), s.freeFrom)
        assertEquals(28_990L + 21_330L, s.openByMonth.first { it.month == YearMonth.of(2026, 10) }.cents)
        // Ordenado pelo que falta pagar.
        assertEquals("Geladeira", s.active.first().name)
    }

    @Test fun agendaMonthCountsEveryInstallmentDueThatMonthPaidOrNot() {
        purchase("Passagem", 93_900, 6, LocalDate.of(2026, 10, 15))
        purchase("Tênis", 63_990, 3, LocalDate.of(2026, 10, 15))
        pay(LocalDate.of(2026, 10, 15))
        val s = InstallmentsSummary.of(transacoes, faturas, contas)
        assertEquals(15_650L + 21_330L, s.monthCents(YearMonth.of(2026, 10)))
        assertEquals(2, s.monthPurchases(YearMonth.of(2026, 10)))
        assertEquals(0L, s.monthCents(YearMonth.of(2027, 6)))
        // Outubro pago: nada em aberto nele, o gráfico começa em novembro.
        assertEquals(YearMonth.of(2026, 11), s.openByMonth.first().month)
        assertEquals(YearMonth.of(2026, 11), InstallmentsSummary.defaultMonth(s.openByMonth, LocalDate.of(2026, 10, 20)))
    }

    @Test fun fullyPaidPurchaseIsNotActiveAndSingleChargesAreIgnored() {
        purchase("Fone", 30_000, 2, LocalDate.of(2026, 8, 10))
        purchase("Livro", 5_000, 1, LocalDate.of(2026, 8, 10))
        pay(LocalDate.of(2026, 8, 10)); pay(LocalDate.of(2026, 9, 10))
        val s = InstallmentsSummary.of(transacoes, faturas, contas)
        assertTrue(s.active.isEmpty())
        assertEquals(0L, s.openCents)
        assertNull(s.freeFrom)
        assertEquals(15_000L, s.monthCents(YearMonth.of(2026, 8)))
    }

    /** Item de fatura importada, como `ImportViewModel` grava: "(01/03)" na descrição, sem `compraId`. */
    private fun imported(desc: String, cents: Long, due: LocalDate, card: String = "Nubank", paid: Boolean = false) {
        val invoice = faturas.firstOrNull { it.referencia == card && it.vencimento.toLocalDate() == due } ?: run {
            val conta = ContaEntity(id = contas.size + 1L, nome = "Fatura $card", valorCentavos = 0,
                vencimento = due.toEpochMillis(), status = if (paid) "pago" else "pendente", tipo = "a_pagar", recorrente = false)
            contas += conta
            FaturaCartaoEntity(id = faturas.size + 1L, contaId = conta.id, referencia = card, fechamento = null,
                vencimento = due.toEpochMillis()).also { faturas += it }
        }
        transacoes += TransacaoEntity(id = transacoes.size + 1L, data = due.minusDays(20).toEpochMillis(), descricao = desc,
            valorCentavos = cents, categoria = "Outros", contaOrigem = "", recorrente = true, origem = "importado", faturaId = invoice.id)
    }

    @Test fun importedInvoiceInstallmentsAreRecognizedAndTheRestEstimated() {
        val oct = LocalDate.of(2026, 10, 15)
        imported("Mercado*Mercadolivre (01/03)", 9_590, oct)
        imported("Jim.Com* Joao Vitor A (02/02)", 21_139, oct)
        imported("Pix no Crédito - Uber", 2_571, oct)

        val s = InstallmentsSummary.of(transacoes, faturas, contas)
        assertEquals(2, s.active.size)
        val ml = s.active.first { it.name == "Mercado*Mercadolivre" }
        assertEquals(3, ml.openCount)
        assertEquals(28_770L, ml.openCents)
        assertTrue(ml.estimated)
        assertEquals(YearMonth.of(2026, 12), ml.lastDue)
        // Parcela 1 do Jim estava numa fatura que não foi importada: conta como paga e fica fora dos meses.
        val jim = s.active.first { it.name == "Jim.Com* Joao Vitor A" }
        assertEquals(setOf(1), jim.paidNumbers)
        assertEquals(2, jim.nextNumber)
        assertFalse(jim.estimated)
        assertEquals(9_590L + 21_139L, s.monthCents(YearMonth.of(2026, 10)))
        assertEquals(0L, s.monthCents(YearMonth.of(2026, 9)))
        assertEquals(listOf(9_590L + 21_139L, 9_590L, 9_590L), s.openByMonth.map { it.cents })
        assertEquals(listOf(0, 1, 1), s.openByMonth.map { it.estimatedCount })
        assertEquals(YearMonth.of(2027, 1), s.freeFrom)
    }

    @Test fun importingTheNextInvoiceReplacesTheEstimateInsteadOfAddingToIt() {
        imported("Mercado*Mercadolivre (01/03)", 9_590, LocalDate.of(2026, 10, 15), paid = true)
        imported("MERCADO*MERCADOLIVRE (02/03)", 9_590, LocalDate.of(2026, 11, 15))

        val s = InstallmentsSummary.of(transacoes, faturas, contas)
        assertEquals(1, s.active.size)
        val ml = s.active.single()
        assertEquals(setOf(1), ml.paidNumbers)
        assertEquals(2, ml.openCount)
        assertEquals(19_180L, s.openCents)
        assertEquals(9_590L, s.monthCents(YearMonth.of(2026, 11)))
        assertEquals(listOf(0, 1), s.openByMonth.map { it.estimatedCount })
    }

    @Test fun monthsWithoutOpenInstallmentsBetweenTwoPurchasesStayInTheChart() {
        purchase("A", 20_000, 2, LocalDate.of(2026, 10, 10))
        purchase("B", 20_000, 2, LocalDate.of(2027, 1, 10))
        val months = InstallmentsSummary.of(transacoes, faturas, contas).openByMonth.map { it.month to it.cents }
        assertEquals(listOf(YearMonth.of(2026, 10) to 10_000L, YearMonth.of(2026, 11) to 10_000L,
            YearMonth.of(2026, 12) to 0L, YearMonth.of(2027, 1) to 10_000L, YearMonth.of(2027, 2) to 10_000L), months)
    }
}
