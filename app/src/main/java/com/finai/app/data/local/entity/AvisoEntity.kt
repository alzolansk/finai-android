package com.finai.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Estado do evento, separado do texto recalculado. Datas ISO locais; tipo guarda a etapa na dispensa. */
@Entity(tableName = "avisos")
data class AvisoEntity(
    @PrimaryKey val id: String,
    val tipo: String,
    val estado: String = "novo",
    val valorRef: Long = 0,
    val vistoEm: String? = null,
    val resolvidoEm: String? = null,
    val dispensadoEm: String? = null,
    val adiadoAte: String? = null,
)
