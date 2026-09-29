package com.finai.app.domain.scenario

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.TransactionType
import com.finai.app.domain.monthlyOccurrence
import com.finai.app.domain.toEpochMillis
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

/**
 * O que o simulador lê: as mesmas listas do Room que o plano central ([com.finai.app.domain.FinancialPlan])
 * usa. É uma cópia em memória — o cenário é somado a ela, nunca gravado.
 */
data class FinanceSnapshot(
    val contas: List<ContaEntity>,
    val transacoes: List<TransacaoEntity>,
    val dividas: List<DividaEntity>,
    val objetivos: List<ObjetivoEntity>,
    val faturas: List<FaturaCartaoEntity> = emptyList(),
) {
    /** Faturas de cartão em aberto, pelo vencimento (a conta a pagar que a importação cria). */
    fun openInvoices(): List<ContaEntity> {
        val ids = faturas.map { it.contaId }.toSet()
        return contas.filter { (it.id in ids || it.nome.startsWith("Fatura ")) && it.tipo == "a_pagar" && it.status != "pago" }
    }
}

/** Uma saída da compra simulada. */
data class ScheduledPayment(val number: Int, val of: Int, val date: LocalDate, val cents: Long)

/**
 * Uma compra hipotética com datas concretas — o que entra no fluxo central durante a simulação.
 * [asTransactions] dá lançamentos com id negativo que só existem na memória da simulação; para
 * gravar de verdade, depois de o usuário confirmar, existe [toRecords], separado.
 */
data class PurchaseScenario(
    val description: String,
    val method: PaymentMethod,
    val payments: List<ScheduledPayment>,
    /** O método foi deduzido (à vista sem forma dita = Pix/débito hoje), e a resposta diz isso. */
    val methodAssumed: Boolean = false,
) {
    val totalCents: Long get() = payments.sumOf { it.cents }
    val count: Int get() = payments.size
    val first: LocalDate get() = payments.first().date
    val last: LocalDate get() = payments.last().date

    /** "Cadeira: 4x de R$ 250 no cartão de crédito, 1ª em 10/10" */
    val label: String get() = buildString {
        append(description).append(": ")
        if (count == 1) {
            append("${brl(totalCents)} à vista (${method.label}) em ${dm(first)}")
        } else {
            val same = payments.all { it.cents == payments.last().cents }
            append(if (same) "${count}x de ${brl(payments.last().cents)}" else "${brl(totalCents)} em ${count}x")
            append(" (${method.label}), 1ª em ${dm(first)}, última em ${dm(last)}")
        }
    }

    /** Lançamentos só da simulação: id negativo, origem [SIMULATION_ORIGIN]. Nunca vão para o Room. */
    fun asTransactions(): List<TransacaoEntity> = payments.mapIndexed { i, p ->
        TransacaoEntity(
            id = -(i + 1L),
            data = p.date.toEpochMillis(),
            descricao = description,
            valorCentavos = p.cents,
            categoria = CATEGORY,
            contaOrigem = method.label,
            recorrente = false,
            tipo = TransactionType.Gasto.name,
            origem = SIMULATION_ORIGIN,
        )
    }

    /**
     * O que "Registrar compra" grava, só depois de o usuário confirmar: um gasto por saída, na data
     * em que o dinheiro sai (a mesma conta que a simulação fez). Cartão não cria fatura: cada parcela
     * vira um gasto no vencimento informado, como a Agenda já mostra parcelas de dívida.
     */
    fun toRecords(): List<TransacaoEntity> = payments.map { p ->
        TransacaoEntity(
            data = p.date.toEpochMillis(),
            descricao = if (count > 1) "$description · parcela ${p.number} de ${p.of}" else description,
            valorCentavos = p.cents,
            categoria = CATEGORY,
            contaOrigem = method.label,
            recorrente = false,
            tipo = TransactionType.Gasto.name,
            origem = "manual",
        )
    }

    companion object {
        const val SIMULATION_ORIGIN = "simulacao"
        const val CATEGORY = "Compras"

        /**
         * Monta o cenário a partir da intenção. Nulo se faltar algo de [PurchaseIntent.missing].
         * [payday] resolve "depois do salário"; sem ciclo cadastrado, é o dia 1 do mês seguinte.
         */
        fun from(intent: PurchaseIntent, today: LocalDate, payday: LocalDate?): PurchaseScenario? {
            if (intent.missing().isNotEmpty()) return null
            val n = intent.count ?: 1
            val method = intent.method ?: if (n > 1) PaymentMethod.Unspecified else PaymentMethod.Immediate
            val first = when {
                intent.firstDate != null -> intent.firstDate
                intent.waitForPayday -> payday ?: today.plusMonths(1).withDayOfMonth(1)
                else -> today
            }
            val payments = split(intent.amountCents ?: return null, n, intent.installmentCents, first)
            return PurchaseScenario(
                description = intent.description ?: "Compra",
                method = method,
                payments = payments,
                methodAssumed = intent.method == null && n == 1,
            )
        }

        /** À vista numa data, pelo [cents] (preço à vista quando o usuário informou um). */
        fun cash(description: String, cents: Long, date: LocalDate, method: PaymentMethod = PaymentMethod.Immediate) =
            PurchaseScenario(description, method, listOf(ScheduledPayment(1, 1, date, cents)))

        /**
         * Divide [total] em [n] saídas mensais a partir de [first] (dia 29–31 cai no último dia de
         * mês curto; começar no último dia segue o fim de mês, como os recorrentes). Parcela
         * informada vale para todas; senão a diferença de centavos vai na primeira.
         */
        fun split(total: Long, n: Int, installment: Long?, first: LocalDate): List<ScheduledPayment> {
            val each = installment ?: (total / n)
            return (0 until n).map { i ->
                val cents = if (installment == null && i == 0) total - each * (n - 1) else each
                ScheduledPayment(i + 1, n, monthlyOccurrence(first, YearMonth.from(first).plusMonths(i.toLong())), cents)
            }
        }

        private fun brl(cents: Long) = formatBrl0(cents / 100.0)
        private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }
}
