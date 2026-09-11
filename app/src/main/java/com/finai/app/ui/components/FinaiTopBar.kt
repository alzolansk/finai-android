package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.ui.theme.FinaiColors

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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            // Backdrop blur has no direct Compose equivalent; a near-opaque flat fill stands in.
            .background(FinaiColors.Background.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(FinaiColors.Ink),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(FinaiColors.Emerald))
            }
            Column {
                Text("FinAI", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(screenLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = FinaiColors.TextMuted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenNotifications),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = "Avisos", tint = FinaiColors.TextTertiary)
                if (notifCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
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
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
                    .background(FinaiColors.Surface)
                    .clickable(onClick = onOpenChat)
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(FinaiColors.EmeraldSoftBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = FinaiColors.EmeraldDark,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text("Assistente", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextBody)
            }
        }
    }
}

/** Standard horizontal + bottom padding used by every screen's scrollable content, clearing the floating nav. */
val ScreenContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 132.dp)
