package com.finai.app.ui.components.capture

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.capture.isTest
import com.finai.app.data.capture.state
import com.finai.app.data.local.entity.SugestaoLancamentoEntity
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.brl2
import com.finai.app.domain.capture.MovementKind
import com.finai.app.domain.capture.SuggestionState
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.EmptyStateCard
import com.finai.app.ui.components.FinaiSettingsTopBar
import com.finai.app.ui.components.PillTag
import com.finai.app.ui.components.SectionHeader
import com.finai.app.ui.components.TextAction
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.rememberReducedMotion

/**
 * "Para confirmar": as sugestões que ainda esperam resposta (a mais antiga primeiro) e as
 * resolvidas há pouco. Tocar abre o quadro da sugestão. Camada por cima de tudo, como a fatura.
 */
@Composable
fun PendingSuggestionsScreen(
    visible: Boolean,
    pending: List<SugestaoLancamentoEntity>,
    resolved: List<SugestaoLancamentoEntity>,
    onOpen: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(visible) { onBack() }
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(if (reduced) 0 else 300, easing = FastOutSlowInEasing)) { it },
        exit = slideOutHorizontally(tween(if (reduced) 0 else 240, easing = FastOutSlowInEasing)) { it },
    ) {
        Column(Modifier.fillMaxSize().background(FinaiColors.Background)) {
            FinaiSettingsTopBar(
                title = "Para confirmar",
                subtitle = when (pending.size) { 0 -> "Nada esperando"; 1 -> "1 movimentação"; else -> "${pending.size} movimentações" },
                onNavigateBack = onBack,
            )
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (pending.isEmpty()) {
                    item {
                        EmptyStateCard(
                            "Nada para confirmar. Quando um app escolhido avisar uma compra ou um Pix, a movimentação aparece aqui " +
                                "para você conferir antes de entrar nas suas contas.",
                            actionLabel = "Lançamentos por notificações", onAction = onOpenSettings,
                        )
                    }
                }
                items(pending, key = { it.id }) { s -> SuggestionRow(s, onClick = { onOpen(s.id) }) }
                if (resolved.isNotEmpty()) {
                    item { SectionHeader("Resolvidas recentemente", modifier = Modifier.padding(top = 14.dp)) }
                    items(resolved.take(20), key = { "r-${it.id}" }) { s ->
                        SuggestionRow(
                            s, onClick = { onOpen(s.id) },
                            trailing = if (s.state == SuggestionState.Ignored) ({ TextAction("Restaurar", { onRestore(s.id) }) }) else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(s: SugestaoLancamentoEntity, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val kind = runCatching { MovementKind.valueOf(s.tipo) }.getOrDefault(MovementKind.Purchase)
    val value = s.valorCentavos?.let(::brl2) ?: "Valor a confirmar"
    val what = s.descricao.ifBlank { kind.label }
    val stateLabel = when (s.state) {
        SuggestionState.Pending -> null
        SuggestionState.Confirmed -> "Salva"
        SuggestionState.Ignored -> "Ignorada"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                if (s.isTest) PillTag("Teste do FinAI", FinaiColors.AmberSoftBg, FinaiColors.AmberDark)
                if (s.state == SuggestionState.Pending && (kind.reviewOnly || s.valorCentavos == null)) {
                    PillTag("Precisa revisar", FinaiColors.RoseSoftBg, FinaiColors.RoseDark)
                }
            }
            Text(what, fontSize = 13.sp, color = FinaiColors.TextBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(kind.label, EntryOptions.dayMonth(s.data.toLocalDate()), s.appNome, stateLabel).joinToString(" · "),
                fontSize = 12.sp, color = FinaiColors.TextTertiary, maxLines = 2,
            )
        }
        trailing?.invoke()
    }
}

/**
 * Indicador discreto de sugestões na fila, nas telas principais: "2 para confirmar". Não abre
 * nada sozinho; tocar leva à lista.
 */
@Composable
fun PendingSuggestionsPill(count: Int, visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible && count > 0, modifier = modifier,
        enter = fadeIn(tween(if (reduced) 0 else 200)) + slideInVertically(tween(if (reduced) 0 else 240)) { -it / 2 },
        exit = fadeOut(tween(if (reduced) 0 else 160)) + slideOutVertically(tween(if (reduced) 0 else 160)) { -it / 2 },
    ) {
        val label = if (count == 1) "1 para confirmar" else "$count para confirmar"
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(99.dp))
                .clickable(onClick = onClick)
                .semantics { contentDescription = "$label. Abrir a lista Para confirmar"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .background(FinaiColors.InkStrong, RoundedCornerShape(99.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).background(FinaiColors.Emerald, CircleShape))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.OnInkStrong)
            }
        }
    }
}

/**
 * Aviso passageiro quando uma movimentação chega com um formulário aberto: não cobre nada e
 * não pega o toque; a sugestão espera na fila e o quadro abre quando o formulário fechar.
 */
@Composable
fun QueuedSuggestionToast(visible: Boolean, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible, modifier = modifier,
        enter = fadeIn(tween(if (reduced) 0 else 200)), exit = fadeOut(tween(if (reduced) 0 else 300)),
    ) {
        Text(
            "Movimentação encontrada · fica em Para confirmar",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.OnInkStrong,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 6.dp)
                .background(FinaiColors.InkStrong.copy(alpha = .92f), RoundedCornerShape(99.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
