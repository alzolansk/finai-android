package com.finai.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed app preferences — onboarding/seed flags only. AI provider
 * keys never live here: they go through
 * [com.finai.app.data.prefs.AiKeyStore] (EncryptedSharedPreferences/Android
 * Keystore, planning.md §7.4). Per-provider daily call counters for the
 * router (planning.md §7.3) live in their own DataStore file,
 * [com.finai.app.data.ai.ProviderUsageStore], kept separate so a schema
 * change there can't collide with these flags.
 */
private val Context.dataStore by preferencesDataStore(name = "finai_prefs")

class FinaiPreferences(private val context: Context) {
    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val SEEDED = booleanPreferencesKey("seeded_starter_data")
    }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    /** True once [com.finai.app.data.repository.FinanceSeeder] has inserted the starter dataset. */
    val seeded: Flow<Boolean> = context.dataStore.data.map { it[Keys.SEEDED] ?: false }

    suspend fun setSeeded(seeded: Boolean) {
        context.dataStore.edit { it[Keys.SEEDED] = seeded }
    }
}
