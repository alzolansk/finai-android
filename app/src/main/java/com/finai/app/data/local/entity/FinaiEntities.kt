package com.finai.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities matching the model in planning.md §8, one-to-one. Desde a
 * Fase 1 são a fonte de verdade de tudo que as telas mostram, via
 * `FinanceRepository` + os calculators de `domain/`.
 */

@Entity(tableName = "transacoes", indices = [Index("faturaId")])
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
    /** Fatura que liquidará esta compra. Nulo para lançamentos manuais e importações antigas. */
    val faturaId: Long? = null,
    /**
     * Receita fora da renda normal (13º, bônus, restituição) — é o que alimenta a "Linha do
     * tempo do ano" da Início. Sem este campo não havia como separar um 13º de um salário
     * lançado avulso.
     */
    @androidx.room.ColumnInfo(defaultValue = "0") val extra: Boolean = false,
)

/** Uma fatura de cartão, exibida como um único compromisso na Agenda. */
@Entity(tableName = "faturas_cartao", indices = [Index("contaId")])
data class FaturaCartaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Conta a pagar criada para a Agenda; o valor dela é a soma dos itens vinculados. */
    val contaId: Long,
    /** Banco, cartão ou os dois, confirmado pelo usuário quando o documento não os informa. */
    val referencia: String,
    /** Data de fechamento, quando o documento a informou. */
    val fechamento: Long?,
    /** Data de vencimento confirmada antes de persistir. */
    val vencimento: Long,
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
    /** Por que a meta existe, flexibilidade do prazo, o que está fora dela — contexto para a leitura da IA. */
    @androidx.room.ColumnInfo(defaultValue = "''") val descricao: String = "",
    /** Quando o valor guardado alcançou o alvo (epoch millis). Nulo se ainda em andamento. */
    val concluidoEm: Long? = null,
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
    /** Quantidade total de parcelas do contrato (0 = não informado / sem parcelas). */
    @androidx.room.ColumnInfo(defaultValue = "0") val parcelasTotais: Int = 0,
    /**
     * Vencimento (epoch millis) da próxima parcela em aberto. As demais são projetadas
     * mês a mês a partir dela (`DebtSchedule`), sem gravar uma linha por parcela.
     * Nulo em dívidas cadastradas antes da v6 ou sem parcela fixa.
     */
    val proximoVencimento: Long? = null,
    /** Quando a dívida foi quitada (epoch millis) — ela sai da lista ativa e fica no histórico. */
    val quitadaEm: Long? = null,
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
    /** Conversa a que a mensagem pertence (0 = histórico anterior às conversas separadas). */
    @androidx.room.ColumnInfo(defaultValue = "0") val conversaId: Long = 0,
    /**
     * Dados do card que abriu a conversa ("Conversar sobre isso", "Ensaiar a
     * ligação"...). Só a primeira pergunta de uma conversa aberta por botão tem;
     * vai no prompt de toda a conversa, mas não aparece no balão.
     */
    val contexto: String? = null,
)

/** Uma linha da lista "Conversas anteriores" — agregado de [MensagemChatEntity]. */
data class ConversaResumo(
    val conversaId: Long,
    val inicio: Long,
    val ultima: Long,
    val total: Int,
    val titulo: String?,
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
