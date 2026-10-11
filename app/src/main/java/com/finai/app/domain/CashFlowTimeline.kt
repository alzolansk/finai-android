package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.scenario.FinanceSnapshot
import com.finai.app.domain.scenario.PeriodProjection
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

/** Estado de um movimento na Agenda sequencial, sempre em relação ao hoje de verdade. */
enum class MovementStatus(val label: String) {
    Done("Realizado"),
    Paid("Pago"),
    Received("Recebido"),
    Expected("Previsto"),
    DueToday("Vence hoje"),
    Overdue("Atrasado"),
    /** Salário que o app repetiu porque o do período ainda não foi lançado. */
    Estimated("Estimado"),
}

/**
 * Um movimento da linha do tempo. [balanceAfterCents] é o saldo do ciclo logo depois dele;
 * nulo quando não há renda principal (não há de onde partir, e o app não inventa um saldo).
 */
data class TimelineMovement(
    val date: LocalDate,
    val descricao: String,
    val cents: Long,
    val status: MovementStatus,
    val balanceAfterCents: Long?,
    val source: CycleSource?,
    /** Data original quando o movimento conta em outro dia (ver [CycleEntry.movedFrom]). */
    val movedFrom: LocalDate? = null,
    /** Renda principal: a referência visual que abre cada ciclo. */
    val mainIncome: Boolean = false,
    /** Onde acontece o menor saldo até o próximo salário — o "Livre até o salário". Só no ciclo atual. */
    val lowest: Boolean = false,
)

enum class SegmentKind { Previous, Current, Future }

/** Um ciclo entre salários na linha do tempo, com os movimentos em ordem e o saldo corrido. */
data class TimelineSegment(
    val kind: SegmentKind,
    /** Dia do salário que abre o ciclo, ou o 1º do mês quando nenhum salário caiu ainda. */
    val start: LocalDate,
    val startsWithSalary: Boolean,
    val nextPayday: LocalDate,
    val nextPaydayEstimated: Boolean,
    /** O salário que abre o ciclo não está lançado: o app repetiu o último. */
    val incomeEstimated: Boolean,
    val movements: List<TimelineMovement>,
    /** Menor saldo do ciclo (no atual, de hoje em diante). Nulo no ciclo anterior, que é só histórico. */
    val floor: CycleFloor?,
) {
    val end: LocalDate get() = nextPayday.minusDays(1)
    val entradasCents: Long get() = movements.filter { it.cents > 0 }.sumOf { it.cents }
    val saidasCents: Long get() = movements.filter { it.cents < 0 }.sumOf { -it.cents }
    /** Sobra no fim do ciclo. Não é o livre: o livre é o menor saldo ([floor]). */
    val sobraCents: Long get() = entradasCents - saidasCents
}

/**
 * A Agenda sequencial: o dinheiro em ordem cronológica, de salário a salário, sem recomeçar no
 * dia 1º de cada mês. Quem recebe dia 30/10 e paga a fatura em 12/11 vê a fatura saindo do
 * salário de 30/10, que é o que de fato acontece; a Agenda mensal ([MonthCashFlow]) continua
 * pondo a fatura em novembro, pelo vencimento.
 *
 * **Não calcula nada por conta própria.** Cada ciclo é um [PayCycle] — o atual é o mesmo do plano
 * central ([FinancialPlan.cycle]), e os seguintes vêm de [PeriodProjection.projectCycles], a mesma
 * projeção do simulador de compra. Daqui saem só a ordem, o saldo depois de cada movimento e o
 * estado de cada um. Por isso o menor saldo do ciclo atual é, por construção, o "Livre até o
 * salário" ([PayCycle.availableCents]).
 *
 * O saldo recomeça em zero a cada salário, como no [PayCycle]: a sobra de um ciclo não passa para
 * o seguinte (um gasto esquecido viraria dinheiro fantasma ciclo após ciclo). A sobra de cada
 * ciclo aparece no fim dele, separada.
 *
 * O ciclo anterior entra só como histórico, com o que de fato aconteceu: conta não paga e parcela
 * em aberto de lá já aparecem no ciclo atual, como devidas hoje.
 */
