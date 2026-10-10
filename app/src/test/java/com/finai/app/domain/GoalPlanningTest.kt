package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.GoalBadge
import com.finai.app.domain.scenario.ScenarioReplyGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Visão consolidada das metas e os pontos de atenção: tudo calculado, nada inventado. */
class GoalPlanningTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val abr2028 = LocalDate.of(2028, 4, 28)

    private fun salario(cents: Long = 300_000) = TransacaoEntity(
        data = LocalDate.of(2026, 7, 30).toEpochMillis(), descricao = "Salário", valorCentavos = cents, categoria = "Outros",
        contaOrigem = "Carteira", recorrente = true, origem = "manual", tipo = TransactionType.Receita.name, rendaPrincipal = true)

    private fun meta(id: Long, nome: String, alvo: Long, guardado: Long = 0, prazo: LocalDate = abr2028, prioridade: Int = id.toInt(),
                     descricao: String = "", aporte: Long = 0) = ObjetivoEntity(
        id = id, tipo = "Viagem", nome = nome, valorAlvoCentavos = alvo, valorGuardadoCentavos = guardado,
        prazo = prazo.toEpochMillis(), prioridade = prioridade, descricao = descricao, aporteMensalCentavos = aporte)

    private fun build(
        objetivos: List<ObjetivoEntity>,
        transacoes: List<TransacaoEntity> = listOf(salario()),
        dividas: List<DividaEntity> = emptyList(),
        contas: List<ContaEntity> = emptyList(),
    ): Triple<FinancialPlan, GoalsOverview, List<GoalInsight>> {
        val plan = FinancialPlan.build(contas, transacoes, dividas, objetivos, today)
        val hasIncome = transacoes.any { it.tipo == TransactionType.Receita.name }
        val overview = GoalPlanning.overview(plan, hasIncome, today)
        return Triple(plan, overview, GoalPlanning.insights(plan, overview, dividas, hasIncome, today))
    }

    private val italia = meta(1, "Itália 2028", 2_000_000)
    private val passagem = meta(2, "Passagem Itália", 1_000_000)

    @Test fun totalCountsOnlyGoalsThatStillNeedMoneyBeforeTheirDeadline() {
        val (_, overview, _) = build(listOf(
            italia, passagem,
            meta(3, "Reserva geral", 0, guardado = 150_000, aporte = 30_000),
            meta(4, "Notebook", 100_000, guardado = 100_000),
            meta(5, "Curso", 100_000, prazo = LocalDate.of(2026, 8, 1)),
        ))
        // 18 meses de out/2026 a abr/2028.
        assertEquals(listOf(111_111L, 55_555L), overview.needs.map { it.neededCents })
        assertEquals(166_666L, overview.totalNeededCents)
        assertEquals(30_000L, overview.reservesPlannedCents)
        assertEquals(196_666L, overview.totalMonthlyCents)
        assertEquals(listOf("Curso"), overview.overdueNames)
    }

    @Test fun reserveWithoutTargetIsNeverDone() {
        val reserva = meta(3, "Reserva geral", 0, guardado = 150_000)
        val plan = GoalCalculator.plan(listOf(reserva), 0, today).single()
        assertEquals(GoalPace.Open, GoalPace.of(plan, today))
        val ui = plan.toUiGoal(today = today)
        assertEquals(GoalBadge.Open, ui.badge)
        assertTrue(ui.openReserve)
        assertEquals(0f, ui.progress)
        assertFalse(Completion.isDone(reserva))
    }

    @Test fun goalsSharingAWordAreFlaggedAsPossibleDoubleCount() {
        val (_, _, insights) = build(listOf(italia, passagem))
        val overlap = insights.single { it.id.startsWith("sobreposicao") }
        assertEquals(2L, overlap.actionGoalId) // a menor é a que talvez esteja dentro da outra
        assertEquals(InsightAction.EditGoal, overlap.action)
        assertEquals(setOf("italia"), GoalPlanning.tokens("Itália 2028"))
    }

    @Test fun aDescriptionSayingItIsSeparateSilencesTheOverlap() {
        val (_, _, insights) = build(listOf(italia, passagem.copy(descricao = "A passagem fica fora do valor da viagem")))
        assertTrue(insights.none { it.id.startsWith("sobreposicao") })
    }

    @Test fun goalsAskingMoreThanTheSurplusSuggestPostponingTheLowestPriority() {
        val carro = meta(3, "Entrada Carro", 3_600_000, prazo = LocalDate.of(2027, 10, 1), prioridade = 3)
        val (_, overview, insights) = build(listOf(italia, carro))
        assertTrue(overview.gapCents!! > 0)
        assertTrue(insights.any { it.id == "conflito" })
        val adiar = insights.single { it.id == "adiar-3" }
        // 12 meses até out/2027; com +6, R$ 36.000 ÷ 18.
        assertTrue(adiar.title, adiar.title.contains("R$ 3.000") && adiar.title.contains("R$ 2.000"))
        assertEquals(3L, adiar.actionGoalId)
    }

    @Test fun withoutIncomeTheAppAsksForItInsteadOfGuessing() {
        val (_, overview, insights) = build(listOf(italia), transacoes = emptyList())
        assertNull(overview.averageSurplusCents)
        assertEquals("sem-renda", insights.first { it.global }.id)
        assertTrue(insights.none { it.id == "conflito" })
    }

    @Test fun aDebtEndingSoonIsAnOpportunityOnlyWhenGoalsLackMoney() {
        val divida = DividaEntity(
            id = 9, nome = "Tablet", valorOriginalCentavos = 240_000, valorAbertoCentavos = 44_000, taxaJurosMensalBasisPoints = 0,
            parcelasRestantes = 2, valorParcelaCentavos = 22_000, parcelasTotais = 12,
            proximoVencimento = LocalDate.of(2026, 10, 9).toEpochMillis(), diaVencimento = 9)
        val carro = meta(3, "Entrada Carro", 3_600_000, prazo = LocalDate.of(2027, 10, 1))
        val (_, _, insights) = build(listOf(carro), dividas = listOf(divida))
        val op = insights.single { it.id == "oportunidade-9" }
        assertTrue(op.title, op.title.contains("dez/2026") && op.title.contains("R$ 220"))
    }

    @Test fun nothingToSayMeansNoInsights() {
        // Meta pequena, folgada, já começada: nenhum ponto merece atenção.
        val (_, _, insights) = build(listOf(meta(1, "Fone", 60_000, guardado = 10_000, prazo = LocalDate.of(2027, 6, 1))))
        assertTrue(insights.map { it.id }.toString(), insights.none { it.global })
    }

    @Test fun theAiMayOnlyQuoteNumbersFromTheFacts() {
        val (plan, overview, insights) = build(listOf(italia, passagem))
        val facts = GoalPlanning.factsBlock(plan, overview, insights, emptyList(), true, today)
        assertTrue(facts.contains("Total necessário por mês: R$ 1.667"))
        assertTrue(facts.contains("com +6 meses R$ 833/mês"))
        assertTrue(ScenarioReplyGuard.accepts("Adiar a Itália derruba o aporte para R$ 833/mês | prazo novo libera margem", facts))
        assertFalse(ScenarioReplyGuard.accepts("Guarde R$ 1.234 por mês | inventado", facts))
    }

    private fun conta(nome: String, cents: Long, vence: LocalDate) = ContaEntity(
        nome = nome, valorCentavos = cents, vencimento = vence.toEpochMillis(), status = "pendente", tipo = "a_pagar", recorrente = false)

    @Test fun anExpensiveDebtIsPrioritisedBeforeNewContributions() {
        val rotativo = DividaEntity(id = 7, nome = "Rotativo", valorOriginalCentavos = 400_000, valorAbertoCentavos = 400_000,
            taxaJurosMensalBasisPoints = 1_200, parcelasRestantes = 0, valorParcelaCentavos = 0)
        val (_, _, insights) = build(listOf(italia), dividas = listOf(rotativo), contas = listOf(conta("Aluguel", 288_600, LocalDate.of(2026, 10, 12))))
        val first = insights.single { it.id == "divida-primeiro" }
        assertTrue(first.title, first.title.contains("Rotativo"))
        assertEquals(InsightAction.OpenDebts, first.action)
    }

    @Test fun aPredictedShortfallComesFirstAndBlocksContributions() {
        val (_, _, insights) = build(
            listOf(italia),
            transacoes = listOf(salario(), TransacaoEntity(data = LocalDate.of(2026, 10, 10).toEpochMillis(), descricao = "Freela",
                valorCentavos = 190_000, categoria = "Outros", contaOrigem = "Carteira", recorrente = false, origem = "manual",
                tipo = TransactionType.Receita.name)),
            contas = listOf(conta("Aluguel", 350_000, LocalDate.of(2026, 10, 8))),
        )
        assertEquals("falta", insights.first().id)
        assertEquals(InsightSeverity.Risk, insights.first().severity)
    }
}
