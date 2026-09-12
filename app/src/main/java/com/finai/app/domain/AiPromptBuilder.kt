package com.finai.app.domain

import com.finai.app.data.ai.AiRequest
import com.finai.app.data.ai.AiTask
import com.finai.app.data.model.Budget
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import com.finai.app.data.model.Debt
import com.finai.app.data.model.Goal
import com.finai.app.data.model.Subscription
import com.finai.app.util.formatBrl0

/**
 * Turns numbers already computed locally (never raw entities or PII) into
 * the prompts sent to whatever [com.finai.app.data.ai.AiProvider] is
 * configured — the one place that decides "o que a IA recebe" per
 * planning.md §4/§6: aggregated results and short labels, no document, no
 * full name/CPF/conta (the data model doesn't carry those fields to begin
 * with).
 */
object AiPromptBuilder {

    private const val SYSTEM_BASE = "Você é o assistente financeiro do app FinAI. Responda sempre em português do Brasil, " +
        "de forma curta e direta, citando os números fornecidos abaixo. Nunca invente valores que não estejam no contexto. " +
        "Não recomende produtos financeiros de terceiros nem dê conselho de investimento específico."

    fun goalInsight(goal: Goal, monthlyCapacityLabel: String, otherActiveGoals: Int): AiRequest {
        val prompt = buildString {
            appendLine("Objetivo: \"${goal.name}\" (${goal.kind}).")
            appendLine("Guardado: ${formatBrl0(goal.saved)} de ${formatBrl0(goal.target)} (${(goal.progress * 100).toInt()}%).")
            appendLine("Previsão atual: ${goal.eta}.")
            appendLine("Status calculado localmente: ${goal.badge.label}.")
            appendLine("Capacidade de poupança mensal total do usuário: $monthlyCapacityLabel.")
            if (otherActiveGoals > 0) appendLine("Essa capacidade é dividida com outro(s) $otherActiveGoals objetivo(s) ativo(s).")
            append("Escreva de 1 a 2 frases curtas explicando a situação real desse objetivo específico e o que fazer a seguir.")
        }
        return AiRequest(AiTask.GOAL_INSIGHT, "$SYSTEM_BASE Você escreve a \"leitura da IA\" de um objetivo financeiro.", prompt)
    }

    fun purchaseVerdict(
        amountLabel: String,
        verdictLabel: String,
        monthlyCapacityLabel: String,
        slackAfterLabel: String,
        topGoalName: String?,
        topGoalAffected: Boolean,
    ): AiRequest {
        val prompt = buildString {
            appendLine("Simulação de compra de $amountLabel.")
            appendLine("Veredito já calculado localmente: \"$verdictLabel\".")
            appendLine("Capacidade de poupança mensal: $monthlyCapacityLabel.")
            appendLine("Folga do mês depois dessa compra: $slackAfterLabel.")
            if (topGoalName != null) {
                val effect = if (topGoalAffected) "o aporte deste mês para ele fica em risco" else "não é afetado por essa compra"
                appendLine("Objetivo prioritário: \"$topGoalName\" — $effect.")
            }
            append("Escreva de 1 a 2 frases explicando esse veredito para o usuário, direto ao ponto.")
        }
        return AiRequest(
            AiTask.PURCHASE_VERDICT,
            "$SYSTEM_BASE Você escreve a explicação do veredito de um simulador de compras (\"Posso comprar?\").",
            prompt,
        )
    }

    fun debtNegotiation(debts: List<Debt>): AiRequest {
        val top = debts.firstOrNull()
        val prompt = buildString {
            if (top == null) {
                append("O usuário não tem nenhuma dívida cadastrada no momento. Diga isso em 1 frase curta.")
            } else {
                appendLine("Dívida prioritária (maior custo de juro): \"${top.name}\".")
                appendLine("Valor em aberto: ${top.amount}. Taxa: ${top.rate} ao mês. ${top.meta}.")
                if (debts.size > 1) appendLine("Há outra(s) ${debts.size - 1} dívida(s) cadastrada(s), de menor prioridade por esse critério.")
                append(
                    "Escreva um roteiro de negociação por telefone para essa dívida específica, em exatamente 4 passos curtos " +
                        "e práticos (o que dizer sobre o relacionamento com o credor, o que pedir, uma alternativa de proposta " +
                        "caso recusem, e o que exigir por escrito antes de aceitar). Responda só com os 4 passos, um por linha, " +
                        "sem numeração e sem introdução.",
                )
            }
        }
        return AiRequest(AiTask.DEBT_NEGOTIATION, "$SYSTEM_BASE Você escreve um roteiro de negociação de dívida para uma ligação real.", prompt)
    }

