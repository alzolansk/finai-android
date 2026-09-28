package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Um movimento dentro do ciclo: positivo entra, negativo sai. */
data class CycleEntry(val date: LocalDate, val descricao: String, val cents: Long, val done: Boolean)

/**
 * Dia em que o dinheiro do ciclo deixa de fechar, o maior buraco até o próximo salário e o
 * compromisso que abriu o buraco ([causa]). Já negativo hoje: [date] é quando ficou negativo.
 */
data class CycleShortfall(val date: LocalDate, val cents: Long, val causa: String? = null)

/** Menor saldo previsto de hoje até a véspera do próximo salário, e o dia em que ele acontece. */
data class CycleFloor(val date: LocalDate, val cents: Long)

/**
 * O ciclo entre dois salários — responde "o dinheiro que tenho aguenta até o próximo
 * salário?", que o balanço do mês ([MonthCashFlow]) não responde: quem recebe dia 30 paga
 * a fatura do dia 12 com o salário anterior, não com o do mesmo mês.
 *
 * Não pede saldo de banco (o usuário pode ter várias contas, e manter isso em dia seria
 * trabalho manual): o ciclo começa no último salário que caiu e termina na véspera do
 * próximo. Salário = receita com "salário"/"holerite" na descrição ([isSalary]), avulsa ou
 * recorrente — o último do estágio e o proporcional são avulsos e também contam. Sem
 * nenhuma assim, vale a maior receita recorrente. Entrada extra nunca é salário, mas entra
 * como dinheiro do ciclo, como qualquer outra receita. Sobra do ciclo anterior não é
 * carregada — de propósito: um gasto esquecido viraria dinheiro fantasma ciclo após ciclo.
 *
 * Saídas do ciclo: gastos lançados (recorrentes pela ocorrência), contas a pagar pelo
 * vencimento (a fatura entra aqui, não os itens dela) e parcelas de dívida em aberto. O que
 * ficou vencido e não foi pago no ciclo anterior continua devido, e entra como compromisso
 * de hoje.
 */
