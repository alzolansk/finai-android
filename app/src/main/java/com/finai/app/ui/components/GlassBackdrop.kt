package com.finai.app.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Vidro de verdade para a barra de baixo (playbook 3a): o conteúdo que rola por trás
 * aparece desfocado e mais saturado dentro da pílula, não só um fundo semitransparente.
 *
 * Como funciona: [glassSource] grava o conteúdo das telas num [RenderNode] a cada
 * quadro em que ele é desenhado, e desenha esse nó na tela. A barra ([drawGlass])
 * desenha o mesmo nó outra vez, deslocado para a posição dela, dentro de um segundo nó
 * com `RenderEffect` (blur + saturação). O nó é desenhado por referência: quando a
 * lista rola, o vidro acompanha no mesmo quadro sem a barra precisar se redesenhar.
 *
 * Por que não a biblioteca Haze: a única versão compatível com o Compose 1.6 deste
 * projeto (0.7.x) não tem saturação, só desfoca a partir da API 32 e puxaria o Compose
 * e o core-ktx do app inteiro para versões mais novas. Isto aqui é o mesmo mecanismo,
 * sem dependência nova.
 *
 * Abaixo da API 31 não há `RenderEffect`: [isSupported] é falso, nada é gravado e a
 * barra usa a tinta sólida de fallback.
 */
@Stable
class GlassBackdrop {
    val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // `Any?` e não `RenderNode?`: RenderNode só existe a partir da API 29, e nenhuma
    // classe carregada nas APIs 26–30 deve depender dele. Só código da API 31+ faz o cast.
    internal var contentNode: Any? = null
    internal var sourceInWindow by mutableStateOf(Offset.Unspecified)
    /** Vira true no primeiro quadro gravado; antes disso o vidro usa a tinta de fallback. */
    internal var hasRecording by mutableStateOf(false)
    /** Superfícies de vidro que leem este conteúdo; a fonte regrava o efeito de cada uma. */
    internal val surfaces = mutableListOf<GlassSurface>()
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop() }

/** Marca o conteúdo que aparece desfocado atrás do vidro. Sem efeito abaixo da API 31. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier {
    if (!backdrop.isSupported) return this
    return this
        .onGloballyPositioned { backdrop.sourceInWindow = it.positionInWindow() }
        .drawWithContent {
            val canvas = drawContext.canvas.nativeCanvas
            if (!canvas.isHardwareAccelerated || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                drawContent()
                return@drawWithContent
            }
            val node = backdrop.contentNode as RenderNode? ?: RenderNode("finaiGlassSource").also { backdrop.contentNode = it }
            node.setPosition(0, 0, size.width.roundToInt(), size.height.roundToInt())
            val recording = node.beginRecording()
            try {
                draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(recording), size) {
                    this@drawWithContent.drawContent()
                }
            } finally {
                node.endRecording()
            }
            canvas.drawRenderNode(node)
            // Um nó com RenderEffect vira uma camada em cache no HWUI, e ela não é refeita
            // só porque o nó de conteúdo que ela desenha mudou. Regravar o efeito aqui, no
            // mesmo quadro em que o conteúdo mudou (rolagem), mantém o vidro em dia sem
            // atraso de um quadro.
            for (surface in backdrop.surfaces) surface.recordEffect(node, backdrop.sourceInWindow)
            if (!backdrop.hasRecording) backdrop.hasRecording = true
        }
}

/** Material do vidro. Os valores vêm do playbook; [GlassMaterial.Nav] é o da barra. */
data class GlassMaterial(
    val tint: Color,
    val fallbackTint: Color,
    /** Raio do blur do Android, como o `blurRadius` do Haze que o playbook indica (22 dp). */
    val blurRadius: Dp,
    val saturation: Float,
    val border: Color,
    val topHighlight: Color,
    val shadowColor: Color,
    val shadowOffsetY: Dp,
    /** Blur da sombra no sentido do CSS (`box-shadow ... 32px`). */
    val shadowBlur: Dp,
) {
    companion object {
        val Nav = GlassMaterial(
            tint = Color(0xFF27272A).copy(alpha = 0.42f),
            fallbackTint = Color(0xFF27272A).copy(alpha = 0.92f),
            blurRadius = Dp(22f),
            saturation = 1.8f,
            border = Color.White.copy(alpha = 0.10f),
            topHighlight = Color.White.copy(alpha = 0.16f),
            shadowColor = Color.Black.copy(alpha = 0.45f),
            shadowOffsetY = Dp(12f),
            shadowBlur = Dp(32f),
        )
    }
}

