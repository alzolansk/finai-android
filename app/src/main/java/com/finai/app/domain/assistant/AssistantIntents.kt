package com.finai.app.domain.assistant

import com.finai.app.data.model.Categorias
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.TransactionType
import com.finai.app.domain.importer.MerchantClassifier
import com.finai.app.domain.isRecurringSeries
import com.finai.app.domain.quickentry.QuickEntryParser
import com.finai.app.domain.scenario.PurchaseIntentParser
import java.time.LocalDate

/**
 * Quem autoriza uma escrita é a mensagem do usuário, lida aqui pelo app (§5.8/§5.10). A IA nunca
 * se dá permissão: ela só propõe a ferramenta; se a frase é pergunta, hipótese ou sugestão aberta,
 * nenhuma escrita acontece sem um sim a uma proposta concreta.
 */
object Authorization {
    enum class Kind {
        /** Afirmação ou ordem direta: "recebi", "paguei", "registre", "mude". */
        Explicit,
        /** Pergunta, "se eu…", "talvez", "o que você acha", "faça o que achar melhor". */
        Hypothetical,
        /** "Sim", "pode", "aplique": só vale para a proposta que está na tela. */
        Affirm,
        Deny,
    }

    private val HYPOTHETICAL_START = Regex(
        """^(e\s+)?(se\s|caso\s|talvez|sera\s+que|acho\s+que|posso\s|devo\s|deveria|da\s+(pra|para)\s|consigo\s|vale\s+a\s+pena|como\s+fic|quanto\s|quantos\s|quando\s|qual\s|quais\s|o\s+que\s|por\s*que|porque\s|simul|quero\s+saber|queria\s+saber|me\s+explic|explique|explica|compar|mostr|list|sera\s|e\s+se\s|imagina|suponha|estou\s+pensando|to\s+pensando|pensando\s+em)""",
    )
    private val HYPOTHETICAL_ANYWHERE = Regex("""\b(talvez|se\s+eu|o\s+que\s+(voce|vc)\s+acha|(faca|faz)\s+o\s+que\s+(voce\s+)?achar\s+melhor|o\s+que\s+for\s+melhor|como\s+(voce|vc)\s+preferir|nao\s+sei\s+se)\b""")
    private val AFFIRM = Regex("""^(sim|isso|isso\s+mesmo|confirmo|confirma|confirmar|confirmado|pode|pode\s+sim|pode\s+fazer|pode\s+aplicar|pode\s+lancar|ok|okay|claro|aplique|aplica|aplicar|faca|faz|manda|manda\s+ver|exato|com\s+certeza|quero|quero\s+sim|salva|salvar|salve|fechado|beleza|certo|s)\b[\s\p{Punct}]*""")
    private val DENY = Regex("""^(nao|n|cancela|cancelar|cancele|deixa|deixa\s+pra\s+la|esquece|nao\s+precisa|para|melhor\s+nao|agora\s+nao)\b""")

    fun classify(text: String): Kind {
        val f = Fmt.fold(text)
        val words = f.split(Regex("""\s+""")).size
        // "Faça o que achar melhor" começa como um "faça", mas é delegar, não confirmar: nunca autoriza.
        if (HYPOTHETICAL_ANYWHERE.containsMatchIn(f)) return Kind.Hypothetical
        if (words <= 6 && AFFIRM.containsMatchIn(f) && !f.contains("?")) return Kind.Affirm
        if (words <= 6 && DENY.containsMatchIn(f) && !f.contains("?")) return Kind.Deny
        if (f.contains("?") || HYPOTHETICAL_START.containsMatchIn(f) || HYPOTHETICAL_ANYWHERE.containsMatchIn(f)) return Kind.Hypothetical
        return Kind.Explicit
    }

