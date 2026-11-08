package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth

/** Parte de um salário recebido em duas vezes. */
enum class IncomePart(val key: String) {
    Advance("adiantamento"),
    Balance("saldo"),
}

/** Como o adiantamento é definido: porcentagem do líquido ou valor fixo. */
sealed interface AdvanceRule {
    data class Percent(val pct: Int) : AdvanceRule
    data class Fixed(val cents: Long) : AdvanceRule

    /** "pct:40" / "fixo:200000" — o que [TransacaoEntity.rendaDivisao] guarda. */
    fun encode(): String = when (this) {
        is Percent -> "pct:$pct"
        is Fixed -> "fixo:$cents"
    }

    companion object {
        fun decode(text: String?): AdvanceRule? {
            val value = text?.substringAfter(':', "")?.toLongOrNull() ?: return null
            return when (text.substringBefore(':')) {
                "pct" -> Percent(value.toInt())
                "fixo" -> Fixed(value)
                else -> null
            }
        }
    }
}

/**
 * Divisão do salário líquido em adiantamento (dia 20 do mês trabalhado) e saldo (dia 5 do mês
 * seguinte). Em centavos inteiros: o saldo é sempre o que sobra do adiantamento, então a soma
 * das duas partes é exatamente o salário, sem erro de arredondamento.
 */
data class IncomeSplit(
    val rule: AdvanceRule,
    val advanceDay: Int = ADVANCE_DAY,
    val balanceDay: Int = BALANCE_DAY,
) {
    /** Porcentagem arredondada para o centavo mais próximo (meio centavo sobe); fixo nunca passa do total. */
    fun advanceCents(totalCents: Long): Long {
        val raw = when (val r = rule) {
            is AdvanceRule.Percent -> (totalCents * r.pct.coerceIn(0, 100) + 50) / 100
            is AdvanceRule.Fixed -> r.cents
        }
        return raw.coerceIn(0, totalCents.coerceAtLeast(0))
    }

    fun balanceCents(totalCents: Long): Long = totalCents - advanceCents(totalCents)

    companion object {
        const val ADVANCE_DAY = 20
        const val BALANCE_DAY = 5
        const val MIN_PCT = 10
        const val MAX_PCT = 90
        const val PCT_STEP = 5
        const val MIN_FIXED_CENTS = 50_000L
        const val MAX_FIXED_CENTS = 500_000L
        const val FIXED_STEP_CENTS = 5_000L
    }
}

val TransacaoEntity.incomePart: IncomePart? get() = IncomePart.values().firstOrNull { it.key == rendaParte }

/** Salário dividido: as duas partes da mesma competência, ligadas por [TransacaoEntity.rendaGrupo]. */
val TransacaoEntity.isSplitIncome: Boolean get() = rendaGrupo != null && incomePart != null

/** Competência (mês trabalhado) de um recebimento que cai em [receiptMonth]. */
fun TransacaoEntity.competenceOf(receiptMonth: YearMonth): YearMonth = receiptMonth.plusMonths(competenciaDeslocamento.toLong())

/**
 * Receita por competência. O fluxo de caixa e o saldo da Agenda continuam pela data de
 * recebimento; relatório e fechamento mensal usam isto: o salário integral conta uma vez na
 * competência, e o adiantamento e o saldo de um mesmo mês trabalhado somam no mesmo mês.
 */
object IncomeCompetence {
    /** Receitas (projetadas, com exceções) cuja competência é [competence]. */
    fun receipts(transacoes: List<TransacaoEntity>, competence: YearMonth): List<TransacaoEntity> =
        transacoes.map { it.competenciaDeslocamento }.distinct().flatMap { offset ->
            transacoes
                .filter { it.competenciaDeslocamento == offset && it.faturaId == null && it.tipo == TransactionType.Receita.name }
                .transactionsInMonth(competence.minusMonths(offset.toLong()).atDay(1))
        }

    fun incomeCents(transacoes: List<TransacaoEntity>, competence: YearMonth): Long =
        receipts(transacoes, competence).sumOf { it.valorCentavos }
}

/** O que gravar para criar, dividir ou desfazer a divisão do salário. */
data class IncomePlan(
    val insert: List<TransacaoEntity> = emptyList(),
    val update: List<TransacaoEntity> = emptyList(),
    val delete: List<TransacaoEntity> = emptyList(),
)

