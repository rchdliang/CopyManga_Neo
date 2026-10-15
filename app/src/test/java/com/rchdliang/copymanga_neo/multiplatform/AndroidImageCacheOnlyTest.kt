package com.rchdliang.copymanga_neo.multiplatform

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class AndroidImageCacheOnlyTest {
    @Test fun inheritedOkHttpTransportRejectsCoilCacheMissBeforeDnsOrNetworkInterceptors() {
        var dnsCalls = 0
        var exchanges = 0
        val retained = OkHttpClient.Builder()
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    dnsCalls++
                    error("A cache-only preview must not resolve any host")
                }
            })
            .addNetworkInterceptor { exchanges++; error("A cache-only preview must not connect") }
            .build()
        // Same newBuilder timeout inheritance as AndroidSharedImageLoaders.
        val timed = retained.newBuilder().callTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).build()
        for (policy in listOf("only-if-cached, max-stale=2147483647", "no-cache, only-if-cached")) {
            timed.newCall(Request.Builder().url("https://synthetic.invalid/cover.jpg")
                .header("Cache-Control", policy).build()).execute().use { response ->
                assertEquals(504, response.code)
            }
        }
        assertEquals(0, dnsCalls)
        assertEquals(0, exchanges)
    }
}
