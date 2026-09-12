package com.finai.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities matching the model in planning.md §8, one-to-one. These are
 * scaffolding for Phase 0 — no repository/DAO wires them to the UI yet;
 * screens still run on FinaiFixtures. Phase 1 replaces the fixtures with
 * Flow<List<...>> queries backed by these tables.
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
    val origem: String, // "manual" | "importado"
)

@Entity(tableName = "contas")
data class ContaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valorCentavos: Long,
    val vencimento: Long,
    val status: String, // "pago" | "pendente" | "atrasado" | "vence_hoje" | "previsto"
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
