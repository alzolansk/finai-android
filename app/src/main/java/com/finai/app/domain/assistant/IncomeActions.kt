package com.finai.app.domain.assistant

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.TransactionType
import com.finai.app.domain.quickentry.QuickEntryParser
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import java.time.LocalDate

/** Receitas avulsas previstas, tanto as da Agenda quanto as lançadas como renda extra. */
internal object IncomeActions {
    data class Income(val tx: TransacaoEntity? = null, val bill: ContaEntity? = null) {
        val id get() = tx?.id ?: bill!!.id
        val type get() = if (tx != null) EntityRef.ENTRY else EntityRef.BILL
        val name get() = tx?.descricao ?: bill!!.nome
        val cents get() = tx?.valorCentavos ?: bill!!.valorCentavos
        val date get() = (tx?.data ?: bill!!.vencimento).toLocalDate()
        fun ref() = EntityRef(type, id, name)
        fun label(today: LocalDate) = "$name · ${Fmt.money(cents)} · ${Fmt.date(date, today)}"
        fun change(value: Long, on: LocalDate, label: String = name, new: Boolean = false): RowChange =
            if (tx != null) RowChange.Tx(if (new) null else tx, tx.copy(id = if (new) 0 else tx.id,
                valorCentavos = value, data = on.toEpochMillis(), descricao = label))
            else RowChange.Bill(if (new) null else bill, bill!!.copy(id = if (new) 0 else bill.id,
                valorCentavos = value, vencimento = on.toEpochMillis(), nome = label))
    }

    private fun key(text: String) = Fmt.fold(text).replace(Regex("""\b(decimo\s+terceiro|13[ºo°]?)\b"""), "decimo")
    fun planned(s: AssistantSnapshot): List<Income> =
        s.transacoes.filter { it.tipo == TransactionType.Receita.name && !it.recorrente && it.rendaGrupo == null && it.faturaId == null && it.data.toLocalDate().isAfter(s.today) }.map { Income(tx = it) } +
            s.contas.filter { it.tipo == "a_receber" && it.status != "pago" && !it.recorrente && it.vencimento.toLocalDate().isAfter(s.today) }.map { Income(bill = it) }

    fun find(s: AssistantSnapshot, text: String): List<Income> {
        val q = key(text)
        // "Décimo" é um assunto, não um identificador único entre empregadores/parcelas.
        return if (q in setOf("decimo", "bonus", "freela", "restituicao", "comissao", "reembolso"))
            planned(s).filter { Regex("""\b${Regex.escape(q)}\b""").containsMatchIn(key(it.name)) }
        else NameMatch.find(q, planned(s)) { key(it.name) }
    }

