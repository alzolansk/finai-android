package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

/**
 * Regras das telas de objetivo (registrar aporte, novo objetivo em 3 passos, editar). A UI só
 * desenha: os limites dos controles, o "cabe na sobra?", a cobertura da reserva em dias e a
 * lista "O que vai mudar" saem daqui, sem IA.
 */
object GoalFlow {
    val TYPES = listOf("Viagem", "Compra", "Reserva", "Outro")

    /** Faixa do valor da meta no passo 2 e na edição (linear, como a referência). */
    const val TARGET_MIN_CENTS = 500_00L
    const val TARGET_MAX_CENTS = 50_000_00L
    const val TARGET_STEP_CENTS = 100_00L
    val TARGET_PRESETS = listOf(2_000_00L, 5_000_00L, 10_000_00L, 20_000_00L)

    /** Prazo de 1 mês a 5 anos, em meses inteiros a partir de hoje. */
    const val MIN_MONTHS = 1
    const val MAX_MONTHS = 60

    /** Aporte: teto padrão do controle e atalhos. */
    const val CONTRIBUTION_MAX_CENTS = 5_000_00L
    const val CONTRIBUTION_STEP_CENTS = 10_00L
    const val CONTRIBUTION_BUTTON_STEP_CENTS = 50_00L
    const val DEFAULT_CONTRIBUTION_CENTS = 200_00L
    val CONTRIBUTION_PRESETS = listOf(100_00L, 250_00L, 500_00L, 1_000_00L)

    /** Saldo de reserva no controle da edição/criação: teto mínimo. */
    const val RESERVE_BALANCE_MAX_CENTS = 10_000_00L
    const val BALANCE_STEP_CENTS = 50_00L

    /** Aporte mensal planejado de reserva contínua. */
    const val PLANNED_MAX_CENTS = 5_000_00L
    const val PLANNED_STEP_CENTS = 50_00L

    fun suggestions(tipo: String): List<String> = when (tipo) {
        "Viagem" -> listOf("Passagem Itália", "Férias em família", "Intercâmbio")
        "Compra" -> listOf("Notebook novo", "Celular", "Carro")
        "Reserva" -> listOf("Reserva de emergência", "Reserva geral", "Imprevistos")
        else -> listOf("Curso", "Casamento", "Presente")
    }

    fun placeholder(tipo: String): String = "Ex.: " + suggestions(tipo).first()

    fun priorityIndex(prioridade: Int): Int = when {
        prioridade <= 1 -> 0
        prioridade == 2 -> 1
        else -> 2
    }

    val PRIORITY_LABELS = listOf("Alta", "Média", "Baixa")
    fun priorityLabel(prioridade: Int): String = PRIORITY_LABELS[priorityIndex(prioridade)]

    // ── valores dos controles ──────────────────────────────────────────

    /** Fração 0..1 → valor arredondado ao passo, dentro de [min, max]. */
    fun valueAt(fraction: Float, minCents: Long, maxCents: Long, stepCents: Long): Long {
        val raw = minCents + (maxCents - minCents) * fraction.coerceIn(0f, 1f).toDouble()
        val stepped = Math.round(raw / stepCents) * stepCents
        return stepped.coerceIn(minCents, maxCents)
    }

    fun fractionOf(cents: Long, minCents: Long, maxCents: Long): Float =
        if (maxCents <= minCents) 0f else ((cents - minCents).toDouble() / (maxCents - minCents)).toFloat().coerceIn(0f, 1f)

    fun roundUp(cents: Long, stepCents: Long): Long = if (cents <= 0) 0 else ((cents + stepCents - 1) / stepCents) * stepCents

    /** Teto do aporte: o que falta na meta com alvo; R$ 5.000 (ou o saldo, se maior) na reserva. */
    fun contributionMaxCents(objetivo: ObjetivoEntity): Long =
        if (objetivo.isOpenReserve) {
            maxOf(CONTRIBUTION_MAX_CENTS, roundUp(objetivo.valorGuardadoCentavos, 1_000_00L))
        } else {
            roundUp((objetivo.valorAlvoCentavos - objetivo.valorGuardadoCentavos).coerceAtLeast(CONTRIBUTION_STEP_CENTS), CONTRIBUTION_STEP_CENTS)
        }

    /** Valor inicial do aporte: o planejado/necessário por mês; sem isso, R$ 200. */
    fun defaultContributionCents(objetivo: ObjetivoEntity, today: LocalDate): Long {
        val monthly = if (objetivo.isOpenReserve) objetivo.aporteMensalCentavos
        else monthlyNeededCents(objetivo.valorAlvoCentavos, objetivo.valorGuardadoCentavos, monthsUntil(objetivo.prazo.toLocalDate(), today))
        val base = if (monthly > 0) roundUp(monthly, CONTRIBUTION_STEP_CENTS) else DEFAULT_CONTRIBUTION_CENTS
        return base.coerceIn(CONTRIBUTION_STEP_CENTS, contributionMaxCents(objetivo))
    }

