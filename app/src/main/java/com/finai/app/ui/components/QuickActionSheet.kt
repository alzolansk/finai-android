package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.finai.app.data.model.QuickAction
import com.finai.app.ui.theme.FinaiColors

/** The "+" FAB menu: launch expense, import invoice, buy simulator, new goal. */
@Composable
fun QuickActionSheet(
    onPick: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FinaiColors.Surface)
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(22.dp))
            .padding(8.dp),
    ) {
        FinaiFixtures.quickActions.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .clickable { onPick(action) }
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(action.tint),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(action.mark, color = action.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(action.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                    Text(action.sub, fontSize = 11.sp, color = FinaiColors.TextMuted)
                }
            }
        }
    }
}
