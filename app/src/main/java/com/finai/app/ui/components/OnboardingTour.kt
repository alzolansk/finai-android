package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.ui.theme.FinaiColors

private data class TourStep(val icon: ImageVector, val title: String, val description: String)

private val tourSteps = listOf(
    TourStep(
        Icons.Filled.AutoAwesome,
        "Bem-vindo ao FinAI",
        "Seus lançamentos, contas, objetivos e dívidas ficam só neste aparelho. Vamos conhecer as principais telas em poucos passos.",
    ),
    TourStep(
        Icons.Filled.Home,
        "Início",
        "Veja quanto você pode gastar hoje, o progresso dos seus objetivos e o que vence nos próximos 7 dias.",
    ),
    TourStep(
        Icons.Filled.CalendarMonth,
        "Agenda",
        "Acompanhe contas a pagar e a receber mês a mês, e marque uma conta como paga com um toque.",
    ),
    TourStep(
        Icons.Filled.TrackChanges,
        "Objetivos e Dívidas",
        "Cadastre metas de compra, viagem ou reserva, e veja a ordem certa de quitar dívidas pelo custo do juro.",
    ),
    TourStep(
        Icons.Filled.PieChart,
        "Limites",
        "Controle quanto já gastou por categoria e identifique assinaturas pouco usadas.",
    ),
    TourStep(
        Icons.Filled.Add,
        "Ação rápida",
        "Toque no botão + na barra inferior para lançar um gasto, importar uma fatura, simular uma compra ou criar um objetivo.",
    ),
    TourStep(
        Icons.Filled.AutoAwesome,
        "Assistente",
        "Pergunte sobre suas finanças a qualquer momento pelo ícone \"Assistente\". Configure uma IA gratuita no ícone de engrenagem para ativar as respostas.",
    ),
)

/**
 * Tour guiado da primeira abertura (e de "Rever tour guiado" nas
 * Configurações). Overlay de tela cheia, bloqueia toques no que está atrás
 * (como [Scrim] faz para os demais overlays do app) e nunca reaparece sozinho
 * depois de concluído/pulado — quem decide isso é
 * [com.finai.app.state.AppViewModel.onboardingComplete], persistido em
 * [com.finai.app.data.prefs.FinaiPreferences].
 */
@Composable
fun OnboardingTour(onFinish: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = tourSteps[stepIndex]
    val isLast = stepIndex == tourSteps.lastIndex
    // Voltar do sistema volta um passo do tour; no primeiro, segue o padrão do Android.
    BackHandler(enabled = stepIndex > 0) { stepIndex-- }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = FinaiColors.Surface,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    tourSteps.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i <= stepIndex) FinaiColors.Emerald else FinaiColors.BorderSubtle),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(FinaiColors.EmeraldSoftBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(step.icon, contentDescription = null, tint = FinaiColors.EmeraldDark)
                }
                Spacer(Modifier.height(14.dp))
                Text(step.title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(step.description, fontSize = 13.sp, lineHeight = 19.sp, color = FinaiColors.TextSecondary)
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip) {
                        Text("Pular", color = FinaiColors.TextTertiary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { if (isLast) onFinish() else stepIndex += 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
                    ) {
                        Text(if (isLast) "Concluir" else "Próximo")
                    }
                }
            }
        }
    }
}
