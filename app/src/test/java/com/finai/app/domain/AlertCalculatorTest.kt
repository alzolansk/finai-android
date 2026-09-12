package com.finai.app.domain

import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCalculatorTest {

    private val today = LocalDate.of(2026, 3, 21)

    private fun conta(
        id: Long,
        nome: String,
        dia: Int,
        tipo: String = "a_pagar",
        status: String = "pendente",
    ) = ContaEntity(
        id = id, nome = nome, valorCentavos = 25_000,
        vencimento = today.withDayOfMonth(dia).toEpochMillis(),
        status = status, tipo = tipo, recorrente = false,
    )

    @Test
    fun `sem nada pendente nao gera aviso`() {
        val alerts = AlertCalculator.alerts(
            contas = listOf(conta(1, "Internet", dia = 10, status = "pago")),
            budgets = listOf(BudgetProgress("Lazer", spentCents = 1_000, limitCents = 80_000, tone = BudgetTone.Ok, projectedEndOfMonthCents = 3_000)),
            subscriptions = emptyList(),
            today = today,
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `conta atrasada e conta vencendo geram avisos urgentes`() {
        val alerts = AlertCalculator.alerts(
            contas = listOf(conta(1, "Aluguel", dia = 10), conta(2, "Cartão", dia = 22)),
            budgets = emptyList(),
            subscriptions = emptyList(),
            today = today,
        )
        assertEquals(2, alerts.size)
        assertTrue(alerts.all { it.severity == AlertSeverity.Urgent })
        assertTrue(alerts.any { it.title.contains("Aluguel") && it.title.contains("atrasada") })
        assertTrue(alerts.any { it.title.contains("Cartão") && it.title.contains("vence em") })
    }

    @Test
    fun `conta futura fora da janela nao vira aviso`() {
        val alerts = AlertCalculator.alerts(
            contas = listOf(conta(1, "Condomínio", dia = 30)),
            budgets = emptyList(),
            subscriptions = emptyList(),
            today = today,
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `orcamento estourado e em risco viram avisos distintos`() {
        val alerts = AlertCalculator.alerts(
            contas = emptyList(),
            budgets = listOf(
                BudgetProgress("Alimentação", spentCents = 130_000, limitCents = 120_000, tone = BudgetTone.Over, projectedEndOfMonthCents = null),
                BudgetProgress("Lazer", spentCents = 60_000, limitCents = 80_000, tone = BudgetTone.Warn, projectedEndOfMonthCents = 90_000),
            ),
            subscriptions = emptyList(),
            today = today,
        )
        assertEquals(2, alerts.size)
        assertTrue(alerts.any { it.title.contains("Alimentação") && it.title.contains("passou do limite") })
        assertTrue(alerts.any { it.title.contains("Lazer") && it.title.contains("perto do limite") })
    }

    @Test
    fun `assinatura parada vira aviso e entrada prevista vira aviso positivo`() {
        val assinatura = AssinaturaEntity(id = 7, nome = "Alura", valorCentavos = 10_900, ultimoUso = today.minusDays(90).toEpochMillis(), status = "ativa")
        val alerts = AlertCalculator.alerts(
            contas = listOf(conta(3, "Salário", dia = 25, tipo = "a_receber")),
            budgets = emptyList(),
            subscriptions = listOf(SubscriptionInsight(assinatura, daysSinceLastUse = 90, looksUnused = true)),
            today = today,
        )
        assertEquals(2, alerts.size)
        // Ordenados por severidade: aviso (assinatura) antes do positivo (entrada).
        assertEquals(AlertSeverity.Warning, alerts[0].severity)
        assertTrue(alerts[0].title.contains("Alura"))
        assertEquals(AlertSeverity.Positive, alerts[1].severity)
        assertTrue(alerts[1].title.contains("Salário"))
    }

    private fun goalPlan(id: Long, nome: String, status: GoalStatus) = GoalPlan(
        objetivo = ObjetivoEntity(id = id, tipo = "Compra", nome = nome, valorAlvoCentavos = 100_000, valorGuardadoCentavos = 10_000, prazo = 0, prioridade = 1),
        progress = 0.1f,
        monthlyContributionNeededCents = 10_000,
        status = status,
        etaLabel = "Julho de 2026",
    )

    @Test
    fun `objetivo no ritmo nao gera aviso, reavaliar e prioridade geram`() {
        val alerts = AlertCalculator.alerts(
            contas = emptyList(),
            budgets = emptyList(),
            subscriptions = emptyList(),
            goals = listOf(
                goalPlan(1, "No ritmo", GoalStatus.OnTrack),
                goalPlan(2, "Reavaliar", GoalStatus.Reassess),
                goalPlan(3, "Prioridade", GoalStatus.Priority),
            ),
            today = today,
        )
        assertEquals(2, alerts.size)
        assertTrue(alerts.all { it.severity == AlertSeverity.Warning })
        assertTrue(alerts.any { it.id == "meta:2:reavaliar" && it.title.contains("Reavaliar") })
        assertTrue(alerts.any { it.id == "meta:3:prioridade" && it.title.contains("Prioridade") })
    }

    @Test
    fun `entrada avulsa confirmada recentemente vira aviso positivo distinto da previsao`() {
        val confirmada = conta(1, "Restituição IR", dia = 19, tipo = "a_receber", status = "pago")
        val alerts = AlertCalculator.alerts(
            contas = listOf(confirmada),
            budgets = emptyList(),
            subscriptions = emptyList(),
            today = today,
        )
        assertEquals(1, alerts.size)
        assertEquals("entrada-confirmada:1", alerts[0].id)
        assertEquals(AlertSeverity.Positive, alerts[0].severity)
        assertTrue(alerts[0].title.contains("confirmada"))
    }

    @Test
    fun `entrada confirmada antiga nao gera aviso`() {
        val confirmadaAntiga = conta(1, "Restituição IR", dia = 1, tipo = "a_receber", status = "pago")
        val alerts = AlertCalculator.alerts(
            contas = listOf(confirmadaAntiga),
            budgets = emptyList(),
            subscriptions = emptyList(),
            today = today,
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `salario recorrente confirmado nao vira entrada extra`() {
        val salario = conta(1, "Salário", dia = 20, tipo = "a_receber", status = "pago").copy(recorrente = true)
        val alerts = AlertCalculator.alerts(
            contas = listOf(salario),
            budgets = emptyList(),
            subscriptions = emptyList(),
            today = today,
        )
        assertTrue(alerts.isEmpty())
    }
}
