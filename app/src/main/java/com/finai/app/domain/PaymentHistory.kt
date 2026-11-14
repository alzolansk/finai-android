package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Histórico de pagamentos para auditoria: faturas e contas marcadas pagas (ou recebidas) e parcelas
 * de dívida registradas com "Paguei a parcela". Só lê o que já está gravado: conta com `status =
 * "pago"` e o dia dela ([ContaEntity.pagoEm]); parcela pelo gasto que o pagamento grava
 * ([DebtSchedule.PAYMENT_ORIGIN]), com a data do dia em que foi registrado.
 */
data class PaymentRecord(
    val kind: Kind,
    val title: String,
    val cents: Long,
    /** Dia em que foi marcada paga. Nulo em conta paga antes da v18, quando o app não guardava o dia. */
    val paidOn: LocalDate?,
    /** Vencimento. Nulo na parcela: o gasto do pagamento não guarda o vencimento que quitou. */
    val dueOn: LocalDate?,
    /** "parcela 3 de 12". */
    val detail: String? = null,
    val conta: ContaEntity? = null,
    val transacao: TransacaoEntity? = null,
) {
    enum class Kind { Invoice, Bill, Received, Installment }

    /** Dia que ordena a lista: o do pagamento, ou o vencimento quando o dia não foi gravado. */
    val sortDate: LocalDate get() = paidOn ?: dueOn ?: LocalDate.MIN

    /** Positivo = pago antes do vencimento; negativo = depois. Nulo sem as duas datas. */
    val daysBeforeDue: Long? get() = if (paidOn != null && dueOn != null) ChronoUnit.DAYS.between(paidOn, dueOn) else null

    /** "pago 14/11 · vence 05/12 (21 dias antes)". */
    fun timing(): String {
        val verb = if (kind == Kind.Received) "recebida" else if (kind == Kind.Installment) "registrada" else "paga"
        val paid = paidOn?.let { "$verb ${dm(it)}" } ?: "$verb · dia não registrado"
        val due = dueOn?.let { " · vence ${dm(it)}" }.orEmpty()
        val gap = daysBeforeDue?.let { d ->
            when {
                d > 0 -> " (${plural(d, "dia")} antes)"
                d < 0 -> " (${plural(-d, "dia")} depois)"
                else -> " (no dia)"
            }
        }.orEmpty()
        return paid + due + gap
    }

    private fun plural(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"
    private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
}

/** Um mês do histórico, pelo dia do pagamento. */
data class PaymentMonth(val month: YearMonth?, val records: List<PaymentRecord>) {
    val paidCents: Long get() = records.filter { it.kind != PaymentRecord.Kind.Received }.sumOf { it.cents }
}

object PaymentHistory {
    /** [invoiceContaIds]: contas que são fatura de cartão (têm uma `faturas_cartao` apontando para elas). */
    fun build(contas: List<ContaEntity>, transacoes: List<TransacaoEntity>, invoiceContaIds: Set<Long>): List<PaymentRecord> {
        val bills = contas.filter { it.status == "pago" }.map { c ->
            PaymentRecord(
                kind = when {
                    c.tipo == "a_receber" -> PaymentRecord.Kind.Received
                    c.id in invoiceContaIds -> PaymentRecord.Kind.Invoice
                    else -> PaymentRecord.Kind.Bill
                },
                title = c.nome,
                cents = c.valorCentavos,
                paidOn = c.pagoEm?.toLocalDate(),
                dueOn = c.vencimento.toLocalDate(),
                conta = c,
            )
        }
        val installments = transacoes.filter { it.origem == DebtSchedule.PAYMENT_ORIGIN && it.tipo == TransactionType.Gasto.name }.map { t ->
            PaymentRecord(
                kind = PaymentRecord.Kind.Installment,
                title = DebtSchedule.debtNameOf(t) ?: t.descricao,
                cents = t.valorCentavos,
                paidOn = t.data.toLocalDate(),
                dueOn = null,
                detail = t.descricao.substringAfterLast(" · ", "").takeIf { it.isNotBlank() && it != t.descricao },
                transacao = t,
            )
        }
        return (bills + installments).sortedWith(
            compareByDescending<PaymentRecord> { it.sortDate }.thenByDescending { it.conta?.id ?: it.transacao?.id ?: 0L },
        )
    }

    /** Por mês do pagamento, do mais recente; contas sem o dia do pagamento ficam num grupo à parte no fim. */
    fun byMonth(records: List<PaymentRecord>): List<PaymentMonth> {
        val (dated, undated) = records.partition { it.paidOn != null }
        return dated.groupBy { YearMonth.from(it.paidOn!!) }.map { (m, rs) -> PaymentMonth(m, rs) } +
            listOfNotNull(undated.takeIf { it.isNotEmpty() }?.let { PaymentMonth(null, it) })
    }

    /**
     * O pagamento de parcela mais recente de cada dívida: só ele pode ser desfeito daqui, porque
     * desfazer volta o vencimento um mês ([DebtSchedule.undoPayment]) e só faz sentido na ordem inversa.
     */
    fun undoableInstallments(records: List<PaymentRecord>): Set<Long> =
        records.filter { it.kind == PaymentRecord.Kind.Installment && it.transacao != null }
            .groupBy { it.title }
            .mapNotNull { (_, rs) -> rs.maxWithOrNull(compareBy<PaymentRecord> { it.sortDate }.thenBy { it.transacao!!.id })?.transacao?.id }
            .toSet()
}
