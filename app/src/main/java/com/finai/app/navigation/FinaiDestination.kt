package com.finai.app.navigation

/**
 * The six screens from the prototype (planning.md §3). Home/Agenda/Goals/Debts
 * sit behind the bottom-nav tabs; Budgets and Import are reached from actions
 * inside other screens (a "Ver todos", the FAB menu, etc.) — mirrors the
 * prototype's `screen` state / `screens` label map in support.js.
 */
enum class FinaiDestination(val route: String, val screenLabel: String) {
    AiSettings("configuracoes-ia", "Configurações"),
    Home("home", "Controle inteligente"),
    Agenda("agenda", "Agenda do mês"),
    Goals("metas", "Objetivos"),
    Debts("dividas", "Plano de quitação"),
    Budgets("orcamentos", "Limites"),
    Import("importar", "Importação");

    companion object {
        fun fromRoute(route: String?): FinaiDestination =
            entries.find { it.route == route } ?: Home
    }
}