    fun plan(request: ActionRequest, s: AssistantSnapshot): ActionPlan {
        val title = if (request.name == ActionPlanner.SPLIT_INCOME) "Dividir receita prevista" else "Alterar receita prevista"
        fun clarify(r: ActionRequest, question: String, awaiting: String? = null, options: List<PendingOption> = emptyList()) =
            ActionPlan(r, PlanState.Clarify, title, question = question, awaiting = awaiting, options = options)
        val id = request.args["id"]?.toLongOrNull()
        val found = if (id != null) planned(s).filter { it.id == id && it.type == request.args["registro"] }
            else find(s, request.args["texto"].orEmpty())
        if (found.isEmpty()) return clarify(request.copy(args = request.args - "id" - "registro"),
            "Não encontrei essa receita avulsa futura. Qual é o nome dela na Agenda?", "texto")
        val source = found.singleOrNull() ?: return clarify(request, "Qual receita você quer alterar?", options = found.take(6).map {
            PendingOption(it.label(s.today), request.args + mapOf("id" to it.id.toString(), "registro" to it.type))
        })
        // Prende o alvo e sua versão na proposta: uma mudança externa invalida a confirmação.
        val sourceVersion = (source.tx ?: source.bill).hashCode().toString()
        val pinned = request.copy(args = request.args + mapOf("id" to source.id.toString(), "registro" to source.type,
            "total_original" to (request.args["total_original"] ?: source.cents.toString()),
            "data_original" to (request.args["data_original"] ?: source.date.toString()),
            "versao_original" to (request.args["versao_original"] ?: sourceVersion)))
        if (pinned.args["versao_original"] != sourceVersion || pinned.args["total_original"] != source.cents.toString() || pinned.args["data_original"] != source.date.toString()) {
            return ActionPlan(pinned, PlanState.Unsupported, title, "Essa receita mudou desde a proposta. Peça a alteração novamente para eu usar o registro atual.")
        }
        fun date(arg: String) = ActionPlanner.parseFutureDate(pinned.args[arg], s.today)
        if (request.name == ActionPlanner.MOVE_INCOME) {
            val on = date("data") ?: return clarify(pinned, "Para qual data vai ${source.name}?", "data")
            if (!on.isAfter(s.today)) return clarify(pinned, "Essa é uma receita prevista. Qual é a nova data futura?", "data")
            if (on == source.date) return ActionPlan(pinned, PlanState.Already, title, "${source.name} já está prevista para ${Fmt.date(on, s.today)}, no valor de ${Fmt.money(source.cents)}.", focus = listOf(source.ref()))
            return ActionPlan(pinned, PlanState.Ready, title, "Mudei ${source.name} para ${Fmt.date(on, s.today)}. O valor continua ${Fmt.money(source.cents)}.",
                preview = listOf("${source.label(s.today)} → ${Fmt.date(on, s.today)}"), changes = listOf(source.change(source.cents, on)), agendaDate = on, focus = listOf(source.ref()))
        }
        val first = date("primeira_data") ?: return clarify(pinned, "Em que data cai a primeira parcela de ${source.name}?", "primeira_data")
        // A data original só é reaproveitada como segunda na proposta completa que será confirmada.
        val second = date("segunda_data") ?: source.date.takeIf { pinned.args["segunda_data"] == null && it > first }
            ?: return clarify(pinned, "Em que data cai a segunda parcela?", "segunda_data")
        if (!first.isAfter(s.today) || second <= first) return clarify(pinned.copy(args = pinned.args - "primeira_data" - "segunda_data"),
            "Preciso de duas datas futuras em ordem. Em que data cai a primeira parcela?", "primeira_data")
        val firstAmount = if (pinned.args["primeiro_valor"] == null) source.cents / 2 else
            QuickEntryParser.parse("x ${pinned.args["primeiro_valor"]}", s.today).cents
                ?: return clarify(pinned, "Qual é o valor da primeira parcela? Informe o valor em reais.", "primeiro_valor")
        if (firstAmount <= 0 || firstAmount >= source.cents) return clarify(pinned, "Qual é o valor da primeira parcela? Ele precisa ser menor que o total de ${Fmt.money(source.cents)}.", "primeiro_valor")
        val rest = source.cents - firstAmount
        val rows = listOf(source.change(firstAmount, first, "${source.name} · 1/2"), source.change(rest, second, "${source.name} · 2/2", new = true))
        val preview = listOf("Receita original: ${source.label(s.today)}", "1ª parcela: ${Fmt.money(firstAmount)} em ${Fmt.date(first, s.today)}",
            "2ª parcela: ${Fmt.money(rest)} em ${Fmt.date(second, s.today)}", "Total preservado: ${Fmt.money(source.cents)}. A receita original será substituída pelas duas parcelas.")
        return ActionPlan(pinned, PlanState.Confirm, title, "Dividi ${source.name}: ${Fmt.money(firstAmount)} em ${Fmt.date(first, s.today)} e ${Fmt.money(rest)} em ${Fmt.date(second, s.today)}. Total: ${Fmt.money(source.cents)}.",
            preview = preview, changes = rows, question = "Confirma essa divisão?", agendaDate = first, focus = listOf(source.ref()),
            notes = listOfNotNull("Propus metade em cada parcela; o centavo que sobrar fica na segunda. Se os valores forem diferentes, diga o valor da primeira.".takeIf { pinned.args["primeiro_valor"] == null },
                "Mantive a data original na segunda parcela.".takeIf { pinned.args["segunda_data"] == null }))
    }
}
