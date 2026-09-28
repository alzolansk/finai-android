package com.finai.app.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.finai.app.util.FinaiLog
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiUsageDataStore by preferencesDataStore(name = "finai_ai_usage")

/**
 * The slice of [ProviderUsageStore] [AiRouter] depends on — pulled out as an
 * interface purely so router fallback logic can be unit-tested (see
 * `AiRouterTest`) with an in-memory fake instead of a real DataStore/Context.
 */
interface UsageTracker {
    suspend fun recordAttempt(provider: ProviderId)
    suspend fun markExhaustedToday(provider: ProviderId)
    suspend fun isExhaustedToday(provider: ProviderId): Boolean
}

/**
 * Local per-provider call counter and daily-exhaustion flag (planning.md §5's
 * `UsoProvedorIA` entity / §7.3's "controller local... reiniciando à
 * meia-noite"). Plain DataStore, not encrypted — this holds counts and dates
 * only, never a key or prompt/response content.
 *
 * [AiRouter] is reactive, not predictive: it doesn't guess a provider's daily
 * limit (those change without notice — planning.md §7.2) and stop early.
 * Instead a provider is marked exhausted only after it actually returns a
 * 429, and stays skipped — without spending another network call — until the
 * local date rolls over.
 */
class ProviderUsageStore private constructor(context: Context) : UsageTracker {

    private val dataStore = context.applicationContext.aiUsageDataStore

    private fun callsKey(provider: ProviderId) = intPreferencesKey("calls_${provider.name.lowercase()}")
    private fun exhaustedDateKey(provider: ProviderId) = stringPreferencesKey("exhausted_date_${provider.name.lowercase()}")

    // Fase 6: o DataStore lança IOException quando o arquivo está corrompido ou
    // o disco está cheio. Esta é uma contagem de cota, não dado do usuário:
    // perder a contagem é um custo aceitável, derrubar o app (ou a rotina de
    // notificação, que roda com o app fechado) por causa dela não é. Em falha, o
    // comportamento degradado é sempre o mais permissivo — tentar o provedor —
    // porque um 429 real volta a marcar o esgotamento na chamada seguinte.

    override suspend fun recordAttempt(provider: ProviderId) {
        try {
            dataStore.edit { prefs -> prefs[callsKey(provider)] = (prefs[callsKey(provider)] ?: 0) + 1 }
        } catch (e: IOException) {
            FinaiLog.w(TAG, "Não foi possível registrar a chamada de ${provider.displayName}", e)
        }
    }

    override suspend fun markExhaustedToday(provider: ProviderId) {
        try {
            dataStore.edit { prefs -> prefs[exhaustedDateKey(provider)] = LocalDate.now().toString() }
        } catch (e: IOException) {
            FinaiLog.w(TAG, "Não foi possível marcar ${provider.displayName} como esgotado", e)
        }
    }

    /** True if [provider] hit its rate limit earlier today (local date) — [AiRouter] skips it without calling out. */
    /**
     * Tira a marca de "sem cota hoje" — ao salvar uma chave nova ou quando o teste de conexão
     * passa. Sem isto, uma marca errada (ou de uma chave antiga) só saía à meia-noite.
     */
    suspend fun clearExhausted(provider: ProviderId) {
        try {
            dataStore.edit { prefs -> prefs.remove(exhaustedDateKey(provider)) }
        } catch (e: java.io.IOException) {
            FinaiLog.w(TAG, "Falha ao limpar a marca de cota de ${provider.name}", e)
        }
    }

    override suspend fun isExhaustedToday(provider: ProviderId): Boolean = try {
        dataStore.data.first()[exhaustedDateKey(provider)] == LocalDate.now().toString()
    } catch (e: IOException) {
        FinaiLog.w(TAG, "Não foi possível ler a cota de ${provider.displayName}", e)
        false
    }

    /**
     * Provedores marcados como esgotados hoje — só para a tela de
     * Configurações mostrar "cota esgotada hoje" em vez de "em uso". A data é
     * lida a cada emissão; se a meia-noite passar com a tela aberta, o estado
     * só se corrige na próxima escrita, o que é aceitável para um rótulo.
     */
    fun exhaustedTodayFlow(): Flow<Set<ProviderId>> = dataStore.data
        .map { prefs ->
            val today = LocalDate.now().toString()
            ProviderId.entries.filter { prefs[exhaustedDateKey(it)] == today }.toSet()
        }
        .catch { e ->
            FinaiLog.w(TAG, "Não foi possível ler a cota dos provedores", e)
            emit(emptySet())
        }

    companion object {
        private const val TAG = "ProviderUsageStore"

        @Volatile private var instance: ProviderUsageStore? = null

        fun get(context: Context): ProviderUsageStore = instance ?: synchronized(this) {
            instance ?: ProviderUsageStore(context).also { instance = it }
        }
    }
}
