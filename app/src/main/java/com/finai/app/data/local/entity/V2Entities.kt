package com.finai.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Fato confirmado pelo usuário que o assistente pode citar (fase 9). Nunca inferido pela IA. */
@Entity(tableName = "memoria")
data class MemoriaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val texto: String,
    /** "padrao" (Já sei), "decisao" (Manter), "nota" (dito no chat). */
    val tipo: String,
    val criadoEm: Long,
    val origem: String,
)

/** Categoria que o usuário escolheu para um estabelecimento: vale antes da regra fixa e da IA. */
@Entity(tableName = "regras_categoria")
data class RegraCategoriaEntity(
    @PrimaryKey val estabelecimentoNormalizado: String,
    val categoria: String,
)

/** Resposta de um passo do fechamento do mês; `passo = "resumo"` guarda o texto da IA. */
@Entity(tableName = "fechamentos", primaryKeys = ["mes", "passo"])
data class FechamentoEntity(
    val mes: String,
    val passo: String,
    val resposta: String,
    val em: Long,
)