    /** Teto do saldo: o alvo, na meta; R$ 10.000 (ou 1,5× o saldo) na reserva contínua. */
    fun balanceMaxCents(continua: Boolean, targetCents: Long, savedCents: Long): Long =
        if (continua) maxOf(RESERVE_BALANCE_MAX_CENTS, roundUp(savedCents * 3 / 2, 1_000_00L))
        else maxOf(targetCents, savedCents)

    // ── prazo ──────────────────────────────────────────────────────────

    /** Último dia do mês que fica [months] meses à frente. */
    fun deadlineFor(months: Int, today: LocalDate): LocalDate = YearMonth.from(today).plusMonths(months.toLong()).atEndOfMonth()

    /** Meses até o prazo, dentro da faixa do controle (prazo vencido vira 1). */
    fun monthsFor(prazo: LocalDate, today: LocalDate): Int =
        monthsUntil(prazo, today).toInt().coerceIn(MIN_MONTHS, MAX_MONTHS)

    /** "em 9 meses", "em 1 ano", "em 2 anos e 3 meses". */
    fun horizonLabel(months: Int): String {
        val years = months / 12
        val rest = months % 12
        val m = if (rest == 1) "1 mês" else "$rest meses"
        val y = if (years == 1) "1 ano" else "$years anos"
        return when {
            years == 0 -> "em $m"
            rest == 0 -> "em $y"
            else -> "em $y e $m"
        }
    }

    fun monthlyNeededCents(targetCents: Long, savedCents: Long, months: Long): Long {
        val missing = (targetCents - savedCents).coerceAtLeast(0)
        if (missing == 0L) return 0
        val m = months.coerceAtLeast(1)
        return (missing + m - 1) / m
    }

    // ── cabe na sobra? ─────────────────────────────────────────────────

    enum class FitLevel { Folgado, Apertado, NaoCabe, SemRenda }

    data class Fit(val level: FitLevel, val label: String, val sharePct: Int, val text: String) {
        /** Quanto da barra do cartão fica cheio. */
        val barFraction: Float get() = (sharePct / 100f).coerceIn(0f, 1f)
    }

    /**
     * O aporte mensal comparado com a sobra média projetada ([GoalsOverview.averageSurplusCents]).
     * Folgado até metade da sobra; Apertado quando cabe sozinho mas, somado ao que as outras metas
     * já pedem, passa da sobra (ou usa mais da metade); Não cabe quando sozinho passa da sobra.
     */
    fun fit(neededCents: Long, averageSurplusCents: Long?, otherGoalsMonthlyCents: Long): Fit {
        if (averageSurplusCents == null || averageSurplusCents <= 0) {
            return Fit(
                FitLevel.SemRenda, "Sem renda", 0,
                if (averageSurplusCents == null) "Cadastre sua renda para o assistente checar se cabe."
                else "Sua sobra média hoje não deixa espaço para aportes.",
            )
        }
        val pct = ((neededCents * 100.0) / averageSurplusCents).let { Math.round(it).toInt() }
        val surplus = formatBrl0(averageSurplusCents / 100.0)
        return when {
            neededCents > averageSurplusCents -> Fit(
                FitLevel.NaoCabe, "Não cabe", pct,
                "Pede ${formatBrl0((neededCents - averageSurplusCents) / 100.0)} a mais que sua sobra média de $surplus.",
            )
            neededCents + otherGoalsMonthlyCents > averageSurplusCents -> Fit(
                FitLevel.Apertado, "Apertado", pct,
                "Somada às outras metas, passa da sua sobra média de $surplus.",
            )
            pct > 50 -> Fit(FitLevel.Apertado, "Apertado", pct, "Usa $pct% da sua sobra média de $surplus.")
            else -> Fit(FitLevel.Folgado, "Folgado", pct, "Usa $pct% da sua sobra média de $surplus.")
        }
    }

    // ── reserva: cobertura em dias ─────────────────────────────────────

    /**
     * Gasto médio por dia: média das saídas do mês ([MonthCashFlow.toPayCents]) no mês atual e nos
     * dois anteriores, só os meses com alguma saída, ÷ 30. Nulo sem nenhuma saída lançada.
     */
    fun averageDailySpendingCents(
        contas: List<ContaEntity>,
        transacoes: List<TransacaoEntity>,
        dividas: List<DividaEntity>,
        today: LocalDate,
    ): Long? {
        val current = YearMonth.from(today)
        val totals = (0L..2L).map { MonthCashFlow.of(contas, transacoes, dividas, current.minusMonths(it), today).toPayCents }
            .filter { it > 0 }
        if (totals.isEmpty()) return null
        return (totals.sum() / totals.size / 30).takeIf { it > 0 }
    }

    fun coverageDays(savedCents: Long, dailySpendingCents: Long?): Int? =
        dailySpendingCents?.takeIf { it > 0 }?.let { (savedCents / it).toInt() }

    // ── textos do aporte ───────────────────────────────────────────────

    data class ContributionPreview(val title: String, val detail: String)

