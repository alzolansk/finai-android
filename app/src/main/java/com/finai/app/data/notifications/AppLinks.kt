package com.finai.app.data.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Para onde o toque numa notificação leva. A MainActivity pede; o FinaiApp navega e consome. */
object AppLinks {
    const val ACTION_OPEN = "com.finai.app.OPEN_ROUTE"
    const val EXTRA_ROUTE = "com.finai.app.extra.ROUTE"
    const val INBOX = "caixa"
    const val AGENDA = "agenda"
    const val SPENDING = "gastos"
    const val PLANS = "planos"
    const val ENTRY = "lancar"
    const val CLOSING = "fechamento"

    private val requested = MutableStateFlow<String?>(null)
    val open = requested.asStateFlow()
    fun request(route: String) { requested.value = route }
    fun consume() { requested.value = null }
}
