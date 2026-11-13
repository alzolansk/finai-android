package com.finai.app.data.repository

import androidx.room.withTransaction
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.AvisoEntity
import com.finai.app.domain.inbox.InboxItem
import com.finai.app.domain.inbox.InboxRules
import java.time.LocalDate

class InboxRepository(private val db: FinaiDatabase) {
    private val dao = db.avisoDao()
    val states = dao.observeAll()

    suspend fun synchronize(items: List<InboxItem>, legacy: Set<String>, today: LocalDate) = db.withTransaction {
        var old = dao.getAll()
        if (old.none { it.id == LEGACY_MARKER }) {
            legacy.forEach { key ->
                val id = if (key.startsWith("coach:")) key else "meta-${key.substringBefore('|')}"
                if (old.none { it.id == id }) {
                    val item = items.firstOrNull { it.id == id }
                    dao.upsert(AvisoEntity(id, item?.stateType ?: "legacy", "dispensado", item?.valueCents ?: 0,
                        dispensadoEm = today.toString()))
                }
            }
            dao.upsert(AvisoEntity(LEGACY_MARKER, "interno", "resolvido"))
            old = dao.getAll()
        }
        // Para uma dispensa antiga sem evento atual, a primeira observação estabelece a referência.
        val baselined = old.map { state ->
            val item = items.firstOrNull { it.id == state.id }
            if (state.tipo == "legacy" && item != null) state.copy(tipo = item.stateType, valorRef = item.valueCents) else state
        }
        InboxRules.reconcile(items, baselined, today).forEach { next ->
            if (next != old.firstOrNull { it.id == next.id }) dao.upsert(next)
        }
    }

    suspend fun markSeen(ids: Set<String>, today: LocalDate) = db.withTransaction {
        dao.getAll().filter { it.id in ids && it.estado == "novo" }.forEach {
            dao.upsert(it.copy(estado = "visto", vistoEm = today.toString()))
        }
    }

    suspend fun dismiss(item: InboxItem, today: LocalDate) = db.withTransaction {
        val previous = dao.getAll().firstOrNull { it.id == item.id }
        dao.upsert(AvisoEntity(item.id, item.stateType, "dispensado", item.valueCents,
            vistoEm = previous?.vistoEm ?: today.toString(), dispensadoEm = today.toString()))
    }

    suspend fun snooze(item: InboxItem, until: LocalDate, today: LocalDate) = db.withTransaction {
        require(until.isAfter(today)) { "Escolha um dia futuro" }
        dao.upsert(AvisoEntity(item.id, item.stateType, "adiado", item.valueCents,
            vistoEm = today.toString(), adiadoAte = until.toString()))
    }

    suspend fun restoreDismissed() = db.withTransaction {
        dao.getAll().filter { it.estado == "dispensado" && !it.id.startsWith("coach:") }.forEach {
            dao.upsert(AvisoEntity(it.id, it.tipo, valorRef = it.valorRef))
        }
    }

    companion object { const val LEGACY_MARKER = "_legacy-inbox-v1" }
}
