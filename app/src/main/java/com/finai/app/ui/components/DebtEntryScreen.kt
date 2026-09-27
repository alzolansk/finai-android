package com.finai.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.domain.DebtEntry
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.TransactionEntry
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun debtAmountText(cents: String): String = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format((cents.toLongOrNull() ?: 0) / 100.0)

private val Emerald = Color(0xFF059669)

/**
 * Cadastro/edição de dívida em tela cheia, no mesmo visual do lançamento manual
 * (`TransactionEntryScreen`) — substitui o `AlertDialog` de seis campos soltos. A dívida é
 * descrita como aparece no contrato (valor da parcela, total de parcelas, quantas já foram
 * pagas, vencimento da próxima); parcelas restantes, saldo estimado e mês da última parcela
 * são derivados em [DebtEntry] e mostrados num resumo antes de salvar.
 */
@Composable
fun DebtEntryScreen(
    visible: Boolean,
    initial: DividaEntity?,
    onDismiss: () -> Unit,
    onSave: (DividaEntity) -> Unit,
) {
    val typography = MaterialTheme.typography
    MaterialTheme(typography = typography.copy(
        bodyLarge = typography.bodyLarge.copy(fontFamily = EntryFont),
        bodyMedium = typography.bodyMedium.copy(fontFamily = EntryFont),
        labelLarge = typography.labelLarge.copy(fontFamily = EntryFont),
    )) {
        ProvideTextStyle(TextStyle(fontFamily = EntryFont, fontSize = 14.sp)) {
            AnimatedVisibility(visible, enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 12 }, exit = fadeOut(tween(160))) {
                // Estado novo a cada abertura/dívida editada — é um formulário curto, sem rascunho.
                key(initial?.id ?: -1L) { DebtEntryContent(initial, onDismiss, onSave) }
            }
        }
    }
}

