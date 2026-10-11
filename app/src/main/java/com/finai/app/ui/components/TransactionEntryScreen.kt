package com.finai.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CardPayment
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.EntryReceipt
import com.finai.app.domain.GoalReceipt
import com.finai.app.domain.IncomeKind
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.PaymentMethod
import com.finai.app.domain.TransactionEntry
import com.finai.app.domain.TransactionType
import com.finai.app.domain.TransferTarget
import com.finai.app.domain.toEpochMillis
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.finaiTween
import com.finai.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Getters, não valores: acompanham o tema claro/escuro (ver FinaiColors).
internal val EntryInk get() = FinaiColors.TextPrimary
internal val EntryMuted get() = FinaiColors.TextMuted
internal val EntryPaper get() = FinaiColors.Background
internal val EntryLine get() = FinaiColors.BorderSubtle
internal val EntryShape = RoundedCornerShape(16.dp)
internal val EntryFont = com.finai.app.ui.theme.FinaiFontFamily

// Curvas do playbook (página "Curvas e durações").
/** Indicadores dos seletores (500 ms) e categoria selecionada (300 ms): mola com leve passagem. */
private val SpringyEasing = CubicBezierEasing(.34f, 1.45f, .55f, 1f)
/** Teclado e painel inferior: suave. */
private val SmoothEasing = CubicBezierEasing(.2f, .8f, .2f, 1f)
/** Count-up da reserva: ease-out cúbico, 1 − (1 − t)³. */
private val CubicOut = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) }

// Superfícies do lançamento: no escuro, as do playbook; no claro, o mesmo layout nas claras.
private val TileBg get() = if (FinaiColors.isDark) Color(0xFF18181B) else FinaiColors.Surface
private val TileIconBg get() = if (FinaiColors.isDark) Color(0xFF27272A) else FinaiColors.SurfaceMuted
private val TileBorder get() = if (FinaiColors.isDark) Color.Transparent else FinaiColors.BorderFaint
private val SelectedBg get() = if (FinaiColors.isDark) FinaiColors.Emerald.copy(alpha = .16f) else FinaiColors.EmeraldSoftBg
private val SelectedIconBg get() = if (FinaiColors.isDark) FinaiColors.Emerald.copy(alpha = .26f) else FinaiColors.EmeraldSoftStroke
private val SelectedText get() = FinaiColors.EmeraldDark
private val TrackColor get() = if (FinaiColors.isDark) Color(0xFF27272A) else Color(0xFFE4E4E7)

/**
 * Lançamento de transação (playbook `finai-gasto-playbook.pdf`): topo com X e tipo, valor
 * grande, conteúdo rolável (categoria ou destino, bloco do tipo e pílulas) e, na base, salvar
 * de 56 dp e o teclado próprio. Depois de salvar, a base vira a confirmação e um aviso no topo
 * oferece "Desfazer", que reverte o recibo inteiro e devolve o formulário preenchido.
 */
@Composable
fun TransactionEntryScreen(
    visible: Boolean,
    /** Contas de débito/Pix, a usada por último primeiro ([EntryOptions.recentAccounts]). */
    accounts: List<String>,
    incomeSources: List<String> = emptyList(),
    initialType: TransactionType = TransactionType.Gasto,
    /** Abre já marcada como entrada extra (o "+" da Linha do tempo do ano). */
    initialExtra: Boolean = false,
    /** Abre já marcada como renda principal ("Informar renda" da Início, Fase 7 item 5). */
    initialMainIncome: Boolean = false,
    onDismiss: () -> Unit,
    onSave: suspend (TransacaoEntity) -> EntryReceipt,
    onViewEntry: (EntryReceipt) -> Unit,
    onSaveCard: (suspend (TransacaoEntity, CardPayment) -> EntryReceipt)? = null,
    cards: List<String> = emptyList(),
    /** As nove categorias na ordem de uso recente ([EntryOptions.categoriesByRecentUse]). */
    categories: List<String> = TransactionEntry.categories,
    objetivos: List<ObjetivoEntity> = emptyList(),
    faturas: List<FaturaCartaoEntity> = emptyList(),
    contas: List<ContaEntity> = emptyList(),
    dailySpendingCents: Long? = null,
    onSaveContribution: (suspend (TransacaoEntity) -> EntryReceipt)? = null,
    onUndo: suspend (EntryReceipt) -> Unit = {},
    /** "Criar reserva"/"Criar objetivo" quando o destino não tem opção. */
    onCreateGoal: ((reserve: Boolean) -> Unit)? = null,
    /** A confirmação fechou (Lançar outro, Ver na agenda ou X): solta a comemoração guardada. */
    onFinished: () -> Unit = {},
) {
    val typography = MaterialTheme.typography
    MaterialTheme(typography = typography.copy(
        bodyLarge = typography.bodyLarge.copy(fontFamily = EntryFont),
        bodyMedium = typography.bodyMedium.copy(fontFamily = EntryFont),
        bodySmall = typography.bodySmall.copy(fontFamily = EntryFont),
        labelLarge = typography.labelLarge.copy(fontFamily = EntryFont),
        titleLarge = typography.titleLarge.copy(fontFamily = EntryFont),
        headlineSmall = typography.headlineSmall.copy(fontFamily = EntryFont),
    )) {
        ProvideTextStyle(TextStyle(fontFamily = EntryFont, fontSize = 14.sp)) {
            TransactionEntryContent(
                EntryInputs(visible, accounts, incomeSources, cards, categories, objetivos, faturas, contas, dailySpendingCents),
                initialType, initialExtra, initialMainIncome, onDismiss, onSave, onViewEntry, onSaveCard,
                onSaveContribution, onUndo, onCreateGoal, onFinished,
            )
        }
    }
}

/** O que a tela lê do app, sem estado próprio. */
private class EntryInputs(
    val visible: Boolean,
    val accounts: List<String>,
    val incomeSources: List<String>,
    val cards: List<String>,
    val categories: List<String>,
    val objetivos: List<ObjetivoEntity>,
    val faturas: List<FaturaCartaoEntity>,
    val contas: List<ContaEntity>,
    val dailySpendingCents: Long?,
)

private fun amountText(cents: String): String = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format((cents.toLongOrNull() ?: 0) / 100.0)

private fun brlCents(cents: Long): String = "R$ " + amountText(cents.toString())

/** O recibo sobrevive à rotação: a confirmação (e o "Desfazer") não pode sumir ao girar. */
private val ReceiptSaver = Saver<EntryReceipt?, Any>(
    save = { r ->
        r?.let {
            arrayListOf<Any?>(
                it.type.name, ArrayList(it.transactionIds), it.agendaDate.toString(), ArrayList(it.createdInvoiceIds),
                ArrayList(it.touchedInvoiceIds), it.invoiceContaId ?: -1L,
                it.goal?.let { g -> arrayListOf<Any?>(g.goalId, g.name, g.beforeCents, g.afterCents, g.targetCents, g.celebrate) },
            )
        }
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        val l = saved as List<Any?>
        @Suppress("UNCHECKED_CAST")
        val g = l[6] as List<Any?>?
        EntryReceipt(
            type = TransactionType.valueOf(l[0] as String),
            transactionIds = (l[1] as List<*>).map { it as Long },
            agendaDate = LocalDate.parse(l[2] as String),
            createdInvoiceIds = (l[3] as List<*>).map { it as Long },
            touchedInvoiceIds = (l[4] as List<*>).map { it as Long },
            invoiceContaId = (l[5] as Long).takeIf { it >= 0 },
            goal = g?.let { GoalReceipt(it[0] as Long, it[1] as String, it[2] as Long, it[3] as Long, it[4] as Long, it[5] as Boolean) },
        )
    },
)

