package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual fun platformReaderSharpnessDefault(): Float = normalizedReaderSharpness(
    System.getProperty("copymanga.reader.sharpness")?.toFloatOrNull() ?: 0f
)

internal fun createReaderSharpenFilter(amount: Float): ImageFilter =
    RuntimeEffect.makeForShader(READER_SHARPEN_SHADER).use { runtime ->
        RuntimeShaderBuilder(runtime).use { builder ->
            builder.uniform("strength", normalizedReaderSharpness(amount))
            ImageFilter.makeRuntimeShader(builder, "content", null)
        }
    }

@Composable
internal actual fun Modifier.readerSharpen(strength: Float): Modifier {
    val amount = normalizedReaderSharpness(strength)
    if (amount == 0f) return this
    val filter = remember(amount) {
        try { createReaderSharpenFilter(amount) }
        catch (_: IllegalArgumentException) { null }
        catch (_: IllegalStateException) { null }
    } ?: return this
    DisposableEffect(filter) { onDispose { filter.close() } }
    val effect = remember(filter) { filter.asComposeRenderEffect() }
    return graphicsLayer {
        renderEffect = if (readerSharpenSurfaceAllowed(size.width.toInt(), size.height.toInt())) effect else null
    }
}

// SkSL counterpart of the Android kernel; parity is checked by the prototype tests.
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
    float gain = (1.0 - smoothstep(0.10, 0.40, hi - lo))
        * smoothstep(0.002, 0.012, abs(detail));
    float delta = clamp(strength * gain * detail, -0.03, 0.03);
    delta = clamp(y + delta, lo, hi) - y;
    // Luma-only correction; strong edges and noise receive less gain.
    return half4(clamp(c.rgb + half3(delta), half3(0), half3(c.a)), c.a);
}
"""
