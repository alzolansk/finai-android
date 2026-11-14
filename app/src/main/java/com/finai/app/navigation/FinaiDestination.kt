package com.finai.app.navigation

/**
 * Destinos da navegação v2 (planning-v2 §4): Hoje · Agenda · [+] · Gastos · Planos na barra;
 * Caixa e Configurações pelo topo; Importação pelo "+" e pelas telas.
 *
 * [headerTitle] é o título da tela: grande no conteúdo e compacto no cabeçalho 4a quando
 * a lista rola. Os dois usam o mesmo texto.
 */
enum class FinaiDestination(val route: String, val headerTitle: String) {
    AiSettings("configuracoes-ia", "Configurações"),
    Home("home", "Hoje"),
    Inbox("caixa", "Caixa"),
    Agenda("agenda", "Agenda"),
    /** Planos (fase 7): Metas | Compromissos. Dívidas e Parcelamentos viraram Compromissos. */
    Goals("planos", "Planos"),
    /** Gastos (fase 6): absorve Limites e assinaturas. */
    Spending("gastos", "Gastos"),
    Import("importar", "Importar fatura");

    companion object {
        fun fromRoute(route: String?): FinaiDestination =
            entries.find { it.route == route } ?: Home
    }
}