/** Kept mounted by the app so Back preserves the draft; saveable state also survives rotation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionEntryContent(
    inputs: EntryInputs,
    initialType: TransactionType,
    initialExtra: Boolean,
    initialMainIncome: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (TransacaoEntity) -> EntryReceipt,
    onViewEntry: (EntryReceipt) -> Unit,
    onSaveCard: (suspend (TransacaoEntity, CardPayment) -> EntryReceipt)?,
    onSaveContribution: (suspend (TransacaoEntity) -> EntryReceipt)?,
    onUndo: suspend (EntryReceipt) -> Unit,
    onCreateGoal: ((Boolean) -> Unit)?,
    onFinished: () -> Unit,
) {
    val visible = inputs.visible
    val today = LocalDate.now()
    // Rascunho: os mesmos campos de antes, mais pagamento, parcelas, tipo de receita e destino.
    var cents by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var dateString by rememberSaveable { mutableStateOf(today.toString()) }
    var recurring by rememberSaveable { mutableStateOf(false) }
    var typeName by rememberSaveable { mutableStateOf(TransactionType.Gasto.name) }
    var payName by rememberSaveable { mutableStateOf(PaymentMethod.Debito.name) }
    var card by rememberSaveable { mutableStateOf("") }
    var installments by rememberSaveable { mutableIntStateOf(EntryOptions.MIN_INSTALLMENTS) }
    /** Vencimento escolhido à mão; vazio = o sugerido pelas faturas. */
    var dueString by rememberSaveable { mutableStateOf("") }
    var dueConfirmed by rememberSaveable { mutableStateOf(false) }
    var incomeName by rememberSaveable { mutableStateOf(IncomeKind.DEFAULT.name) }
    var targetName by rememberSaveable { mutableStateOf(TransferTarget.Reserva.name) }
    /** Objetivo escolhido; −1 = nenhum (com uma opção só, ela vale sem toque). */
    var goalId by rememberSaveable { mutableLongStateOf(-1L) }
    var destAccount by rememberSaveable { mutableStateOf("") }
    var receipt by rememberSaveable(stateSaver = ReceiptSaver) { mutableStateOf<EntryReceipt?>(null) }
    var undoing by remember { mutableStateOf(false) }
    var undoError by remember { mutableStateOf<String?>(null) }

    val type = TransactionType.valueOf(typeName)
    val pay = PaymentMethod.valueOf(payName)
    val incomeKind = IncomeKind.valueOf(incomeName)
    val target = TransferTarget.valueOf(targetName)
    val date = LocalDate.parse(dateString)
    val isGasto = type == TransactionType.Gasto
    val isReceita = type == TransactionType.Receita
    val isTransfer = type == TransactionType.Transferencia
    val isCard = isGasto && pay.isCard && onSaveCard != null
    val count = if (isCard && pay == PaymentMethod.Parcelado) installments else 1
    val valueCents = cents.toLongOrNull() ?: 0L

    // Só aplica o tipo pedido de fora (ex.: "+" da linha do tempo pedindo Receita) quando o
    // rascunho está vazio — senão reabrir a tela com "Voltar preserva rascunho" trocaria o
    // tipo de um lançamento que o usuário já estava preenchendo.
    LaunchedEffect(visible) {
        if (visible && receipt == null && cents.isEmpty() && category.isEmpty() && description.isBlank()) {
            typeName = initialType.name
            if (initialType == TransactionType.Receita) incomeName = when {
                initialExtra -> IncomeKind.Extra.name
                initialMainIncome -> IncomeKind.Salario.name
                else -> IncomeKind.DEFAULT.name
            }
        }
    }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    val success = receipt != null
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val reducedMotion = rememberReducedMotion()

    // Receita usa "de onde veio" (empregador, cliente...), não a conta de gasto/transferência.
    val availableAccounts = if (isReceita) inputs.incomeSources.filter { it.isNotBlank() }.distinct()
        else (inputs.accounts.filter { it.isNotBlank() } + EntryOptions.FALLBACK_ACCOUNT).distinct()
    val selectedAccount = account.ifBlank { availableAccounts.firstOrNull() ?: "" }

    // Fatura: sugerida pelos dados guardados; a tela só aceita salvar com ela confirmada.
    val suggestedDue = remember(card, dateString, inputs.faturas, inputs.contas) {
        EntryOptions.resolveCardDue(card, date, inputs.faturas, inputs.contas)
    }
    val dueDate = dueString.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: suggestedDue.date
    val dueOk = dueDate != null && !dueDate.isBefore(date) &&
        (dueConfirmed || (dueString.isBlank() && suggestedDue.confident))
    val plan = EntryOptions.Installments(count, valueCents)

    // Destino da transferência.
    val goalOptions = when (target) {
        TransferTarget.Reserva -> EntryOptions.reserves(inputs.objetivos)
        TransferTarget.Objetivo -> EntryOptions.goals(inputs.objetivos)
        TransferTarget.Conta -> emptyList()
    }
    val selectedGoal = goalOptions.firstOrNull { it.id == goalId }
        ?: EntryOptions.autoSelected(goalOptions)?.let { id -> goalOptions.first { it.id == id } }
    val toGoal = isTransfer && target != TransferTarget.Conta

    val valid = !saving && !success && valueCents > 0 && when (type) {
        TransactionType.Gasto -> category in TransactionEntry.categories &&
            (!isCard || (card.isNotBlank() && dueOk && plan.valid))
        TransactionType.Receita -> true
        TransactionType.Transferencia -> if (toGoal) selectedGoal != null && onSaveContribution != null
            else destAccount.isNotBlank() && !destAccount.trim().equals(selectedAccount.trim(), ignoreCase = true)
    }
    val saveLabel = when {
        saving -> "Salvando…"
        isTransfer && target == TransferTarget.Reserva -> "Guardar na reserva"
        isTransfer && target == TransferTarget.Objetivo -> "Guardar no objetivo"
        else -> "Salvar ${type.label.lowercase(Locale("pt", "BR"))}"
    }

    var amountFont by remember(cents, LocalConfiguration.current.screenWidthDp, LocalDensity.current.fontScale) {
        mutableFloatStateOf(if (amountText(cents).length > 9) 42f else 54f)
    }
    // Description uses the system IME; the custom keypad must yield to it instead of stacking on top.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // O teclado recolhe ao tocar em categoria, pagamento ou pílula e volta ao tocar no valor.
    var amountEditing by rememberSaveable { mutableStateOf(true) }
    val showKeypad = amountEditing && !imeVisible && panel == null && !success

    fun openPanel(name: String) {
        if (saving || success) return
        focus.clearFocus(); amountEditing = false; panel = name
    }

    fun clearDraft(keepType: Boolean) {
        cents = ""; category = ""; description = ""; account = ""; dateString = LocalDate.now().toString()
        recurring = false; payName = PaymentMethod.Debito.name; card = ""; installments = EntryOptions.MIN_INSTALLMENTS
        dueString = ""; dueConfirmed = false; incomeName = IncomeKind.DEFAULT.name; goalId = -1L; destAccount = ""
        if (!keepType) { typeName = TransactionType.Gasto.name; targetName = TransferTarget.Reserva.name }
        receipt = null; error = null; undoError = null; amountEditing = true; panel = null
    }

    fun close() {
        focus.clearFocus()
        if (panel != null) { panel = null; return }
        if (saving || undoing) return
        if (success) { clearDraft(keepType = false); onFinished() }
        onDismiss()
    }
    BackHandler(visible) { close() }

    fun selectType(t: TransactionType) {
        if (t == type) return
        typeName = t.name
        // Trocar de tipo não carrega uma conta/cartão para dentro de "de onde veio" nem o contrário.
        account = ""; recurring = false
    }

    fun save() {
        if (saving || success || !valid) return
        focus.clearFocus()
        saving = true; error = null
        val base = TransacaoEntity(
            data = date.toEpochMillis(), descricao = description.trim(), valorCentavos = valueCents,
            categoria = category, contaOrigem = selectedAccount, recorrente = false, origem = "manual", tipo = type.name,
        )
        scope.launch {
            try {
                receipt = when (type) {
                    TransactionType.Gasto -> {
                        val entry = base.copy(descricao = base.descricao.ifEmpty { category },
                            recorrente = recurring && pay == PaymentMethod.Debito)
                        if (isCard) requireNotNull(onSaveCard)(entry, CardPayment(card.trim(), requireNotNull(dueDate), count))
                        else onSave(entry)
                    }
                    TransactionType.Receita -> onSave(incomeKind.applyTo(base).copy(
                        descricao = base.descricao.ifEmpty { incomeDescription(incomeKind) }))
                    TransactionType.Transferencia -> if (toGoal) {
                        val goal = requireNotNull(selectedGoal)
                        requireNotNull(onSaveContribution)(base.copy(
                            descricao = base.descricao.ifEmpty { "Aporte · ${goal.nome}" },
                            categoria = TransactionType.Transferencia.label, objetivoId = goal.id, contaDestino = goal.nome))
                    } else onSave(base.copy(
                        descricao = base.descricao.ifEmpty { "Para ${destAccount.trim()}" },
                        categoria = TransactionType.Transferencia.label, contaDestino = destAccount.trim()))
                }
                undoError = null
            }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { error = e.message ?: "Confira os dados do lançamento." }
            catch (e: Exception) { error = "Não foi possível salvar. Tente novamente." }
            finally { saving = false }
        }
    }

    fun undo() {
        val r = receipt ?: return
        if (undoing) return
        undoing = true; undoError = null
        scope.launch {
            try {
                onUndo(r)
                // Volta ao formulário preenchido: o rascunho nunca foi limpo.
                receipt = null; amountEditing = false
            }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { undoError = e.message ?: "Não foi possível desfazer." }
            catch (e: Exception) { undoError = "Não foi possível desfazer. Tente novamente." }
            finally { undoing = false }
        }
    }

    AnimatedVisibility(visible, enter = fadeIn(tween(if (reducedMotion) 0 else 260)) + slideInVertically(tween(if (reducedMotion) 0 else 260)) { it / 12 }, exit = fadeOut(tween(if (reducedMotion) 0 else 160))) {
        Box(Modifier.fillMaxSize().background(EntryPaper).pointerInput(Unit) {
            // The dedicated layer also owns empty-space taps, so the app behind it cannot react.
            detectTapGestures(onTap = { focus.clearFocus() })
        }) {
        BoxWithConstraints(Modifier.fillMaxSize()
            // Excludes the IME here so maxWidth/maxHeight (used below to pick the
            // portrait/landscape split) stay stable while the keyboard animates in and out;
            // the IME inset itself is still consumed further down via imePadding().
            .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))) {
            val landscape = maxWidth > maxHeight
            val form: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize().then(if (panel != null) Modifier.blur(4.dp) else Modifier)) {
                    // 01 · Topo: X à esquerda e o seletor de tipo, fora da rolagem.
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { close() }, enabled = !saving && !undoing) {
                            EntryGlyph("close", EntryInk, "Fechar lançamento")
                        }
                        EntryTypeTabs(type, enabled = !saving && !success, modifier = Modifier.weight(1f)) { selectType(it) }
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).alpha(if (success) .45f else 1f)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 02 · Valor.
                        AmountBlock(
                            cents, type, amountFont, onOverflow = { if (amountFont > 18f) amountFont *= .9f },
                            helpLine = when {
                                isCard && pay == PaymentMethod.Parcelado && plan.valid -> "${plan.count}× de ${brlCents(plan.baseCents)}"
                                cents.isEmpty() && showKeypad -> "Toque nos números abaixo"
                                !showKeypad -> "Toque para editar o valor"
                                else -> ""
                            },
                            helpAccent = isCard && pay == PaymentMethod.Parcelado && plan.valid,
                            enabled = !saving && !success,
                        ) { focus.clearFocus(); panel = null; amountEditing = true }

                        // 03 · Conteúdo: categoria ou destino, bloco do tipo e pílulas.
                        when (type) {
                            TransactionType.Gasto -> {
                                CategoryStrip(inputs.categories, category, enabled = !saving && !success) {
                                    category = it; amountEditing = false; focus.clearFocus()
                                }
                                if (onSaveCard != null) {
                                    PaymentSelector(pay, enabled = !saving && !success) { p ->
                                        payName = p.name; amountEditing = false
                                        if (p.isCard) recurring = false
                                        if (p.isCard && card.isBlank()) card = inputs.cards.firstOrNull().orEmpty()
                                    }
                                }
                                if (isCard && pay == PaymentMethod.Credito) {
                                    InvoiceLine(EntryOptions.invoiceLine(card, dueDate, dueOk, 1), needsAction = !dueOk) {
                                        openPanel(if (card.isBlank()) "card" else "due")
                                    }
                                }
                                if (isCard && pay == PaymentMethod.Parcelado) {
                                    InstallmentCard(plan, valueCents, enabled = !saving && !success,
                                        line = EntryOptions.invoiceLine(card, dueDate, dueOk, count), needsAction = !dueOk,
                                        onChange = { installments = it; amountEditing = false },
                                        onLineClick = { openPanel(if (card.isBlank()) "card" else "due") })
                                }
                            }
                            TransactionType.Receita -> IncomeKindSelector(incomeKind, enabled = !saving && !success) {
                                incomeName = it.name; amountEditing = false
                            }
                            TransactionType.Transferencia -> {
                                DestinationStrip(target, enabled = !saving && !success) {
                                    targetName = it.name; goalId = -1L; amountEditing = false
                                }
                                if (target == TransferTarget.Conta) {
                                    EntryField("transfer", "Para", destAccount.ifBlank { "Escolher conta" }, destAccount.isBlank()) { openPanel("dest") }
                                } else {
                                    GoalDestinations(
                                        // Na confirmação o Room já tem o novo saldo: a prévia mostra o estado atual.
                                        target, goalOptions, selectedGoal, if (success) 0 else valueCents, inputs.dailySpendingCents,
                                        enabled = !saving && !success,
                                        onSelect = { goalId = it; amountEditing = false },
                                        onCreate = onCreateGoal?.let { create -> { create(target == TransferTarget.Reserva) } },
                                    )
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            EntryPill("calendar", EntryOptions.dateLabel(date, today), contentLabel = "Data") { openPanel("date") }
                            when {
                                isCard -> EntryPill("card", card.ifBlank { "Cartão" }, placeholder = card.isBlank(), contentLabel = "Cartão") { openPanel("card") }
                                isReceita -> EntryPill("briefcase", selectedAccount.ifBlank { "De onde veio" }, placeholder = selectedAccount.isBlank(), contentLabel = "De onde veio") { openPanel("account") }
                                else -> EntryPill("wallet", if (isTransfer) "De $selectedAccount" else selectedAccount, contentLabel = if (isTransfer) "Conta de origem" else "Conta") { openPanel("account") }
                            }
                            EntryPill("description", description.ifBlank { "Descrição" }, placeholder = description.isBlank(), contentLabel = "Descrição") { openPanel("description") }
                            // "Todo mês" só existe no gasto no débito/Pix.
                            if (isGasto && !isCard) EntryPill("repeat", "Todo mês", active = recurring, placeholder = !recurring, contentLabel = "Repetição") { openPanel("repeat") }
                        }
                        error?.let { Text(it, color = FinaiColors.RoseDark, fontSize = 13.sp) }
                        Spacer(Modifier.height(4.dp))
                    }
                    // 04 · Base: salvar sempre visível; depois de salvar, a confirmação no lugar.
                    if (receipt == null) {
                        Button(onClick = { save() }, enabled = valid, shape = EntryShape,
                            colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.InkStrong, contentColor = FinaiColors.OnInkStrong, disabledContainerColor = EntryLine, disabledContentColor = EntryMuted),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, disabledElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp)) {
                            Text(saveLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        EntryConfirmation(
                            receipt!!, type, summary = EntryOptions.summaryLine(
                                valueCents, confirmationCategory(type, category, incomeKind, selectedGoal?.nome, destAccount),
                                date, if (isCard) card else selectedAccount, today),
                            dailySpendingCents = inputs.dailySpendingCents, enabled = !undoing,
                            onAnother = { clearDraft(keepType = true); onFinished() },
                            onViewAgenda = { val r = receipt!!; clearDraft(keepType = false); onFinished(); onViewEntry(r) },
                        )
                    }
                }
            }
            val keypad: @Composable () -> Unit = {
                EntryKeypad(visible = showKeypad, enabled = !saving && !success, onKey = { focus.clearFocus(); cents = TransactionEntry.key(cents, it) })
            }
            if (landscape) {
                // The keypad panel's width also eases out so hiding it (e.g. to type the
                // description) doesn't leave a dead gutter next to a form stuck at 60% width.
                val formWeight by animateFloatAsState(if (showKeypad) .6f else 1f, finaiTween(350), label = "formWeight")
                Row(Modifier.fillMaxSize().imePadding(), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(formWeight).fillMaxHeight()) { form() }
                    Box(Modifier.weight((1f - formWeight).coerceAtLeast(0.0001f))) { keypad() }
                }
            } else {
                Column(Modifier.fillMaxSize().imePadding()) {
                    Box(Modifier.weight(1f)) { form() }
                    keypad()
                }
            }
            // Desfazer: aviso de vidro no topo, enquanto a confirmação estiver aberta.
            UndoNotice(
                visible = success, title = receipt?.let { confirmationTitle(it, type) } ?: "", busy = undoing, error = undoError,
                // Abaixo do topo, para o X continuar à mão.
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp), onUndo = { undo() },
            )
        }

        // Painéis inferiores das pílulas e do cartão.
        var lastPanel by remember { mutableStateOf(panel) }
        if (panel != null) lastPanel = panel
        EntryPanel(visible = panel != null, title = panelTitle(lastPanel, isReceita), onDismiss = { panel = null }) {
            when (lastPanel) {
                "date" -> EntryCalendar(date, onSelect = { picked ->
                    if (picked.toString() != dateString) { dueString = ""; dueConfirmed = false }
                    dateString = picked.toString(); panel = null
                })
                "account" -> OptionList(availableAccounts + selectedAccount, selectedAccount,
                    customLabel = if (isReceita) "De onde veio (empresa, cliente...)" else "Outra conta",
                    icon = if (isReceita) "briefcase" else "wallet") { account = it; panel = null }
                "dest" -> OptionList(availableAccounts.filterNot { it.equals(selectedAccount, ignoreCase = true) } + destAccount,
                    destAccount, customLabel = "Outra conta", icon = "wallet") { destAccount = it; panel = null }
                "card" -> OptionList(inputs.cards + card, card, customLabel = "Banco/cartão (ex.: Nubank final 1234)", icon = "card") {
                    if (!it.equals(card, ignoreCase = true)) { dueString = ""; dueConfirmed = false }
                    card = it; panel = if (EntryOptions.resolveCardDue(it, date, inputs.faturas, inputs.contas).confident) null else "due"
                }
                "due" -> DuePanel(card, suggestedDue, dueDate, date,
                    onConfirm = { d -> dueString = d.toString(); dueConfirmed = true; panel = null })
                "description" -> DescriptionPanel(description) { description = it; panel = null }
                "repeat" -> OptionRows(listOf("Só desta vez", "Todo mês"), if (recurring) 1 else 0) { recurring = it == 1; panel = null }
                else -> {}
            }
        }
        }
    }
}

