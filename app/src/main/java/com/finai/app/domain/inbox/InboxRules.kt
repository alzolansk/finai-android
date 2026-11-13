package com.finai.app.domain.inbox

import com.finai.app.data.local.entity.AvisoEntity
import com.finai.app.data.local.entity.SugestaoLancamentoEntity
import com.finai.app.domain.FinanceAlert
import com.finai.app.domain.GoalInsight
import com.finai.app.domain.TimelineMovement
import java.time.LocalDate
import kotlin.math.abs

enum class InboxGroup { Confirm, Act, Watch }

data class InboxItem(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val valueCents: Long = 0,
    val group: InboxGroup = InboxGroup.Watch,
    val due: LocalDate? = null,
    /** 0 = previsto; 1 = limite atingido ou conta atrasada. Guardado na dispensa. */
    val level: Int = 0,
    val alert: FinanceAlert? = null,
    val suggestion: SugestaoLancamentoEntity? = null,
    val insight: GoalInsight? = null,
    val movement: TimelineMovement? = null,
) {
    val stateType: String get() = "$kind:$level"
}

object InboxRules {
    fun worsened(item: InboxItem, state: AvisoEntity): Boolean {
        // Decisão confirmada ("Manter separadas") não é um aviso financeiro reabrível.
        if (item.insight?.permanentDismiss == true || item.kind == "coach") return false
        val previousLevel = state.tipo.substringAfterLast(':', "0").toIntOrNull() ?: 0
        return item.level > previousLevel ||
            abs(item.valueCents.toDouble() - state.valorRef) > abs(state.valorRef.toDouble()) * 0.20
    }

    fun visible(items: List<InboxItem>, states: List<AvisoEntity>, today: LocalDate): List<InboxItem> {
        val byId = states.associateBy { it.id }
        return items.filter { item ->
            val state = byId[item.id]
            when (state?.estado) {
                "resolvido" -> false
                "dispensado" -> worsened(item, state)
                "adiado" -> state.adiadoAte?.let { !today.isBefore(LocalDate.parse(it)) } ?: true
                else -> true
            }
        }
    }

    fun isNew(item: InboxItem, states: List<AvisoEntity>): Boolean = states.firstOrNull { it.id == item.id }?.vistoEm == null

    /** Reconcilia sem apagar decisões antigas; um evento que deixa de existir fica resolvido. */
    fun reconcile(items: List<InboxItem>, states: List<AvisoEntity>, today: LocalDate): List<AvisoEntity> {
        val active = items.associateBy { it.id }
        val old = states.associateBy { it.id }
        return (old.keys + active.keys).map { id ->
            val item = active[id]
            val state = old[id]
            when {
                item == null -> if (state!!.estado in setOf("novo", "visto", "adiado"))
                    state.copy(estado = "resolvido", resolvidoEm = today.toString()) else state
                state == null -> AvisoEntity(id, item.stateType, valorRef = item.valueCents)
                state.estado == "dispensado" && worsened(item, state) -> AvisoEntity(id, item.stateType, valorRef = item.valueCents)
                state.estado == "adiado" && (state.adiadoAte == null || !today.isBefore(LocalDate.parse(state.adiadoAte))) ->
                    AvisoEntity(id, item.stateType, valorRef = item.valueCents)
                else -> state
            }
        }
    }
}
