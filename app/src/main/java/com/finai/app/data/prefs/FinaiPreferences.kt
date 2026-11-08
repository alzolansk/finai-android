package com.finai.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val TIPS_SEEN = stringSetPreferencesKey("tips_seen")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DISMISSED_INSIGHTS = stringSetPreferencesKey("dismissed_insights")
        val CAPTURE_ENABLED = booleanPreferencesKey("capture_enabled")
        val CAPTURE_PACKAGES = stringSetPreferencesKey("capture_packages")
        val SAFETY_MARGIN = longPreferencesKey("safety_margin_cents")
        val RECON_DATE = stringPreferencesKey("reconciled_on")
        val RECON_BALANCE = longPreferencesKey("reconciled_balance_cents")
        val RECON_ADJUST = longPreferencesKey("reconciled_adjust_cents")
    }

    /**
     * Última conciliação com o saldo do banco: data, saldo informado e ajuste lançado. Decide o
     * aviso "Confira com o banco" da Início (mais de 30 dias ou nunca). O saldo em si vem dos
     * lançamentos, inclusive o de ajuste, que ficam no Room. "Apagar todos os dados" limpa.
     */
    val reconciliation: Flow<com.finai.app.domain.ReconciliationState?> = context.dataStore.data.map { p ->
        val date = p[Keys.RECON_DATE]?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return@map null
        com.finai.app.domain.ReconciliationState(date, p[Keys.RECON_BALANCE] ?: 0L, p[Keys.RECON_ADJUST] ?: 0L)
    }

    suspend fun saveReconciliation(state: com.finai.app.domain.ReconciliationState) {
        context.dataStore.edit {
            it[Keys.RECON_DATE] = state.date.toString()
            it[Keys.RECON_BALANCE] = state.balanceCents
            it[Keys.RECON_ADJUST] = state.adjustmentCents
        }
    }

    suspend fun clearReconciliation() {
        context.dataStore.edit { it.remove(Keys.RECON_DATE); it.remove(Keys.RECON_BALANCE); it.remove(Keys.RECON_ADJUST) }
    }

    /**
     * Lançamento por notificações: se a captura está ligada e quais apps o FinAI acompanha. A
     * autorização de leitura é do Android (acesso a notificações); isto é só a escolha do
     * usuário dentro do FinAI. Sem a chave, desligada. "Apagar todos os dados" pausa.
     */
    val captureEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.CAPTURE_ENABLED] ?: false }
    val capturePackages: Flow<Set<String>> = context.dataStore.data.map { it[Keys.CAPTURE_PACKAGES] ?: emptySet() }

    suspend fun setCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CAPTURE_ENABLED] = enabled }
    }

    suspend fun setCapturePackages(packages: Set<String>) {
        context.dataStore.edit { it[Keys.CAPTURE_PACKAGES] = packages }
    }

    /**
     * Margem de segurança do "livre" (planning-v2, Fase 1): quanto o app separa e não chama de
     * livre. Padrão R$ 300 fixos; 0 = sem margem. Preferência de uso, não dado financeiro: não é
     * apagada por "Apagar todos os dados" e nunca é recalculada sozinha.
     */
    val safetyMarginCents: Flow<Long> = context.dataStore.data.map {
        it[Keys.SAFETY_MARGIN] ?: com.finai.app.domain.balance.FreeToSpend.DEFAULT_MARGIN_CENTS
    }

    suspend fun setSafetyMarginCents(cents: Long) {
        context.dataStore.edit { it[Keys.SAFETY_MARGIN] = cents.coerceAtLeast(0) }
    }

    /**
     * Pontos de atenção de Objetivos que o usuário dispensou ([com.finai.app.domain.GoalInsight.dismissKey]).
     * Apagado com "Apagar todos os dados", junto com as metas a que se refere.
     */
    val dismissedInsights: Flow<Set<String>> = context.dataStore.data.map { it[Keys.DISMISSED_INSIGHTS] ?: emptySet() }

    suspend fun dismissInsight(key: String) {
        context.dataStore.edit { it[Keys.DISMISSED_INSIGHTS] = (it[Keys.DISMISSED_INSIGHTS] ?: emptySet()) + key }
    }

    suspend fun clearDismissedInsights() {
        context.dataStore.edit { it.remove(Keys.DISMISSED_INSIGHTS) }
    }

    /**
     * Aparência escolhida em Configurações → Aparência. Sem a chave (instalação nova ou
     * anterior ao tema escuro) fica [ThemeMode.LIGHT], que era o único tema até então.
     * Não é apagado por "Apagar todos os dados": é preferência de uso, não dado financeiro.
     */
    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { p -> ThemeMode.entries.firstOrNull { it.name == p[Keys.THEME_MODE] } ?: ThemeMode.LIGHT }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /**
     * Telas cujas dicas já foram vistas (Fase 7, item 6 — substitui o tour em tela cheia).
     * Sem a chave, quem já tinha concluído o tour antigo ([Keys.ONBOARDING_COMPLETE]) conta
     * como tendo visto todas: não deve ver as dicas de novo sozinho.
     */
    val tipsSeen: Flow<Set<String>> = context.dataStore.data.map { seenOf(it) }

    private fun seenOf(p: androidx.datastore.preferences.core.Preferences): Set<String> =
        p[Keys.TIPS_SEEN] ?: if (p[Keys.ONBOARDING_COMPLETE] == true) com.finai.app.domain.TipScript.screens else emptySet()

    suspend fun markTipsSeen(screen: String) {
        context.dataStore.edit { it[Keys.TIPS_SEEN] = seenOf(it) + screen }
    }

    /** "Rever tour guiado" e "Apagar todos os dados": as dicas voltam a aparecer em cada tela. */
    suspend fun resetTips() {
        context.dataStore.edit { it[Keys.TIPS_SEEN] = emptySet() }
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

/** Tema do app. [SYSTEM] acompanha o modo escuro do Android. */
enum class ThemeMode(val label: String) {
    LIGHT("Claro"),
    DARK("Escuro"),
    SYSTEM("Igual ao sistema"),
}
