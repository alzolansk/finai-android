package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.NotificationItem
import com.finai.app.ui.theme.FinaiColors

/** "Avisos da IA" dropdown card, anchored under the top bar's bell icon. */
@Composable
fun NotificationsCard(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FinaiColors.Surface)
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(22.dp)),
    ) {
        Text(
            "Avisos da IA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
        FinaiFixtures.notifications.forEachIndexed { index, notif ->
            if (index > 0) {
                androidx.compose.material3.HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp)
            }
            NotificationRow(notif)
        }
        Text(
            "Fechar", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClose)
                .padding(13.dp),
        )
    }
}

@Composable
private fun NotificationRow(notif: NotificationItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Dot(notif.dotColor, modifier = Modifier.padding(top = 5.dp))
        Column {
            Text(notif.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(
                notif.body, fontSize = 11.5.sp, lineHeight = 16.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
