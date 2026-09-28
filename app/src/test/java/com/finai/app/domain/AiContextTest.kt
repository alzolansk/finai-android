package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** O que a IA recebe sobre dívidas e metas: cada uma separada, com a divisão feita pelo app. */
class AiContextTest {
    private val today = LocalDate.of(2026, 9, 28)

    private fun divida(nome: String, aberto: Long, taxaBp: Int, parcela: Long = 0, restantes: Int = 0) = DividaEntity(
        nome = nome, valorOriginalCentavos = aberto, valorAbertoCentavos = aberto, taxaJurosMensalBasisPoints = taxaBp,
        parcelasRestantes = restantes, valorParcelaCentavos = parcela, parcelasTotais = restantes,
        proximoVencimento = if (restantes > 0) LocalDate.of(2026, 10, 10).toEpochMillis() else null,
    )

    @Test fun eachDebtGoesSeparatelyWithItsOwnInterest() {
        val summary = DebtCalculator.summarize(
            listOf(divida("Empréstimo", 741_300, 190, 41_200, 18), divida("Rotativo Nubank", 438_700, 1_390)),
            today,
        )
        val block = AiContext.debtsBlock(summary.ordered, today)
        val lines = AiContext.debtLines(summary.ordered, today)
        assertEquals(2, lines.size)
        // Maior juro primeiro, com os juros dele — não a soma.
        assertTrue(lines[0], lines[0].startsWith("1. \"Rotativo Nubank\""))
        assertTrue(lines[0], lines[0].contains("13,9% ao mês") && lines[0].contains("R$ 610 de juros"))
        assertTrue(lines[0], lines[0].contains("sem parcela fixa"))
        assertTrue(lines[1], lines[1].contains("parcela de R$ 412, faltam 18"))
        assertTrue(block.contains("nunca apresente o total"))
    }

    @Test fun goalsCarryTheSplitOfTheSurplus() {
        val objetivos = listOf(
            ObjetivoEntity(id = 1, tipo = "Reserva", nome = "Reserva de emergência", valorAlvoCentavos = 2_550_000,
                valorGuardadoCentavos = 1_240_000, prazo = LocalDate.of(2027, 12, 28).toEpochMillis(), prioridade = 1),
            ObjetivoEntity(id = 2, tipo = "Viagem", nome = "Portugal", valorAlvoCentavos = 1_500_000,
                valorGuardadoCentavos = 550_000, prazo = LocalDate.of(2027, 4, 28).toEpochMillis(), prioridade = 2),
        )
        val plans = GoalCalculator.plan(objetivos, 114_00, today)
        val block = AiContext.goalsBlock(plans)
        val lines = AiContext.goalLines(plans)
        assertTrue(lines[0], lines[0].startsWith("Prioridade 1: \"Reserva de emergência\""))
        // A sobra de R$ 114 vai toda para a primeira; a segunda recebe o que sobra dela (nada).
        assertTrue(lines[0], lines[0].contains("no plano até o salário: R$ 114"))
        assertTrue(lines[1], lines[1].contains("no plano até o salário: R$ 0"))
        assertTrue(block.contains("não recomende a mesma sobra para outra meta"))
    }

    @Test fun goalInsightsGoInOneRequestWithThePlan() {
        fun goal(id: String, name: String) = com.finai.app.data.model.Goal(
            id = id, kind = "Viagem", name = name, saved = 5_500.0, target = 15_000.0, eta = "abril de 2027",
            badge = com.finai.app.data.model.GoalBadge.Reassess, note = "", action = "Registrar aporte",
            planNote = "Sem valor adicional até o salário de 30/10: a sobra vai para \"Rotativo\".",
        )
        val request = AiPromptBuilder.goalInsights(listOf(goal("1", "Portugal"), goal("2", "Carro")), "Plano financeiro calculado pelo app")
        assertTrue(request.prompt.contains("[1] \"Portugal\""))
        assertTrue(request.prompt.contains("[2] \"Carro\""))
        assertTrue(request.prompt.contains("Plano financeiro calculado pelo app"))
        assertTrue(request.prompt.contains("a sobra vai para \"Rotativo\""))
    }

    @Test fun numberedSectionsSplitTheBatchedAnswer() {
        val raw = "Aqui vai:\n**[1]**\nAgora: bem.\nPróximo passo: seguir.\nRisco: nenhum relevante.\n[2] Agora: parado.\nPróximo passo: esperar.\nRisco: prazo."
        val sections = AiReplyFormat.numberedSections(raw)
        assertEquals(setOf(1, 2), sections.keys)
        assertEquals(3, AiReplyFormat.labeled(sections.getValue(1), AiPromptBuilder.GOAL_INSIGHT_LABELS)!!.size)
        assertEquals("Agora: parado.", sections.getValue(2).lines().first())
    }
}