data class CashFlowTimeline(
    val today: LocalDate,
    /** O ciclo atual; nulo sem renda principal. */
    val cycle: PayCycle?,
    val segments: List<TimelineSegment>,
    /** Sem renda principal: os movimentos deste mês e dos dois seguintes, em ordem, sem saldo. */
    val unprojected: List<TimelineMovement> = emptyList(),
) {
    val current: TimelineSegment? get() = segments.firstOrNull { it.kind == SegmentKind.Current }

    /**
     * O fluxo como a IA recebe, para perguntas como "tenho dinheiro para a fatura do dia 12?",
     * "quanto do meu salário já está comprometido?" ou "quanto vai sobrar até o salário?". Tudo
     * calculado aqui; a IA só lê. Descrições curtas e sem sequências de 4+ dígitos (planning.md §4).
     */
    fun aiBlock(maxUpcoming: Int = 14, maxNext: Int = 8): String {
        val c = cycle
        val seg = current
        if (c == null || seg == null) {
            return "Fluxo cronológico: sem renda principal cadastrada, o app não projeta o saldo entre salários " +
                "(não sabe quando o dinheiro entra). Não estime esse saldo; sugira marcar a renda principal na Agenda."
        }
        return buildString {
            appendLine(
                "Fluxo cronológico calculado pelo app (Agenda sequencial). O saldo é do ciclo entre salários: " +
                    "continua na virada do mês e recomeça a cada salário. Não é saldo de banco.",
            )
            val desde = c.inicio?.let { "do salário de ${dm(it)} (${brl(c.salarioCents)})" }
                ?: "de ${dm(seg.start)} (nenhum salário caiu ainda; soma o que entrou desde o dia 1º)"
            appendLine("- Ciclo atual: $desde até a véspera do próximo salário, ${dm(c.proximo)}" + (if (c.proximoEstimado) " (data estimada)." else "."))
            appendLine(
                "- Entradas do ciclo: ${brl(c.entradasCents)}, das quais ${brl(c.aReceberCents)} ainda vão entrar. " +
                    "Já saiu: ${brl(c.jaSaiuCents)}. Ainda vai sair: ${brl(c.comprometidoCents)}.",
            )
            if (c.entradasCents > 0) {
                val saidas = c.jaSaiuCents + c.comprometidoCents
                appendLine("- Comprometido das entradas do ciclo (já saiu + ainda vai sair): ${brl(saidas)}, ${saidas * 100 / c.entradasCents}% das entradas.")
            }
            appendLine("- Saldo do ciclo no fim de hoje (${dm(today)}): ${signed(c.saldoHojeCents)}.")
            val dueToday = seg.movements.filter { it.date == today && it.status in pendingToday }
            if (dueToday.isNotEmpty()) {
                appendLine("- Pendente hoje (já descontado desse saldo): " + dueToday.joinToString("; ") { "${label(it)} ${signed(it.cents)}" } + ".")
            }
            val upcoming = seg.movements.filter { it.date.isAfter(today) }
            if (upcoming.isEmpty()) {
                appendLine("- Nada previsto daqui até o salário.")
            } else {
                appendLine("- Daqui até o salário, em ordem (saldo do ciclo depois de cada movimento):")
                upcoming.take(maxUpcoming).forEach { appendLine("  ${dm(it.date)} ${label(it)}: ${signed(it.cents)} → saldo ${signed(it.balanceAfterCents ?: 0)}") }
                if (upcoming.size > maxUpcoming) appendLine("  (mais ${upcoming.size - maxUpcoming} movimentos até o salário)")
            }
            appendLine(
                "- Menor saldo até o salário: ${signed(c.floor.cents)} em ${dm(c.floor.date)}. " +
                    "Livre para gastar hoje sem deixar nenhum pagamento acima sem dinheiro: ${brl(c.availableCents)}.",
            )
            c.shortfall?.let { appendLine("- Falta prevista: ${brl(it.cents)} a partir de ${dm(it.date)}" + (it.causa?.let { s -> " (${clean(s)})" } ?: "") + ".") }
            appendLine("- Sobra no fim do ciclo (véspera de ${dm(c.proximo)}): ${signed(c.livreCents)}. Não é o livre de hoje: o livre é o menor saldo.")
            segments.firstOrNull { it.kind == SegmentKind.Future }?.let { n ->
                val floor = n.floor
                appendLine(
                    "- Próximo ciclo (${dm(n.start)} a ${dm(n.end)}): começa do zero com " +
                        (if (n.incomeEstimated) "o salário estimado (repetição do último)" else "o salário") +
                        (floor?.let { "; menor saldo previsto ${signed(it.cents)} em ${dm(it.date)}" } ?: "") +
                        "; sobra no fim ${signed(n.sobraCents)}.",
                )
                val out = n.movements.filter { it.cents < 0 }
                if (out.isNotEmpty()) {
                    appendLine("  Pagamentos desse ciclo: " + out.take(maxNext).joinToString("; ") {
                        "${dm(it.date)} ${label(it)} ${signed(it.cents)} (saldo ${signed(it.balanceAfterCents ?: 0)})"
                    } + (if (out.size > maxNext) "; e mais ${out.size - maxNext}" else "") + ".")
                }
            }
            append(
                "Regras: para dizer se um pagamento cabe, use o saldo do ciclo antes e depois dele nesta sequência e o menor " +
                    "saldo até o salário, não o balanço do mês do vencimento (uma conta de um mês pode ser paga com o salário do " +
                    "mês anterior). Um gasto novo hoje cabe se não passar do livre. A sobra de um ciclo não passa para o seguinte.",
            )
        }
    }

    companion object {
        const val DEFAULT_FUTURE_CYCLES = 3

        private val pendingToday = setOf(MovementStatus.Overdue, MovementStatus.DueToday)

        /**
         * [cycle] é o ciclo atual já calculado pelo plano central ([FinancialPlan.cycle]); nulo
         * calcula de novo com as mesmas listas (dá o mesmo resultado).
         */
        fun build(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            today: LocalDate,
            cycle: PayCycle? = null,
            futureCycles: Int = DEFAULT_FUTURE_CYCLES,
        ): CashFlowTimeline {
            val snapshot = FinanceSnapshot(contas, transacoes, dividas, emptyList())
            val projected = PeriodProjection.projectCycles(
                snapshot, emptyList(), today, until = today.plusYears(2), maxPeriods = 1 + futureCycles, first = cycle,
            ) ?: return CashFlowTimeline(today, null, emptyList(), unprojected(contas, transacoes, dividas, today))

            val salaryIds = PayCycle.salariesOf(transacoes).map { it.id }.toSet()
            val current = projected.first().cycle
            val previous = current.inicio?.let { inicio ->
                runCatching { PayCycle.of(contas, transacoes, dividas, inicio.minusDays(1), asOf = today) }.getOrNull()
            }?.takeIf { it.proximo == current.inicio }

            val segments = buildList {
                // Histórico: só o que aconteceu. O que ficou em aberto lá já está no ciclo atual, hoje.
                previous?.let { add(segment(it, SegmentKind.Previous, false, today, salaryIds) { e -> realized(e) }) }
                projected.forEachIndexed { i, p ->
                    add(segment(p.cycle, if (i == 0) SegmentKind.Current else SegmentKind.Future, p.estimatedIncome, today, salaryIds))
                }
            }
            return CashFlowTimeline(today, current, segments)
        }

        /** O estado do movimento visto de hoje (num ciclo projetado, o "hoje" do [PayCycle] é o dia do salário). */
        fun statusOf(entry: CycleEntry, today: LocalDate): MovementStatus = when (val s = entry.source) {
            is CycleSource.Lancamento -> when {
                s.transacao.id < 0 -> MovementStatus.Estimated
                entry.date.isAfter(today) -> MovementStatus.Expected
                else -> MovementStatus.Done
            }
            is CycleSource.Conta -> {
                val due = s.conta.vencimento.toLocalDate()
                when {
                    s.conta.status == "pago" -> if (s.conta.tipo == "a_receber") MovementStatus.Received else MovementStatus.Paid
                    due.isBefore(today) -> MovementStatus.Overdue
                    due == today -> MovementStatus.DueToday
                    else -> MovementStatus.Expected
                }
            }
            is CycleSource.Parcela -> {
                val due = s.parcela.vencimento
                when {
                    due != null && due.isBefore(today) -> MovementStatus.Overdue
                    due == today -> MovementStatus.DueToday
                    else -> MovementStatus.Expected
                }
            }
            null -> if (entry.date.isAfter(today)) MovementStatus.Expected else MovementStatus.Done
        }

        private fun realized(e: CycleEntry): Boolean = e.movedFrom == null && when (val s = e.source) {
            is CycleSource.Parcela -> false
            is CycleSource.Conta -> s.conta.status == "pago"
            else -> true
        }

        private fun isMainIncome(e: CycleEntry, salaryIds: Set<Long>): Boolean {
            val t = (e.source as? CycleSource.Lancamento)?.transacao ?: return false
            return e.cents > 0 && (t.id in salaryIds || t.id < 0)
        }

        private fun segment(
            c: PayCycle,
            kind: SegmentKind,
            estimated: Boolean,
            today: LocalDate,
            salaryIds: Set<Long>,
            keep: (CycleEntry) -> Boolean = { true },
        ): TimelineSegment {
            val entries = c.ordered.filter(keep)
            val floor = if (kind == SegmentKind.Previous) null else c.floor
            // O menor saldo cai no fim do dia dele: o último movimento daquele dia.
            val lowestIndex = if (kind == SegmentKind.Current && floor != null) entries.indexOfLast { it.date == floor.date } else -1
            var running = 0L
            val movements = entries.mapIndexed { i, e ->
                running += e.cents
                TimelineMovement(
                    date = e.date,
                    descricao = e.descricao,
                    cents = e.cents,
                    status = statusOf(e, today),
                    balanceAfterCents = running,
                    source = e.source,
                    movedFrom = e.movedFrom,
                    mainIncome = isMainIncome(e, salaryIds),
                    lowest = i == lowestIndex,
                )
            }
            return TimelineSegment(
                kind = kind,
                start = c.inicio ?: c.contaDesde ?: c.today,
                startsWithSalary = c.inicio != null,
                nextPayday = c.proximo,
                nextPaydayEstimated = c.proximoEstimado,
                incomeEstimated = estimated,
                movements = movements,
                floor = floor,
            )
        }

        /**
         * Sem renda principal não há ciclo: lista o que entra e sai deste mês e dos dois seguintes,
         * pelas mesmas regras do mês ([MonthCashFlow]), sem saldo.
         */
        private fun unprojected(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            today: LocalDate,
        ): List<TimelineMovement> {
            val entries = (0L..2L).map { YearMonth.from(today).plusMonths(it) }.flatMap { m ->
                val flow = MonthCashFlow.of(contas, transacoes, dividas, m, today)
                buildList {
                    (flow.payable + flow.receivable).forEach { conta ->
                        val sign = if (conta.tipo == "a_receber") 1 else -1
                        add(CycleEntry(conta.vencimento.toLocalDate(), conta.nome, sign * conta.valorCentavos, conta.status == "pago", CycleSource.Conta(conta)))
                    }
                    (flow.recorrentes + flow.avulsas).filter { it.freeCashSign != 0 }.forEach { t ->
                        val d = t.data.toLocalDate()
                        add(CycleEntry(d, t.descricao, t.freeCashSign * t.valorCentavos, !d.isAfter(today), CycleSource.Lancamento(t)))
                    }
                    flow.debtsDue.forEach { p ->
                        add(CycleEntry(p.vencimento ?: maxOf(m.atDay(1), today), p.divida.nome, -p.valorCentavos, false, CycleSource.Parcela(p)))
                    }
                }
            }
            return entries.sortedWith(compareBy<CycleEntry> { it.date }.thenByDescending { it.cents }).map { e ->
                TimelineMovement(e.date, e.descricao, e.cents, statusOf(e, today), balanceAfterCents = null, source = e.source)
            }
        }

        private fun brl(cents: Long) = formatBrl0(cents / 100.0)
        private fun signed(cents: Long) = if (cents < 0) "-" + brl(-cents) else brl(cents)
        private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

        /** Sem sequências longas de dígitos (final de cartão, código de loja) e curta. */
        private fun clean(text: String) = text.replace(Regex("""\d{4,}"""), "").replace(Regex("""\s+"""), " ").trim().take(32)

        private fun label(m: TimelineMovement): String {
            val base = clean(m.descricao).ifBlank {
                (m.source as? CycleSource.Lancamento)?.transacao?.categoria ?: "Movimento"
            }
            val parcela = (m.source as? CycleSource.Parcela)?.parcela?.takeIf { it.numero != null }?.let { " (parcela ${it.numero} de ${it.total})" }.orEmpty()
            val tag = when (m.status) {
                MovementStatus.Overdue -> " (atrasado" + (m.movedFrom?.let { ", venceu ${dm(it)}" } ?: "") + ")"
                MovementStatus.Estimated -> " (estimado)"
                MovementStatus.Paid -> m.movedFrom?.let { " (já pago; vence ${dm(it)})" } ?: " (pago)"
                MovementStatus.Received -> m.movedFrom?.let { " (já recebido; previsto para ${dm(it)})" } ?: " (recebido)"
                else -> ""
            }
            return base + parcela + tag
        }
    }
}
