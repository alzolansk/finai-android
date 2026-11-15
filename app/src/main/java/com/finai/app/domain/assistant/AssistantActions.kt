package com.finai.app.domain.assistant

import com.finai.app.data.local.entity.AcompanhamentoEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.MemoriaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.PlanoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Categorias
import com.finai.app.domain.Completion
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.EntryChange
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.IncomePart
import com.finai.app.domain.IncomePlanner
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.PayCycle
import com.finai.app.domain.RecurringSeries
import com.finai.app.domain.TransactionType
import com.finai.app.domain.importer.MerchantClassifier
import com.finai.app.domain.incomePart
import com.finai.app.domain.isAdjustment
import com.finai.app.domain.isOpenReserve
import com.finai.app.domain.isRecurringSeries
import com.finai.app.domain.isSplitIncome
import com.finai.app.domain.occurrenceFor
import com.finai.app.domain.quickentry.QuickDraft
import com.finai.app.domain.quickentry.QuickEntryParser
import com.finai.app.domain.seriesRoot
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.transactionsInMonth
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Pedido de escrita: o nome de uma ferramenta de ação do catálogo e os argumentos em texto. */
data class ActionRequest(val name: String, val args: Map<String, String> = emptyMap())

/**
 * Uma linha que a ação muda: o registro como estava no retrato ([before], nulo = criar) e como
 * fica ([after], nulo = excluir). O executor só grava se [before] ainda for o que está no banco.
 */
sealed interface RowChange {
    data class Tx(val before: TransacaoEntity?, val after: TransacaoEntity?) : RowChange
    data class Bill(val before: ContaEntity?, val after: ContaEntity?) : RowChange
    data class Goal(val before: ObjetivoEntity?, val after: ObjetivoEntity?) : RowChange
    data class Debt(val before: DividaEntity?, val after: DividaEntity?) : RowChange
    data class Budget(val before: OrcamentoCategoriaEntity?, val after: OrcamentoCategoriaEntity?) : RowChange
    data class Watch(val before: AcompanhamentoEntity?, val after: AcompanhamentoEntity?) : RowChange
    data class Memory(val before: MemoriaEntity?, val after: MemoriaEntity?) : RowChange
    data class Plan(val before: PlanoEntity?, val after: PlanoEntity?) : RowChange
}

enum class PlanState {
    /** Alvo único, alcance pontual e dados completos: o pedido explícito basta. */
    Ready,
    /** Efeito sensível (excluir, série inteira, possível duplicado): mostra a mudança exata e espera o sim. */
    Confirm,
    /** Falta um dado essencial ou há mais de um alvo plausível. */
    Clarify,
    /** O que foi pedido já está registrado: nada a fazer, nada duplicado. */
    Already,
    /** O domínio não suporta essa mudança por aqui: explica, não improvisa uma gravação parcial. */
    Unsupported,
}

/**
 * O que uma ação faria, calculado sobre o retrato sem gravar nada (`preview_financial_change`).
 * [changes] é exatamente o que o executor aplica; [summary] é a frase de sucesso, escrita a
 * partir desses mesmos registros.
 */
data class ActionPlan(
    val request: ActionRequest,
    val state: PlanState,
    val title: String,
    val summary: String = "",
    /** Antes → depois, em linhas de tela. */
    val preview: List<String> = emptyList(),
    val changes: List<RowChange> = emptyList(),
    val question: String = "",
    val options: List<PendingOption> = emptyList(),
    /** Chave do argumento que a próxima mensagem do usuário preenche (pergunta aberta). */
    val awaiting: String? = null,
    val notes: List<String> = emptyList(),
    val agendaDate: LocalDate? = null,
    val focus: List<EntityRef> = emptyList(),
)

/**
 * As ferramentas de ação (§5.10). Funções puras: resolvem o alvo e o alcance no retrato e
 * devolvem as linhas a gravar, com as mesmas regras de domínio das telas
 * ([RecurringSeries], [DebtSchedule], [Completion]). Quem autoriza e quem grava é o app
 * ([Authorization], executor): aqui nunca se decide **se** a escrita acontece.
 *
 * Regras fixas: o alcance padrão é **um registro ou uma ocorrência**; mais de um alvo plausível
 * vira pergunta, nunca "o mais parecido"; o que o domínio não suporta devolve [PlanState.Unsupported].
 */
object ActionPlanner {
    const val RECEIVE_INCOME = "registrar_recebimento_renda"
    const val PAY_BILL = "pagar_conta"
    const val REOPEN_BILL = "reabrir_conta"
    const val PAY_INSTALLMENT = "pagar_parcela"
    const val CONTRIBUTE = "guardar_meta"
    const val CREATE_ENTRY = "criar_lancamento"
    const val UPDATE_ENTRY = "atualizar_lancamento"
    const val UPDATE_SERIES = "atualizar_serie"
    const val DELETE_ENTRY = "excluir_lancamento"
    const val CREATE_GOAL = "criar_meta"
    const val UPDATE_GOAL = "atualizar_meta"
    const val SET_BUDGET = "definir_limite"
    const val CREATE_WATCH = "criar_acompanhamento"
    const val CANCEL_WATCH = "cancelar_acompanhamento"
    const val REMEMBER = "lembrar"
    const val SAVE_PLAN = "salvar_plano"
    const val UNDO = "desfazer"

    val NAMES = setOf(
        RECEIVE_INCOME, PAY_BILL, REOPEN_BILL, PAY_INSTALLMENT, CONTRIBUTE, CREATE_ENTRY, UPDATE_ENTRY, UPDATE_SERIES, DELETE_ENTRY,
        CREATE_GOAL, UPDATE_GOAL, SET_BUDGET, CREATE_WATCH, CANCEL_WATCH, REMEMBER, SAVE_PLAN, UNDO,
    )

    /** Ações que sempre mostram a mudança exata antes de gravar, mesmo com pedido explícito. */
    val ALWAYS_CONFIRM = setOf(DELETE_ENTRY, UPDATE_SERIES)

    const val ORIGIN = "assistente"

    /** O que entra no prompt: a IA escolhe a ação; o app valida a autorização e o alcance. */
    val CATALOG: String = """
        Quando o usuário PEDIR ou INFORMAR algo que muda um registro (não pergunta, não hipótese), responda SOMENTE com uma linha JSON:
        {"acao":"registrar_recebimento_renda","data":"hoje"}  (opcionais: "parte":"adiantamento|saldo|integral", "valor")
        {"acao":"pagar_conta","nome":"fatura nubank","data":"hoje"}   {"acao":"reabrir_conta","nome":"internet"}
        {"acao":"pagar_parcela","divida":"picpay"}
        {"acao":"guardar_meta","meta":"viagem","valor":"200"}
        {"acao":"criar_lancamento","frase":"gastei 45 no mercado hoje no débito"}
        {"acao":"atualizar_lancamento","texto":"internet","valor":"120"}  (só esta ocorrência; opcionais: "data","categoria","mes":"2026-11")
        {"acao":"atualizar_serie","texto":"internet","dia":"10"}  (esta e as próximas; só se o usuário disser "daqui para frente/todo mês")
        {"acao":"excluir_lancamento","texto":"uber","data":"ontem"}
        {"acao":"criar_meta","nome":"Viagem","valor":"5000","prazo":"2027-10"}   {"acao":"atualizar_meta","meta":"viagem","prazo":"2028-10"}
        {"acao":"definir_limite","categoria":"Lazer","valor":"400"}
        {"acao":"criar_acompanhamento","tipo":"quitacao|compra|categoria|saldo","alvo":"nome","valor":"1000"}   {"acao":"cancelar_acompanhamento","alvo":"nome"}
        {"acao":"lembrar","texto":"não quer mexer na reserva"}   {"acao":"salvar_plano"}   {"acao":"desfazer"}
        Nunca use "acao" para sugestão sua, pergunta, "se eu…", "talvez" ou "faça o que achar melhor": nesses casos consulte ou explique. Use só valores que o usuário disse.
    """.trimIndent()

    /** Lê a linha JSON de ação; nulo quando a resposta não pede escrita. */
    fun parseCall(reply: String): ActionRequest? {
        val line = reply.lines().map { it.trim().trim('`') }.firstOrNull { it.startsWith("{") && it.contains("\"acao\"") } ?: return null
        val args = AssistantTools.parseArgs(line)
        val name = args["acao"]?.takeIf { it in NAMES } ?: return null
        return ActionRequest(name, args - "acao")
    }

    private fun brl(c: Long) = Fmt.money(c)
    private fun cents(text: String?, today: LocalDate): Long? = text?.takeIf { it.isNotBlank() }?.let { QuickEntryParser.parse(it, today).cents }

