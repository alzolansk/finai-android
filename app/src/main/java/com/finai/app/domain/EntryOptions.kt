package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Regras do lançamento (playbook `finai-gasto-playbook.pdf`), em Kotlin puro: forma de
 * pagamento, parcelas, vencimento do cartão, tipo de receita, destino da transferência, ordem
 * das categorias e textos curtos. A tela só desenha o que sai daqui.
 */
enum class PaymentMethod(val label: String) {
    /** Sai da conta na data escolhida. Único que permite "Todo mês". */
    Debito("Débito / Pix"),
    /** Uma vez no cartão: entra numa fatura. */
    Credito("Crédito"),
    /** De 2 a 24 parcelas, uma por fatura. */
    Parcelado("Parcelado");

    val isCard: Boolean get() = this != Debito
}

/** Tipo de entrada da receita. Um só; os indicadores do registro saem daqui. */
enum class IncomeKind(val label: String, val detail: String) {
    Salario("Salário", "Renda principal. Define o ciclo até o próximo pagamento."),
    Recorrente("Recorrente", "Repete todo mês, fora o salário."),
    Avulsa("Avulsa", "Entra só uma vez."),
    Extra("Extra", "13º, bônus, restituição. Fica fora da renda do mês.");

    /** Salário = renda principal e recorrente; Recorrente = recorrente; Extra = extra; Avulsa = nada. */
    fun applyTo(entry: TransacaoEntity): TransacaoEntity = entry.copy(
        categoria = label,
        rendaPrincipal = this == Salario,
        recorrente = this == Salario || this == Recorrente,
        extra = this == Extra,
    )

    companion object {
        val DEFAULT = Avulsa
        fun of(t: TransacaoEntity): IncomeKind = when {
            t.rendaPrincipal -> Salario
            t.extra -> Extra
            t.recorrente -> Recorrente
            else -> Avulsa
        }
    }
}

/** Para onde vai uma transferência. */
enum class TransferTarget(val label: String) { Reserva("Reserva"), Objetivo("Objetivo"), Conta("Outra conta") }

object EntryOptions {
    const val MIN_INSTALLMENTS = 2
    const val MAX_INSTALLMENTS = 24
    const val RESERVE_KIND = "Reserva"
    const val FALLBACK_ACCOUNT = "Carteira"

    // ── categorias e contas ─────────────────────────────────────────

    /**
     * As nove categorias, as usadas por último primeiro (o maior id é o lançamento mais
     * recente). As que nunca foram usadas mantêm a ordem padrão, depois das usadas.
     */
    fun categoriesByRecentUse(transacoes: List<TransacaoEntity>): List<String> {
        val lastUse = transacoes
            .filter { it.tipo == TransactionType.Gasto.name && it.categoria in TransactionEntry.categories }
            .groupBy { it.categoria }
            .mapValues { (_, list) -> list.maxOf { it.id } }
        return TransactionEntry.categories.sortedByDescending { lastUse[it] ?: Long.MIN_VALUE }
    }

    /** Cartões conhecidos: referência das faturas e cartão das compras parceladas, os recentes primeiro. */
    fun knownCards(faturas: List<FaturaCartaoEntity>, transacoes: List<TransacaoEntity>): List<String> {
        val fromPurchases = transacoes.filter { it.compraId != null || it.faturaId != null }
            .sortedByDescending { it.id }.map { it.contaOrigem.trim() }
        val fromInvoices = faturas.sortedByDescending { it.id }.map { it.referencia.trim() }
        return (fromPurchases + fromInvoices).filter { it.isNotBlank() }.distinctBy { it.lowercase() }
    }

    /**
     * Contas de débito/Pix já usadas, a mais recente primeiro, sem os cartões; Carteira no fim
     * quando não aparece. É o que já vem preenchido na pílula de conta.
     */
    fun recentAccounts(transacoes: List<TransacaoEntity>, cards: Collection<String>, extra: Collection<String> = emptyList()): List<String> {
        val cardKeys = cards.map { it.trim().lowercase() }.toSet()
        val used = transacoes
            .filter { it.tipo != TransactionType.Receita.name && it.faturaId == null && it.compraId == null }
            .sortedByDescending { it.id }
            .map { it.contaOrigem.trim() }
        return (used + extra.map { it.trim() } + FALLBACK_ACCOUNT)
            .filter { it.isNotBlank() && it.lowercase() !in cardKeys }
            .distinctBy { it.lowercase() }
    }

