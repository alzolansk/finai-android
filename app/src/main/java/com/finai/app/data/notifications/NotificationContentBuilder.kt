package com.finai.app.data.notifications

import com.finai.app.data.ai.AiProvider
import com.finai.app.data.ai.AiResponse
import com.finai.app.domain.AiPromptBuilder
import com.finai.app.domain.BehaviorPattern
import com.finai.app.domain.FinanceAlert

/** O que vai na notificação — sempre pronto para exibir, com ou sem IA. */
data class NotificationContent(val title: String, val body: String)

/**
 * Camada de "geração de conteúdo" da Fase 5 (planning.md §9): a única que
 * decide o *texto* de uma notificação, nunca se ela deve existir — isso já
 * foi decidido por [com.finai.app.domain.AlertCalculator] /
 * [com.finai.app.domain.BehaviorCoach] antes de chegar aqui. Usa
 * exclusivamente a infraestrutura de IA da Fase 3 ([provider] é, na prática,
 * sempre o `AiRouter`), e nunca é chamada quando não há evento — quem decide
 * isso é [com.finai.app.data.work.FinanceCheckWorker], não esta classe.
 *
 * Se a IA estiver indisponível (sem chave, sem rede, cota esgotada em todos
 * os provedores), a notificação sai mesmo assim com o texto determinístico
 * do próprio alerta/padrão — nunca com "notificação indisponível": o dado
 * já é local e já é bom o bastante para mostrar sozinho (planning.md §4).
 */
class NotificationContentBuilder(private val provider: AiProvider) {

    suspend fun forAlerts(alerts: List<FinanceAlert>): NotificationContent {
        require(alerts.isNotEmpty()) { "forAlerts não deve ser chamado sem eventos — quem decide isso é o Worker." }
        val fallbackTitle = if (alerts.size == 1) alerts.first().title else "Você tem ${alerts.size} avisos financeiros"
        val fallbackBody = if (alerts.size == 1) {
            alerts.first().body
        } else {
            alerts.joinToString(" ") { it.title + "." }
        }
        return when (val response = provider.generate(AiPromptBuilder.proactiveAlerts(alerts))) {
            is AiResponse.Success -> NotificationContent(fallbackTitle, response.text)
            is AiResponse.Unavailable -> NotificationContent(fallbackTitle, fallbackBody)
        }
    }

    suspend fun forCoach(pattern: BehaviorPattern): NotificationContent =
        when (val response = provider.generate(AiPromptBuilder.behaviorCoach(pattern))) {
            is AiResponse.Success -> NotificationContent(pattern.title, response.text)
            is AiResponse.Unavailable -> NotificationContent(pattern.title, pattern.detail)
        }
}
