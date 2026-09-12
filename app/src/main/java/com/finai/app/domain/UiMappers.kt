package com.finai.app.domain

import androidx.compose.ui.graphics.Color
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
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
        analysis = "A leitura da IA sobre este objetivo chega na Fase 2 (planning.md §9).",
        action = "Registrar aporte",
    )
}

fun DebtPlan.toUiDebt(): Debt {
    val (rateColor, barColor) = toneColors(
        over = divida.taxaJurosMensalBasisPoints >= 800,
        warn = divida.taxaJurosMensalBasisPoints >= 200,
    )
    val meta = if (divida.parcelasRestantes > 0 && divida.valorParcelaCentavos > 0) {
        "${divida.parcelasRestantes} parcela(s) restante(s) · ${formatBrl0(centsToReais(divida.valorParcelaCentavos))}/mês"
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

private fun initialsOf(nome: String): String =
    nome.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifEmpty { "?" }

fun ContaEntity.toUiBill(): Bill {
    val status = when (this.status) {
        "pago" -> BillStatus.Paid
        "atrasado" -> BillStatus.Overdue
        "vence_hoje" -> BillStatus.DueToday
        else -> BillStatus.Pending
    }
    val (tint, ink) = palette[(nome.hashCode() and Int.MAX_VALUE) % palette.size]
    return Bill(
        name = nome,
        meta = (if (recorrente) "Fixo" else "Avulso") + " · " + formatDayMonth(vencimento),
        amount = formatBrl0(centsToReais(valorCentavos)),
        status = status,
        initials = initialsOf(nome),
        tint = tint,
        ink = ink,
    )
}

fun ContaEntity.toUiWeekBill(): WeekBill {
    val date = vencimento.toLocalDate()
    val (label, tone) = when {
        tipo == "a_receber" -> "Previsto" to StatusTone.Positive
        status == "atrasado" -> "Atrasado" to StatusTone.Due
        status == "vence_hoje" -> "Vence hoje" to StatusTone.Due
        status == "pago" -> "Pago" to StatusTone.Scheduled
        else -> "Pendente" to StatusTone.Pending
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

fun ContaEntity.toUiTimelineEntry(): TimelineEntry = TimelineEntry(
    month = MONTH_ABBREV_PT[vencimento.toLocalDate().monthValue - 1],
    amount = "+ " + formatBrl0(centsToReais(valorCentavos)),
    label = nome,
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