    /** De onde vieram as receitas, a mais recente primeiro. */
    fun recentIncomeSources(transacoes: List<TransacaoEntity>): List<String> =
        transacoes.filter { it.tipo == TransactionType.Receita.name }
            .sortedByDescending { it.id }.map { it.contaOrigem.trim() }
            .filter { it.isNotBlank() }.distinctBy { it.lowercase() }

    // ── parcelas ────────────────────────────────────────────────────

    /** Divisão em centavos inteiros: a sobra vai para as primeiras, como [CardPurchase.schedule]. */
    data class Installments(val count: Int, val totalCents: Long) {
        val baseCents: Long get() = totalCents / count
        val remainder: Int get() = (totalCents % count).toInt()
        val amounts: List<Long> get() = List(count) { baseCents + if (it < remainder) 1 else 0 }
        val valid: Boolean get() = count >= 1 && totalCents >= count

        /** "10× R$ 289,90". Com sobra, o valor comum; a sobra aparece em [remainderNote]. */
        val headline: String get() = "$count× ${brl2(baseCents)}"
        val remainderNote: String? get() = when (remainder) {
            0 -> null
            1 -> "1ª com ${brl2(baseCents + 1)}"
            else -> "as $remainder primeiras com ${brl2(baseCents + 1)}"
        }

        /** Leitura do controle de parcelas: "10 parcelas de R$ 289,90". */
        val spoken: String get() = "$count parcelas de ${brl2(baseCents)}" +
            (remainderNote?.let { ", $it" } ?: "")
    }

    // ── fatura do cartão ────────────────────────────────────────────

    /**
     * Vencimento da fatura em que a compra entra. [confident] só quando os dados guardados
     * mostram a fatura com segurança: uma fatura em aberto deste cartão, com fechamento
     * informado, e a compra entre o fechamento anterior (projetado um mês antes, com folga) e
     * o fechamento dela. Fora disso, [date] é só sugestão e a tela pede confirmação.
     */
    data class CardDue(val date: LocalDate?, val confident: Boolean)

    private const val CLOSING_MARGIN_DAYS = 3L

    fun resolveCardDue(
        card: String,
        purchase: LocalDate,
        faturas: List<FaturaCartaoEntity>,
        contas: List<ContaEntity>,
    ): CardDue {
        val key = card.trim()
        if (key.isEmpty()) return CardDue(null, false)
        val mine = faturas.filter { it.referencia.trim().equals(key, ignoreCase = true) }
        if (mine.isEmpty()) return CardDue(null, false)
        val paidDues = mine.filter { f -> contas.firstOrNull { it.id == f.contaId }?.status == "pago" }
            .map { it.vencimento.toLocalDate() }.toSet()

        val sure = mine.filter { f ->
            val close = f.fechamento?.toLocalDate() ?: return@filter false
            val due = f.vencimento.toLocalDate()
            val previousClose = close.minusMonths(1).plusDays(CLOSING_MARGIN_DAYS)
            due !in paidDues && !due.isBefore(purchase) && !purchase.isAfter(close) && purchase.isAfter(previousClose)
        }.minByOrNull { it.vencimento }
        if (sure != null) return CardDue(sure.vencimento.toLocalDate(), true)

        // Sugestão pela última fatura conhecida: mesmo dia de vencimento, no mês certo.
        val latest = mine.maxByOrNull { it.vencimento }!!
        val due = latest.vencimento.toLocalDate()
        val close = latest.fechamento?.toLocalDate()
        // Com fechamento: a primeira fatura cujo fechamento (mesmo dia, mês a mês) alcança a compra.
        // Sem fechamento: o primeiro vencimento depois da compra.
        var months = if (close != null) {
            var k = ChronoUnit.MONTHS.between(YearMonth.from(close), YearMonth.from(purchase)) - 1
            while (sameDayIn(close, k).isBefore(purchase)) k++
            k
        } else {
            var k = ChronoUnit.MONTHS.between(YearMonth.from(due), YearMonth.from(purchase)) - 1
            while (!sameDayIn(due, k).isAfter(purchase)) k++
            k
        }
        var candidate = sameDayIn(due, months)
        while (candidate.isBefore(purchase) || candidate in paidDues) { months++; candidate = sameDayIn(due, months) }
        return CardDue(candidate, false)
    }

