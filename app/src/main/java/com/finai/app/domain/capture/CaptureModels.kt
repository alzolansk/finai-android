package com.finai.app.domain.capture

import java.time.LocalDate

/**
 * Lançamento assistido por notificação: o que o listener leu, o que o parser entendeu e o que
 * ainda falta para virar lançamento. Kotlin puro, sem Android: o serviço só preenche
 * [CapturedNotification] e a tela só desenha o que sai daqui.
 */

/**
 * Os campos de texto de uma notificação de um app escolhido pelo usuário. Nada além disso é
 * lido (sem ícone, sem ações, sem extras que não sejam texto). [systemKey] é a chave que o
 * Android dá à notificação (pacote + id + tag): a mesma em reenvios e atualizações.
 */
data class CapturedNotification(
    val packageName: String,
    val appLabel: String,
    val systemKey: String,
    val postedAt: Long,
    val title: String?,
    val text: String?,
    val bigText: String? = null,
    val subText: String? = null,
    /** Linhas do estilo caixa de entrada (várias mensagens numa notificação só). */
    val lines: List<String> = emptyList(),
) {
    /**
     * O corpo, sem repetir: o texto expandido normalmente contém o texto curto, e aí só ele
     * vale. Título vem na frente, porque é onde vários bancos põem "Compra aprovada".
     */
    fun body(): String {
        val main = listOfNotNull(text, bigText).map { it.trim() }.filter { it.isNotEmpty() }
        val merged = when {
            main.size == 2 && main[1].contains(main[0]) -> listOf(main[1])
            main.size == 2 && main[0].contains(main[1]) -> listOf(main[0])
            else -> main
        }
        return (listOfNotNull(title?.trim()?.takeIf { it.isNotEmpty() }) + merged + listOfNotNull(subText?.trim()?.takeIf { it.isNotEmpty() }))
            .joinToString("\n")
    }
}

/** Natureza da movimentação reconhecida. [reviewOnly] = o app não lança sozinho; pede revisão. */
enum class MovementKind(val label: String, val reviewOnly: Boolean) {
    /** "Compra aprovada" sem dizer se foi crédito ou débito: a forma é perguntada. */
    Purchase("Compra", false),
    CreditPurchase("Compra no crédito", false),
    DebitPurchase("Compra no débito", false),
    InstallmentPurchase("Compra parcelada", false),
    PixSent("Pix enviado", false),
    PixReceived("Pix recebido", false),
    Refund("Estorno ou cancelamento", true),
    InvoicePayment("Pagamento de fatura", true),
    Transfer("Transferência", true);

    val isIncome: Boolean get() = this == PixReceived
    val isCardPurchase: Boolean get() = this == CreditPurchase || this == InstallmentPurchase
}

/** Por que uma notificação foi descartada sem virar sugestão. */
enum class IgnoreReason(val label: String) {
    Declined("Compra recusada"),
    Promotion("Propaganda ou oferta"),
    LimitInfo("Aviso de limite ou saldo"),
    NoAmount("Sem valor"),
    NoMovement("Sem movimentação identificável"),
}

/** O que ainda falta para salvar. A tela nunca grava com algum destes em aberto. */
enum class MissingField(val label: String) {
    Amount("Valor"),
    PaymentMethod("Forma de pagamento"),
    Account("Conta ou cartão"),
    Category("Categoria"),
    DueDate("Vencimento da fatura"),
    Installments("Número de parcelas"),
    Review("Tipo de lançamento"),
}

/** O que o parser tirou do texto. Nada aqui é inventado: campo ausente fica nulo. */
data class ParsedMovement(
    val kind: MovementKind,
    /** Valor total da movimentação. Nulo quando a mensagem tem vários valores sem explicar qual é qual. */
    val amountCents: Long?,
    /** Valores encontrados, para o usuário escolher quando [ambiguous]. */
    val candidatesCents: List<Long> = emptyList(),
    /** Estabelecimento, pagador ou destinatário, como veio no texto. Vazio se não identificado. */
    val counterpart: String = "",
    val date: LocalDate,
    /** false = [date] é o dia em que a notificação chegou, não uma data escrita na mensagem. */
    val dateExplicit: Boolean,
    val installments: Int? = null,
    val installmentCents: Long? = null,
    /** "final 1234", quando a mensagem diz. Só para ajudar a escolher o cartão; não é gravado no lançamento. */
    val cardLastDigits: String? = null,
    /** Conta ou cartão já conhecido que a mensagem cita pelo nome. */
    val accountHint: String? = null,
    /** Uma das categorias de gasto, quando a regra local reconhece o estabelecimento. */
    val category: String? = null,
    /** Vários valores e nenhuma regra explica qual é o da compra. */
    val ambiguous: Boolean = false,
)

sealed interface ParseOutcome {
    /** [signature] identifica o conteúdo (para reenvio/atualização); [lineIndex] só em notificação agrupada. */
    data class Movement(val movement: ParsedMovement, val signature: String, val lineIndex: Int? = null) : ParseOutcome
    data class Ignored(val reason: IgnoreReason) : ParseOutcome
}

/** Estado de uma sugestão gravada. */
enum class SuggestionState(val key: String) {
    Pending("pendente"), Confirmed("confirmada"), Ignored("ignorada");

    companion object {
        fun of(key: String): SuggestionState = entries.firstOrNull { it.key == key } ?: Pending
    }
}

/** De onde veio a sugestão. As duas de teste nunca mexem nas finanças nem vão para o backup. */
enum class SuggestionOrigin(val key: String) {
    Notification("notificacao"),
    /** "Simular a leitura": texto digitado na área de testes, sem passar pelo Android. */
    Simulation("simulacao"),
    /** "Enviar notificação de teste": notificação real do FinAI, lida pelo listener. */
    TestNotification("teste");

    val isTest: Boolean get() = this != Notification

    companion object {
        fun of(key: String): SuggestionOrigin = entries.firstOrNull { it.key == key } ?: Notification
    }
}
