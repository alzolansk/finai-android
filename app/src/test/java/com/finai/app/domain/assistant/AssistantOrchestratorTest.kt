package com.finai.app.domain.assistant

import com.finai.app.domain.assistant.T.d
import com.finai.app.domain.assistant.T.snap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Autorização, FactGuard e o orquestrador com fakes: nenhuma escrita sem pedido explícito ou sim a uma proposta. */
class AssistantOrchestratorTest {
    private val today = T.TODAY

    // ── Authorization ───────────────────────────────────────────────────

    @Test fun classifiesWhoAuthorizesWhat() {
        val explicit = listOf("Meu salário caiu adiantado este mês, recebi hoje", "Paguei a fatura do Nubank hoje", "Guarde R$ 200 na viagem", "Registre R$ 32 de almoço hoje")
        explicit.forEach { assertEquals(it, Authorization.Kind.Explicit, Authorization.classify(it)) }
        val hypothetical = listOf("Quanto posso guardar?", "Se eu receber hoje, como fica?", "Talvez eu quite essa dívida", "Faça o que achar melhor", "E se eu pagar em 3x?")
        hypothetical.forEach { assertEquals(it, Authorization.Kind.Hypothetical, Authorization.classify(it)) }
        assertEquals(Authorization.Kind.Affirm, Authorization.classify("Sim, aplique essa mudança"))
        assertEquals(Authorization.Kind.Deny, Authorization.classify("Não, deixa"))
    }

    @Test fun anActionProposedByTheModelOnlyRunsDirectWhenTheUserSaidTheValue() {
        val req = ActionRequest(ActionPlanner.CONTRIBUTE, mapOf("meta" to "viagem", "valor" to "500"))
        assertTrue(Authorization.valuesComeFrom(req, "guarde 500 na viagem", today))
        assertFalse(Authorization.valuesComeFrom(req, "guarde um dinheiro na viagem", today))
        assertFalse(Authorization.valuesComeFrom(req, "guarde 200 na viagem", today))
    }

    // ── FactGuard ───────────────────────────────────────────────────────

    @Test fun factGuardChecksCentsSignAndYear() {
        val facts = "Sobra R$ 1.250,00 em 14/11/2026 e a conta é de R$ 78,50."
        assertTrue(FactGuard.accepts("Sobram R$ 1.250,00 em 14/11/2026.", facts))
        assertTrue("sem centavos vale o arredondado", FactGuard.accepts("A conta é de R\$ 79.", facts))
        assertFalse("centavos trocados", FactGuard.accepts("Sobram R$ 1.250,50.", facts))
        assertFalse("sinal trocado", FactGuard.accepts("Ficam -R$ 1.250,00.", facts))
        assertFalse("ano diferente", FactGuard.accepts("Em 14/11/2025.", facts))
        assertFalse("data que não existe nos fatos", FactGuard.accepts("Em 15/11.", facts))
        assertFalse("valor inventado", FactGuard.accepts("Sobram R$ 9.999.", facts))
        assertFalse(FactGuard.accepts("", facts))
    }

    // ── orquestrador ────────────────────────────────────────────────────

    private class Rig(val s: AssistantSnapshot, val ai: (List<ToolResult>) -> String? = { null }) {
        val executor = FakeExecutor()
        val sessions = FakeSessions()
        var asks = 0
        val orchestrator = AssistantOrchestrator({ s }, executor, sessions, { results, _ -> asks++; ai(results) })
        fun session(conv: Long = 1) = runBlocking { sessions.load(conv) }
        fun say(text: String, id: Long, conv: Long = 1) = runBlocking { orchestrator.handle(Turn(conv, id, text)) }
    }

    private val salaryState = snap(listOf(T.salary(start = d(2026, 9, 28))), objetivos = listOf(T.goal(1, "Viagem")))

    @Test fun anExplicitSalaryMessageRunsWithoutAConfirmationAndWithoutTheModel() {
        val rig = Rig(salaryState)
        val reply = rig.say("Meu salário caiu adiantado este mês, recebi hoje", id = 10)
        assertEquals(1, rig.executor.applied.size)
        assertEquals(0, rig.asks)
        val action = reply.answer!!.action!!
        assertEquals(ActionStatus.Applied, action.status)
        assertTrue(action.canUndo)
        assertEquals(today, action.agendaDate)
        assertEquals(1L, rig.session().lastActionId)
    }

    @Test fun theSameMessageTwiceNeverAppliesTwice() {
        val rig = Rig(salaryState)
        rig.say("Meu salário caiu adiantado este mês, recebi hoje", id = 10)
        val again = rig.say("Meu salário caiu adiantado este mês, recebi hoje", id = 10)
        assertEquals("mesma mensagem = mesma chave", 1, rig.executor.applied.size)
        assertEquals(ActionStatus.AlreadyApplied, again.answer!!.action!!.status)
    }

    @Test fun hypothesesAndQuestionsNeverWrite() {
        val rig = Rig(salaryState) { null }
        listOf("Se eu receber meu salário hoje, como fico?", "Quanto posso guardar na viagem?", "Talvez eu guarde 200 na viagem", "Faça o que achar melhor").forEach {
            rig.say(it, id = it.length.toLong())
        }
        assertTrue(rig.executor.applied.isEmpty())
    }

