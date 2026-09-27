package com.finai.app.domain

import androidx.compose.ui.graphics.Color
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Bill
import com.finai.app.data.model.BillStatus
import com.finai.app.data.model.Budget
import com.finai.app.data.model.Debt
import com.finai.app.data.model.Goal
import com.finai.app.data.model.GoalBadge
import com.finai.app.data.model.StatusTone
import com.finai.app.data.model.Subscription
import com.finai.app.data.model.TimelineEntry
import com.finai.app.data.model.TimelineTone
import com.finai.app.data.model.WeekBill
import com.finai.app.util.formatBrl0
import java.time.LocalDate

/**
 * Converts domain calculation results (plain numbers) into the existing
 * UI-shaped models the Compose screens already render (planning.md §5's
 * MVVM boundary: calculators + mappers do the thinking, screens stay dumb).
 */

private val Green = Color(0xFF059669)
private val GreenFill = Color(0xFF10B981)
private val Amber = Color(0xFFB45309)
private val AmberFill = Color(0xFFF59E0B)
private val Red = Color(0xFFE11D48)
private val RedFill = Color(0xFFF43F5E)

private fun toneColors(over: Boolean, warn: Boolean): Pair<Color, Color> = when {
    over -> Red to RedFill
    warn -> Amber to AmberFill
    else -> Green to GreenFill
}

fun GoalPlan.toUiGoal(): Goal {
    val badge = when (status) {
        GoalStatus.OnTrack -> GoalBadge.OnTrack
        GoalStatus.Reassess -> GoalBadge.Reassess
        GoalStatus.Priority -> GoalBadge.Priority
    }
    val note = when (status) {
        GoalStatus.OnTrack -> "${formatBrl0(centsToReais(monthlyContributionNeededCents))}/mês mantém a data."
        GoalStatus.Reassess -> "Concorre com outros objetivos pela mesma capacidade de poupança."
        GoalStatus.Priority -> "Sem capacidade de poupança disponível este mês para este objetivo."
    }
    return Goal(
        id = objetivo.id.toString(),
        kind = objetivo.tipo,
        name = objetivo.nome,
        saved = centsToReais(objetivo.valorGuardadoCentavos),
        target = centsToReais(objetivo.valorAlvoCentavos),
        eta = etaLabel,
        badge = badge,
        note = note,
        action = "Registrar aporte",
    )
}

fun DebtPlan.toUiDebt(): Debt {
    val (rateColor, barColor) = toneColors(
        over = divida.taxaJurosMensalBasisPoints >= 800,
        warn = divida.taxaJurosMensalBasisPoints >= 200,
    )
    val hasParcelaFixa = divida.parcelasRestantes > 0 && divida.valorParcelaCentavos > 0
    val meta = if (hasParcelaFixa) {
        val restantes = if (divida.parcelasTotais >= divida.parcelasRestantes && divida.parcelasTotais > 0)
            "${divida.parcelasRestantes} de ${divida.parcelasTotais} parcelas restantes"
        else "${divida.parcelasRestantes} parcela(s) restante(s)"
        val fim = DebtSchedule.lastInstallmentMonth(divida, java.time.LocalDate.now())
            ?.let { " · até ${MONTH_NAMES_PT[it.monthValue - 1].take(3).lowercase()}/${it.year % 100}" }.orEmpty()
        "$restantes · ${formatBrl0(centsToReais(divida.valorParcelaCentavos))}/mês$fim"
    } else {
        "Sem parcela fixa definida"
    }
    return Debt(
        id = divida.id,
        rank = rank,
        name = divida.nome,
        meta = meta,
        amount = formatBrl0(centsToReais(divida.valorAbertoCentavos)),
        rate = DebtCalculator.rateLabel(divida.taxaJurosMensalBasisPoints),
        rateColor = rateColor,
        progressPct = progress,
        barColor = barColor,
        hasParcelaFixa = hasParcelaFixa,
    )
}

fun BudgetProgress.toUiBudget(): Budget {
    val over = tone == BudgetTone.Over
    val warn = tone == BudgetTone.Warn
    val (valueColor, barColor) = toneColors(over, warn)
    val note = when {
        limitCents <= 0 -> "Defina um limite para acompanhar esta categoria."
        over -> "Estourado em ${formatBrl0(centsToReais(spentCents - limitCents))}."
        warn -> "No ritmo atual fecha ${formatBrl0(centsToReais((projectedEndOfMonthCents ?: 0) - limitCents))} acima."
        else -> "Folga de ${formatBrl0(centsToReais(limitCents - spentCents))}."
    }
    return Budget(
        name = categoria,
        spent = centsToReais(spentCents),
        limit = if (limitCents <= 0) 1.0 else centsToReais(limitCents), // avoid /0 in Budget.progressPct
        note = note,
        valueColor = valueColor,
        barColor = barColor,
    )
}

private val palette = listOf(
    Color(0xFFEEF2FF) to Color(0xFF4F46E5),
    Color(0xFFF4F4F5) to Color(0xFF3F3F46),
    Color(0xFFFFF1F2) to Color(0xFFE11D48),
    Color(0xFFECFDF5) to Color(0xFF059669),
    Color(0xFFF5F3FF) to Color(0xFF7C3AED),
    Color(0xFFFFFBEB) to Color(0xFFB45309),
)

