package com.finai.app.domain.scenario

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.domain.FinancialPlan
import com.finai.app.domain.Recommendation
import com.finai.app.domain.toLocalDate
import com.finai.app.util.formatBrl0
import java.time.LocalDate

enum class OptionKind(val label: String) {
    Asked("Como você perguntou"),
    CashNow("À vista hoje"),
    WaitPayday("Esperar e pagar à vista"),
}

enum class PeriodStatus { Ok, Tight, Short, AlreadyShort }

/** Veredito da opção, do melhor para o pior ([rank]). */
enum class Verdict(val label: String, val rank: Int) {
    Fits("cabe", 0),
    Tight("cabe, mas fica apertado", 1),
    AlreadyShort("não cabe: as contas já não fechavam antes, e a compra aumenta a falta", 2),
    DoesNotFit("não cabe", 3),
}

/** Um período com e sem a compra. */
data class PeriodImpact(
    val start: LocalDate,
    val end: LocalDate,
    val current: Boolean,
    val estimatedIncome: Boolean,
    val incomeCents: Long,
    val livreBeforeCents: Long,
    val livreAfterCents: Long,
    /** Dia do menor saldo com a compra. */
    val worstDate: LocalDate,
    val payments: List<ScheduledPayment>,
    val debtInstallmentsCents: Long,
    /** Faturas de cartão em aberto que vencem no período. */
    val invoices: List<ContaEntity>,
    /** "a 1ª parcela cai junto com a Fatura Nubank de 10/10 (R$ 900)". */
    val collisions: List<String>,
    val status: PeriodStatus,
) {
    val purchaseCents: Long get() = payments.sumOf { it.cents }
    val reductionCents: Long get() = (livreBeforeCents - livreAfterCents).coerceAtLeast(0)
    val affected: Boolean get() = payments.isNotEmpty()
}

data class OptionResult(
    val kind: OptionKind,
    val scenario: PurchaseScenario,
    val periods: List<PeriodImpact>,
    val verdict: Verdict,
    /** Recomendação do plano para o livre do período atual, antes e depois da compra. */
    val recommendationsBefore: List<Recommendation> = emptyList(),
    val recommendationsAfter: List<Recommendation> = emptyList(),
) {
    val affected: List<PeriodImpact> get() = periods.filter { it.affected }
    val worst: PeriodImpact? get() = affected.minByOrNull { it.livreAfterCents }
    /** Nenhuma saída fica sem dinheiro em nenhum período com pagamento. */
    val billsCovered: Boolean get() = affected.none { it.livreAfterCents < 0 }
}

/**
 * O resultado inteiro da simulação — a única fonte dos números que a resposta pode citar.
 * [factsBlock] é o que a IA recebe; [reply] é a resposta pronta, sem IA, usada quando a IA não
 * está disponível ou quando a resposta dela cita um valor que não está aqui
 * ([ScenarioReplyGuard]).
 */