    private fun sameDayIn(due: LocalDate, months: Long): LocalDate {
        val m = YearMonth.from(due).plusMonths(months)
        return m.atDay(due.dayOfMonth.coerceAtMost(m.lengthOfMonth()))
    }

    /**
     * Linha do cartão: "Entra na fatura Nubank de nov · vence 10/11" no crédito e
     * "1ª parcela na fatura Nubank de nov (vence 10/11) · última em jul/27" no parcelado.
     */
    fun invoiceLine(card: String, due: LocalDate?, confirmed: Boolean, installments: Int): String {
        val name = card.trim()
        if (name.isEmpty()) return "Escolha o cartão"
        if (due == null) return "Informe o vencimento da fatura $name"
        if (!confirmed) return "Confirme o vencimento da fatura $name: ${dayMonth(due)}?"
        val month = MONTH_ABBREV_PT[due.monthValue - 1].lowercase()
        if (installments <= 1) return "Entra na fatura $name de $month · vence ${dayMonth(due)}"
        val last = due.plusMonths((installments - 1).toLong())
        return "1ª parcela na fatura $name de $month (vence ${dayMonth(due)}) · última em " +
            "${MONTH_ABBREV_PT[last.monthValue - 1].lowercase()}/${last.year % 100}"
    }

    // ── transferência e reserva ─────────────────────────────────────

    /**
     * Destino "Reserva": objetivos do tipo Reserva (com alvo ou contínuos) e qualquer reserva
     * contínua. Reserva com alvo já concluída sai da lista; a contínua nunca conclui.
     */
    fun reserves(objetivos: List<ObjetivoEntity>): List<ObjetivoEntity> =
        objetivos.filter { (it.tipo == RESERVE_KIND || it.isOpenReserve) && (it.isOpenReserve || it.concluidoEm == null) }
            .sortedWith(compareBy({ it.prioridade }, { it.id }))

    /** Destino "Objetivo": metas com alvo, em andamento, que não são reserva. */
    fun goals(objetivos: List<ObjetivoEntity>): List<ObjetivoEntity> =
        objetivos.filter { it.tipo != RESERVE_KIND && !it.isOpenReserve && it.concluidoEm == null }
            .sortedWith(compareBy({ it.prioridade }, { it.id }))

    /** Uma opção só: já vem escolhida. Nenhuma ou várias: o usuário escolhe. */
    fun autoSelected(options: List<ObjetivoEntity>): Long? = options.singleOrNull()?.id

