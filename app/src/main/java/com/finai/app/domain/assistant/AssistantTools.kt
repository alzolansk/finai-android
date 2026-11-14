package com.finai.app.domain.assistant

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Categorias
import com.finai.app.domain.CashFlowTimeline
import com.finai.app.domain.CycleSource
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.balance.AccountBalance
import com.finai.app.domain.balance.FreeToSpend
import com.finai.app.domain.importer.MerchantClassifier
import com.finai.app.domain.quickentry.QuickEntryContext
import com.finai.app.domain.quickentry.QuickEntryParser
import com.finai.app.domain.scenario.PurchaseSimulatorV2
import com.finai.app.domain.scenario.PurchaseWay
import com.finai.app.domain.spending.SpendingAnalysis
import com.finai.app.domain.toLocalDate
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Pedido de consulta que a IA devolve: nome da ferramenta e argumentos em texto. */
data class ToolCall(val name: String, val args: Map<String, String>)

/** Resultado de uma ferramenta: o que aparece em "consultei: …" e os fatos que a resposta pode citar. */
data class ToolResult(val label: String, val facts: String)

data class AssistantData(
    val contas: List<ContaEntity>,
    val transacoes: List<TransacaoEntity>,
    val dividas: List<DividaEntity>,
    val marginCents: Long,
    val quick: QuickEntryContext = QuickEntryContext(),
    val fitsToSaveCents: Long? = null,
)

/**
 * As ferramentas do assistente (planning-v2, fase 9, P1). Todas locais e determinísticas: a IA
 * só escolhe **qual consulta fazer**; o número vem daqui e vira o bloco de fatos do
 * [com.finai.app.domain.scenario.ScenarioReplyGuard].
 */
object AssistantTools {
    const val MAX_CALLS = 3

    /** O que entra no prompt para a IA saber o que pode pedir. Curto de propósito (cota gratuita). */
    val CATALOG: String = """
        Se precisar de um dado que não está no resumo, responda SOMENTE com uma linha JSON, sem mais nada:
        {"consulta":"buscar_lancamentos","texto":"ifood","periodo":"mes_atual"}
        {"consulta":"total_por_categoria","categoria":"Alimentação","periodo":"mes_passado"}
        {"consulta":"comparar_periodos","categoria":"Lazer","a":"mes_passado","b":"mes_atual"}
        {"consulta":"saldo_previsto","data":"2026-12-10"}
        {"consulta":"livre_hoje"}
        {"consulta":"simular_compra","valor":"1200","forma":"3x"}
        Períodos aceitos: mes_atual, mes_passado, ano, ultimos_30_dias ou um mês no formato 2026-10.
        Categorias: ${Categorias.all.joinToString(", ")}.
        Use consulta só quando a pergunta pede um número que você não recebeu. Nunca invente o resultado.
    """.trimIndent()

    private val PAIR = Regex(""""([a-z_]+)"\s*:\s*(?:"([^"]*)"|(-?[\d.,]+))""")

    /** Lê a linha JSON de consulta; nulo quando a resposta já é texto para o usuário. */
    fun parseCall(reply: String): ToolCall? {
        val line = reply.lines().map { it.trim().trim('`') }.firstOrNull { it.startsWith("{") && it.contains("\"consulta\"") } ?: return null
        val args = PAIR.findAll(line).associate { it.groupValues[1] to (it.groupValues[2].ifEmpty { it.groupValues[3] }) }
        val name = args["consulta"]?.takeIf { it in NAMES } ?: return null
        return ToolCall(name, args - "consulta")
    }

    val NAMES = setOf("buscar_lancamentos", "total_por_categoria", "comparar_periodos", "saldo_previsto", "livre_hoje", "simular_compra", "rascunho_lancamento")

    private fun brl(c: Long) = formatBrl0(c / 100.0)
    private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

    /** Intervalo de datas de um período em texto; o padrão é o mês atual. */
    fun period(text: String?, today: LocalDate): Triple<LocalDate, LocalDate, String> {
        val t = QuickEntryParser.fold(text.orEmpty()).replace(' ', '_')
        val current = YearMonth.from(today)
        fun month(m: YearMonth) = Triple(m.atDay(1), minOf(m.atEndOfMonth(), today), MONTH_NAMES_PT[m.monthValue - 1].lowercase() + if (m.year != today.year) " de ${m.year}" else "")
        Regex("""(\d{4})-(\d{2})""").matchEntire(t)?.let { m ->
            runCatching { YearMonth.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()?.let { return month(it) }
        }
        Regex("""ultimos_(\d{1,3})_dias""").matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toLong().coerceIn(1, 366)
            return Triple(today.minusDays(n - 1), today, "últimos $n dias")
        }
        return when (t) {
            "mes_passado" -> month(current.minusMonths(1))
            "ano", "ano_atual" -> Triple(LocalDate.of(today.year, 1, 1), today, today.year.toString())
            else -> month(current)
        }
    }