/**
 * Estado de desenho de uma superfície de vidro: o nó com o efeito, a posição na janela e
 * a geometria do último desenho (que a fonte usa para regravar o efeito).
 */
@Stable
class GlassSurface {
    internal var positionInWindow by mutableStateOf(Offset.Unspecified)
    private var effectNode: Any? = null
    private var effectKey: Pair<Float, Float>? = null
    private var width = 0f
    private var height = 0f
    private var blurRadiusPx = 0f
    private var saturation = 1f
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pill = Path()
    private val pillShifted = Path()

    internal val inflatePx: Float get() = ceil(blurRadiusPx)

    internal fun setGeometry(width: Float, height: Float, blurRadiusPx: Float, saturation: Float) {
        this.width = width
        this.height = height
        this.blurRadiusPx = blurRadiusPx
        this.saturation = saturation
    }

    /** Grava o conteúdo de trás, deslocado para a posição da superfície, no nó com blur. */
    internal fun recordEffect(source: Any, sourceInWindow: Offset): Any? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val barPos = positionInWindow
        if (width <= 0f || height <= 0f || !barPos.isSpecified || !sourceInWindow.isSpecified) return null
        val node = effectNode as RenderNode? ?: RenderNode("finaiGlassEffect").also { effectNode = it }
        val key = blurRadiusPx to saturation
        if (key != effectKey) {
            val blur = RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
            val saturate = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
            node.setRenderEffect(RenderEffect.createColorFilterEffect(saturate, blur))
            effectKey = key
        }
        // Folga do tamanho do blur em volta da pílula: sem ela as bordas do vidro
        // repetiriam o último pixel (CLAMP) em vez de misturar o que está ao lado.
        val inflate = inflatePx
        node.setPosition(0, 0, ceil(width + inflate * 2).toInt(), ceil(height + inflate * 2).toInt())
        val rec = node.beginRecording()
        try {
            rec.translate(inflate + sourceInWindow.x - barPos.x, inflate + sourceInWindow.y - barPos.y)
            rec.drawRenderNode(source as RenderNode)
        } finally {
            node.endRecording()
        }
        return node
    }

    internal fun pillPath(width: Float, height: Float, dy: Float = 0f, target: Path = pill): Path {
        val r = height / 2f
        target.reset()
        target.addRoundRect(RoundRect(0f, dy, width, height + dy, CornerRadius(r, r)))
        return target
    }

    internal fun shiftedPillPath(width: Float, height: Float, dy: Float): Path = pillPath(width, height, dy, pillShifted)

    internal fun shadowPaint(material: GlassMaterial, sigmaPx: Float, offsetYPx: Float): Paint = shadowPaint.apply {
        color = android.graphics.Color.BLACK
        setShadowLayer(sigmaToRadius(sigmaPx), 0f, offsetYPx, material.shadowColor.toArgb())
    }
}

/**
 * Uma superfície de vidro. Com [backdrop], ela se registra na fonte enquanto estiver na
 * composição; sem, serve só para os caminhos de desenho (o indicador usa assim).
 */
@Composable
fun rememberGlassSurface(backdrop: GlassBackdrop? = null): GlassSurface {
    val surface = remember { GlassSurface() }
    if (backdrop != null) {
        DisposableEffect(backdrop, surface) {
            backdrop.surfaces += surface
            onDispose { backdrop.surfaces -= surface }
        }
    }
    return surface
}

/** Registra onde a superfície está na janela, para o vidro alinhar com o conteúdo de trás. */
fun Modifier.glassPosition(surface: GlassSurface): Modifier =
    onGloballyPositioned { surface.positionInWindow = it.positionInWindow() }

/**
 * Desenha a pílula de vidro do tamanho atual: sombra (só por fora, para não escurecer o
 * vidro), conteúdo desfocado e saturado, tinta, borda e brilho interno superior.
 */
