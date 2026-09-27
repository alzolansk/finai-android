package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Uma parcela de dívida projetada num mês da Agenda. */
data class DebtInstallment(
    val divida: DividaEntity,
    /** Número da parcela no contrato (ex.: 4 de 12); nulo quando o total não foi informado. */
    val numero: Int?,
    val total: Int?,
    /** Nulo em dívida antiga sem vencimento cadastrado — a Agenda não inventa um dia. */
    val vencimento: LocalDate?,
    val valorCentavos: Long,
    /** Só a parcela mais antiga em aberto pode ser marcada como paga (as outras vêm depois dela). */
    val isNext: Boolean,
    val atrasada: Boolean,
)

/**
 * Projeta as parcelas restantes de cada dívida nos meses em que vencem, a partir de
 * [DividaEntity.proximoVencimento] — uma por mês até acabar [DividaEntity.parcelasRestantes].
 * Vencimento no último dia do mês (30/09) segue o fim de mês (31/10, 30/11), como os
 * lançamentos recorrentes ([monthlyOccurrence]).
 * Nada é gravado por parcela: pagar uma avança o vencimento em um mês e decrementa o
 * restante ([afterPayment]), e a projeção inteira anda junto.
 *
 * Dívida sem vencimento (cadastrada antes da v6) fica ancorada no mês de [today]: continua
 * aparecendo no mês corrente como antes e agora também nos seguintes, sem dia definido.
 */
object DebtSchedule {

    fun installmentsInMonth(dividas: List<DividaEntity>, month: YearMonth, today: LocalDate): List<DebtInstallment> =
        dividas.mapNotNull { installmentInMonth(it, month, today) }
            .sortedWith(compareBy(nullsLast()) { it.vencimento })

    fun installmentInMonth(divida: DividaEntity, month: YearMonth, today: LocalDate): DebtInstallment? {
        if (divida.parcelasRestantes <= 0 || divida.valorParcelaCentavos <= 0) return null
        val anchor = divida.proximoVencimento?.toLocalDate()
        val anchorMonth = anchor?.let(YearMonth::from) ?: YearMonth.from(today)
        val offset = ChronoUnit.MONTHS.between(anchorMonth, month).toInt()
        if (offset < 0 || offset >= divida.parcelasRestantes) return null
        val vencimento = anchor?.let { monthlyOccurrence(it, month) }
        val total = divida.parcelasTotais.takeIf { it >= divida.parcelasRestantes && it > 0 }
        // A última parcela não passa do que ainda está em aberto (evita somar centavos a mais).
        val isLast = offset == divida.parcelasRestantes - 1
        val valor = if (isLast && divida.valorAbertoCentavos > 0) {
            val jaProjetado = divida.valorParcelaCentavos * offset
            (divida.valorAbertoCentavos - jaProjetado).coerceIn(1, divida.valorParcelaCentavos)
        } else divida.valorParcelaCentavos
        return DebtInstallment(
            divida = divida,
            numero = total?.let { it - divida.parcelasRestantes + offset + 1 },
            total = total,
            vencimento = vencimento,
            valorCentavos = valor,
            isNext = offset == 0,
            atrasada = vencimento != null && vencimento.isBefore(today),
        )
    }

    /** Mês da última parcela em aberto, ou nulo se não houver parcelas. */
    fun lastInstallmentMonth(divida: DividaEntity, today: LocalDate): YearMonth? {
        if (divida.parcelasRestantes <= 0) return null
        val anchorMonth = divida.proximoVencimento?.toLocalDate()?.let(YearMonth::from) ?: YearMonth.from(today)
        return anchorMonth.plusMonths((divida.parcelasRestantes - 1).toLong())
    }

    /** Categoria e origem do gasto que "Paguei a parcela" grava (FinanceViewModel.pagarParcela). */
    const val PAYMENT_CATEGORY = "Dívidas"
    const val PAYMENT_ORIGIN = "divida"

    /**
     * Nome da dívida gravado no gasto do pagamento ("Tablet · parcela 9 de 12",
     * "Tablet · quitação"). O gasto não guarda o id da dívida; o nome é o elo.
     */
    fun debtNameOf(payment: TransacaoEntity): String? =
        payment.takeIf { it.origem == PAYMENT_ORIGIN }?.descricao?.substringBeforeLast(" · ")?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Desfaz um pagamento registrado por engano (excluir o gasto na Agenda): a
     * parcela volta ao saldo e às restantes, e o vencimento volta um mês. Numa
     * quitação, o valor volta inteiro e as parcelas são recontadas pelo saldo.
     */
    fun undoPayment(divida: DividaEntity, payment: TransacaoEntity): DividaEntity {
        val aberto = divida.valorAbertoCentavos + payment.valorCentavos
        val paidOn = payment.data.toLocalDate()
        if (payment.descricao.endsWith("quitação")) {
            val restantes = if (divida.valorParcelaCentavos > 0) {
                ((aberto + divida.valorParcelaCentavos - 1) / divida.valorParcelaCentavos).toInt()
                    .let { if (divida.parcelasTotais > 0) it.coerceAtMost(divida.parcelasTotais) else it }
            } else 0
            return divida.copy(
                valorAbertoCentavos = aberto,
                parcelasRestantes = restantes,
                proximoVencimento = if (restantes > 0) paidOn.toEpochMillis() else null,
            )
        }
        val current = divida.proximoVencimento?.toLocalDate()
        // Sem próximo vencimento, a parcela desfeita era a última: volta a vencer no dia do pagamento.
        val previous = current?.let { monthlyOccurrence(it, YearMonth.from(it).minusMonths(1)) } ?: paidOn
        return divida.copy(
            valorAbertoCentavos = aberto,
            parcelasRestantes = divida.parcelasRestantes + 1,
            proximoVencimento = previous.toEpochMillis(),
        )
    }

    /** Estado da dívida depois de pagar a parcela mais antiga em aberto. */
    fun afterPayment(divida: DividaEntity, today: LocalDate): DividaEntity {
        if (divida.parcelasRestantes <= 0) return divida
        val anchor = divida.proximoVencimento?.toLocalDate()
        // Sem vencimento conhecido a parcela paga era a do mês corrente; a próxima cai no mês
        // seguinte, no mesmo dia de hoje — é o melhor palpite honesto até o usuário editar.
        val next = anchor?.let { monthlyOccurrence(it, YearMonth.from(it).plusMonths(1)) }
            ?: dueDateIn(YearMonth.from(today).plusMonths(1), today.dayOfMonth)
        val restantes = divida.parcelasRestantes - 1
        return divida.copy(
            parcelasRestantes = restantes,
            valorAbertoCentavos = (divida.valorAbertoCentavos - divida.valorParcelaCentavos).coerceAtLeast(0),
            proximoVencimento = if (restantes > 0) next.toEpochMillis() else null,
        )
    }

    /** Dia fixo; 29–31 num mês mais curto cai no último dia. Parcela ancorada no fim do mês usa [monthlyOccurrence]. */
    fun dueDateIn(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceAtMost(month.lengthOfMonth()))
}
