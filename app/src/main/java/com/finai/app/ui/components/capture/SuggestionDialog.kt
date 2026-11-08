package com.finai.app.ui.components.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.finai.app.data.capture.SuggestionUnavailableException
import com.finai.app.data.capture.isTest
import com.finai.app.data.capture.savedIds
import com.finai.app.data.capture.state
import com.finai.app.data.capture.toMovement
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.SugestaoLancamentoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.EntryReceipt
import com.finai.app.domain.PaymentMethod
import com.finai.app.domain.TransactionType
import com.finai.app.domain.brl2
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.capture.MissingField
import com.finai.app.domain.capture.MovementKind
import com.finai.app.domain.capture.SuggestionDraft
import com.finai.app.domain.capture.SuggestionMatcher
import com.finai.app.domain.capture.SuggestionRules
import com.finai.app.domain.capture.SuggestionState
import com.finai.app.ui.components.PillTag
import com.finai.app.ui.components.SelectChip
import com.finai.app.ui.theme.FinaiColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

/** O que o quadro precisa do app: listas para as escolhas e para conferir repetição. */
class SuggestionContext(
    val accounts: List<String>,
    val cards: List<String>,
    val categories: List<String>,
    val faturas: List<FaturaCartaoEntity>,
    val contas: List<ContaEntity>,
    val transacoes: List<TransacaoEntity>,
)

/** Ações do quadro. As suspensas lançam exceção com mensagem para a tela quando não dá. */
class SuggestionActions(
    val save: suspend (Long, SuggestionDraft, EntryOptions.CardDue?) -> EntryReceipt,
    val ignore: suspend (Long) -> Unit,
    val undo: suspend (Long, EntryReceipt) -> Unit,
    val edit: (SugestaoLancamentoEntity, SuggestionDraft) -> Unit,
    val restore: (Long) -> Unit,
    val viewAgenda: (EntryReceipt?, SugestaoLancamentoEntity) -> Unit,
)

private val DraftSaver = listSaver<SuggestionDraft?, Any?>(
    save = { d ->
        if (d == null) emptyList() else listOf(
            d.kind.name, d.amountCents, d.counterpart, d.category, d.payment?.name, d.account, d.card, d.installments,
            d.date.toString(), d.dueDate?.toString(), d.dueConfirmed,
        )
    },
    restore = { l ->
        if (l.isEmpty()) null else SuggestionDraft(
            kind = MovementKind.valueOf(l[0] as String), amountCents = l[1] as Long?, counterpart = l[2] as String,
            category = l[3] as String?, payment = (l[4] as String?)?.let(PaymentMethod::valueOf), account = l[5] as String?,
            card = l[6] as String?, installments = l[7] as Int?, date = LocalDate.parse(l[8] as String),
            dueDate = (l[9] as String?)?.let(LocalDate::parse), dueConfirmed = l[10] as Boolean,
        )
    },
)

