package com.finai.app.domain

import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Regras das telas de aporte, novo objetivo e edição. */
class GoalFlowTest {
    private val today = LocalDate.of(2026, 10, 10)

    private fun reserva(saldo: Long, aporte: Long = 0) = ObjetivoEntity(
        id = 1, tipo = "Reserva", nome = "Reserva", valorAlvoCentavos = 0, valorGuardadoCentavos = saldo,
        prazo = today.plusYears(1).toEpochMillis(), prioridade = 1, aporteMensalCentavos = aporte,
    )

    private fun meta(alvo: Long, saldo: Long, prazo: LocalDate = LocalDate.of(2027, 7, 31)) = ObjetivoEntity(
        id = 2, tipo = "Viagem", nome = "Passagem Itália", valorAlvoCentavos = alvo, valorGuardadoCentavos = saldo,
        prazo = prazo.toEpochMillis(), prioridade = 2,
    )

    @Test fun sliderValuesSnapToTheStep() {
        assertEquals(1_440_00L, GoalFlow.valueAt(0.2881f, 0, 5_000_00, 10_00))
        assertEquals(5_000_00L, GoalFlow.valueAt(0.0909f, GoalFlow.TARGET_MIN_CENTS, GoalFlow.TARGET_MAX_CENTS, GoalFlow.TARGET_STEP_CENTS))
        assertEquals(0.04f, GoalFlow.fractionOf(200_00, 0, 5_000_00), 0.0001f)
    }

    @Test fun deadlineAndHorizonFollowTheMonthCount() {
        assertEquals(LocalDate.of(2027, 7, 31), GoalFlow.deadlineFor(9, today))
        assertEquals("em 9 meses", GoalFlow.horizonLabel(9))
        assertEquals("em 1 mês", GoalFlow.horizonLabel(1))
        assertEquals("em 1 ano", GoalFlow.horizonLabel(12))
        assertEquals("em 2 anos e 3 meses", GoalFlow.horizonLabel(27))
        assertEquals(9, GoalFlow.monthsFor(LocalDate.of(2027, 7, 31), today))
        // Prazo vencido não quebra o controle.
        assertEquals(1, GoalFlow.monthsFor(LocalDate.of(2026, 3, 1), today))
    }

    @Test fun monthlyNeededRoundsUp() {
        assertEquals(555_56L, GoalFlow.monthlyNeededCents(5_000_00, 0, 9))
        assertEquals(0L, GoalFlow.monthlyNeededCents(5_000_00, 6_000_00, 9))
    }

    @Test fun fitComparesWithAverageSurplus() {
        val folgado = GoalFlow.fit(556_00, 3_100_00, 0)
        assertEquals(GoalFlow.FitLevel.Folgado, folgado.level)
        assertEquals(18, folgado.sharePct)
        assertEquals("Usa 18% da sua sobra média de R$ 3.100.", folgado.text)
        assertEquals(GoalFlow.FitLevel.Apertado, GoalFlow.fit(1_000_00, 3_100_00, 2_500_00).level)
        assertEquals(GoalFlow.FitLevel.NaoCabe, GoalFlow.fit(4_000_00, 3_100_00, 0).level)
        assertEquals(GoalFlow.FitLevel.SemRenda, GoalFlow.fit(556_00, null, 0).level)
    }

    @Test fun reserveCoverageUsesAverageMonthlySpending() {
        val gasto = TransacaoEntity(
            id = 1, valorCentavos = 3_150_00, categoria = "Moradia", descricao = "Aluguel", contaOrigem = "Carteira",
            recorrente = false, data = today.withDayOfMonth(5).toEpochMillis(), origem = "manual",
        )
        val daily = GoalFlow.averageDailySpendingCents(emptyList(), listOf(gasto), emptyList(), today)
        assertEquals(105_00L, daily)
        val preview = GoalFlow.preview(reserva(2_000_00), 200_00, daily)
        assertEquals("Reserva vai para R$ 2.200", preview.title)
        assertEquals("Cobre cerca de 20 dias dos seus gastos médios", preview.detail)
        assertNull(GoalFlow.averageDailySpendingCents(emptyList(), emptyList(), emptyList(), today))
    }

    @Test fun contributionLimitsAndDefault() {
        assertEquals(5_000_00L, GoalFlow.contributionMaxCents(reserva(2_000_00)))
        assertEquals(200_00L, GoalFlow.defaultContributionCents(reserva(2_000_00), today))
        assertEquals(300_00L, GoalFlow.defaultContributionCents(reserva(2_000_00, aporte = 300_00), today))
        // Meta com alvo: o teto é o que falta, e o padrão é o aporte mensal necessário.
        assertEquals(3_000_00L, GoalFlow.contributionMaxCents(meta(5_000_00, 2_000_00)))
        assertEquals(340_00L, GoalFlow.defaultContributionCents(meta(5_000_00, 2_000_00), today))
    }

    @Test fun successMessages() {
        assertEquals(
            "Sua reserva cresceu 121%. Cada real aqui é tranquilidade para imprevistos.",
            GoalFlow.successMessage(reserva(2_000_00), 2_420_00, today),
        )
        assertEquals("Meta alcançada. O que entrar agora passa do alvo.", GoalFlow.successMessage(meta(5_000_00, 4_800_00), 200_00, today))
        assertTrue(GoalFlow.successMessage(meta(5_000_00, 1_000_00), 500_00, today).startsWith("Faltam R$ 3.500."))
    }

    @Test fun changesListOnlyWhatMoved() {
        val before = reserva(2_000_00).copy(prioridade = 2)
        val after = before.copy(prioridade = 1, descricao = "Reserva Geral")
        val changes = GoalFlow.changes(before, after)
        assertEquals(listOf("Prioridade", "Descrição"), changes.map { it.label })
        assertEquals(GoalFlow.Change("Descrição", "Vazia", "Reserva Geral"), changes[1])
        // Prioridade 4 e 3 caem as duas em "Baixa": não é mudança.
        assertTrue(GoalFlow.changes(before.copy(prioridade = 4), before.copy(prioridade = 3)).isEmpty())
        // Virar meta com alvo mostra alvo e prazo novos.
        val meta = GoalFlow.changes(before, before.copy(valorAlvoCentavos = 5_000_00, prazo = LocalDate.of(2027, 7, 31).toEpochMillis()))
        assertEquals(listOf("Reserva contínua", "Valor da meta", "Prazo"), meta.map { it.label })
    }
}
