package com.finai.app.domain

import com.finai.app.data.model.Debt
import com.finai.app.data.model.Goal
import com.finai.app.util.formatBrl0

/**
 * O que um botão de "falar com a IA" carrega: a pergunta que aparece no chat
 * como se o usuário tivesse escrito, e o contexto invisível do card de origem,
 * que vai no prompt da conversa inteira ([AiPromptBuilder.chat]). Sem isso o
 * botão era só um atalho de navegação — abria o chat vazio.
 *
 * Mesma regra de privacidade dos prompts (planning.md §4): só números já
 * calculados e rótulos que o usuário vê na tela.
 */
data class AssistantTopic(val question: String, val context: String)

object AssistantTopics {

    fun behaviorPattern(pattern: BehaviorPattern) = AssistantTopic(
        question = "Quero entender melhor este padrão: ${pattern.title.trimEnd('.')}.",
        context = buildString {
            appendLine("Cartão \"Padrão de gasto do mês\" da tela Início.")
            appendLine("Padrão detectado localmente: ${pattern.title}")
            append("Fatos calculados: ${pattern.detail}")
            appendLine()
            append("Explique por que isso acontece e sugira uma mudança concreta e pequena para as próximas semanas.")
        },
    )

    fun goal(goal: Goal) = AssistantTopic(
        question = "Quero simular caminhos para chegar em \"${goal.name}\".",
        context = buildString {
            appendLine("Objetivo \"${goal.name}\" (${goal.kind}), aberto pelo botão \"Simular\" da tela Objetivos.")
            if (goal.description.isNotBlank()) appendLine("Descrição do usuário: ${goal.description.trim()}")
            appendLine("Guardado: ${formatBrl0(goal.saved)} de ${formatBrl0(goal.target)}. Previsão atual: ${goal.eta}.")
            appendLine("Situação calculada: ${goal.badge.label}. ${goal.note}")
            if (goal.planNote.isNotBlank()) appendLine("No plano do app: ${goal.planNote}")
            if (goal.projectionNote.isNotBlank()) appendLine(goal.projectionNote)
            append(
                "Julgue pela sobra projetada até o prazo. Compare dois ou três cenários (prazo diferente, meta ajustada, " +
                    "mudar a ordem de prioridade) e diga qual faz mais sentido considerando a descrição. Os cenários partem do " +
                    "plano: se a meta receber mais agora, diga de qual recomendação do plano o dinheiro sairia.",
            )
        },
    )

    fun debtCall(debts: List<Debt>): AssistantTopic {
        val top = debts.firstOrNull()
            ?: return AssistantTopic("Como me preparar para negociar uma dívida?", "O usuário não tem dívida cadastrada.")
        return AssistantTopic(
            question = "Vamos ensaiar a ligação para negociar \"${top.name}\".",
            context = buildString {
                appendLine("Botão \"Ensaiar a ligação com a IA\" da tela Dívidas.")
                appendLine("Dívida: \"${top.name}\", ${top.amount} em aberto, juros de ${top.rate} ao mês. ${top.meta}.")
                if (debts.size > 1) appendLine("Há outras ${debts.size - 1} dívidas, de menor custo de juro.")
                append(
                    "Faça o papel do atendente do credor. Comece a ligação como o atendente e espere a fala do usuário. " +
                        "A cada resposta dele, continue no papel e, numa linha à parte começando com \"Dica:\", diga o que ele " +
                        "poderia ter dito melhor.",
                )
            },
        )
    }

    fun purchase(
        amountLabel: String,
        verdictLabel: String,
        monthlyCapacityLabel: String,
        slackAfterLabel: String,
        topGoalName: String?,
        topGoalAffected: Boolean,
    ) = AssistantTopic(
        // "à vista agora" deixa a pergunta completa para o simulador de cenários do chat
        // (Pix/débito hoje), sem precisar perguntar a forma de pagamento de volta.
        question = "Posso fazer uma compra de $amountLabel à vista agora?",
        context = buildString {
            appendLine("Simulador \"Posso comprar?\".")
            appendLine("Valor: $amountLabel. Veredito calculado localmente: \"$verdictLabel\".")
            appendLine("Livre até o salário: $monthlyCapacityLabel. Livre depois da compra: $slackAfterLabel.")
            if (topGoalName != null) {
                appendLine("Recomendação do plano para o livre: \"$topGoalName\" — " + if (topGoalAffected) "sobra menos para ela." else "não muda.")
            }
            append("Explique o veredito e diga como a compra poderia caber (esperar, parcelar sem juros, cortar em outro lugar).")
        },
    )

    fun decision(decision: String): AssistantTopic {
        // A pergunta aparece no balão do usuário, que não formata Markdown.
        val plain = AiReplyFormat.plain(decision).replace('\n', ' ').trimEnd('.')
        return AssistantTopic(
            question = "Me ajuda com isto: $plain.",
            context = "Sugestão da seção \"Decisões para você\" da tela Início: \"$plain\". " +
                "Explique o impacto de seguir essa sugestão e o primeiro passo concreto.",
        )
    }
}