private fun incomeDescription(kind: IncomeKind): String = when (kind) {
    IncomeKind.Salario -> "Salário"
    IncomeKind.Recorrente -> "Receita recorrente"
    IncomeKind.Avulsa -> "Receita"
    IncomeKind.Extra -> "Entrada extra"
}

private fun confirmationCategory(type: TransactionType, category: String, kind: IncomeKind, goal: String?, dest: String): String =
    when (type) {
        TransactionType.Gasto -> category
        TransactionType.Receita -> kind.label
        TransactionType.Transferencia -> "para " + (goal ?: dest.trim())
    }

private fun confirmationTitle(r: EntryReceipt, type: TransactionType): String = when {
    r.goal != null -> "Guardado em ${r.goal.name}"
    else -> type.confirmation.removeSuffix("!")
}

private fun panelTitle(panel: String?, isReceita: Boolean): String = when (panel) {
    "date" -> "Data"
    "account" -> if (isReceita) "De onde veio" else "Conta"
    "dest" -> "Para qual conta?"
    "card" -> "Cartão de crédito"
    "due" -> "Vencimento da fatura"
    "description" -> "Descrição"
    "repeat" -> "Repetição"
    else -> ""
}

// ── 02 · valor ──────────────────────────────────────────────────────────

@Composable
private fun AmountBlock(
    cents: String, type: TransactionType, amountFont: Float, onOverflow: () -> Unit,
    helpLine: String, helpAccent: Boolean, enabled: Boolean, onEdit: () -> Unit,
) {
    val amountColor = if (cents.isEmpty()) FinaiColors.TextDisabled else when (type) {
        TransactionType.Gasto -> if (FinaiColors.isDark) Color(0xFFFAFAFA) else EntryInk
        TransactionType.Receita -> FinaiColors.EmeraldDark
        TransactionType.Transferencia -> FinaiColors.Indigo
    }
    val spoken = EntryOptions.spokenAmount(cents.toLongOrNull() ?: 0)
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)
            .clickable(enabled = enabled, onClickLabel = "Editar valor", onClick = onEdit)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.clearAndSetSemantics { contentDescription = "Valor: $spoken" }) {
            Text("R$ ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (cents.isEmpty()) EntryMuted else amountColor, modifier = Modifier.padding(bottom = 7.dp))
            Text(amountText(cents), fontSize = amountFont.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-2).sp, color = amountColor, maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
                onTextLayout = { if (it.hasVisualOverflow) onOverflow() })
        }
        // Linha de ajuda: sempre ocupa o espaço, para o valor não pular ao aparecer/sumir.
        Text(helpLine, color = if (helpAccent) FinaiColors.EmeraldDark else EntryMuted, fontSize = 12.sp,
            fontWeight = if (helpAccent) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp).heightIn(min = 16.dp))
    }
}