    /**
     * Prévia do aporte. Com alvo: percentual antes → depois e quanto falta. Reserva contínua:
     * saldo antes → depois e cobertura em dias pelo gasto médio — sem percentual inventado.
     */
    data class GoalMove(
        val name: String,
        val beforeCents: Long,
        val afterCents: Long,
        val targetCents: Long,
        val coverageBeforeDays: Int?,
        val coverageAfterDays: Int?,
    ) {
        val hasTarget: Boolean get() = targetCents > 0
        val beforePercent: Int get() = GoalFlow.percent(beforeCents, targetCents)
        val afterPercent: Int get() = GoalFlow.percent(afterCents, targetCents)
        val missingCents: Long get() = (targetCents - afterCents).coerceAtLeast(0)
        val beforeFraction: Float get() = if (hasTarget) (beforeCents.toFloat() / targetCents).coerceIn(0f, 1f) else 0f
        val afterFraction: Float get() = if (hasTarget) (afterCents.toFloat() / targetCents).coerceIn(0f, 1f) else 0f

        /** "62% → 64%" ou "R$ 1.000,00 → R$ 1.500,00". */
        val changeLabel: String get() = if (hasTarget) "$beforePercent% → $afterPercent%" else "${brl2(beforeCents)} → ${brl2(afterCents)}"

        /** "R$ 12.900,00 de R$ 20.000,00" ou "cobre 12 → 18 dias". */
        val detailLabel: String get() = when {
            hasTarget -> "${brl2(afterCents)} de ${brl2(targetCents)}"
            coverageAfterDays != null -> "Cobre ${coverageBeforeDays ?: 0} → $coverageAfterDays dias de gastos"
            else -> "Saldo ${brl2(afterCents)}"
        }

        /** Depois de guardar: "faltam R$ 7.100,00" ou a cobertura; nunca falta inventada na contínua. */
        val afterNote: String get() = when {
            hasTarget && missingCents == 0L -> "Meta alcançada"
            hasTarget -> "Faltam ${brl2(missingCents)}"
            coverageAfterDays != null -> "Cobre cerca de $coverageAfterDays dias de gastos"
            else -> "Lance seus gastos para ver quantos dias ela cobre"
        }

        /** O que a região "live" anuncia, só no fim: "Reserva de emergência agora com R$ 12.900". */
        val announcement: String get() = "$name agora com ${formatBrl0Cents(afterCents)}"
    }

    fun goalMove(objetivo: ObjetivoEntity, amountCents: Long, dailySpendingCents: Long?): GoalMove {
        val before = objetivo.valorGuardadoCentavos
        val after = before + amountCents.coerceAtLeast(0)
        val target = if (objetivo.isOpenReserve) 0 else objetivo.valorAlvoCentavos
        return GoalMove(
            objetivo.nome, before, after, target,
            GoalFlow.coverageDays(before, dailySpendingCents).takeIf { target == 0L },
            GoalFlow.coverageDays(after, dailySpendingCents).takeIf { target == 0L },
        )
    }

    // ── textos ──────────────────────────────────────────────────────

    /** "hoje", "ontem" ou "12/10". */
    fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        else -> dayMonth(date)
    }

    /** Resumo da confirmação, em uma linha: valor, categoria, data, conta. */
    fun summaryLine(valueCents: Long, category: String, date: LocalDate, account: String, today: LocalDate): String =
        listOf(brl2(valueCents), category, dateLabel(date, today).lowercase(), account.trim())
            .filter { it.isNotBlank() }.joinToString(" · ")

    fun dayMonth(d: LocalDate): String = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

    // ── valor por extenso (TalkBack) ────────────────────────────────

    private val UNITS = listOf(
        "zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez",
        "onze", "doze", "treze", "catorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove",
    )
    private val TENS = listOf("", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa")
    private val HUNDREDS = listOf(
        "", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos", "seiscentos", "setecentos", "oitocentos", "novecentos",
    )

    private fun below1000(n: Int): String {
        if (n == 100) return "cem"
        val parts = mutableListOf<String>()
        if (n >= 100) parts += HUNDREDS[n / 100]
        val rest = n % 100
        when {
            rest == 0 -> {}
            rest < 20 -> parts += UNITS[rest]
            else -> { parts += TENS[rest / 10]; if (rest % 10 != 0) parts += UNITS[rest % 10] }
        }
        return parts.joinToString(" e ")
    }

    /** Inteiro por extenso, até 999.999.999. */
    fun numberInWords(value: Long): String {
        require(value in 0..999_999_999)
        if (value == 0L) return "zero"
        val millions = (value / 1_000_000).toInt()
        val thousands = ((value / 1000) % 1000).toInt()
        val units = (value % 1000).toInt()
        val groups = buildList {
            if (millions > 0) add(if (millions == 1) "um milhão" else "${below1000(millions)} milhões")
            if (thousands > 0) add(if (thousands == 1) "mil" else "${below1000(thousands)} mil")
            if (units > 0) add(below1000(units))
        }
        // "e" antes do último grupo quando ele é menor que 100 ou centena redonda: "mil e cem",
        // "dois mil e cinco"; senão só espaço: "dois mil oitocentos e noventa e nove".
        val lastValue = if (units > 0) units else thousands * 1000
        return if (groups.size > 1 && (lastValue % 1000 < 100 || lastValue % 100 == 0)) {
            groups.dropLast(1).joinToString(" ") + " e " + groups.last()
        } else groups.joinToString(" ")
    }

    /** "Dois mil oitocentos e noventa e nove reais", "Quarenta e três reais e cinquenta centavos". */
    fun spokenAmount(cents: Long): String {
        val reais = cents / 100
        val centavos = cents % 100
        val reaisText = when {
            reais == 0L -> null
            reais == 1L -> "um real"
            reais % 1_000_000 == 0L -> "${numberInWords(reais)} de reais"
            else -> "${numberInWords(reais)} reais"
        }
        val centsText = when (centavos) {
            0L -> null
            1L -> "um centavo"
            else -> "${numberInWords(centavos)} centavos"
        }
        val text = listOfNotNull(reaisText, centsText).joinToString(" e ").ifEmpty { "zero reais" }
        return text.replaceFirstChar { it.uppercase() }
    }

    private fun formatBrl0Cents(cents: Long): String = com.finai.app.util.formatBrl0(cents / 100.0)
}

