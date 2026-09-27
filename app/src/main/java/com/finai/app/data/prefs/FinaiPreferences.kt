package com.finai.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed app preferences — o tour guiado, a configuração inicial de
 * dados e a lista de contas/cartões conhecidos vivem aqui. AI provider keys
 * never live here: they go through [com.finai.app.data.prefs.AiKeyStore]
 * (EncryptedSharedPreferences/Android Keystore, planning.md §7.4).
 * Per-provider daily call counters for the router (planning.md §7.3) live in
 * their own DataStore file, [com.finai.app.data.ai.ProviderUsageStore], kept
 * separate so a schema change there can't collide with this flag.
 *
 * Não guarda nenhum dado financeiro nem flag de "dataset inicial semeado" —
 * uma instalação nova do app começa sem nenhum lançamento/conta/objetivo/
 * dívida, e o que é persistido fora do Room é só: se o usuário já viu (ou
 * pulou) o tour guiado, se já passou (ou pulou) a configuração inicial de
 * dados, e os nomes de conta/cartão que ele digitou nessa configuração (só o
 * nome — "Nubank", "Carteira" — nunca um número de conta/cartão real).
 */
private val Context.dataStore by preferencesDataStore(name = "finai_prefs")

class FinaiPreferences(private val context: Context) {
    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val INITIAL_SETUP_COMPLETE = booleanPreferencesKey("initial_setup_complete")
        val KNOWN_ACCOUNTS = stringSetPreferencesKey("known_accounts")
    }

    /** Ausência da chave (instalação nova, ou depois de "Apagar todos os dados") conta como "não concluído". */
    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    /**
     * Configuração inicial de dados financeiros (renda, contas, gastos fixos,
     * dívidas, objetivos) — [com.finai.app.ui.components.InitialSetupWizard],
     * mostrada uma vez logo depois do tour guiado. Ausência da chave conta
     * como "não concluído", mesma convenção de [onboardingComplete].
     */
    val initialSetupComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INITIAL_SETUP_COMPLETE] ?: false }

    suspend fun setInitialSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.INITIAL_SETUP_COMPLETE] = complete }
    }

    /**
     * Nomes de conta/cartão que o usuário já digitou (na configuração inicial
     * ou num lançamento manual anterior — [com.finai.app.FinaiApp] soma isto
     * aos nomes distintos já usados em `TransacaoEntity.contaOrigem`), para o
     * seletor de conta do lançamento manual oferecer algo além de "Carteira"
     * mesmo antes do primeiro lançamento.
     */
    val knownAccounts: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.KNOWN_ACCOUNTS] ?: emptySet() }

    suspend fun addKnownAccounts(names: Set<String>) {
        val trimmed = names.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { it[Keys.KNOWN_ACCOUNTS] = (it[Keys.KNOWN_ACCOUNTS] ?: emptySet()) + trimmed }
    }

    /**
     * Some da lista de conta/cartão conhecidos ("Configurações" →
     * "Contas e cartões"). Não afeta lançamentos já gravados com esse nome em
     * `contaOrigem` — eles continuam existindo e o nome continua aparecendo
     * no seletor por causa deles, o que é o comportamento certo (não é dado
     * órfão, é um lançamento real).
     */
    suspend fun removeKnownAccount(name: String) {
        context.dataStore.edit { it[Keys.KNOWN_ACCOUNTS] = (it[Keys.KNOWN_ACCOUNTS] ?: emptySet()) - name }
    }
}
