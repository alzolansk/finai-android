package com.finai.app.domain.assistant

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Categorias
import com.finai.app.domain.BudgetCalculator
import com.finai.app.domain.CashFlowTimeline
import com.finai.app.domain.CycleSource
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.TimelineMovement
import com.finai.app.domain.TransactionType
import com.finai.app.domain.balance.AccountBalance
import com.finai.app.domain.balance.FreeToSpend
import com.finai.app.domain.importer.MerchantClassifier
import com.finai.app.domain.isOpenReserve
import com.finai.app.domain.plans.DebtPrepayment
import com.finai.app.domain.plans.GoalConflictResolver
import com.finai.app.domain.projection.CashFlowProjection
import com.finai.app.domain.projection.PaymentWindow
import com.finai.app.domain.projection.ScenarioEngine
import com.finai.app.domain.projection.ScenarioEvent
import com.finai.app.domain.projection.ScenarioVerdict
import com.finai.app.domain.projection.VariableProvision
import com.finai.app.domain.quickentry.QuickEntryParser
import com.finai.app.domain.scenario.PurchaseSimulatorV2
import com.finai.app.domain.scenario.PurchaseWay
import com.finai.app.domain.spending.SpendingAnalysis
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.transactionsInMonth
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Pedido de consulta que a IA (ou a regra local) faz: nome da ferramenta e argumentos em texto. */
data class ToolCall(val name: String, val args: Map<String, String>)

/**
 * Resultado de uma ferramenta: o rótulo de "consultei: …", os fatos que a resposta pode citar,
 * o cartão montado pelo app ([answer]) e os registros que ficam em foco na conversa.
 */
data class ToolResult(
    val label: String,
    val facts: String,
    val answer: AssistantAnswer? = null,
    val focus: List<EntityRef> = emptyList(),
)

/**
 * As ferramentas de leitura do assistente (§5.3). Todas locais e determinísticas: a IA só escolhe
 * **qual consulta fazer**; valores, datas e vereditos saem daqui, do mesmo motor de saldo e
 * projeção que alimenta Hoje e Agenda. Nenhuma grava nada.
 */
object AssistantTools {
    /** Teto de consultas por pergunta (três rodadas, §5.4). */
    const val MAX_CALLS = 3
    private const val PAGE = 8

    /** O que entra no prompt para a IA saber o que pode pedir. Curto de propósito (cota gratuita). */
    val CATALOG: String = """
        Se precisar de um dado que não está no resumo, responda SOMENTE com uma linha JSON, sem mais nada:
        {"consulta":"situacao"}
        {"consulta":"buscar_lancamentos","texto":"ifood","periodo":"mes_atual","tipo":"gasto"}
        {"consulta":"analisar_gastos","categoria":"Alimentação","periodo":"mes_passado"}
        {"consulta":"comparar_periodos","categoria":"Lazer","a":"mes_passado","b":"mes_atual"}
        {"consulta":"explicar_saldo"}
        {"consulta":"listar_compromissos","dias":"30"}
        {"consulta":"projetar_caixa","meses":"3"}
        {"consulta":"janela_pagamento","valor":"1500"}  ou  {"consulta":"janela_pagamento","divida":"nome"}
        {"consulta":"simular_cenario","tipo":"gasto","valor":"1200","parcelas":"3"}
        {"consulta":"simular_cenario","tipo":"renda_extra","valor":"2000","data":"2026-12-20"}
        {"consulta":"simular_cenario","tipo":"queda_renda","percentual":"20","meses":"3"}
        {"consulta":"simular_cenario","tipo":"gasto_mensal","valor":"150"}
        {"consulta":"simular_cenario","tipo":"quitar_divida","divida":"nome"}
        {"consulta":"montar_plano"}  ou  {"consulta":"montar_plano","valor_extra":"3000"}
        {"consulta":"revisar_meta","meta":"nome"}
        {"consulta":"revisar_divida","divida":"nome"}
        {"consulta":"acompanhar_plano"}
        Períodos aceitos: mes_atual, mes_passado, ano, ultimos_30_dias ou um mês no formato 2026-10.
        Categorias: ${Categorias.all.joinToString(", ")}.
        Use consulta quando a pergunta pede um número, uma data ou uma comparação que você não recebeu. Nunca invente o resultado.
    """.trimIndent()

    val NAMES = setOf(
        "situacao", "buscar_lancamentos", "analisar_gastos", "total_por_categoria", "comparar_periodos", "explicar_saldo",
        "listar_compromissos", "projetar_caixa", "saldo_previsto", "livre_hoje", "janela_pagamento", "simular_cenario",
        "simular_compra", "montar_plano", "revisar_meta", "revisar_divida", "acompanhar_plano", "rascunho_lancamento",
    )

    private val PAIR = Regex(""""([a-z_]+)"\s*:\s*(?:"([^"]*)"|(-?[\d.,]+))""")

    /** Lê a linha JSON de consulta; nulo quando a resposta já é texto para o usuário. */
    fun parseCall(reply: String): ToolCall? {
        val line = reply.lines().map { it.trim().trim('`') }.firstOrNull { it.startsWith("{") && it.contains("\"consulta\"") } ?: return null
        val args = parseArgs(line)
        val name = args["consulta"]?.takeIf { it in NAMES } ?: return null
        return ToolCall(name, args - "consulta")
    }

    internal fun parseArgs(line: String): Map<String, String> =
        PAIR.findAll(line).associate { it.groupValues[1] to (it.groupValues[2].ifEmpty { it.groupValues[3] }) }

    private fun brl(c: Long) = Fmt.money(c)
    private fun dm(d: LocalDate) = Fmt.dm(d)
    private fun monthName(m: YearMonth, today: LocalDate) = MONTH_NAMES_PT[m.monthValue - 1].lowercase() + if (m.year != today.year) " de ${m.year}" else ""
    private fun cents(text: String?, today: LocalDate): Long? = text?.takeIf { it.isNotBlank() }?.let { QuickEntryParser.parse(it, today).cents }

    /** Intervalo de datas de um período em texto; o padrão é o mês atual. */
    fun period(text: String?, today: LocalDate): Triple<LocalDate, LocalDate, String> {
        val t = QuickEntryParser.fold(text.orEmpty()).trim().replace(' ', '_')
        val current = YearMonth.from(today)
        fun month(m: YearMonth) = Triple(m.atDay(1), minOf(m.atEndOfMonth(), today), monthName(m, today))
        Regex("""(\d{4})-(\d{2})""").matchEntire(t)?.let { m ->
            runCatching { YearMonth.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()?.let { return month(it) }
        }
        Regex("""ultimos_(\d{1,3})_dias""").matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toLong().coerceIn(1, 366)
            return Triple(today.minusDays(n - 1), today, "últimos $n dias")
        }
        MONTH_NAMES_PT.indexOfFirst { QuickEntryParser.fold(it) == t }.takeIf { it >= 0 }?.let { i ->
            val m = YearMonth.of(today.year, i + 1).let { if (it > current) it.minusYears(1) else it }
            return month(m)
        }
        return when (t) {
            "mes_passado" -> month(current.minusMonths(1))
            "ano", "ano_atual", "este_ano" -> Triple(LocalDate.of(today.year, 1, 1), today, today.year.toString())
            "semana", "esta_semana", "ultimos_7_dias" -> Triple(today.minusDays(6), today, "últimos 7 dias")
            "hoje" -> Triple(today, today, "hoje")
            else -> month(current)
        }
    }