    @Test fun anActionTheModelSuggestsWithAValueTheUserDidNotSayBecomesAProposal() {
        val rig = Rig(salaryState) { "{\"acao\":\"guardar_meta\",\"meta\":\"viagem\",\"valor\":\"500\"}" }
        val reply = rig.say("me ajuda com a viagem", id = 20)
        assertTrue(rig.executor.applied.isEmpty())
        assertEquals(ActionStatus.NeedsConfirmation, reply.answer!!.action!!.status)
        assertNotNull(rig.session().pending)
        // O sim executa exatamente a proposta que estava na tela.
        val ok = rig.say("sim", id = 21)
        assertEquals(1, rig.executor.applied.size)
        assertEquals(ActionStatus.Applied, ok.answer!!.action!!.status)
        assertNull(rig.session().pending)
    }

    @Test fun denyingAProposalLeavesEverythingAsItWas() {
        val rig = Rig(salaryState) { "{\"acao\":\"guardar_meta\",\"meta\":\"viagem\",\"valor\":\"500\"}" }
        rig.say("me ajuda com a viagem", id = 20)
        val no = rig.say("não, deixa", id = 21)
        assertTrue(rig.executor.applied.isEmpty())
        assertNull(rig.session().pending)
        assertEquals("Cancelado", no.answer!!.title)
    }

    @Test fun deletionAndSeriesAlwaysWaitForTheYes() {
        val uber = T.tx(7, d(2026, 11, 13), 2_300, "Uber", "Transporte")
        val rig = Rig(snap(listOf(uber)))
        val ask = rig.say("apague o uber de ontem", id = 30)
        assertTrue(rig.executor.applied.isEmpty())
        assertEquals(ActionStatus.NeedsConfirmation, ask.answer!!.action!!.status)
        rig.say("sim", id = 31)
        assertEquals(1, rig.executor.applied.size)
        assertTrue(rig.executor.applied.single().first.changes.single().let { it is RowChange.Tx && it.after == null })
    }

    @Test fun clarifyThenChoosingByNumberAppliesOnlyTheChosenTarget() {
        val s = snap(contas = listOf(T.conta(1, "Fatura Nubank", 120_000, d(2026, 11, 20)), T.conta(2, "Fatura Nubank Ultra", 80_000, d(2026, 11, 22))))
        val rig = Rig(s)
        val ask = rig.say("paguei a fatura do nubank hoje", id = 40)
        assertEquals(ActionStatus.NeedsClarification, ask.answer!!.action!!.status)
        assertTrue(rig.executor.applied.isEmpty())
        rig.say("2", id = 41)
        val change = rig.executor.applied.single().first.changes.single() as RowChange.Bill
        assertEquals(2L, change.before!!.id)
    }

    @Test fun conflictFromTheExecutorMeansNothingWasWrittenAndTheAnswerSaysSo() {
        val rig = Rig(salaryState)
        rig.executor.nextStatus = ActionStatus.Conflict
        val reply = rig.say("Meu salário caiu adiantado este mês, recebi hoje", id = 50)
        assertEquals(ActionStatus.Conflict, reply.answer!!.action!!.status)
        assertTrue(reply.text.contains("Nada foi gravado"))
        assertNull(rig.session().lastActionId)
    }

    @Test fun undoGoesThroughTheExecutorAndConflictsAreShown() {
        val rig = Rig(salaryState)
        rig.say("Meu salário caiu adiantado este mês, recebi hoje", id = 10)
        val undo = rig.say("desfaz isso", id = 11)
        assertEquals(listOf<Long?>(null), rig.executor.undone)
        assertEquals(ActionStatus.Undone, undo.answer!!.action!!.status)
        assertNull(rig.session().lastActionId)
    }

    @Test fun anExplanationWithInventedNumbersIsDroppedButTheAppCardStays() {
        val s = snap(listOf(T.tx(1, today.minusDays(1), 4_590, "iFood", "Alimentação")))
        val rig = Rig(s) { results -> if (results.isEmpty()) "{\"consulta\":\"buscar_lancamentos\",\"texto\":\"ifood\"}" else "Foram R$ 9.999 com delivery." }
        val reply = rig.say("o que tem de ifood nos meus lançamentos", id = 60)
        assertNotNull(reply.answer)
        assertEquals("", reply.answer!!.explanation)
        assertFalse(reply.text.contains("9.999"))
    }

    @Test fun aGoodExplanationEntersTheCard() {
        val s = snap(listOf(T.tx(1, today.minusDays(1), 4_590, "iFood", "Alimentação")))
        val rig = Rig(s) { results -> if (results.isEmpty()) "{\"consulta\":\"buscar_lancamentos\",\"texto\":\"ifood\"}" else "Só um pedido, de R$ 45,90." }
        val reply = rig.say("o que tem de ifood nos meus lançamentos", id = 61)
        assertEquals("asks=${rig.asks} text=${reply.text}", "Só um pedido, de R$ 45,90.", reply.answer!!.explanation)
        assertEquals(1, reply.consulted.size)
    }

    @Test fun withoutTheModelLocalRulesStillAnswerAndTheOfflineCardOffersOptions() {
        val rig = Rig(salaryState)
        val q = rig.say("como fico nos próximos meses?", id = 70)
        assertNotNull(q.answer)
        assertEquals(0, rig.asks)
        val offline = rig.say("conte uma história financeira", id = 71)
        assertEquals("IA indisponível", offline.answer!!.title)
        assertTrue(offline.answer!!.options.isNotEmpty())
    }

    @Test fun codecKeepsTheExplanationAndTheAction() {
        val a = AssistantAnswer(AnswerKind.Action, "t", headline = "h", explanation = "e", action = ActionInfo(ActionStatus.Applied, 7, true, today))
        val back = com.finai.app.data.assistant.AssistantAnswerCodec.decode(com.finai.app.data.assistant.AssistantAnswerCodec.encode(a))
        assertEquals(a, back)
    }
}
