package com.finai.app.domain.assistant

import com.finai.app.data.local.entity.AcompanhamentoEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.MemoriaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.PlanoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.plans.SavingsCapacity
import com.finai.app.domain.quickentry.QuickEntryContext
import com.finai.app.domain.quickentry.QuickEntryParser
import com.finai.app.domain.toEpochMillis
import com.finai.app.util.formatBrl
import com.finai.app.util.formatBrl0
import java.time.LocalDate

/**
 * Retrato financeiro consistente (§5.1): tudo o que as consultas e as ações do assistente leem,
 * lido do Room numa transação só, com a data e a margem daquele instante. As ferramentas nunca
 * partem de texto de tela nem de emissões parciais dos flows.
 */
data class AssistantSnapshot(
    val contas: List<ContaEntity>,
    val transacoes: List<TransacaoEntity>,
    val dividas: List<DividaEntity>,
    val marginCents: Long,
    val today: LocalDate,
    val objetivos: List<ObjetivoEntity> = emptyList(),
    val orcamentos: List<OrcamentoCategoriaEntity> = emptyList(),
    val faturas: List<FaturaCartaoEntity> = emptyList(),
    val memoria: List<MemoriaEntity> = emptyList(),
    /** Estabelecimento normalizado → categoria aprendida. */
    val regras: Map<String, String> = emptyMap(),
    val acompanhamentos: List<AcompanhamentoEntity> = emptyList(),
    val planos: List<PlanoEntity> = emptyList(),
    val nowMillis: Long = today.toEpochMillis(),
    val extraAccounts: Collection<String> = emptyList(),
) {
    /** Identifica o retrato: muda quando qualquer dado financeiro, a margem ou o dia mudam. */
    val id: String by lazy {
        Integer.toHexString(listOf(contas, transacoes, dividas, objetivos, orcamentos, marginCents, today).hashCode())
    }

    val cards: List<String> by lazy { EntryOptions.knownCards(faturas, transacoes) }
    val accounts: List<String> by lazy { EntryOptions.recentAccounts(transacoes, cards, extraAccounts) }

    val quick: QuickEntryContext by lazy {
        QuickEntryContext(accounts = accounts, cards = cards, goals = objetivos.filter { it.concluidoEm == null }.map { it.nome }, learned = regras)
    }

    val capacity: SavingsCapacity by lazy { SavingsCapacity.of(contas, transacoes, dividas, today) }

    val openDebts: List<DividaEntity> get() = dividas.filter { it.quitadaEm == null && (it.valorAbertoCentavos > 0 || it.parcelasRestantes > 0) }
    val openGoals: List<ObjetivoEntity> get() = objetivos.filter { it.concluidoEm == null }
}

/** Referência a um registro que a conversa citou, para "essa dívida" e "a mesma meta" voltarem a ele. */
data class EntityRef(val type: String, val id: Long, val label: String) {
    companion object {
        const val DEBT = "divida"
        const val GOAL = "meta"
        const val BILL = "conta"
        const val ENTRY = "lancamento"
    }
}

enum class AnswerKind { Query, Explanation, Decision, Plan, Action, Question, Education }
enum class Tone { Neutral, Good, Warn, Bad }

data class AnswerMetric(val label: String, val value: String, val tone: Tone = Tone.Neutral)

/** Botão de resposta: [send] é a mensagem que o toque envia (o mesmo caminho de digitar). */
data class AnswerOption(val label: String, val send: String = label)

/** Estado de uma escrita, como o executor devolveu. A resposta de sucesso só existe a partir daqui. */
enum class ActionStatus(val key: String) {
    Applied("applied"), AlreadyApplied("already_applied"), NeedsClarification("needs_clarification"),
    NeedsConfirmation("needs_confirmation"), Conflict("conflict"), Unsupported("unsupported"), Failed("failed"), Undone("undone");

    companion object { fun of(key: String?) = values().firstOrNull { it.key == key } }
}

data class ActionInfo(val status: ActionStatus, val actionId: Long? = null, val canUndo: Boolean = false, val agendaDate: LocalDate? = null)

/**
 * Resposta estruturada (§5.6): os valores, datas e o estado da ação são montados pelo app a
 * partir do resultado local; a IA só pode acrescentar explicação em texto. Cada tipo de resposta
 * usa só os campos que fazem sentido para ele.
 */
