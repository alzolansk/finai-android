package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.ui.theme.FinaiColors

/** Estado de uma linha de dinheiro. Não há caixinha: o estado é o selo e o valor esmaecido. */
enum class MoneyRowState(val badge: String?, val speech: String?) {
    /** Ainda vai acontecer (ou ainda não foi baixado). */
    Pending(null, "pendente"),
    /** Saída já paga. */
    Paid("Pago", "pago"),
    /** Entrada já recebida. */
    Received("Recebido", "recebido"),
    /** Lançamento sem estado (gasto já feito, ajuste): sem selo e sem esmaecer. */
    Plain(null, null),
}

/** Fundo da linha: transparente (dentro de um painel), cartão, renda (verde) ou menor saldo (âmbar). */
enum class MoneyRowBackground { None, Card, Income, Low }

/**
 * A linha de lista do app v2 (planning-v2 §6, Fase 0): avatar de 38 dp, nome + selo, subtítulo,
 * valor à direita (esmaecido quando já foi pago) e, opcionalmente, "fica R$ X" embaixo do valor.
 *
 * O toque é na linha inteira (60 dp no mínimo, `Role.Button`) e o estado é lido pelo TalkBack
 * ("pago"/"pendente"). **Nenhuma caixinha de seleção**: tocar paga, tocar de novo reabre, e quem
 * chama decide isso em [onClick]. Só desenha; não conhece Room nem calcula nada.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoneyRow(
    title: String,
    amount: String,
    avatar: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: MoneyRowState = MoneyRowState.Plain,
    /** Entrada de dinheiro: avatar e valor em verde. */
    income: Boolean = false,
    /** "fica R$ 1.220": saldo depois deste movimento. */
    balanceAfter: String? = null,
    /** Fundo verde suave (a renda que abre o período na Agenda "A seguir"). */
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    background: MoneyRowBackground = if (highlighted) MoneyRowBackground.Income else MoneyRowBackground.None,
    /** Cor do "fica R$ X" (âmbar no menor saldo, rosa se negativo). */
    balanceAfterColor: Color? = null,
    /** Toque longo: as outras ações (editar, excluir, itens da fatura), sem botão fixo na linha. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    /** Rótulo da ação do toque, lido pelo TalkBack ("marcar como paga", "reabrir"). */
    onClickLabel: String? = null,
) {
    val settled = state == MoneyRowState.Paid || state == MoneyRowState.Received
    val shape = RoundedCornerShape(14.dp)
    val a11y = buildString {
        append(title).append(", ").append(if (income) "entrada " else "saída ").append(amount.trimStart('−', '-', '+', ' '))
        subtitle?.let { append(", ").append(it) }
        state.speech?.let { append(", ").append(it) }
        balanceAfter?.let { append(", ").append(it) }
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .let { base ->
                when (background) {
                    MoneyRowBackground.None -> base
                    MoneyRowBackground.Card -> base.background(FinaiColors.Surface).border(1.dp, FinaiColors.BorderHairline, shape)
                    MoneyRowBackground.Income -> base.background(FinaiColors.EmeraldSoftBg).border(1.dp, FinaiColors.EmeraldSoftBorder, shape)
                    MoneyRowBackground.Low -> base.background(FinaiColors.AmberSoftBg).border(1.dp, FinaiColors.Amber.copy(alpha = .35f), shape)
                }
            }
            .let {
                when {
                    onClick != null && onLongClick != null -> it.combinedClickable(
                        role = Role.Button, onClickLabel = onClickLabel, onLongClickLabel = onLongClickLabel,
                        onLongClick = onLongClick, onClick = onClick,
                    )
                    onClick != null -> it.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                    else -> it
                }
            }
            .heightIn(min = 60.dp)
            .padding(horizontal = if (background == MoneyRowBackground.None) 4.dp else 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = a11y
                state.speech?.let { stateDescription = it }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(if (income) FinaiColors.EmeraldSoftBg else FinaiColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                avatar.take(2), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (income) FinaiColors.EmeraldDark else FinaiColors.TextSecondary,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                state.badge?.let { badge ->
                    Text(
                        badge, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(FinaiColors.EmeraldSoftBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            subtitle?.let { Text(it, fontSize = 13.sp, color = FinaiColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                amount, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = if (income) FinaiColors.EmeraldDark else FinaiColors.TextPrimary,
                modifier = Modifier.alpha(if (settled) 0.55f else 1f),
            )
            balanceAfter?.let {
                Text(
                    it, fontSize = 12.sp, color = balanceAfterColor ?: FinaiColors.TextMuted,
                    fontWeight = if (balanceAfterColor != null) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