/** "R$ 289,90", em centavos inteiros (sem passar por Double na conta). */
internal fun brl2(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val reais = abs / 100
    val grouped = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "${sign}R$ $grouped,${"%02d".format(abs % 100)}"
}

/**
 * O que uma gravação do lançamento criou, para "Desfazer" reverter exatamente isso, numa
 * transação só, e para a confirmação mostrar os valores lidos do banco depois de gravar.
 */
data class EntryReceipt(
    val type: TransactionType,
    val transactionIds: List<Long>,
    /** Dia que a Agenda deve abrir: data do lançamento, ou vencimento da 1ª fatura no cartão. */
    val agendaDate: LocalDate,
    /** Faturas criadas por esta compra (somem no desfazer) e já existentes que receberam itens. */
    val createdInvoiceIds: List<Long> = emptyList(),
    val touchedInvoiceIds: List<Long> = emptyList(),
    /** Conta da Agenda que representa a 1ª fatura, para destacar. */
    val invoiceContaId: Long? = null,
    val goal: GoalReceipt? = null,
)

/** Aporte gravado: objetivo antes e depois, como ficou no Room. */
data class GoalReceipt(
    val goalId: Long,
    val name: String,
    val beforeCents: Long,
    val afterCents: Long,
    val targetCents: Long,
    val celebrate: Boolean,
)

/**
 * O que a Agenda destaca depois de "Ver na agenda": o lançamento salvo ou, numa compra no
 * cartão, a fatura em que a 1ª parcela entrou (os itens moram dentro dela).
 */
data class AgendaHighlight(val transactionIds: Set<Long>, val contaId: Long?) {
    /** Chaves das linhas da Agenda (`AgendaEntry.key`). */
    val keys: Set<String>
        get() = transactionIds.flatMap { listOf("transacao-$it", "transacao-rec-$it") }.toSet() +
            listOfNotNull(contaId?.let { "conta-$it" })

    companion object {
        fun of(receipt: EntryReceipt): AgendaHighlight =
            if (receipt.invoiceContaId != null) AgendaHighlight(emptySet(), receipt.invoiceContaId)
            else AgendaHighlight(receipt.transactionIds.toSet(), null)
    }
}

/** Um aporte no histórico do objetivo: a transferência que o gravou. */
data class ContributionRecord(val transactionId: Long, val date: LocalDate, val cents: Long, val origin: String) {
    /** "Transferência de Itaú". */
    val originLabel: String get() = if (origin.isBlank()) "Transferência" else "Transferência de $origin"

    companion object {
        /** Aportes por objetivo, do mais recente ao mais antigo. */
        fun byGoal(transacoes: List<TransacaoEntity>): Map<Long, List<ContributionRecord>> =
            transacoes.filter { it.isGoalContribution }
                .sortedWith(compareByDescending<TransacaoEntity> { it.data }.thenByDescending { it.id })
                .groupBy({ it.objetivoId!! }) { ContributionRecord(it.id, it.data.toLocalDate(), it.valorCentavos, it.contaOrigem.trim()) }
    }
}