    fun preview(objetivo: ObjetivoEntity, amountCents: Long, dailySpendingCents: Long?): ContributionPreview {
        val after = objetivo.valorGuardadoCentavos + amountCents
        val afterText = formatBrl0(after / 100.0)
        if (objetivo.isOpenReserve) {
            val days = coverageDays(after, dailySpendingCents)
            return ContributionPreview(
                "${objetivo.nome} vai para $afterText",
                if (days != null) "Cobre cerca de $days dias dos seus gastos médios" else "Lance seus gastos para ver quantos dias ela cobre",
            )
        }
        val pct = percent(after, objetivo.valorAlvoCentavos)
        val missing = objetivo.valorAlvoCentavos - after
        return ContributionPreview(
            "${objetivo.nome} vai para $afterText",
            if (missing <= 0) "Meta alcançada com este aporte" else "$pct% da meta · faltam ${formatBrl0(missing / 100.0)}",
        )
    }

    fun percent(savedCents: Long, targetCents: Long): Int =
        if (targetCents <= 0) 0 else ((savedCents * 100) / targetCents).toInt().coerceAtLeast(0)

    /** Frase curta embaixo dos cartões de sucesso. */
    fun successMessage(objetivo: ObjetivoEntity, amountCents: Long, today: LocalDate): String {
        val before = objetivo.valorGuardadoCentavos
        val after = before + amountCents
        if (objetivo.isOpenReserve) {
            return if (before <= 0) "Sua reserva começou agora. Cada real aqui é tranquilidade para imprevistos."
            else "Sua reserva cresceu ${Math.round(amountCents * 100.0 / before)}%. Cada real aqui é tranquilidade para imprevistos."
        }
        val missing = objetivo.valorAlvoCentavos - after
        if (missing <= 0) return "Meta alcançada. O que entrar agora passa do alvo."
        val months = monthsUntil(objetivo.prazo.toLocalDate(), today)
        val monthly = monthlyNeededCents(objetivo.valorAlvoCentavos, after, months)
        return if (objetivo.prazo.toLocalDate().isBefore(today)) "Faltam ${formatBrl0(missing / 100.0)}. O prazo já passou; vale rever a data."
        else "Faltam ${formatBrl0(missing / 100.0)}. Com ${formatBrl0(monthly / 100.0)}/mês você chega até ${formatMonthYearLong(objetivo.prazo.toLocalDate())}."
    }

    // ── edição: o que vai mudar ────────────────────────────────────────

    data class Change(val label: String, val before: String?, val after: String)

    /** Campos alterados, na ordem da tela. [before] nulo = não havia valor para riscar. */
    fun changes(before: ObjetivoEntity, after: ObjetivoEntity): List<Change> {
        val out = mutableListOf<Change>()
        fun money(c: Long) = formatBrl0(c / 100.0)
        if (before.nome.trim() != after.nome.trim()) out += Change("Nome", before.nome, after.nome.trim())
        if (before.tipo != after.tipo) out += Change("Tipo", before.tipo, after.tipo)
        if (before.isOpenReserve != after.isOpenReserve) {
            out += Change("Reserva contínua", if (before.isOpenReserve) "Sim" else "Não", if (after.isOpenReserve) "Sim" else "Não")
        }
        if (after.isOpenReserve) {
            if (before.aporteMensalCentavos != after.aporteMensalCentavos) {
                out += Change(
                    "Aporte mensal planejado",
                    if (before.aporteMensalCentavos > 0) money(before.aporteMensalCentavos) + "/mês" else "Nenhum",
                    if (after.aporteMensalCentavos > 0) money(after.aporteMensalCentavos) + "/mês" else "Nenhum",
                )
            }
        } else {
            if (before.valorAlvoCentavos != after.valorAlvoCentavos) {
                out += Change("Valor da meta", before.valorAlvoCentavos.takeIf { it > 0 }?.let(::money), money(after.valorAlvoCentavos))
            }
            val pb = YearMonth.from(before.prazo.toLocalDate())
            val pa = YearMonth.from(after.prazo.toLocalDate())
            if (before.isOpenReserve || pb != pa) {
                out += Change(
                    "Prazo",
                    if (before.isOpenReserve) null else formatMonthYearLong(before.prazo.toLocalDate()),
                    formatMonthYearLong(after.prazo.toLocalDate()),
                )
            }
        }
        if (before.valorGuardadoCentavos != after.valorGuardadoCentavos) {
            out += Change("Saldo atual", money(before.valorGuardadoCentavos), money(after.valorGuardadoCentavos))
        }
        if (priorityIndex(before.prioridade) != priorityIndex(after.prioridade)) {
            out += Change("Prioridade", priorityLabel(before.prioridade), priorityLabel(after.prioridade))
        }
        if (before.descricao.trim() != after.descricao.trim()) {
            out += Change("Descrição", before.descricao.trim().ifEmpty { "Vazia" }, after.descricao.trim().ifEmpty { "Vazia" })
        }
        return out
    }
}