fun DrawScope.drawGlass(backdrop: GlassBackdrop, surface: GlassSurface, material: GlassMaterial) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val pill = surface.pillPath(w, h)

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        // Sombra de forma no canvas acelerado só existe a partir da API 28.
        if (!native.isHardwareAccelerated || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            native.save()
            native.clipOutPath(pill.asAndroidPath())
            val paint = surface.shadowPaint(material, material.shadowBlur.toPx() / 2f, material.shadowOffsetY.toPx())
            native.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, paint)
            native.restore()
        }
    }

    surface.setGeometry(w, h, material.blurRadius.toPx(), material.saturation)
    val source = backdrop.contentNode
    // Lidas aqui (estado) para o desenho ser refeito quando a barra ou a fonte mudam de lugar.
    val sourcePos = backdrop.sourceInWindow
    surface.positionInWindow
    var drew = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdrop.hasRecording && source != null) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (!native.isHardwareAccelerated) return@drawIntoCanvas
            val node = surface.recordEffect(source, sourcePos) ?: return@drawIntoCanvas
            val inflate = surface.inflatePx
            native.save()
            native.clipPath(pill.asAndroidPath())
            native.translate(-inflate, -inflate)
            native.drawRenderNode(node as RenderNode)
            native.restore()
            drew = true
        }
    }
    drawPath(pill, if (drew) material.tint else material.fallbackTint)

    drawGlassEdges(surface, w, h, material.border, material.topHighlight)
}

/**
 * Borda de 1 dp e brilho interno no topo (o `inset 0 1px 0` do CSS): a faixa entre a
 * pílula e a mesma pílula deslocada 1 dp para baixo. Usado também no indicador.
 */
fun DrawScope.drawGlassEdges(surface: GlassSurface, w: Float, h: Float, border: Color, topHighlight: Color) {
    val stroke = Dp(1f).toPx()
    val pill = surface.pillPath(w, h)
    if (topHighlight.alpha > 0f) {
        val shifted = surface.shiftedPillPath(w, h, stroke)
        clipPath(pill) {
            clipPath(shifted, ClipOp.Difference) {
                drawRect(topHighlight)
            }
        }
    }
    if (border.alpha > 0f) {
        val half = stroke / 2f
        drawRoundRect(
            color = border,
            topLeft = Offset(half, half),
            size = androidx.compose.ui.geometry.Size(w - stroke, h - stroke),
            cornerRadius = CornerRadius((h - stroke) / 2f),
            style = Stroke(stroke),
        )
    }
}


/**
 * O blur do Android recebe um "raio" que ele converte em desvio padrão
 * (sigma = raio × 0,57735 + 0,5); o playbook dá os valores no sentido do CSS, que já são
 * o desvio. Converter mantém o mesmo visual do protótipo.
 */
internal fun sigmaToRadius(sigma: Float): Float = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0f)

/**
 * Vidro retangular de ponta a ponta, para o cabeçalho (playbook 4a): o conteúdo de trás
 * desfocado e saturado dentro do retângulo, e a tinta por cima. Sem sombra, borda nem
 * brilho; a linha inferior é de quem chama. Sem gravação disponível (API < 31 ou antes do
 * primeiro quadro), só a tinta de fallback, mais opaca.
 */
fun DrawScope.drawGlassRect(
    backdrop: GlassBackdrop,
    surface: GlassSurface,
    blurRadius: Dp,
    saturation: Float,
    tint: Color,
    fallbackTint: Color,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    surface.setGeometry(w, h, blurRadius.toPx(), saturation)
    val source = backdrop.contentNode
    val sourcePos = backdrop.sourceInWindow
    surface.positionInWindow
    var drew = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdrop.hasRecording && source != null) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (!native.isHardwareAccelerated) return@drawIntoCanvas
            val node = surface.recordEffect(source, sourcePos) ?: return@drawIntoCanvas
            val inflate = surface.inflatePx
            native.save()
            native.clipRect(0f, 0f, w, h)
            native.translate(-inflate, -inflate)
            native.drawRenderNode(node as RenderNode)
            native.restore()
            drew = true
        }
    }
    drawRect(if (drew) tint else fallbackTint)
}