data class AssistantAnswer(
    val kind: AnswerKind,
    val title: String,
    val headline: String = "",
    val metrics: List<AnswerMetric> = emptyList(),
    /** Registros e contas que explicam o total. */
    val evidence: List<String> = emptyList(),
    /** Hipóteses, limites do que foi verificado e dados que faltam. */
    val notes: List<String> = emptyList(),
    val options: List<AnswerOption> = emptyList(),
    val action: ActionInfo? = null,
) {
    /** A mesma resposta em texto: histórico da conversa, modo sem cartão e fatos para a IA. */
    fun plainText(): String = buildString {
        if (headline.isNotBlank()) appendLine(headline)
        metrics.forEach { appendLine("- ${it.label}: ${it.value}") }
        evidence.forEach { appendLine("- $it") }
        notes.forEach { appendLine(it) }
    }.trim().ifBlank { title }
}

/** Proposta ou pergunta à espera do usuário: sobrevive ao processo junto com a conversa. */
data class PendingAction(
    val request: ActionRequest,
    /** "confirm" (falta o sim) ou "clarify" (falta um dado ou escolher o alvo). */
    val kind: String,
    /** Opções oferecidas: rótulo → argumentos que a escolha acrescenta ao pedido. */
    val options: List<PendingOption> = emptyList(),
    /** Mensagem do usuário que pediu a ação (a autorização é dela). */
    val messageId: Long = 0,
    val snapshotId: String = "",
) {
    companion object { const val CONFIRM = "confirm"; const val CLARIFY = "clarify" }
}

data class PendingOption(val label: String, val args: Map<String, String>)

/**
 * Estado da conversa (§5.7, camada 2): o que está pendente e o que está em foco. Valores de
 * saldo, dívida e meta nunca ficam aqui; a referência volta ao dado atual.
 */
data class AssistantSession(
    val pending: PendingAction? = null,
    val focus: List<EntityRef> = emptyList(),
    val lastActionId: Long? = null,
    /** Último cenário simulado, para "e se for em 6x?" e "e se eu esperar?". */
    val lastScenario: ToolCall? = null,
) {
    fun focusOf(type: String): EntityRef? = focus.firstOrNull { it.type == type }

    fun withFocus(refs: List<EntityRef>): AssistantSession =
        if (refs.isEmpty()) this else copy(focus = (refs + focus).distinctBy { it.type }.take(4))
}

internal object Fmt {
    /** Valor com centavos só quando eles existem: "R$ 45" e "R$ 45,50". */
    fun money(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        val text = if (abs % 100 == 0L) formatBrl0(abs / 100) else formatBrl(abs / 100.0)
        return if (cents < 0) "-$text" else text
    }

    /** Centavos como argumento de ferramenta ("45.00"), o formato que [QuickEntryParser] relê sem ambiguidade. */
    fun arg(cents: Long): String = "${cents / 100}.${"%02d".format(cents % 100)}"

    fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

    /** Dia e mês; com o ano quando não é o de [today]. */
    fun date(d: LocalDate, today: LocalDate) = if (d.year == today.year) dm(d) else "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)

    fun day(d: LocalDate, today: LocalDate) = when (d) {
        today -> "hoje, ${dm(d)}"
        today.minusDays(1) -> "ontem, ${dm(d)}"
        else -> date(d, today)
    }

    fun fold(text: String) = QuickEntryParser.fold(text).trim()

    /** Nome sem dígitos longos (final de cartão, código de loja) e curto: o que pode ir para a IA. */
    fun clean(text: String) = text.replace(Regex("""\d{4,}"""), "").replace(Regex("""\s+"""), " ").trim().take(32)
}

/**
 * Procura um registro pelo nome que o usuário falou. Igualdade vence; depois, o nome que contém
 * a busca (ou o contrário); por último, palavras em comum. Mais de um resultado = ambíguo: quem
 * chama pergunta, nunca escolhe o "mais parecido".
 */
internal object NameMatch {
    private val STOP = setOf("de", "do", "da", "dos", "das", "o", "a", "os", "as", "meu", "minha", "para", "pra", "pro", "no", "na", "em", "conta", "fatura", "boleto", "cartao", "divida", "meta", "objetivo")

    fun <T> find(query: String, items: List<T>, name: (T) -> String): List<T> {
        val q = Fmt.fold(query)
        if (q.isBlank()) return emptyList()
        val exact = items.filter { Fmt.fold(name(it)) == q }
        if (exact.isNotEmpty()) return exact
        val contains = items.filter { val n = Fmt.fold(name(it)); n.isNotBlank() && (n.contains(q) || q.contains(n)) }
        if (contains.isNotEmpty()) return contains
        val tokens = q.split(Regex("""[^a-z0-9]+""")).filter { it.length >= 3 && it !in STOP }
        if (tokens.isEmpty()) return emptyList()
        return items.filter { item -> val n = Fmt.fold(name(item)); tokens.any { Regex("""\b${Regex.escape(it)}""").containsMatchIn(n) } }
    }
}