    /**
     * Uma ação proposta pela IA só roda direto se cada valor em dinheiro dela foi dito pelo
     * usuário nesta mensagem: o pedido não pode crescer no caminho.
     */
    fun valuesComeFrom(request: ActionRequest, userText: String, today: LocalDate): Boolean {
        val said = Regex("""\d[\d.,]*""").findAll(Fmt.fold(userText)).mapNotNull { QuickEntryParser.parse("x ${it.value}", today).cents }.toSet()
        val asked = listOfNotNull(request.args["valor"], request.args["aporte"]).mapNotNull { QuickEntryParser.parse("x $it", today).cents }
        val phrase = request.args["frase"]?.let { QuickEntryParser.parse(it, today).cents }
        return (asked + listOfNotNull(phrase)).all { it in said }
    }
}

/**
 * Linguagem → pedido, por regra local e sem IA, para o que é frequente e conhecido (§5.4). O que
 * não casa aqui vai para a IA escolher a ferramenta; nada é perdido por não ter regra.
 */
object IntentParser {
    private fun has(f: String, pattern: String) = Regex(pattern).containsMatchIn(f)
    private fun amount(text: String, today: LocalDate): Long? =
        // Sem as datas ("14/11", "dia 10") e sem "em 3x", que não são dinheiro.
        Fmt.fold(text).replace(Regex("""\b\d{1,2}/\d{1,2}(/\d{2,4})?\b|\bdia\s+\d{1,2}\b|\b\d{1,2}\s*(x|vezes|parcelas|meses|mes|dias|%)"""), " ")
            .let { cleaned -> Regex("""(r\$\s*)?\d[\d.,]*\s*(mil|k)?""").find(cleaned)?.value }?.let { QuickEntryParser.parse("x $it", today).cents }

    private fun dateWord(f: String): String? = when {
        has(f, """\banteontem\b""") -> "anteontem"
        has(f, """\bontem\b""") -> "ontem"
        has(f, """\b(hoje|agora|acabou de|acabei de)\b""") -> "hoje"
        else -> Regex("""\b\d{1,2}/\d{1,2}(/\d{2,4})?\b""").find(f)?.value
    }

    private fun category(f: String): String? = Categorias.all.firstOrNull { has(f, """\b${Regex.escape(Fmt.fold(it))}\b""") }

    private val FILLER = Regex("""\b(a|o|as|os|de|do|da|dos|das|meu|minha|meus|minhas|no|na|em|para|pra|pro|que|por|favor|ja|eu|hoje|ontem|anteontem|agora|esse|este|essa|esta|mes|conta|boleto|divida|parcela|proxima|ultima|sem|tocar|mexer|reserva|quando|puder|der|couber)\b""")
    private fun name(text: String): String = Fmt.fold(text).replace(Regex("""[^a-z0-9\s]"""), " ").replace(FILLER, " ").replace(Regex("""\s+"""), " ").trim()