@Composable
private fun DebtEntryContent(initial: DividaEntity?, onDismiss: () -> Unit, onSave: (DividaEntity) -> Unit) {
    val initialParcelada = initial?.let { it.parcelasRestantes > 0 && it.valorParcelaCentavos > 0 } ?: true
    val initialTotal = initial?.let { if (it.parcelasTotais >= it.parcelasRestantes && it.parcelasTotais > 0) it.parcelasTotais else it.parcelasRestantes } ?: 0
    var parcelada by rememberSaveable { mutableStateOf(initialParcelada) }
    var nome by rememberSaveable { mutableStateOf(initial?.nome ?: "") }
    // Valor da parcela (parcelada) ou saldo em aberto (sem parcelas), em centavos como o teclado do lançamento.
    var parcelaCents by rememberSaveable { mutableStateOf(initial?.valorParcelaCentavos?.takeIf { it > 0 }?.toString() ?: "") }
    var abertoCents by rememberSaveable { mutableStateOf(initial?.valorAbertoCentavos?.takeIf { it > 0 && !initialParcelada }?.toString() ?: "") }
    var total by rememberSaveable { mutableStateOf(initialTotal.takeIf { it > 0 }?.toString() ?: "") }
    var pagas by rememberSaveable { mutableStateOf(initial?.let { (initialTotal - it.parcelasRestantes).coerceAtLeast(0).toString() } ?: "0") }
    var dateString by rememberSaveable { mutableStateOf(initial?.proximoVencimento?.toLocalDate()?.toString()) }
    var rate by rememberSaveable { mutableStateOf(initial?.let { DebtEntry.rateText(it.taxaJurosMensalBasisPoints) } ?: "") }
    // Saldo devedor informado à mão numa parcelada; vazio = parcelas restantes × parcela.
    var saldoCents by rememberSaveable {
        mutableStateOf(initial?.takeIf { initialParcelada }?.let {
            val estimado = DebtEntry.saldoEstimadoCents(it.valorParcelaCentavos, initialTotal, initialTotal - it.parcelasRestantes)
            if (it.valorAbertoCentavos != estimado && it.valorAbertoCentavos > 0) it.valorAbertoCentavos.toString() else ""
        } ?: "")
    }
    // Qual valor o teclado está editando: o principal ("main") ou o saldo devedor ("saldo").
    var keypadTarget by rememberSaveable { mutableStateOf(if (initial == null) "main" else "") }
    var textFocused by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showKeypad = keypadTarget.isNotEmpty() && !textFocused && !imeVisible

    val mainCents = if (parcelada) parcelaCents else abertoCents
    val totalN = total.toIntOrNull() ?: 0
    val pagasN = pagas.toIntOrNull() ?: 0
    val date = dateString?.let(LocalDate::parse)
    val valid = DebtEntry.canSave(nome, parcelada, mainCents.toLongOrNull() ?: 0, totalN, pagasN, date, rate)
    fun leaveKeypad() { keypadTarget = "" }
    fun close() { focus.clearFocus(); sheet = null; onDismiss() }
    BackHandler { close() }

    BoxWithConstraints(Modifier.fillMaxSize().background(EntryPaper)
        .pointerInput(Unit) { detectTapGestures(onTap = { focus.clearFocus() }) }
        .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))) {
        val form: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(62.dp).background(Color.White)) {
                    IconButton(onClick = { close() }, modifier = Modifier.align(Alignment.CenterStart)) { EntryGlyph("back", EntryInk, "Voltar") }
                    Text(if (initial != null) "Editar dívida" else "Nova dívida", color = EntryInk, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color(0xFFF1F1F2)))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SegmentedTabs(
                        options = listOf("Parcelada", "Sem parcelas"),
                        selected = if (parcelada) 0 else 1,
                        onSelect = { parcelada = it == 0; keypadTarget = "main"; focus.clearFocus() },
                    )
                    Text(
                        if (parcelada) "Empréstimo, financiamento, compra parcelada — tem valor fixo por mês."
                        else "Rotativo do cartão, cheque especial — só o saldo e os juros.",
                        color = EntryMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )

                    // Valor principal, digitado no teclado próprio como no lançamento.
                    val mainActive = keypadTarget == "main"
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { focus.clearFocus(); keypadTarget = "main" },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (parcelada) "VALOR DA PARCELA" else "QUANTO VOCÊ DEVE HOJE", color = EntryMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val color = if (mainCents.isEmpty()) Color(0xFFD4D4D8) else EntryInk
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)
                            .semantics { contentDescription = "Valor: R$ ${debtAmountText(mainCents)}" }) {
                            Text("R$ ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (mainCents.isEmpty()) EntryMuted else color, modifier = Modifier.padding(bottom = 6.dp))
                            Text(debtAmountText(mainCents), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, color = color, maxLines = 1)
                        }
                        Box(Modifier.padding(top = 6.dp).size(width = 56.dp, height = 3.dp).clip(CircleShape)
                            .background(if (mainActive) EntryInk else Color.Transparent))
                    }

                    TextRow("description", nome, { nome = it }, "Nome (ex.: Empréstimo Itaú)", onFocus = { textFocused = it; if (it) leaveKeypad() })
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DebtEntry.suggestions.forEach { s ->
                            val selected = nome.trim().equals(s, ignoreCase = true)
                            Box(Modifier.clip(CircleShape).background(if (selected) Color(0xFFECFDF5) else Color.White)
                                .border(1.dp, if (selected) Color(0xFFA7F3D0) else EntryLine, CircleShape)
                                .clickable { nome = s; focus.clearFocus() }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                                Text(s, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (selected) Color(0xFF047857) else Color(0xFF3F3F46))
                            }
                        }
                    }

                    if (parcelada) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Stepper("Total de parcelas", total, { total = it }, min = 1, Modifier.weight(1f), onFocus = { textFocused = it; if (it) leaveKeypad() })
                            Stepper("Já pagas", pagas, { pagas = it }, min = 0, Modifier.weight(1f), onFocus = { textFocused = it; if (it) leaveKeypad() },
                                max = (totalN - 1).coerceAtLeast(0))
                        }
                        if (totalN > 0 && pagasN >= totalN) {
                            Text("Já pagas precisa ser menor que o total — senão a dívida está quitada.", color = Color(0xFFBE123C), fontSize = 12.sp)
                        }
                        EntryField("calendar", "Próxima parcela vence em",
                            date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))?.let { "$it · ${debtDayLabel(date)}" } ?: "Escolher data",
                            placeholder = date == null) { focus.clearFocus(); leaveKeypad(); sheet = "date" }
                    }

                    TextRow("percent", rate, { v -> rate = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                        "Juros ao mês (opcional)", suffix = "% a.m.", keyboardType = KeyboardType.Decimal,
                        isError = DebtEntry.parseRateBp(rate) == null, onFocus = { textFocused = it; if (it) leaveKeypad() })

                    if (parcelada) {
                        val estimado = DebtEntry.saldoEstimadoCents(parcelaCents.toLongOrNull() ?: 0, totalN, pagasN)
                        val saldoActive = keypadTarget == "saldo"
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(EntryShape).background(Color.White)
                            .border(if (saldoActive) 1.5.dp else 1.dp, if (saldoActive) EntryInk else EntryLine.copy(alpha = .65f), EntryShape)
                            .clickable { focus.clearFocus(); keypadTarget = "saldo" }
                            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            EntryGlyph("wallet", EntryMuted)
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text("Saldo devedor hoje", color = EntryMuted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (saldoCents.isEmpty()) "R$ ${debtAmountText(estimado.toString())} · estimado" else "R$ ${debtAmountText(saldoCents)}",
                                    color = if (saldoCents.isEmpty()) EntryMuted else EntryInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (saldoCents.isNotEmpty()) {
                                Text("Usar estimativa", color = Emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(CircleShape).clickable { saldoCents = "" }.padding(6.dp))
                            }
                        }
                        Text("Informe só se o banco mostrar um valor diferente (juros embutidos, quitação antecipada).",
                            color = EntryMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    }

                    if (parcelada && valid) {
                        val restantes = DebtEntry.restantes(totalN, pagasN)
                        val ultima = date!!.plusMonths((restantes - 1).toLong())
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(EntryShape).background(Color(0xFFECFDF5))
                            .border(1.dp, Color(0xFFA7F3D0), EntryShape).padding(14.dp)) {
                            Text("Faltam $restantes parcela${if (restantes > 1) "s" else ""} de R$ ${debtAmountText(parcelaCents)}",
                                color = Color(0xFF065F46), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Aparece na Agenda ${debtDayLabel(date)} até ${MONTH_NAMES_PT[ultima.monthValue - 1].lowercase()} de ${ultima.year}.",
                                color = Color(0xFF047857), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }

                    Button(onClick = {
                        focus.clearFocus()
                        onSave(DebtEntry.build(initial, nome, parcelada, mainCents.toLong(), totalN, pagasN, date, rate, saldoCents.toLongOrNull()))
                    }, enabled = valid, shape = EntryShape,
                        colors = ButtonDefaults.buttonColors(containerColor = EntryInk, contentColor = Color.White, disabledContainerColor = EntryLine, disabledContentColor = EntryMuted),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, disabledElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).height(52.dp)) {
                        Text(if (initial != null) "Salvar alterações" else "Salvar dívida", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        val keypad: @Composable () -> Unit = {
            EntryKeypad(visible = showKeypad, enabled = true, onKey = { key ->
                focus.clearFocus()
                when {
                    keypadTarget == "saldo" -> saldoCents = TransactionEntry.key(saldoCents, key)
                    parcelada -> parcelaCents = TransactionEntry.key(parcelaCents, key)
                    else -> abertoCents = TransactionEntry.key(abertoCents, key)
                }
            })
        }
        if (maxWidth > maxHeight) {
            val formWeight by animateFloatAsState(if (showKeypad) .6f else 1f, tween(220), label = "debtFormWeight")
            Row(Modifier.fillMaxSize().imePadding(), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(formWeight).fillMaxHeight()) { form() }
                Box(Modifier.weight((1f - formWeight).coerceAtLeast(0.0001f))) { keypad() }
            }
        } else {
            Column(Modifier.fillMaxSize().imePadding()) {
                Box(Modifier.weight(1f)) { form() }
                if (showKeypad) {
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(start = 16.dp, end = 8.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (keypadTarget == "saldo") "Saldo devedor" else if (parcelada) "Valor da parcela" else "Valor em aberto",
                            color = EntryMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { leaveKeypad() }) { Text("OK", color = EntryInk, fontWeight = FontWeight.Bold) }
                    }
                }
                keypad()
            }
        }
    }
    if (sheet == "date") {
        EntryCalendarSheet(selected = date ?: LocalDate.now(), onSelect = { dateString = it.toString(); sheet = null }, onDismiss = { sheet = null }, showYesterday = false)
    }
}

