package com.finai.app.data.prefs

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the Gemini API key in EncryptedSharedPreferences (AES-256 via the
 * Android Keystore) — never in plain DataStore or source, per planning.md
 * §7.4. Fase 3 will add one key per provider behind the same store.
 *
 * Singleton via [get], mirroring [com.finai.app.data.local.FinaiDatabase.get],
 * so every ViewModel that builds its own [com.finai.app.data.ai.GeminiAiProvider]
 * observes the same key and a change from the settings dialog is seen
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
        // were configured.
        Log.e("AiKeyStore", "Não foi possível abrir o armazenamento seguro da chave de IA", e)
        null
    }

    private val _apiKey = MutableStateFlow(prefs?.getString(KEY_GEMINI, null))
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    fun setGeminiApiKey(key: String) {
        if (prefs == null) return
        prefs.edit().putString(KEY_GEMINI, key).apply()
        _apiKey.value = key
    }

    fun clearGeminiApiKey() {
        if (prefs == null) return
        prefs.edit().remove(KEY_GEMINI).apply()
        _apiKey.value = null
    }

    companion object {
        /** Must match the `sharedpref` path excluded in backup_rules.xml/data_extraction_rules.xml. */
        const val PREFS_FILE_NAME = "finai_ai_keys"
        private const val KEY_GEMINI = "gemini_api_key"

        @Volatile private var instance: AiKeyStore? = null

        fun get(context: Context): AiKeyStore = instance ?: synchronized(this) {
            instance ?: AiKeyStore(context.applicationContext).also { instance = it }
        }
    }
}
