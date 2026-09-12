package com.finai.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities matching the model in planning.md §8, one-to-one. Desde a
 * Fase 1 são a fonte de verdade de tudo que as telas mostram, via
 * `FinanceRepository` + os calculators de `domain/`.
 */

@Entity(tableName = "transacoes")
data class TransacaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long, // epoch millis
    val descricao: String,
    val valorCentavos: Long,
    val categoria: String,
    val contaOrigem: String,
    val recorrente: Boolean,
    @androidx.room.ColumnInfo(defaultValue = "'Gasto'") val tipo: String = "Gasto",
    val origem: String, // "manual" | "importado"
)

@Entity(tableName = "contas")
data class ContaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valorCentavos: Long,
    val vencimento: Long,
    // Só o que a data de vencimento não revela. "atrasado"/"vence_hoje"/"previsto" não são
    // persistidos: mudam sozinhos com o passar do dia e são derivados por BillStatusCalculator.
    val status: String, // "pago" | "pendente"
    val tipo: String, // "a_pagar" | "a_receber"
    val recorrente: Boolean,
)

@Entity(tableName = "objetivos")
data class ObjetivoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val nome: String,
    val valorAlvoCentavos: Long,
    val valorGuardadoCentavos: Long,
    val prazo: Long,
    val prioridade: Int,
)

@Entity(tableName = "dividas")
data class DividaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valorOriginalCentavos: Long,
    val valorAbertoCentavos: Long,
    val taxaJurosMensalBasisPoints: Int, // 1% a.m. = 100 bp
    val parcelasRestantes: Int,
    val valorParcelaCentavos: Long,
)

@Entity(tableName = "orcamento_categorias", primaryKeys = ["categoria", "mesReferencia"])
data class OrcamentoCategoriaEntity(
    val categoria: String,
    val limiteMensalCentavos: Long,
    val mesReferencia: String, // "yyyy-MM"
)

@Entity(tableName = "assinaturas")
data class AssinaturaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valorCentavos: Long,
    val ultimoUso: Long?,
    val status: String, // "ativa" | "cancelada"
)

@Entity(tableName = "mensagens_chat")
data class MensagemChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val papel: String, // "usuario" | "ia"
    val texto: String,
    val timestamp: Long,
)

@Entity(tableName = "uso_provedor_ia", primaryKeys = ["provedor", "data"])
data class UsoProvedorIaEntity(
    val provedor: String,
    val data: String, // "yyyy-MM-dd"
    val quantidadeChamadasHoje: Int,
)

/**
 * Registro de deduplicação das notificações proativas (Fase 5, planning.md
 * §9/§10: "no máximo as notificações relevantes daquele dia, não uma por
 * hora"). [chave] é o id estável do evento — o mesmo id de
 * [com.finai.app.domain.FinanceAlert.id] ou de
 * [com.finai.app.domain.BehaviorPattern.id] — e [ultimoEnvio] é a última data
 * (yyyy-MM-dd) em que uma notificação por esse evento foi de fato entregue.
 * `FinanceCheckWorker` só volta a notificar o mesmo id num dia seguinte.
 */
@Entity(tableName = "notificacoes_enviadas")
data class NotificacaoEnviadaEntity(
    @PrimaryKey val chave: String,
    val ultimoEnvio: String, // "yyyy-MM-dd"
)