data class ScenarioResult(
    val today: LocalDate,
    val byCycle: Boolean,
    val asked: OptionResult,
    val alternatives: List<OptionResult>,
    val notes: List<String>,
) {
    val options: List<OptionResult> get() = listOf(asked) + alternatives

    /** Melhor pelos números: veredito, depois o menor custo total, depois a opção perguntada. */
    val best: OptionResult get() = options.minWith(
        compareBy<OptionResult> { it.verdict.rank }.thenBy { it.scenario.totalCents }.thenBy { it.kind.ordinal },
    )

    private fun periodName(p: PeriodImpact): String = when {
        byCycle && p.current -> "Ciclo atual, até ${dm(p.end)}"
        byCycle -> "Ciclo de ${dm(p.start)} a ${dm(p.end)}"
        p.current -> "Resto deste mês, até ${dm(p.end)}"
        else -> "Mês de ${dm(p.start)} a ${dm(p.end)}"
    }

    private fun statusText(s: PeriodStatus) = when (s) {
        PeriodStatus.Ok -> "cabe"
        PeriodStatus.Tight -> "apertado"
        PeriodStatus.Short -> "falta dinheiro"
        PeriodStatus.AlreadyShort -> "já faltava antes da compra"
    }

    private fun periodLine(p: PeriodImpact): String = buildString {
        append("${periodName(p)}: ")
        append(p.payments.joinToString(" + ") { pay -> if (pay.of > 1) "parcela ${pay.number} de ${brl(pay.cents)} em ${dm(pay.date)}" else "${brl(pay.cents)} em ${dm(pay.date)}" })
        append("; livre ${sign(p.livreBeforeCents)} → ${sign(p.livreAfterCents)} (${statusText(p.status)})")
        if (p.livreAfterCents < 0) append(", menor saldo em ${dm(p.worstDate)}")
        if (p.estimatedIncome) append("; salário estimado, ainda não lançado")
        if (p.debtInstallmentsCents > 0) append("; parcelas de dívidas já previstas no período: ${brl(p.debtInstallmentsCents)}")
        p.collisions.forEach { append("; $it") }
    }

    private fun optionSummary(o: OptionResult): String = buildString {
        append("${o.kind.label} (${o.scenario.label}): ${o.verdict.label}")
        o.worst?.let { w ->
            if (w.livreAfterCents < 0) append("; falta ${brl(-w.livreAfterCents)} em ${dm(w.worstDate)}")
            else append("; menor livre com a compra: ${brl(w.livreAfterCents)}, no ${periodName(w).lowercase()}")
        }
        append("; custo total ${brl(o.scenario.totalCents)}")
    }

    private fun recommendationLine(o: OptionResult): String? {
        val before = o.recommendationsBefore
        if (before.isEmpty()) return null
        val after = o.recommendationsAfter.associateBy { it.target to it.id }
        val changes = before.mapNotNull { r ->
            val now = after[r.target to r.id]?.cents ?: 0
            if (now == r.cents) null else "\"${r.name}\" de ${brl(r.cents)} para ${brl(now)}"
        }
        return if (changes.isEmpty()) "A recomendação do plano para o livre não muda."
        else "Sobra menos para a recomendação do plano: " + changes.joinToString("; ") + "."
    }

    /** Tudo o que a IA pode citar. Cada número aqui foi calculado pelo app. */
    fun factsBlock(): String = buildString {
        appendLine("Simulação calculada pelo app (nada foi lançado; nenhum saldo mudou):")
        appendLine("Compra simulada: ${asked.scenario.label}. Total: ${brl(asked.scenario.totalCents)}.")
        appendLine("Veredito: ${asked.verdict.label}.")
        appendLine(
            if (asked.billsCovered) "Contas: todas continuam cobertas nos períodos com pagamento."
            else "Contas: alguma saída fica sem dinheiro: " + asked.affected.filter { it.livreAfterCents < 0 }
                .joinToString("; ") { "falta ${brl(-it.livreAfterCents)} em ${dm(it.worstDate)}" } + ".",
        )
        appendLine("Impacto por ${if (byCycle) "ciclo entre salários" else "mês"} (livre = menor saldo previsto no período; antes → depois):")
        asked.affected.forEach { appendLine("- ${periodLine(it)}") }
        val untouched = asked.periods.count { !it.affected }
        if (untouched > 0) appendLine("- Os outros $untouched período(s) simulados não mudam: o app não carrega sobra de um período para o outro.")
        recommendationLine(asked)?.let { appendLine(it) }
        if (alternatives.isNotEmpty()) {
            appendLine("Comparação:")
            options.forEach { appendLine("- ${optionSummary(it)}") }
            val b = best
            appendLine(if (b.kind == OptionKind.Asked) "Melhor pelos números: como você perguntou." else "Melhor pelos números: ${b.kind.label.lowercase()} (${b.scenario.label}).")
        }
        notes.forEach { appendLine("Observação: $it") }
    }.trimEnd()

    /** Uma linha curta por opção, para a comparação da resposta pronta. */
    private fun optionShort(o: OptionResult): String = buildString {
        append(
            when (o.kind) {
                OptionKind.Asked -> "Como você perguntou"
                OptionKind.CashNow -> "À vista hoje${if (o.scenario.totalCents != asked.scenario.totalCents) " (${brl(o.scenario.totalCents)})" else ""}"
                OptionKind.WaitPayday -> "Esperar ${if (byCycle) "o salário de" else "até"} ${dm(o.scenario.first)} e pagar à vista"
            },
        )
        append(": ${o.verdict.label}")
        o.worst?.let { w ->
            if (w.livreAfterCents < 0) append("; falta ${brl(-w.livreAfterCents)} em ${dm(w.worstDate)}")
            else append("; o livre mais baixo fica em ${brl(w.livreAfterCents)}")
        }
    }

    /** Resposta sem IA — sempre correta, porque só usa os números do [factsBlock]. */
    fun reply(): String = buildString {
        val cur = asked.periods.firstOrNull()
        val w = asked.worst
        when {
            w != null && w.livreAfterCents < 0 -> appendLine("{{Falta em ${dm(w.worstDate)}|${sign(w.livreAfterCents)}}}")
            cur != null && cur.affected -> appendLine("{{Livre ${if (byCycle) "até o salário" else "até o fim do mês"}|${brl(cur.livreAfterCents)}}}")
            w != null -> appendLine("{{Menor livre|${brl(w.livreAfterCents)}}}")
        }
        append("${asked.scenario.label}: **${asked.verdict.label}**. ")
        appendLine(
            if (asked.billsCovered) "Todas as contas continuam cobertas."
            else "Alguma conta fica sem dinheiro.",
        )
        appendLine()
        asked.affected.forEach { p ->
            append("- ${periodName(p)}: livre de ${sign(p.livreBeforeCents)} para ${sign(p.livreAfterCents)}")
            if (p.status == PeriodStatus.Tight) append(" (apertado)")
            if (p.livreAfterCents < 0 && p.livreBeforeCents >= 0) append(", falta em ${dm(p.worstDate)}")
            if (p.estimatedIncome) append(", com salário estimado")
            append(".")
            p.collisions.forEach { append(" ${it.replaceFirstChar { c -> c.uppercase() }}.") }
            appendLine()
        }
        if (asked.periods.any { !it.affected }) appendLine("- Os outros períodos não mudam: o app não carrega sobra de um para o outro.")
        recommendationLine(asked)?.let { appendLine("- $it") }
        if (alternatives.isNotEmpty()) {
            appendLine()
            appendLine("Comparação:")
            options.forEach { appendLine("- ${optionShort(it)}.") }
            val b = best
            appendLine(if (b.kind == OptionKind.Asked) "Pelos números, o jeito que você perguntou é o melhor." else "Pelos números, a melhor opção é: ${optionShort(b).substringBefore(":").lowercase()}.")
        }
        notes.forEach { appendLine(it) }
        appendLine()
        append("Nada foi lançado. Se decidir comprar, toque em \"Registrar compra\".")
    }.trim()

    companion object {
        private fun brl(cents: Long) = formatBrl0(cents / 100.0)
        private fun sign(cents: Long) = if (cents < 0) "-${brl(-cents)}" else brl(cents)
        private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }
}

