package com.finai.app.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** Only instantiated on API 33+. Coordinates include the capture's blur overscan.
 * No clock/noise/chromatic split: a stationary capsule lens, independent of the UI above it.
 */
@RequiresApi(33)
internal class NavGlassRefraction {
    private val shader = RuntimeShader(SOURCE)

    fun effect(width: Float, height: Float, inset: Float, background: RenderEffect): RenderEffect {
        shader.setFloatUniform("extent", width, height)
        shader.setFloatUniform("inset", inset)
        shader.setFloatUniform("bandRatio", NavGlassSpec.RefractionBand)
        shader.setFloatUniform("strengthRatio", NavGlassSpec.RefractionStrength)
        // RenderEffect snapshots uniforms. Rebuild only when geometry/material changes,
        // never on scrolling or touch lighting; the compiled RuntimeShader is retained.
        return RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "background"), background,
        )
    }

    companion object {
        internal const val SOURCE = """
            uniform shader background;
            uniform float2 extent;
            uniform float inset;
            uniform float bandRatio;
            uniform float strengthRatio;
            half4 main(float2 coord) {
                float2 p = coord - float2(inset) - extent * 0.5;
                float radius = min(extent.x, extent.y) * 0.5;
                float axis = max(0.0, extent.x * 0.5 - radius);
                float2 v = p - float2(clamp(p.x, -axis, axis), 0.0);
                float len = length(v);
                float depth = radius - len;
                float edge = 1.0 - smoothstep(0.0, radius * bandRatio, depth);
                float2 normal = v / max(len, 0.001);
                float2 sampleAt = coord - normal * edge * edge * radius * strengthRatio;
                return background.eval(sampleAt);
            }
        """
    }
}
