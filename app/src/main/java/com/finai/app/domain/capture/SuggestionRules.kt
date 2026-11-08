package com.finai.app.domain.capture

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CardPayment
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.IncomeKind
import com.finai.app.domain.PaymentMethod
import com.finai.app.domain.TransactionEntry
import com.finai.app.domain.TransactionType
import com.finai.app.domain.importer.DuplicateDetector
import com.finai.app.domain.importer.MerchantClassifier
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import java.time.LocalDate
import kotlin.math.abs

/** `TransacaoEntity.origem` de um lançamento confirmado a partir de uma notificação. */
const val NOTIFICATION_ORIGIN = "notificacao"

/**
 * O que o quadro "Movimentação encontrada" edita antes de salvar. Começa com o que o parser leu
 * ([SuggestionRules.draftOf]); forma de pagamento, conta, cartão e vencimento só existem quando
 * a mensagem disse ou o usuário escolheu.
 */
data class SuggestionDraft(
    val kind: MovementKind,
    val amountCents: Long?,
    val counterpart: String,
    val category: String?,
    /** Nulo enquanto a forma não é conhecida ("Compra aprovada" sem dizer crédito ou débito). */
    val payment: PaymentMethod?,
    /** Conta de débito/Pix, ou "de onde veio" numa receita. */
    val account: String?,
    val card: String?,
    val installments: Int?,
    val date: LocalDate,
    /** Vencimento escolhido para a fatura (nulo = o sugerido pelas faturas guardadas). */
    val dueDate: LocalDate? = null,
    val dueConfirmed: Boolean = false,
) {
    val type: TransactionType get() = if (kind.isIncome) TransactionType.Receita else TransactionType.Gasto
    val isCard: Boolean get() = payment?.isCard == true

    /** Descrição gravada: quem/onde, ou o tipo quando a mensagem não disse. */
    val description: String
        get() = when (kind) {
            MovementKind.PixSent -> if (counterpart.isBlank()) "Pix enviado" else "Pix para $counterpart"
            MovementKind.PixReceived -> if (counterpart.isBlank()) "Pix recebido" else "Pix de $counterpart"
            else -> counterpart.ifBlank { category ?: kind.label }
        }
}

object SuggestionRules {

    fun draftOf(m: ParsedMovement): SuggestionDraft = SuggestionDraft(
        kind = m.kind,
        amountCents = m.amountCents,
        counterpart = m.counterpart,
        category = m.category,
        payment = when (m.kind) {
            MovementKind.CreditPurchase -> PaymentMethod.Credito
            MovementKind.InstallmentPurchase -> PaymentMethod.Parcelado
            MovementKind.DebitPurchase, MovementKind.PixSent -> PaymentMethod.Debito
            else -> null
        },
        // Receita: "de onde veio" é o pagador, quando a mensagem diz. Gasto: só uma conta que a
        // mensagem citou pelo nome — nunca deduzida do app que mandou a notificação.
        account = if (m.kind.isIncome) m.counterpart.ifBlank { null } else m.accountHint?.takeIf { m.kind != MovementKind.CreditPurchase && m.kind != MovementKind.InstallmentPurchase },
        card = m.accountHint?.takeIf { m.kind.isCardPurchase },
        installments = m.installments,
        date = m.date,
    )

    /**
     * O que falta para salvar. [cardDue] é a fatura sugerida para o cartão escolhido
     * ([EntryOptions.resolveCardDue]): só vale sem confirmação quando é "segura".
     */
    fun missing(d: SuggestionDraft, cardDue: EntryOptions.CardDue?): List<MissingField> = buildList {
        if (d.kind.reviewOnly) { add(MissingField.Review); return@buildList }
        val amount = d.amountCents ?: 0
        if (amount <= 0) add(MissingField.Amount)
        if (d.type == TransactionType.Receita) return@buildList
        if (d.category !in TransactionEntry.categories) add(MissingField.Category)
        when {
            d.payment == null -> add(MissingField.PaymentMethod)
            !d.isCard -> if (d.account.isNullOrBlank()) add(MissingField.Account)
            else -> {
                if (d.card.isNullOrBlank()) add(MissingField.Account)
                else if (dueOf(d, cardDue) == null || !dueOk(d, cardDue)) add(MissingField.DueDate)
                if (d.payment == PaymentMethod.Parcelado) {
                    val n = d.installments
                    if (n == null || n !in EntryOptions.MIN_INSTALLMENTS..EntryOptions.MAX_INSTALLMENTS || amount < n) add(MissingField.Installments)
                }
            }
        }
    }

    fun dueOf(d: SuggestionDraft, cardDue: EntryOptions.CardDue?): LocalDate? = d.dueDate ?: cardDue?.date

    private fun dueOk(d: SuggestionDraft, cardDue: EntryOptions.CardDue?): Boolean {
        val due = dueOf(d, cardDue) ?: return false
        if (due.isBefore(d.date)) return false
        return d.dueConfirmed || (d.dueDate == null && cardDue?.confident == true)
    }

    /** O lançamento a gravar, pelo mesmo caminho do "+". Só chamar com [missing] vazio. */
    fun entryOf(d: SuggestionDraft): TransacaoEntity {
        val base = TransacaoEntity(
            data = d.date.toEpochMillis(), descricao = d.description, valorCentavos = requireNotNull(d.amountCents),
            categoria = d.category.orEmpty(), contaOrigem = d.account.orEmpty().trim(), recorrente = false,
            origem = NOTIFICATION_ORIGIN, tipo = d.type.name,
        )
        return if (d.type == TransactionType.Receita) IncomeKind.Avulsa.applyTo(base) else base
    }

    fun cardPaymentOf(d: SuggestionDraft, cardDue: EntryOptions.CardDue?): CardPayment = CardPayment(
        card = requireNotNull(d.card).trim(),
        firstDueDate = requireNotNull(dueOf(d, cardDue)),
        installments = if (d.payment == PaymentMethod.Parcelado) requireNotNull(d.installments) else 1,
    )
}

/**
 * Problema B: a mesma compra que já está gravada por outro caminho (lançamento manual, outra
 * notificação, fatura importada). Só avisa — nunca une nem apaga nada; duas compras iguais e
 * legítimas continuam podendo ser salvas.
 */
object SuggestionMatcher {
    private const val NEAR_DAYS = 2L

    fun similar(
        amountCents: Long,
        installmentCents: Long?,
        date: LocalDate,
        counterpart: String,
        type: TransactionType,
        existing: List<TransacaoEntity>,
    ): List<TransacaoEntity> {
        val merchant = MerchantClassifier.normalizeMerchant(counterpart)
        return existing.filter { t ->
            if (t.tipo != type.name) return@filter false
            val sameValue = t.valorCentavos == amountCents ||
                (installmentCents != null && t.valorCentavos == installmentCents && (t.parcelaNumero ?: 1) == 1)
            if (!sameValue) return@filter false
            if (t.parcelaNumero != null && t.parcelaNumero != 1) return@filter false
            if (abs(t.data.toLocalDate().toEpochDay() - date.toEpochDay()) > NEAR_DAYS) return@filter false
            val other = MerchantClassifier.normalizeMerchant(t.descricao.substringBeforeLast(" · parcela "))
            merchant.isBlank() || other.isBlank() || DuplicateDetector.similarity(merchant, other) >= 0.55 ||
                (merchant.length >= 5 && other.contains(merchant)) || (other.length >= 5 && merchant.contains(other))
        }
    }
}
