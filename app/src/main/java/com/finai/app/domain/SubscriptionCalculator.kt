package com.finai.app.domain

import com.finai.app.data.local.entity.AssinaturaEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val SUBSCRIPTION_UNUSED_THRESHOLD_DAYS = 45L

data class SubscriptionInsight(val assinatura: AssinaturaEntity, val daysSinceLastUse: Long?, val looksUnused: Boolean)

/** "Assinaturas que a IA questionou" — planning.md §3.5: the *flag* is a plain date-threshold rule, no AI involved. */
object SubscriptionCalculator {
    fun insights(assinaturas: List<AssinaturaEntity>, today: LocalDate = LocalDate.now()): List<SubscriptionInsight> =
        assinaturas.filter { it.status == "ativa" }.map { sub ->
            val days = sub.ultimoUso?.let { ChronoUnit.DAYS.between(it.toLocalDate(), today) }
            SubscriptionInsight(sub, days, days == null || days >= SUBSCRIPTION_UNUSED_THRESHOLD_DAYS)
        }
}
