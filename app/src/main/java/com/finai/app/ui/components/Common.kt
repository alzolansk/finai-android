package com.finai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween

/** Mirrors the prototype's `.fin-press` class — a light scale-down while pressed. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    return this.scale(scale)
}

@Composable
fun rememberPressInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** A rounded progress track + fill, used for goals, debts and budgets. */
@Composable
fun ProgressTrack(
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 7.dp,
    trackColor: Color = Color(0xFFF4F4F5),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(99.dp))
                .background(fillColor),
        )
    }
}

/** Small uppercase pill, used for badges (goal status, budget/decision tags). */
@Composable
fun PillTag(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text = text, color = foreground, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

/**
 * Fades content in the first time it's composed — used for empty states
 * across Agenda/Objetivos/Dívidas/Limites so "nothing here yet" doesn't just
 * pop in. A plain `AnimatedVisibility(visible = true, ...)` would NOT do
 * this: its initial/target state are equal on first remember, so nothing
 * would actually transition. This forces the initial state to `false`, then
 * flips it right away.
 */
@Composable
fun FadeInAppear(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = state, enter = fadeIn(finaiTween(FinaiMotion.Standard)), modifier = modifier, content = { content() })
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Confirmação genérica antes de excluir um dado persistido (lançamento,
 * conta, objetivo, dívida) — nenhuma dessas ações some com um único toque
 * mais. [description] deve identificar o item de verdade (nome + valor),
 * nunca um texto genérico tipo "tem certeza?" — é o que faz a confirmação
 * útil em vez de só um obstáculo a mais.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Excluir",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = FinaiColors.RoseDark) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(description, color = FinaiColors.TextSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.RoseDark, contentColor = Color.White),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Ação em texto com alvo de toque de 48 dp (Fase 7, item 4): "Ver todos", "Abrir agenda" e
 * afins eram `Text` com `clickable` direto, com uns 16 dp de altura.
 */
@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = FinaiColors.EmeraldDark) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Título de seção com uma ação opcional à direita, alinhados pelo centro. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            subtitle?.let { Text(it, fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (action != null && onAction != null) TextAction(action, onAction)
    }
}

/**
 * Estado vazio que orienta (Fase 7, item 9): diz o que falta e oferece a ação concreta que
 * resolve, em vez de só "nada aqui". "Sem cadastro" é diferente de "zero real" — este é o
 * primeiro caso.
 */
@Composable
fun EmptyStateCard(
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = if (actionLabel != null) 2.dp else 14.dp),
    ) {
        Text(message, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(end = 8.dp))
        Row(modifier = Modifier.offset(x = (-6).dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (actionLabel != null && onAction != null) TextAction("$actionLabel ›", onAction)
            if (secondaryLabel != null && onSecondary != null) TextAction("$secondaryLabel ›", onSecondary, color = FinaiColors.TextSecondary)
        }
    }
}

/**
 * Botão de ação frequente, 48 dp (Fase 7, item 8): "Paguei a parcela", "Guardar",
 * "Marcar extra" deixaram de ser texto pequeno clicável.
 */
@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (primary) Modifier.background(FinaiColors.EmeraldDark)
                else Modifier.border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(12.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (primary) Color.White else FinaiColors.TextPrimary)
    }
}

/** Chip de filtro com alvo de 48 dp (Importar, Fatura). */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) FinaiColors.Ink else FinaiColors.Surface)
            .border(1.dp, if (selected) FinaiColors.Ink else FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else FinaiColors.TextSecondary, maxLines = 1)
    }
}

/** Uma opção do menu ⋮. [destructive] pinta de vermelho (excluir). */
data class OverflowAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * Menu de três pontos (Fase 7, item 8): editar e excluir saem da linha como texto pequeno
 * e vão para cá. Excluir continua pedindo confirmação ([ConfirmDeleteDialog]) em quem chama.
 */
@Composable
fun OverflowMenu(actions: List<OverflowAction>, contentDescription: String, modifier: Modifier = Modifier, tint: Color = FinaiColors.TextTertiary) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        androidx.compose.material3.IconButton(onClick = { open = true }) {
            Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = contentDescription, tint = tint)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { a ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(a.label, fontSize = 14.sp, color = if (a.destructive) FinaiColors.RoseDark else FinaiColors.TextPrimary) },
                    onClick = { open = false; a.onClick() },
                )
            }
        }
    }
}
