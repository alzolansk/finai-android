package com.finai.app.domain.assistant

import com.finai.app.domain.quickentry.QuickDraft
import com.finai.app.domain.quickentry.QuickEntryParser

/**
 * Confere a explicação da IA contra os fatos que o app calculou (§3.4). Mais estrita que a trava
 * antiga: compara **centavos** quando a resposta os escreve, o **sinal** e o **ano** das datas
 * completas. Não valida a conclusão — por isso o veredito, os valores e as datas principais vêm
 * do cartão montado pelo app, e não deste texto.
 */
object FactGuard {
    private val money = Regex("""(-\s*)?R\$\s*(-\s*)?(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{2}))?""")
    private val date = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{4}))?\b""")

    private data class Amount(val reais: Long, val cents: Int?, val negative: Boolean)

    private fun amounts(text: String) = money.findAll(text).map { m ->
        Amount(m.groupValues[3].replace(".", "").toLong(), m.groupValues[4].toIntOrNull(), m.groupValues[1].isNotBlank() || m.groupValues[2].isNotBlank())
    }.toList()

    fun inventedIn(reply: String, facts: String): List<String> {
        val known = amounts(facts)
        val wrongMoney = amounts(reply).filter { r ->
            known.none { k ->
                val sameCents = r.cents == null || (k.cents ?: 0) == r.cents
                // Sem centavos na resposta: vale o valor inteiro ou o arredondado do fato.
                val sameReais = k.reais == r.reais || (r.cents == null && k.cents != null && k.cents >= 50 && k.reais + 1 == r.reais)
                sameReais && sameCents && (!r.negative || k.negative)
            }
        }.map { "R$ ${it.reais}" + (it.cents?.let { c -> ",%02d".format(c) } ?: "") }
        val knownDates = date.findAll(facts).map { Triple(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toIntOrNull()) }.toList()
        val wrongDates = date.findAll(reply).map { Triple(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toIntOrNull()) }.filter { r ->
            knownDates.none { k -> k.first == r.first && k.second == r.second && (r.third == null || k.third == null || k.third == r.third) }
        }.map { "%02d/%02d".format(it.first, it.second) + (it.third?.let { y -> "/$y" } ?: "") }.toList()
        return wrongMoney + wrongDates
    }

    fun accepts(reply: String, facts: String): Boolean = reply.isNotBlank() && inventedIn(reply, facts).isEmpty()
}

/** Resultado de uma escrita pedida ao executor. */
data class ExecResult(val status: ActionStatus, val actionId: Long? = null, val message: String = "", val summary: String = "")

/** Quem grava de fato (transação no Room, idempotência, recibo). O domínio só fala com esta interface. */
interface ActionExecutor {
    /** Aplica as linhas de [plan] se o banco ainda estiver como o retrato; [key] identifica a operação autorizada. */
    suspend fun apply(plan: ActionPlan, key: String, conversationId: Long): ExecResult

    /** Desfaz pelo recibo; [actionId] nulo = a última ação aplicada na conversa. */
    suspend fun undo(conversationId: Long, actionId: Long?): ExecResult
}

interface SessionStore {
    suspend fun load(conversationId: Long): AssistantSession
    suspend fun save(conversationId: Long, session: AssistantSession)
}

/** Uma mensagem do usuário a responder. [messageId] é o id gravado dela: a autorização da escrita é dessa mensagem. */
data class Turn(val conversationId: Long, val messageId: Long, val text: String)

/** O que o chat mostra: o texto (histórico e modo sem cartão), o cartão e, se for o caso, o rascunho para o formulário. */
data class AssistantReply(
    val text: String,
    val answer: AssistantAnswer? = null,
    val consulted: List<String> = emptyList(),
    /** Lançamento que precisa do formulário (cartão, categoria faltando): o cartão "Lançamento pronto" de sempre. */
    val pendingEntry: QuickDraft? = null,
)

/**
 * O orquestrador da conversa (§5.4), sem Compose nem Room:
 *
 * 1. retoma o que está pendente (confirmação, escolha de alvo, dado que faltava);
 * 2. tenta as regras locais de ação e de consulta — sem IA, sem cota;
 * 3. senão, deixa a IA escolher ferramentas do catálogo (consulta ou ação), em no máximo
 *    [AssistantTools.MAX_CALLS] rodadas, e confere a explicação contra os fatos;
 * 4. qualquer escrita passa por [ActionPlanner] (alvo e alcance), [Authorization] (quem pediu) e
 *    [ActionExecutor] (revalida, grava numa transação, guarda o recibo).
 *
 * [ask] devolve o texto da IA ou nulo quando ela está indisponível (o motivo vem em [unavailable]).
 */
class AssistantOrchestrator(
    private val snapshot: suspend () -> AssistantSnapshot,
    private val executor: ActionExecutor,
    private val sessions: SessionStore,
    private val ask: suspend (results: List<ToolResult>, focus: List<EntityRef>) -> String?,
    private val unavailable: () -> String? = { null },
) {
    suspend fun handle(turn: Turn): AssistantReply {
        val s = snapshot()
        val session = sessions.load(turn.conversationId)
        val kind = Authorization.classify(turn.text)

        // 1. O que estava pendente.
        session.pending?.let { pending ->
            resume(turn, s, session, pending, kind)?.let { return it }
            // O usuário mudou de assunto: a proposta não vale mais.
            sessions.save(turn.conversationId, session.copy(pending = null))
        }
        val current = session.copy(pending = null)

        // 2. Regras locais.
        if (kind != Authorization.Kind.Hypothetical) {
            IntentParser.action(turn.text, s, current)?.let { request ->
                return act(turn, s, current, request, direct = true, messageId = turn.messageId)
            }
        }
        IntentParser.query(turn.text, s, current)?.let { call ->
            val result = AssistantTools.run(call, s)
            sessions.save(turn.conversationId, current.withFocus(result.focus).copy(lastScenario = call.takeIf { it.name == "simular_cenario" } ?: current.lastScenario))
            return AssistantReply(result.answer?.plainText() ?: result.facts, result.answer, listOf(result.label))
        }

        // 3. A IA escolhe a ferramenta.
        val consulted = mutableListOf<ToolResult>()
        var focus = current
        var text = ask(consulted, focus.focus) ?: return offline()
        while (true) {
            ActionPlanner.parseCall(text)?.let { request ->
                // A IA só propõe: roda direto apenas se a frase do usuário é uma ordem ou afirmação
                // clara e os valores são os que ele disse; senão vira proposta com confirmação.
                val direct = kind == Authorization.Kind.Explicit && Authorization.valuesComeFrom(request, turn.text, s.today)
                return act(turn, s, focus, request, direct, turn.messageId)
            }
            val call = AssistantTools.parseCall(text) ?: break
            if (consulted.size >= AssistantTools.MAX_CALLS) break
            val result = AssistantTools.run(call, s)
            consulted += result
            focus = focus.withFocus(result.focus).copy(lastScenario = call.takeIf { it.name == "simular_cenario" } ?: focus.lastScenario)
            text = ask(consulted.toList(), focus.focus) ?: break
        }
        sessions.save(turn.conversationId, focus)
        if (consulted.isEmpty()) {
            return if (AssistantTools.parseCall(text) == null && ActionPlanner.parseCall(text) == null) AssistantReply(text.trim()) else offline()
        }
        // Com consulta: o cartão vem do app; a IA só entra como explicação, se não inventou número.
        val facts = consulted.joinToString("\n") { it.facts }
        val last = consulted.last()
        val explained = text.trim().takeIf { AssistantTools.parseCall(it) == null && FactGuard.accepts(it, facts) }
        val answer = last.answer?.let { if (explained != null) it.copy(explanation = explained) else it }
        return AssistantReply(
            text = explained ?: answer?.plainText() ?: consulted.joinToString("\n") { it.facts.trim() },
            answer = answer,
            consulted = consulted.map { it.label },
        )
    }

    private fun offline(): AssistantReply {
        val why = unavailable()?.let { " ($it)" }.orEmpty()
        return AssistantReply(
            "Não consegui falar com a IA agora$why. Sem ela eu ainda respondo sozinho: sua situação, gastos por categoria ou lugar, contas a pagar, projeção dos próximos meses, " +
                "quando cabe pagar algo e cenários (\"e se…\"). Também registro lançamentos, pagamentos, aportes e o salário recebido.",
            AssistantAnswer(AnswerKind.Question, "IA indisponível", headline = "Posso responder sem ela:", options = listOf(
                AnswerOption("Minha situação", "Qual é a minha situação hoje?"), AnswerOption("O que vence", "O que vence nos próximos dias?"),
                AnswerOption("Gastos do mês", "Quanto gastei este mês?"), AnswerOption("Próximos meses", "Como fico nos próximos meses?"),
            )),
        )
    }

    /** Resposta a uma proposta ou pergunta pendente; nulo = a mensagem não é resposta a ela. */
    private suspend fun resume(turn: Turn, s: AssistantSnapshot, session: AssistantSession, pending: PendingAction, kind: Authorization.Kind): AssistantReply? {
        val f = Fmt.fold(turn.text)
        if (kind == Authorization.Kind.Deny) {
            sessions.save(turn.conversationId, session.copy(pending = null))
            return AssistantReply("Certo, não alterei nada.", AssistantAnswer(AnswerKind.Action, "Cancelado", headline = "Não alterei nada."))
        }
        if (pending.kind == PendingAction.CONFIRM) {
            if (kind != Authorization.Kind.Affirm) return null
            return act(turn, s, session, pending.request, direct = true, messageId = pending.messageId, confirmed = true)
        }
        // Escolha entre as opções: pelo número, pelo rótulo ou por uma palavra que só uma opção tem.
        val options = pending.options.filter { it.label != AWAITING }
        if (options.isNotEmpty()) {
            val byIndex = Regex("""^(\d)\b""").find(f)?.groupValues?.get(1)?.toIntOrNull()?.let { options.getOrNull(it - 1) }
            val byLabel = options.filter { Fmt.fold(it.label) == f || Fmt.fold(it.label).startsWith(f) || f.startsWith(Fmt.fold(it.label).substringBefore(" · ")) }.singleOrNull()
            val words = f.split(Regex("""[^a-z0-9]+""")).filter { it.length >= 3 }
            val byWord = options.filter { o -> val l = Fmt.fold(o.label); words.any { Regex("""\b${Regex.escape(it)}""").containsMatchIn(l) } }.singleOrNull()
            (byIndex ?: byLabel ?: byWord)?.let { choice ->
                return act(turn, s, session, pending.request.copy(args = choice.args), direct = true, messageId = pending.messageId)
            }
        }
        // Pergunta aberta ("qual o valor?"): a mensagem inteira é a resposta, se for curta e não for outro pedido.
        val awaiting = pending.options.firstOrNull { it.label == AWAITING }?.args?.get(AWAITING)
        if (awaiting != null && f.length <= 60 && kind != Authorization.Kind.Hypothetical && IntentParser.action(turn.text, s, session.copy(pending = null))?.name.let { it == null || it == ActionPlanner.CREATE_ENTRY }) {
            return act(turn, s, session, pending.request.copy(args = pending.request.args + (awaiting to turn.text.trim())), direct = true, messageId = pending.messageId)
        }
        return null
    }

    private suspend fun act(
        turn: Turn, s: AssistantSnapshot, session: AssistantSession, request: ActionRequest, direct: Boolean, messageId: Long, confirmed: Boolean = false,
    ): AssistantReply {
        if (request.name == ActionPlanner.UNDO) return undo(turn, session)
        val plan = runCatching { ActionPlanner.plan(request, s, confirmed) }.getOrElse {
            return AssistantReply("Não consegui preparar essa alteração: ${it.message ?: "dado inesperado"}. Nada foi gravado.",
                AssistantAnswer(AnswerKind.Action, "Não alterei nada", headline = it.message ?: "Dado inesperado.", action = ActionInfo(ActionStatus.Failed)))
        }
        fun pendingOf(kind: String, options: List<PendingOption>) = PendingAction(plan.request, kind, options, messageId, s.id)
        return when (plan.state) {
            PlanState.Unsupported -> {
                // Lançamento que precisa do formulário (cartão, categoria): o cartão "Lançamento pronto".
                if (request.name == ActionPlanner.CREATE_ENTRY && plan.summary == "form") {
                    val draft = QuickEntryParser.parse(request.args["frase"].orEmpty(), s.today, s.quick)
                    sessions.save(turn.conversationId, session.copy(pending = null))
                    return AssistantReply(QuickEntryParser.chatReply(draft, s.today), pendingEntry = draft)
                }
                sessions.save(turn.conversationId, session.copy(pending = null))
                AssistantReply(plan.summary, AssistantAnswer(AnswerKind.Action, plan.title, headline = "Não alterei nada.", notes = listOf(plan.summary), action = ActionInfo(ActionStatus.Unsupported)))
            }
            PlanState.Already -> {
                sessions.save(turn.conversationId, session.copy(pending = null).withFocus(plan.focus))
                AssistantReply(plan.summary, AssistantAnswer(AnswerKind.Action, plan.title, headline = plan.summary, action = ActionInfo(ActionStatus.AlreadyApplied, agendaDate = plan.agendaDate)))
            }
            PlanState.Clarify -> {
                val options = plan.options + listOfNotNull(plan.awaiting?.let { PendingOption(AWAITING, mapOf(AWAITING to it)) })
                sessions.save(turn.conversationId, session.copy(pending = pendingOf(PendingAction.CLARIFY, options)))
                AssistantReply(
                    plan.question + if (plan.options.isEmpty()) "" else "\n" + plan.options.mapIndexed { i, o -> "${i + 1}. ${o.label}" }.joinToString("\n"),
                    AssistantAnswer(AnswerKind.Question, plan.title, headline = plan.question, options = plan.options.map { AnswerOption(it.label) }, action = ActionInfo(ActionStatus.NeedsClarification)),
                )
            }
            PlanState.Confirm -> propose(turn, session, plan, pendingOf(PendingAction.CONFIRM, emptyList()), plan.question.ifBlank { "Confirma?" })
            PlanState.Ready -> {
                if (!direct) return propose(turn, session, plan, pendingOf(PendingAction.CONFIRM, emptyList()), "Quer que eu aplique?")
                val result = executor.apply(plan, "${turn.conversationId}:$messageId:${plan.request.name}:${plan.request.args.toSortedMap()}", turn.conversationId)
                when (result.status) {
                    ActionStatus.Applied, ActionStatus.AlreadyApplied -> {
                        sessions.save(turn.conversationId, session.copy(pending = null, lastActionId = result.actionId ?: session.lastActionId).withFocus(plan.focus))
                        val again = result.status == ActionStatus.AlreadyApplied
                        AssistantReply(
                            if (again) "Isso já tinha sido aplicado; não gravei de novo. ${plan.summary}" else plan.summary,
                            AssistantAnswer(AnswerKind.Action, plan.title, headline = plan.summary, evidence = plan.preview, notes = plan.notes,
                                action = ActionInfo(result.status, result.actionId, canUndo = result.actionId != null, agendaDate = plan.agendaDate)),
                        )
                    }
                    else -> {
                        sessions.save(turn.conversationId, session.copy(pending = null))
                        val why = result.message.ifBlank { "Os dados mudaram enquanto eu preparava a alteração." }
                        AssistantReply("$why Nada foi gravado. Peça de novo para eu refazer com os dados atuais.",
                            AssistantAnswer(AnswerKind.Action, plan.title, headline = "Nada foi gravado.", notes = listOf(why), action = ActionInfo(result.status)))
                    }
                }
            }
        }
    }

    private suspend fun propose(turn: Turn, session: AssistantSession, plan: ActionPlan, pending: PendingAction, question: String): AssistantReply {
        sessions.save(turn.conversationId, session.copy(pending = pending).withFocus(plan.focus))
        return AssistantReply(
            (plan.preview + question).joinToString("\n"),
            AssistantAnswer(AnswerKind.Action, plan.title, headline = question, evidence = plan.preview, notes = plan.notes + "Nada foi gravado ainda.",
                options = listOf(AnswerOption("Confirmar", "Sim, pode aplicar"), AnswerOption("Cancelar", "Não, deixa")), action = ActionInfo(ActionStatus.NeedsConfirmation)),
        )
    }

    suspend fun undo(turn: Turn, session: AssistantSession, actionId: Long? = null): AssistantReply {
        val result = executor.undo(turn.conversationId, actionId)
        sessions.save(turn.conversationId, session.copy(pending = null, lastActionId = null))
        return when (result.status) {
            ActionStatus.Undone -> AssistantReply("Desfeito: ${result.summary.ifBlank { "a última alteração" }.replaceFirstChar { it.lowercase() }} Os registros voltaram a como estavam.",
                AssistantAnswer(AnswerKind.Action, "Desfeito", headline = "Os registros voltaram a como estavam.", action = ActionInfo(ActionStatus.Undone, result.actionId)))
            ActionStatus.Conflict -> AssistantReply("Não desfiz: ${result.message} Nada mudou.",
                AssistantAnswer(AnswerKind.Action, "Não foi possível desfazer", headline = result.message, action = ActionInfo(ActionStatus.Conflict, result.actionId)))
            else -> AssistantReply(result.message.ifBlank { "Não há alteração minha para desfazer nesta conversa." },
                AssistantAnswer(AnswerKind.Action, "Nada a desfazer", headline = result.message.ifBlank { "Não há alteração minha para desfazer nesta conversa." }, action = ActionInfo(ActionStatus.Unsupported)))
        }
    }

    companion object {
        /** Marca, entre as opções pendentes, a chave do argumento que a próxima mensagem preenche. */
        const val AWAITING = "__awaiting"
    }
}
