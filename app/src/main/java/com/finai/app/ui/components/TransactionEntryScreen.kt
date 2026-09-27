package com.finai.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.TransactionEntry
import com.finai.app.domain.TransactionType
import com.finai.app.domain.toEpochMillis
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val EntryInk = Color(0xFF18181B)
internal val EntryMuted = Color(0xFFA1A1AA)
internal val EntryPaper = Color(0xFFFAFAFA)
internal val EntryLine = Color(0xFFE4E4E7)
internal val EntryShape = RoundedCornerShape(16.dp)
internal val EntryFont = FontFamily(
    Font(com.finai.app.R.font.inter_400, FontWeight.Normal),
    Font(com.finai.app.R.font.inter_500, FontWeight.Medium),
    Font(com.finai.app.R.font.inter_600, FontWeight.SemiBold),
    Font(com.finai.app.R.font.inter_700, FontWeight.Bold),
    Font(com.finai.app.R.font.inter_800, FontWeight.ExtraBold),
)

@Composable
fun TransactionEntryScreen(
    visible: Boolean,
    accounts: List<String>,
    incomeSources: List<String> = emptyList(),
    initialType: TransactionType = TransactionType.Gasto,
    /** Abre já marcada como entrada extra (o "+" da Linha do tempo do ano). */
    initialExtra: Boolean = false,
    onDismiss: () -> Unit,
    onSave: suspend (TransacaoEntity) -> Unit,
    onViewEntry: (LocalDate) -> Unit,
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
            TransactionEntryContent(visible, accounts, incomeSources, initialType, initialExtra, onDismiss, onSave, onViewEntry)
        }
    }
}

private fun amountText(cents: String): String = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format((cents.toLongOrNull() ?: 0) / 100.0)