/**
 * Gera e regenera as entradas do salário. Funções puras: devolvem o que gravar, o repositório
 * grava numa transação. Nenhuma ocorrência é gravada por mês: continuam sendo duas séries
 * recorrentes (adiantamento e saldo) ou uma (integral), projetadas por [occurrenceFor].
 *
 * Regenerar muda só o que ainda não foi efetivado: entrada com data até hoje fica como está. A
 * divisão nova vale a partir da primeira competência sem nenhum recebimento efetivado; se o
 * adiantamento da competência em curso já foi pago, o saldo pendente dela vira "salário −
 * adiantamento já pago" (o fechamento continua contando o salário uma vez só).
 */
object IncomePlanner {
    const val ADVANCE_NAME = "Adiantamento salarial"
    const val BALANCE_NAME = "Saldo do salário"

    /**
     * Entradas de um salário que começa na competência [startComp]. [template] dá descrição,
     * categoria e conta; [integralDay] é o dia do recebimento integral.
     */
    fun generate(
        template: TransacaoEntity, totalCents: Long, split: IncomeSplit?, startComp: YearMonth,
        integralDay: Int, group: String,
    ): List<TransacaoEntity> {
        require(totalCents > 0) { "O salário precisa ser maior que zero." }
        val base = template.copy(
            id = 0, valorCentavos = totalCents, tipo = TransactionType.Receita.name, recorrente = true, extra = false,
            faturaId = null, compraId = null, parcelaNumero = null, parcelasTotal = null, objetivoId = null,
            recorrenteAte = null, diaRecorrencia = null, excecoes = "", serieId = null,
            competenciaDeslocamento = 0, rendaGrupo = null, rendaParte = null, rendaDivisao = null,
        )
        if (split == null) {
            return listOf(base.copy(data = monthlyOccurrence(startComp.atDay(integralDay.coerceIn(1, 28)), startComp).toEpochMillis(), rendaPrincipal = true))
        }
        val adv = split.advanceCents(totalCents)
        val rest = totalCents - adv
        require(adv > 0 && rest > 0) { "As duas partes precisam ter valor." }
        val next = startComp.plusMonths(1)
        val common = base.copy(rendaGrupo = group, rendaDivisao = split.rule.encode())
        return listOf(
            common.copy(
                data = startComp.atDay(split.advanceDay.coerceAtMost(startComp.lengthOfMonth())).toEpochMillis(),
                descricao = ADVANCE_NAME, valorCentavos = adv, rendaParte = IncomePart.Advance.key,
                // O ciclo "até o próximo salário" começa no adiantamento; o saldo é entrada dentro dele.
                rendaPrincipal = true,
            ),
            common.copy(
                data = next.atDay(split.balanceDay.coerceAtMost(next.lengthOfMonth())).toEpochMillis(),
                descricao = BALANCE_NAME, valorCentavos = rest, rendaParte = IncomePart.Balance.key,
                competenciaDeslocamento = -1, rendaPrincipal = false,
            ),
        )
    }

    /** A renda de hoje: o que Minha renda mostra e edita. */
    data class Config(
        val totalCents: Long,
        val split: IncomeSplit?,
        val descricao: String,
        val conta: String,
        /** Primeira competência em que uma mudança de divisão passa a valer. */
        val nextCompetence: YearMonth,
        val template: TransacaoEntity,
    )

    /**
     * Séries do salário principal: as recorrentes marcadas como renda (ou reconhecidas pelo nome),
     * as duas partes de um salário dividido e os trechos anteriores de cada uma. Vazio sem renda
     * recorrente cadastrada.
     */
    fun familyOf(all: List<TransacaoEntity>): List<TransacaoEntity> {
        val seed = PayCycle.salariesOf(all).filter { it.isRecurringSeries }
        if (seed.isEmpty()) return emptyList()
        val groups = seed.mapNotNull { it.rendaGrupo }.toSet()
        val roots = seed.map { it.seriesRoot }.toSet()
        return all.filter { it.isRecurringSeries && it.tipo == TransactionType.Receita.name && (it.rendaGrupo in groups || it.seriesRoot in roots) }
    }

    fun current(all: List<TransacaoEntity>, today: LocalDate): Config? {
        val rows = familyOf(all)
        if (rows.isEmpty()) return null
        val live = rows.filter { it.seriesEnd == null || it.seriesEnd!! >= YearMonth.from(today) }.ifEmpty { rows }
        val advance = live.filter { it.incomePart == IncomePart.Advance }.maxByOrNull { it.startMonth }
        val balance = live.filter { it.incomePart == IncomePart.Balance }.maxByOrNull { it.startMonth }
        val template = advance ?: live.maxByOrNull { it.startMonth }!!
        val total = if (advance != null && balance != null) advance.valorCentavos + balance.valorCentavos else template.valorCentavos
        val split = advance?.takeIf { balance != null }?.let { a -> AdvanceRule.decode(a.rendaDivisao)?.let(::IncomeSplit) }
        val next = lastPaidCompetence(rows, today)?.plusMonths(1) ?: YearMonth.from(today)
        return Config(total, split, template.descricao, template.contaOrigem, next, template)
    }