    /** "hoje", "ontem", "anteontem", "14/11", "2026-11-14"; nulo = não disse. */
    fun parseDate(text: String?, today: LocalDate): LocalDate? {
        val t = Fmt.fold(text.orEmpty())
        if (t.isBlank()) return null
        runCatching { LocalDate.parse(t) }.getOrNull()?.let { return it }
        return when {
            t.contains("anteontem") -> today.minusDays(2)
            t.contains("ontem") -> today.minusDays(1)
            t.contains("hoje") || t.contains("agora") -> today
            else -> QuickEntryParser.parse("x 1 $t", today).takeIf { it.dateExplicit }?.date
        }
    }

    /** "outubro de 2028", "out/2028", "10/2028", "2028-10", "em 12 meses"; nulo = não entendi. */
    fun parseMonth(text: String?, today: LocalDate): YearMonth? {
        val t = Fmt.fold(text.orEmpty())
        if (t.isBlank()) return null
        Regex("""(\d{4})-(\d{1,2})""").find(t)?.let { m -> return runCatching { YearMonth.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull() }
        Regex("""\b(\d{1,2})/(\d{4})\b""").find(t)?.let { m -> return runCatching { YearMonth.of(m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull() }
        Regex("""\b(\d{1,3})\s*mes""").find(t)?.let { m -> return YearMonth.from(today).plusMonths(m.groupValues[1].toLong()) }
        val index = MONTH_NAMES_PT.indexOfFirst { name -> val f = Fmt.fold(name); Regex("""\b(${f}|${f.take(3)})\b""").containsMatchIn(t) }
        if (index < 0) return null
        val year = Regex("""\b(20\d{2})\b""").find(t)?.value?.toIntOrNull()
        val month = YearMonth.of(year ?: today.year, index + 1)
        return if (year == null && month < YearMonth.from(today)) month.plusYears(1) else month
    }

    private fun clarify(request: ActionRequest, title: String, question: String, options: List<PendingOption> = emptyList(), awaiting: String? = null) =
        ActionPlan(request, PlanState.Clarify, title, question = question, options = options, awaiting = awaiting)

    private fun unsupported(request: ActionRequest, title: String, why: String) = ActionPlan(request, PlanState.Unsupported, title, summary = why)

    private fun with(request: ActionRequest, vararg extra: Pair<String, String>) = request.args + extra

    fun plan(request: ActionRequest, s: AssistantSnapshot, confirmed: Boolean = false): ActionPlan {
        val plan = when (request.name) {
            RECEIVE_INCOME -> receiveIncome(request, s)
            PAY_BILL -> payBill(request, s)
            REOPEN_BILL -> reopenBill(request, s)
            PAY_INSTALLMENT -> payInstallment(request, s)
            CONTRIBUTE -> contribute(request, s)
            CREATE_ENTRY -> createEntry(request, s)
            UPDATE_ENTRY -> updateEntry(request, s)
            UPDATE_SERIES -> updateSeries(request, s)
            DELETE_ENTRY -> deleteEntry(request, s)
            CREATE_GOAL -> createGoal(request, s)
            UPDATE_GOAL -> updateGoal(request, s)
            SET_BUDGET -> setBudget(request, s)
            CREATE_WATCH -> createWatch(request, s)
            CANCEL_WATCH -> cancelWatch(request, s)
            REMEMBER -> remember(request, s)
            SAVE_PLAN -> savePlan(request, s)
            else -> unsupported(request, "Não sei fazer isso", "Essa ação não existe no catálogo do assistente.")
        }
        // O sim do usuário libera só a proposta que ele viu: a ação continua a mesma, recalculada agora.
        return if (plan.state == PlanState.Confirm && confirmed) plan.copy(state = PlanState.Ready) else plan
    }

    // ── renda ───────────────────────────────────────────────────────────────

    private data class Occ(val row: TransacaoEntity, val month: YearMonth, val shown: TransacaoEntity) {
        val date: LocalDate get() = shown.data.toLocalDate()
        /** A mesma renda: o grupo do salário dividido ou a raiz da série. */
        val family: String get() = row.rendaGrupo ?: "s${row.seriesRoot}"
    }

    private fun incomeRows(all: List<TransacaoEntity>): List<TransacaoEntity> {
        fun income(t: TransacaoEntity) = t.tipo == TransactionType.Receita.name && !t.extra && t.faturaId == null && t.valorCentavos > 0
        // Toda renda com cara de salário (marcada, pelo nome ou dividida), recorrente ou avulsa: duas fontes viram pergunta.
        val single = all.filter { income(it) && (it.rendaPrincipal || PayCycle.isSalary(it) || it.isSplitIncome) }
        return (IncomePlanner.familyOf(all) + single).distinctBy { it.id }
    }

    private fun occurrences(rows: List<TransacaoEntity>, today: LocalDate): List<Occ> {
        val current = YearMonth.from(today)
        return rows.flatMap { row ->
            if (row.isRecurringSeries) (-1L..2L).mapNotNull { i -> current.plusMonths(i).let { m -> row.occurrenceFor(m)?.let { Occ(row, m, it) } } }
            else listOf(Occ(row, YearMonth.from(row.data.toLocalDate()), row))
        }
    }

    private fun partLabel(o: Occ) = when (o.shown.incomePart) {
        IncomePart.Advance -> "o adiantamento"
        IncomePart.Balance -> "o saldo do salário"
        else -> "\"${Fmt.clean(o.shown.descricao.ifBlank { "Salário" })}\""
    }

    /**
     * "Meu salário caiu adiantado, recebi hoje": move **uma** ocorrência prevista para o dia do
     * recebimento. Os meses anteriores e seguintes, a outra parte do salário dividido, o 13º e
     * outras rendas não mudam. "Adiantado" é a data antecipada, não a parte chamada adiantamento:
     * com salário dividido e sem a parte dita, pergunta.
     */
    private fun receiveIncome(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Recebimento do salário"
        val date = parseDate(request.args["data"], today) ?: today
        if (date.isAfter(today)) return unsupported(request, title, "Só registro recebimento que já aconteceu. Para mudar a data prevista de um salário futuro, edite a renda em Minha renda.")
        val value = cents(request.args["valor"], today)
        val rows = incomeRows(s.transacoes)
        if (rows.isEmpty()) {
            if (value == null) return clarify(request, title, "Você ainda não tem renda cadastrada, então não há um salário previsto para ajustar. Qual foi o valor recebido? Eu lanço como receita de ${Fmt.day(date, today)}.", awaiting = "valor")
            val entry = TransacaoEntity(
                data = date.toEpochMillis(), descricao = "Salário", valorCentavos = value, categoria = "Receita", contaOrigem = s.accounts.first(),
                recorrente = false, origem = ORIGIN, tipo = TransactionType.Receita.name,
            )
            return ActionPlan(request, PlanState.Ready, title, "Lancei ${brl(value)} de salário em ${Fmt.day(date, today)}. Não criei renda recorrente: para o app prever os próximos, cadastre em Minha renda.",
                preview = listOf("Nova receita: Salário, ${brl(value)}, ${Fmt.day(date, today)}"), changes = listOf(RowChange.Tx(null, entry)), agendaDate = date)
        }
        val all = occurrences(rows, today)
        val partArg = Fmt.fold(request.args["parte"].orEmpty())
        val part = when {
            partArg.startsWith("adiant") || partArg == "vale" -> IncomePart.Advance
            partArg.startsWith("saldo") || partArg.startsWith("rest") || partArg.startsWith("segunda") -> IncomePart.Balance
            else -> null
        }
        val whole = partArg.startsWith("integral") || partArg.startsWith("inteir") || partArg.startsWith("tudo") || partArg.startsWith("duas")
        val family = request.args["renda"]
        fun mine(o: Occ) = (family == null || o.family == family) && (part == null || o.shown.incomePart == part)
        // O que já consta recebido exatamente nesse dia não é recebido de novo.
        val onDay = all.filter { it.date == date && mine(it) }
        val received = onDay.map { it.family to it.shown.incomePart }.toSet()
        // Ainda previsto: depois do dia do recebimento, e perto o bastante para ser o que ele substitui.
        val ahead = all.filter { it.date.isAfter(date) && !it.date.isAfter(date.plusDays(45)) && mine(it) && (it.family to it.shown.incomePart) !in received }.sortedBy { it.date }
        // Recebimento previsto para poucos dias atrás, que o app já conta como recebido na data antiga.
        val recent = all.filter { it.date.isBefore(date) && !it.date.isBefore(date.minusDays(10)) && mine(it) }.sortedBy { it.date }
        val early = Fmt.fold(request.args["antecipado"].orEmpty()).startsWith("s")
        val which = Fmt.fold(request.args["alvo"].orEmpty())
        if (recent.isNotEmpty() && ahead.isNotEmpty() && !early && which.isBlank()) {
            val r = recent.last(); val n = ahead.first()
            return clarify(request, title, "${partLabel(r).replaceFirstChar { it.uppercase() }} previsto para ${Fmt.dm(r.date)} já consta como recebido naquele dia. O que caiu ${Fmt.day(date, today)}?", listOf(
                PendingOption("Foi o de ${Fmt.dm(r.date)}, só que caiu ${Fmt.day(date, today)}", with(request, "alvo" to "recente")),
                PendingOption("É o de ${Fmt.dm(n.date)}, adiantado", with(request, "alvo" to "proximo")),
            ))
        }
        val pending = if (which.startsWith("recente") || (ahead.isEmpty() && recent.isNotEmpty() && which.isBlank() && !early && onDay.isEmpty())) recent.takeLast(1) else ahead
        if (pending.isEmpty()) {
            if (onDay.isNotEmpty()) {
                val o = onDay.first()
                return ActionPlan(request, PlanState.Already, title, "Já consta: ${partLabel(o)} (${brl(o.shown.valorCentavos)}) está registrado como recebido em ${Fmt.day(date, today)}. Não lancei de novo.",
                    agendaDate = date)
            }
            return if (value != null) createLooseIncome(request, s, date, value)
            else clarify(request, title, "Não encontrei um salário previsto perto de ${Fmt.day(date, today)}. Qual foi o valor recebido? Eu lanço como receita avulsa.", awaiting = "valor")
        }
        // A próxima de cada renda (e de cada parte, no salário dividido).
        val next = pending.groupBy { it.family to it.shown.incomePart }.map { it.value.first() }.sortedBy { it.date }
        val families = next.map { it.family }.distinct()
        if (families.size > 1) {
            return clarify(request, title, "Você tem mais de uma renda prevista. Qual delas caiu?", next.groupBy { it.family }.map { (f, list) ->
                val o = list.first()
                PendingOption("${Fmt.clean(o.row.descricao)} · ${brl(list.sumOf { it.shown.valorCentavos })} · previsto ${Fmt.dm(o.date)}", with(request, "renda" to f))
            })
        }
        val split = next.any { it.shown.isSplitIncome }
        val targets = when {
            !split -> listOf(next.first())
            part != null -> listOf(next.first())
            whole -> next
            else -> return clarify(request, title, "Seu salário vem em duas partes. O que caiu ${Fmt.day(date, today)}: o adiantamento, o saldo ou o salário inteiro?",
                next.map { o -> PendingOption("${partLabel(o).replaceFirstChar { it.uppercase() }} · ${brl(o.shown.valorCentavos)} · previsto ${Fmt.dm(o.date)}", with(request, "parte" to (o.shown.incomePart?.key ?: "integral"))) } +
                    PendingOption("O salário inteiro · ${brl(next.sumOf { it.shown.valorCentavos })}", with(request, "parte" to "integral")))
        }
        if (value != null && targets.size > 1) return unsupported(request, title, "Com as duas partes de uma vez não sei a qual o valor de ${brl(value)} pertence. Diga qual parte caiu com esse valor.")
        val partial = Fmt.fold(request.args["parcial"].orEmpty()).startsWith("s")
        val changes = mutableListOf<RowChange>()
        val preview = mutableListOf<String>()
        targets.forEach { o ->
            val expected = o.shown.valorCentavos
            val received = value ?: expected
            if (partial && received >= expected) return unsupported(request, title, "O valor recebido (${brl(received)}) não é menor que o previsto (${brl(expected)}), então não é um recebimento parcial.")
            val change = EntryChange(date, received, o.shown.descricao, o.shown.categoria, o.shown.contaOrigem)
            when {
                partial -> {
                    // Recebeu só uma parte: o recebido entra no dia; o restante continua previsto na data original.
                    val rest = expected - received
                    changes += if (o.row.isRecurringSeries) RowChange.Tx(o.row, RecurringSeries.editOnly(o.row, o.month, change.copy(data = o.date, valorCentavos = rest)))
                    else RowChange.Tx(o.row, o.row.copy(valorCentavos = rest))
                    changes += RowChange.Tx(null, loose(o, date, received, s))
                    preview += "${partLabel(o).replaceFirstChar { it.uppercase() }}: ${brl(received)} recebidos ${Fmt.day(date, today)}; ${brl(rest)} continuam previstos para ${Fmt.dm(o.date)}"
                }
                !o.row.isRecurringSeries -> changes += RowChange.Tx(o.row, o.row.copy(data = date.toEpochMillis(), valorCentavos = received))
                YearMonth.from(date) == o.month -> changes += RowChange.Tx(o.row, RecurringSeries.editOnly(o.row, o.month, change))
                else -> {
                    // Antecipação que cruza o mês: a ocorrência prevista é suprimida só naquele mês e o
                    // recebimento entra como lançamento avulso com a mesma competência e a mesma parte.
                    changes += RowChange.Tx(o.row, RecurringSeries.deleteOnly(o.row, o.month))
                    changes += RowChange.Tx(null, loose(o, date, received, s))
                }
            }
            if (!partial) preview += "${partLabel(o).replaceFirstChar { it.uppercase() }}: de ${Fmt.date(o.date, today)} para ${Fmt.day(date, today)}" +
                if (received != expected) ", valor de ${brl(expected)} para ${brl(received)} só nesta vez" else ", valor mantido (${brl(expected)})"
        }
        val o = targets.first()
        val what = if (targets.size > 1) "do salário inteiro" else "de ${partLabel(o)}"
        val summary = if (partial) "Registrei ${brl(value!!)} recebidos ${Fmt.day(date, today)}; o restante continua previsto para ${Fmt.dm(o.date)}."
        else "Atualizei o recebimento $what de ${Fmt.date(o.date, today)} para ${Fmt.day(date, today)}. " +
            (if (value != null && value != o.shown.valorCentavos) "O valor desta vez ficou em ${brl(value)}" else "O valor foi mantido") +
            (if (o.row.isRecurringSeries) " e os próximos meses continuam nas datas habituais." else ".")
        return ActionPlan(request, PlanState.Ready, title, summary, preview, changes, agendaDate = date,
            notes = listOfNotNull("O mês trabalhado (competência) continua o mesmo.".takeIf { targets.any { YearMonth.from(date) != it.month } }),
            focus = listOf(EntityRef(EntityRef.ENTRY, o.row.id, o.row.descricao)))
    }

    /** Recebimento avulso que substitui uma ocorrência prevista: mesma parte, mesmo grupo, mesma competência. */
    private fun loose(o: Occ, date: LocalDate, cents: Long, s: AssistantSnapshot): TransacaoEntity = o.shown.copy(
        id = 0, data = date.toEpochMillis(), valorCentavos = cents, recorrente = false, recorrenteAte = null, diaRecorrencia = null,
        excecoes = "", serieId = null, origem = ORIGIN,
        competenciaDeslocamento = o.row.competenciaDeslocamento + ChronoUnit.MONTHS.between(YearMonth.from(date), o.month).toInt(),
    )

    private fun createLooseIncome(request: ActionRequest, s: AssistantSnapshot, date: LocalDate, value: Long): ActionPlan {
        val entry = TransacaoEntity(
            data = date.toEpochMillis(), descricao = "Salário", valorCentavos = value, categoria = "Receita", contaOrigem = s.accounts.first(),
            recorrente = false, origem = ORIGIN, tipo = TransactionType.Receita.name,
        )
        return ActionPlan(request, PlanState.Ready, "Recebimento do salário", "Lancei ${brl(value)} como receita avulsa de ${Fmt.day(date, s.today)}. A renda recorrente cadastrada não foi alterada.",
            preview = listOf("Nova receita: Salário, ${brl(value)}, ${Fmt.day(date, s.today)}"), changes = listOf(RowChange.Tx(null, entry)), agendaDate = date)
    }

    // ── contas, faturas e parcelas ──────────────────────────────────────────

    private fun billLabel(c: ContaEntity, today: LocalDate) = "${Fmt.clean(c.nome)} · ${brl(c.valorCentavos)} · vence ${Fmt.date(c.vencimento.toLocalDate(), today)}"

    private fun payBill(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Pagamento de conta"
        val date = parseDate(request.args["data"], today) ?: today
        if (date.isAfter(today)) return unsupported(request, title, "Só registro pagamento que já aconteceu.")
        val id = request.args["id"]?.toLongOrNull()
        val query = request.args["nome"].orEmpty()
        val payable = s.contas.filter { it.tipo != "a_receber" }
        val open = payable.filter { it.status != "pago" }
        val found = if (id != null) open.filter { it.id == id } else NameMatch.find(query, open) { it.nome }
        if (found.isEmpty()) {
            val paid = NameMatch.find(query, payable.filter { it.status == "pago" }) { it.nome }.maxByOrNull { it.vencimento }
            // Conta que o usuário lançou como gasto de todo mês: "paguei" traz a ocorrência do mês para o dia.
            val recurring = recurringBill(request, s, query, date)
            if (recurring != null) return recurring
            if (paid != null) return ActionPlan(request, PlanState.Already, title, "\"${Fmt.clean(paid.nome)}\" (venc. ${Fmt.date(paid.vencimento.toLocalDate(), today)}) já está marcada como paga. Não mudei nada.")
            return clarify(request, title, if (query.isBlank()) "Qual conta você pagou?" else "Não encontrei conta em aberto chamada \"$query\". Qual é o nome dela na Agenda? Se foi um gasto novo, diga o valor que eu lanço.", awaiting = "nome")
        }
        // Mais de uma em aberto com esse nome: a vencida ou a que vence primeiro é a óbvia só se as outras são de meses seguintes.
        val target = found.singleOrNull() ?: run {
            val sorted = found.sortedBy { it.vencimento }
            val first = sorted.first()
            val sameSeries = sorted.all { Fmt.fold(it.nome) == Fmt.fold(first.nome) } && sorted.map { YearMonth.from(it.vencimento.toLocalDate()) }.distinct().size == sorted.size
            if (sameSeries && request.args["qual"] == null) first
            else return clarify(request, title, "Encontrei mais de uma conta em aberto. Qual você pagou?", sorted.take(4).map { PendingOption(billLabel(it, today), with(request, "id" to it.id.toString())) })
        }
        val millis = if (date == today) s.nowMillis else date.toEpochMillis()
        val after = target.copy(status = "pago", pagoEm = millis)
        val due = target.vencimento.toLocalDate()
        return ActionPlan(request, PlanState.Ready, title,
            "Marquei \"${Fmt.clean(target.nome)}\" (${brl(target.valorCentavos)}, venc. ${Fmt.date(due, today)}) como paga em ${Fmt.day(date, today)}. O valor saiu do saldo no dia do pagamento; não lancei outro gasto por cima.",
            preview = listOf("${billLabel(target, today)}: em aberto → paga em ${Fmt.day(date, today)}"), changes = listOf(RowChange.Bill(target, after)), agendaDate = date,
            focus = listOf(EntityRef(EntityRef.BILL, target.id, target.nome)))
    }

    private fun recurringBill(request: ActionRequest, s: AssistantSnapshot, query: String, date: LocalDate): ActionPlan? {
        if (query.isBlank()) return null
        val month = YearMonth.from(date)
        val series = s.transacoes.filter { it.isRecurringSeries && it.tipo == TransactionType.Gasto.name }
        val match = NameMatch.find(query, series.filter { it.occurrenceFor(month) != null }) { it.descricao }.distinctBy { it.seriesRoot }
        val row = match.singleOrNull() ?: return null
        val occ = row.occurrenceFor(month) ?: return null
        val shown = occ.data.toLocalDate()
        if (!shown.isAfter(date)) return ActionPlan(request, PlanState.Already, "Pagamento de conta",
            "\"${Fmt.clean(occ.descricao)}\" deste mês já consta como paga em ${Fmt.dm(shown)} (${brl(occ.valorCentavos)}). Não mudei nada.", agendaDate = shown)
        val after = RecurringSeries.editOnly(row, month, EntryChange(date, occ.valorCentavos, occ.descricao, occ.categoria, occ.contaOrigem))
        return ActionPlan(request, PlanState.Ready, "Pagamento de conta",
            "Registrei \"${Fmt.clean(occ.descricao)}\" (${brl(occ.valorCentavos)}) como paga em ${Fmt.day(date, s.today)}, em vez de ${Fmt.dm(shown)}. Só este mês mudou.",
            preview = listOf("${Fmt.clean(occ.descricao)}: de ${Fmt.dm(shown)} para ${Fmt.day(date, s.today)} (só este mês)"), changes = listOf(RowChange.Tx(row, after)), agendaDate = date,
            focus = listOf(EntityRef(EntityRef.ENTRY, row.id, row.descricao)))
    }

    private fun reopenBill(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Reabrir pagamento"
        val id = request.args["id"]?.toLongOrNull()
        val paid = s.contas.filter { it.status == "pago" && it.tipo != "a_receber" }
        val found = if (id != null) paid.filter { it.id == id } else NameMatch.find(request.args["nome"].orEmpty(), paid) { it.nome }
        if (found.isEmpty()) return clarify(request, title, "Não encontrei conta paga com esse nome. Qual é o nome dela na Agenda?", awaiting = "nome")
        // A paga mais recentemente é a que alguém desmarca; se houver dúvida real, pergunta.
        val target = found.singleOrNull() ?: found.sortedByDescending { it.pagoEm ?: it.vencimento }.let { sorted ->
            if (sorted.all { Fmt.fold(it.nome) == Fmt.fold(sorted.first().nome) }) sorted.first()
            else return clarify(request, title, "Qual pagamento você quer reabrir?", sorted.take(4).map { PendingOption(billLabel(it, today), with(request, "id" to it.id.toString())) })
        }
        return ActionPlan(request, PlanState.Ready, title, "Reabri \"${Fmt.clean(target.nome)}\" (${brl(target.valorCentavos)}): voltou a constar em aberto, com vencimento em ${Fmt.date(target.vencimento.toLocalDate(), today)}.",
            preview = listOf("${billLabel(target, today)}: paga → em aberto"), changes = listOf(RowChange.Bill(target, target.copy(status = "pendente", pagoEm = null))),
            agendaDate = target.vencimento.toLocalDate(), focus = listOf(EntityRef(EntityRef.BILL, target.id, target.nome)))
    }

    private fun payInstallment(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Parcela de dívida"
        val id = request.args["id"]?.toLongOrNull()
        val debts = s.openDebts.filter { it.parcelasRestantes > 0 }
        val found = if (id != null) debts.filter { it.id == id } else if (request.args["divida"].isNullOrBlank()) debts else NameMatch.find(request.args["divida"].orEmpty(), debts) { it.nome }
        if (found.isEmpty()) return clarify(request, title, "Não encontrei dívida com parcela em aberto com esse nome. Qual é o nome dela em Planos?", awaiting = "divida")
        val d = found.singleOrNull() ?: return clarify(request, title, "De qual dívida foi a parcela?",
            found.take(4).map { PendingOption("${Fmt.clean(it.nome)} · ${brl(it.valorParcelaCentavos)}", with(request, "id" to it.id.toString())) })
        cents(request.args["valor"], today)?.let { v ->
            if (v != minOf(d.valorParcelaCentavos, d.valorAbertoCentavos)) return unsupported(request, title,
                "A parcela de \"${Fmt.clean(d.nome)}\" é de ${brl(d.valorParcelaCentavos)} e você falou em ${brl(v)}. Pagamento parcial ou com valor diferente não é registrado por aqui: ajuste a dívida em Planos › Compromissos.")
        }
        val date = parseDate(request.args["data"], today) ?: today
        if (date.isAfter(today)) return unsupported(request, title, "Só registro pagamento que já aconteceu.")
        val number = if (d.parcelasTotais > 0) d.parcelasTotais - d.parcelasRestantes + 1 else null
        val value = minOf(d.valorParcelaCentavos, d.valorAbertoCentavos).coerceAtLeast(0)
        val payment = TransacaoEntity(
            data = date.toEpochMillis(), descricao = if (number != null) "${d.nome} · parcela $number de ${d.parcelasTotais}" else "${d.nome} · parcela",
            valorCentavos = value, categoria = DebtSchedule.PAYMENT_CATEGORY, contaOrigem = "", recorrente = false, origem = DebtSchedule.PAYMENT_ORIGIN,
        )
        val after = Completion.onDebtSaved(d, DebtSchedule.afterPayment(d, today), s.nowMillis).divida
        val done = Completion.isPaid(after)
        return ActionPlan(request, PlanState.Ready, title,
            "Registrei a parcela ${number?.let { "$it de ${d.parcelasTotais} " } ?: ""}de \"${Fmt.clean(d.nome)}\" (${brl(value)}) em ${Fmt.day(date, today)}. " +
                if (done) "Era a última: a dívida está quitada." else "Faltam ${after.parcelasRestantes} parcela(s) e ${brl(after.valorAbertoCentavos)} em aberto; as outras continuam nas datas delas.",
            preview = listOf("${Fmt.clean(d.nome)}: ${d.parcelasRestantes} → ${after.parcelasRestantes} parcela(s); em aberto ${brl(d.valorAbertoCentavos)} → ${brl(after.valorAbertoCentavos)}"),
            changes = listOf(RowChange.Tx(null, payment), RowChange.Debt(d, after)), agendaDate = date, focus = listOf(EntityRef(EntityRef.DEBT, d.id, d.nome)))
    }

    // ── metas ───────────────────────────────────────────────────────────────

    private fun contribute(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Aporte em meta"
        val goals = s.openGoals
        if (goals.isEmpty()) return unsupported(request, title, "Você ainda não tem meta nem reserva cadastrada. Crie uma em Planos (ou peça: \"crie a meta Viagem de R$ 5.000 para outubro de 2027\") e eu registro o aporte.")
        val id = request.args["id"]?.toLongOrNull()
        val query = request.args["meta"].orEmpty()
        val found = when {
            id != null -> goals.filter { it.id == id }
            query.isBlank() -> goals
            else -> NameMatch.find(query, goals) { it.nome }.ifEmpty {
                // "na reserva" sem o nome exato: as reservas contínuas.
                if (Fmt.fold(query).contains("reserva")) goals.filter { it.isOpenReserve || Fmt.fold(it.tipo) == "reserva" } else emptyList()
            }
        }
        if (found.isEmpty()) return clarify(request, title, "Não encontrei meta chamada \"$query\". Em qual delas eu guardo?", goals.take(4).map { PendingOption(it.nome, with(request, "id" to it.id.toString())) })
        val g = found.singleOrNull() ?: return clarify(request, title, "Em qual meta eu guardo?", found.take(4).map { PendingOption("${it.nome} · ${brl(it.valorGuardadoCentavos)} guardados", with(request, "id" to it.id.toString())) })
        val value = cents(request.args["valor"], today) ?: return clarify(request.copy(args = with(request, "id" to g.id.toString())), title, "Quanto você quer guardar em \"${Fmt.clean(g.nome)}\"?", awaiting = "valor")
        val date = parseDate(request.args["data"], today) ?: today
        if (date.isAfter(today)) return unsupported(request, title, "Aporte futuro não é registrado: ele só sai do saldo quando acontece. Posso criar um acompanhamento se quiser.")
        val entry = TransacaoEntity(
            data = date.toEpochMillis(), descricao = "Aporte · ${g.nome}", valorCentavos = value, categoria = TransactionType.Transferencia.label,
            contaOrigem = request.args["conta"]?.takeIf { it.isNotBlank() } ?: s.accounts.first(), recorrente = false, origem = "manual",
            tipo = TransactionType.Transferencia.name, objetivoId = g.id, contaDestino = g.nome,
        )
        val after = Completion.onGoalSaved(g, g.copy(valorGuardadoCentavos = g.valorGuardadoCentavos + value), s.nowMillis).objetivo
        val reached = after.concluidoEm != null
        return ActionPlan(request, PlanState.Ready, title,
            "Guardei ${brl(value)} em \"${Fmt.clean(g.nome)}\": de ${brl(g.valorGuardadoCentavos)} para ${brl(after.valorGuardadoCentavos)}" +
                (if (!g.isOpenReserve) " de ${brl(g.valorAlvoCentavos)}" else "") + ". O valor saiu do saldo livre." + if (reached) " Meta alcançada!" else "",
            preview = listOf("${Fmt.clean(g.nome)}: ${brl(g.valorGuardadoCentavos)} → ${brl(after.valorGuardadoCentavos)}"),
            changes = listOf(RowChange.Tx(null, entry), RowChange.Goal(g, after)), agendaDate = date, focus = listOf(EntityRef(EntityRef.GOAL, g.id, g.nome)))
    }

    private fun createGoal(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Nova meta"
        val name = request.args["nome"].orEmpty().trim().replaceFirstChar { it.uppercase() }.take(40)
        if (name.isBlank()) return clarify(request, title, "Qual é o nome da meta?", awaiting = "nome")
        if (s.objetivos.any { Fmt.fold(it.nome) == Fmt.fold(name) && it.concluidoEm == null }) return ActionPlan(request, PlanState.Already, title, "Já existe uma meta chamada \"$name\". Não criei outra.")
        val value = cents(request.args["valor"], today) ?: return clarify(request, title, "Qual é o valor da meta \"$name\"?", awaiting = "valor")
        val deadline = parseMonth(request.args["prazo"], today) ?: return clarify(request, title, "Para quando é a meta \"$name\"? (mês e ano)", awaiting = "prazo")
        if (deadline < YearMonth.from(today)) return clarify(request, title, "Esse prazo já passou. Para quando é a meta?", awaiting = "prazo")
        val folded = Fmt.fold(name)
        val tipo = when {
            folded.contains("viagem") || folded.contains("ferias") -> "Viagem"
            folded.contains("reserva") || folded.contains("emergencia") -> "Reserva"
            else -> "Outro"
        }
        val goal = ObjetivoEntity(tipo = tipo, nome = name, valorAlvoCentavos = value, valorGuardadoCentavos = 0, prazo = deadline.atEndOfMonth().toEpochMillis(),
            prioridade = (s.objetivos.maxOfOrNull { it.prioridade } ?: 0) + 1)
        val months = ChronoUnit.MONTHS.between(YearMonth.from(today), deadline).coerceAtLeast(1)
        val monthly = (value + months - 1) / months
        return ActionPlan(request, PlanState.Ready, title,
            "Criei a meta \"$name\": ${brl(value)} até ${MONTH_NAMES_PT[deadline.monthValue - 1].lowercase()} de ${deadline.year}, cerca de ${brl(monthly)} por mês. As outras metas não foram alteradas.",
            preview = listOf("Nova meta: $name, ${brl(value)}, prazo ${deadline.monthValue}/${deadline.year}"), changes = listOf(RowChange.Goal(null, goal)),
            notes = listOfNotNull("Hoje cabe guardar ${brl(s.capacity.cabeGuardarCents)} por mês somando todas as metas.".takeIf { s.capacity.cabeGuardarCents < monthly }))
    }

    private fun updateGoal(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Alterar meta"
        val id = request.args["id"]?.toLongOrNull()
        val found = if (id != null) s.openGoals.filter { it.id == id } else NameMatch.find(request.args["meta"].orEmpty(), s.openGoals) { it.nome }
        if (found.isEmpty()) return clarify(request, title, "Qual meta você quer alterar?", s.openGoals.take(4).map { PendingOption(it.nome, with(request, "id" to it.id.toString())) })
        val g = found.singleOrNull() ?: return clarify(request, title, "Qual delas?", found.take(4).map { PendingOption(it.nome, with(request, "id" to it.id.toString())) })
        val deadline = parseMonth(request.args["prazo"], today)
        val target = cents(request.args["valor"], today)
        val monthly = cents(request.args["aporte"], today)
        if (deadline == null && target == null && monthly == null) return clarify(request.copy(args = with(request, "id" to g.id.toString())), title, "O que muda em \"${Fmt.clean(g.nome)}\": o prazo (mês e ano) ou o valor?", awaiting = "prazo")
        if (deadline != null && deadline < YearMonth.from(today)) return clarify(request, title, "Esse prazo já passou. Qual é o novo prazo?", awaiting = "prazo")
        if (deadline != null && g.isOpenReserve) return unsupported(request, title, "\"${Fmt.clean(g.nome)}\" é uma reserva contínua: não tem prazo para mudar.")
        var after = g
        val preview = mutableListOf<String>()
        deadline?.let {
            val old = YearMonth.from(g.prazo.toLocalDate())
            after = after.copy(prazo = it.atEndOfMonth().toEpochMillis())
            preview += "Prazo: ${old.monthValue}/${old.year} → ${it.monthValue}/${it.year}"
        }
        target?.let { after = after.copy(valorAlvoCentavos = it); preview += "Valor: ${brl(g.valorAlvoCentavos)} → ${brl(it)}" }
        monthly?.let { after = after.copy(aporteMensalCentavos = it); preview += "Aporte mensal planejado: ${brl(g.aporteMensalCentavos)} → ${brl(it)}" }
        after = Completion.onGoalSaved(g, after, s.nowMillis).objetivo
        if (after == g) return ActionPlan(request, PlanState.Already, title, "\"${Fmt.clean(g.nome)}\" já está assim. Não mudei nada.")
        val missing = (after.valorAlvoCentavos - after.valorGuardadoCentavos).coerceAtLeast(0)
        val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(after.prazo.toLocalDate())).coerceAtLeast(1)
        return ActionPlan(request, PlanState.Ready, title,
            "Alterei \"${Fmt.clean(g.nome)}\": ${preview.joinToString("; ").replaceFirstChar { it.lowercase() }}." +
                (if (!after.isOpenReserve && missing > 0) " Agora ela pede cerca de ${brl((missing + months - 1) / months)} por mês." else "") + " Nenhuma outra meta foi alterada.",
            preview, listOf(RowChange.Goal(g, after)), focus = listOf(EntityRef(EntityRef.GOAL, g.id, g.nome)))
    }

    private fun setBudget(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val title = "Limite de categoria"
        val cat = MerchantClassifier.normalizeCategoryName(request.args["categoria"].orEmpty())
            ?: return clarify(request, title, "Para qual categoria é o limite?", Categorias.all.take(6).map { PendingOption(it, with(request, "categoria" to it)) })
        val value = cents(request.args["valor"], s.today) ?: return clarify(request.copy(args = with(request, "categoria" to cat)), title, "Qual é o limite de $cat por mês?", awaiting = "valor")
        val month = YearMonth.from(s.today).toString()
        val before = s.orcamentos.firstOrNull { it.categoria == cat && it.mesReferencia == month }
        if (before?.limiteMensalCentavos == value) return ActionPlan(request, PlanState.Already, title, "O limite de $cat neste mês já é ${brl(value)}.")
        return ActionPlan(request, PlanState.Ready, title,
            "Defini o limite de $cat em ${brl(value)} para este mês" + (before?.let { " (era ${brl(it.limiteMensalCentavos)})" } ?: "") + ". Os outros meses e categorias não mudaram.",
            preview = listOf("$cat: ${before?.let { brl(it.limiteMensalCentavos) } ?: "sem limite"} → ${brl(value)}"),
            changes = listOf(RowChange.Budget(before, OrcamentoCategoriaEntity(cat, value, month))))
    }

    // ── lançamentos ─────────────────────────────────────────────────────────

    /** O rascunho de [QuickEntryParser] vira o mesmo registro que o formulário grava. Nulo = precisa do formulário (cartão, dado faltando). */
    fun entryOf(draft: QuickDraft, s: AssistantSnapshot): TransacaoEntity? {
        val cents = draft.cents?.takeIf { it > 0 } ?: return null
        val base = TransacaoEntity(
            data = draft.date.toEpochMillis(), descricao = draft.description, valorCentavos = cents, categoria = draft.category.orEmpty(),
            contaOrigem = draft.account ?: s.accounts.firstOrNull() ?: EntryOptions.FALLBACK_ACCOUNT, recorrente = false, origem = "manual", tipo = draft.type.name,
        )
        return when (draft.type) {
            TransactionType.Gasto -> if (draft.category == null || draft.payment?.isCard == true) null else base.copy(descricao = base.descricao.ifBlank { draft.category })
            TransactionType.Receita -> base.copy(descricao = base.descricao.ifBlank { "Receita" }, categoria = "Receita")
            TransactionType.Transferencia -> null
        }
    }

    private fun createEntry(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Novo lançamento"
        val draft = QuickEntryParser.parse(request.args["frase"].orEmpty(), today, s.quick)
        if (draft.type == TransactionType.Transferencia) {
            return contribute(ActionRequest(CONTRIBUTE, listOfNotNull(draft.goalName?.let { "meta" to it }, draft.cents?.let { "valor" to Fmt.arg(it) },
                "data" to draft.date.toString()).toMap()), s)
        }
        val entry = entryOf(draft, s) ?: return unsupported(request, title, "form")
        if (entry.data.toLocalDate().isAfter(today)) return unsupported(request, title, "form")
        // Mesma coisa, no mesmo dia, pelo mesmo valor: pode ser a mesma compra dita duas vezes.
        val twin = s.transacoes.firstOrNull {
            !it.recorrente && it.tipo == entry.tipo && it.valorCentavos == entry.valorCentavos && it.data == entry.data && Fmt.fold(it.descricao) == Fmt.fold(entry.descricao)
        }
        val what = "${if (entry.tipo == TransactionType.Receita.name) "receita" else "gasto"} de ${brl(entry.valorCentavos)}"
        val detail = listOfNotNull(entry.descricao, entry.categoria.takeIf { entry.tipo == TransactionType.Gasto.name && it != entry.descricao }, entry.contaOrigem, Fmt.day(entry.data.toLocalDate(), today)).joinToString(" · ")
        val plan = ActionPlan(request, PlanState.Ready, title, "Lancei o $what: $detail.", preview = listOf("Novo $what: $detail"), changes = listOf(RowChange.Tx(null, entry)),
            agendaDate = entry.data.toLocalDate(), notes = listOfNotNull("Saiu de \"${entry.contaOrigem}\" (a conta usada por último), no débito.".takeIf { draft.account == null && entry.tipo == TransactionType.Gasto.name }))
        return if (twin != null) plan.copy(state = PlanState.Confirm, question = "Já existe um lançamento igual (${detail}). Lançar outro mesmo assim?") else plan
    }

    private fun editable(t: TransacaoEntity) = !t.isAdjustment && (t.tipo == TransactionType.Gasto.name || t.tipo == TransactionType.Receita.name)

    private fun entryLabel(t: TransacaoEntity, today: LocalDate) = "${Fmt.clean(t.descricao.ifBlank { t.categoria })} · ${brl(t.valorCentavos)} · ${Fmt.date(t.data.toLocalDate(), today)}"

    /** Ocorrências de [month] (e do mês anterior, se nada bater) que casam com o texto, o valor e o dia ditos. */
    private fun findEntries(s: AssistantSnapshot, request: ActionRequest, month: YearMonth, lookBack: Boolean, withContributions: Boolean = false): List<Pair<TransacaoEntity, YearMonth>> {
        val today = s.today
        request.args["id"]?.toLongOrNull()?.let { id ->
            val row = s.transacoes.firstOrNull { it.id == id } ?: return emptyList()
            val m = request.args["mes"]?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: if (row.isRecurringSeries) month else YearMonth.from(row.data.toLocalDate())
            return listOf(row to m)
        }
        val q = Fmt.fold(request.args["texto"].orEmpty())
        val amount = cents(request.args["valor_atual"], today)
        val day = parseDate(request.args["quando"], today)
        fun inMonth(m: YearMonth) = s.transacoes.filter { editable(it) || (withContributions && it.objetivoId != null && !it.isAdjustment) }.mapNotNull { row ->
            val occ = (if (row.isRecurringSeries) row.occurrenceFor(m) else row.takeIf { YearMonth.from(it.data.toLocalDate()) == m }) ?: return@mapNotNull null
            val matches = (q.isBlank() || Fmt.fold(occ.descricao + " " + occ.categoria).contains(q)) && (amount == null || occ.valorCentavos == amount) && (day == null || occ.data.toLocalDate() == day)
            if (matches) row to m else null
        }
        if (q.isBlank() && amount == null && day == null) return emptyList()
        return inMonth(month).ifEmpty { if (lookBack) inMonth(month.minusMonths(1)) else emptyList() }
    }

    private fun updateEntry(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Alterar lançamento"
        val month = request.args["mes"]?.let { parseMonth(it, today) } ?: YearMonth.from(today)
        val found = findEntries(s, request, month, lookBack = request.args["mes"] == null)
        if (found.isEmpty()) return clarify(request, title, "Não encontrei esse lançamento. Qual é o nome dele na Agenda?", awaiting = "texto")
        val (row, m) = found.singleOrNull() ?: return clarify(request, title, "Encontrei mais de um. Qual você quer alterar?",
            found.take(4).map { (r, mo) -> PendingOption(entryLabel(r.occurrenceFor(mo) ?: r, today), with(request, "id" to r.id.toString(), "mes" to mo.toString())) })
        val occ = (if (row.isRecurringSeries) row.occurrenceFor(m) else row) ?: return unsupported(request, title, "Esse lançamento não tem ocorrência em ${m.monthValue}/${m.year}.")
        if (row.objetivoId != null) return unsupported(request, title, "Aporte não é editado: desfaça o aporte e registre de novo com o valor certo.")
        if (row.origem == DebtSchedule.PAYMENT_ORIGIN) return unsupported(request, title, "Pagamento de parcela não é editado: exclua o pagamento na Agenda e pague de novo.")
        val newValue = cents(request.args["valor"], today)
        val newDate = parseDate(request.args["data"], today) ?: request.args["dia"]?.toIntOrNull()?.takeIf { it in 1..31 }?.let { m.atDay(it.coerceAtMost(m.lengthOfMonth())) }
        val newCategory = request.args["categoria"]?.let { MerchantClassifier.normalizeCategoryName(it) }
        if (request.args["categoria"] != null && newCategory == null) return clarify(request, title, "Não conheço essa categoria. Qual delas?", Categorias.all.map { PendingOption(it, with(request, "categoria" to it, "id" to row.id.toString(), "mes" to m.toString())) })
        if (newValue == null && newDate == null && newCategory == null) return clarify(request.copy(args = with(request, "id" to row.id.toString(), "mes" to m.toString())), title, "O que muda em \"${Fmt.clean(occ.descricao)}\": o valor, a data ou a categoria?", awaiting = "valor")
        if (row.faturaId != null && (newValue != null || newDate != null)) return unsupported(request, title, "Essa compra está numa fatura de cartão: mudar o valor ou a data recalcula a fatura, então isso é feito tocando nela na Agenda. A categoria eu consigo mudar.")
        if (newDate != null && row.isRecurringSeries && YearMonth.from(newDate) != m) return unsupported(request, title, "Uma ocorrência de lançamento recorrente não muda de mês por aqui. Posso mudar o dia dentro de ${m.monthValue}/${m.year}.")
        val change = EntryChange(newDate ?: occ.data.toLocalDate(), newValue ?: occ.valorCentavos, occ.descricao, newCategory ?: occ.categoria, occ.contaOrigem)
        val after = if (row.isRecurringSeries) RecurringSeries.editOnly(row, m, change) else RecurringSeries.editSingle(row, change)
        if (after == row) return ActionPlan(request, PlanState.Already, title, "\"${Fmt.clean(occ.descricao)}\" já está assim. Não mudei nada.")
        val preview = listOfNotNull(
            newValue?.takeIf { it != occ.valorCentavos }?.let { "Valor: ${brl(occ.valorCentavos)} → ${brl(it)}" },
            newDate?.takeIf { it != occ.data.toLocalDate() }?.let { "Data: ${Fmt.date(occ.data.toLocalDate(), today)} → ${Fmt.date(it, today)}" },
            newCategory?.takeIf { it != occ.categoria }?.let { "Categoria: ${occ.categoria} → $it" },
        )
        return ActionPlan(request, PlanState.Ready, title,
            "Alterei \"${Fmt.clean(occ.descricao)}\": ${preview.joinToString("; ").replaceFirstChar { it.lowercase() }}." +
                (if (row.isRecurringSeries) " Só a ocorrência de ${MONTH_NAMES_PT[m.monthValue - 1].lowercase()} mudou; os outros meses continuam como eram." else "") +
                (if (newCategory != null) " Não criei regra para as próximas compras nesse lugar." else ""),
            preview, listOf(RowChange.Tx(row, after)), agendaDate = change.data, focus = listOf(EntityRef(EntityRef.ENTRY, row.id, row.descricao)))
    }

    private fun updateSeries(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Alterar daqui para frente"
        val current = YearMonth.from(today)
        val series = s.transacoes.filter { it.isRecurringSeries && editable(it) && (it.occurrenceFor(current) != null || it.occurrenceFor(current.plusMonths(1)) != null) }
        val id = request.args["id"]?.toLongOrNull()
        val found = (if (id != null) series.filter { it.id == id } else NameMatch.find(request.args["texto"].orEmpty(), series) { it.descricao }).distinctBy { it.seriesRoot }
        if (found.isEmpty()) return clarify(request, title, "Não encontrei lançamento de todo mês com esse nome. Qual é o nome dele na Agenda?", awaiting = "texto")
        val row = found.singleOrNull() ?: return clarify(request, title, "Qual deles muda daqui para frente?", found.take(4).map { PendingOption("${Fmt.clean(it.descricao)} · ${brl(it.valorCentavos)}", with(request, "id" to it.id.toString())) })
        if (row.isSplitIncome || row.rendaPrincipal) return unsupported(request, title, "A renda principal é alterada em Configurações › Minha renda, que refaz as duas partes do salário juntas. Por aqui eu mudaria só um pedaço.")
        // Começa na primeira ocorrência que ainda não aconteceu: o que já passou não muda.
        val month = listOf(current, current.plusMonths(1)).firstOrNull { m -> row.occurrenceFor(m)?.data?.toLocalDate()?.isAfter(today) == true }
            ?: return unsupported(request, title, "Não achei uma próxima ocorrência desse lançamento para começar a mudança.")
        val occ = row.occurrenceFor(month)!!
        val newValue = cents(request.args["valor"], today)
        val day = request.args["dia"]?.let { Regex("""(\d{1,2})""").find(it)?.value?.toIntOrNull() }?.takeIf { it in 1..31 }
        if (newValue == null && day == null) return clarify(request.copy(args = with(request, "id" to row.id.toString())), title, "O que muda em \"${Fmt.clean(row.descricao)}\" daqui para frente: o valor ou o dia?", awaiting = "valor")
        val change = EntryChange(day?.let { month.atDay(it.coerceAtMost(month.lengthOfMonth())) } ?: occ.data.toLocalDate(), newValue ?: occ.valorCentavos, occ.descricao, occ.categoria, occ.contaOrigem)
        val edit = RecurringSeries.editFrom(row, month, change, RecurringSeries.laterSegments(row, month, s.transacoes))
        val changes = buildList {
            edit.delete.filter { it.id != row.id }.forEach { add(RowChange.Tx(it, null)) }
            add(RowChange.Tx(row, edit.update))
            edit.insert?.let { add(RowChange.Tx(null, it)) }
        }
        val preview = listOfNotNull(
            newValue?.let { "Valor: ${brl(occ.valorCentavos)} → ${brl(it)}" }, day?.let { "Dia: ${occ.data.toLocalDate().dayOfMonth} → $it" },
            "Vale a partir de ${MONTH_NAMES_PT[month.monthValue - 1].lowercase()} de ${month.year}, em todos os meses seguintes. O que já passou não muda.",
        )
        return ActionPlan(request, PlanState.Confirm, title,
            "Alterei \"${Fmt.clean(row.descricao)}\" de ${MONTH_NAMES_PT[month.monthValue - 1].lowercase()} em diante: " +
                listOfNotNull(newValue?.let { "valor ${brl(it)}" }, day?.let { "dia $it" }).joinToString(" e ") + ". Os meses anteriores ficaram como estavam.",
            preview, changes, question = "Isso muda \"${Fmt.clean(row.descricao)}\" em todos os meses a partir de ${MONTH_NAMES_PT[month.monthValue - 1].lowercase()}. Confirma?",
            agendaDate = change.data, focus = listOf(EntityRef(EntityRef.ENTRY, row.id, row.descricao)))
    }

    private fun deleteEntry(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Excluir lançamento"
        val args = request.args.toMutableMap()
        // Na exclusão, "valor" e "data" identificam o alvo (não há valor novo).
        args.remove("valor")?.let { args.putIfAbsent("valor_atual", it) }
        args.remove("data")?.let { args.putIfAbsent("quando", it) }
        val lookup = request.copy(args = args)
        val month = args["quando"]?.let { parseDate(it, today) }?.let(YearMonth::from) ?: args["mes"]?.let { parseMonth(it, today) } ?: YearMonth.from(today)
        // Aporte também se exclui por aqui: a meta volta ao valor de antes (mostrado na confirmação).
        val found = findEntries(s, lookup, month, lookBack = args["quando"] == null && args["mes"] == null, withContributions = true)
        if (found.isEmpty()) return clarify(request, title, "Não encontrei esse lançamento. Qual é o nome dele na Agenda (e o dia, se souber)?", awaiting = "texto")
        val (row, m) = found.singleOrNull() ?: return clarify(request, title, "Encontrei mais de um parecido. Qual deles eu excluo? (Só excluo o que você escolher.)",
            found.take(4).map { (r, mo) -> PendingOption(entryLabel(r.occurrenceFor(mo) ?: r, today), args + mapOf("id" to r.id.toString(), "mes" to mo.toString())) })
        val occ = (if (row.isRecurringSeries) row.occurrenceFor(m) else row) ?: return unsupported(request, title, "Esse lançamento não tem ocorrência nesse mês.")
        if (row.faturaId != null) return unsupported(request, title, "Essa compra está numa fatura de cartão: excluir recalcula a fatura, então isso é feito tocando nela na Agenda.")
        val label = entryLabel(occ, today)
        val changes = mutableListOf<RowChange>()
        val effects = mutableListOf<String>()
        if (row.isRecurringSeries) {
            changes += RowChange.Tx(row, RecurringSeries.deleteOnly(row, m))
            effects += "É um lançamento de todo mês: só a ocorrência de ${MONTH_NAMES_PT[m.monthValue - 1].lowercase()} some; os outros meses continuam."
        } else {
            changes += RowChange.Tx(row, null)
            row.objetivoId?.let { goalId ->
                val goal = s.objetivos.firstOrNull { it.id == goalId }
                if (goal != null) {
                    val after = Completion.onGoalSaved(goal, goal.copy(valorGuardadoCentavos = (goal.valorGuardadoCentavos - row.valorCentavos).coerceAtLeast(0)), s.nowMillis).objetivo
                    changes += RowChange.Goal(goal, after)
                    effects += "É um aporte: \"${Fmt.clean(goal.nome)}\" volta de ${brl(goal.valorGuardadoCentavos)} para ${brl(after.valorGuardadoCentavos)}."
                }
            }
            if (row.origem == DebtSchedule.PAYMENT_ORIGIN) {
                val debt = DebtSchedule.debtNameOf(row)?.let { name -> s.dividas.firstOrNull { it.nome == name } }
                    ?: return unsupported(request, title, "É um pagamento de parcela, mas não encontrei a dívida dele. Exclua pela Agenda.")
                val after = Completion.onDebtSaved(debt, DebtSchedule.undoPayment(debt, row), s.nowMillis).divida
                changes += RowChange.Debt(debt, after)
                effects += "É um pagamento de parcela: \"${Fmt.clean(debt.nome)}\" volta a ter ${after.parcelasRestantes} parcela(s) em aberto."
            }
        }
        return ActionPlan(request.copy(args = args + mapOf("id" to row.id.toString(), "mes" to m.toString())), PlanState.Confirm, title,
            "Excluí $label." + effects.joinToString("") { " $it" }, preview = listOf("Excluir: $label") + effects, changes = changes,
            question = "Vou excluir $label. Confirma?", agendaDate = occ.data.toLocalDate())
    }

    // ── acompanhamento, memória e plano ─────────────────────────────────────

    private fun createWatch(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val today = s.today
        val title = "Acompanhamento"
        val type = Fmt.fold(request.args["tipo"].orEmpty())
        val value = cents(request.args["valor"], today)
        val rule = when {
            type.startsWith("quit") || type.startsWith("div") -> {
                val id = request.args["id"]?.toLongOrNull()
                val debts = if (id != null) s.openDebts.filter { it.id == id } else AssistantTools.findDebt(s, request.args["alvo"].orEmpty())
                if (debts.isEmpty()) return clarify(request, title, "Não encontrei essa dívida. Qual é o nome dela em Planos?", awaiting = "alvo")
                val d = debts.singleOrNull() ?: return clarify(request, title, "Qual dívida eu acompanho?", debts.take(4).map { PendingOption(it.nome, with(request, "id" to it.id.toString())) })
                AcompanhamentoEntity(tipo = WatchRules.PAYOFF, alvo = d.nome, alvoId = d.id, valorCentavos = value ?: 0, descricao = "", estado = WatchRules.ACTIVE, criadoEm = s.nowMillis)
            }
            type.startsWith("cat") || type.startsWith("gasto") || type.startsWith("limite") -> {
                val cat = MerchantClassifier.normalizeCategoryName(request.args["alvo"].orEmpty())
                    ?: return clarify(request, title, "De qual categoria?", Categorias.all.take(6).map { PendingOption(it, with(request, "alvo" to it)) })
                AcompanhamentoEntity(tipo = WatchRules.CATEGORY, alvo = cat, valorCentavos = value ?: return clarify(request.copy(args = with(request, "alvo" to cat)), title, "Aviso quando $cat passar de quanto no mês?", awaiting = "valor"),
                    descricao = "", estado = WatchRules.ACTIVE, criadoEm = s.nowMillis)
            }
            type.startsWith("saldo") -> AcompanhamentoEntity(tipo = WatchRules.BALANCE, alvo = "", valorCentavos = value ?: return clarify(request, title, "Aviso quando o saldo previsto ficar abaixo de quanto?", awaiting = "valor"),
                descricao = "", estado = WatchRules.ACTIVE, criadoEm = s.nowMillis)
            else -> AcompanhamentoEntity(tipo = WatchRules.PURCHASE, alvo = request.args["alvo"].orEmpty().take(40), valorCentavos = value ?: return clarify(request, title, "Qual é o valor que precisa caber?", awaiting = "valor"),
                descricao = "", estado = WatchRules.ACTIVE, criadoEm = s.nowMillis)
        }
        val described = rule.copy(descricao = WatchRules.describe(rule))
        if (s.acompanhamentos.any { it.estado == WatchRules.ACTIVE && it.tipo == rule.tipo && it.alvoId == rule.alvoId && Fmt.fold(it.alvo) == Fmt.fold(rule.alvo) && it.valorCentavos == rule.valorCentavos })
            return ActionPlan(request, PlanState.Already, title, "Esse acompanhamento já existe: ${described.descricao.replaceFirstChar { it.lowercase() }}.")
        val now = WatchRules.evaluate(described, s)
        // Nasce já avaliado: se a condição vale hoje, a resposta diz; o aviso só sai numa mudança futura.
        val stored = described.copy(ultimaAvaliacao = today.toString(), ultimoResultado = WatchRules.resultKey(now, today))
        return ActionPlan(request, PlanState.Ready, title,
            "Combinado: ${described.descricao.replaceFirstChar { it.lowercase() }}. " + (if (now.met) "Hoje a condição já vale: ${now.detail} " else "Hoje ainda não vale. ") +
                "Reavalio quando seus dados mudarem e na checagem diária; o aviso aparece na Caixa. Para parar, diga \"cancele o acompanhamento\".",
            preview = listOf(described.descricao), changes = listOf(RowChange.Watch(null, stored)),
            notes = listOfNotNull(if (rule.tipo == WatchRules.PAYOFF && rule.valorCentavos == 0L) "Uso o saldo em aberto cadastrado; se o credor der um valor de quitação, me diga." else null,
                "A checagem é diária, não no minuto em que a condição muda."))
    }

    private fun cancelWatch(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val title = "Cancelar acompanhamento"
        val active = s.acompanhamentos.filter { it.estado == WatchRules.ACTIVE }
        if (active.isEmpty()) return ActionPlan(request, PlanState.Already, title, "Não há acompanhamento ativo.")
        val id = request.args["id"]?.toLongOrNull()
        val query = request.args["alvo"].orEmpty()
        val found = if (id != null) active.filter { it.id == id } else if (query.isBlank()) active else NameMatch.find(query, active) { it.descricao + " " + it.alvo }
        val rule = found.singleOrNull() ?: return clarify(request, title, "Qual acompanhamento eu cancelo?", found.ifEmpty { active }.take(4).map { PendingOption(it.descricao, with(request, "id" to it.id.toString())) })
        return ActionPlan(request, PlanState.Ready, title, "Cancelei: ${rule.descricao.replaceFirstChar { it.lowercase() }}. Não aviso mais sobre isso.",
            preview = listOf("Cancelar: ${rule.descricao}"), changes = listOf(RowChange.Watch(rule, null)))
    }

    private fun remember(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val title = "Preferência"
        val text = request.args["texto"].orEmpty().trim().replaceFirstChar { it.uppercase() }.take(160)
        if (text.length < 4) return clarify(request, title, "O que você quer que eu lembre?", awaiting = "texto")
        if (s.memoria.any { Fmt.fold(it.texto) == Fmt.fold(text) }) return ActionPlan(request, PlanState.Already, title, "Isso já está guardado em \"Sei sobre você\".")
        return ActionPlan(request, PlanState.Ready, title, "Guardei: \"$text\". Vale para as próximas conversas; você pode apagar em \"Sei sobre você\", no topo do chat.",
            preview = listOf("Lembrar: $text"), changes = listOf(RowChange.Memory(null, MemoriaEntity(texto = text, tipo = "preferencia", criadoEm = s.nowMillis, origem = "chat"))))
    }

    private fun savePlan(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val plan = SavedPlan.of(s)
        val month = MONTH_NAMES_PT[plan.month.monthValue - 1].lowercase()
        return ActionPlan(request, PlanState.Ready, "Plano aceito",
            "Salvei o plano de $month como está hoje: renda ${brl(plan.incomeCents)}, fixos ${brl(plan.fixedCents)}, dia a dia ${brl(plan.variableCents)}" +
                (if (plan.goals.isNotEmpty()) ", metas " + plan.goals.joinToString(", ") { "${Fmt.clean(it.second)} ${brl(it.third)}" } else "") +
                ". Pergunte \"estou seguindo o plano?\" quando quiser comparar. Nenhum aporte foi registrado.",
            preview = listOf("Plano de $month"), changes = listOf(RowChange.Plan(null, PlanoEntity(mes = plan.month.toString(), criadoEm = s.nowMillis, conteudo = plan.encode()))))
    }
}
