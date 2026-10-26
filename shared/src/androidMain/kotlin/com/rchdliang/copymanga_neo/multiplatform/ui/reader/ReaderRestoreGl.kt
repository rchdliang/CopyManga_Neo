package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Anime4K 四阶段 GLES 后端。三个特征阶段使用有符号 RGBA16F，不能用普通 8 位颜色纹理替代。
 * 所有调用只能发生在会话专用线程；程序与同尺寸纹理跨页复用，最终仅读回一次 RGBA8。
 */
internal class ReaderRestoreGl(private val assets: AssetManager) {
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private val programs = mutableMapOf<ReaderRestoreMode, IntArray>()
    private val textures = IntArray(4)
    private val framebuffer = IntArray(1)
    private val vertexArray = IntArray(1)
    private var width = 0
    private var height = 0

    init {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY)
            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1))
            check(EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API))
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attrs = intArrayOf(
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, 0x40, // EGL_OPENGL_ES3_BIT_KHR
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE
            )
            check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0)
            val config = checkNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT)
            surface = EGL14.eglCreatePbufferSurface(display, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
            GLES30.glGenFramebuffers(1, framebuffer, 0)
            GLES30.glGenVertexArrays(1, vertexArray, 0)
            GLES30.glBindVertexArray(vertexArray[0])
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDisable(GLES30.GL_DITHER)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
            checkGl()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun restore(input: Bitmap, mode: ReaderRestoreMode, checkCurrent: () -> Unit): Bitmap {
        checkCurrent()
        val w = input.width
        val h = input.height
        val maxTexture = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
        if (w > maxTexture[0] || h > maxTexture[0]) throw ReaderRestoreUnavailable("图片超出 GPU 支持尺寸，显示原图")
        val bytes = Math.multiplyExact(Math.multiplyExact(w, h), 4)
        if (input.rowBytes != w * 4) throw ReaderRestoreUnavailable("此位图格式暂不支持修复")
        val pixels = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        input.copyPixelsToBuffer(pixels)
        // 首版只处理不透明的 sRGB 静态漫画，避免将透明度误当成网络特征或改变原有合成。
        for (i in 3 until bytes step 4) if ((pixels.get(i).toInt() and 255) != 255) {
            throw ReaderRestoreUnavailable("透明图片暂用原图")
        }
        pixels.rewind()
        allocate(w, h)
        val passes = programs[mode] ?: compileModel(mode).also { programs[mode] = it }
        checkCurrent()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        GLES30.glViewport(0, 0, w, h)
        GLES30.glBindVertexArray(vertexArray[0])
        val destinations = intArrayOf(1, 2, 1, 3)
        val features = intArrayOf(0, 1, 2, 1)
        for (pass in 0..3) {
            checkCurrent()
            attach(destinations[pass])
            GLES30.glUseProgram(passes[pass])
            bindTexture(passes[pass], "uMain", 0, textures[0])
            bindTexture(passes[pass], "uFeatures", 1, textures[features[pass]])
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            checkGl()
        }
        checkCurrent()
        // 上传时第 0 行与读回第 0 行均对应原位图首行，无需额外翻转。
        pixels.rewind()
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        checkGl()
        checkCurrent()
        pixels.rewind()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(pixels)
            setHasAlpha(false)
        }
    }

    private fun allocate(w: Int, h: Int) {
        if (w == width && h == height) return
        GLES30.glDeleteTextures(4, textures, 0)
        textures.fill(0)
        width = 0
        height = 0
        GLES30.glGenTextures(4, textures, 0)
        for (i in textures.indices) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[i])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            val floating = i == 1 || i == 2
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, if (floating) GLES30.GL_RGBA16F else GLES30.GL_RGBA8,
                w, h, 0, GLES30.GL_RGBA, if (floating) GLES30.GL_HALF_FLOAT else GLES30.GL_UNSIGNED_BYTE, null as java.nio.Buffer?)
            // 实际检查浮点渲染目标；不根据厂商名称猜测驱动能力。
            attach(i)
        }
        checkGl()
        width = w
        height = h
    }

    private fun attach(index: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, textures[index], 0)
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            "GPU does not support the required render target"
        }
    }

    private fun bindTexture(program: Int, name: String, unit: Int, texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, name), unit)
    }

    private fun compileModel(mode: ReaderRestoreMode): IntArray {
        val source = assets.open("reader-restore/${mode.asset}").bufferedReader().use { it.readText() }
        val stages = source.split(Regex("(?m)^//!DESC[^\\n]*")).drop(1)
        check(stages.size == 4) { "Unexpected Anime4K pass count" }
        val result = IntArray(4)
        try {
            stages.forEachIndexed { index, stage ->
                var body = stage.lineSequence().filterNot { it.startsWith("//!") }.joinToString("\n")
                body = body.replace("MAIN_texOff", "sampleMain")
                    .replace(Regex("conv2d(?:_[0-9]+)?_tf_texOff"), "sampleFeatures")
                    .replace("MAIN_tex(MAIN_pos)", "sampleMain(vec2(0.0))")
                val finish = if (index == 3)
                    "vec4 value = hook(); fragColor = vec4(clamp(value.rgb, 0.0, 1.0), 1.0);"
                    else "fragColor = hook();"
                result[index] = link(FRAGMENT_HEADER + body + "\nvoid main() { " + finish + " }")
            }
            return result
        } catch (error: Throwable) {
            result.filter { it != 0 }.forEach { GLES30.glDeleteProgram(it) }
            throw error
        }
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES30.glCreateShader(type)
        check(id != 0)
        GLES30.glShaderSource(id, source)
        GLES30.glCompileShader(id)
        val status = IntArray(1)
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES30.glGetShaderInfoLog(id).take(2000)
            GLES30.glDeleteShader(id)
            error("Restore shader compilation failed: $message")
        }
        return id
    }

    private fun link(fragment: String): Int {
        val vertex = shader(GLES30.GL_VERTEX_SHADER, VERTEX)
        var pixel = 0
        var program = 0
        try {
            pixel = shader(GLES30.GL_FRAGMENT_SHADER, fragment)
            program = GLES30.glCreateProgram()
            check(program != 0)
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, pixel)
            GLES30.glLinkProgram(program)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { "Restore program link failed: ${GLES30.glGetProgramInfoLog(program).take(2000)}" }
            return program
        } catch (error: Throwable) {
            if (program != 0) GLES30.glDeleteProgram(program)
            throw error
        } finally {
            GLES30.glDeleteShader(vertex)
            if (pixel != 0) GLES30.glDeleteShader(pixel)
        }
    }

    private fun checkGl() {
        val code = GLES30.glGetError()
        check(code == GLES30.GL_NO_ERROR) { "OpenGL error $code" }
    }

    fun close() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE &&
            EGL14.eglMakeCurrent(display, surface, surface, context)) {
            programs.values.forEach { ids -> ids.forEach { GLES30.glDeleteProgram(it) } }
            GLES30.glDeleteTextures(4, textures, 0)
            GLES30.glDeleteFramebuffers(1, framebuffer, 0)
            GLES30.glDeleteVertexArrays(1, vertexArray, 0)
        }
        programs.clear()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
        EGL14.eglReleaseThread()
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
    }

    companion object {
        private const val VERTEX = """#version 300 es
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
"""
        private const val FRAGMENT_HEADER = """#version 300 es
precision highp float;
precision highp int;
uniform highp sampler2D uMain;
uniform highp sampler2D uFeatures;
out vec4 fragColor;
vec4 sampleMain(vec2 offset) {
    ivec2 pos = clamp(ivec2(gl_FragCoord.xy) + ivec2(offset), ivec2(0), textureSize(uMain, 0) - 1);
    return texelFetch(uMain, pos, 0);
}
vec4 sampleFeatures(vec2 offset) {
    ivec2 pos = clamp(ivec2(gl_FragCoord.xy) + ivec2(offset), ivec2(0), textureSize(uFeatures, 0) - 1);
    return texelFetch(uFeatures, pos, 0);
}
"""
    }
}
