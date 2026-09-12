package com.finai.app.data.model

import androidx.compose.ui.graphics.Color

/**
 * Fixed-shape UI models for the Phase 0 prototype screens. These mirror the
 * plain objects built by `renderVals()` in the Claude Design prototype
 * (project/FinAI Mobile.dc.html) — one class per `sc-for` list. No business
 * logic lives here yet; real computation (goal progress, debt ordering,
 * safe-to-spend, ...) arrives in Phase 1 per planning.md §6.
 */

enum class GoalBadge(val label: String, val bg: Color, val fg: Color) {
    OnTrack("No ritmo", Color(0xFFECFDF5), Color(0xFF059669)),
    Reassess("Reavaliar", Color(0xFFFFFBEB), Color(0xFFB45309)),
    Priority("Prioridade", Color(0xFFEEF2FF), Color(0xFF4F46E5)),
}

data class Goal(
    val id: String,
    val kind: String,
    val name: String,
    val saved: Double,
    val target: Double,
    val eta: String,
    val badge: GoalBadge,
    val note: String,
    val action: String,
) {
    val progress: Float get() = (saved / target).toFloat().coerceIn(0f, 1f)
}

enum class TimelineTone(val color: Color, val line: Color) {
    Positive(Color(0xFF059669), Color(0xFF10B981)),
    Neutral(Color(0xFFA1A1AA), Color(0xFFE4E4E7)),
    Negative(Color(0xFFE11D48), Color(0xFFFDA4AF)),
}

data class TimelineEntry(
    val month: String,
    val amount: String,
    val label: String,
    val tone: TimelineTone,
)

enum class StatusTone(val color: Color) {
    Due(Color(0xFFE11D48)),
    Scheduled(Color(0xFFA1A1AA)),
    Pending(Color(0xFFB45309)),
    Positive(Color(0xFF059669)),
}

data class WeekBill(
    val day: String,
    val mon: String,
    val name: String,
    val cat: String,
    val amount: String,
    val status: String,
    val statusTone: StatusTone,
)

enum class BillStatus(val label: String, val bg: Color, val fg: Color) {
    Overdue("Atrasado", Color(0xFFFFF1F2), Color(0xFFE11D48)),
    Pending("Pendente", Color(0xFFFFFBEB), Color(0xFFB45309)),
    DueToday("Vence hoje", Color(0xFFFFF1F2), Color(0xFFE11D48)),
    Paid("Pago", Color(0xFFECFDF5), Color(0xFF059669)),
}

data class Bill(
    val name: String,
    val meta: String,
    val amount: String,
    val status: BillStatus,
    val initials: String,
    val tint: Color,
    val ink: Color,
)

data class Debt(
    val id: Long,
    val rank: Int,
    val name: String,
    val meta: String,
    val amount: String,
    val rate: String,
    val rateColor: Color,
    val progressPct: Float,
    val barColor: Color,
)

data class NegotiationStep(val n: Int, val text: String)

data class Budget(
    val name: String,
    val spent: Double,
    val limit: Double,
    val note: String,
    val valueColor: Color,
    val barColor: Color,
) {
    val progressPct: Float get() = (spent / limit).toFloat().coerceIn(0f, 1f)
}

data class Subscription(
    val name: String,
    val note: String,
    val amount: String,
    val cta: String,
)

data class QuickAction(
    val mark: String,
    val tint: Color,
    val ink: Color,
    val title: String,
    val sub: String,
)

enum class ChatRole { Me, Ai }

data class ChatMessage(val role: ChatRole, val text: String)

data class NotificationItem(val dotColor: Color, val title: String, val body: String)

data class SimEffect(val label: String, val detail: String, val delta: String, val color: Color)

enum class SimVerdict(val label: String, val background: Color) {
    Fits("Cabe, sem mexer em nada", Color(0xFF065F46)),
    FitsButCosts("Cabe, mas custa tempo", Color(0xFF78350F)),
    DoesNotFit("Não cabe este mês", Color(0xFF881337)),
}