/**
 * Insere uma compra hipotética no fluxo financeiro central, só em memória, e recalcula o
 * impacto por data e por período. Nada é gravado: o cenário entra como lançamentos com id
 * negativo somados à cópia das listas do Room ([FinanceSnapshot]), e some quando a simulação
 * termina. Todo número da resposta vem daqui (planning.md §6: a IA só explica).
 */
object ScenarioSimulator {

    /** Abaixo disto o período fica "apertado": 10% das entradas dele, e nunca menos que R$ 100. */
    const val TIGHT_SHARE_PERCENT = 10
    const val TIGHT_MIN_CENTS = 10_000L

    fun tightThreshold(incomeCents: Long): Long = maxOf(TIGHT_MIN_CENTS, incomeCents * TIGHT_SHARE_PERCENT / 100)

    fun simulate(snapshot: FinanceSnapshot, intent: PurchaseIntent, today: LocalDate): ScenarioResult? {
        val payday = PeriodProjection.nextPayday(snapshot, today)
        val asked = PurchaseScenario.from(intent, today, payday) ?: return null
        val cashCents = intent.cashPriceCents ?: asked.totalCents
        val waitDate = payday ?: today.plusMonths(1).withDayOfMonth(1)

        val alternatives = buildList {
            val askedIsCashToday = asked.count == 1 && asked.first == today
            if (!askedIsCashToday && (asked.count > 1 || intent.waitForPayday || intent.cashPriceCents != null)) {
                add(OptionKind.CashNow to PurchaseScenario.cash(asked.description, cashCents, today))
            }
            if (asked.count > 1 || (askedIsCashToday && waitDate.isAfter(today))) {
                if (!(asked.count == 1 && asked.first == waitDate)) {
                    add(OptionKind.WaitPayday to PurchaseScenario.cash(asked.description, cashCents, waitDate))
                }
            }
        }

        val until = (listOf(asked) + alternatives.map { it.second }).maxOf { it.last }
        val base = PeriodProjection.periods(snapshot, emptyList(), today, until)
        val byCycle = PeriodProjection.hasCycle(snapshot, today)
        val planBefore = plan(snapshot, emptyList(), today)

        fun evaluate(kind: OptionKind, sc: PurchaseScenario): OptionResult {
            val with = PeriodProjection.periods(snapshot, sc.asTransactions(), today, until)
            val planAfter = plan(snapshot, sc.asTransactions(), today)
            val periods = base.zip(with).mapIndexed { i, (b, a) -> impact(i == 0, b, a, sc, snapshot, planBefore, planAfter) }
            return OptionResult(
                kind = kind,
                scenario = sc,
                periods = periods,
                verdict = verdictOf(periods),
                recommendationsBefore = planBefore.recommendations,
                recommendationsAfter = planAfter.recommendations,
            )
        }

        val notes = buildList {
            if (asked.methodAssumed) add("Considerei pagamento à vista por Pix/débito em ${dm(asked.first)}, a saída mais cedo possível. Se for no cartão, diga o vencimento da fatura.")
            if (asked.method == PaymentMethod.Unspecified) add("Considerei as datas informadas para cada parcela; a forma de pagamento não muda a conta.")
            if (asked.count > 1 && intent.cashPriceCents == null) add("Sem preço à vista informado, as opções à vista usam o mesmo valor total.")
            if (asked.count == 1 && intent.cashPriceCents == null) add("Para comparar com parcelado, diga em quantas vezes e quando vence a 1ª parcela.")
            if (!PeriodProjection.hasCycle(snapshot, today)) add("Sem renda principal cadastrada, a conta é por mês, não até o salário.")
            if (base.any { it.estimatedIncome }) add("Nos ciclos sem salário lançado, o app repete o último salário como estimativa.")
            base.lastOrNull()?.end?.takeIf { asked.last.isAfter(it) }?.let {
                add("As saídas depois de ${dm(it)} ficam fora da simulação (limite de ${PeriodProjection.MAX_PERIODS} períodos).")
            }
        }

        return ScenarioResult(
            today = today,
            byCycle = byCycle,
            asked = evaluate(OptionKind.Asked, asked),
            alternatives = alternatives.map { (k, sc) -> evaluate(k, sc) },
            notes = notes,
        )
    }

