package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class EntryOptionsTest {
    private val today = LocalDate.of(2026, 10, 11)

    private fun tx(id: Long, tipo: String = "Gasto", categoria: String = "Alimentação", conta: String = "Nubank",
                   valor: Long = 4300, data: LocalDate = today, objetivoId: Long? = null, faturaId: Long? = null, compraId: String? = null) =
        TransacaoEntity(id = id, data = data.toEpochMillis(), descricao = "x", valorCentavos = valor, categoria = categoria,
            contaOrigem = conta, recorrente = false, origem = "manual", tipo = tipo, objetivoId = objetivoId,
            faturaId = faturaId, compraId = compraId)

    @Test fun categoriesFollowRecentUseAndKeepDefaultOrderForUnused() {
        val list = EntryOptions.categoriesByRecentUse(listOf(
            tx(1, categoria = "Moradia"), tx(5, categoria = "Compras"), tx(3, categoria = "Moradia"),
            tx(9, tipo = "Receita", categoria = "Lazer"),
        ))
        assertEquals(listOf("Compras", "Moradia"), list.take(2))
        // Receita não conta como uso; o resto fica na ordem padrão.
        assertEquals(TransactionEntry.categories.filter { it !in setOf("Compras", "Moradia") }, list.drop(2))
        assertEquals(9, list.size)
    }

    @Test fun accountsAreRecentFirstWithoutCardsAndWithWalletFallback() {
        val accounts = EntryOptions.recentAccounts(
            listOf(tx(1, conta = "Itaú"), tx(2, conta = "Nubank", compraId = "c"), tx(3, conta = "Inter")),
            cards = listOf("Nubank"),
        )
        assertEquals(listOf("Inter", "Itaú", "Carteira"), accounts)
        assertEquals(listOf("Carteira"), EntryOptions.recentAccounts(emptyList(), emptyList()))
    }

    @Test fun installmentsSplitInWholeCentsWithRemainderFirst() {
        for (count in EntryOptions.MIN_INSTALLMENTS..EntryOptions.MAX_INSTALLMENTS) {
            for (total in listOf(289_900L, 10_000L, 99_99L, 1_000_01L)) {
                val p = EntryOptions.Installments(count, total)
                assertEquals(total, p.amounts.sum())
                assertTrue(p.amounts.zipWithNext().all { (a, b) -> a >= b })
            }
        }
        val ten = EntryOptions.Installments(10, 289_900)
        assertEquals("10× R$ 289,90", ten.headline)
        assertNull(ten.remainderNote)
        assertEquals("10 parcelas de R$ 289,90", ten.spoken)
        val three = EntryOptions.Installments(3, 10_000)
        assertEquals(listOf(3334L, 3333L, 3333L), three.amounts)
        assertEquals("1ª com R$ 33,34", three.remainderNote)
    }

    private fun fatura(id: Long, due: LocalDate, close: LocalDate?, contaId: Long = id) =
        FaturaCartaoEntity(id = id, contaId = contaId, referencia = "Nubank", fechamento = close?.toEpochMillis(), vencimento = due.toEpochMillis())
    private fun conta(id: Long, due: LocalDate, status: String = "pendente") =
        ContaEntity(id = id, nome = "Fatura Nubank", valorCentavos = 100, vencimento = due.toEpochMillis(), status = status, tipo = "a_pagar", recorrente = false)

    @Test fun openInvoiceWithKnownClosingIsTheSafeDueDate() {
        val nov = LocalDate.of(2026, 11, 10)
        val due = EntryOptions.resolveCardDue("nubank ", today, listOf(fatura(1, nov, LocalDate.of(2026, 11, 3))), listOf(conta(1, nov)))
        assertEquals(EntryOptions.CardDue(nov, true), due)
    }

    @Test fun purchaseAfterClosingIsOnlyASuggestionForTheNextMonth() {
        val oct = LocalDate.of(2026, 10, 10)
        val due = EntryOptions.resolveCardDue("Nubank", today, listOf(fatura(1, oct, LocalDate.of(2026, 10, 3))), listOf(conta(1, oct)))
        assertEquals(EntryOptions.CardDue(LocalDate.of(2026, 11, 10), false), due)
    }

    @Test fun withoutClosingTheDueDayIsSuggestedAndPaidInvoicesAreSkipped() {
        val oct20 = LocalDate.of(2026, 10, 20)
        val suggestion = EntryOptions.resolveCardDue("Nubank", today, listOf(fatura(1, LocalDate.of(2026, 9, 20), null)), emptyList())
        assertEquals(EntryOptions.CardDue(oct20, false), suggestion)
        val paid = EntryOptions.resolveCardDue("Nubank", today, listOf(fatura(1, oct20, null)), listOf(conta(1, oct20, "pago")))
        assertEquals(LocalDate.of(2026, 11, 20), paid.date)
        assertFalse(paid.confident)
    }

    @Test fun unknownCardHasNoDueDate() {
        assertEquals(EntryOptions.CardDue(null, false), EntryOptions.resolveCardDue("Inter", today, listOf(fatura(1, today, today)), emptyList()))
        assertEquals(EntryOptions.CardDue(null, false), EntryOptions.resolveCardDue(" ", today, emptyList(), emptyList()))
    }

    @Test fun invoiceLinesMatchThePlaybook() {
        val nov = LocalDate.of(2026, 11, 10)
        assertEquals("Entra na fatura Nubank de nov · vence 10/11", EntryOptions.invoiceLine("Nubank", nov, true, 1))
        assertEquals("1ª parcela na fatura Nubank de nov (vence 10/11) · última em ago/27", EntryOptions.invoiceLine("Nubank", nov, true, 10))
        assertTrue(EntryOptions.invoiceLine("Nubank", nov, false, 10).startsWith("Confirme"))
    }

    @Test fun incomeKindsAreExclusive() {
        val base = tx(1, tipo = "Receita")
        val salary = IncomeKind.Salario.applyTo(base)
        assertTrue(salary.rendaPrincipal && salary.recorrente && !salary.extra)
        val recurring = IncomeKind.Recorrente.applyTo(salary)
        assertTrue(!recurring.rendaPrincipal && recurring.recorrente && !recurring.extra)
        val single = IncomeKind.Avulsa.applyTo(recurring)
        assertTrue(!single.rendaPrincipal && !single.recorrente && !single.extra)
        val extra = IncomeKind.Extra.applyTo(salary)
        assertTrue(!extra.rendaPrincipal && !extra.recorrente && extra.extra)
        IncomeKind.values().forEach { assertEquals(it, IncomeKind.of(it.applyTo(base))) }
        assertTrue(PayCycle.isMainIncome(salary))
    }

    private fun goal(id: Long, tipo: String, alvo: Long, guardado: Long = 0, concluido: Long? = null, prioridade: Int = 1) =
        ObjetivoEntity(id = id, tipo = tipo, nome = "G$id", valorAlvoCentavos = alvo, valorGuardadoCentavos = guardado,
            prazo = today.plusYears(1).toEpochMillis(), prioridade = prioridade, concluidoEm = concluido)

    @Test fun reservesAndGoalsAreSeparatedAndOneOptionIsPreselected() {
        val list = listOf(
            goal(1, "Reserva", 0), goal(2, "Reserva", 20_000_00, prioridade = 0), goal(3, "Viagem", 10_000_00),
            goal(4, "Reserva", 1000, 1000, concluido = 1), goal(5, "Compra", 0),
        )
        assertEquals(listOf(2L, 1L, 5L), EntryOptions.reserves(list).map { it.id })
        assertEquals(listOf(3L), EntryOptions.goals(list).map { it.id })
        assertEquals(3L, EntryOptions.autoSelected(EntryOptions.goals(list)))
        assertNull(EntryOptions.autoSelected(EntryOptions.reserves(list)))
        assertNull(EntryOptions.autoSelected(emptyList()))
    }

    @Test fun goalMoveShowsPercentOnlyWithTarget() {
        val target = EntryOptions.goalMove(goal(1, "Reserva", 20_000_00, 12_400_00), 500_00, 100_00)
        assertEquals("62% → 64%", target.changeLabel)
        assertEquals("R$ 12.900,00 de R$ 20.000,00", target.detailLabel)
        assertEquals("Faltam R$ 7.100,00", target.afterNote)
        assertNull(target.coverageAfterDays)
        assertEquals("G1 agora com R$ 12.900", target.announcement)

        val open = EntryOptions.goalMove(goal(2, "Reserva", 0, 1_000_00), 500_00, 50_00)
        assertFalse(open.hasTarget)
        assertEquals("R$ 1.000,00 → R$ 1.500,00", open.changeLabel)
        assertEquals("Cobre 20 → 30 dias de gastos", open.detailLabel)
        assertEquals(0, open.missingCents.toInt().let { if (open.hasTarget) it else 0 })
        assertFalse(open.afterNote.contains("Faltam"))
        assertFalse(open.changeLabel.contains("%"))
    }

    @Test fun amountIsReadInWords() {
        assertEquals("Dois mil oitocentos e noventa e nove reais", EntryOptions.spokenAmount(289_900))
        assertEquals("Quarenta e três reais e cinquenta centavos", EntryOptions.spokenAmount(43_50))
        assertEquals("Um real", EntryOptions.spokenAmount(100))
        assertEquals("Cinco centavos", EntryOptions.spokenAmount(5))
        assertEquals("Mil e cem reais", EntryOptions.spokenAmount(1_100_00))
        assertEquals("Dois mil e cinco reais", EntryOptions.spokenAmount(2_005_00))
        assertEquals("Cem reais", EntryOptions.spokenAmount(100_00))
        assertEquals("Cento e um reais", EntryOptions.spokenAmount(101_00))
        assertEquals("Um milhão de reais", EntryOptions.spokenAmount(1_000_000_00))
        assertEquals("Zero reais", EntryOptions.spokenAmount(0))
    }

    @Test fun summaryLineHasValueCategoryDateAccount() {
        assertEquals("R$ 43,00 · Alimentação · hoje · Nubank", EntryOptions.summaryLine(4300, "Alimentação", today, "Nubank", today))
        assertEquals("R$ 43,00 · Alimentação · 09/10", EntryOptions.summaryLine(4300, "Alimentação", today.minusDays(2), "", today))
    }

    // ── aporte no fluxo de caixa: sai uma vez, nunca é gasto ─────────

    @Test fun contributionLeavesFreeCashOnceAndIsNotAnExpense() {
        val salary = tx(1, tipo = "Receita", categoria = "Salário", valor = 5_000_00, data = today.withDayOfMonth(1))
        val aporte = tx(2, tipo = "Transferencia", categoria = "Transferência", valor = 500_00, objetivoId = 9)
        val between = tx(3, tipo = "Transferencia", categoria = "Transferência", valor = 300_00)
        val flow = MonthCashFlow.of(emptyList(), listOf(salary, aporte, between), emptyList(), YearMonth.from(today), today)
        assertEquals(4_500_00L, flow.saldoCents)
        assertEquals(0L, flow.toPayCents)
        assertEquals(500_00L, flow.aportesCents)
        assertEquals(-1, aporte.freeCashSign)
        assertEquals(0, between.freeCashSign)
        assertEquals(1, salary.freeCashSign)
        // Fora do orçamento e da média de gasto da reserva.
        val budgets = BudgetCalculator.forCategories(listOf(com.finai.app.data.local.entity.OrcamentoCategoriaEntity("Transferência", 100, "2026-10")),
            listOf(aporte), today)
        assertEquals(0L, budgets.single().spentCents)
        assertNull(GoalFlow.averageDailySpendingCents(emptyList(), listOf(aporte), emptyList(), today))
        // O livre do mês desconta o aporte uma vez.
        assertEquals(4_500_00L, SafeToSpendCalculator.calculate(emptyList(), listOf(salary, aporte, between), 0, today).slackThisMonthCents)
    }
}