/**
 * "Movimentação encontrada": quadro pequeno no centro, uma sugestão por vez. Nada é gravado até
 * "Salvar lançamento"; "Editar" leva ao formulário do "+" preenchido; "Ignorar" guarda a decisão;
 * o X deixa para depois (a sugestão continua em "Para confirmar"). [suggestion] nulo com
 * [missing] = aberta por uma notificação de algo que não existe mais.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionDialog(
    suggestion: SugestaoLancamentoEntity?,
    missing: Boolean,
    pendingCount: Int,
    position: Int,
    context: SuggestionContext,
    actions: SuggestionActions,
    onLater: () -> Unit,
    onNext: (() -> Unit)?,
) {
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            val maxH = maxHeight
            Column(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxH)
                    .clip(RoundedCornerShape(24.dp))
                    .background(FinaiColors.Surface)
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(24.dp))
                    .semantics { paneTitle = "Movimentação encontrada" },
            ) {
                when {
                    suggestion == null && missing -> Unavailable(null, null, actions, onLater)
                    suggestion == null -> Box(Modifier.fillMaxWidth().height(120.dp))
                    else -> SuggestionBody(suggestion, pendingCount, position, context, actions, onLater, onNext)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SuggestionBody(
    s: SugestaoLancamentoEntity,
    pendingCount: Int,
    position: Int,
    context: SuggestionContext,
    actions: SuggestionActions,
    onLater: () -> Unit,
    onNext: (() -> Unit)?,
) {
    var receipt by remember(s.id) { mutableStateOf<EntryReceipt?>(null) }
    // Resolvida fora deste quadro (outra tela, outro toque): mostra o estado, não o formulário.
    // Sem retorno antecipado: em lambda/função composable ele desalinha os grupos do Compose.
    if (receipt == null && s.state != SuggestionState.Pending) {
        Unavailable(s, s.state, actions, onLater)
    } else {
        SuggestionForm(s, pendingCount, position, context, actions, onLater, onNext, receipt) { receipt = it }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SuggestionForm(
    s: SugestaoLancamentoEntity,
    pendingCount: Int,
    position: Int,
    context: SuggestionContext,
    actions: SuggestionActions,
    onLater: () -> Unit,
    onNext: (() -> Unit)?,
    receipt: EntryReceipt?,
    setReceipt: (EntryReceipt?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember(s.id) { mutableStateOf(false) }
    var error by remember(s.id) { mutableStateOf<String?>(null) }
    val movement = remember(s.id, s.atualizadoEm) { s.toMovement() }
    var draft by rememberSaveable(s.id, stateSaver = DraftSaver) { mutableStateOf<SuggestionDraft?>(SuggestionRules.draftOf(movement)) }
    val d = draft ?: SuggestionRules.draftOf(movement)
    var typingOther by rememberSaveable(s.id) { mutableStateOf(false) }

    val cardDue = if (d.isCard && !d.card.isNullOrBlank()) EntryOptions.resolveCardDue(d.card, d.date, context.faturas, context.contas) else null
    val missingFields = SuggestionRules.missing(d, cardDue)
    val similar = remember(d.amountCents, d.date, d.counterpart, context.transacoes, receipt) {
        val amount = d.amountCents
        if (amount == null || d.kind.reviewOnly || receipt != null) emptyList()
        else SuggestionMatcher.similar(amount, movement.installmentCents, d.date, d.counterpart, d.type, context.transacoes)
    }

    // Topo: título, contador e X ("deixar para depois").
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(top = 8.dp)) {
            Text(
                if (receipt != null) (if (s.isTest) "Teste concluído" else "Lançamento salvo") else "Movimentação encontrada",
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (receipt == null && pendingCount > 1) {
                Text("$position de $pendingCount para confirmar", fontSize = 12.sp, color = FinaiColors.TextTertiary)
            }
        }
        IconButton(onClick = onLater, enabled = !busy) {
            Icon(Icons.Filled.Close, contentDescription = "Fechar e deixar para depois", tint = FinaiColors.TextTertiary)
        }
    }

    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PillTag("Detectado por notificação · ${s.appNome}", FinaiColors.EmeraldSoftBg, FinaiColors.EmeraldDark)
            if (s.isTest) PillTag("Teste do FinAI", FinaiColors.AmberSoftBg, FinaiColors.AmberDark)
            if (d.kind.reviewOnly || movement.ambiguous) PillTag("Precisa revisar", FinaiColors.RoseSoftBg, FinaiColors.RoseDark)
        }

        val done = receipt
        if (done != null) {
            Saved(s, d, done)
        } else {

        // Valor e descrição.
        Column {
            val amount = d.amountCents
            Text(
                amount?.let(::brl2) ?: "Qual é o valor?",
                fontSize = if (amount != null) 32.sp else 20.sp, fontWeight = FontWeight.ExtraBold,
                color = if (amount != null) FinaiColors.TextPrimary else FinaiColors.AmberDark,
                modifier = Modifier.semantics { if (amount != null) contentDescription = EntryOptions.spokenAmount(amount) },
            )
            Text(d.description, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextBody)
            Text(kindLine(d, movement.installmentCents), fontSize = 13.sp, color = FinaiColors.TextTertiary)
        }
        if (movement.ambiguous && movement.candidatesCents.isNotEmpty()) {
            Text("A mensagem tem mais de um valor. Qual é o desta movimentação?", fontSize = 13.sp, color = FinaiColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                movement.candidatesCents.forEach { c ->
                    SelectChip(brl2(c), selected = d.amountCents == c) { draft = d.copy(amountCents = c) }
                }
            }
        }

        if (d.kind.reviewOnly) {
            Notice(reviewText(d.kind), tone = Tone.Rose)
        } else {
            if (d.type == TransactionType.Gasto) {
                // Forma de pagamento: só se a notificação não disse.
                if (d.kind == MovementKind.Purchase) {
                    FieldLabel("Como foi pago?", required = d.payment == null)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip("Débito / Pix", d.payment == PaymentMethod.Debito) { draft = d.copy(payment = PaymentMethod.Debito, card = null) }
                        SelectChip("Crédito", d.payment == PaymentMethod.Credito) { draft = d.copy(payment = PaymentMethod.Credito, account = null, dueConfirmed = false) }
                    }
                }
                if (d.payment != null) {
                    val isCard = d.isCard
                    val current = if (isCard) d.card else d.account
                    FieldLabel(if (isCard) "Qual cartão?" else "Qual conta?", required = current.isNullOrBlank(),
                        hint = movement.cardLastDigits?.let { "A notificação fala do cartão final $it." })
                    val options = (if (isCard) context.cards else context.accounts).filter { it.isNotBlank() }.take(6)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        (options + listOfNotNull(current?.takeIf { c -> c.isNotBlank() && options.none { it.equals(c, true) } })).forEach { name ->
                            SelectChip(name, current.equals(name, ignoreCase = true)) {
                                draft = if (isCard) d.copy(card = name, dueConfirmed = false, dueDate = null) else d.copy(account = name)
                                typingOther = false
                            }
                        }
                        SelectChip("Outro", typingOther) { typingOther = true }
                    }
                    if (typingOther) OtherName(if (isCard) "Nome do cartão" else "Nome da conta") { name ->
                        draft = if (isCard) d.copy(card = name, dueConfirmed = false, dueDate = null) else d.copy(account = name)
                        typingOther = false
                    }
                    if (isCard && !d.card.isNullOrBlank()) {
                        val due = SuggestionRules.dueOf(d, cardDue)
                        val ok = MissingField.DueDate !in missingFields
                        val n = if (d.payment == PaymentMethod.Parcelado) d.installments ?: 1 else 1
                        Text(EntryOptions.invoiceLine(d.card, due, ok, n), fontSize = 13.sp,
                            color = if (ok) FinaiColors.TextSecondary else FinaiColors.AmberDark)
                        when {
                            due == null -> Notice("Primeira compra neste cartão no FinAI: use Editar para informar o vencimento da fatura.", Tone.Amber)
                            !ok -> SelectChip("Confirmar vencimento ${EntryOptions.dayMonth(due)}", d.dueConfirmed) { draft = d.copy(dueConfirmed = true) }
                        }
                    }
                }
                FieldLabel("Categoria", required = MissingField.Category in missingFields)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A escolhida (ou sugerida) vem primeiro, à vista; as outras na ordem de uso recente.
                    val ordered = remember(s.id, context.categories) { listOfNotNull(movement.category) + context.categories.filter { it != movement.category } }
                    ordered.forEach { c -> SelectChip(c, d.category == c) { draft = d.copy(category = c) } }
                }
            } else {
                d.account?.let { Text("De: $it", fontSize = 13.sp, color = FinaiColors.TextSecondary) }
            }
            Text(
                if (d.date == movement.date && !movement.dateExplicit) "Data: ${EntryOptions.dayMonth(d.date)} (dia da notificação)"
                else "Data: ${EntryOptions.dayMonth(d.date)} (escrita na mensagem)",
                fontSize = 13.sp, color = FinaiColors.TextSecondary,
            )
        }

        if (s.possivelRepeticao) Notice("Chegou outra notificação igual há pouco. Se for a mesma compra, toque em Ignorar; se foram duas, salve as duas.", Tone.Amber)
        similar.firstOrNull()?.let { t ->
            Notice("Parecido com \"${t.descricao}\" de ${EntryOptions.dayMonth(t.data.toLocalDate())} (${brl2(t.valorCentavos)}), já lançado. Se for a mesma, ignore esta.", Tone.Amber)
        }
        val blocking = missingFields.filter { it != MissingField.Review }
        if (blocking.isNotEmpty()) {
            Text("Falta: " + blocking.joinToString(" · ") { it.label }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = FinaiColors.AmberDark, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        error?.let { Text(it, fontSize = 13.sp, color = FinaiColors.RoseDark, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        }
        Spacer(Modifier.height(2.dp))
    }

    // Base fixa: salvar, editar, ignorar.
    val done = receipt
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (done != null) {
            if (!s.isTest) Button(
                onClick = { actions.viewAgenda(done, s) }, enabled = !busy, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.InkStrong, contentColor = FinaiColors.OnInkStrong),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Ver na agenda", fontWeight = FontWeight.Bold) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            try { actions.undo(s.id, done); setReceipt(null) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Não foi possível desfazer." }
                            finally { busy = false }
                        }
                    },
                    enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(if (busy) "Desfazendo…" else "Desfazer", fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary) }
                OutlinedButton(
                    onClick = { onNext?.invoke() ?: onLater() }, enabled = !busy, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(if (onNext != null) "Próxima" else "Fechar", fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary) }
            }
            error?.let { Text(it, fontSize = 13.sp, color = FinaiColors.RoseDark) }
        } else {
        val canSave = missingFields.isEmpty() && !busy
        Button(
            onClick = {
                busy = true; error = null
                scope.launch {
                    try { setReceipt(actions.save(s.id, d, cardDue)) }
                    catch (e: CancellationException) { throw e }
                    catch (e: SuggestionUnavailableException) { error = e.message }
                    catch (e: IllegalArgumentException) { error = e.message ?: "Confira os dados." }
                    catch (e: Exception) { error = "Não foi possível salvar. A sugestão continua aqui para tentar de novo." }
                    finally { busy = false }
                }
            },
            enabled = canSave, shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FinaiColors.EmeraldButton, contentColor = Color.White,
                disabledContainerColor = FinaiColors.SurfaceMuted, disabledContentColor = FinaiColors.TextMuted,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(if (busy) "Salvando…" else "Salvar lançamento", fontWeight = FontWeight.Bold) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { actions.edit(s, d) }, enabled = !busy, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Editar", fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary) }
            OutlinedButton(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try { actions.ignore(s.id); onNext?.invoke() ?: onLater() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "Não foi possível ignorar agora." }
                        finally { busy = false }
                    }
                },
                enabled = !busy, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, FinaiColors.RoseSoftStroke),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Ignorar", fontWeight = FontWeight.Bold, color = FinaiColors.RoseDark) }
        }
        }
    }
}

@Composable
private fun Saved(s: SugestaoLancamentoEntity, d: SuggestionDraft, r: EntryReceipt) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).background(FinaiColors.EmeraldButton, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
        }
        Text(
            if (s.isTest) "Teste do FinAI: nada foi lançado nas suas finanças."
            else listOfNotNull(d.amountCents?.let(::brl2), d.description, d.category).joinToString(" · ") +
                if (d.isCard) " · na fatura ${d.card}" else "",
            fontSize = 14.sp, color = FinaiColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
        )
        if (!s.isTest && r.transactionIds.size > 1) {
            Text("${r.transactionIds.size} parcelas, uma em cada fatura.", fontSize = 13.sp, color = FinaiColors.TextTertiary)
        }
    }
}

/** Aberta pela notificação, mas já resolvida (ou apagada): diz o que aconteceu. */
@Composable
private fun Unavailable(s: SugestaoLancamentoEntity?, state: SuggestionState?, actions: SuggestionActions, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Movimentação encontrada", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
            modifier = Modifier.semantics { heading() })
        Text(
            when (state) {
                SuggestionState.Confirmed -> "Esta movimentação já foi salva" + (s?.valorCentavos?.let { " (${brl2(it)})" } ?: "") + "."
                SuggestionState.Ignored -> "Você ignorou esta movimentação. Se mudou de ideia, ela volta para \"Para confirmar\"."
                else -> "Esta movimentação não existe mais (foi apagada ou já saiu da lista)."
            },
            fontSize = 14.sp, color = FinaiColors.TextSecondary,
        )
        if (s != null && state == SuggestionState.Confirmed && !s.isTest && s.savedIds.isNotEmpty()) {
            Button(onClick = { actions.viewAgenda(null, s) }, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.InkStrong, contentColor = FinaiColors.OnInkStrong),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Ver na agenda", fontWeight = FontWeight.Bold) }
        }
        if (s != null && state == SuggestionState.Ignored) {
            OutlinedButton(onClick = { actions.restore(s.id) }, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Voltar para Para confirmar", fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary) }
        }
        OutlinedButton(onClick = onClose, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Fechar", fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        }
    }
}

