package com.finai.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed app preferences. Phase 0 only proves the wiring with one
 * flag; from Phase 2 on this is also where AI provider keys go — always via
 * EncryptedSharedPreferences/Android Keystore, never in plain DataStore
 * (planning.md §7.4), and per-provider daily call counters for the router
 * from planning.md §7.3.
 */
private val Context.dataStore by preferencesDataStore(name = "finai_prefs")

class FinaiPreferences(private val context: Context) {
    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }
}
