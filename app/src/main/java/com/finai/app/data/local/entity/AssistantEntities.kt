package com.finai.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Estado de uma conversa do assistente que precisa sobreviver ao processo: a proposta à espera
 * de confirmação, a pergunta pendente, as entidades em foco ("essa dívida") e o último recibo.
 * Nenhum valor financeiro mora aqui: saldo e dívida vêm sempre dos dados atuais.
 */
@Entity(tableName = "sessoes_assistente")
data class SessaoAssistenteEntity(
    @PrimaryKey val conversaId: Long,
    val estado: String,
    val atualizadoEm: Long,
)

/**
 * Uma escrita feita pelo assistente: a chave impede que a mesma operação autorizada rode duas
 * vezes (retentativa, retomada do app) e o recibo guarda o antes e o depois para o Desfazer.
 */
@Entity(tableName = "acoes_assistente", indices = [Index(value = ["chave"], unique = true)])
data class AcaoAssistenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chave: String,
    val conversaId: Long,
    val tipo: String,
    val resumo: String,
    val recibo: String,
    /** "applied" | "undone" */
    val estado: String,
    val criadoEm: Long,
)

/**
 * Acompanhamento pedido pelo usuário no chat ("avise quando eu puder quitar"). Só existe por
 * pedido explícito; pode ser pausado ou cancelado.
 */
@Entity(tableName = "acompanhamentos")
data class AcompanhamentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "quitacao" | "compra" | "categoria" | "saldo" — ver [com.finai.app.domain.assistant.WatchRules]. */
    val tipo: String,
    val alvo: String,
    val alvoId: Long? = null,
    val valorCentavos: Long,
    val descricao: String,
    /** "ativo" | "pausado" */
    val estado: String,
    val criadoEm: Long,
    /** Dia da última avaliação (yyyy-MM-dd) e o que ela deu ("atendido" | "aguardando"). */
    val ultimaAvaliacao: String? = null,
    val ultimoResultado: String? = null,
)

/**
 * Plano do mês que o usuário aceitou, como estava naquele dia. É contra ele que "estou seguindo
 * o plano?" compara o realizado: o previsto não é reconstruído com os dados de hoje.
 */
@Entity(tableName = "planos")
data class PlanoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "yyyy-MM" */
    val mes: String,
    val criadoEm: Long,
    /** [com.finai.app.domain.assistant.SavedPlan.encode] */
    val conteudo: String,
)