@Composable
private fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(42.dp).clip(CircleShape).background(Color(0xFFF4F4F5)).padding(3.dp)) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            val bg by animateColorAsState(if (isSelected) EntryInk else Color.Transparent, finaiTween(FinaiMotion.Quick), label = "debtTabBg")
            val fg by animateColorAsState(if (isSelected) Color.White else Color(0xFF71717A), finaiTween(FinaiMotion.Quick), label = "debtTabFg")
            Box(Modifier.weight(1f).fillMaxHeight()
                .then(if (isSelected) Modifier.shadow(3.dp, CircleShape) else Modifier)
                .clip(CircleShape).background(bg)
                .selectable(isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center) {
                Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TextRow(
    icon: String, value: String, onChange: (String) -> Unit, placeholder: String,
    onFocus: (Boolean) -> Unit, suffix: String? = null, keyboardType: KeyboardType = KeyboardType.Text, isError: Boolean = false,
) {
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(EntryShape).background(Color.White)
        .border(1.dp, if (isError) Color(0xFFFDA4AF) else EntryLine.copy(alpha = .65f), EntryShape)
        .padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        EntryGlyph(icon, EntryMuted)
        Spacer(Modifier.width(14.dp))
        BasicTextField(value, onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = EntryFont, fontSize = 14.sp, color = EntryInk), cursorBrush = SolidColor(EntryInk),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).padding(vertical = 15.dp).onFocusChanged { onFocus(it.isFocused) }
                .semantics { contentDescription = placeholder },
            decorationBox = { field -> Box { if (value.isEmpty()) Text(placeholder, color = Color(0xFF808080), fontSize = 14.sp); field() } })
        if (suffix != null) Text(suffix, color = EntryMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stepper(
    label: String, value: String, onChange: (String) -> Unit, min: Int, modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit, max: Int = 600,
) {
    val focus = LocalFocusManager.current
    val n = value.toIntOrNull() ?: 0
    Column(modifier.clip(EntryShape).background(Color.White).border(1.dp, EntryLine.copy(alpha = .65f), EntryShape).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = EntryMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            StepButton("minus", "Diminuir $label", enabled = n > min) { focus.clearFocus(); onChange((n - 1).coerceAtLeast(min).toString()) }
            BasicTextField(value, { v -> onChange(v.filter(Char::isDigit).take(3)) }, singleLine = true,
                textStyle = TextStyle(fontFamily = EntryFont, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = EntryInk, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(EntryInk),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.width(56.dp).onFocusChanged { onFocus(it.isFocused) }.semantics { contentDescription = label },
                decorationBox = { field -> Box(contentAlignment = Alignment.Center) { if (value.isEmpty()) Text("–", color = Color(0xFFD4D4D8), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); field() } })
            StepButton("plus", "Aumentar $label", enabled = n < max) { focus.clearFocus(); onChange((n + 1).coerceAtMost(max).coerceAtLeast(min).toString()) }
        }
    }
}

@Composable
private fun StepButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(30.dp).background(if (enabled) Color(0xFFF4F4F5) else Color(0xFFFAFAFA), CircleShape), contentAlignment = Alignment.Center) {
            EntryGlyph(glyph, if (enabled) EntryInk else Color(0xFFD4D4D8), description, 16)
        }
    }
}

/** Vencimento no último dia do mês vira "todo fim de mês" (DebtSchedule segue o fim de mês). */
private fun debtDayLabel(date: java.time.LocalDate): String =
    if (date.dayOfMonth == date.lengthOfMonth()) "todo último dia do mês" else "todo dia ${date.dayOfMonth}"
