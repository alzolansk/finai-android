package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * Compras parceladas no cartão, de duas origens:
 * - lançadas no app (`CardPurchase`): cada parcela já é um `TransacaoEntity` com `compraId`;
 * - vindas de fatura importada: só a parcela daquela fatura existe ("LOJA (01/03)", "PARC 2/6", lida por
 *   `InvoiceSummary.installmentOf`). Faturas importadas de meses diferentes da mesma compra viram uma compra
 *   só; parcelas anteriores à primeira fatura importada contam como pagas (já passaram), e as seguintes à
 *   última são **estimadas** com o mesmo valor, um mês depois da outra. Quando a fatura seguinte for
 *   importada, a parcela real ocupa o lugar da estimada, nunca as duas.
 *
 * Nada aqui grava nada. A parcela entra no mês do vencimento da fatura e conta como paga quando a conta
 * dessa fatura está paga, a mesma regra da Agenda.
 */
data class InstallmentPurchase(
    val compraId: String,
    val name: String,
    val card: String,
    val totalInstallments: Int,
    val totalCents: Long,
    /** Valor da próxima parcela em aberto (ou da primeira, se já está tudo pago). */
    val monthlyCents: Long,
    /** Números das parcelas pagas (1-based). */
    val paidNumbers: Set<Int>,
    /** Próxima parcela a pagar, a de menor número em aberto. */
    val nextNumber: Int?,
    val openCount: Int,
    val openCents: Long,
    val lastDue: YearMonth?,
    /** Veio de fatura importada e tem parcela futura estimada (ainda não está em nenhuma fatura). */
    val estimated: Boolean = false,
) {
    val paidCount get() = paidNumbers.size
    val active get() = openCount > 0
}

data class InstallmentMonth(val month: YearMonth, val cents: Long, val count: Int, val estimatedCount: Int = 0)

