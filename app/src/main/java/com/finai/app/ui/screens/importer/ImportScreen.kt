package com.finai.app.ui.screens.importer

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
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
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.ImportStepItem
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors

@Composable
fun ImportScreen(importStage: Int, onRunImport: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Importar fatura", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                Text(
                    "PDF, planilha ou foto — a IA lê e classifica", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.5.dp, FinaiColors.BorderSubtle, RoundedCornerShape(22.dp))
                    .background(FinaiColors.Surface)
                    .padding(vertical = 28.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(FinaiColors.EmeraldSoftBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(24.dp))
                }
                Text(
                    FinaiFixtures.importTitle(importStage), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    FinaiFixtures.importSubtitle(importStage), fontSize = 12.sp, lineHeight = 18.sp,
                    color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 5.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 15.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(FinaiColors.Emerald)
                        .clickable(onClick = onRunImport)
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                ) {
                    Text(FinaiFixtures.importCta(importStage), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                    .background(FinaiColors.Surface)
                    .padding(16.dp),
            ) {
                Text(
                    "O que a IA faz com o arquivo", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = FinaiColors.TextPrimary, modifier = Modifier.padding(bottom = 12.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    FinaiFixtures.importSteps(importStage).forEachIndexed { index, step ->
                        ImportStepRow(index + 1, step)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportStepRow(number: Int, step: ImportStepItem) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (step.done) FinaiColors.EmeraldSoftBg else FinaiColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (step.done) "✓" else number.toString(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                color = if (step.done) FinaiColors.EmeraldDark else FinaiColors.TextMuted,
            )
        }
        Text(
            step.text, fontSize = 12.5.sp, lineHeight = 18.sp,
            color = if (step.done) FinaiColors.TextPrimary else FinaiColors.TextTertiary,
        )
    }
}