data class PayCycle(
    val salarioNome: String,
    val salarioCents: Long,
    /** Dia em que caiu o salário que abriu o ciclo; nulo se o primeiro ainda não caiu. */
    val inicio: LocalDate?,
    val proximo: LocalDate,
    /** Nenhum salário lançado depois de hoje: [proximo] é um mês depois do último, estimado. */
    val proximoEstimado: Boolean,
    val entries: List<CycleEntry>,
    val today: LocalDate,
    /**
     * Verdadeiro quando a renda foi marcada como principal pelo usuário (ou, em dado antigo,
     * tem "salário" no nome). Falso quando o app deduziu a maior receita recorrente.
     */
    val salarioDefinido: Boolean = true,
) {
    val entradasCents: Long get() = entries.filter { it.cents > 0 }.sumOf { it.cents }
    val aReceberCents: Long get() = entries.filter { it.cents > 0 && !it.done }.sumOf { it.cents }
    val jaSaiuCents: Long get() = entries.filter { it.cents < 0 && it.done }.sumOf { -it.cents }
    val comprometidoCents: Long get() = entries.filter { it.cents < 0 && !it.done }.sumOf { -it.cents }
    val livreCents: Long get() = entradasCents - jaSaiuCents - comprometidoCents

    /** Dias a partir de hoje em que o dinheiro precisa durar (o dia do salário já é do próximo ciclo). */
    val diasAteProximo: Int get() = ChronoUnit.DAYS.between(today, proximo).toInt().coerceAtLeast(1)

    val progress: Float get() {
        val start = inicio ?: return 0f
        val total = ChronoUnit.DAYS.between(start, proximo).toFloat()
        return if (total <= 0f) 0f else (ChronoUnit.DAYS.between(start, today) / total).coerceIn(0f, 1f)
    }

    private val ordered: List<CycleEntry> get() =
        entries.sortedWith(compareBy<CycleEntry> { it.date }.thenByDescending { it.cents })

    /**
     * O menor saldo corrido de hoje em diante. É o que dá para gastar agora sem que nenhum
     * compromisso até o salário fique sem dinheiro: o total do ciclo ([livreCents]) pode
     * fechar positivo e ainda assim faltar no dia 12 se uma entrada só cai no dia 20.
     */
    val floor: CycleFloor get() {
        // Saldo de fim de dia, a partir de hoje: o que entra no mesmo dia de uma saída cobre a
        // saída. Antes, o saldo "antes de hoje" contava como candidato sozinho — no dia do
        // salário o ciclo começa hoje, esse saldo é zero, e o livre saía R$ 0 com o salário
        // já caindo.
        var running = entries.filter { it.date.isBefore(today) }.sumOf { it.cents }
        val byDay = entries.filter { !it.date.isBefore(today) }.groupBy { it.date }.toSortedMap()
        running += byDay.remove(today)?.sumOf { it.cents } ?: 0L
        var floor = CycleFloor(today, running)
        byDay.forEach { (date, dayEntries) ->
            running += dayEntries.sumOf { it.cents }
            if (running < floor.cents) floor = CycleFloor(date, running)
        }
        return floor
    }

    /**
     * Falta prevista de hoje até o salário. Um buraco que já passou e se fechou (uma entrada
     * cobriu depois) não conta: o que importa é se o dinheiro fecha daqui para frente.
     */
    val shortfall: CycleShortfall? get() {
        val worst = floor.cents
        if (worst >= 0) return null
        var running = 0L
        var since: CycleEntry? = null
        for (e in ordered) {
            val before = running
            running += e.cents
            if (e.date.isBefore(today)) {
                since = if (running < 0) since ?: e else null
                continue
            }
            if (before >= 0 && running < 0 && since == null) since = e
            if (since != null) break
        }
        val causa = since?.takeIf { it.cents < 0 }?.descricao?.ifBlank { null }
        return CycleShortfall(since?.date ?: today, -worst, causa)
    }

    companion object {
        private val salaryWords = listOf("salario", "holerite")
        private val notSalary = Regex("""decimo|ferias|(^|\D)13(\D|$)""")

        /** Sem acento e minúsculo: "Salário", "SALARIO" e "salario" são a mesma coisa. */
        private fun normalize(text: String): String =
            java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

        /**
         * O app não tem campo "salário" (e a descrição é livre), então reconhece pelo nome.
         * "13º salário" e "férias" ficam de fora: são entrada extra, não o dinheiro do mês.
         */
        fun isSalary(t: TransacaoEntity): Boolean {
            if (t.tipo != TransactionType.Receita.name || t.extra || t.faturaId != null || t.valorCentavos <= 0) return false
            val d = normalize(t.descricao)
            return salaryWords.any { it in d } && !notSalary.containsMatchIn(d)
        }

        /** Receita que conta como renda principal: marcada pelo usuário, ou reconhecida pelo nome. */
        fun isMainIncome(t: TransacaoEntity): Boolean =
            t.tipo == TransactionType.Receita.name && !t.extra && t.faturaId == null && t.valorCentavos > 0 &&
                (t.rendaPrincipal || isSalary(t))

        /**
         * A marcação explícita ([TransacaoEntity.rendaPrincipal]) manda. O nome é só fallback
         * para dado que ainda não foi marcado; sem nenhum dos dois, vale a maior recorrente.
         */
        fun salariesOf(transacoes: List<TransacaoEntity>): List<TransacaoEntity> {
            val marked = transacoes.filter { it.rendaPrincipal && isMainIncome(it) }
            if (marked.isNotEmpty()) return marked
            val byName = transacoes.filter(::isSalary)
            if (byName.isNotEmpty()) return byName
            return listOfNotNull(transacoes
                .filter { it.recorrente && it.tipo == TransactionType.Receita.name && it.faturaId == null && !it.extra && it.valorCentavos > 0 }
                .maxWithOrNull(compareBy<TransacaoEntity> { it.valorCentavos }.thenByDescending { it.data }))
        }

        /** Dias de pagamento perto de hoje: a data do avulso, ou a ocorrência mensal do recorrente. */
        private fun payDates(t: TransacaoEntity, today: LocalDate): List<LocalDate> {
            val first = t.data.toLocalDate()
            if (!t.recorrente) return listOf(first)
            val cur = YearMonth.from(today)
            return (-1L..1L).map { cur.plusMonths(it) }
                .filter { it >= YearMonth.from(first) }
                .map { monthlyOccurrence(first, it) } + first
        }

        fun of(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            today: LocalDate,
        ): PayCycle? {
            val paydays = salariesOf(transacoes).flatMap { t -> payDates(t, today).map { it to t } }
            val last = paydays.filter { !it.first.isAfter(today) }.maxByOrNull { it.first }
            val next = paydays.filter { it.first.isAfter(today) }.minByOrNull { it.first }
            if (last == null && next == null) return null
            val inicio = last?.first
            val proximo = next?.first ?: inicio!!.plusMonths(1)
            val salario = (last ?: next)!!.second
            val start = inicio ?: today
            fun inCycle(d: LocalDate) = !d.isBefore(start) && d.isBefore(proximo)
            val months = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }
                .takeWhile { it <= YearMonth.from(proximo) }.toList()

            val lancamentos = months.flatMap { m ->
                transacoes.filter { it.faturaId == null && it.tipo != TransactionType.Transferencia.name }
                    .transactionsInMonth(m.atDay(1))
            }.mapNotNull { t ->
                val d = t.data.toLocalDate()
                if (!inCycle(d)) return@mapNotNull null
                val sign = if (t.tipo == TransactionType.Receita.name) 1 else -1
                CycleEntry(d, t.descricao, sign * t.valorCentavos, done = !d.isAfter(today))
            }

            val contasEntries = contas.mapNotNull { c ->
                val d = c.vencimento.toLocalDate()
                val pago = c.status == "pago"
                val sign = if (c.tipo == "a_receber") 1 else -1
                when {
                    inCycle(d) -> CycleEntry(d, c.nome, sign * c.valorCentavos, done = pago)
                    // Conta a pagar vencida e não paga: ainda sai deste dinheiro, hoje.
                    d.isBefore(start) && !pago && c.tipo == "a_pagar" -> CycleEntry(today, c.nome, -c.valorCentavos, done = false)
                    else -> null
                }
            }

            // Parcelas desde a mais antiga em aberto — as vencidas antes do ciclo continuam devidas.
            val firstDebtMonth = dividas.mapNotNull { it.proximoVencimento?.toLocalDate()?.let(YearMonth::from) }
                .minOrNull()?.coerceAtMost(YearMonth.from(start)) ?: YearMonth.from(start)
            val parcelas = generateSequence(firstDebtMonth) { it.plusMonths(1) }
                .takeWhile { it <= YearMonth.from(proximo) }
                .flatMap { m -> DebtSchedule.installmentsInMonth(dividas, m, today).asSequence().map { m to it } }
                .mapNotNull { (m, p) ->
                    // Sem dia definido: ancorada no mês, conta como devida no primeiro dia dele que cai no ciclo.
                    val due = p.vencimento ?: maxOf(m.atDay(1), today)
                    val date = when {
                        due.isBefore(start) -> today
                        inCycle(due) -> due
                        else -> return@mapNotNull null
                    }
                    CycleEntry(date, p.divida.nome, -p.valorCentavos, done = false)
                }.toList()

            return PayCycle(
                salarioNome = salario.descricao.ifBlank { salario.categoria },
                salarioDefinido = isMainIncome(salario),
                salarioCents = salario.valorCentavos,
                inicio = inicio,
                proximo = proximo,
                proximoEstimado = next == null,
                entries = lancamentos + contasEntries + parcelas,
                today = today,
            )
        }
    }
}
