package com.finai.app.data.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object InboxLinks {
    const val ACTION_OPEN = "com.finai.app.OPEN_INBOX"
    private val requested = MutableStateFlow(false)
    val open = requested.asStateFlow()
    fun request() { requested.value = true }
    fun consume() { requested.value = false }
}