    /** Pedido de escrita dito com todas as letras; nulo = não é uma ação conhecida (ou é pergunta). */
    fun action(text: String, s: AssistantSnapshot, session: AssistantSession): ActionRequest? {
        val today = s.today
        val f = Fmt.fold(text)
        val date = dateWord(f)
        fun args(vararg pairs: Pair<String, String?>) = pairs.mapNotNull { (k, v) -> v?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()

        // Desfazer a última coisa que o assistente gravou.
        if (has(f, """^(desfaz|desfaca|desfazer|desfaca\s+isso|volta\s+atras|reverte|reverta)\b""")) return ActionRequest(ActionPlanner.UNDO)

        // Preferência a lembrar.
        Regex("""^(lembre|lembra|lembrar|anote|anota|guarde|guarda|considere|considera)(-se|\s+se)?\s+(de\s+)?que\s+(.+)""").find(f)?.let {
            val original = text.trim().substringAfter("que ", text).trim()
            return ActionRequest(ActionPlanner.REMEMBER, mapOf("texto" to original))
        }
        if (has(f, """^(eu\s+)?(nao|nunca)\s+quero\s+(mexer|tocar|usar|cortar|reduzir|diminuir)\b""") || has(f, """^(minha\s+)?prioridade\s+(e|eh)\b""")) {
            return ActionRequest(ActionPlanner.REMEMBER, mapOf("texto" to text.trim().trimEnd('.')))
        }

        // Acompanhamentos.
        if (has(f, """\b(cancel\w*|pare|para|parar|nao\s+precisa\s+mais)\b.*\b(acompanhamento|avis\w*|alert\w*)\b""")) {
            return ActionRequest(ActionPlanner.CANCEL_WATCH, args("alvo" to name(f.replace(Regex("""\b(cancel\w*|pare|para|parar|nao|precisa|mais|acompanhamento|avis\w*|alert\w*|me|sobre)\b"""), " "))))
        }
        if (has(f, """\b(me\s+)?(avis\w+|alert\w+|notifi\w+)\b.*\b(quando|se)\b""")) {
            val value = amount(text, today)?.let { Fmt.arg(it) }
            val after = f.substringAfter("quando", f.substringAfter(" se "))
            return when {
                has(after, """\bquit\w+\b""") -> {
                    val target = name(after.substringAfter("quitar", after.substringAfter("quit")).replace(Regex("""\d[\d.,]*|r\$"""), " "))
                    ActionRequest(ActionPlanner.CREATE_WATCH, args("tipo" to "quitacao", "alvo" to target.ifBlank { session.focusOf(EntityRef.DEBT)?.label.orEmpty() }, "valor" to value,
                        "id" to session.focusOf(EntityRef.DEBT)?.id?.toString().takeIf { target.isBlank() }))
                }
                has(after, """\bsaldo\b""") -> ActionRequest(ActionPlanner.CREATE_WATCH, args("tipo" to "saldo", "valor" to value))
                category(after) != null -> ActionRequest(ActionPlanner.CREATE_WATCH, args("tipo" to "categoria", "alvo" to category(after), "valor" to value))
                else -> ActionRequest(ActionPlanner.CREATE_WATCH, args("tipo" to "compra", "valor" to value,
                    "alvo" to name(after.replace(Regex("""\b(eu|couber|puder|der|comprar|pagar|caber|cabe|gastar|um|uma|reais|real)\b|\d[\d.,]*|r\$"""), " "))))
            }
        }

        // Plano aceito.
        if (has(f, """\b(aceito|aceitar|salv\w+|guard\w+|seguir)\b.*\bplano\b""")) return ActionRequest(ActionPlanner.SAVE_PLAN)

        // Salário recebido. "Adiantado" é a data antecipada; a parte só vem do substantivo "adiantamento".
        val incomeNoun = has(f, """\b(salario|holerite|ordenado|adiantamento|vale|meu\s+pagamento|pagamento\s+do\s+mes)\b""")
        val incomeVerb = has(f, """\b(caiu|cairam|recebi|entrou|pingou|depositaram|depositou|pagaram|chegou|foi\s+pago|foi\s+depositado)\b""")
        val otherIncome = has(f, """\b(freela\w*|cliente|reembolso|bonus|13|decimo|ferias|restituicao|rescisao|premio|venda|vendi|comissao)\b""")
        if (incomeNoun && incomeVerb && !otherIncome && !has(f, """\b(nao|ainda\s+nao|nem)\s+(caiu|recebi|entrou|chegou)\b""")) {
            val part = when {
                has(f, """\b(o|meu)\s+(adiantamento|vale)\b|\badiantamento\s+(salarial|caiu|entrou|chegou)\b""") -> "adiantamento"
                has(f, """\b(saldo\s+do\s+salario|segunda\s+parte|restante\s+do\s+salario|resto\s+do\s+salario)\b""") -> "saldo"
                has(f, """\b(salario\s+inteiro|todo\s+o\s+salario|salario\s+todo|integral)\b""") -> "integral"
                else -> null
            }
            return ActionRequest(ActionPlanner.RECEIVE_INCOME, args(
                "data" to (date ?: "hoje"), "parte" to part, "valor" to amount(text, today)?.let { Fmt.arg(it) },
                "antecipado" to "sim".takeIf { has(f, """\b(adiantad\w+|antecipad\w+|antes\s+d[aoe]\s+(hora|dia|data)|mais\s+cedo)\b""") },
                "parcial" to "sim".takeIf { has(f, """\b(so\s+(uma\s+)?parte|parcial|metade|so\s+um\s+pedaco)\b""") },
            ))
        }

        val paid = has(f, """\b(paguei|pago|quitei|acabei\s+de\s+pagar|foi\s+pag[ao])\b""")
        // Parcela de dívida.
        if (paid && has(f, """\bparcelas?\b""")) {
            val target = name(f.substringAfter("parcela").replace(Regex("""\b(paguei|pago|quitei)\b|\d[\d.,]*|r\$"""), " "))
            return ActionRequest(ActionPlanner.PAY_INSTALLMENT, args("divida" to target, "data" to date, "valor" to amount(text, today)?.let { Fmt.arg(it) },
                "id" to session.focusOf(EntityRef.DEBT)?.id?.toString().takeIf { target.isBlank() }))
        }
        // Reabrir um pagamento.
        if (has(f, """\b(nao\s+paguei|ainda\s+nao\s+paguei|desmarque|desmarca|reabra|reabre|reabrir|reabri)\b""")) {
            val target = name(f.replace(Regex("""\b(nao|ainda|paguei|desmarque|desmarca|reabra|reabre|reabrir|reabri|pagamento|como|paga|fatura)\b"""), " "))
            if (target.isNotBlank() || has(f, """\bfatura\b""")) return ActionRequest(ActionPlanner.REOPEN_BILL, args("nome" to target.ifBlank { "fatura" }))
        }
        // Conta ou fatura paga: só quando o nome bate com algo em aberto (senão é um gasto novo).
        if (paid) {
            val target = name(f.replace(Regex("""\b(paguei|pago|quitei|acabei|pagar|foi|paga|fatura)\b|\d[\d.,]*|r\$|reais"""), " "))
            val isInvoice = has(f, """\bfaturas?\b""")
            val open = s.contas.filter { it.tipo != "a_receber" }
            val bills = if (target.isNotBlank()) NameMatch.find(target, open) { it.nome } else if (isInvoice) open.filter { it.status != "pago" && Fmt.fold(it.nome).contains("fatura") } else emptyList()
            val recurring = if (target.isNotBlank()) NameMatch.find(target, s.transacoes.filter { it.isRecurringSeries && it.tipo == TransactionType.Gasto.name }) { it.descricao } else emptyList()
            if (bills.isNotEmpty() || recurring.isNotEmpty() || (isInvoice && amount(text, today) == null)) {
                return ActionRequest(ActionPlanner.PAY_BILL, args("nome" to target.ifBlank { "fatura" }, "data" to date))
            }
        }

        // Daqui para frente: muda a série.
        if (has(f, """\b(daqui\s+(pra|para)\s+(a\s+)?frente|a\s+partir\s+de\s+agora|de\s+agora\s+em\s+diante|todo\s+mes|todos\s+os\s+meses|dos\s+proximos\s+meses)\b""") &&
            has(f, """\b(vence|vencimento|vai\s+vencer|custa|vai\s+custar|valor|passou|passa|aumentou|subiu|baixou|caiu|vai\s+ser|sera|fica|ficou)\b""")) {
            val day = Regex("""\bdia\s+(\d{1,2})\b""").find(f)?.groupValues?.get(1)
            val target = name(f.replace(Regex("""\b(daqui|frente|partir|agora|diante|todo|todos|proximos|meses|vence|vencimento|vai|vencer|custa|custar|valor|passou|passa|aumentou|subiu|baixou|caiu|ser|sera|fica|ficou|dia|reais)\b|\d[\d.,]*|r\$"""), " "))
            if (target.isNotBlank()) return ActionRequest(ActionPlanner.UPDATE_SERIES, args("texto" to target, "dia" to day, "valor" to amount(text, today)?.let { Fmt.arg(it) }))
        }

        // "Essa compra foi transporte, não lazer": recategoriza o lançamento em foco.
        val cat = category(f)
        if (cat != null && has(f, """\b(essa|esse|aquela|aquele|a\s+ultima|o\s+ultimo)\s+(compra|gasto|lancamento|despesa)\b.*\b(foi|e|eh|era|de)\b""")) {
            val first = Categorias.all.filter { has(f, """\b${Regex.escape(Fmt.fold(it))}\b""") }.minByOrNull { f.indexOf(Fmt.fold(it)) } ?: cat
            return ActionRequest(ActionPlanner.UPDATE_ENTRY, args("id" to session.focusOf(EntityRef.ENTRY)?.id?.toString(), "categoria" to first))
        }
        // "Minha internet foi R$ 120 este mês": o valor desta ocorrência.
        Regex("""^(a\s+|o\s+|minha\s+|meu\s+)?(.+?)\s+(foi|veio|ficou|saiu|deu)\s+(de\s+|em\s+|por\s+)?(r\$\s*)?\d""").find(f)?.let { m ->
            val target = name(m.groupValues[2])
            val known = target.isNotBlank() && NameMatch.find(target, s.transacoes.filter { it.isRecurringSeries && it.tipo == TransactionType.Gasto.name }) { it.descricao }.isNotEmpty()
            if (known && (has(f, """\b(este|esse|neste|nesse)\s+mes\b""") || !QuickEntryParser.looksLikeEntry(text))) {
                return ActionRequest(ActionPlanner.UPDATE_ENTRY, args("texto" to target, "valor" to amount(text, today)?.let { Fmt.arg(it) }))
            }
        }

        // Metas.
        if (has(f, """^(crie|cria|criar|nova|quero\s+criar|abra|abre)\b.*\b(meta|objetivo)\b""")) {
            val rest = f.substringAfter("meta", f.substringAfter("objetivo"))
            val goalName = Regex("""^\s*(?:de\s+|para\s+|pra\s+|chamad[ao]\s+)?(.+?)(?:\s+(?:de|com|no\s+valor\s+de)\s+(?:r\$\s*)?\d|\s+(?:para|pra|ate)\s+|$)""").find(rest)?.groupValues?.get(1).orEmpty()
            val deadline = Regex("""\b(?:para|pra|ate|em)\s+((?:${MONTH_NAMES_PT.joinToString("|") { Fmt.fold(it) }})\w*(?:\s+de\s+\d{4})?|\d{1,2}/\d{4}|\d{4}-\d{2}|\d{1,3}\s+meses)""").find(rest)?.groupValues?.get(1)
            return ActionRequest(ActionPlanner.CREATE_GOAL, args("nome" to name(goalName), "valor" to amount(rest, today)?.let { Fmt.arg(it) }, "prazo" to deadline))
        }
        if (has(f, """^(mude|muda|mudar|altere|altera|alterar|adie|adia|adiar|passe|passa|troque|troca|antecipe|antecipa)\b""") && (has(f, """\b(meta|objetivo|prazo)\b""") || s.openGoals.any { f.contains(Fmt.fold(it.nome)) })) {
            val goal = s.openGoals.filter { f.contains(Fmt.fold(it.nome)) }.maxByOrNull { it.nome.length }
            val deadline = Regex("""\b(?:para|pra)\s+(.+)$""").find(f)?.groupValues?.get(1)?.takeIf { ActionPlanner.parseMonth(it, today) != null }
            val target = goal?.nome ?: name(f.replace(Regex("""^(mude|muda|mudar|altere|altera|alterar|adie|adia|adiar|passe|passa|troque|troca|antecipe|antecipa)\b"""), " ").substringBefore(" para ").substringBefore(" pra ")
                .replace(Regex("""\b(meta|objetivo|prazo|valor)\b"""), " "))
            val value = if (deadline == null || has(f, """\bvalor\b""")) amount(text, today)?.let { Fmt.arg(it) } else null
            return ActionRequest(ActionPlanner.UPDATE_GOAL, args("meta" to target.ifBlank { session.focusOf(EntityRef.GOAL)?.label.orEmpty() }, "prazo" to deadline, "valor" to value,
                "id" to (goal?.id ?: session.focusOf(EntityRef.GOAL)?.id?.takeIf { target.isBlank() })?.toString()))
        }

        // Limite de categoria.
        if (cat != null && has(f, """\b(limite|teto|orcamento)\b""") && amount(text, today) != null) {
            return ActionRequest(ActionPlanner.SET_BUDGET, args("categoria" to cat, "valor" to amount(text, today)?.let { Fmt.arg(it) }))
        }

        // Excluir.
        if (has(f, """^(apague|apaga|apagar|exclua|exclui|excluir|remova|remove|remover|delete|deleta|deletar|tire|tira)\b""")) {
            val target = name(f.replace(Regex("""^(apague|apaga|apagar|exclua|exclui|excluir|remova|remove|remover|delete|deleta|deletar|tire|tira)\b"""), " ")
                .replace(Regex("""\b(lancamento|lancamentos|gasto|compra|receita|despesa|duplicad[oa]s?|repetid[oa]s?|errad[oa])\b|\d[\d.,/]*|r\$|reais"""), " "))
            return ActionRequest(ActionPlanner.DELETE_ENTRY, args("texto" to target, "quando" to date, "valor_atual" to amount(text, today)?.let { Fmt.arg(it) },
                "id" to session.focusOf(EntityRef.ENTRY)?.id?.toString().takeIf { target.isBlank() && date == null && amount(text, today) == null }))
        }

        // Guardar em meta: "guarde 200 na viagem".
        if (has(f, """\b(guarde|guarda|guardei|guardar|separe|separa|separei|aportei|aporte|coloque|coloca|coloquei|reservei|poupei)\b""") && !has(f, """\bque\b""")) {
            val draft = QuickEntryParser.parse(text, today, s.quick)
            val target = draft.goalName ?: name(draft.description)
            if (draft.cents != null || target.isNotBlank()) {
                return ActionRequest(ActionPlanner.CONTRIBUTE, args("meta" to target, "valor" to draft.cents?.let { Fmt.arg(it) }, "data" to date))
            }
        }

        // Lançamento novo dito numa frase.
        if (QuickEntryParser.looksLikeEntry(text)) return ActionRequest(ActionPlanner.CREATE_ENTRY, mapOf("frase" to text))
        return null
    }

    private fun periodOf(f: String): String? = when {
        has(f, """\bmes\s+passado\b|\bultimo\s+mes\b""") -> "mes_passado"
        has(f, """\b(esse|este|neste|nesse|do|no)\s+ano\b""") -> "ano"
        has(f, """\b(essa|esta|nessa|nesta|na|da)\s+semana\b""") -> "semana"
        has(f, """\bhoje\b""") -> "hoje"
        else -> Regex("""\bultimos\s+(\d{1,3})\s+dias\b""").find(f)?.let { "ultimos_${it.groupValues[1]}_dias" }
            ?: MONTH_NAMES_PT.firstOrNull { has(f, """\b(em|de|no\s+mes\s+de)\s+${Regex.escape(Fmt.fold(it))}\b""") }?.let { Fmt.fold(it) }
    }

    /** Pergunta frequente que o app responde sozinho, sem IA; nulo = deixa a IA escolher a consulta. */
    fun query(text: String, s: AssistantSnapshot, session: AssistantSession): ToolCall? {
        val today = s.today
        val f = Fmt.fold(text)
        val value = amount(text, today)?.let { Fmt.arg(it) }
        fun call(name: String, vararg pairs: Pair<String, String?>) = ToolCall(name, pairs.mapNotNull { (k, v) -> v?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap())
        val debtInText = s.openDebts.filter { f.contains(Fmt.fold(it.nome)) }.maxByOrNull { it.nome.length }
        val goalInText = s.openGoals.filter { f.contains(Fmt.fold(it.nome)) }.maxByOrNull { it.nome.length }
        val refersBack = has(f, """\b(essa|esta|dessa|desta|nessa|a\s+mesma|ela|dela)\b""")
        val debtName = debtInText?.nome ?: session.focusOf(EntityRef.DEBT)?.label?.takeIf { refersBack || has(f, """\bdivida\b""") }
        val goalName = goalInText?.nome ?: session.focusOf(EntityRef.GOAL)?.label?.takeIf { refersBack || has(f, """\bmeta\b""") }

        // Seguimento do último cenário: "e em 6x?", "e à vista?", "e se for R$ 800?".
        session.lastScenario?.takeIf { has(f, """^e\s""") && f.length < 60 }?.let { last ->
            val n = Regex("""\b(\d{1,2})\s*(x|vezes|parcelas)\b""").find(f)?.groupValues?.get(1)
            val patch = when {
                n != null -> last.args + ("parcelas" to n)
                has(f, """\ba\s+vista\b""") -> last.args - "parcelas"
                value != null && has(f, """\b(for|fosse|por|de)\b""") -> last.args + ("valor" to value)
                has(f, """\b(esperar|depois\s+do\s+salario|mes\s+que\s+vem|proximo\s+mes)\b""") -> last.args + ("data" to today.plusMonths(1).toString())
                else -> null
            }
            if (patch != null) return ToolCall(last.name, patch)
        }

        // Quando posso pagar / quitar / comprar.
        if (has(f, """\bquando\b.*\b(posso|consigo|vou\s+poder|da\s+(pra|para)|dara|daria|poderei|cabe|vai\s+caber)\b.*\b(pagar|quitar|comprar|gastar)\b""") || has(f, """\bquando\b.*\b(cabe|couber)\b""")) {
            return if (has(f, """\bquit\w+\b""") || (debtName != null && value == null)) call("janela_pagamento", "divida" to debtName.orEmpty(), "valor" to value) else call("janela_pagamento", "valor" to value)
        }
        // Cenários "e se".
        if (has(f, """^(e\s+)?(se|caso)\b|\be\s+se\b|\bsimul\w+\b""")) {
            val months = Regex("""\b(\d{1,2})\s+mes(es)?\b""").find(f)?.groupValues?.get(1)
            val pct = Regex("""\b(\d{1,3})\s*(%|por\s+cento)""").find(f)?.groupValues?.get(1)
            when {
                has(f, """\b(receber|ganhar|renda|salario)\b.*\b(menos|cair|diminuir|reduzir|caia|menor)\b|\b(perder|perco)\b.*\b(emprego|renda|salario)\b""") ->
                    return call("simular_cenario", "tipo" to "queda_renda", "percentual" to (pct ?: "100".takeIf { has(f, """\b(perder|perco)\b""") && value == null }), "valor" to value.takeIf { pct == null }, "meses" to months)
                has(f, """\bquit\w+\b""") -> return call("simular_cenario", "tipo" to "quitar_divida", "divida" to debtName.orEmpty(), "valor" to value)
                has(f, """\b(receber|ganhar|entrar|cair|vender)\b""") && value != null -> return call("simular_cenario", "tipo" to "renda_extra", "valor" to value)
                has(f, """\b(todo\s+mes|por\s+mes|mensal|assinar|assinatura)\b""") && value != null -> return call("simular_cenario", "tipo" to "gasto_mensal", "valor" to value, "meses" to months)
            }
        }
        // Compra: a mesma leitura do "Posso comprar?", simulada no motor único.
        runCatching { PurchaseIntentParser.parse(text, today) }.getOrNull()?.let { intent ->
            val cents = intent.amountCents
            if (cents != null && Authorization.classify(text) == Authorization.Kind.Hypothetical) {
                return call("simular_cenario", "tipo" to "gasto", "valor" to Fmt.arg(cents), "parcelas" to intent.count?.takeIf { it > 1 }?.toString(), "data" to intent.firstDate?.toString())
            }
        }
        // Gasto por categoria, lugar e período.
        if (has(f, """\bquanto\b.*\b(gastei|gasto|gastamos|foi|paguei|torrei|saiu)\b""") || has(f, """\bgastos?\s+(com|em|de|no|na)\b""")) {
            val cat = category(f)
            val place = if (cat != null) null else Regex("""\b(?:com|em|no|na|de)\s+(.+?)(?:\s+(?:esse|este|neste|nesse|no|na|em|mes|ano|semana|hoje|ultimos|desde)\b.*)?[?.!]*$""").find(f)?.groupValues?.get(1)
                ?.let(::name)?.takeIf { it.isNotBlank() && it !in setOf("total", "tudo", "geral") }
            return call("analisar_gastos", "categoria" to cat, "texto" to place, "periodo" to periodOf(f))
        }
        if (has(f, """\b(compar\w+)\b""") && has(f, """\bmes(es)?\b""")) return call("comparar_periodos", "categoria" to category(f))
        if (has(f, """\b(por\s*que|porque)\b.*\b(livre|saldo|folga|dinheiro)\b|\bo\s+que\s+mudou\b|\bde\s+onde\s+vem\b|\bexpli\w+\b.*\b(saldo|livre)\b""")) return call("explicar_saldo")
        if (has(f, """\b(o\s+que|quais?|quanto)\b.*\b(vence\w*|tenho\s+(pra|para|a)\s+pagar|contas?|compromissos?|boletos?|faturas?)\b""") && !has(f, """\bgastei\b""")) {
            return call("listar_compromissos", "dias" to if (has(f, """\bsemana\b""")) "7" else Regex("""\b(\d{1,3})\s+dias\b""").find(f)?.groupValues?.get(1))
        }
        if (has(f, """\bcomo\s+(fico|vou\s+ficar|fica|estarei|estara|vai\s+ficar)\b|\bprojec\w+\b|\bproximos\s+(\d+\s+)?meses\b""")) {
            return call("projetar_caixa", "meses" to Regex("""\b(\d{1,2})\s+mes""").find(f)?.groupValues?.get(1))
        }
        if (has(f, """\b(seguindo|cumprindo)\b.*\bplano\b|\bplano\b.*\b(realizado|andando|indo)\b|\bcomo\s+(esta|ta|anda)\s+o\s+plano\b""")) return call("acompanhar_plano")
        if (has(f, """\b(mont\w+|organiz\w+|fac\w+|faz|cri\w+|quero)\b.*\bplano\b|\borganiz\w+\b.*\b(contas|metas|financas|dinheiro)\b|\bplano\s+(para|pro|do)\s+(o\s+)?mes\b""")) return call("montar_plano")
        if (has(f, """\bcomo\s+(uso|usar|usaria|gasto|aplico|aproveito|divido)\b""") && (value != null || has(f, """\b(13|decimo|bonus|ferias|restituicao|rescisao|extra)\b"""))) return call("montar_plano", "valor_extra" to value)
        if (debtName != null && has(f, """\b(como\s+(esta|ta|anda)|quanto\s+falta|falta\s+quanto|resumo|situacao|devo|vale\s+a\s+pena)\b""")) return call("revisar_divida", "divida" to debtName)
        if (goalName != null && has(f, """\b(como\s+(esta|ta|anda)|quanto\s+falta|falta\s+quanto|resumo|situacao|vou\s+conseguir|da\s+tempo)\b""")) return call("revisar_meta", "meta" to goalName)
        if (has(f, """\bquanto\b.*\b(posso|da\s+(pra|para)|consigo)\s+(gastar|usar)\b|\bquanto\b.*\b(tenho|sobra|esta|ta|fica)\b.*\b(livre|disponivel|sobrando)\b|\b(qual|como)\b.*\b(meu\s+saldo|minha\s+situacao|minhas\s+financas|meu\s+dinheiro)\b|^(situacao|saldo|livre)\b""")) {
            return call("situacao")
        }
        return null
    }
}
