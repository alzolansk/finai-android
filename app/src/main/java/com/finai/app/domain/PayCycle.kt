package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Um movimento dentro do ciclo: positivo entra, negativo sai. */
data class CycleEntry(val date: LocalDate, val descricao: String, val cents: Long, val done: Boolean)

/** Primeiro dia em que o dinheiro do ciclo não fecha, e o maior buraco até o próximo salário. */
data class CycleShortfall(val date: LocalDate, val cents: Long)

/**
 * O ciclo entre dois salários — responde "o dinheiro que tenho aguenta até o próximo
 * salário?", que o balanço do mês ([MonthCashFlow]) não responde: quem recebe dia 30 paga
 * a fatura do dia 12 com o salário anterior, não com o do mesmo mês.
 *
 * Não pede saldo de banco (o usuário pode ter várias contas, e manter isso em dia seria
 * trabalho manual): o ciclo começa no último salário que caiu e termina na véspera do
 * próximo. Salário = a maior receita recorrente cadastrada (não extra); outras receitas
 * que caem no meio do ciclo também entram. Sobra do ciclo anterior não é carregada — de
 * propósito: um gasto esquecido viraria dinheiro fantasma ciclo após ciclo.
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
    val entries: List<CycleEntry>,
    val today: LocalDate,
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

    /**
     * Saldo dia a dia, na ordem em que as coisas acontecem: o total do ciclo pode fechar
     * positivo e ainda assim faltar dinheiro no dia 12 se uma entrada só cai no dia 20.
     */
    val shortfall: CycleShortfall? get() {
        var running = 0L
        var firstNegative: LocalDate? = null
        var worst = 0L
        entries.sortedWith(compareBy<CycleEntry> { it.date }.thenByDescending { it.cents }).forEach { e ->
            running += e.cents
            if (running < 0 && firstNegative == null) firstNegative = e.date
            if (running < worst) worst = running
        }
        return firstNegative?.let { CycleShortfall(it, -worst) }
    }

    companion object {
        /** A maior receita recorrente — o app não tem um campo "salário", a descrição é livre. */
        fun salaryOf(transacoes: List<TransacaoEntity>): TransacaoEntity? = transacoes
            .filter { it.recorrente && it.tipo == TransactionType.Receita.name && it.faturaId == null && !it.extra && it.valorCentavos > 0 }
            .maxWithOrNull(compareBy<TransacaoEntity> { it.valorCentavos }.thenByDescending { it.data })

        fun of(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            today: LocalDate,
        ): PayCycle? {
            val salario = salaryOf(transacoes) ?: return null
            val firstPay = salario.data.toLocalDate()
            fun payIn(month: YearMonth): LocalDate? =
                if (month < YearMonth.from(firstPay)) null else DebtSchedule.dueDateIn(month, firstPay.dayOfMonth)

            val cur = YearMonth.from(today)
            val inicio = listOf(cur, cur.minusMonths(1)).mapNotNull(::payIn).firstOrNull { !it.isAfter(today) }
            val proximo = if (firstPay.isAfter(today)) firstPay
            else listOf(cur, cur.plusMonths(1)).mapNotNull(::payIn).first { it.isAfter(today) }
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
                salarioCents = salario.valorCentavos,
                inicio = inicio,
                proximo = proximo,
                entries = lancamentos + contasEntries + parcelas,
                today = today,
            )
        }
    }
}
