package com.finai.app.domain.assistant

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.AdvanceRule
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.IncomePlanner
import com.finai.app.domain.IncomeSplit
import com.finai.app.domain.occurrenceFor
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.assistant.T.d
import com.finai.app.domain.assistant.T.plan
import com.finai.app.domain.assistant.T.snap
import com.finai.app.domain.assistant.T.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Ações do assistente (plano §5.10 e §9): alvo, alcance e efeito de cada ferramenta de escrita, sem gravar nada. */
class AssistantActionsTest {
    private val today = T.TODAY
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)

    private fun txChanges(p: ActionPlan) = p.changes.filterIsInstance<RowChange.Tx>()

    // ── salário recebido ────────────────────────────────────────────────

    @Test fun theSalaryPhraseBecomesAReceiveIncomeRequest() {
        val s = snap(listOf(T.salary(start = d(2026, 9, 28))))
        val r = T.parse("Meu salário caiu adiantado este mês, recebi hoje", s)!!
        assertEquals(ActionPlanner.RECEIVE_INCOME, r.name)
        assertEquals("hoje", r.args["data"])
        assertEquals("sim", r.args["antecipado"])
        assertNull("adiantado é a data, não a parte", r.args["parte"])
    }

    @Test fun salaryInTheSameMonthMovesOnlyThatOccurrence() {
        val row = T.salary(start = d(2026, 9, 28))
        val p = plan(ActionPlanner.RECEIVE_INCOME, snap(listOf(row)), args = arrayOf("data" to "hoje"))
        assertEquals(PlanState.Ready, p.state)
        val change = txChanges(p).single()
        assertEquals(row, change.before)
        val after = change.after!!
        assertEquals(today, after.occurrenceFor(nov)!!.data.toLocalDate())
        assertEquals("o valor é mantido", row.valorCentavos, after.occurrenceFor(nov)!!.valorCentavos)
        // Os outros meses continuam como eram: nada de regenerar a série.
        assertEquals(row.occurrenceFor(YearMonth.of(2026, 10))!!.data, after.occurrenceFor(YearMonth.of(2026, 10))!!.data)
        assertEquals(d(2026, 12, 28), after.occurrenceFor(dec)!!.data.toLocalDate())
        assertEquals(d(2026, 11, 14), p.agendaDate)
    }

    @Test fun salaryAnticipatedAcrossTheMonthSuppressesTheOriginalAndKeepsTheCompetence() {
        val row = T.salary(start = d(2026, 9, 5))
        val now = d(2026, 11, 27)
        val p = plan(ActionPlanner.RECEIVE_INCOME, snap(listOf(row), today = now), args = arrayOf("data" to "hoje"))
        assertEquals(PlanState.Ready, p.state)
        val changes = txChanges(p)
        assertEquals(2, changes.size)
        // A ocorrência de dezembro some só naquele mês; janeiro segue a regra.
        val suppressed = changes[0].after!!
        assertNull(suppressed.occurrenceFor(dec))
        assertNotNull(suppressed.occurrenceFor(YearMonth.of(2027, 1)))
        // O recebimento vira avulso hoje, com a competência de dezembro preservada.
        val loose = changes[1].after!!
        assertNull(changes[1].before)
        assertEquals(now, loose.data.toLocalDate())
        assertFalse(loose.recorrente)
        assertEquals(row.valorCentavos, loose.valorCentavos)
        assertEquals(1, loose.competenciaDeslocamento)
        assertEquals(ActionPlanner.ORIGIN, loose.origem)
        assertTrue(p.notes.any { it.contains("competência") })
    }

    @Test fun anAlreadyReceivedSalaryIsNotRecordedTwice() {
        val row = T.salary(start = d(2026, 9, 28))
        val first = plan(ActionPlanner.RECEIVE_INCOME, snap(listOf(row)), args = arrayOf("data" to "hoje"))
        val moved = txChanges(first).single().after!!
        val again = plan(ActionPlanner.RECEIVE_INCOME, snap(listOf(moved)), args = arrayOf("data" to "hoje"))
        assertEquals(PlanState.Already, again.state)
        assertTrue(again.changes.isEmpty())
    }

    @Test fun splitSalaryAskedAboutAdvanceVersusBalanceNeverGuessed() {
        val rows = IncomePlanner.generate(T.salary(start = d(2026, 10, 1)), 600_000, IncomeSplit(AdvanceRule.Percent(40)), YearMonth.of(2026, 10), 5, "g1")
            .mapIndexed { i, r -> r.copy(id = i + 1L) }
        val s = snap(rows)
        // "Caiu adiantado" sem dizer a parte: pergunta, porque há mais de uma plausível.
        val ask = plan(ActionPlanner.RECEIVE_INCOME, s, args = arrayOf("data" to "hoje", "antecipado" to "sim"))
        assertEquals(PlanState.Clarify, ask.state)
        assertTrue(ask.changes.isEmpty())
        assertTrue(ask.options.any { it.args["parte"] == "adiantamento" })
        assertTrue(ask.options.any { it.args["parte"] == "integral" })
    }

    @Test fun splitSalaryWithThePartNamedMovesOnlyThatPart() {
        val rows = IncomePlanner.generate(T.salary(start = d(2026, 10, 1)), 600_000, IncomeSplit(AdvanceRule.Percent(40)), YearMonth.of(2026, 10), 5, "g1")
            .mapIndexed { i, r -> r.copy(id = i + 1L) }
        val advance = rows.first { it.rendaParte == "adiantamento" }
        val p = plan(ActionPlanner.RECEIVE_INCOME, snap(rows), args = arrayOf("data" to "hoje", "parte" to "adiantamento"))
        assertEquals(PlanState.Ready, p.state)
        val change = txChanges(p).single()
        assertEquals("o saldo e as outras ocorrências não mudam", advance.id, change.before!!.id)
        assertEquals(today, change.after!!.occurrenceFor(nov)!!.data.toLocalDate())
    }

    @Test fun twoIncomesAskWhichOneArrived() {
        val a = T.salary(id = 1, start = d(2026, 9, 28), name = "Salário Alfa")
        val b = T.salary(id = 2, start = d(2026, 9, 29), cents = 300_000, name = "Salário Beta").copy(rendaPrincipal = false)
        val s = snap(listOf(a, b))
        val p = plan(ActionPlanner.RECEIVE_INCOME, s, args = arrayOf("data" to "hoje"))
        assertEquals(PlanState.Clarify, p.state)
        assertTrue(p.changes.isEmpty())
        assertEquals(2, p.options.size)
    }

    @Test fun withoutAnyIncomeItAsksForTheValueAndThenCreatesALooseReceipt() {
        val s = snap()
        val ask = plan(ActionPlanner.RECEIVE_INCOME, s, args = arrayOf("data" to "hoje"))
        assertEquals(PlanState.Clarify, ask.state)
        assertEquals("valor", ask.awaiting)
        val ok = plan(ActionPlanner.RECEIVE_INCOME, s, args = arrayOf("data" to "hoje", "valor" to "3000.00"))
        assertEquals(PlanState.Ready, ok.state)
        val row = txChanges(ok).single().after!!
        assertEquals(300_000, row.valorCentavos)
        assertFalse("não cria série recorrente", row.recorrente)
    }

    @Test fun partialReceiptLeavesTheRestPending() {
        val row = T.salary(start = d(2026, 9, 28))
        val p = plan(ActionPlanner.RECEIVE_INCOME, snap(listOf(row)), args = arrayOf("data" to "hoje", "valor" to "2000.00", "parcial" to "sim"))
        assertEquals(PlanState.Ready, p.state)
        val changes = txChanges(p)
        assertEquals(2, changes.size)
        assertEquals(200_000, changes[1].after!!.valorCentavos)
        assertEquals(585_000 - 200_000, changes[0].after!!.occurrenceFor(nov)!!.valorCentavos)
        // Valor igual ou maior que o previsto não é parcial.
        val notPartial = plan(ActionPlanner.RECEIVE_INCOME, snap(listOf(row)), args = arrayOf("data" to "hoje", "valor" to "5850.00", "parcial" to "sim"))
        assertEquals(PlanState.Unsupported, notPartial.state)
    }

    @Test fun aFutureReceiptIsNotRecorded() {
        val p = plan(ActionPlanner.RECEIVE_INCOME, snap(listOf(T.salary(start = d(2026, 9, 28)))), args = arrayOf("data" to "2026-11-20"))
        assertEquals(PlanState.Unsupported, p.state)
        assertTrue(p.changes.isEmpty())
    }

    // ── contas e faturas ────────────────────────────────────────────────

    @Test fun payingABillMarksItPaidWithoutAddingAnExpense() {
        val bill = T.conta(1, "Fatura Nubank", 120_000, d(2026, 11, 20))
        val p = plan(ActionPlanner.PAY_BILL, snap(contas = listOf(bill)), args = arrayOf("nome" to "fatura nubank", "data" to "hoje"))
        assertEquals(PlanState.Ready, p.state)
        val change = p.changes.single() as RowChange.Bill
        assertEquals("pago", change.after!!.status)
        assertNotNull(change.after!!.pagoEm)
        assertTrue("não lança outro gasto por cima", p.changes.none { it is RowChange.Tx })
    }

    @Test fun twoCandidateBillsAreNeverGuessed() {
        val s = snap(contas = listOf(T.conta(1, "Fatura Nubank", 120_000, d(2026, 11, 20)), T.conta(2, "Fatura Nubank Ultra", 80_000, d(2026, 11, 22))))
        val p = plan(ActionPlanner.PAY_BILL, s, args = arrayOf("nome" to "nubank"))
        assertEquals(PlanState.Clarify, p.state)
        assertEquals(2, p.options.size)
        assertTrue(p.changes.isEmpty())
    }

    @Test fun anAlreadyPaidBillAndAFutureDateChangeNothing() {
        val paid = T.conta(1, "Internet", 12_000, d(2026, 11, 5), paid = true)
        assertEquals(PlanState.Already, plan(ActionPlanner.PAY_BILL, snap(contas = listOf(paid)), args = arrayOf("nome" to "internet")).state)
        val open = T.conta(2, "Luz", 12_000, d(2026, 11, 25))
        assertEquals(PlanState.Unsupported, plan(ActionPlanner.PAY_BILL, snap(contas = listOf(open)), args = arrayOf("nome" to "luz", "data" to "2026-11-20")).state)
    }

    @Test fun reopeningABillRestoresIt() {
        val paid = T.conta(1, "Internet", 12_000, d(2026, 11, 5), paid = true).copy(pagoEm = d(2026, 11, 4).toEpochMillis())
        val p = plan(ActionPlanner.REOPEN_BILL, snap(contas = listOf(paid)), args = arrayOf("nome" to "internet"))
        val after = (p.changes.single() as RowChange.Bill).after!!
        assertEquals("pendente", after.status)
        assertNull(after.pagoEm)
    }

    // ── parcela de dívida ───────────────────────────────────────────────

    @Test fun payingAnInstallmentAdvancesOnlyThatDebt() {
        val debt = T.debt(1, "Picpay")
        val other = T.debt(2, "Banco X")
        val p = plan(ActionPlanner.PAY_INSTALLMENT, snap(dividas = listOf(debt, other)), args = arrayOf("divida" to "picpay"))
        assertEquals(PlanState.Ready, p.state)
        val payment = txChanges(p).single().after!!
        assertEquals(DebtSchedule.PAYMENT_ORIGIN, payment.origem)
        assertEquals(20_000, payment.valorCentavos)
        val change = p.changes.filterIsInstance<RowChange.Debt>().single()
        assertEquals(1, change.after!!.id)
        assertEquals(4, change.after!!.parcelasRestantes)
        assertEquals("a outra dívida não muda", 1, p.changes.filterIsInstance<RowChange.Debt>().size)
    }

    @Test fun anInstallmentWithADifferentValueIsNotRecorded() {
        val p = plan(ActionPlanner.PAY_INSTALLMENT, snap(dividas = listOf(T.debt(1, "Picpay"))), args = arrayOf("divida" to "picpay", "valor" to "50.00"))
        assertEquals(PlanState.Unsupported, p.state)
        assertTrue(p.changes.isEmpty())
    }

    // ── metas ───────────────────────────────────────────────────────────

    @Test fun contributingMovesMoneyIntoTheOneIdentifiedGoal() {
        val s = snap(objetivos = listOf(T.goal(1, "Viagem"), T.goal(2, "Carro", saved = 0)))
        val p = plan(ActionPlanner.CONTRIBUTE, s, args = arrayOf("meta" to "viagem", "valor" to "200.00"))
        assertEquals(PlanState.Ready, p.state)
        val entry = txChanges(p).single().after!!
        assertEquals(1L, entry.objetivoId)
        assertEquals(20_000, entry.valorCentavos)
        val goal = p.changes.filterIsInstance<RowChange.Goal>().single()
        assertEquals(120_000, goal.after!!.valorGuardadoCentavos)
        assertEquals("o aporte não mexe em outras metas", 1, p.changes.filterIsInstance<RowChange.Goal>().size)
    }

    @Test fun twoMatchingGoalsAskWhichOne() {
        val s = snap(objetivos = listOf(T.goal(1, "Viagem Europa"), T.goal(2, "Viagem Japão")))
        val p = plan(ActionPlanner.CONTRIBUTE, s, args = arrayOf("meta" to "viagem", "valor" to "200.00"))
        assertEquals(PlanState.Clarify, p.state)
        assertEquals(2, p.options.size)
        assertTrue(p.changes.isEmpty())
    }

    @Test fun contributionWithoutValueFutureDateOrGoalsDoesNotWrite() {
        val s = snap(objetivos = listOf(T.goal(1, "Viagem")))
        assertEquals("valor", plan(ActionPlanner.CONTRIBUTE, s, args = arrayOf("meta" to "viagem")).awaiting)
        assertEquals(PlanState.Unsupported, plan(ActionPlanner.CONTRIBUTE, s, args = arrayOf("meta" to "viagem", "valor" to "100", "data" to "2026-12-01")).state)
        assertEquals(PlanState.Unsupported, plan(ActionPlanner.CONTRIBUTE, snap(), args = arrayOf("meta" to "viagem", "valor" to "100")).state)
    }

    @Test fun creatingAndEditingAGoalTouchesOnlyThatGoal() {
        val s = snap(objetivos = listOf(T.goal(1, "Viagem"), T.goal(2, "Carro")))
        val create = plan(ActionPlanner.CREATE_GOAL, s, args = arrayOf("nome" to "Reforma", "valor" to "8000", "prazo" to "2027-10"))
        assertEquals(PlanState.Ready, create.state)
        assertEquals(1, create.changes.size)
        assertEquals(PlanState.Already, plan(ActionPlanner.CREATE_GOAL, s, args = arrayOf("nome" to "viagem", "valor" to "8000", "prazo" to "2027-10")).state)
        val edit = plan(ActionPlanner.UPDATE_GOAL, s, args = arrayOf("meta" to "viagem", "prazo" to "2028-10"))
        assertEquals(PlanState.Ready, edit.state)
        val change = edit.changes.single() as RowChange.Goal
        assertEquals(1L, change.before!!.id)
        assertEquals(change.before!!.valorAlvoCentavos, change.after!!.valorAlvoCentavos)
        assertEquals(YearMonth.of(2028, 10), YearMonth.from(change.after!!.prazo.toLocalDate()))
    }

    // ── limite ──────────────────────────────────────────────────────────

    @Test fun aBudgetLimitIsSetForThisMonthOnlyAndRepeatingItChangesNothing() {
        val p = plan(ActionPlanner.SET_BUDGET, snap(), args = arrayOf("categoria" to "Lazer", "valor" to "400"))
        assertEquals(PlanState.Ready, p.state)
        val row = (p.changes.single() as RowChange.Budget).after!!
        assertEquals("2026-11", row.mesReferencia)
        assertEquals(40_000, row.limiteMensalCentavos)
        val s2 = snap().copy(orcamentos = listOf(row))
        assertEquals(PlanState.Already, plan(ActionPlanner.SET_BUDGET, s2, args = arrayOf("categoria" to "Lazer", "valor" to "400")).state)
    }

    // ── série e exclusão: sempre confirmam ──────────────────────────────

    private val internet = tx(5, d(2026, 9, 10), 9_900, "Internet", "Casa", recorrente = true)

    @Test fun editingJustThisMonthChangesOneOccurrence() {
        val s = snap(listOf(internet))
        val r = T.parse("Minha internet foi R$ 120 este mês", s)!!
        assertEquals(ActionPlanner.UPDATE_ENTRY, r.name)
        val p = ActionPlanner.plan(r, s)
        assertEquals(PlanState.Ready, p.state)
        val after = txChanges(p).single().after!!
        assertEquals(12_000, after.occurrenceFor(nov)!!.valorCentavos)
        assertEquals(9_900, after.occurrenceFor(dec)!!.valorCentavos)
        assertEquals(9_900, after.occurrenceFor(YearMonth.of(2026, 10))!!.valorCentavos)
    }

    @Test fun changingTheSeriesAlwaysAsksForConfirmationAndOnlyAfterItWrites() {
        val s = snap(listOf(internet))
        val r = T.parse("Daqui para frente minha internet vence dia 15", s)!!
        assertEquals(ActionPlanner.UPDATE_SERIES, r.name)
        val ask = ActionPlanner.plan(r, s)
        assertEquals(PlanState.Confirm, ask.state)
        assertTrue(ask.question.isNotBlank())
        val yes = ActionPlanner.plan(r, s, confirmed = true)
        assertEquals(PlanState.Ready, yes.state)
        // Só a série da internet: o que já passou continua com o dia antigo.
        assertTrue(yes.changes.all { it is RowChange.Tx })
        assertEquals(d(2026, 11, 10), txChanges(yes).first().before!!.occurrenceFor(nov)!!.data.toLocalDate())
    }

    @Test fun deletingAlwaysConfirmsAndShowsTheExactRecord() {
        val uber = tx(7, d(2026, 11, 13), 2_300, "Uber", "Transporte")
        val s = snap(listOf(uber))
        val r = T.parse("apague o uber de ontem", s)!!
        assertEquals(ActionPlanner.DELETE_ENTRY, r.name)
        val ask = ActionPlanner.plan(r, s)
        assertEquals(PlanState.Confirm, ask.state)
        assertNull(txChanges(ask).single().after)
        assertTrue(ask.question.contains("Uber"))
        assertEquals(PlanState.Ready, ActionPlanner.plan(r, s, confirmed = true).state)
    }

    @Test fun twoIdenticalRecordsAreNeverBothDeleted() {
        val s = snap(listOf(tx(7, d(2026, 11, 13), 2_300, "Uber", "Transporte"), tx(8, d(2026, 11, 13), 2_300, "Uber", "Transporte")))
        val p = plan(ActionPlanner.DELETE_ENTRY, s, args = arrayOf("texto" to "uber", "data" to "ontem"))
        assertEquals(PlanState.Clarify, p.state)
        assertTrue(p.changes.isEmpty())
        assertEquals(2, p.options.size)
    }

    @Test fun deletingAContributionGivesTheMoneyBackToTheGoal() {
        val goal = T.goal(1, "Viagem", saved = 120_000)
        val aporte = tx(9, d(2026, 11, 13), 20_000, "Aporte · Viagem", "Transferência", "Transferencia").copy(objetivoId = 1, contaDestino = "Viagem")
        val p = plan(ActionPlanner.DELETE_ENTRY, snap(listOf(aporte), objetivos = listOf(goal)), args = arrayOf("texto" to "aporte"))
        assertEquals(PlanState.Confirm, p.state)
        assertEquals(100_000, p.changes.filterIsInstance<RowChange.Goal>().single().after!!.valorGuardadoCentavos)
    }

    // ── lançamento novo ─────────────────────────────────────────────────

    @Test fun aSpokenExpenseWithAllTheDataIsCreatedAndASecondIdenticalOneAsksFirst() {
        val s = snap(listOf(tx(1, d(2026, 11, 1), 1_000, "Padaria", "Alimentação")))
        val r = T.parse("gastei 45 no mercado hoje no débito", s)!!
        assertEquals(ActionPlanner.CREATE_ENTRY, r.name)
        val p = ActionPlanner.plan(r, s)
        assertEquals(p.summary, PlanState.Ready, p.state)
        val row = txChanges(p).single().after!!
        assertEquals(4_500, row.valorCentavos)
        assertEquals(today, row.data.toLocalDate())
        // O mesmo gasto de novo no mesmo dia: pergunta antes.
        val twice = ActionPlanner.plan(r, snap(listOf(row.copy(id = 2))))
        assertEquals(PlanState.Confirm, twice.state)
    }

    // ── acompanhamento, memória, desfazer ───────────────────────────────

    @Test fun watchRulesAndMemoryAreCreatedWithoutTouchingFinancialRecords() {
        val s = snap(dividas = listOf(T.debt(1, "Picpay")))
        val w = plan(ActionPlanner.CREATE_WATCH, s, args = arrayOf("tipo" to "quitacao", "alvo" to "picpay"))
        assertEquals(PlanState.Ready, w.state)
        assertTrue(w.changes.single() is RowChange.Watch)
        val m = plan(ActionPlanner.REMEMBER, s, args = arrayOf("texto" to "não quer mexer na reserva"))
        assertTrue(m.changes.single() is RowChange.Memory)
        assertEquals(ActionPlanner.UNDO, T.parse("desfaz isso", s)!!.name)
    }

    @Test fun anActionOutsideTheCatalogIsUnsupported() {
        assertEquals(PlanState.Unsupported, plan("apagar_tudo", snap()).state)
        assertNull(ActionPlanner.parseCall("{\"acao\":\"apagar_tudo\"}"))
        assertEquals(ActionPlanner.PAY_BILL, ActionPlanner.parseCall("Claro!\n{\"acao\":\"pagar_conta\",\"nome\":\"internet\"}")!!.name)
    }
}