@Composable
private fun FieldLabel(text: String, required: Boolean, hint: String? = null) {
    Column {
        Text(
            text + if (required) " · obrigatório" else "", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (required) FinaiColors.AmberDark else FinaiColors.TextPrimary,
        )
        hint?.let { Text(it, fontSize = 12.sp, color = FinaiColors.TextTertiary) }
    }
}

@Composable
private fun OtherName(label: String, onDone: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(40) }, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text.trim()) }),
            modifier = Modifier.weight(1f),
        )
        SelectChip("Usar", false) { if (text.isNotBlank()) onDone(text.trim()) }
    }
}

private enum class Tone { Amber, Rose }

@Composable
private fun Notice(text: String, tone: Tone) {
    Text(
        text, fontSize = 13.sp, lineHeight = 18.sp,
        color = if (tone == Tone.Rose) FinaiColors.RoseDeep else FinaiColors.AmberDeep,
        modifier = Modifier.fillMaxWidth()
            .background(if (tone == Tone.Rose) FinaiColors.RoseSoftBg else FinaiColors.AmberSoftBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}

private fun kindLine(d: SuggestionDraft, installmentCents: Long?): String = when {
    d.kind == MovementKind.InstallmentPurchase && d.installments != null ->
        "Compra parcelada · ${d.installments}× de ${brl2(installmentCents ?: ((d.amountCents ?: 0) / d.installments))}"
    d.kind == MovementKind.Purchase && d.payment == null -> "Compra · forma de pagamento não informada"
    d.kind == MovementKind.Purchase -> "Compra no ${if (d.isCard) "crédito" else "débito / Pix"}"
    d.kind == MovementKind.PixReceived -> "Pix recebido · vira receita avulsa"
    else -> d.kind.label
}

private fun reviewText(kind: MovementKind): String = when (kind) {
    MovementKind.Refund -> "Estorno ou cancelamento não vira gasto nem receita sozinho. Se quiser registrar, use Editar; se a compra original está lançada, você pode excluí-la na Agenda."
    MovementKind.InvoicePayment -> "Pagamento de fatura: marque a fatura como paga na Agenda. Lançar como gasto contaria as compras duas vezes."
    MovementKind.Transfer -> "Transferência: use Editar para dizer de qual conta saiu e para onde foi."
    else -> "Confira e use Editar para registrar do jeito certo."
}