data class InstallmentsSummary(
    /** Compras com parcela em aberto, da que tem mais em aberto para a que tem menos. */
    val active: List<InstallmentPurchase>,
    val openCents: Long,
    val openCount: Int,
    /** Em aberto por mês de vencimento, sem buracos entre o primeiro e o último mês. */
    val openByMonth: List<InstallmentMonth>,
    /** Todas as parcelas (pagas ou não) por mês de vencimento: o que o mês da Agenda tem. */
    private val allByMonth: Map<YearMonth, List<Pair<String, Long>>>,
) {
    /** Primeiro mês sem parcela em aberto, depois da última. */
    val freeFrom: YearMonth? get() = openByMonth.lastOrNull()?.month?.plusMonths(1)

    val isEmpty get() = active.isEmpty() && allByMonth.isEmpty()

    fun monthCents(month: YearMonth): Long = allByMonth[month].orEmpty().sumOf { it.second }

    /** Compras distintas com parcela vencendo em [month]. */
    fun monthPurchases(month: YearMonth): Int = allByMonth[month].orEmpty().map { it.first }.distinct().size

    /**
     * Uma parcela. [recorded] = está numa fatura do app. Não gravada e paga = anterior à primeira fatura
     * importada; não gravada e em aberto = estimada.
     */
    private data class Slot(val number: Int, val cents: Long, val due: YearMonth, val paid: Boolean, val recorded: Boolean) {
        val estimated get() = !recorded && !paid
    }

    private data class Group(val id: String, val name: String, val card: String, val total: Int, val slots: List<Slot>)

    companion object {
        fun of(
            transacoes: List<TransacaoEntity>,
            faturas: List<FaturaCartaoEntity>,
            contas: List<ContaEntity>,
        ): InstallmentsSummary {
            val invoiceById = faturas.associateBy { it.id }
            val accountById = contas.associateBy { it.id }
            /** Vencimento e situação da fatura do item; sem fatura, o item não é parcela de cartão. */
            fun invoiceOf(t: TransacaoEntity): Triple<YearMonth, Boolean, String>? {
                val invoice = invoiceById[t.faturaId] ?: return null
                val account = accountById[invoice.contaId] ?: return null
                return Triple(YearMonth.from(invoice.vencimento.toLocalDate()), account.status == "pago", invoice.referencia)
            }

            val groups = mutableListOf<Group>()

            // 1. Lançadas no app: todas as parcelas já estão no banco.
            transacoes.filter { it.compraId != null && (it.parcelasTotal ?: 0) > 1 }
                .groupBy { requireNotNull(it.compraId) }
                .forEach { (id, list) ->
                    val slots = list.mapNotNull { t ->
                        val (due, paid, _) = invoiceOf(t) ?: return@mapNotNull null
                        Slot(t.parcelaNumero ?: 0, t.valorCentavos, due, paid, recorded = true)
                    }
                    if (slots.isEmpty()) return@forEach
                    val first = list.minBy { it.parcelaNumero ?: 0 }
                    groups += Group(id, first.descricao.substringBeforeLast(" · parcela ").ifBlank { first.categoria },
                        first.contaOrigem, first.parcelasTotal ?: slots.size, slots.sortedBy { it.number })
                }

            // 2. Fatura importada: "(02/03)" na descrição. A mesma compra aparece em faturas de meses
            // seguidos; a chave junta nome, cartão, quantidade de parcelas, mês da 1ª parcela e valor.
            data class Imported(val t: TransacaoEntity, val number: Int, val total: Int, val due: YearMonth, val paid: Boolean, val card: String)
            transacoes.filter { it.compraId == null && it.valorCentavos > 0 }
                .mapNotNull { t ->
                    val (number, total) = InvoiceSummary.installmentOf(t.descricao) ?: return@mapNotNull null
                    val (due, paid, card) = invoiceOf(t) ?: return@mapNotNull null
                    Imported(t, number, total, due, paid, card)
                }
                .groupBy { listOf(InvoiceSummary.cleanName(it.t.descricao).lowercase(), it.card.trim().lowercase(),
                    it.total, it.due.minusMonths((it.number - 1).toLong()), it.t.valorCentavos) }
                .forEach { (key, list) ->
                    val known = list.associateBy { it.number }
                    val firstKnown = known.keys.min()
                    val last = known.getValue(known.keys.max())
                    val start = last.due.minusMonths((last.number - 1).toLong())
                    val slots = (1..last.total).map { n ->
                        val k = known[n]
                        when {
                            k != null -> Slot(n, k.t.valorCentavos, k.due, k.paid, recorded = true)
                            // Antes da primeira fatura importada: já passou, conta como paga.
                            n < firstKnown -> Slot(n, last.t.valorCentavos, start.plusMonths((n - 1).toLong()), paid = true, recorded = false)
                            else -> Slot(n, last.t.valorCentavos, start.plusMonths((n - 1).toLong()), paid = false, recorded = false)
                        }
                    }
                    groups += Group("fatura:" + key.joinToString("|"), InvoiceSummary.cleanName(last.t.descricao).ifBlank { last.t.categoria },
                        last.card, last.total, slots)
                }

            val purchases = groups.map { g ->
                val open = g.slots.filter { !it.paid }
                InstallmentPurchase(
                    compraId = g.id,
                    name = g.name,
                    card = g.card,
                    totalInstallments = g.total,
                    totalCents = g.slots.sumOf { it.cents },
                    monthlyCents = (open.firstOrNull() ?: g.slots.first()).cents,
                    paidNumbers = g.slots.filter { it.paid }.map { it.number }.toSet(),
                    nextNumber = open.firstOrNull()?.number,
                    openCount = open.size,
                    openCents = open.sumOf { it.cents },
                    lastDue = g.slots.maxOfOrNull { it.due },
                    estimated = open.any { it.estimated },
                )
            }

            val allSlots = groups.flatMap { g -> g.slots.map { g.id to it } }
            // Parcela anterior à primeira fatura importada não está em fatura nenhuma do app: fica fora dos meses.
            val inMonths = allSlots.filter { (_, s) -> s.recorded || !s.paid }
            val openSlots = allSlots.filter { !it.second.paid }
            val openGrouped = openSlots.groupBy { it.second.due }
            val openByMonth = if (openGrouped.isEmpty()) emptyList() else {
                val first = openGrouped.keys.min()
                val last = openGrouped.keys.max()
                generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.map { month ->
                    val list = openGrouped[month].orEmpty().map { it.second }
                    InstallmentMonth(month, list.sumOf { it.cents }, list.size, list.count { it.estimated })
                }.toList()
            }

            return InstallmentsSummary(
                active = purchases.filter { it.active }.sortedWith(
                    compareByDescending<InstallmentPurchase> { it.openCents }.thenBy { it.name },
                ),
                openCents = openSlots.sumOf { it.second.cents },
                openCount = openSlots.size,
                openByMonth = openByMonth,
                allByMonth = inMonths.groupBy({ it.second.due }) { it.first to it.second.cents },
            )
        }

        /** Mês que o gráfico abre selecionado: o atual, se tiver parcela em aberto; senão o primeiro. */
        fun defaultMonth(months: List<InstallmentMonth>, today: LocalDate): YearMonth? =
            months.firstOrNull { it.month == YearMonth.from(today) && it.cents > 0 }?.month
                ?: months.firstOrNull { it.month >= YearMonth.from(today) && it.cents > 0 }?.month
                ?: months.firstOrNull { it.cents > 0 }?.month
    }
}
