package com.rchdliang.copymanga_neo.ui.reader

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

internal fun platformReaderSharpnessDefault(): Float = 0f

@Composable
internal fun Modifier.readerSharpen(strength: Float, pageKey: String = ""): Modifier {
    val amount = normalizedReaderSharpness(strength)
    if (amount == 0f || Build.VERSION.SDK_INT < 33 || !LocalView.current.isHardwareAccelerated) return this
    return androidReaderSharpen(amount, pageKey)
}

@RequiresApi(33)
@Composable
private fun Modifier.androidReaderSharpen(amount: Float, pageKey: String): Modifier {
    val report = LocalReaderSharpenStatus.current
    // Compile once for this image; changing strength only creates a uniform snapshot effect.
    val shader = remember { runCatching { RuntimeShader(READER_SHARPEN_SHADER) }.getOrNull() }
        ?: run { SideEffect { report?.invoke(pageKey, false) }; return this }
    val effect = remember(shader, amount) {
        try {
            shader.setFloatUniform("strength", amount)
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } catch (_: IllegalArgumentException) { null }
          catch (_: IllegalStateException) { null }
    } ?: run { SideEffect { report?.invoke(pageKey, false) }; return this }
    var surfaceSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val measured = surfaceSize != androidx.compose.ui.unit.IntSize.Zero
    val surfaceAllowed = readerSharpenSurfaceAllowed(surfaceSize.width, surfaceSize.height)
    SideEffect {
        if (measured) {
            report?.invoke(pageKey, surfaceAllowed)
        }
    }
    return this.then(Modifier.onSizeChanged { surfaceSize = it }).graphicsLayer {
        renderEffect = if (readerSharpenSurfaceAllowed(size.width.toInt(), size.height.toInt())) effect else null
    }
}

// Deliberately platform-local; AGSL is not a commonMain or iOS API.
internal const val READER_SHARPEN_SHADER = """
uniform shader content;
uniform float strength;
float luma(half4 c) { return dot(float3(c.rgb), float3(0.2126, 0.7152, 0.0722)); }
half4 main(float2 p) {
    half4 c = content.eval(p);
    half4 n = content.eval(p + float2(0, -1));
    half4 s = content.eval(p + float2(0, 1));
    half4 w = content.eval(p + float2(-1, 0));
    half4 e = content.eval(p + float2(1, 0));
    // Premultiplied alpha: preserve transparent edges and letterboxing verbatim.
    if (min(c.a, min(min(n.a, s.a), min(w.a, e.a))) < 0.999) return c;
    float y = luma(c);
    float yn = luma(n), ys = luma(s), yw = luma(w), ye = luma(e);
    float lo = min(y, min(min(yn, ys), min(yw, ye)));
    float hi = max(y, max(max(yn, ys), max(yw, ye)));
    float detail = y - (yn + ys + yw + ye) * 0.25;
    // 抑制平坦区域的微小噪声，但不再关闭黑白文字的高反差边缘。
    float gain = smoothstep(0.004, 0.02, hi - lo);
    float limit = 0.12 * strength;
    float delta = clamp(3.0 * strength * gain * detail, -limit, limit);
    // 限制在原邻域亮度内，避免在黑线两侧制造额外的白边或黑边。
    delta = clamp(y + delta, lo, hi) - y;
    return half4(clamp(c.rgb + half3(delta), half3(0), half3(c.a)), c.a);
}
"""

@Composable
internal fun readerSharpnessAvailable(): Boolean = android.os.Build.VERSION.SDK_INT >= 33 && androidx.compose.ui.platform.LocalView.current.isHardwareAccelerated
