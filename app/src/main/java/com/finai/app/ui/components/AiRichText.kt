package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.AiReplyFormat
import com.finai.app.ui.theme.FinaiColors

/** Cores do texto de IA — [Light] nos cartões claros, [OnDark] nos cartões escuros (coach). */
data class AiTextPalette(
    val body: Color,
    val strong: Color,
    val money: Color,
    val moneyBg: Color,
    val negative: Color,
    val negativeBg: Color,
    val bullet: Color,
) {
    companion object {
        /** Getter: acompanha o tema claro/escuro a cada leitura. */
        val Light: AiTextPalette get() = AiTextPalette(
            body = FinaiColors.TextBody,
            strong = FinaiColors.TextPrimary,
            money = FinaiColors.EmeraldDark,
            moneyBg = FinaiColors.Emerald.copy(alpha = 0.10f),
            negative = FinaiColors.RoseDark,
            negativeBg = FinaiColors.Rose.copy(alpha = 0.10f),
            bullet = FinaiColors.EmeraldDark,
        )
        val OnDark = AiTextPalette(
            body = Color.White.copy(alpha = 0.78f),
            strong = Color.White,
            money = Color.White,
            moneyBg = Color.White.copy(alpha = 0.14f),
            negative = Color(0xFFFECDD3),
            negativeBg = Color(0xFFBE123C).copy(alpha = 0.25f),
            bullet = Color.White.copy(alpha = 0.6f),
        )
    }
}

/**
 * Texto de IA já formatado: sem asterisco nem título de Markdown, com valores
 * em reais em destaque (negativos em vermelho) e listas como itens — ver
 * [AiReplyFormat]. [showHighlights] desenha os cartões `{{rótulo|valor}}` que
 * só o chat pede.
 */
@Composable
fun AiRichText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.5.sp,
    lineHeight: TextUnit = 18.sp,
    palette: AiTextPalette = AiTextPalette.Light,
    showHighlights: Boolean = false,
) {
    val reply = remember(text) { AiReplyFormat.parse(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showHighlights && reply.highlights.isNotEmpty()) {
            HighlightRow(reply.highlights, palette)
        }
        reply.blocks.forEach { block ->
            val annotated = remember(block, palette) { block.spans.toAnnotated(palette) }
            when (block) {
                is AiReplyFormat.Block.Paragraph ->
                    Text(annotated, fontSize = fontSize, lineHeight = lineHeight, color = palette.body)
                is AiReplyFormat.Block.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", fontSize = fontSize, lineHeight = lineHeight, color = palette.bullet, fontWeight = FontWeight.Bold)
                    Text(annotated, fontSize = fontSize, lineHeight = lineHeight, color = palette.body)
                }
            }
        }
    }
}

/** Mesmo tratamento de valores, numa linha só (ex.: título de uma decisão). */
@Composable
fun rememberAiAnnotated(text: String, palette: AiTextPalette = AiTextPalette.Light): AnnotatedString =
    remember(text, palette) {
        AiReplyFormat.parse(text).blocks.flatMap { it.spans }.toAnnotated(palette)
    }

@Composable
private fun HighlightRow(highlights: List<AiReplyFormat.Highlight>, palette: AiTextPalette) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        highlights.forEach { h ->
            val valueColor = if (h.negative) palette.negative else palette.money
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (h.negative) palette.negativeBg else palette.moneyBg)
                    .border(1.dp, valueColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    h.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.body,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    h.value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = valueColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun List<AiReplyFormat.Span>.toAnnotated(palette: AiTextPalette): AnnotatedString = buildAnnotatedString {
    forEach { span ->
        val style = when (span.kind) {
            AiReplyFormat.Kind.Money -> SpanStyle(color = palette.money, fontWeight = FontWeight.Bold, background = palette.moneyBg)
            AiReplyFormat.Kind.NegativeMoney -> SpanStyle(color = palette.negative, fontWeight = FontWeight.Bold, background = palette.negativeBg)
            AiReplyFormat.Kind.Percent -> SpanStyle(color = palette.strong, fontWeight = FontWeight.Bold)
            AiReplyFormat.Kind.Plain -> if (span.strong) SpanStyle(color = palette.strong, fontWeight = FontWeight.SemiBold) else null
        }
        if (style == null) append(span.text) else withStyle(style) { append(span.text) }
    }
}