// ── seletores ───────────────────────────────────────────────────────────

/** Gasto · Receita · Transferência, com indicador branco que desliza (500 ms, mola). */
@Composable
private fun EntryTypeTabs(type: TransactionType, enabled: Boolean, modifier: Modifier = Modifier, onSelect: (TransactionType) -> Unit) {
    SegmentedSelector(TransactionType.values().map { it.label }, type.ordinal, enabled, modifier, onSelect = { onSelect(TransactionType.values()[it]) })
}

/** Débito / Pix · Crédito · Parcelado. */
@Composable
private fun PaymentSelector(pay: PaymentMethod, enabled: Boolean, onSelect: (PaymentMethod) -> Unit) {
    SegmentedSelector(PaymentMethod.values().map { it.label }, pay.ordinal, enabled, Modifier, indicatorOnDark = Color(0xFF3F3F46),
        onSelect = { onSelect(PaymentMethod.values()[it]) })
}

@Composable
private fun SegmentedSelector(
    labels: List<String>, selectedIndex: Int, enabled: Boolean, modifier: Modifier,
    indicatorOnDark: Color = Color.White, onSelect: (Int) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    BoxWithConstraints(modifier.fillMaxWidth().height(54.dp).clip(CircleShape)
        .background(FinaiColors.SurfaceMuted).padding(3.dp).selectableGroup()) {
        val tabWidth = maxWidth / labels.size
        val offset by animateDpAsState(tabWidth * selectedIndex,
            if (reducedMotion) snap() else tween(500, easing = SpringyEasing), label = "segmentIndicator")
        val indicator = if (FinaiColors.isDark) indicatorOnDark else Color.White
        val onIndicator = if (indicator.luminance() > .5f) Color(0xFF18181B) else Color(0xFFFAFAFA)
        // Um tamanho para todos os rótulos: encolhe só o necessário para o maior caber inteiro.
        var fontSize by remember(tabWidth, LocalDensity.current.fontScale, labels) { mutableFloatStateOf(13f) }
        Box(Modifier.offset(x = offset).width(tabWidth).fillMaxHeight()
            .shadow(3.dp, CircleShape).clip(CircleShape).background(indicator))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val tabFg by animateColorAsState(if (selected) onIndicator else FinaiColors.TextTertiary,
                    finaiTween(if (reducedMotion) 0 else 200), label = "segmentFg")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center) {
                    Text(label, color = tabFg, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        onTextLayout = { if (it.hasVisualOverflow && fontSize > 8f) fontSize = (fontSize - .5f).coerceAtLeast(8f) })
                }
            }
        }
    }
}