    /** Lançamentos (com as ocorrências dos recorrentes) entre duas datas. Compra no cartão entra pela data da compra, nunca pelo pagamento da fatura. */
    private fun entries(s: AssistantSnapshot, from: LocalDate, to: LocalDate, kind: String): List<TransacaoEntity> {
        val months = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(to) }
        return months.flatMap { s.transacoes.transactionsInMonth(it.atDay(1)).asSequence() }
            .filter { val d = it.data.toLocalDate(); !d.isBefore(from) && !d.isAfter(to) }
            .filter {
                when (kind) {
                    "receita" -> it.tipo == TransactionType.Receita.name
                    "todos" -> it.tipo == TransactionType.Receita.name || SpendingAnalysis.isConsumption(it)
                    else -> SpendingAnalysis.isConsumption(it)
                }
            }.toList()
    }

    private fun matches(t: TransacaoEntity, q: String) = q.isBlank() || QuickEntryParser.fold(t.descricao + " " + t.categoria).contains(q)

    private fun line(t: TransacaoEntity) = "${dm(t.data.toLocalDate())}: ${Fmt.clean(t.descricao.ifBlank { t.categoria })}, ${brl(t.valorCentavos)}"

    fun run(call: ToolCall, s: AssistantSnapshot): ToolResult {
        val today = s.today
        val a = call.args
        return when (call.name) {
            "situacao" -> status(s)
            "buscar_lancamentos" -> {
                val (from, to, label) = period(a["periodo"], today)
                val kind = QuickEntryParser.fold(a["tipo"].orEmpty()).let { if (it.startsWith("rec")) "receita" else if (it.startsWith("tod")) "todos" else "gasto" }
                val raw = a["texto"].orEmpty()
                val q = Fmt.fold(raw)
                val cat = MerchantClassifier.normalizeCategoryName(a["categoria"].orEmpty())
                val found = entries(s, from, to, kind).filter { matches(it, q) && (cat == null || it.categoria == cat) }.sortedByDescending { it.data }
                val page = (a["pagina"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val shown = found.drop((page - 1) * PAGE).take(PAGE)
                val what = if (kind == "receita") "receita(s)" else if (kind == "todos") "lançamento(s)" else "gasto(s)"
                val about = listOfNotNull(raw.takeIf { it.isNotBlank() }?.let { "\"$it\"" }, cat).joinToString(" · ").ifBlank { what }
                val total = found.sumOf { it.valorCentavos }
                val facts = if (found.isEmpty()) "Nenhum registro de $about em $label."
                else buildString {
                    appendLine("${found.size} $what com $about em $label, somando ${brl(total)}.")
                    shown.forEach { appendLine("- ${line(it)}") }
                    val rest = found.size - (page - 1) * PAGE - shown.size
                    if (rest > 0) appendLine("(mais $rest não listados; peça a página ${page + 1})")
                }
                val answer = AssistantAnswer(
                    AnswerKind.Query, "$about em $label",
                    headline = if (found.isEmpty()) "Nenhum registro em $label." else "${brl(total)} em ${found.size} $what",
                    evidence = shown.map(::line),
                    notes = listOfNotNull("Período: ${dm(from)} a ${dm(to)}. Compra no cartão conta pela data da compra.".takeIf { found.isNotEmpty() }),
                )
                ToolResult("$about em $label", facts, answer, shown.singleOrNull()?.let { listOf(EntityRef(EntityRef.ENTRY, it.id, it.descricao)) }.orEmpty())
            }
            "analisar_gastos", "total_por_categoria" -> spending(s, a)
            "comparar_periodos" -> compare(s, a)
            "explicar_saldo" -> explain(s)
            "listar_compromissos" -> commitments(s, a)
            "projetar_caixa" -> projection(s, a)
            "saldo_previsto" -> {
                val date = runCatching { LocalDate.parse(a["data"].orEmpty()) }.getOrNull()?.takeIf { !it.isBefore(today) }
                    ?.let { minOf(it, today.plusMonths(CashFlowProjection.MAX_MONTHS)) } ?: today
                val p = CashFlowProjection.of(s.contas, s.transacoes, s.dividas, today, date)
                ToolResult(
                    "saldo previsto em ${dm(date)}",
                    "Saldo em conta hoje: ${brl(p.accountCents)}. Saldo previsto no fim de ${dm(date)}, com o que já está lançado: ${brl(p.balanceOn(date))}.",
                    AssistantAnswer(AnswerKind.Query, "Saldo previsto em ${dm(date)}", headline = brl(p.balanceOn(date)),
                        metrics = listOf(AnswerMetric("Em conta hoje", brl(p.accountCents)), AnswerMetric("Menor saldo até lá", "${brl(p.lowest.cents)} em ${dm(p.lowest.date)}", tone(p.lowest.cents, s.marginCents))),
                        notes = listOf("Conta só o que já está lançado; gasto do dia a dia ainda não lançado fica de fora.")),
                )
            }
            "livre_hoje" -> {
                val free = FreeToSpend.of(s.contas, s.transacoes, s.dividas, today, s.marginCents)
                ToolResult("livre de hoje", free?.aiBlock(6) ?: "Sem renda cadastrada: o app não calcula o livre até a próxima entrada.", status(s).answer)
            }
            "janela_pagamento" -> paymentWindow(s, a)
            "simular_cenario" -> scenario(s, a)
            "simular_compra" -> {
                val cents = QuickEntryParser.parse(a["valor"].orEmpty(), today).cents ?: 0L
                val n = Regex("""(\d{1,2})""").find(a["forma"].orEmpty())?.value?.toIntOrNull()
                val ways = (PurchaseSimulatorV2.WAYS + listOfNotNull(n?.takeIf { it in 1..24 }?.let(::PurchaseWay))).distinct().sortedBy { it.installments }
                if (cents <= 0) ToolResult("simulação de compra", "Faltou o valor da compra para simular.")
                else ToolResult("compra de ${formatBrl0(cents / 100.0)}", PurchaseSimulatorV2.simulate(s.contas, s.transacoes, s.dividas, today, cents, s.marginCents,
                    fitsToSaveCents = s.capacity.cabeGuardarCents, ways = ways).factsBlock(""))
            }
            "montar_plano" -> PlanBuilder.build(s, cents(a["valor_extra"], today))
            "revisar_meta" -> goalReview(s, a["meta"].orEmpty())
            "revisar_divida" -> debtReview(s, a["divida"].orEmpty())
            "acompanhar_plano" -> PlanBuilder.follow(s)
            else -> {
                val d = QuickEntryParser.parse(a["frase"].orEmpty(), today, s.quick)
                ToolResult("rascunho de lançamento", "Rascunho: ${d.type.label}, ${d.cents?.let(::brl) ?: "sem valor"}, ${d.description.ifBlank { "sem descrição" }}, ${dm(d.date)}.")
            }
        }
    }

    private fun tone(cents: Long, margin: Long) = when {
        cents < 0 -> Tone.Bad
        cents < margin -> Tone.Warn
        else -> Tone.Good
    }

    // ── situação ────────────────────────────────────────────────────────────

    private fun status(s: AssistantSnapshot): ToolResult {
        val free = FreeToSpend.of(s.contas, s.transacoes, s.dividas, s.today, s.marginCents)
        val quality = dataQuality(s)
        if (free == null) {
            val account = AccountBalance.todayCents(s.contas, s.transacoes, s.dividas, s.today)
            val facts = "Em conta hoje: ${brl(account)}. Sem renda cadastrada: o app não sabe quando o dinheiro entra, então não calcula o livre até a próxima entrada."
            return ToolResult("situação", facts, AssistantAnswer(AnswerKind.Query, "Sua situação hoje", headline = "Em conta hoje: ${brl(account)}",
                notes = listOf("Falta cadastrar sua renda para eu calcular quanto está livre.") + quality, options = listOf(AnswerOption("Como cadastro minha renda?"))))
        }
        val short = free.shortfall
        val headline = if (short != null) "Faltam ${brl(short.cents)} a partir de ${dm(short.date)}" else "Livre até ${dm(free.windowEnd)}: ${brl(free.freeCents)}"
        val answer = AssistantAnswer(
            AnswerKind.Query, "Sua situação hoje", headline = headline,
            metrics = listOfNotNull(
                AnswerMetric("Em conta hoje", brl(free.accountCents)),
                AnswerMetric("Por dia", brl(free.perDayCents)).takeIf { short == null },
                AnswerMetric("Próxima renda", "${brl(free.nextIncome.cents)} em ${dm(free.nextIncome.date)}" + if (free.nextIncome.estimated) " (estimada)" else ""),
                AnswerMetric("Menor saldo previsto", "${brl(free.lowest.cents)} em ${dm(free.lowest.date)}", tone(free.lowest.cents, s.marginCents)),
                AnswerMetric("Margem de segurança", brl(free.marginCents)),
            ),
            evidence = free.movements.take(5).map { "${dm(it.date)}: ${Fmt.clean(it.descricao)}, ${brl(it.cents)}" },
            notes = listOfNotNull(short?.causa?.let { "A falta começa com \"${Fmt.clean(it)}\"." }, "O saldo desce para dentro da margem.".takeIf { free.insideMargin }) + quality,
        )
        return ToolResult("situação de hoje", free.aiBlock(8) + if (quality.isEmpty()) "" else "\nQualidade dos dados: " + quality.joinToString(" "), answer)
    }

    /** O que o retrato não sabe: dito junto da resposta, nunca escondido. */
    internal fun dataQuality(s: AssistantSnapshot): List<String> = buildList {
        add("O saldo é a soma do que foi lançado, não o saldo do banco.")
        val legacy = s.contas.count { it.status == "pago" && it.pagoEm == null }
        if (legacy > 0) add("$legacy conta(s) paga(s) antes de o app guardar o dia do pagamento contam pelo vencimento.")
        if (VariableProvision.of(s.contas, s.transacoes, s.dividas, s.today) == null) add("Histórico curto: ainda não dá para estimar o gasto do dia a dia dos próximos meses.")
    }

    // ── gastos ──────────────────────────────────────────────────────────────

    private fun spending(s: AssistantSnapshot, a: Map<String, String>): ToolResult {
        val today = s.today
        val (from, to, label) = period(a["periodo"], today)
        val cat = MerchantClassifier.normalizeCategoryName(a["categoria"].orEmpty())
        val q = Fmt.fold(a["texto"].orEmpty())
        val all = entries(s, from, to, "gasto").filter { matches(it, q) }
        val list = all.filter { cat == null || it.categoria == cat }
        val total = list.sumOf { it.valorCentavos }
        val refunds = list.filter { it.valorCentavos < 0 }
        val what = listOfNotNull(cat, a["texto"]?.takeIf { it.isNotBlank() }?.let { "\"$it\"" }).joinToString(" · ").ifBlank { "Gastos" }
        val byCategory = list.groupBy { it.categoria }.map { it.key to it.value.sumOf { t -> t.valorCentavos } }.sortedByDescending { it.second }
        val byMerchant = list.groupBy { MerchantClassifier.normalizeMerchant(it.descricao).ifBlank { it.categoria.lowercase() } }
            .map { Triple(it.key, it.value.sumOf { t -> t.valorCentavos }, it.value.size) }.sortedByDescending { it.second }
        val facts = buildString {
            if (cat != null && q.isBlank()) appendLine("$cat em $label: ${brl(total)} em ${list.size} lançamento(s).")
            else appendLine("$what em $label (${dm(from)} a ${dm(to)}): ${brl(total)} em ${list.size} lançamento(s).")
            if (cat == null && byCategory.size > 1) appendLine("Por categoria: " + byCategory.joinToString("; ") { "${it.first} ${brl(it.second)}" } + ".")
            if (byMerchant.size > 1) appendLine("Onde mais foi: " + byMerchant.take(5).joinToString("; ") { "${Fmt.clean(it.first)} ${brl(it.second)} (${it.third}×)" } + ".")
            if (refunds.isNotEmpty()) appendLine("Inclui ${refunds.size} estorno(s), somando ${brl(refunds.sumOf { it.valorCentavos })}.")
            if (list.isEmpty()) appendLine("Nenhum gasto registrado nesse recorte.")
            val limit = cat?.let { c -> s.orcamentos.firstOrNull { it.categoria == c && it.mesReferencia == YearMonth.from(to).toString() } }
            if (limit != null) appendLine("Limite de $cat no mês: ${brl(limit.limiteMensalCentavos)}.")
        }.trim()
        val answer = AssistantAnswer(
            AnswerKind.Query, "$what em $label",
            headline = if (list.isEmpty()) "Nenhum gasto nesse recorte." else "${brl(total)} em ${list.size} lançamento(s)",
            metrics = (if (cat == null) byCategory.take(5).map { AnswerMetric(it.first, brl(it.second)) } else emptyList()),
            evidence = (if (cat != null || q.isNotBlank()) byMerchant.take(5).map { "${Fmt.clean(it.first).replaceFirstChar { c -> c.uppercase() }}: ${brl(it.second)} (${it.third}×)" } else emptyList()) +
                list.sortedByDescending { it.valorCentavos }.take(3).map { "Maior: ${line(it)}" },
            notes = listOfNotNull(
                "Período: ${dm(from)} a ${dm(to)}. Compra no cartão conta pela data da compra; o pagamento da fatura não soma de novo.".takeIf { list.isNotEmpty() },
                "Inclui ${refunds.size} estorno(s).".takeIf { refunds.isNotEmpty() },
            ),
            options = listOfNotNull(AnswerOption("Comparar com o mês passado", "Compare ${cat ?: "meus gastos"} deste mês com o mês passado").takeIf { list.isNotEmpty() }),
        )
        return ToolResult("${cat ?: what.lowercase()} em $label", facts, answer)
    }

    private fun compare(s: AssistantSnapshot, a: Map<String, String>): ToolResult {
        val today = s.today
        var pa = period(a["a"] ?: "mes_passado", today)
        val pb = period(a["b"] ?: "mes_atual", today)
        val cat = MerchantClassifier.normalizeCategoryName(a["categoria"].orEmpty())
        // Mês parcial só se compara com o mesmo número de dias do outro período.
        val partial = pb.second == today && YearMonth.from(pb.first) == YearMonth.from(today) && pb.first.dayOfMonth == 1 && today != YearMonth.from(today).atEndOfMonth()
        val fullA = pa
        if (partial && pa.first.dayOfMonth == 1) {
            val cut = pa.first.withDayOfMonth(today.dayOfMonth.coerceAtMost(pa.first.lengthOfMonth()))
            if (cut.isBefore(pa.second)) pa = Triple(pa.first, cut, pa.third)
        }
        fun total(p: Triple<LocalDate, LocalDate, String>) = entries(s, p.first, p.second, "gasto").filter { cat == null || it.categoria == cat }.sumOf { it.valorCentavos }
        val ta = total(pa); val tb = total(pb); val diff = tb - ta
        val what = cat ?: "Gastos"
        val sameDays = pa != fullA
        val scope = if (sameDays) " (até o dia ${pa.second.dayOfMonth}, para comparar o mesmo número de dias)" else ""
        val facts = buildString {
            appendLine("$what em ${pa.third}$scope: ${brl(ta)}. $what em ${pb.third}: ${brl(tb)}. Diferença: ${brl(kotlin.math.abs(diff))} ${if (diff >= 0) "a mais" else "a menos"} em ${pb.third}.")
            if (sameDays) appendLine("$what em ${fullA.third} no mês inteiro: ${brl(total(fullA))}.")
            if (cat == null) {
                val ca = entries(s, pa.first, pa.second, "gasto").groupBy { it.categoria }.mapValues { e -> e.value.sumOf { it.valorCentavos } }
                val cb = entries(s, pb.first, pb.second, "gasto").groupBy { it.categoria }.mapValues { e -> e.value.sumOf { it.valorCentavos } }
                val moves = (ca.keys + cb.keys).map { it to ((cb[it] ?: 0) - (ca[it] ?: 0)) }.filter { it.second != 0L }.sortedByDescending { kotlin.math.abs(it.second) }.take(4)
                if (moves.isNotEmpty()) appendLine("O que mais mudou: " + moves.joinToString("; ") { "${it.first} ${if (it.second > 0) "+" else "-"}${brl(kotlin.math.abs(it.second))}" } + ".")
            }
        }.trim()
        val answer = AssistantAnswer(
            AnswerKind.Explanation, "$what: ${pa.third} × ${pb.third}",
            headline = if (diff == 0L) "Igual nos dois períodos." else "${brl(kotlin.math.abs(diff))} ${if (diff > 0) "a mais" else "a menos"} em ${pb.third}",
            metrics = listOf(AnswerMetric(pa.third.replaceFirstChar { it.uppercase() }, brl(ta)), AnswerMetric(pb.third.replaceFirstChar { it.uppercase() }, brl(tb), if (diff > 0) Tone.Warn else Tone.Good)),
            evidence = facts.lines().drop(1),
            notes = listOfNotNull("Comparei os mesmos ${pa.second.dayOfMonth} dias de cada mês, porque ${pb.third} ainda não acabou.".takeIf { sameDays }),
        )
        return ToolResult("$what: ${pa.third} × ${pb.third}", facts, answer)
    }

    // ── saldo e compromissos ────────────────────────────────────────────────

    private fun settledRecent(s: AssistantSnapshot, days: Long): List<TimelineMovement> {
        val from = s.today.minusDays(days)
        val start = YearMonth.from(from)
        val months = ChronoUnit.MONTHS.between(start, YearMonth.from(s.today)).toInt() + 1
        return CashFlowTimeline.monthMovements(s.contas, s.transacoes, s.dividas, s.today, months, from = start, paidOnPaymentDay = true)
            .filter { AccountBalance.settled(it, s.today) && !it.date.isBefore(from) && !it.date.isAfter(s.today) }
    }

    private fun explain(s: AssistantSnapshot): ToolResult {
        val today = s.today
        val account = AccountBalance.todayCents(s.contas, s.transacoes, s.dividas, today)
        val recent = settledRecent(s, 30)
        val inCents = recent.filter { it.cents > 0 }.sumOf { it.cents }
        val outCents = recent.filter { it.cents < 0 }.sumOf { -it.cents }
        val biggest = recent.filter { it.cents < 0 }.sortedBy { it.cents }.take(5)
        val free = FreeToSpend.of(s.contas, s.transacoes, s.dividas, today, s.marginCents)
        val current = YearMonth.from(today)
        val now = MonthCashFlow.of(s.contas, s.transacoes, s.dividas, current, today)
        val before = MonthCashFlow.of(s.contas, s.transacoes, s.dividas, current.minusMonths(1), today)
        val facts = buildString {
            appendLine("Em conta hoje (${dm(today)}): ${brl(account)}. Nos últimos 30 dias entraram ${brl(inCents)} e saíram ${brl(outCents)}.")
            if (biggest.isNotEmpty()) appendLine("Maiores saídas dos últimos 30 dias: " + biggest.joinToString("; ") { "${dm(it.date)} ${Fmt.clean(it.descricao)} ${brl(-it.cents)}" } + ".")
            appendLine("${monthName(current, today).replaceFirstChar { it.uppercase() }}: entra ${brl(now.toGetCents)} e sai ${brl(now.toPayCents)}. ${monthName(current.minusMonths(1), today).replaceFirstChar { it.uppercase() }}: entrou ${brl(before.toGetCents)} e saiu ${brl(before.toPayCents)}.")
            if (free != null) {
                appendLine("Ainda vai sair até ${dm(free.windowEnd)}: ${brl(free.outflowsCents)}; ainda vai entrar: ${brl(free.inflowsCents)}. Menor saldo previsto: ${brl(free.lowest.cents)} em ${dm(free.lowest.date)}.")
                appendLine(if (free.shortfall != null) "Falta prevista de ${brl(free.shortfall.cents)} a partir de ${dm(free.shortfall.date)}." else "Livre até ${dm(free.windowEnd)}: ${brl(free.freeCents)} (menor saldo menos a margem de ${brl(free.marginCents)}).")
            }
            append("O app não guarda o livre de dias anteriores: a mudança é explicada pelo que entrou e saiu, não por uma foto de ontem.")
        }
        val answer = AssistantAnswer(
            AnswerKind.Explanation, "De onde vem o seu saldo", headline = "Em conta hoje: ${brl(account)}",
            metrics = listOfNotNull(
                AnswerMetric("Entrou em 30 dias", brl(inCents), Tone.Good), AnswerMetric("Saiu em 30 dias", brl(outCents)),
                free?.let { AnswerMetric("Ainda vai sair até ${dm(it.windowEnd)}", brl(it.outflowsCents)) },
                free?.let { AnswerMetric("Livre", brl(it.freeCents), tone(it.lowest.cents, s.marginCents)) },
            ),
            evidence = biggest.map { "${dm(it.date)}: ${Fmt.clean(it.descricao)}, ${brl(-it.cents)}" },
            notes = listOf("O app não guarda o livre de dias anteriores; explico pelo que entrou e saiu.") + dataQuality(s).take(1),
        )
        return ToolResult("composição do saldo", facts, answer)
    }

    private fun kindOf(m: TimelineMovement): String = when (val src = m.source) {
        is CycleSource.Parcela -> "parcela" + (src.parcela.numero?.let { n -> " $n" + (src.parcela.total?.let { " de $it" } ?: "") } ?: "")
        is CycleSource.Conta -> if (src.conta.tipo == "a_receber") "a receber" else "conta"
        is CycleSource.Lancamento -> if (src.transacao.recorrente) "todo mês" else "lançado"
        null -> "hipótese"
    }

    private fun commitments(s: AssistantSnapshot, a: Map<String, String>): ToolResult {
        val today = s.today
        val days = (a["dias"]?.toLongOrNull() ?: 30).coerceIn(1, 120)
        val end = today.plusDays(days)
        val p = CashFlowProjection.of(s.contas, s.transacoes, s.dividas, today, end)
        val out = p.movements.filter { it.cents < 0 }
        val overdue = out.filter { it.movedFrom != null }
        val total = out.sumOf { -it.cents }
        fun row(m: TimelineMovement) = "${dm(m.movedFrom ?: m.date)}: ${Fmt.clean(m.descricao)}, ${brl(-m.cents)} (${kindOf(m)}${if (m.movedFrom != null) ", atrasada" else ""})"
        val facts = if (out.isEmpty()) "Nada a pagar lançado até ${dm(end)}."
        else buildString {
            appendLine("${out.size} compromisso(s) até ${dm(end)}, somando ${brl(total)}." + if (overdue.isNotEmpty()) " ${overdue.size} atrasado(s), somando ${brl(overdue.sumOf { -it.cents })}." else "")
            out.take(12).forEach { appendLine("- ${row(it)}") }
            if (out.size > 12) appendLine("(mais ${out.size - 12} não listados)")
            append("Itens de fatura estão dentro da fatura (não somam duas vezes). Menor saldo no período: ${brl(p.lowest.cents)} em ${dm(p.lowest.date)}.")
        }
        val answer = AssistantAnswer(
            AnswerKind.Query, "A pagar até ${dm(end)}", headline = if (out.isEmpty()) "Nada lançado até lá." else "${brl(total)} em ${out.size} compromisso(s)",
            metrics = listOfNotNull(
                AnswerMetric("Atrasados", brl(overdue.sumOf { -it.cents }), Tone.Bad).takeIf { overdue.isNotEmpty() },
                AnswerMetric("Menor saldo no período", "${brl(p.lowest.cents)} em ${dm(p.lowest.date)}", tone(p.lowest.cents, s.marginCents)),
            ),
            evidence = out.take(8).map(::row) + listOfNotNull("mais ${out.size - 8} na Agenda".takeIf { out.size > 8 }),
            notes = listOf("As compras do cartão estão dentro de cada fatura."),
        )
        return ToolResult("compromissos até ${dm(end)}", facts, answer)
    }

    private fun projection(s: AssistantSnapshot, a: Map<String, String>): ToolResult {
        val today = s.today
        val months = (a["meses"]?.toLongOrNull() ?: 3).coerceIn(1, 12)
        val end = YearMonth.from(today).plusMonths(months).atEndOfMonth()
        val base = CashFlowProjection.of(s.contas, s.transacoes, s.dividas, today, end)
        val provision = VariableProvision.of(s.contas, s.transacoes, s.dividas, today)
        val estimated = provision?.let { CashFlowProjection.of(s.contas, s.transacoes, s.dividas, today, end, provision = it) }
        val lowsEst = estimated?.monthLows()?.toMap().orEmpty()
        val rows = base.monthLows().map { (m, low) ->
            val endBalance = base.balanceOn(minOf(m.atEndOfMonth(), end))
            Triple(m, low, endBalance)
        }
        val facts = buildString {
            appendLine("Projeção do caixa de ${dm(today)} a ${Fmt.date(end, today)}, com o que já está lançado (renda, contas, faturas, parcelas e recorrentes). Em conta hoje: ${brl(base.accountCents)}.")
            rows.forEach { (m, low, endBalance) ->
                append("- ${monthName(m, today)}: menor saldo ${brl(low.cents)} em ${dm(low.date)}; termina em ${brl(endBalance)}")
                lowsEst[m]?.let { append("; com o gasto do dia a dia estimado, menor saldo ${brl(it.cents)}") }
                appendLine(".")
            }
            base.firstNegative?.let { appendLine("O saldo fica negativo a partir de ${Fmt.date(it.date, today)} (${brl(it.cents)}).") }
            if (provision != null) appendLine("Estimativa do dia a dia: ${brl(provision.monthlyAverageCents)} por mês (média de ${provision.sampleMonths} meses); já desconta o que está lançado.")
            else appendLine("Sem histórico suficiente para estimar o gasto do dia a dia: os meses futuros só têm o que já está lançado, então o saldo real tende a ser menor.")
            append("Aporte planejado de meta não sai do caixa até ser registrado.")
        }
        val worst = estimated ?: base
        val answer = AssistantAnswer(
            AnswerKind.Decision, "Seu caixa nos próximos $months mês(es)",
            headline = base.firstNegative?.let { "Fica negativo a partir de ${Fmt.date(it.date, today)}" } ?: "Menor saldo: ${brl(base.lowest.cents)} em ${Fmt.date(base.lowest.date, today)}",
            metrics = rows.map { (m, low, _) -> AnswerMetric(monthName(m, today).replaceFirstChar { it.uppercase() }, "mín. ${brl(low.cents)}" + (lowsEst[m]?.let { " · est. ${brl(it.cents)}" } ?: ""), tone(lowsEst[m]?.cents ?: low.cents, s.marginCents)) },
            evidence = rows.map { (m, _, e) -> "${monthName(m, today).replaceFirstChar { it.uppercase() }} termina em ${brl(e)}" },
            notes = listOfNotNull(
                if (provision != null) "\"est.\" inclui ${brl(provision.monthlyAverageCents)}/mês de gasto do dia a dia (média de ${provision.sampleMonths} meses)." else "Sem histórico para estimar o dia a dia: o saldo real tende a ser menor.",
                "Com a estimativa, o menor saldo é ${brl(worst.lowest.cents)} em ${Fmt.date(worst.lowest.date, today)}.".takeIf { estimated != null },
                "Aporte de meta só sai do caixa quando é registrado.",
            ),
        )
        return ToolResult("projeção de $months mês(es)", facts, answer)
    }

    // ── decisões ────────────────────────────────────────────────────────────

    internal fun findDebt(s: AssistantSnapshot, query: String): List<DividaEntity> =
        if (query.isBlank()) s.openDebts else NameMatch.find(query, s.openDebts) { it.nome }

    internal fun findGoal(s: AssistantSnapshot, query: String): List<ObjetivoEntity> =
        if (query.isBlank()) s.openGoals else NameMatch.find(query, s.openGoals) { it.nome }

    private fun ambiguous(label: String, kind: String, names: List<String>, template: (String) -> String): ToolResult {
        val facts = if (names.isEmpty()) "Não encontrei $kind com esse nome." else "Há mais de uma $kind possível: ${names.joinToString(", ")}. Pergunte qual."
        return ToolResult(label, facts, AssistantAnswer(AnswerKind.Question, if (names.isEmpty()) "Não encontrei" else "Qual delas?", headline = if (names.isEmpty()) "Não encontrei $kind com esse nome." else "Encontrei mais de uma.",
            options = names.take(4).map { AnswerOption(it, template(it)) }))
    }

    private fun horizon(s: AssistantSnapshot, months: Long = 12): LocalDate = YearMonth.from(s.today).plusMonths(months).atEndOfMonth()

    private fun paymentWindow(s: AssistantSnapshot, a: Map<String, String>): ToolResult {
        val today = s.today
        val debtQuery = a["divida"].orEmpty()
        val debts = if (debtQuery.isNotBlank() || a["valor"].isNullOrBlank()) findDebt(s, debtQuery) else emptyList()
        if (debtQuery.isNotBlank() && debts.size != 1) return ambiguous("janela de pagamento", "dívida", debts.map { it.nome }) { "Quando posso quitar $it?" }
        val debt = debts.singleOrNull()
        val amount = cents(a["valor"], today) ?: debt?.valorAbertoCentavos
            ?: return ToolResult("janela de pagamento", "Faltou o valor a pagar.", AssistantAnswer(AnswerKind.Question, "Qual é o valor?", headline = "Diga o valor que você quer pagar para eu achar a primeira data."))
        val end = debt?.let { DebtSchedule.lastInstallmentMonth(it, today)?.atEndOfMonth() }?.let { maxOf(it, horizon(s, 6)) } ?: horizon(s)
        val provision = VariableProvision.of(s.contas, s.transacoes, s.dividas, today)
        val dividas = if (debt != null) s.dividas.filter { it.id != debt.id } else s.dividas
        val p = CashFlowProjection.of(s.contas, s.transacoes, dividas, today, end, provision = provision)
        val w = PaymentWindow.of(p, amount, s.marginCents)
        val what = debt?.let { "quitar ${Fmt.clean(it.nome)}" } ?: "pagar ${brl(amount)}"
        val facts = buildString {
            appendLine("Pergunta: quando dá para $what (${brl(amount)}) sem o saldo ficar negativo depois. Verificado até ${Fmt.date(p.end, today)}.")
            when {
                w.firstWithMargin != null -> appendLine("Primeira data com a margem de ${brl(s.marginCents)} preservada: ${Fmt.date(w.firstWithMargin, today)}." +
                    (w.firstViable?.takeIf { it != w.firstWithMargin }?.let { " Sem a margem, já caberia em ${Fmt.date(it, today)}." } ?: ""))
                w.firstViable != null -> appendLine("Cabe a partir de ${Fmt.date(w.firstViable, today)}, mas usando a margem de segurança de ${brl(s.marginCents)}. Com a margem inteira, não cabe até ${Fmt.date(p.end, today)}.")
                else -> appendLine("Não cabe em nenhuma data até ${Fmt.date(p.end, today)}: em todos os dias, pagar ${brl(amount)} deixaria algum dia seguinte negativo.")
            }
            w.lowestAfter?.let { appendLine("Pagando nessa data, o menor saldo depois fica em ${brl(it.cents)} (${Fmt.date(it.date, today)}).") }
            if (debt != null) {
                appendLine("Valor usado: o saldo em aberto cadastrado (${brl(debt.valorAbertoCentavos)}). A cotação de quitação do credor pode ser menor; peça a ele e me diga o valor.")
                appendLine("Na conta, as parcelas dessa dívida deixam de existir depois da quitação.")
            }
            if (provision != null) appendLine("Inclui ${brl(provision.monthlyAverageCents)}/mês de gasto do dia a dia estimado.") else appendLine("Não inclui gasto do dia a dia ainda não lançado (sem histórico para estimar).")
            if (p.truncated) appendLine("A projeção foi cortada em ${Fmt.date(p.end, today)}; o que vem depois não foi verificado.")
            append("Metas: aporte planejado não foi descontado; a reserva e o valor guardado em metas não contam como caixa.")
        }
        val pick = w.firstWithMargin ?: w.firstViable
        val answer = AssistantAnswer(
            AnswerKind.Decision, "Quando dá para $what",
            headline = when {
                w.firstWithMargin != null -> if (w.firstWithMargin == today) "Já cabe hoje, com a margem preservada" else "A partir de ${Fmt.date(w.firstWithMargin, today)}"
                w.firstViable != null -> "A partir de ${Fmt.date(w.firstViable, today)}, usando a margem"
                else -> "Não cabe até ${Fmt.date(p.end, today)}"
            },
            metrics = listOfNotNull(
                AnswerMetric("Valor", brl(amount)),
                w.firstViable?.takeIf { it != w.firstWithMargin }?.let { AnswerMetric("Sem a margem", Fmt.date(it, today), Tone.Warn) },
                w.lowestAfter?.let { AnswerMetric("Menor saldo depois", "${brl(it.cents)} em ${Fmt.date(it.date, today)}", tone(it.cents, s.marginCents)) },
            ),
            notes = listOfNotNull(
                debt?.let { "Usei o saldo em aberto cadastrado. Se o credor der uma cotação de quitação, me diga o valor." },
                if (provision != null) "Inclui ${brl(provision.monthlyAverageCents)}/mês de gasto do dia a dia estimado." else "Não inclui gasto do dia a dia ainda não lançado.",
                "Reserva e valor guardado em metas não entram como caixa.",
                "Verificado até ${Fmt.date(p.end, today)}." + if (p.truncated) " O que vem depois não foi conferido." else "",
            ),
            options = listOfNotNull(
                debt?.takeIf { pick == null || pick != today }?.let { AnswerOption("Me avise quando couber", "Me avise quando eu puder quitar ${it.nome}") },
                AnswerOption("E parcelando?", "E se eu parcelar ${brl(amount)} em 6x?").takeIf { debt == null },
            ),
        )
        return ToolResult("janela para $what", facts, answer, debt?.let { listOf(EntityRef(EntityRef.DEBT, it.id, it.nome)) }.orEmpty())
    }

    private fun scenario(s: AssistantSnapshot, a: Map<String, String>): ToolResult {
        val today = s.today
        val type = QuickEntryParser.fold(a["tipo"].orEmpty()).replace(' ', '_')
        val value = cents(a["valor"], today)
        val date = runCatching { LocalDate.parse(a["data"].orEmpty()) }.getOrNull()?.takeIf { !it.isBefore(today) } ?: today
        val months = (a["meses"]?.toIntOrNull() ?: 6).coerceIn(1, 24)
        val n = (a["parcelas"] ?: a["forma"])?.let { Regex("""(\d{1,2})""").find(it)?.value?.toIntOrNull() }?.takeIf { it in 2..48 }
        var focus = emptyList<EntityRef>()
        var extraNote: String? = null
        var horizonMonths = 6L
        fun missing(what: String) = ToolResult("cenário", "Faltou $what para simular.", AssistantAnswer(AnswerKind.Question, "Falta um dado", headline = "Para simular, preciso de $what."))
        val (label, events) = when (type) {
            "gasto", "compra", "gasto_unico" -> {
                val v = value ?: return missing("o valor")
                if (n != null) {
                    val each = v / n
                    horizonMonths = maxOf(6L, n.toLong() + 1)
                    "gasto de ${brl(v)} em ${n}x de ${brl(each)}" to listOf(ScenarioEvent.Installments(date.plusMonths(if (a["data"].isNullOrBlank()) 1 else 0), n, each, "Compra simulada"))
                } else "gasto de ${brl(v)} em ${Fmt.day(date, today)}" to listOf(ScenarioEvent.OneOff(date, -v, "Gasto simulado"))
            }
            "renda_extra", "receita", "entrada" -> {
                val v = value ?: return missing("o valor")
                "entrada de ${brl(v)} em ${Fmt.day(date, today)}" to listOf(ScenarioEvent.OneOff(date, v, "Entrada simulada"))
            }
            "queda_renda", "renda_menor", "aumento_renda" -> {
                val pct = a["percentual"]?.let { Regex("""(\d{1,3})""").find(it)?.value?.toIntOrNull() }
                if (pct == null && value == null) return missing("quanto a renda muda (percentual ou valor)")
                val sign = if (type == "aumento_renda") 1 else -1
                horizonMonths = maxOf(6L, months.toLong())
                val text = if (pct != null) "$pct%" else "${brl(value!!)} por recebimento"
                "renda ${if (sign < 0) "menor" else "maior"} em $text por $months mês(es)" to listOf(ScenarioEvent.IncomeChange(pct?.let { it * sign }, if (pct == null) value!! * sign else null, months))
            }
            "gasto_mensal", "economia_mensal" -> {
                val v = value ?: return missing("o valor por mês")
                val sign = if (type == "economia_mensal") 1 else -1
                horizonMonths = maxOf(6L, months.toLong())
                "${if (sign < 0) "gasto novo" else "economia"} de ${brl(v)} por mês, por $months mês(es)" to listOf(ScenarioEvent.Monthly(date.plusMonths(if (a["data"].isNullOrBlank()) 1 else 0), months, v * sign, if (sign < 0) "Gasto mensal simulado" else "Economia simulada"))
            }
            "quitar_divida", "quitacao" -> {
                val debts = findDebt(s, a["divida"].orEmpty())
                if (debts.size != 1) return ambiguous("cenário de quitação", "dívida", debts.map { it.nome }) { "E se eu quitar $it?" }
                val d = debts.single()
                focus = listOf(EntityRef(EntityRef.DEBT, d.id, d.nome))
                val v = value ?: d.valorAbertoCentavos
                horizonMonths = maxOf(6L, d.parcelasRestantes.toLong() + 1).coerceAtMost(CashFlowProjection.MAX_MONTHS)
                extraNote = if (value == null) "Usei o saldo em aberto cadastrado (${brl(v)}); a cotação de quitação do credor pode ser menor."
                else "Seguindo o contrato você ainda pagaria ${brl(d.valorParcelaCentavos * d.parcelasRestantes)} em ${d.parcelasRestantes} parcela(s); quitando por ${brl(v)}, a diferença é ${brl(d.valorParcelaCentavos * d.parcelasRestantes - v)}."
                "quitar ${Fmt.clean(d.nome)} por ${brl(v)} em ${Fmt.day(date, today)}" to listOf(ScenarioEvent.PayOffDebt(d.id, v, date))
            }
            else -> return ToolResult("cenário", "Tipo de cenário desconhecido. Tipos: gasto, renda_extra, queda_renda, gasto_mensal, quitar_divida.")
        }
        val end = horizon(s, horizonMonths)
        val r = ScenarioEngine.run(s.contas, s.transacoes, s.dividas, today, end, s.marginCents, events)
        val asks = GoalConflictResolver.asks(s.objetivos, today).sumOf { it.monthlyCents }
        val fits = s.capacity.cabeGuardarCents
        val verdict = r.verdict
        val changed = r.months.filter { it.beforeCents != it.afterCents }
        val facts = buildString {
            appendLine("Cenário: $label. Comparado com o plano atual, no mesmo ponto de partida (em conta hoje: ${brl(r.base.accountCents)}), até ${Fmt.date(end, today)}.")
            appendLine("Veredito calculado pelo app: ${verdict.label}.")
            appendLine("Menor saldo sem o cenário: ${brl(r.lowestBefore.cents)} em ${Fmt.date(r.lowestBefore.date, today)}. Com o cenário: ${brl(r.lowestAfter.cents)} em ${Fmt.date(r.lowestAfter.date, today)}. Margem de segurança: ${brl(s.marginCents)}.")
            changed.take(8).forEach { appendLine("- ${monthName(it.month, today)}: menor saldo de ${brl(it.beforeCents)} para ${brl(it.afterCents)}") }
            r.after.firstNegative?.let { appendLine("Com o cenário, o saldo fica negativo a partir de ${Fmt.date(it.date, today)}.") }
            if (verdict == ScenarioVerdict.AlreadyShort) appendLine("O plano atual já ficava negativo antes desta decisão: o problema não nasce dela.")
            if (asks > 0) appendLine("Metas pedem ${brl(asks)} por mês e hoje cabe guardar ${brl(fits)} por mês. Aporte planejado não foi descontado do caixa.")
            extraNote?.let { appendLine(it) }
            append("Só o que já está lançado entra na conta; gasto do dia a dia futuro não está incluído.")
        }
        val answer = AssistantAnswer(
            AnswerKind.Decision, label.replaceFirstChar { it.uppercase() }, headline = verdict.label,
            metrics = listOf(
                AnswerMetric("Menor saldo hoje", "${brl(r.lowestBefore.cents)} em ${Fmt.date(r.lowestBefore.date, today)}", tone(r.lowestBefore.cents, s.marginCents)),
                AnswerMetric("Com o cenário", "${brl(r.lowestAfter.cents)} em ${Fmt.date(r.lowestAfter.date, today)}", tone(r.lowestAfter.cents, s.marginCents)),
            ) + listOfNotNull(AnswerMetric("Metas pedem / cabe guardar", "${brl(asks)} / ${brl(fits)} por mês").takeIf { asks > 0 }),
            evidence = changed.take(6).map { "${monthName(it.month, today).replaceFirstChar { c -> c.uppercase() }}: de ${brl(it.beforeCents)} para ${brl(it.afterCents)}" },
            notes = listOfNotNull(
                "O plano atual já ficava negativo antes desta decisão.".takeIf { verdict == ScenarioVerdict.AlreadyShort },
                extraNote, "Nada foi lançado: é só uma simulação.", "Verificado até ${Fmt.date(end, today)}; gasto do dia a dia futuro não entra.",
            ),
        )
        return ToolResult("cenário: $label", facts, answer, focus)
    }

    private fun goalReview(s: AssistantSnapshot, query: String): ToolResult {
        val today = s.today
        val goals = findGoal(s, query)
        if (goals.size != 1) return ambiguous("meta", "meta", goals.map { it.nome }) { "Como está a meta $it?" }
        val g = goals.single()
        val asks = GoalConflictResolver.asks(s.objetivos, today)
        val ask = asks.firstOrNull { it.id == g.id }
        val fits = s.capacity.cabeGuardarCents
        val total = asks.sumOf { it.monthlyCents }
        val conflict = GoalConflictResolver.resolve(s.objetivos, fits, today)
        val deadline = YearMonth.from(g.prazo.toLocalDate())
        val contributed = s.transacoes.filter { it.objetivoId == g.id && YearMonth.from(it.data.toLocalDate()) == YearMonth.from(today) }.sumOf { it.valorCentavos }
        val facts = buildString {
            if (g.isOpenReserve) appendLine("${g.nome} é uma reserva contínua: ${brl(g.valorGuardadoCentavos)} guardados" + (if (g.aporteMensalCentavos > 0) ", aporte planejado de ${brl(g.aporteMensalCentavos)} por mês." else ", sem aporte mensal planejado."))
            else appendLine("${g.nome}: ${brl(g.valorGuardadoCentavos)} de ${brl(g.valorAlvoCentavos)} guardados; faltam ${brl((g.valorAlvoCentavos - g.valorGuardadoCentavos).coerceAtLeast(0))}. Prazo: ${monthName(deadline, today)}." +
                (ask?.let { " Para chegar lá: ${brl(it.monthlyCents)} por mês." } ?: ""))
            appendLine("Neste mês você já guardou ${brl(contributed)} nela.")
            appendLine("Todas as metas juntas pedem ${brl(total)} por mês; cabe guardar ${brl(fits)} por mês (média dos próximos 6 meses" + (if (s.capacity.usesEstimatedSalary) ", com salário estimado" else "") + ").")
            if (conflict != null) {
                if (conflict.goalId == g.id) appendLine("Esta é a meta que cede (menor prioridade): " + (conflict.newDeadline?.let { "com prazo em ${monthName(it, today)} o aporte cai para ${brl(conflict.newMonthlyCents)} por mês e todas cabem." } ?: "nem adiando o prazo todas cabem."))
                else appendLine("As metas não cabem todas; quem cede é \"${conflict.goalName}\", de menor prioridade.")
            } else appendLine("Todas as metas cabem na sobra prevista.")
            append("O valor guardado na meta não conta como dinheiro livre.")
        }
        val answer = AssistantAnswer(
            AnswerKind.Decision, g.nome, headline = if (g.isOpenReserve) "${brl(g.valorGuardadoCentavos)} guardados" else "${brl(g.valorGuardadoCentavos)} de ${brl(g.valorAlvoCentavos)}",
            metrics = listOfNotNull(
                ask?.takeIf { !g.isOpenReserve }?.let { AnswerMetric("Precisa por mês", brl(it.monthlyCents)) },
                AnswerMetric("Prazo", monthName(deadline, today)).takeIf { !g.isOpenReserve },
                AnswerMetric("Guardado neste mês", brl(contributed)),
                AnswerMetric("Metas pedem / cabe", "${brl(total)} / ${brl(fits)} por mês", if (total > fits) Tone.Warn else Tone.Good),
            ),
            notes = listOfNotNull(conflict?.takeIf { it.goalId == g.id }?.let { c -> c.newDeadline?.let { "Com prazo em ${monthName(it, today)}, o aporte cai para ${brl(c.newMonthlyCents)}/mês e todas as metas cabem." } },
                "O valor guardado não conta como dinheiro livre."),
            options = listOfNotNull(conflict?.takeIf { it.goalId == g.id }?.newDeadline?.let { AnswerOption("Mudar o prazo para ${monthName(it, today)}", "Mude o prazo da meta ${g.nome} para ${monthName(it, today)} de ${it.year}") }),
        )
        return ToolResult("meta ${Fmt.clean(g.nome)}", facts, answer, listOf(EntityRef(EntityRef.GOAL, g.id, g.nome)))
    }

    private fun debtReview(s: AssistantSnapshot, query: String): ToolResult {
        val today = s.today
        val debts = findDebt(s, query)
        if (debts.size != 1) return ambiguous("dívida", "dívida", debts.map { it.nome }) { "Como está a dívida $it?" }
        val d = debts.single()
        val last = DebtSchedule.lastInstallmentMonth(d, today)
        val next = DebtSchedule.nextDue(d)
        val contract = d.parcelasTotais > 0
        val remainingFace = d.valorParcelaCentavos * d.parcelasRestantes
        val free = FreeToSpend.of(s.contas, s.transacoes, s.dividas, today, s.marginCents)?.freeCents ?: 0
        val prepay = DebtPrepayment.opportunity(d, free)
        val facts = buildString {
            appendLine("${d.nome}: ${brl(d.valorAbertoCentavos)} em aberto" + (if (d.parcelasRestantes > 0) ", ${d.parcelasRestantes} parcela(s) de ${brl(d.valorParcelaCentavos)}" else "") +
                (if (d.taxaJurosMensalBasisPoints > 0) ", juros de ${"%.2f".format(d.taxaJurosMensalBasisPoints / 100.0).replace('.', ',')}% ao mês." else ", sem taxa de juros informada."))
            next?.let { appendLine("Próxima parcela: ${Fmt.date(it, today)}" + if (it.isBefore(today)) " (atrasada)." else ".") }
            if (last != null) appendLine("Seguindo o contrato, termina em ${monthName(last, today)} e ainda saem ${brl(remainingFace)} no total.")
            if (!contract) appendLine("Sem contrato de parcelas cadastrado: o prazo depende de quanto for pago por mês.")
            if (prepay != null) appendLine("Antecipar ${brl(prepay.amountCents)} do livre de hoje elimina ${prepay.installmentsCut} parcela(s) do fim e evita cerca de ${brl(prepay.savedCents)} em juros (estimativa pela taxa informada, não é proposta do credor).")
            else if (d.taxaJurosMensalBasisPoints == 0) appendLine("Sem a taxa de juros não dá para estimar o ganho de antecipar. A economia real depende da cotação do credor.")
            append("Não negociei nem consultei o credor: valores de acordo precisam vir dele.")
        }
        val answer = AssistantAnswer(
            AnswerKind.Decision, d.nome, headline = "${brl(d.valorAbertoCentavos)} em aberto",
            metrics = listOfNotNull(
                AnswerMetric("Parcelas", "${d.parcelasRestantes} de ${brl(d.valorParcelaCentavos)}").takeIf { d.parcelasRestantes > 0 },
                next?.let { AnswerMetric("Próxima", Fmt.date(it, today), if (it.isBefore(today)) Tone.Bad else Tone.Neutral) },
                last?.let { AnswerMetric("Termina em", monthName(it, today)) },
                AnswerMetric("Ainda sai no total", brl(remainingFace)).takeIf { d.parcelasRestantes > 0 },
                prepay?.let { AnswerMetric("Antecipando ${brl(it.amountCents)}", "evita ~${brl(it.savedCents)} de juros", Tone.Good) },
            ),
            notes = listOfNotNull("O ganho de antecipar é estimativa pela taxa informada; a proposta real vem do credor.".takeIf { prepay != null }, "Sem a taxa de juros não estimo o ganho de antecipar.".takeIf { d.taxaJurosMensalBasisPoints == 0 }),
            options = listOf(AnswerOption("Quando posso quitar?", "Quando posso quitar ${d.nome}?"), AnswerOption("Simular quitação", "E se eu quitar ${d.nome}?")),
        )
        return ToolResult("dívida ${Fmt.clean(d.nome)}", facts, answer, listOf(EntityRef(EntityRef.DEBT, d.id, d.nome)))
    }

    /** Limites do mês com o gasto de cada um: usado pelo plano e pelos acompanhamentos. */
    internal fun budgets(s: AssistantSnapshot) = BudgetCalculator.forCategories(
        s.orcamentos.filter { it.mesReferencia == YearMonth.from(s.today).toString() },
        SpendingAnalysis.monthEntries(s.transacoes, YearMonth.from(s.today)).filter { !it.data.toLocalDate().isAfter(s.today) },
        s.today,
    )
}