    fun decisions(
        topDebt: Debt?,
        budgetsOver: List<Budget>,
        unusedSubscriptions: List<Subscription>,
        reassessGoals: List<Goal>,
        safeNote: String,
    ): AiRequest {
        val prompt = buildString {
            appendLine("Resumo financeiro do mês do usuário:")
            appendLine("- Folga do mês: $safeNote")
            topDebt?.let { appendLine("- Dívida de maior custo: \"${it.name}\" (${it.rate} ao mês, ${it.amount} em aberto).") }
            if (budgetsOver.isNotEmpty()) appendLine("- Categorias de orçamento estouradas: ${budgetsOver.joinToString { b -> b.name }}.")
            if (unusedSubscriptions.isNotEmpty()) {
                appendLine("- Assinaturas pouco usadas: ${unusedSubscriptions.joinToString { s -> s.name }}.")
            }
            if (reassessGoals.isNotEmpty()) appendLine("- Objetivos que precisam de atenção: ${reassessGoals.joinToString { g -> g.name }}.")
            if (topDebt == null && budgetsOver.isEmpty() && unusedSubscriptions.isEmpty() && reassessGoals.isEmpty()) {
                appendLine("- Nada fora do esperado neste momento.")
            }
            append(
                "Com base só nisso, escreva até 3 sugestões curtas e acionáveis para essa semana, uma por linha, sem numeração, " +
                    "sem introdução nem conclusão. Se não houver nada relevante, diga em 1 frase que está tudo sob controle.",
            )
        }
        return AiRequest(AiTask.DECISIONS, "$SYSTEM_BASE Você escreve as sugestões da seção \"Decisões para você\" da tela inicial.", prompt)
    }

    // ── Fase 4 · importação de fatura ────────────────────────────────────
    // Os três prompts abaixo recebem SÓ o que o parsing local já extraiu e
    // normalizou: nome do estabelecimento sem código de loja/final de cartão
    // (ver MerchantClassifier.normalizeMerchant), valor e dia/mês. O arquivo
    // original — PDF, planilha ou foto — nunca sai do aparelho (planning.md §4).

    /** [lines] já vem numerado e normalizado pelo chamador ([com.finai.app.data.ai.ImportAiAssistant]). */
    fun importCategories(lines: List<String>, categories: List<String>): AiRequest {
        val prompt = buildString {
            appendLine("Classifique cada lançamento de cartão abaixo em UMA destas categorias: ${categories.joinToString(", ")}.")
            appendLine("Responda uma linha por item, no formato \"numero | categoria\", sem explicação e sem texto extra.")
            appendLine("Se não der para saber, responda \"Outros\".")
            appendLine()
            lines.forEach { appendLine(it) }
        }
        return AiRequest(
            AiTask.IMPORT_CATEGORY,
            "$SYSTEM_BASE Você classifica lançamentos de fatura de cartão em categorias de orçamento. " +
                "Responda apenas as linhas pedidas, nada mais.",
            prompt,
        )
    }

    fun importRecurrences(lines: List<String>): AiRequest {
        val prompt = buildString {
            appendLine("Para cada lançamento abaixo, diga se ele parece uma assinatura/cobrança recorrente mensal.")
            appendLine("Responda uma linha por item, no formato \"numero | sim\" ou \"numero | nao\", sem explicação.")
            appendLine("Compra avulsa, parcela de compra e conta variável não são assinatura.")
            appendLine()
            lines.forEach { appendLine(it) }
        }
        return AiRequest(
            AiTask.IMPORT_RECURRENCE,
            "$SYSTEM_BASE Você identifica assinaturas recorrentes em lançamentos de fatura. Responda apenas as linhas pedidas.",
            prompt,
        )
    }