// ── 03 · faixa de categorias / destinos ─────────────────────────────────

@Composable
private fun CategoryStrip(categories: List<String>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Categoria" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(categories, key = { it }) { item ->
            StripTile(item, item, item == selected, enabled) { onSelect(item) }
        }
    }
}

@Composable
private fun DestinationStrip(target: TransferTarget, enabled: Boolean, onSelect: (TransferTarget) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransferTarget.values().forEach { t ->
            StripTile(
                when (t) { TransferTarget.Reserva -> "target"; TransferTarget.Objetivo -> "flag"; TransferTarget.Conta -> "transfer" },
                t.label, t == target, enabled,
            ) { onSelect(t) }
        }
    }
}

/** Item de 76 × 84 dp. Selecionado: verde 16% e escala 1,04 com mola (300 ms). */
@Composable
private fun StripTile(icon: String, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val reduced = rememberReducedMotion()
    val spec = if (reduced) snap<Float>() else tween(300, easing = SpringyEasing)
    val colorSpec = if (reduced) snap<Color>() else tween(300)
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, spec, label = "tileScale")
    val bg by animateColorAsState(if (selected) SelectedBg else TileBg, colorSpec, label = "tileBg")
    val iconBg by animateColorAsState(if (selected) SelectedIconBg else TileIconBg, colorSpec, label = "tileIconBg")
    val fg by animateColorAsState(if (selected) SelectedText else FinaiColors.TextStrong, colorSpec, label = "tileFg")
    val shape = RoundedCornerShape(18.dp)
    // 76 dp na fonte padrão; cresce com a fonte do sistema para o rótulo não ser cortado.
    val width = 76.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.45f)
    Column(
        Modifier.width(width).heightIn(min = 84.dp).scale(scale).clip(shape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else TileBorder, shape)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(top = 10.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            EntryGlyph(icon, fg, size = 20)
        }
        var fontSize by remember(label, LocalDensity.current.fontScale) { mutableFloatStateOf(11.5f) }
        Text(label, color = fg, fontSize = fontSize.sp, lineHeight = (fontSize + 2).sp, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            onTextLayout = { if (it.hasVisualOverflow && fontSize > 8.5f) fontSize = (fontSize - .5f).coerceAtLeast(8.5f) })
    }
}

// ── bloco do tipo: cartão, parcelas, receita, reserva ────────────────────