    private fun plan(s: FinanceSnapshot, extra: List<com.finai.app.data.local.entity.TransacaoEntity>, today: LocalDate): FinancialPlan =
        FinancialPlan.build(s.contas, s.transacoes + extra, s.dividas, s.objetivos, today)

    /** O livre do período atual é o do plano central — o mesmo número do topo da Início. */
    private fun FinancialPlan.livreSigned(): Long = shortfall?.let { -it.cents } ?: livreCents

    private fun impact(
        current: Boolean,
        before: PeriodState,
        after: PeriodState,
        sc: PurchaseScenario,
        s: FinanceSnapshot,
        planBefore: FinancialPlan,
        planAfter: FinancialPlan,
    ): PeriodImpact {
        val livreBefore = if (current) planBefore.livreSigned() else before.floorCents
        val livreAfter = if (current) planAfter.livreSigned() else after.floorCents
        val worst = if (current) planAfter.shortfall?.date ?: after.floorDate else after.floorDate
        val payments = sc.payments.filter { !it.date.isBefore(before.start) && !it.date.isAfter(before.end) }
        val invoices = s.openInvoices().filter { c -> c.vencimento.toLocalDate().let { !it.isBefore(before.start) && !it.isAfter(before.end) } }
        val collisions = payments.flatMap { p ->
            invoices.filter { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(it.vencimento.toLocalDate(), p.date)) <= 3 }.map { inv ->
                val what = if (p.of > 1) "a parcela ${p.number}" else "o pagamento"
                val how = if (inv.vencimento.toLocalDate() == p.date) "cai junto com" else "cai perto de"
                "$what $how ${inv.nome} de ${dm(inv.vencimento.toLocalDate())} (${brl(inv.valorCentavos)})"
            }
        }
        val status = when {
            livreBefore < 0 -> PeriodStatus.AlreadyShort
            livreAfter < 0 -> PeriodStatus.Short
            livreAfter < tightThreshold(before.incomeCents) -> PeriodStatus.Tight
            else -> PeriodStatus.Ok
        }
        return PeriodImpact(
            start = before.start,
            end = before.end,
            current = current,
            estimatedIncome = before.estimatedIncome,
            incomeCents = before.incomeCents,
            livreBeforeCents = livreBefore,
            livreAfterCents = livreAfter,
            worstDate = worst,
            payments = payments,
            debtInstallmentsCents = before.debtInstallmentsCents,
            invoices = invoices,
            collisions = collisions,
            status = status,
        )
    }

    private fun verdictOf(periods: List<PeriodImpact>): Verdict {
        val hit = periods.filter { it.affected }
        return when {
            hit.any { it.status == PeriodStatus.Short } -> Verdict.DoesNotFit
            hit.any { it.status == PeriodStatus.AlreadyShort } -> Verdict.AlreadyShort
            hit.any { it.status == PeriodStatus.Tight } -> Verdict.Tight
            else -> Verdict.Fits
        }
    }

    private fun brl(cents: Long) = formatBrl0(cents / 100.0)
    private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
}
