package com.rchdliang.copymanga_neo.data.local.portable

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.test.*

class DocumentMigrationTest {
    @Serializable
    private data class Value(val name: String = "", val count: Int = 0)
    private class Disk : DocumentStorage {
        val files = mutableMapOf<String, String>()
        var failing: String? = null
        override suspend fun read(key: String) = files[key]
        override suspend fun write(key: String, value: String) {
            check(key != failing) { "disk full" }
            files[key] = value
        }

        override suspend fun remove(key: String) {
            files.remove(key)
        }
    }

    private fun envelope(version: Int, payload: String) =
        DocumentEnvelope.encode(
            "copymanga.document/sample",
            version,
            Json.parseToJsonElement(payload)
        )

    @Test
    fun futureVersionAndUnknownFieldsAreNeverOverwritten() = runTest {
        for (raw in listOf(
            envelope(3, """{"name":"future"}"""),
            envelope(1, """{"name":"known","futureField":42}""")
        )) {
            val disk = Disk().apply { files["sample"] = raw }
            val store = DocumentStore(disk, "sample", Value.serializer(), Value())
            assertTrue(store.load().isFailure)
            assertTrue(store.update { Value("replacement") }.isFailure)
            assertEquals(raw, disk.files["sample"])
            assertEquals(Value(), store.state.value)
        }
    }

    @Test
    fun skippedVersionsMigrateInOrderAndRetainOriginal() = runTest {
        val original = envelope(1, """{"oldName":"reader"}""")
        val disk = Disk().apply { files["sample"] = original }
        val steps = mutableListOf<Int>()
        val store = DocumentStore(
            disk, "sample", Value.serializer(), Value(), schemaVersion = 3,
            migrations = mapOf(
                1 to { value ->
                    steps += 1; buildJsonObject {
                    put(
                        "name",
                        value.jsonObject.getValue("oldName")
                    )
                }
                },
                2 to { value ->
                    steps += 2; JsonObject(
                    value.jsonObject + ("count" to JsonPrimitive(
                        7
                    ))
                )
                }
            ))
        assertTrue(store.load().isSuccess)
        assertEquals(listOf(1, 2), steps)
        assertEquals(Value("reader", 7), store.state.value)
        assertEquals(original, disk.files["sample.before-v3"])
        assertEquals(
            3,
            Json.parseToJsonElement(disk.files.getValue("sample")).jsonObject["schemaVersion"]!!.jsonPrimitive.int
        )
    }

    @Test
    fun interruptedCommitAndMissingStepLeaveOriginalAndCanRetry() = runTest {
        val raw = envelope(1, """{"name":"old"}""")
        val disk = Disk().apply { files["sample"] = raw; failing = "sample" }
        val store = DocumentStore(
            disk, "sample", Value.serializer(), Value(), schemaVersion = 2,
            migrations = mapOf(1 to { value ->
                JsonObject(
                    value.jsonObject + ("count" to JsonPrimitive(
                        9
                    ))
                )
            })
        )
        assertTrue(store.load().isFailure)
        assertEquals(raw, disk.files["sample"])
        assertEquals(Value(), store.state.value)
        disk.failing = null
        assertTrue(store.load().isSuccess)
        assertEquals(Value("old", 9), store.state.value)
        val missing = DocumentStore(disk, "sample", Value.serializer(), Value(), schemaVersion = 4)
        val committed = disk.files["sample"]
        assertTrue(missing.update { Value("bad") }.isFailure)
        assertEquals(committed, disk.files["sample"])
    }

    @Test
    fun legacyPayloadIsWrappedOnlyAfterValidation() = runTest {
        val disk = Disk().apply { files["sample"] = """{"name":"reader","count":-1}""" }
        val original = disk.files["sample"]
        val store = DocumentStore(
            disk,
            "sample",
            Value.serializer(),
            Value(),
            validate = { require(it.count >= 0) })
        assertTrue(store.load().isFailure)
        assertEquals(original, disk.files["sample"])
        assertEquals(1, disk.files.size)
    }
}