/** Cor de fundo/tinta do avatar de um item, estável pelo nome (mesma paleta das contas). */
fun avatarColorsOf(nome: String): Pair<Color, Color> = palette[(nome.hashCode() and Int.MAX_VALUE) % palette.size]

fun initialsOf(nome: String): String =
    nome.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifEmpty { "?" }

fun ContaEntity.toUiBill(today: LocalDate = LocalDate.now()): Bill {
    val status = when (BillStatusCalculator.of(this, today)) {
        EffectiveBillStatus.PAGO -> BillStatus.Paid
        EffectiveBillStatus.ATRASADO -> BillStatus.Overdue
        EffectiveBillStatus.VENCE_HOJE -> BillStatus.DueToday
        EffectiveBillStatus.PENDENTE -> BillStatus.Pending
        EffectiveBillStatus.PREVISTO -> BillStatus.Expected
    }
    val (tint, ink) = avatarColorsOf(nome)
    val amountLabel = formatBrl0(centsToReais(valorCentavos)).let { if (tipo == "a_receber") "+ $it" else it }
    val kindLabel = when {
        tipo == "a_receber" && recorrente -> "Entrada fixa"
        tipo == "a_receber" -> "Entrada"
        recorrente -> "Fixo"
        else -> "Avulso"
    }
    return Bill(
        id = id,
        name = nome,
        meta = "$kindLabel · " + formatDayMonth(vencimento),
        amount = amountLabel,
        status = status,
        initials = initialsOf(nome),
        tint = tint,
        ink = ink,
        date = vencimento.toLocalDate(),
        recorrente = recorrente,
        aReceber = tipo == "a_receber",
        valorCentavos = valorCentavos,
    )
}

fun ContaEntity.toUiWeekBill(today: LocalDate = LocalDate.now()): WeekBill {
    val date = vencimento.toLocalDate()
    val (label, tone) = when (BillStatusCalculator.of(this, today)) {
        EffectiveBillStatus.PREVISTO -> "Previsto" to StatusTone.Positive
        EffectiveBillStatus.ATRASADO -> "Atrasado" to StatusTone.Due
        EffectiveBillStatus.VENCE_HOJE -> "Vence hoje" to StatusTone.Due
        EffectiveBillStatus.PAGO -> "Pago" to StatusTone.Scheduled
        EffectiveBillStatus.PENDENTE -> "Pendente" to StatusTone.Pending
    }
    val amount = formatBrl0(centsToReais(valorCentavos)).let { if (tipo == "a_receber") "+ $it" else it }
    return WeekBill(
        day = date.dayOfMonth.toString().padStart(2, '0'),
        mon = MONTH_ABBREV_PT[date.monthValue - 1].uppercase(),
        name = nome,
        cat = if (tipo == "a_receber") "Entrada" else if (recorrente) "Fixo" else "Conta",
        amount = amount,
        status = label,
        statusTone = tone,
    )
}

/**
 * Só lançamentos avulsos (não recorrentes) entram aqui — um lançamento recorrente é
 * projetado por mês (ver [transactionsInMonth]), não numa janela corrida de 7 dias que
 * pode cruzar mês, e cobrir isso direito exigiria a mesma projeção com um range de
 * datas em vez de um mês inteiro (fora do escopo desta correção pontual).
 */
fun TransacaoEntity.toUiWeekBill(today: LocalDate = LocalDate.now()): WeekBill {
    val date = data.toLocalDate()
    val amount = formatBrl0(centsToReais(valorCentavos)).let { if (tipo == TransactionType.Receita.name) "+ $it" else it }
    return WeekBill(
        day = date.dayOfMonth.toString().padStart(2, '0'),
        mon = MONTH_ABBREV_PT[date.monthValue - 1].uppercase(),
        name = descricao.ifBlank { categoria },
        cat = if (tipo == TransactionType.Receita.name) "Entrada" else "Lançamento",
        amount = amount,
        status = if (date == today) "Hoje" else "Previsto",
        statusTone = StatusTone.Positive,
    )
}

fun ExtraIncome.toUiTimelineEntry(): TimelineEntry = TimelineEntry(
    month = MONTH_ABBREV_PT[date.monthValue - 1],
    amount = "+ " + formatBrl0(centsToReais(cents)),
    label = label,
    tone = TimelineTone.Positive,
)

fun AssinaturaEntity.toUiSubscription(insight: SubscriptionInsight): Subscription {
    val note = when {
        ultimoUso == null -> "Nunca utilizada"
        insight.daysSinceLastUse != null && insight.daysSinceLastUse < 1 -> "Uso recente"
        else -> "Sem acesso há ${insight.daysSinceLastUse} dias"
    }
    return Subscription(
        name = nome,
        note = note,
        amount = formatBrl0(centsToReais(valorCentavos)),
        cta = if (insight.looksUnused) "Cancelar" else "Manter",
    )
}

private fun formatDayMonth(millis: Long): String {
    val d = millis.toLocalDate()
    return "%02d/%02d".format(d.dayOfMonth, d.monthValue)
}
