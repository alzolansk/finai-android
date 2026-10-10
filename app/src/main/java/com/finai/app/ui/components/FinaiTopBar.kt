package com.finai.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Barra das telas internas (Configurações, Parcelamentos): o mesmo cabeçalho 4a
 * ([FinaiPageHeader]) com a seta no lugar do símbolo, sem as ações e sempre no estado
 * rolado, porque essas telas não têm título grande no conteúdo. Fundo sólido: elas não
 * ficam por cima da fonte do vidro.
 */
@Composable
fun FinaiSettingsTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    FinaiPageHeader(
        title = title,
        subtitle = subtitle,
        leading = HeaderLeading.Back(onNavigateBack),
        solid = true,
        modifier = modifier,
    )
}

/**
 * Padding padrão do conteúdo rolável das telas. Em cima, o espaço do cabeçalho 4a
 * (status bar + 56 dp + 6 dp): o conteúdo rola por baixo do vidro dele. Embaixo, os 140 dp
 * da barra de vidro mais o inset da navegação do sistema, para o último item ficar visível
 * acima dela.
 */
val ScreenContentPadding: PaddingValues
    @Composable get() = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = PageHeaderSpec.contentTop,
        bottom = FinaiNavBarSpec.ContentBottomPadding +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
