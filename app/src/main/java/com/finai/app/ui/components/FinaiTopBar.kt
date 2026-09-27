package com.finai.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.R
import com.finai.app.ui.theme.FinaiColors

// Tons tirados da própria folha da logo (res/drawable-nodpi/finai_mark.png): o verde claro
// do topo, o médio da dobra e o escuro da sombra interna.
private val BrandLeafMist = Color(0xFFF1FBF3)
private val BrandLeafWash = Color(0xFFDDF6E3)
private val BrandLeafEdge = Color(0xFFBFEBCB)
private val BrandLeafDeep = Color(0xFF0B6B3A)

/** Botão quadrado da topbar no verde da marca: fundo em degradê suave, contorno e ícone da folha. */
@Composable
private fun BrandIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(BrandLeafMist, BrandLeafWash)))
            .border(1.dp, BrandLeafEdge, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Sticky top bar: FinAI mark + screen label on the left, notification bell
 * (with unread badge) and the "Assistente" chat entry point on the right.
 * Mirrors the `top bar` block in the prototype.
 */
@Composable
fun FinaiTopBar(
    screenLabel: String,
    notifCount: Int,
    onOpenNotifications: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onBellCenterX: (Float) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Backdrop blur has no direct Compose equivalent; a near-opaque flat fill stands in.
            .background(FinaiColors.Background)
            .border(width = 1.dp, color = FinaiColors.BorderHairline)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.finai_mark),
                contentDescription = null,
                modifier = Modifier.height(34.dp),
            )
            Column {
                Text("FinAI", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                Text(screenLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandIconButton(onClick = onOpenAiSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Configurações", tint = BrandLeafDeep, modifier = Modifier.size(20.dp))
            }
            // O painel de avisos aponta a seta para o centro do sino e cresce a partir dele.
            BrandIconButton(
                onClick = onOpenNotifications,
                modifier = Modifier.onGloballyPositioned { onBellCenterX(it.boundsInRoot().center.x) },
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Avisos", tint = BrandLeafDeep, modifier = Modifier.size(20.dp))
                // A new alert popping the badge in (rather than just appearing) is the one
                // place a little spring feels right — it's rare and meant to catch the eye.
                val reducedMotion = com.finai.app.ui.theme.rememberReducedMotion()
                androidx.compose.animation.AnimatedVisibility(
                    visible = notifCount > 0,
                    enter = if (reducedMotion) androidx.compose.animation.EnterTransition.None else {
                        androidx.compose.animation.scaleIn(
                            androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy),
                        ) + androidx.compose.animation.fadeIn()
                    },
                    exit = if (reducedMotion) androidx.compose.animation.ExitTransition.None else {
                        androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
                    },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(1.5.dp)
                            .clip(CircleShape)
                            .background(FinaiColors.Rose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(notifCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(FinaiColors.Ink)
                    .clickable(onClick = onOpenChat)
                    .padding(start = 6.dp, end = 11.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(FinaiColors.Emerald),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text("Assistente", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** A quiet navigation bar for secondary flows, without the app-wide action shortcuts. */
@Composable
fun FinaiSettingsTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FinaiColors.Background)
            .border(width = 1.dp, color = FinaiColors.BorderHairline)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FinaiColors.SurfaceMuted),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = FinaiColors.TextBody, modifier = Modifier.size(20.dp))
        }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
    }
}

/** Standard horizontal + bottom padding used by every screen's scrollable content, clearing the floating nav. */
val ScreenContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 132.dp)
