package com.finai.app.data.notifications

import com.finai.app.data.ai.AiFailureKind
import com.finai.app.data.ai.AiProvider
import com.finai.app.data.ai.AiRequest
import com.finai.app.data.ai.AiResponse
import com.finai.app.data.ai.AiTask
import com.finai.app.domain.AlertSeverity
import com.finai.app.domain.BehaviorPattern
import com.finai.app.domain.BehaviorPatternKind
import com.finai.app.domain.FinanceAlert
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A "geração de conteúdo" da Fase 5 nunca decide se um evento existe — só
 * recebe o que [com.finai.app.domain.AlertCalculator]/[com.finai.app.domain.BehaviorCoach]
 * já decidiram e escolhe o texto final, com o texto determinístico como
 * fallback sempre que a IA (aqui, um fake — nunca rede real) está indisponível.
 */
class NotificationContentBuilderTest {

    private class FakeProvider(private val response: AiResponse) : AiProvider {
        var lastRequest: AiRequest? = null
        var callCount = 0
            private set

        override suspend fun generate(request: AiRequest): AiResponse {
            callCount++
            lastRequest = request
            return response
        }
    }

    private fun alert(id: String, title: String = "Título", body: String = "Corpo") =
        FinanceAlert(id, AlertSeverity.Urgent, title, body)

    @Test
    fun `um unico alerta usa o titulo do proprio alerta e o texto da IA quando disponivel`() = runTest {
        val provider = FakeProvider(AiResponse.Success("Sua fatura vence amanhã, separa o dinheiro."))
        val content = NotificationContentBuilder(provider).forAlerts(listOf(alert("atrasado:1", "Fatura atrasada", "R$ 100 há 2 dias")))

        assertEquals("Fatura atrasada", content.title)
        assertEquals("Sua fatura vence amanhã, separa o dinheiro.", content.body)
        assertEquals(AiTask.PROACTIVE_ALERT, provider.lastRequest?.task)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `sem IA disponivel usa o texto deterministico do alerta`() = runTest {
        val provider = FakeProvider(AiResponse.Unavailable("sem chave configurada", AiFailureKind.NO_KEY))
        val content = NotificationContentBuilder(provider).forAlerts(listOf(alert("atrasado:1", "Fatura atrasada", "R$ 100 há 2 dias")))

        assertEquals("Fatura atrasada", content.title)
        assertEquals("R$ 100 há 2 dias", content.body)
    }

    @Test
    fun `varios alertas geram um unico resumo, nao uma chamada por evento`() = runTest {
        val provider = FakeProvider(AiResponse.Unavailable("offline", AiFailureKind.NETWORK_ERROR))
        val alerts = listOf(alert("a:1", "Conta 1"), alert("a:2", "Conta 2"), alert("a:3", "Conta 3"))

        val content = NotificationContentBuilder(provider).forAlerts(alerts)

        assertEquals(1, provider.callCount)
        assertTrue(content.title.contains("3"))
        assertTrue(content.body.contains("Conta 1") && content.body.contains("Conta 2") && content.body.contains("Conta 3"))
    }

    @Test
    fun `coach usa o titulo do padrao e o texto deterministico como fallback`() = runTest {
        val provider = FakeProvider(AiResponse.Unavailable("sem chave", AiFailureKind.NO_KEY))
        val pattern = BehaviorPattern(
            id = "coach:crescimento:Lazer",
            kind = BehaviorPatternKind.CATEGORY_GROWTH,
            title = "Gasto com Lazer subiu 50%",
            detail = "Você gastou R$ 300 em Lazer este mês, contra R$ 200 no mês passado.",
        )

        val content = NotificationContentBuilder(provider).forCoach(pattern)

        assertEquals(pattern.title, content.title)
        assertEquals(pattern.detail, content.body)
        assertEquals(AiTask.BEHAVIOR_COACH, provider.lastRequest?.task)
    }
}