/** Kept mounted by the app so Back preserves the draft; saveable state also survives rotation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEntryContent(
    visible: Boolean,
    accounts: List<String>,
    incomeSources: List<String>,
    initialType: TransactionType,
    initialExtra: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (TransacaoEntity) -> Unit,
    onViewEntry: (LocalDate) -> Unit,
) {
    var cents by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var dateString by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var recurring by rememberSaveable { mutableStateOf(false) }
    var typeName by rememberSaveable { mutableStateOf(TransactionType.Gasto.name) }
    var extra by rememberSaveable { mutableStateOf(false) }
    // Só aplica o tipo pedido de fora (ex.: "+" da linha do tempo pedindo Receita) quando o
    // rascunho está vazio — senão reabrir a tela com "Voltar preserva rascunho" trocaria o
    // tipo de um lançamento que o usuário já estava preenchendo.
    LaunchedEffect(visible) {
        if (visible && cents.isEmpty() && category.isEmpty() && description.isBlank()) {
            typeName = initialType.name
            extra = initialExtra && initialType == TransactionType.Receita
            if (extra) recurring = false
        }
    }
    var success by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val type = TransactionType.valueOf(typeName)
    val date = LocalDate.parse(dateString)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // Receita usa "de onde veio" (empregador, cliente...), não a conta/cartão usada em
    // gasto/transferência — um nome de empresa digitado ali não deve virar opção permanente
    // no seletor de conta/cartão (era o que acontecia quando os dois compartilhavam a lista).
    val isReceita = type == TransactionType.Receita
    val availableAccounts = if (isReceita) incomeSources.filter { it.isNotBlank() }.distinct()
        else (accounts.filter { it.isNotBlank() } + listOf("Carteira")).distinct()
    val selectedAccount = account.ifBlank { availableAccounts.firstOrNull() ?: "" }
    // Trocar de tipo não deve carregar uma conta/cartão para dentro de "de onde veio" nem
    // o contrário.
    LaunchedEffect(typeName) { account = "" }
    var amountFont by remember(cents, LocalConfiguration.current.screenWidthDp, LocalDensity.current.fontScale) {
        mutableFloatStateOf(46f)
    }
    val valid = TransactionEntry.canSave(cents, category) && !saving
    // Description uses the system IME; the custom keypad must yield to it instead of stacking on top.
    var descriptionFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // O teclado numérico só faz sentido enquanto o valor está sendo digitado — depois que o
    // usuário segue para outro campo (categoria, conta, data, recorrente, ou rola a tela) ele
    // deixa de ajudar e só ocupa espaço olhando pro usuário. Reabre se ele tocar no valor de novo.
    var amountEditing by rememberSaveable { mutableStateOf(true) }
    val showKeypad = amountEditing && !descriptionFocused && !imeVisible
    fun close() { focus.clearFocus(); sheet = null; if (!saving && !success) onDismiss() }
    BackHandler(visible) { close() }
    AnimatedVisibility(visible, enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 12 }, exit = fadeOut(tween(160))) {
        BoxWithConstraints(Modifier.fillMaxSize().background(EntryPaper).pointerInput(Unit) {
            // The dedicated layer also owns empty-space taps, so the app behind it cannot react.
            detectTapGestures(onTap = { focus.clearFocus() })
        }
            // Excludes the IME here so maxWidth/maxHeight (used below to pick the
            // portrait/landscape split) stay stable while the keyboard animates in and out;
            // the IME inset itself is still consumed further down via imePadding().
            .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))) {
            val form: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize().then(if (sheet != null) Modifier.blur(4.dp) else Modifier)) {
                Box(Modifier.fillMaxWidth().height(62.dp).background(Color.White)) {
                    IconButton(onClick = { close() }, enabled = !saving, modifier = Modifier.align(Alignment.CenterStart)) {
                        EntryGlyph("back", EntryInk, "Voltar")
                    }
                    Text("Novo lançamento", color = EntryInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color(0xFFF1F1F2)))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth().height(42.dp).clip(CircleShape).background(Color(0xFFF4F4F5)).padding(3.dp)) {
                        TransactionType.values().forEach { item ->
                            val selected = type == item
                            val tabBg by animateColorAsState(if (selected) EntryInk else Color.Transparent, finaiTween(FinaiMotion.Quick), label = "typeTabBg")
                            val tabFg by animateColorAsState(if (selected) Color.White else Color(0xFF71717A), finaiTween(FinaiMotion.Quick), label = "typeTabFg")
                            Box(Modifier.weight(1f).fillMaxHeight()
                                .then(if (selected) Modifier.shadow(3.dp, CircleShape) else Modifier)
                                .clip(CircleShape).background(tabBg)
                                .selectable(selected, enabled = !saving, role = Role.Tab, onClick = { typeName = item.name }),
                                contentAlignment = Alignment.Center) {
                                Text(item.label, color = tabFg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                    // clearFocus é o que traz o teclado de volta: sem ele a Descrição continuava
                    // focada (mesmo depois de fechar o teclado do sistema com "voltar") e
                    // showKeypad ficava falso, então tocar no valor não fazia nada.
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp).clickable(enabled = !saving) { focus.clearFocus(); amountEditing = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                        val amountColor = if (cents.isEmpty()) Color(0xFFD4D4D8) else when (type) {
                            TransactionType.Gasto -> EntryInk
                            TransactionType.Receita -> Color(0xFF059669)
                            TransactionType.Transferencia -> Color(0xFF4F46E5)
                        }
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics { contentDescription = "Valor: R$ ${amountText(cents)}" }) {
                            Text("R$ ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (cents.isEmpty()) EntryMuted else amountColor, modifier = Modifier.padding(bottom = 7.dp))
                            Text(amountText(cents), fontSize = amountFont.sp, fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-2).sp, color = amountColor, maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                                onTextLayout = { if (it.hasVisualOverflow && amountFont > 18f) amountFont *= .9f })
                        }
                        Text("Digite o valor no teclado abaixo", color = EntryMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(EntryShape).background(Color.White).border(1.dp, EntryLine.copy(alpha = .65f), EntryShape).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        EntryGlyph("description", EntryMuted)
                        Spacer(Modifier.width(14.dp))
                        BasicTextField(description, { description = it }, enabled = !saving, singleLine = true,
                            textStyle = TextStyle(fontFamily = EntryFont, fontSize = 14.sp, color = EntryInk), cursorBrush = SolidColor(EntryInk),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                            modifier = Modifier.weight(1f).padding(vertical = 14.dp)
                                .onFocusChanged { descriptionFocused = it.isFocused; if (it.isFocused) amountEditing = false }
                                .semantics { contentDescription = "Descrição" },
                            decorationBox = { field -> Box { if (description.isEmpty()) Text("Descrição", color = Color(0xFF808080), fontSize = 14.sp); field() } })
                        if (description.isNotEmpty()) IconButton(onClick = { description = "" }, enabled = !saving) { EntryGlyph("close", EntryMuted, "Limpar descrição") }
                    }
                    EntryField("card", if (isReceita) "De onde veio" else "Conta/cartão de origem",
                        selectedAccount.ifEmpty { if (isReceita) "Ex.: empresa, cliente" else selectedAccount }, isReceita && selectedAccount.isEmpty()) {
                        if (!saving) { focus.clearFocus(); amountEditing = false; sheet = "account" }
                    }
                    EntryField("calendar", "Data", date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + when(date) {
                        LocalDate.now() -> " · hoje"; LocalDate.now().minusDays(1) -> " · ontem"; else -> ""
                    }) { if (!saving) { focus.clearFocus(); amountEditing = false; sheet = "date" } }
                    EntryField("category", "Categoria", category.ifEmpty { "Selecionar categoria" }, category.isEmpty()) { if (!saving) { focus.clearFocus(); amountEditing = false; sheet = "category" } }
                    // Extra e recorrente se excluem: entrada extra é justamente o que está fora da renda que se repete.
                    if (isReceita) {
                        EntrySwitchRow("sparkle", if (extra) "Entrada extra · 13º, bônus, restituição" else "Entrada extra", extra, !saving, "Entrada extra") {
                            amountEditing = false; extra = !extra; if (extra) recurring = false
                        }
                    }
                    EntrySwitchRow("repeat", if (recurring) "Recorrente · todo mês" else "Recorrente", recurring, !saving, "Recorrente") {
                        amountEditing = false; recurring = !recurring; if (recurring) extra = false
                    }
                    if (error != null) Text(error!!, color = Color(0xFFBE123C), fontSize = 13.sp)
                    Button(onClick = {
                        focus.clearFocus()
                        saving = true; error = null
                        val entry = TransacaoEntity(data = date.toEpochMillis(), descricao = description.trim().ifEmpty { category },
                            valorCentavos = cents.toLong(), categoria = category, contaOrigem = selectedAccount,
                            recorrente = recurring, origem = "manual", tipo = type.name, extra = isReceita && extra)
                        scope.launch {
                            try { onSave(entry); success = true }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = "Não foi possível salvar. Tente novamente." }
                            finally { saving = false }
                        }
                    }, enabled = valid, shape = EntryShape,
                        colors = ButtonDefaults.buttonColors(containerColor = EntryInk, contentColor = Color.White, disabledContainerColor = EntryLine, disabledContentColor = EntryMuted),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, disabledElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).height(52.dp)) {
                        Text(if (saving) "Salvando…" else "Salvar lançamento", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            }
            val keypad: @Composable () -> Unit = {
                EntryKeypad(visible = showKeypad, enabled = !saving && !success, onKey = { focus.clearFocus(); cents = TransactionEntry.key(cents, it) })
            }
            if (maxWidth > maxHeight) {
                // The keypad panel's width also eases out so hiding it (e.g. to type the
                // description) doesn't leave a dead gutter next to a form stuck at 60% width.
                val formWeight by animateFloatAsState(if (showKeypad) .6f else 1f, tween(220), label = "formWeight")
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
        }
    }
    if (visible && sheet == "category") {
        EntrySheet("Selecionar categoria", { sheet = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionEntry.categories.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            val selected = item == category
                            val color by animateColorAsState(if (selected) Color(0xFF047857) else Color(0xFF3F3F46), finaiTween(FinaiMotion.Quick), label = "categoryFg")
                            val bg by animateColorAsState(if (selected) Color(0xFFECFDF5) else EntryPaper, finaiTween(FinaiMotion.Quick), label = "categoryBg")
                            val border by animateColorAsState(if (selected) Color(0xFFA7F3D0) else Color(0xFFF1F1F2), finaiTween(FinaiMotion.Quick), label = "categoryBorder")
                            val borderWidth by animateFloatAsState(if (selected) 1.5f else 1f, finaiTween(FinaiMotion.Quick), label = "categoryBorderWidth")
                            val scale by animateFloatAsState(if (selected) 1.03f else 1f, finaiTween(FinaiMotion.Quick), label = "categoryScale")
                            Column(Modifier.weight(1f).heightIn(min = 70.dp).scale(scale).clip(EntryShape)
                                .background(bg)
                                .border(borderWidth.dp, border, EntryShape)
                                .selectable(selected, role = Role.RadioButton, onClick = { category = item; sheet = null })
                                .padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                EntryGlyph(item, color)
                                Text(item, color = color, fontSize = 12.sp, lineHeight = 14.sp, fontFamily = EntryFont, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
    if (visible && sheet == "account") {
        var custom by rememberSaveable { mutableStateOf("") }
        EntrySheet(if (isReceita) "De onde veio" else "Conta/cartão de origem", { sheet = null }) {
            Column {
                (availableAccounts + selectedAccount).distinct().filter { it.isNotBlank() }.forEach { item ->
                    Row(Modifier.fillMaxWidth().selectable(item == selectedAccount, role = Role.RadioButton, onClick = { account = item; sheet = null }).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        EntryGlyph("card", EntryMuted)
                        Text(item, color = EntryInk, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        if (item == selectedAccount) EntryGlyph("check", Color(0xFF059669))
                    }
                }
                OutlinedTextField(custom, { custom = it }, label = { Text(if (isReceita) "De onde veio (empresa, cliente...)" else "Outra conta/cartão") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = custom.isNotBlank(), onClick = { account = custom.trim(); sheet = null }) { Text(if (isReceita) "Usar" else "Usar conta") }
            }
        }
    }
    if (visible && sheet == "date") {
        EntryCalendarSheet(selected = date, onSelect = { picked -> dateString = picked.toString(); sheet = null }, onDismiss = { sheet = null })
    }
    if (visible && success) Dialog(onDismissRequest = {}) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).background(Color(0xFF10B981), CircleShape), contentAlignment = Alignment.Center) { EntryGlyph("check", Color.White) }
            Text(type.confirmation, color = EntryInk, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            Text("R$ ${amountText(cents)} em $category" + (if (recurring) " · recorrente" else "") + (if (isReceita && extra) " · entrada extra" else ""), color = Color(0xFF71717A), fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = {
                val savedDate = date
                cents = ""; category = ""; description = ""; recurring = false; extra = false; typeName = TransactionType.Gasto.name
                dateString = LocalDate.now().toString(); account = ""; success = false; amountEditing = true
                onViewEntry(savedDate)
            }, colors = ButtonDefaults.buttonColors(containerColor = EntryInk), shape = EntryShape, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Ver lançamento") }
        }
    }
}

@Composable
internal fun EntryField(icon: String, label: String, value: String, placeholder: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(EntryShape).background(Color.White).border(1.dp, EntryLine.copy(alpha = .65f), EntryShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        EntryGlyph(icon, EntryMuted)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(label, color = EntryMuted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = if (placeholder) EntryMuted else EntryInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        EntryGlyph("down", EntryMuted, size = 16)
    }
}

@Composable
private fun EntrySwitchRow(icon: String, text: String, checked: Boolean, enabled: Boolean, description: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(EntryShape).background(Color.White).border(1.dp, EntryLine.copy(alpha = .65f), EntryShape).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        EntryGlyph(icon, EntryMuted)
        Text(text, color = EntryInk, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
        // Compact 46 x 26 track from the reference, with a full 48 dp touch area.
        Box(Modifier.size(48.dp).semantics { contentDescription = description }
            .selectable(checked, enabled = enabled, role = Role.Switch, onClick = onToggle), contentAlignment = Alignment.Center) {
            val trackColor by animateColorAsState(if (checked) Color(0xFF10B981) else EntryLine, finaiTween(FinaiMotion.Quick), label = "switchTrack")
            val thumbOffset by androidx.compose.animation.core.animateDpAsState(if (checked) 20.dp else 0.dp, finaiTween(FinaiMotion.Quick), label = "switchThumb")
            Box(Modifier.size(46.dp, 26.dp).clip(CircleShape).background(trackColor).padding(3.dp)) {
                Box(Modifier.offset(x = thumbOffset).size(20.dp).shadow(1.dp, CircleShape).background(Color.White, CircleShape))
            }
        }
    }
}

@Composable
internal fun EntryKeypad(visible: Boolean, enabled: Boolean, onKey: (String) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
        exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
    ) {
        Column(Modifier.fillMaxWidth().height(240.dp).background(Brush.linearGradient(listOf(Color.White.copy(alpha = .9f), Color.White.copy(alpha = .78f)))).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "erase")).forEach { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { key ->
                        Box(Modifier.weight(1f).fillMaxHeight().clip(EntryShape).background(if (key == "00" || key == "erase") Color(0xFFF7F7F8) else Color.White)
                            .border(1.dp, Color(0xFFF4F4F5), EntryShape).clickable(enabled = enabled, role = Role.Button, onClick = { onKey(key) }),
                            contentAlignment = Alignment.Center) {
                            if (key == "erase") EntryGlyph("erase", Color(0xFF52525B), "Apagar")
                            else Text(key, color = if (key == "00") Color(0xFF52525B) else EntryInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .85f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White, tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = .36f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 14.dp, bottom = 4.dp).size(38.dp, 4.dp).background(EntryLine, CircleShape)) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = EntryInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Box(Modifier.size(30.dp).background(Color(0xFFF4F4F5), CircleShape), contentAlignment = Alignment.Center) { EntryGlyph("close", Color(0xFF71717A), "Fechar", 14) }
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
    var viewedMonth by remember { mutableStateOf(YearMonth.from(selected)) }
    EntrySheet("Selecionar data", onDismiss) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                TextButton(onClick = { onSelect(LocalDate.now()) }) { Text("Hoje") }
                if (showYesterday) TextButton(onClick = { onSelect(LocalDate.now().minusDays(1)) }) { Text("Ontem") }
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
                        Text(label, color = EntryMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..viewedMonth.lengthOfMonth()) {
                                val date = viewedMonth.atDay(day)
                                val isSelected = date == selected
                                val isToday = date == LocalDate.now()
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape)
                                        .background(if (isSelected) EntryInk else Color.Transparent)
                                        .then(if (isToday && !isSelected) Modifier.border(1.dp, EntryInk, CircleShape) else Modifier)
                                        .selectable(isSelected, role = Role.Button, onClick = { onSelect(date) }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(day.toString(), color = if (isSelected) Color.White else EntryInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
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
