package com.finai.app.data.model

import androidx.compose.ui.graphics.Color

/**
 * Fixed-shape UI models the Compose screens render. They mirror the plain
 * objects built by `renderVals()` in the Claude Design prototype
 * (project/FinAI Mobile.dc.html) — one class per `sc-for` list — but every
 * instance is now produced from Room data by `domain/UiMappers.kt`, never
 * from a fixture. No business logic lives here: that's `domain/`'s job
 * (planning.md §6).
 */

enum class GoalBadge(val label: String, val bg: Color, val fg: Color) {
    OnTrack("No ritmo", Color(0xFFECFDF5), Color(0xFF047857)),
    Reassess("Reavaliar", Color(0xFFFFFBEB), Color(0xFFB45309)),
    // Neutro, não roxo (Fase 7, item 7): prioridade é informação, não alerta.
    Priority("Prioridade", Color(0xFFF4F4F5), Color(0xFF3F3F46)),
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
    /** Descrição escrita pelo usuário (por que a meta existe, flexibilidade do prazo...). */
    val description: String = "",
    /** Sobra projetada até o prazo, em texto — vai para a IA ([com.finai.app.domain.SavingsProjection]). */
    val projectionNote: String = "",
    /** "Concluída em set/2026"; nulo enquanto a meta está em andamento. */
    val completedLabel: String? = null,
    /** Quanto passou do alvo (aportes depois de concluída). */
    val exceeded: Double = 0.0,
) {
    val progress: Float get() = (saved / target).toFloat().coerceIn(0f, 1f)
}

enum class TimelineTone(val color: Color, val line: Color) {
    Positive(Color(0xFF047857), Color(0xFF10B981)),
    Neutral(Color(0xFF63636B), Color(0xFFE4E4E7)),
    Negative(Color(0xFFBE123C), Color(0xFFFDA4AF)),
}

data class TimelineEntry(
    val month: String,
    val amount: String,
    val label: String,
    val tone: TimelineTone,
)

enum class StatusTone(val color: Color) {
    Due(Color(0xFFBE123C)),
    Scheduled(Color(0xFF63636B)),
    Pending(Color(0xFFB45309)),
    Positive(Color(0xFF047857)),
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
    Overdue("Atrasado", Color(0xFFFFF1F2), Color(0xFFBE123C)),
    Pending("Pendente", Color(0xFFF4F4F5), Color(0xFF3F3F46)),
    DueToday("Vence hoje", Color(0xFFFFFBEB), Color(0xFF92400E)),
    Paid("Pago", Color(0xFFECFDF5), Color(0xFF047857)),
    Expected("Previsto", Color(0xFFF4F4F5), Color(0xFF3F3F46)),
}

data class Bill(
    val id: Long,
    val name: String,
    val meta: String,
    val amount: String,
    val status: BillStatus,
    val initials: String,
    val tint: Color,
    val ink: Color,
    /** Vencimento — a Agenda agrupa a lista por dia. */
    val date: java.time.LocalDate? = null,
    val recorrente: Boolean = false,
    val aReceber: Boolean = false,
    val valorCentavos: Long = 0,
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
    val hasParcelaFixa: Boolean = false,
)

data class PaidDebt(
    val id: Long,
    val name: String,
    /** Valor original do contrato (ou "—" se não informado). */
    val originalLabel: String,
    val paidLabel: String,
    val detail: String,
)

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
    val icon: String,
    val tintFrom: Color,
    val tintTo: Color,
    val ink: Color,
    val title: String,
    val sub: String,
)

enum class ChatRole { Me, Ai }

data class ChatMessage(val role: ChatRole, val text: String)

data class SimEffect(val label: String, val detail: String, val delta: String, val color: Color)

enum class SimVerdict(val label: String, val background: Color) {
    Fits("Cabe, sem mexer em nada", Color(0xFF065F46)),
    FitsButCosts("Cabe, mas custa tempo", Color(0xFF78350F)),
    DoesNotFit("Não cabe este mês", Color(0xFF881337)),
}