@Composable
private fun InvoiceLine(text: String, needsAction: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(TileBg)
            .border(1.dp, TileBorder, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = if (needsAction) "Confirmar vencimento" else "Trocar fatura", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryGlyph("card", if (needsAction) FinaiColors.Amber else EntryMuted, size = 18)
        Text(text, color = EntryInk, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
        if (needsAction) Text("Confirmar", color = SelectedText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** "10× R$ 289,90", controle de 2× a 24× (passos de 1) e a fatura da 1ª e da última parcela. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallmentCard(
    plan: EntryOptions.Installments, totalCents: Long, enabled: Boolean, line: String, needsAction: Boolean,
    onChange: (Int) -> Unit, onLineClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(TileBg).border(1.dp, TileBorder, shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (plan.valid) plan.headline else "${plan.count}×", color = EntryInk, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (totalCents > 0) Text("total ${brlCents(totalCents)}", color = EntryMuted, fontSize = 12.sp)
        }
        val interaction = remember { MutableInteractionSource() }
        Slider(
            value = plan.count.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(EntryOptions.MIN_INSTALLMENTS, EntryOptions.MAX_INSTALLMENTS)) },
            valueRange = EntryOptions.MIN_INSTALLMENTS.toFloat()..EntryOptions.MAX_INSTALLMENTS.toFloat(),
            steps = EntryOptions.MAX_INSTALLMENTS - EntryOptions.MIN_INSTALLMENTS - 1,
            enabled = enabled,
            interactionSource = interaction,
            colors = SliderDefaults.colors(
                activeTrackColor = FinaiColors.Emerald, inactiveTrackColor = TrackColor,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                disabledActiveTrackColor = FinaiColors.Emerald.copy(alpha = .5f), disabledInactiveTrackColor = TrackColor,
            ),
            thumb = {
                Box(Modifier.size(28.dp).clip(CircleShape).background(FinaiColors.Emerald.copy(alpha = .28f)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(20.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                .semantics { stateDescription = if (plan.valid) plan.spoken else "${plan.count} parcelas" },
        )
        // Marcas 2× 6× 12× 18× 24×, na posição de cada valor no trilho.
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics {}) {
            val span = (EntryOptions.MAX_INSTALLMENTS - EntryOptions.MIN_INSTALLMENTS).toFloat()
            val inset = 14.dp // metade do polegar de 28 dp: onde o trilho começa e termina
            val label = 30.dp
            listOf(2, 6, 12, 18, 24).forEach { mark ->
                val f = (mark - EntryOptions.MIN_INSTALLMENTS) / span
                val x = inset + (maxWidth - inset * 2) * f - label / 2
                Text("$mark×", color = EntryMuted, fontSize = 11.sp, maxLines = 1, softWrap = false,
                    modifier = Modifier.offset(x = x).width(label), textAlign = TextAlign.Center)
            }
        }
        plan.remainderNote?.takeIf { plan.valid }?.let {
            Text(it, color = EntryMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)
                .clickable(onClickLabel = if (needsAction) "Confirmar vencimento" else "Trocar fatura", onClick = onLineClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(line, color = EntryInk.copy(alpha = .9f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            if (needsAction) Text("Confirmar", color = SelectedText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Salário · Recorrente · Avulsa · Extra — um só, sem interruptores que se desligam sozinhos. */
@Composable
private fun IncomeKindSelector(selected: IncomeKind, enabled: Boolean, onSelect: (IncomeKind) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IncomeKind.values().toList().chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { kind ->
                    val isSel = kind == selected
                    val reduced = rememberReducedMotion()
                    val bg by animateColorAsState(if (isSel) SelectedBg else TileBg, if (reduced) snap() else tween(300), label = "incomeBg")
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 88.dp).clip(shape).background(bg)
                            .border(1.dp, if (isSel) Color.Transparent else TileBorder, shape)
                            .selectable(isSel, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(kind) })
                            .padding(14.dp),
                    ) {
                        Text(kind.label, color = if (isSel) SelectedText else EntryInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(kind.detail, color = EntryMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Reserva/objetivo de destino. Nenhuma: oferece criar. Uma: já vem escolhida ("Vai direto
 * para"). Várias: "Qual reserva?", nenhuma marcada. A prévia é ao vivo: com alvo, a barra e
 * "62% → 64%"; na reserva contínua, saldo antes → depois e a cobertura em dias.
 */
@Composable
private fun GoalDestinations(
    target: TransferTarget, options: List<ObjetivoEntity>, selected: ObjetivoEntity?, amountCents: Long,
    dailySpendingCents: Long?, enabled: Boolean, onSelect: (Long) -> Unit, onCreate: (() -> Unit)?,
) {
    val reserve = target == TransferTarget.Reserva
    if (options.isEmpty()) {
        val shape = RoundedCornerShape(18.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(TileBg).border(1.dp, TileBorder, shape).padding(16.dp)) {
            Text(if (reserve) "Nenhuma reserva cadastrada" else "Nenhum objetivo em andamento", color = EntryInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(if (reserve) "Crie uma reserva para guardar este valor nela." else "Crie um objetivo para guardar este valor nele.",
                color = EntryMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            if (onCreate != null) {
                OutlinedButton(onClick = onCreate, enabled = enabled, shape = EntryShape, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
                    Text(if (reserve) "Criar reserva" else "Criar objetivo", color = EntryInk, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }
    Text(
        when {
            options.size == 1 -> "Vai direto para"
            reserve -> "Qual reserva?"
            else -> "Qual objetivo?"
        },
        color = EntryMuted, fontSize = 13.sp, modifier = Modifier.semantics { heading() },
    )
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { goal ->
            val isSel = goal.id == selected?.id
            val move = EntryOptions.goalMove(goal, if (isSel) amountCents else 0, dailySpendingCents)
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(if (isSel) SelectedBg else TileBg)
                    .border(if (isSel) 1.5.dp else 1.dp, if (isSel) FinaiColors.Emerald.copy(alpha = .7f) else TileBorder, shape)
                    .selectable(isSel, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(goal.id) })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.nome, color = EntryInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val moving = isSel && amountCents > 0
                    Text(
                        when {
                            moving -> move.changeLabel
                            move.hasTarget -> "${move.beforePercent}%"
                            else -> brlCents(move.beforeCents)
                        },
                        color = if (moving) SelectedText else EntryMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                if (move.hasTarget) {
                    GainBar(move.beforeFraction, move.afterFraction, Modifier.padding(top = 10.dp))
                    Text("${brlCents(move.afterCents)} de ${brlCents(move.targetCents)}", color = EntryMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(
                        if (isSel && amountCents > 0) move.detailLabel
                        else move.coverageBeforeDays?.let { "Reserva contínua · cobre $it dias de gastos" } ?: "Reserva contínua",
                        color = EntryMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Barra do objetivo com o ganho do aporte em verde claro. */
@Composable
private fun GainBar(before: Float, after: Float, modifier: Modifier = Modifier, height: Int = 6) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(CircleShape).background(TrackColor)) {
        Box(Modifier.fillMaxWidth(after.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Color(0xFF6EE7B7)))
        Box(Modifier.fillMaxWidth(before.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(FinaiColors.Emerald))
    }
}

// ── pílulas e painéis ───────────────────────────────────────────────────

/** Pílula de 38 dp, com área de toque de 48 dp. Abre um painel inferior. */
@Composable
private fun EntryPill(
    icon: String, text: String, placeholder: Boolean = false, active: Boolean = false, contentLabel: String,
    onClick: () -> Unit,
) {
    val bg = if (active) SelectedBg else TileBg
    val fg = when {
        active -> SelectedText
        placeholder -> EntryMuted
        else -> EntryInk
    }
    Box(
        Modifier.heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Alterar ${contentLabel.lowercase(Locale("pt", "BR"))}", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$contentLabel: $text" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(38.dp).clip(CircleShape).background(bg).border(1.dp, if (active) Color.Transparent else TileBorder, CircleShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryGlyph(icon, fg, size = 16)
            Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).widthIn(max = 180.dp))
        }
    }
}

/** Painel inferior do lançamento: 380 ms, curva suave; toque no fundo cancela. */
@Composable
private fun EntryPanel(visible: Boolean, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val reduced = rememberReducedMotion()
    val duration = if (reduced) 0 else 380
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .85f
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(duration, easing = SmoothEasing)), exit = fadeOut(tween(duration, easing = SmoothEasing))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .36f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Fechar painel", onClick = onDismiss))
        }
        AnimatedVisibility(
            visible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(duration, easing = SmoothEasing)) { it },
            exit = slideOutVertically(tween(duration, easing = SmoothEasing)) { it },
        ) {
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxHeight).clip(shape).background(FinaiColors.Surface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding().imePadding()
                    .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp, bottom = 4.dp).size(38.dp, 4.dp).background(EntryLine, CircleShape))
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = EntryInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismiss) {
                        Box(Modifier.size(30.dp).background(FinaiColors.SurfaceMuted, CircleShape), contentAlignment = Alignment.Center) { EntryGlyph("close", FinaiColors.TextTertiary, "Fechar", 14) }
                    }
                }
                content()
            }
        }
    }
}

/** Lista de opções com campo "outra": contas, origens e cartões. Só nomes, sem cadastro bancário. */
@Composable
private fun OptionList(options: List<String>, selected: String, customLabel: String, icon: String, onPick: (String) -> Unit) {
    var custom by rememberSaveable { mutableStateOf("") }
    Column(Modifier.selectableGroup()) {
        options.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.forEach { item ->
            val isSel = item.equals(selected.trim(), ignoreCase = true)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(EntryShape)
                .selectable(isSel, role = Role.RadioButton, onClick = { onPick(item) }).padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                EntryGlyph(icon, EntryMuted)
                Text(item, color = EntryInk, modifier = Modifier.weight(1f).padding(start = 12.dp))
                if (isSel) EntryGlyph("check", FinaiColors.EmeraldDark)
            }
        }
        OutlinedTextField(custom, { custom = it.take(60) }, label = { Text(customLabel) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (custom.isNotBlank()) onPick(custom.trim()) }),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        TextButton(enabled = custom.isNotBlank(), onClick = { onPick(custom.trim()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Usar") }
    }
}

@Composable
private fun OptionRows(options: List<String>, selectedIndex: Int, onPick: (Int) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(EntryShape)
                .selectable(i == selectedIndex, role = Role.RadioButton, onClick = { onPick(i) }).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = EntryInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (i == selectedIndex) EntryGlyph("check", FinaiColors.EmeraldDark)
            }
        }
    }
}

@Composable
private fun DescriptionPanel(initial: String, onDone: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Column {
        OutlinedTextField(text, { text = it.take(80) }, placeholder = { Text("Ex.: almoço com o time") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone(text.trim()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(requester).semantics { contentDescription = "Descrição" })
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            if (text.isNotEmpty()) TextButton(onClick = { text = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Limpar") }
            TextButton(onClick = { onDone(text.trim()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Pronto") }
        }
    }
}

/**
 * Vencimento da fatura. Com sugestão vinda das faturas, um toque confirma; sem dados
 * suficientes (ou para outra data), o calendário. Nunca inventa um ciclo de cartão.
 */
@Composable
private fun DuePanel(card: String, suggestion: EntryOptions.CardDue, current: LocalDate?, purchase: LocalDate, onConfirm: (LocalDate) -> Unit) {
    Column {
        val proposed = current ?: suggestion.date
        if (proposed != null) {
            Text(
                if (suggestion.confident) "Pelos dados da fatura $card, a compra entra na que vence em ${EntryOptions.dayMonth(proposed)}."
                else "Não dá para saber a fatura com certeza pelo que o app tem do $card. Confirme o vencimento.",
                color = EntryMuted, fontSize = 13.sp, lineHeight = 18.sp,
            )
            Button(onClick = { onConfirm(proposed) }, enabled = !proposed.isBefore(purchase), shape = EntryShape,
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.InkStrong, contentColor = FinaiColors.OnInkStrong),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) {
                Text("Confirmar vencimento ${EntryOptions.dayMonth(proposed)}", fontWeight = FontWeight.Bold)
            }
            Text("Ou escolha outra data:", color = EntryMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        } else {
            Text("Primeira compra neste cartão no app: informe o vencimento da fatura em que ela entra.",
                color = EntryMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        EntryCalendar(proposed ?: purchase, onSelect = { if (!it.isBefore(purchase)) onConfirm(it) }, showShortcuts = false, minDate = purchase)
    }
}

// ── confirmação ─────────────────────────────────────────────────────────

/** Aviso de vidro no topo com "Desfazer". */
@Composable
private fun UndoNotice(visible: Boolean, title: String, busy: Boolean, error: String?, modifier: Modifier, onUndo: () -> Unit) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible, modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        enter = slideInVertically(tween(if (reduced) 0 else 300, easing = SmoothEasing)) { -it } + fadeIn(tween(if (reduced) 0 else 200)),
        exit = slideOutVertically(tween(if (reduced) 0 else 220)) { -it } + fadeOut(tween(if (reduced) 0 else 160)),
    ) {
        val shape = RoundedCornerShape(18.dp)
        val glass = if (FinaiColors.isDark) Color(0xFF27272A).copy(alpha = .9f) else Color.White.copy(alpha = .94f)
        Row(
            Modifier.fillMaxWidth().shadow(18.dp, shape).clip(shape).background(glass)
                .border(1.dp, if (FinaiColors.isDark) Color.White.copy(alpha = .08f) else Color.Black.copy(alpha = .06f), shape)
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryGlyph("check", FinaiColors.EmeraldDark, size = 18)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = EntryInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                error?.let { Text(it, color = FinaiColors.RoseDark, fontSize = 12.sp, lineHeight = 15.sp) }
            }
            TextButton(onClick = onUndo, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (busy) "Desfazendo…" else "Desfazer", fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark)
            }
        }
    }
}

/**
 * Base depois de salvar: check colorido por tipo (ou o cartão da reserva com count-up), resumo
 * em uma linha, "Lançar outro" e "Ver na agenda".
 */
@Composable
private fun EntryConfirmation(
    receipt: EntryReceipt, type: TransactionType, summary: String, dailySpendingCents: Long?, enabled: Boolean,
    onAnother: () -> Unit, onViewAgenda: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val goal = receipt.goal
        if (goal != null) {
            ReserveCountUp(goal, dailySpendingCents)
        } else {
            val (circle, check) = when (type) {
                TransactionType.Gasto -> (if (FinaiColors.isDark) Color(0xFFFAFAFA) else FinaiColors.InkStrong) to
                    (if (FinaiColors.isDark) Color(0xFF18181B) else FinaiColors.OnInkStrong)
                TransactionType.Receita -> FinaiColors.Emerald to Color(0xFF052E1F)
                TransactionType.Transferencia -> FinaiColors.Indigo to (if (FinaiColors.isDark) Color(0xFF1E1B4B) else Color.White)
            }
            Box(Modifier.size(48.dp).background(circle, CircleShape), contentAlignment = Alignment.Center) { EntryGlyph("check", check) }
            Text(type.confirmation, color = EntryInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        }
        Text(summary, color = FinaiColors.TextTertiary, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAnother, enabled = enabled, shape = EntryShape, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Lançar outro", color = EntryInk, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Button(onClick = onViewAgenda, enabled = enabled, shape = EntryShape,
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.InkStrong, contentColor = FinaiColors.OnInkStrong),
                modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Ver na agenda", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/**
 * Cartão da reserva depois de guardar, com os valores lidos do banco: 0–350 ms em escala 0,98
 * com o valor anterior; 350–1350 ms valor e barra sobem juntos (ease-out cúbico); depois halo
 * verde de 600 ms, escala volta a 1 com mola e aparece quanto falta (ou a cobertura, na
 * contínua). O TalkBack só ouve o valor final. Animações desligadas: estado final direto.
 */
@Composable
private fun ReserveCountUp(goal: GoalReceipt, dailySpendingCents: Long?) {
    val reduced = rememberReducedMotion()
    val progress = remember(goal) { Animatable(if (reduced) 1f else 0f) }
    val scale = remember(goal) { Animatable(if (reduced) 1f else .98f) }
    val halo = remember(goal) { Animatable(if (reduced) 1f else 0f) }
    var done by remember(goal) { mutableStateOf(reduced) }
    LaunchedEffect(goal) {
        if (reduced) return@LaunchedEffect
        progress.animateTo(1f, tween(1000, delayMillis = 350, easing = CubicOut))
        launch { scale.animateTo(1f, spring(dampingRatio = .55f, stiffness = 380f)) }
        halo.animateTo(1f, tween(600))
        done = true
    }
    val added = goal.afterCents - goal.beforeCents
    val shown = goal.beforeCents + (added * progress.value.toDouble()).roundToLong()
    val target = goal.targetCents
    val shape = RoundedCornerShape(20.dp)
    val move = EntryOptions.GoalMove(goal.name, goal.beforeCents, goal.afterCents, target,
        com.finai.app.domain.GoalFlow.coverageDays(goal.beforeCents, dailySpendingCents).takeIf { target == 0L },
        com.finai.app.domain.GoalFlow.coverageDays(goal.afterCents, dailySpendingCents).takeIf { target == 0L })
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .shadow((18 * halo.value).dp, shape, ambientColor = FinaiColors.Emerald, spotColor = FinaiColors.Emerald)
            .clip(shape).background(TileBg)
            .border(1.5.dp, FinaiColors.Emerald.copy(alpha = .55f * halo.value), shape)
            .padding(16.dp)
            .clearAndSetSemantics { contentDescription = "${goal.name}: ${brlCents(goal.afterCents)}, ${move.afterNote}" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.name, color = EntryMuted, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("+ ${brlCents(added)}", color = SelectedText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(SelectedBg).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Text(brlCents(shown), color = EntryInk, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 6.dp))
        if (target > 0) {
            val from = (goal.beforeCents.toFloat() / target).coerceIn(0f, 1f)
            val to = (goal.afterCents.toFloat() / target).coerceIn(0f, 1f)
            GainBar(from + (to - from) * progress.value, from + (to - from) * progress.value, Modifier.padding(top = 10.dp))
        }
        Text(move.afterNote, color = if (done) EntryMuted else Color.Transparent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
    // Região "live": vazia durante a contagem, o valor final quando termina.
    Box(Modifier.size(1.dp).semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = if (done) move.announcement else ""
    })
}

// ── peças compartilhadas com Dívidas, Fatura e Agenda ───────────────────

@Composable
internal fun EntryField(icon: String, label: String, value: String, placeholder: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(EntryShape).background(FinaiColors.Surface).border(1.dp, EntryLine.copy(alpha = .65f), EntryShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        EntryGlyph(icon, EntryMuted)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(label, color = EntryMuted, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = if (placeholder) EntryMuted else EntryInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        EntryGlyph("down", EntryMuted, size = 16)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .85f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FinaiColors.Surface, tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = .36f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 14.dp, bottom = 4.dp).size(38.dp, 4.dp).background(EntryLine, CircleShape)) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = EntryInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Box(Modifier.size(30.dp).background(FinaiColors.SurfaceMuted, CircleShape), contentAlignment = Alignment.Center) { EntryGlyph("close", FinaiColors.TextTertiary, "Fechar", 14) }
                }
            }
            content()
        }
    }
}

private val WeekdayLabelsPt = listOf("D", "S", "T", "Q", "Q", "S", "S")

/**
 * Substitui o `DatePicker` do Material3: aquele exigia apertar "Confirmar" e quem
 * arrastasse o dedo sem confirmar via a data escolhida ser descartada silenciosamente
 * (parecia "o app ignora a data escolhida"), além do fling do mês por swipe pular
 * vários meses de uma vez. Aqui a navegação é só por seta (sem swipe, sem fling) e
 * tocar num dia já aplica e fecha — não existe estado "selecionado mas não confirmado".
 */
@Composable
internal fun EntryCalendarSheet(selected: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit, showYesterday: Boolean = true) {
    EntrySheet("Selecionar data", onDismiss) {
        EntryCalendar(selected, onSelect, showYesterday = showYesterday)
    }
}

@Composable
private fun EntryCalendar(
    selected: LocalDate, onSelect: (LocalDate) -> Unit, showYesterday: Boolean = true, showShortcuts: Boolean = true,
    minDate: LocalDate? = null,
) {
    var viewedMonth by remember { mutableStateOf(YearMonth.from(selected)) }
    Column {
        if (showShortcuts) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                TextButton(onClick = { onSelect(LocalDate.now()) }) { Text("Hoje") }
                if (showYesterday) TextButton(onClick = { onSelect(LocalDate.now().minusDays(1)) }) { Text("Ontem") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewedMonth = viewedMonth.minusMonths(1) }) { EntryGlyph("back", EntryInk, "Mês anterior") }
            Text(
                "${MONTH_NAMES_PT[viewedMonth.monthValue - 1].replaceFirstChar { it.uppercase() }} de ${viewedMonth.year}",
                color = EntryInk, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { viewedMonth = viewedMonth.plusMonths(1) }) {
                Box(Modifier.graphicsLayer { rotationZ = 180f }) { EntryGlyph("back", EntryInk, "Próximo mês") }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            WeekdayLabelsPt.forEach { label ->
                Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = EntryMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        val firstOfMonth = viewedMonth.atDay(1)
        val leadingBlanks = firstOfMonth.dayOfWeek.value % 7 // Monday=1..Sunday=7 -> Sunday-first grid
        val totalCells = leadingBlanks + viewedMonth.lengthOfMonth()
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - leadingBlanks + 1
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..viewedMonth.lengthOfMonth()) {
                            val date = viewedMonth.atDay(day)
                            val isSelected = date == selected
                            val isToday = date == LocalDate.now()
                            val allowed = minDate == null || !date.isBefore(minDate)
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (isSelected) FinaiColors.InkStrong else Color.Transparent)
                                    .then(if (isToday && !isSelected) Modifier.border(1.dp, EntryInk, CircleShape) else Modifier)
                                    .selectable(isSelected, enabled = allowed, role = Role.Button, onClick = { onSelect(date) })
                                    .semantics { contentDescription = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "BR"))) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(day.toString(), color = when {
                                    isSelected -> FinaiColors.OnInkStrong
                                    !allowed -> FinaiColors.TextDisabled
                                    else -> EntryInk
                                }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Reference glyphs: 24-unit grid, rounded 1.8-unit stroke, 21 dp rendered size. */
@Composable
internal fun EntryGlyph(name: String, color: Color, description: String? = null, size: Int = 21) {
    val data = when (name) {
        "back" -> "M19 12H5 M11 6l-6 6 6 6"
        "down" -> "M6 9l6 6 6-6"
        "close" -> "M6 6l12 12 M18 6L6 18"
        "check" -> "M5 12l4 4L19 6"
        "card" -> "M4 5H20Q21 5 21 7V17Q21 19 19 19H5Q3 19 3 17V7Q3 5 4 5 M3 10H21"
        "calendar" -> "M5 4H19Q21 4 21 6V19Q21 21 19 21H5Q3 21 3 19V6Q3 4 5 4 M7 2V6 M17 2V6 M3 10H21"
        "description" -> "M4 8V4H8 M16 4H20V8 M20 16V20H16 M8 20H4V16 M8 12H16"
        "category" -> "M5 5h2v2H5z M11 5h2v2h-2z M17 5h2v2h-2z M5 11h2v2H5z M11 11h2v2h-2z M17 11h2v2h-2z M5 17h2v2H5z M11 17h2v2h-2z M17 17h2v2h-2z"
        "repeat" -> "M20 3v6h-6 M4 21v-6h6 M20 9A8 8 0 0 0 6 5L4 8 M4 15a8 8 0 0 0 14 4l2-3"
        "erase" -> "M9 5H22V19H9L2 12Z M12 9l6 6 M18 9l-6 6"
        "sparkle" -> "M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2Z M19 3v4 M17 5h4"
        "percent" -> "M19 5L5 19 M7 7h.1 M17 17h.1"
        "hash" -> "M4 9h16 M4 15h16 M10 3L8 21 M16 3l-2 18"
        "minus" -> "M5 12h14"
        "plus" -> "M12 5v14 M5 12h14"
        "briefcase" -> "M4 8h16q1 0 1 1v10q0 1-1 1H4q-1 0-1-1V9q0-1 1-1Z M9 8V5q0-1 1-1h4q1 0 1 1v3 M3 13h18"
        "installment" -> "M6 3h12v18l-2-1.5-2 1.5-2-1.5-2 1.5-2-1.5-2 1.5Z M9 8h6 M9 12h6 M9 16h3"
        "transfer" -> "M4 8h15 M15 4l4 4-4 4 M20 16H5 M9 12l-4 4 4 4"
        "wallet" -> "M4 6h14q2 0 2 2v10q0 2-2 2H5q-2 0-2-2V7q0-3 3-3h10 M16 13h.1"
        "target" -> "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z M12 12h.1"
        "flag" -> "M5 21V4 M5 4h11l-2 4 2 4H5"
        "Alimentação" -> "M5 3v6q0 3 3 3t3-3V3 M8 3v18 M19 21V3q-4 0-4 7h4"
        "Transporte" -> "M5 17V9q0-5 7-5t7 5v8Z M5 10h14 M7 17v3 M17 17v3 M8 14h1 M15 14h1 M9 4V2h6v2"
        "Moradia" -> "M3 10l9-7 9 7v11H3Z M9 21V13h6v8 M6 10h.1"
        "Saúde" -> "M12 21S3 15 3 8a5 5 0 0 1 9-3 5 5 0 0 1 9 3c0 7-9 13-9 13Z"
        "Lazer" -> "M7 6h10q4 0 5 10 0 5-5 1l-1-1H8l-1 1q-5 4-5-1Q3 6 7 6Z M6 9v6 M3 12h6 M16 10h.1 M19 13h.1"
        "Educação" -> "M2 8l10-5 10 5-10 5Z M6 10v7q6 5 12 0v-7 M22 8v7"
        "Compras" -> "M4 7h16l-2 14H6Z M8 8V5a4 4 0 0 1 8 0v3"
        "Serviços" -> "M5 3l4 4-2 3q3 5 7 7l3-2 4 4q-2 6-8 2Q4 16 2 7q-1-4 3-4Z"
        else -> "M5 12h.1 M12 12h.1 M19 12h.1"
    }
    val path = remember(data) { androidx.core.graphics.PathParser.createPathFromPathData(data)!!.asComposePath() }
    Canvas(Modifier.size(size.dp).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, color, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