    private fun spending(data: AssistantData, from: LocalDate, to: LocalDate): List<TransacaoEntity> {
        val months = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(to) }
        return months.flatMap { SpendingAnalysis.monthEntries(data.transacoes, it).asSequence() }
            .filter { val d = it.data.toLocalDate(); !d.isBefore(from) && !d.isAfter(to) }.toList()
    }

    fun run(call: ToolCall, data: AssistantData, today: LocalDate): ToolResult = when (call.name) {
        "buscar_lancamentos" -> {
            val (from, to, label) = period(call.args["periodo"], today)
            val q = QuickEntryParser.fold(call.args["texto"].orEmpty()).trim()
            val found = spending(data, from, to).filter { q.isBlank() || QuickEntryParser.fold(it.descricao + " " + it.categoria).contains(q) }
                .sortedByDescending { it.data }
            val facts = if (found.isEmpty()) "Nenhum gasto com \"${call.args["texto"].orEmpty()}\" em $label."
            else buildString {
                appendLine("${found.size} gasto(s) com \"${call.args["texto"].orEmpty()}\" em $label, somando ${brl(found.sumOf { it.valorCentavos })}.")
                found.take(8).forEach { appendLine("- ${dm(it.data.toLocalDate())}: ${MerchantClassifier.normalizeMerchant(it.descricao).take(28)}, ${brl(it.valorCentavos)}") }
                if (found.size > 8) appendLine("(mais ${found.size - 8} não listados)")
            }
            ToolResult("\"${call.args["texto"].orEmpty()}\" em $label", facts)
        }
        "total_por_categoria" -> {
            val (from, to, label) = period(call.args["periodo"], today)
            val cat = MerchantClassifier.normalizeCategoryName(call.args["categoria"].orEmpty())
            val all = spending(data, from, to)
            val facts = if (cat == null) {
                "Gasto por categoria em $label: " + all.groupBy { it.categoria }.map { it.key to it.value.sumOf { t -> t.valorCentavos } }
                    .sortedByDescending { it.second }.joinToString("; ") { "${it.first} ${brl(it.second)}" }.ifBlank { "nenhum gasto" } + "."
            } else {
                val list = all.filter { it.categoria == cat }
                "$cat em $label: ${brl(list.sumOf { it.valorCentavos })} em ${list.size} lançamento(s)."
            }
            ToolResult("${cat ?: "categorias"} em $label", facts)
        }
        "comparar_periodos" -> {
            val a = period(call.args["a"] ?: "mes_passado", today)
            val b = period(call.args["b"] ?: "mes_atual", today)
            val cat = MerchantClassifier.normalizeCategoryName(call.args["categoria"].orEmpty())
            fun total(p: Triple<LocalDate, LocalDate, String>) = spending(data, p.first, p.second).filter { cat == null || it.categoria == cat }.sumOf { it.valorCentavos }
            val ta = total(a); val tb = total(b); val diff = tb - ta
            val what = cat ?: "Gastos"
            ToolResult("$what: ${a.third} × ${b.third}",
                "$what em ${a.third}: ${brl(ta)}. $what em ${b.third}: ${brl(tb)}. Diferença: ${brl(kotlin.math.abs(diff))} ${if (diff >= 0) "a mais" else "a menos"} em ${b.third}.")
        }
        "saldo_previsto" -> {
            val date = runCatching { LocalDate.parse(call.args["data"].orEmpty()) }.getOrNull()?.takeIf { !it.isBefore(today) }
                ?.let { minOf(it, today.plusMonths(6)) } ?: today
            val start = AccountBalance.startMonthFor(data.transacoes, data.contas, today)
            val months = ChronoUnit.MONTHS.between(start, YearMonth.from(date)).toInt() + 1
            val all = CashFlowTimeline.monthMovements(data.contas, data.transacoes, data.dividas, today, months, from = start)
            val account = all.filter { AccountBalance.settled(it, today) }.sumOf { it.cents }
            val ahead = all.filterNot { AccountBalance.settled(it, today) }.filter { m ->
                when {
                    m.date.isAfter(date) -> false
                    m.date.isBefore(today) -> m.cents < 0
                    m.date == today && m.cents > 0 && m.source is CycleSource.Conta -> false
                    else -> true
                }
            }.sumOf { it.cents }
            ToolResult("saldo previsto em ${dm(date)}", "Saldo em conta hoje: ${brl(account)}. Saldo previsto no fim de ${dm(date)}, com o que já está lançado: ${brl(account + ahead)}.")
        }
        "livre_hoje" -> {
            val free = FreeToSpend.of(data.contas, data.transacoes, data.dividas, today, data.marginCents)
            ToolResult("livre de hoje", free?.aiBlock(6) ?: "Sem renda cadastrada: o app não calcula o livre até a próxima entrada.")
        }
        "simular_compra" -> {
            val cents = QuickEntryParser.parse(call.args["valor"].orEmpty(), today).cents ?: 0L
            val n = Regex("""(\d{1,2})""").find(call.args["forma"].orEmpty())?.value?.toIntOrNull()
            val ways = (PurchaseSimulatorV2.WAYS + listOfNotNull(n?.takeIf { it in 1..24 }?.let(::PurchaseWay))).distinct().sortedBy { it.installments }
            if (cents <= 0) ToolResult("simulação de compra", "Faltou o valor da compra para simular.")
            else ToolResult("compra de ${brl(cents)}", PurchaseSimulatorV2.simulate(data.contas, data.transacoes, data.dividas, today, cents, data.marginCents,
                fitsToSaveCents = data.fitsToSaveCents, ways = ways).factsBlock(""))
        }
        else -> {
            val d = QuickEntryParser.parse(call.args["frase"].orEmpty(), today, data.quick)
            ToolResult("rascunho de lançamento", "Rascunho: ${d.type.label}, ${d.cents?.let(::brl) ?: "sem valor"}, ${d.description.ifBlank { "sem descrição" }}, ${dm(d.date)}.")
        }
    }
}
