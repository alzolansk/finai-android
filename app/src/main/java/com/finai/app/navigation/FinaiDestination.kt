package com.finai.app.navigation

/**
 * The six screens from the prototype (planning.md §3). Home/Agenda/Goals/Debts
 * sit behind the bottom-nav tabs; Budgets and Import are reached from actions
 * inside other screens (a "Ver todos", the FAB menu, etc.) — mirrors the
 * prototype's `screen` state / `screens` label map in support.js.
 *
 * [headerTitle] é o título da tela: grande no conteúdo e compacto no cabeçalho 4a quando
 * a lista rola. Os dois usam o mesmo texto.
 */
enum class FinaiDestination(val route: String, val headerTitle: String) {
    AiSettings("configuracoes-ia", "Configurações"),
    Home("home", "Início"),
    Agenda("agenda", "Agenda"),
    Goals("metas", "Objetivos"),
    Debts("dividas", "Dívidas"),
    Budgets("orcamentos", "Limites do mês"),
    Import("importar", "Importar fatura");

    companion object {
        fun fromRoute(route: String?): FinaiDestination =
            entries.find { it.route == route } ?: Home
    }
}