    fun importDuplicates(lines: List<String>): AiRequest {
        val prompt = buildString {
            appendLine("Cada item abaixo traz um lançamento novo e um lançamento já registrado com o mesmo valor.")
            appendLine("Diga se os dois são a MESMA compra lançada duas vezes.")
            appendLine("Responda uma linha por item, no formato \"numero | sim\" ou \"numero | nao\", sem explicação.")
            appendLine("Compras iguais em dias diferentes no mesmo estabelecimento podem ser compras distintas.")
            appendLine()
            lines.forEach { appendLine(it) }
        }
        return AiRequest(
            AiTask.IMPORT_DUPLICATE,
            "$SYSTEM_BASE Você decide se dois lançamentos de fatura são a mesma compra duplicada. Responda apenas as linhas pedidas.",
            prompt,
        )
    }

    // ── Fase 5 · notificações proativas e coach comportamental ────────────
    // Os dois prompts abaixo só entram em jogo depois que a detecção local já
    // decidiu que existe um evento (AlertCalculator/BehaviorCoach) — a IA
    // nunca decide se algo é relevante, só reescreve o texto que
    // FinanceCheckWorker já vai mostrar de qualquer forma (com o texto
    // determinístico do próprio alerta/padrão) se a IA estiver indisponível.

    /** [alerts] já foi filtrado para os que ainda não foram notificados hoje — ver `NotificationDedupeStore`. */
    fun proactiveAlerts(alerts: List<FinanceAlert>): AiRequest {
        val prompt = buildString {
            appendLine("Eventos financeiros detectados hoje, já calculados localmente (não invente outros nem valores diferentes):")
            alerts.forEach { appendLine("- ${it.title}: ${it.body}") }
            append(
                if (alerts.size == 1) {
                    "Escreva o texto de uma notificação push curta (1 frase, no máximo 140 caracteres) sobre esse evento. " +
                        "Direto ao ponto, sem saudação."
                } else {
                    "Escreva o texto de uma notificação push resumindo esses ${alerts.size} eventos em até 2 frases curtas " +
                        "(no máximo 200 caracteres no total), priorizando o mais urgente primeiro. Sem saudação, sem lista."
                },
            )
        }
        return AiRequest(
            AiTask.PROACTIVE_ALERT,
            "$SYSTEM_BASE Você escreve o texto de uma notificação push proativa sobre eventos financeiros já detectados.",
            prompt,
        )
    }

    fun behaviorCoach(pattern: BehaviorPattern): AiRequest {
        val prompt = buildString {
            appendLine("Padrão de comportamento de gasto detectado localmente: \"${pattern.title}\".")
            appendLine("Fatos já calculados: ${pattern.detail}")
            append(
                "Escreva de 1 a 2 frases curtas comentando esse padrão para o usuário, em tom de observação (não de bronca), " +
                    "com uma sugestão prática opcional. Não invente números além dos fornecidos.",
            )
        }
        return AiRequest(
            AiTask.BEHAVIOR_COACH,
            "$SYSTEM_BASE Você é o \"coach de comportamento\" do app: comenta um padrão de gasto já identificado, nunca decide se ele existe.",
            prompt,
        )
    }

    fun chat(financeSummary: String, history: List<ChatMessage>, question: String): AiRequest {
        val historyText = history.takeLast(8).joinToString("\n") { m ->
            (if (m.role == ChatRole.Me) "Usuário" else "FinAI") + ": " + m.text
        }
        val prompt = buildString {
            appendLine("Resumo financeiro atual do usuário:")
            append(financeSummary)
            if (historyText.isNotBlank()) {
                appendLine()
                appendLine("Conversa até agora:")
                appendLine(historyText)
            }
            appendLine()
            append("Pergunta nova do usuário: $question")
        }
        return AiRequest(
            AiTask.CHAT,
            "$SYSTEM_BASE Você é o assistente de chat livre do app, respondendo perguntas sobre as finanças do usuário e " +
                "citando os números do resumo quando fizer sentido.",
            prompt,
        )
    }
}
