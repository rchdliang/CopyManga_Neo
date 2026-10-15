package com.rchdliang.copymanga_neo.multiplatform

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream

/**
 * A JVM-only Android linkage check: no emulator, network, or Compose rendering required.
 * Shared Android compilation and the application's runtime may resolve different experimental
 * Material3 versions. Kotlin source compilation alone cannot catch a removed runtime overload.
 */
class SearchMaterialAbiTest {
    @Test fun sharedSearchCategoryCallsExistInPackagedAndroidMaterial() = assertMaterialCalls(
        "com/rchdliang/copymanga_neo/multiplatform/ui/search/SearchPageKt",
        requiredOwner = "androidx/compose/material3/ToggleButtonKt"
    )

    @Test fun sharedDetailCategoryCallsExistInPackagedAndroidMaterial() = assertMaterialCalls(
        "com/rchdliang/copymanga_neo/multiplatform/ui/detail/DetailScreenKt",
        requiredOwner = "androidx/compose/material3/ToggleButtonKt"
    )

    @Test fun sharedDownloadMenuCallsExistInPackagedAndroidMaterial() = assertMaterialCalls(
        "com/rchdliang/copymanga_neo/multiplatform/ui/components/DownloadSelectionControlsKt",
        requiredOwner = "androidx/compose/material3/MenuDefaults"
    )

    @Test fun sharedDownloadLabelCallsExistInPackagedAndroidMaterial() = assertMaterialCalls(
        "com/rchdliang/copymanga_neo/multiplatform/ui/components/DownloadMenuGroupLabelKt",
        requiredOwner = "androidx/compose/material3/MenuDefaults"
    )

    private fun assertMaterialCalls(callerName: String, requiredOwner: String) {
        val calls = readClass(callerName).calls
            .filter { it.owner.startsWith("androidx/compose/material3/") }
        assertTrue("Expected compiled Material3 calls in $callerName", calls.isNotEmpty())
        assertTrue("Expected call to $requiredOwner in $callerName",
            calls.any { it.owner == requiredOwner })
        val missing = calls.filter { call ->
            call.name + call.descriptor !in readClass(call.owner).methods
        }
        assertTrue("$callerName references methods missing from Android runtime: $missing",
            missing.isEmpty())
    }

    private data class Call(val owner: String, val name: String, val descriptor: String)
    private data class ParsedClass(val calls: List<Call>, val methods: Set<String>)

    private fun readClass(name: String): ParsedClass {
        val stream = requireNotNull(javaClass.getResourceAsStream("/$name.class")) {
            "Missing Android test runtime class: $name"
        }
        return DataInputStream(stream).use { input ->
            check(input.readInt() == 0xCAFEBABE.toInt())
            input.readUnsignedShort() // minor
            input.readUnsignedShort() // major
            val pool = arrayOfNulls<Any>(input.readUnsignedShort())
            val calls = mutableListOf<Pair<Int, Int>>()
            var index = 1
            while (index < pool.size) {
                when (val tag = input.readUnsignedByte()) {
                    1 -> pool[index] = input.readUTF()
                    3, 4 -> input.readInt()
                    5, 6 -> { input.readLong(); index++ }
                    7, 8, 16, 19, 20 -> pool[index] = input.readUnsignedShort()
                    9, 10, 11, 12, 17, 18 -> {
                        val pair = input.readUnsignedShort() to input.readUnsignedShort()
                        pool[index] = pair
                        if (tag == 10 || tag == 11) calls += pair
                    }
                    15 -> { input.readUnsignedByte(); input.readUnsignedShort() }
                    else -> error("Unsupported class constant tag $tag")
                }
                index++
            }
            fun utf(index: Int) = pool[index] as String
            fun skipAttributes() {
                repeat(input.readUnsignedShort()) {
                    input.readUnsignedShort()
                    input.skipNBytes(input.readInt().toLong())
                }
            }
            input.readUnsignedShort() // access
            input.readUnsignedShort() // this
            input.readUnsignedShort() // super
            repeat(input.readUnsignedShort()) { input.readUnsignedShort() }
            repeat(input.readUnsignedShort()) {
                input.readUnsignedShort(); input.readUnsignedShort(); input.readUnsignedShort()
                skipAttributes()
            }
            val methods = buildSet {
                repeat(input.readUnsignedShort()) {
                    input.readUnsignedShort()
                    add(utf(input.readUnsignedShort()) + utf(input.readUnsignedShort()))
                    skipAttributes()
                }
            }
            ParsedClass(calls.map { (owner, nameAndType) ->
                @Suppress("UNCHECKED_CAST")
                val signature = pool[nameAndType] as Pair<Int, Int>
                Call(utf(pool[owner] as Int), utf(signature.first), utf(signature.second))
            }, methods)
        }
    }
}
