package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.DiffTone
import com.finai.app.domain.Reconciliation
import com.finai.app.domain.diffNote
import com.finai.app.domain.diffTone
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun reais(cents: Long) = (if (cents < 0) "− " else "") + formatBrl(kotlin.math.abs(cents) / 100.0)

/**
 * Conciliar saldo (ref. 3b): o usuário digita o saldo do app do banco, vê a diferença em tempo real
 * e lança o ajuste. A folha abre sobre a Início, com o fundo escurecido.
 *
 * [registeredCents] é o saldo que o FinAI mostra agora. [onConfirm] recebe a conta e o saldo do banco
 * e grava o ajuste (se houver diferença) e a data da conciliação.
 */
@Composable
fun ReconcileSheet(
    registeredCents: Long,
    accounts: List<String>,
    onDismiss: () -> Unit,
    onConfirm: suspend (account: String, bankCents: Long) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var bank by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf(accounts.firstOrNull().orEmpty().ifBlank { DEFAULT_ACCOUNT }) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val bankCents = Reconciliation.parse(bank)
    val diff = Reconciliation.difference(bankCents, registeredCents)
    val tone = diffTone(diff)
    val zero = diff == 0L

    EntrySheet("Conciliar saldo", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                "Conta $account · abra o app do banco e digite o saldo exibido agora.",
                color = EntryMuted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            if (accounts.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.take(3).forEach { name ->
                        val selected = name == account
                        Box(
                            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (selected) FinaiColors.InkStrong else FinaiColors.SurfaceMuted)
                                .clickable(role = Role.RadioButton, enabled = !saving) { account = name }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                color = if (selected) FinaiColors.OnInkStrong else FinaiColors.TextSecondary)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(FinaiColors.SurfaceMuted).padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Saldo registrado no FinAI", color = FinaiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(reais(registeredCents), color = EntryInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(FinaiColors.Surface)
                    .border(1.5.dp, FinaiColors.Emerald, RoundedCornerShape(15.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Saldo no banco", color = FinaiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("R$", color = EntryMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                BasicTextField(
                    // O cursor fica sempre no fim: a máscara reescreve o texto e os dígitos entram em ordem.
                    value = TextFieldValue(bank, TextRange(bank.length)), onValueChange = { bank = Reconciliation.mask(it.text); error = null },
                    enabled = !saving, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = EntryInk, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(FinaiColors.Emerald),
                    modifier = Modifier.width(132.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (bank.isEmpty()) Text("0,00", color = FinaiColors.TextDisabled, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            inner()
                        }
                    },
                )
            }

            // Verde-escuro sem diferença (ou valor inválido), vinho se o banco tem menos, azul-escuro se tem mais.
            Column(
                Modifier.fillMaxWidth().padding(top = 3.dp).clip(RoundedCornerShape(18.dp)).background(
                    when (tone) {
                        DiffTone.Neutral -> Color(0xFF065F46)
                        DiffTone.BankLess -> Color(0xFF881337)
                        DiffTone.BankMore -> Color(0xFF1E3A8A)
                    },
                ).padding(16.dp),
            ) {
                Text("DIFERENÇA", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.7f))
                Text(
                    if (diff == null) "—" else (if (diff > 0) "+ " else "") + reais(diff),
                    fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(top = 4.dp),
                )
                Text(diffNote(diff), fontSize = 13.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = 0.88f), modifier = Modifier.padding(top = 6.dp))
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(15.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("Ajuste será lançado como", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EntryMuted)
                Text(
                    "Ajuste de saldo · ${"%02d/%02d".format(today.dayOfMonth, today.monthValue)} · fora dos limites",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = EntryInk, modifier = Modifier.padding(top = 2.dp),
                )
            }

            error?.let { Text(it, color = FinaiColors.RoseDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }

            val closes = diff == null
            val label = when {
                saving -> "Salvando…"
                diff == null || zero -> "Fechar"
                else -> "Lançar ajuste de " + (if (diff > 0) "+" else "−") + formatBrl(kotlin.math.abs(diff) / 100.0)
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 3.dp).heightIn(min = 52.dp).clip(RoundedCornerShape(17.dp))
                    .background(FinaiColors.InkStrong)
                    .clickable(role = Role.Button, enabled = !saving) {
                        // Sem valor digitado só fecha; com o valor (mesmo sem diferença) registra a conciliação.
                        if (closes || bankCents == null) { onDismiss() } else {
                            saving = true; error = null
                            scope.launch {
                                try { onConfirm(account, bankCents); onDismiss() }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = "Não foi possível lançar o ajuste. Tente novamente." }
                                finally { saving = false }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = FinaiColors.OnInkStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private const val DEFAULT_ACCOUNT = "Carteira"
