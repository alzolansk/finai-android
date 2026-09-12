package com.finai.app.data.prefs

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.finai.app.data.ai.ProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores every AI provider's API key in EncryptedSharedPreferences (AES-256
 * via the Android Keystore) — never in plain DataStore or source, per
 * planning.md §7.4. One key per [ProviderId] (Fase 3 — planning.md §9), each
 * configured independently from [com.finai.app.ui.components.ApiKeySettingsScreen].
 *
 * Singleton via [get], mirroring [com.finai.app.data.local.FinaiDatabase.get],
 * so [com.finai.app.data.ai.AiRouter] instances built by different ViewModels
 * all observe the same keys and a change from the settings dialog is seen
 * everywhere immediately.
 */
class AiKeyStore private constructor(context: Context) {

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Must never take the rest of the app down with it — Fase 1's deterministic
        // screens don't depend on this. Worst case: the AI layer behaves as if no key
        // were configured for any provider.
        Log.e("AiKeyStore", "Não foi possível abrir o armazenamento seguro das chaves de IA", e)
        null
    }

    init {
        // Fase 2 stored the (only) Gemini key under this legacy name; migrate it once so a
        // key configured before Fase 3 isn't silently lost when providers each get their own slot.
        prefs?.let { p ->
            val legacyGeminiKey = p.getString(LEGACY_GEMINI_KEY, null)
            if (legacyGeminiKey != null && p.getString(prefKey(ProviderId.GEMINI), null) == null) {
                p.edit().putString(prefKey(ProviderId.GEMINI), legacyGeminiKey).remove(LEGACY_GEMINI_KEY).apply()
            }
        }
    }

    private val flows: Map<ProviderId, MutableStateFlow<String?>> = ProviderId.entries.associateWith { provider ->
        MutableStateFlow(prefs?.getString(prefKey(provider), null))
    }

    fun keyFlow(provider: ProviderId): StateFlow<String?> = flows.getValue(provider).asStateFlow()

    /** Current value without collecting — used by [com.finai.app.data.ai.AiRouter] before each attempt. */
    fun currentKey(provider: ProviderId): String? = flows.getValue(provider).value

    fun setKey(provider: ProviderId, key: String) {
        if (prefs == null) return
        prefs.edit().putString(prefKey(provider), key).apply()
        flows.getValue(provider).value = key
    }

    fun clearKey(provider: ProviderId) {
        if (prefs == null) return
        prefs.edit().remove(prefKey(provider)).apply()
        flows.getValue(provider).value = null
    }

    private fun prefKey(provider: ProviderId) = "api_key_${provider.name.lowercase()}"

    companion object {
        /** Must match the `sharedpref` path excluded in backup_rules.xml/data_extraction_rules.xml. */
        const val PREFS_FILE_NAME = "finai_ai_keys"
        private const val LEGACY_GEMINI_KEY = "gemini_api_key"

        @Volatile private var instance: AiKeyStore? = null

        fun get(context: Context): AiKeyStore = instance ?: synchronized(this) {
            instance ?: AiKeyStore(context.applicationContext).also { instance = it }
        }
    }
}
