package com.finai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.navigation.FinaiDestination
import com.finai.app.ui.theme.FinaiColors

/**
 * The dark floating pill nav from the prototype: Início / Agenda / (+) /
 * Objetivos / Dívidas. Orçamentos and Importar are reachable from inside
 * screens, not from here — same as the design.
 */
@Composable
fun FinaiBottomNav(
    current: FinaiDestination,
    addOpen: Boolean,
    onSelect: (FinaiDestination) -> Unit,
    onToggleAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FinaiColors.Ink.copy(alpha = 0.96f))
            .border(1.dp, FinaiColors.InkBorder, RoundedCornerShape(20.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NavTab(Icons.Filled.Home, "Início", current == FinaiDestination.Home, Modifier.weight(1f)) {
            onSelect(FinaiDestination.Home)
        }
        NavTab(Icons.Filled.CalendarMonth, "Agenda", current == FinaiDestination.Agenda, Modifier.weight(1f)) {
            onSelect(FinaiDestination.Agenda)
        }

        val rotation by animateFloatAsState(if (addOpen) 45f else 0f, label = "fabRotation")
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(width = 50.dp, height = 46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(FinaiColors.Emerald)
                .clickable(onClick = onToggleAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Ações rápidas",
                tint = Color.White,
                modifier = Modifier.size(22.dp).rotate(rotation),
            )
        }

        NavTab(Icons.Filled.TrackChanges, "Objetivos", current == FinaiDestination.Goals, Modifier.weight(1f)) {
            onSelect(FinaiDestination.Goals)
        }
        NavTab(Icons.Filled.CreditCard, "Dívidas", current == FinaiDestination.Debts, Modifier.weight(1f)) {
            onSelect(FinaiDestination.Debts)
        }
    }
}

@Composable
private fun NavTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) FinaiColors.InkBorder else Color.Transparent
    val fg = if (selected) Color.White else FinaiColors.TextMuted
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(19.dp))
        Text(label, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
