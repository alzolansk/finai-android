package com.finai.app.domain.assistant

import com.finai.app.domain.assistant.T.d
import com.finai.app.domain.assistant.T.snap
import com.finai.app.domain.assistant.T.tx
import com.finai.app.domain.projection.CashFlowProjection
import com.finai.app.domain.toLocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AssistantRequestsRegressionTest {
    private val today = T.TODAY
    private val extra = tx(9, d(2026, 12, 20), 230_001, "Décimo terceiro", "Receita", "Receita").copy(extra = true)
    private val splitText = "preciso dividir o decimo terceiro em duas vezes, a primeira parcela vai cair 30/11"

    @Test fun screenshotRequestFindsTheFutureIncomeAndPreservesEveryCent() {
        val s = snap(listOf(extra))
        val request = IntentParser.action(splitText, s, AssistantSession())!!
        val plan = ActionPlanner.plan(request, s)
        assertEquals(PlanState.Confirm, plan.state)
        val changes = plan.changes.filterIsInstance<RowChange.Tx>()
        assertEquals(2, changes.size)
        assertEquals(extra, changes[0].before)
        assertNull(changes[1].before)
        assertEquals(extra.id, changes[0].after!!.id)
        assertEquals(0L, changes[1].after!!.id)
        assertEquals(extra.valorCentavos, changes.sumOf { it.after!!.valorCentavos })
        assertEquals(listOf(d(2026, 11, 30), extra.data.toLocalDate()), changes.map { it.after!!.data.toLocalDate() })
        assertTrue(changes.all { it.after!!.extra && !it.after!!.rendaPrincipal })
        // Substituir o registro, em vez de adicionar o total original de novo.
        val after = snap(changes.mapIndexed { i, c -> c.after!!.copy(id = i + 20L) })
        assertEquals(CashFlowProjection.of(s.contas, s.transacoes, s.dividas, today, d(2026, 12, 31)).balanceOn(d(2026, 12, 31)),
            CashFlowProjection.of(after.contas, after.transacoes, after.dividas, today, d(2026, 12, 31)).balanceOn(d(2026, 12, 31)))
    }

    @Test fun accountsReceivableCanAlsoBeSplitAtomically() {
        val bill = T.conta(9, "13º salário", 115_000, d(2026, 12, 20)).copy(tipo = "a_receber")
        val s = snap(contas = listOf(bill))
        val plan = ActionPlanner.plan(ActionRequest(ActionPlanner.SPLIT_INCOME, mapOf("texto" to "decimo terceiro", "primeira_data" to "30/11")), s)
        assertEquals(PlanState.Confirm, plan.state)
        val changes = plan.changes.filterIsInstance<RowChange.Bill>()
        assertEquals(bill, changes.first().before)
        assertEquals(bill.valorCentavos, changes.sumOf { it.after!!.valorCentavos })
        assertTrue(changes.all { it.after!!.tipo == "a_receber" && it.after!!.status == "pendente" })
    }

    @Test fun missingOrAmbiguousIncomeNeverBecomesAnInventedNewEntry() {
        val req = ActionRequest(ActionPlanner.SPLIT_INCOME, mapOf("texto" to "decimo", "primeira_data" to "30/11"))
        assertEquals(PlanState.Clarify, ActionPlanner.plan(req, snap()).state)
        val ambiguous = ActionPlanner.plan(req, snap(listOf(extra, extra.copy(id = 10, descricao = "Décimo outro emprego"))))
        assertEquals(PlanState.Clarify, ambiguous.state)
        assertEquals(2, ambiguous.options.size)
        assertTrue(ambiguous.changes.isEmpty())
    }

    @Test fun conversationCanCorrectTheProposalAndConfirmOnce() = runBlocking {
        val executor = FakeExecutor()
        val sessions = FakeSessions()
        var calls = 0
        val s = snap(listOf(extra))
        val chat = AssistantOrchestrator({ s }, executor, sessions, { _, _ -> calls++; null })
        val first = chat.handle(Turn(1, 1, splitText))
        assertEquals(ActionStatus.NeedsConfirmation, first.answer!!.action!!.status)
        assertTrue(executor.applied.isEmpty())
        val corrected = chat.handle(Turn(1, 2, "a segunda vai cair 22/12"))
        assertEquals(ActionStatus.NeedsConfirmation, corrected.answer!!.action!!.status)
        assertTrue(executor.applied.isEmpty())
        chat.handle(Turn(1, 3, "sim"))
        assertEquals(1, executor.applied.size)
        assertEquals(d(2026, 12, 22), (executor.applied.single().first.changes.last() as RowChange.Tx).after!!.data.toLocalDate())
        assertEquals(0, calls)
    }

    @Test fun changedSourceInvalidatesTheConfirmedSplit() {
        val req = ActionRequest(ActionPlanner.SPLIT_INCOME, mapOf("texto" to "decimo", "primeira_data" to "30/11"))
        val shown = ActionPlanner.plan(req, snap(listOf(extra)))
        val changed = ActionPlanner.plan(shown.request, snap(listOf(extra.copy(valorCentavos = 999_999))), confirmed = true)
        assertEquals(PlanState.Unsupported, changed.state)
        assertTrue(changed.changes.isEmpty())
    }

    @Test fun screenshotFollowUpFindsExistingIncomeAcrossMonths() = runBlocking {
        val s = snap(listOf(extra))
        val executor = FakeExecutor()
        val sessions = FakeSessions()
        val chat = AssistantOrchestrator({ s }, executor, sessions, { _, _ -> error("Não precisa de IA") })
        val found = chat.handle(Turn(1, 1, "recebeu sim, esta pro dia 20/12"))
        assertTrue(found.answer!!.evidence.single().contains("Décimo terceiro"))
        // Sem foco nem nome, primeiro consulta; não altera uma receita silenciosamente.
        assertTrue(executor.applied.isEmpty())
    }

    @Test fun focusedIncomeDateCorrectionMovesOnlyTheExistingRecord() = runBlocking {
        val s = snap(listOf(extra))
        val executor = FakeExecutor()
        val sessions = FakeSessions()
        val chat = AssistantOrchestrator({ s }, executor, sessions, { _, _ -> error("Não precisa de IA") })
        chat.handle(Turn(1, 1, "onde esta cadastrado o decimo terceiro?"))
        val same = chat.handle(Turn(1, 2, "recebeu sim, esta pro dia 20/12"))
        assertEquals(ActionStatus.AlreadyApplied, same.answer!!.action!!.status)
        chat.handle(Turn(1, 3, "corrija para 22/12"))
        val change = executor.applied.single().first.changes.single() as RowChange.Tx
        assertEquals(extra, change.before)
        assertEquals(d(2026, 12, 22), change.after!!.data.toLocalDate())
    }

    @Test fun savingsQuestionWithPreambleIsReadOnlyAndUsesTheExactDate() = runBlocking {
        val s = snap()
        val text = "seguindo as tendências atuais, quanto vou poder colocar na reserva dia 15/01/2027"
        assertEquals(Authorization.Kind.Hypothetical, Authorization.classify(text))
        val call = IntentParser.query(text, s, AssistantSession())!!
        assertEquals("capacidade_aporte", call.name)
        assertEquals("15/01/2027", call.args["data"])
        val executor = FakeExecutor()
        val chat = AssistantOrchestrator({ s }, executor, FakeSessions(), { _, _ -> error("Não precisa de IA") })
        val answer = chat.handle(Turn(1, 1, text)).answer!!
        assertTrue(answer.title.contains("15/01/2027"))
        assertTrue(executor.applied.isEmpty())
    }

    private fun savingsState() = snap(listOf(
        tx(1, d(2026, 11, 1), 1_000_000, "Saldo inicial", "Receita", "Receita"),
        tx(2, d(2026, 10, 3), 31_000, "Mercado", "Alimentação"),
        tx(3, d(2026, 9, 3), 31_000, "Mercado", "Alimentação"),
    ), contas = listOf(T.conta(1, "Conta depois do aporte", 600_000, d(2027, 1, 20))))

    @Test fun savingCapacityProtectsBillsAfterTheRequestedDateAndTheMargin() {
        val s = savingsState()
        val r = SavingsAnswer.run(s, mapOf("data" to "2027-01-15"))
        val p = CashFlowProjection.of(s.contas, s.transacoes, s.dividas, today, d(2027, 4, 30), provision = com.finai.app.domain.projection.VariableProvision.of(s.contas, s.transacoes, s.dividas, today))
        val expected = (p.lowestFrom(d(2027, 1, 15))!!.cents - s.marginCents).coerceAtLeast(0)
        assertEquals(Fmt.money(expected), r.answer!!.metrics.first().value)
        assertTrue(expected < p.balanceOn(d(2027, 1, 15)) - 600_000)
        assertTrue(r.answer!!.headline.contains("poderia guardar"))
    }

    @Test fun unknownHistoryIsNotPresentedAsASafeRecommendation() {
        val r = SavingsAnswer.run(snap(listOf(tx(1, today.minusDays(1), 100_000, "Receita", "Receita", "Receita"))), mapOf("data" to "2027-01-15"))
        assertTrue(r.answer!!.headline.contains("teto"))
        assertTrue(r.answer!!.notes.any { it.contains("não é uma recomendação") })
    }

    @Test fun invalidAndOutOfRangeDatesDoNotSilentlyAnswerForToday() {
        listOf("31/02/2027", "2029-01-15", "2026-10-01").forEach { date ->
            assertEquals(date, AnswerKind.Question, SavingsAnswer.run(snap(), mapOf("data" to date)).answer!!.kind)
        }
    }

    @Test fun yearlessFutureDateUsesNextJanuary() {
        assertEquals(d(2027, 1, 15), ActionPlanner.parseFutureDate("15/01", today))
        assertTrue(SavingsAnswer.run(snap(), mapOf("data" to "15/01")).answer!!.title.contains("15/01/2027"))
    }

    @Test fun spendingQuestionContainingPorDoesNotBecomeASavingsQuestion() {
        assertEquals("analisar_gastos", IntentParser.query("Quanto paguei por alimentação?", snap(), AssistantSession())!!.name)
        assertEquals("capacidade_aporte", IntentParser.query("Quanto posso pôr na reserva dia 15/01?", snap(), AssistantSession())!!.name)
    }

    @Test fun dayThirteenAndOtherNamedSubjectsNeverRetargetTheExtraIncome() {
        val s = snap(listOf(extra))
        val focus = AssistantSession(focus = listOf(EntityRef(EntityRef.ENTRY, extra.id, extra.descricao)))
        assertNotEquals(ActionPlanner.MOVE_INCOME, IntentParser.action("minha internet esta para 13/12", s, AssistantSession())?.name)
        assertNotEquals(ActionPlanner.MOVE_INCOME, IntentParser.action("minha internet esta para 22/12", s, focus)?.name)
        assertNotEquals(ActionPlanner.MOVE_INCOME, IntentParser.action("mude minha internet para 22/12", s, focus)?.name)
        assertEquals(ActionPlanner.SPLIT_INCOME, IntentParser.action("divida o 13 em duas partes, primeira 30/11", s, AssistantSession())!!.name)
    }

    @Test fun modelDeflectionIsRetriedAsAToolAndNeverSentBackToTheUser() = runBlocking {
        var asks = 0
        val executor = FakeExecutor()
        val chat = AssistantOrchestrator({ snap(listOf(extra)) }, executor, FakeSessions(), { _, _ ->
            asks++
            if (asks == 1) "Peça para o app registrar essa renda." else """{"consulta":"buscar_receitas_previstas","texto":"decimo"}"""
        })
        val r = chat.handle(Turn(1, 1, "me ajude com meu recebimento extra"))
        assertNotNull(r.answer)
        assertFalse(r.text.contains("Peça para o app"))
        assertTrue(executor.applied.isEmpty())
    }

    @Test fun priorShortfallBlocksAnUnconditionalSavingsRecommendation() {
        val s = savingsState().copy(contas = listOf(T.conta(1, "Conta antes do aporte", 2_000_000, d(2026, 12, 1))))
        val answer = SavingsAnswer.run(s, mapOf("data" to "2027-01-15")).answer!!
        assertTrue(answer.headline.contains("preciso cobrir"))
        assertEquals(Fmt.money(0), answer.metrics.first().value)
    }

    @Test fun modelCannotClaimItSavedWithoutAnExecutorReceipt() = runBlocking {
        val executor = FakeExecutor()
        val chat = AssistantOrchestrator({ snap() }, executor, FakeSessions(), { _, _ -> "Registrei sua nova receita." })
        val r = chat.handle(Turn(1, 1, "organize a minha renda extra"))
        assertFalse(r.text.contains("Registrei"))
        assertTrue(r.answer!!.headline.contains("Nenhum registro foi modificado"))
        assertTrue(executor.applied.isEmpty())
    }

    @Test fun generalProjectionNamesTheEstimateAndHighlightsItsShortfall() {
        val s = savingsState().copy(contas = listOf(T.conta(1, "Conta grande", 950_000, d(2027, 1, 20))))
        val answer = AssistantTools.run(ToolCall("projetar_caixa", mapOf("meses" to "3")), s).answer!!
        assertTrue(answer.headline.contains("falta dinheiro"))
        assertFalse(answer.plainText().contains("est."))
        assertTrue(answer.evidence.all { it.contains("estimado") })
    }
}
