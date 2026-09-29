package com.finai.app.domain

import com.finai.app.data.ai.AiRequest
import com.finai.app.data.ai.AiTask
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import com.finai.app.data.model.Debt
import com.finai.app.data.model.Goal
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

    // As regras de formato existem porque a resposta é desenhada por
    // AiReplyFormat: negrito e valores em R$ ganham destaque, o resto do
    // Markdown é descartado. Pedir poucos números é regra de produto — a tela
    // já mostra os valores; a IA escolhe o que muda a decisão e interpreta.
    private const val SYSTEM_BASE = "Você é o assistente financeiro do app FinAI. Responda sempre em português do Brasil, " +
        "de forma curta e direta. Texto simples: nada de títulos (#), tabelas ou blocos de código; use **negrito** " +
        "em no máximo dois trechos; use lista com \"- \" só para passos. Escreva valores como R$ 1.234 (negativos como -R$ 1.234). " +
        "Cite no máximo dois valores, os que mais pesam na decisão; não repita números só para descrevê-los — interprete e " +
        "diga o que fazer. Nunca invente valores que não estejam no contexto. " +
        "Prioridades e a recomendação para o dinheiro livre já foram decididas pelo app: explique, não decida de novo. " +
        "Não recomende produtos financeiros de terceiros nem dê conselho de investimento específico."

    /** Rótulos das três linhas da leitura do objetivo, na ordem em que a tela mostra. */
    val GOAL_INSIGHT_LABELS = listOf("Agora", "Próximo passo", "Risco")

    /** Quantas metas vão numa leitura da IA: a resposta cabe no limite de saída dos provedores. */
    const val GOAL_INSIGHT_MAX_GOALS = 4

    /**
     * A leitura da IA de todas as metas numa chamada só (antes era uma por meta, cada uma
     * repetindo o bloco de todas as metas). Recebe o plano central ([FinancialPlan.aiBlock]):
     * a IA explica o que o app decidiu para cada meta, não decide de novo para onde vai a sobra.
     * A resposta vem em seções "[n]", lidas por [AiReplyFormat.numberedSections].
     */
    fun goalInsights(goals: List<Goal>, planBlock: String): AiRequest {
        val prompt = buildString {
            appendLine(planBlock)
            appendLine()
            appendLine("Metas:")
            goals.forEachIndexed { i, goal ->
                append("[${i + 1}] \"${goal.name}\" (${goal.kind}): guardado ${formatBrl0(goal.saved)} de ${formatBrl0(goal.target)}, ")
                append("prazo ${goal.eta}, situação no prazo: ${goal.badge.label}. ${goal.note}")
                if (goal.planNote.isNotBlank()) append(" ${goal.planNote}")
                if (goal.description.isNotBlank()) append(" Descrição do usuário: ${goal.description.trim()}")
                appendLine()
            }
            appendLine()
            appendLine("O usuário já vê os valores na tela: não os repita. O próximo passo segue a recomendação do plano: não sugira aporte agora para meta que o plano não recomenda.")
            appendLine("Para cada meta, responda com a marca [n] numa linha e depois exatamente 3 linhas curtas (uma frase cada):")
            appendLine("Agora: o que a situação significa para esta meta.")
            appendLine("Próximo passo: uma ação concreta coerente com o plano.")
            append("Risco: o que pode atrasar, ou \"nenhum relevante\".")
        }
        return AiRequest(AiTask.GOAL_INSIGHT, "$SYSTEM_BASE Você escreve a \"leitura da IA\" dos objetivos financeiros.", prompt)
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
            appendLine("Livre até o salário, segundo o plano do app: $monthlyCapacityLabel.")
            appendLine("Livre depois dessa compra: $slackAfterLabel.")
            if (topGoalName != null) {
                val effect = if (topGoalAffected) "sobra menos do que o plano recomenda para ele" else "a recomendação não muda"
                appendLine("Recomendação do plano para o livre: \"$topGoalName\" — $effect.")
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

    /**
     * Pergunta de compra que o app já simulou ([com.finai.app.domain.scenario.ScenarioSimulator]).
     * O prompt leva só o resultado da simulação: nenhum lançamento, nenhum nome de conta. A IA
     * explica e resume; qualquer valor ou data fora de [facts] faz o app descartar a resposta
     * ([com.finai.app.domain.scenario.ScenarioReplyGuard]) e mostrar o texto calculado.
     */
    fun purchaseScenario(facts: String, question: String): AiRequest {
        val prompt = buildString {
            appendLine(facts)
            appendLine()
            appendLine("Pergunta do usuário: $question")
            appendLine()
            appendLine("Responda usando só os valores, datas e conclusões acima. Não calcule nada: não some, não subtraia, não divida, não arredonde de outro jeito.")
            appendLine("Comece com um ou dois destaques no formato {{rótulo curto|R$ 1.234}}, copiando valores do bloco.")
            appendLine("Depois, em até 4 frases: o veredito, se as contas continuam cobertas, o período mais apertado e, se houver comparação, qual opção é melhor e por quê.")
            append("Se o usuário perguntar algo que o bloco não responde, diga que o app não calculou isso. Termine dizendo que nada foi lançado.")
        }
        return AiRequest(
            AiTask.PURCHASE_SCENARIO,
            "$SYSTEM_BASE Você explica o resultado de uma simulação de compra feita pelo app. " +
                "Os números e o veredito são do app; você não faz contas nem muda conclusões.",
            prompt,
        )
    }

    /**
     * [history] é só a conversa atual — cada conversa começa limpa, para a IA
     * não misturar o assunto (nem os números) de conversas de outros dias.
     * [topicContext] vem do botão que abriu a conversa ("Conversar sobre
     * isso", "Ensaiar a ligação"...): o usuário vê só a pergunta, a IA recebe
     * também os dados do card de origem.
     */
    fun chat(financeSummary: String, history: List<ChatMessage>, question: String, topicContext: String? = null): AiRequest {
        val historyText = history.takeLast(8).joinToString("\n") { m ->
            (if (m.role == ChatRole.Me) "Usuário" else "FinAI") + ": " + AiReplyFormat.plain(m.text)
        }
        val prompt = buildString {
            if (!topicContext.isNullOrBlank()) {
                appendLine("Assunto desta conversa (o usuário abriu o chat a partir deste ponto do app):")
                appendLine(topicContext.trim())
                appendLine()
            }
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
            "$SYSTEM_BASE Você é o assistente de chat do app e responde perguntas sobre as finanças do usuário. " +
                "Cumprimento ou conversa sem pergunta financeira: responda em 1 ou 2 frases, sem citar nenhum valor, e " +
                "ofereça ajuda com gastos, dívidas ou objetivos. " +
                "Quando a resposta girar em torno de valores, comece com um ou dois destaques, cada um numa linha, no formato " +
                "{{rótulo curto|R$ 1.234}} (rótulo de até 3 palavras), e depois explique em até 4 frases o que eles significam " +
                "e qual a próxima ação, sem repetir os valores dos destaques. " +
                "Os números do resumo são os de agora; se a conversa anterior citar valores diferentes, valem os do resumo. " +
                "Se o usuário perguntar se pode comprar ou parcelar algo, não calcule o impacto: peça o valor, a forma de " +
                "pagamento e a data da primeira parcela, para o app simular.",
            prompt,
        )
    }
}