    /** Última competência com recebimento já efetivado (data até [today]) nas séries; nulo se nenhum. */
    fun lastPaidCompetence(rows: List<TransacaoEntity>, today: LocalDate): YearMonth? =
        rows.flatMap { row -> occurrences(row, today).filter { !it.second.isAfter(today) }.map { row.competenceOf(it.first) } }.maxOrNull()

    /**
     * Troca a divisão do salário. [rows] são todas as séries atuais dele (uma integral, ou
     * adiantamento e saldo, com os trechos). [split] nulo = integral.
     */
    fun regenerate(rows: List<TransacaoEntity>, totalCents: Long, split: IncomeSplit?, today: LocalDate): IncomePlan {
        require(rows.isNotEmpty()) { "Nenhuma renda cadastrada." }
        val template = rows.maxByOrNull { it.startMonth }!!
        val lastPaid = lastPaidCompetence(rows, today)
        val startComp = lastPaid?.plusMonths(1) ?: rows.minOf { it.startMonth.plusMonths(it.competenciaDeslocamento.toLong()) }
        val group = rows.firstNotNullOfOrNull { it.rendaGrupo } ?: "renda-${template.seriesRoot.takeIf { it > 0 } ?: System.nanoTime()}"
        val integralDay = rows.firstOrNull { !it.isSplitIncome }?.recurrenceDay ?: IncomeSplit.BALANCE_DAY

        // Adiantamento da competência em curso já pago, com o saldo ainda por vir.
        val inFlight = lastPaid?.let { comp ->
            val adv = rows.filter { it.incomePart == IncomePart.Advance }.flatMap { r -> occurrences(r, today).filter { !it.second.isAfter(today) && r.competenceOf(it.first) == comp }.map { r.occurrenceFor(it.first) } }.firstOrNull()
            val saldo = rows.filter { it.incomePart == IncomePart.Balance }.firstOrNull { r -> occurrences(r, today).any { r.competenceOf(it.first) == comp && it.second.isAfter(today) } }
            if (adv != null && saldo != null) Triple(saldo, comp.minusMonths(saldo.competenciaDeslocamento.toLong()), adv.valorCentavos) else null
        }

        val update = mutableListOf<TransacaoEntity>()
        val delete = mutableListOf<TransacaoEntity>()
        rows.forEach { row ->
            val paid = occurrences(row, today).filter { !it.second.isAfter(today) }
            val flight = inFlight?.takeIf { it.first.id == row.id }
            when {
                flight != null -> {
                    val month = flight.second
                    val pending = (totalCents - flight.third).coerceAtLeast(1)
                    update += row.copy(
                        recorrenteAte = month.toString(),
                        excecoes = OccurrenceCodec.encode(
                            row.overrides.filter { it.mes < month } + OccurrenceOverride(month, valorCentavos = pending.takeIf { it != row.valorCentavos }),
                        ),
                    )
                }
                paid.isEmpty() -> delete += row
                else -> {
                    val last = paid.maxOf { it.first }
                    update += row.copy(recorrenteAte = last.toString(), excecoes = OccurrenceCodec.encode(row.overrides.filter { it.mes <= last }))
                }
            }
        }
        val insert = generate(template, totalCents, split, startComp, integralDay, group)
        return IncomePlan(insert = insert, update = update, delete = delete)
    }

    /** Meses em que a série tem ocorrência (começo ao fim, ou até dois meses depois de hoje se sem fim) e a data de cada uma. */
    private fun occurrences(row: TransacaoEntity, today: LocalDate): List<Pair<YearMonth, LocalDate>> {
        if (!row.isRecurringSeries) return listOf(row.startMonth to row.data.toLocalDate())
        val end = row.seriesEnd ?: maxOf(row.startMonth, YearMonth.from(today)).plusMonths(2)
        return generateSequence(row.startMonth) { it.plusMonths(1) }.takeWhile { it <= end }
            .mapNotNull { m -> row.occurrenceFor(m)?.let { m to it.data.toLocalDate() } }.toList()
    }
}
