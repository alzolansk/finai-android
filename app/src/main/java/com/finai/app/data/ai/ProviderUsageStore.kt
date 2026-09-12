package com.finai.app.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.first

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

    override suspend fun recordAttempt(provider: ProviderId) {
        dataStore.edit { prefs -> prefs[callsKey(provider)] = (prefs[callsKey(provider)] ?: 0) + 1 }
    }

    override suspend fun markExhaustedToday(provider: ProviderId) {
        dataStore.edit { prefs -> prefs[exhaustedDateKey(provider)] = LocalDate.now().toString() }
    }

    /** True if [provider] hit its rate limit earlier today (local date) — [AiRouter] skips it without calling out. */
    override suspend fun isExhaustedToday(provider: ProviderId): Boolean {
        val exhaustedDate = dataStore.data.first()[exhaustedDateKey(provider)] ?: return false
        return exhaustedDate == LocalDate.now().toString()
    }

    companion object {
        @Volatile private var instance: ProviderUsageStore? = null

        fun get(context: Context): ProviderUsageStore = instance ?: synchronized(this) {
            instance ?: ProviderUsageStore(context).also { instance = it }
        }
    }
}
