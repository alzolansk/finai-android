package com.finai.app.data.ai

import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache of successful responses for the exact same request seen
 * earlier today (planning.md §7.3 — "cache simples... evitando gastar cota
 * com a mesma chamada duas vezes"), e.g. the simulator veredict for the same
 * purchase amount asked twice. A process-wide `object` rather than something
 * built per-[AiRouter] instance, since [com.finai.app.state.AiViewModel] and
 * [com.finai.app.state.AppViewModel] each build their own router and must
 * still share one cache. Deliberately in-memory only (not DataStore/Room):
 * it exists to avoid a handful of redundant calls within a running session,
 * not to survive process death, and holds only already-public-facing AI
 * text, never a key.
 */
object AiResponseCache {

    private data class Entry(val text: String, val date: LocalDate)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(request: AiRequest): String? {
        val entry = entries[keyFor(request)] ?: return null
        return entry.text.takeIf { entry.date == LocalDate.now() }
    }

    fun put(request: AiRequest, text: String) {
        entries[keyFor(request)] = Entry(text, LocalDate.now())
    }

    private fun keyFor(request: AiRequest): String {
        val raw = "${request.task}|${request.systemInstruction}|${request.prompt}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
