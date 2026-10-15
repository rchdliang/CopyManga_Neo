package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.repository.portable.SecureCredentialStorage
import com.rchdliang.copymanga_neo.data.repository.portable.EnvelopeSecureCredentialStorage
import com.rchdliang.copymanga_neo.data.repository.portable.SecureCredentialEnvelopeBackend
import com.rchdliang.copymanga_neo.data.repository.portable.StoredCredential
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Windows: DPAPI CurrentUser protected bytes only on disk. Linux: Secret Service via libsecret's
 * secret-tool, never a preferences/file fallback. Missing/locked stores raise actionable failures.
 * Passwords/tokens are sent over child stdin, never command arguments or environment variables.
 */
class DesktopSecureCredentialStorage(
    private val directory: Path,
    private val applicationId: String = "com.rchdliang.copymanga_neo",
) : SecureCredentialStorage {
    private val windows = System.getProperty("os.name").startsWith("Windows", true)
    private val linux = System.getProperty("os.name").startsWith("Linux", true)
    private val secretTool = findExecutable("secret-tool")
    private val powershell = findExecutable("powershell.exe")
    override val persistent: Boolean = (windows && powershell != null) || (linux && secretTool != null)

    init { require(applicationId.matches(Regex("[A-Za-z0-9._-]+"))) }

    private val envelope = EnvelopeSecureCredentialStorage(object : SecureCredentialEnvelopeBackend {
        override suspend fun readEnvelope() = readProtected("sessions-v2", "credentials-v2.dpapi")
        override suspend fun writeEnvelope(value: String) = writeProtected(value)
        override suspend fun readLegacy(mode: RequestMode) = readProtected(mode.name, "${mode.name.lowercase()}.dpapi")
    }, persistent)
    override suspend fun readAll() = envelope.readAll()
    override suspend fun writeAll(values: Map<RequestMode, StoredCredential>) = envelope.writeAll(values)

    private suspend fun readProtected(account: String, filename: String): String? = withContext(Dispatchers.IO) {
        check(persistent) { "系统没有可用的安全凭据存储，请安装并解锁Secret Service/libsecret；不会明文保存登录信息" }
        if (windows) {
            val protected = try {
                Files.newInputStream(directory.resolve(filename)).use { it.readNBytes(MAX_TRANSPORT_BYTES + 1) }
            } catch (_: java.nio.file.NoSuchFileException) { return@withContext null }
            check(protected.size <= MAX_TRANSPORT_BYTES) { "加密凭据文件大小异常" }
            val decrypted = invokeDpapi("Unprotect", protected.toString(Charsets.US_ASCII))
            val plaintext = Base64.getDecoder().decode(decrypted)
            try { plaintext.toString(Charsets.UTF_8) } finally { plaintext.fill(0) }
        } else {
            val output = command(listOf(requireNotNull(secretTool), "lookup", "application", applicationId, "mode", account))
            if (output.exit == 1 && output.stdout.isBlank() && output.stderr.isBlank()) {
                // lookup returns no value for an unavailable/locked item too. Only a successful
                // empty search proves absence; never migrate over an existing unreadable item.
                val search = command(listOf(requireNotNull(secretTool), "search", "--all", "application", applicationId, "mode", account))
                check(secretToolConfirmsAbsence(search.exit, search.stdout, search.stderr)) {
                    "安全凭据存在但无法读取，请先解锁系统密钥环；原记录已保留"
                }
                return@withContext null
            }
            check(output.exit == 0) { "无法访问安全密钥环，请确认Secret Service正在运行且已解锁" }
            output.stdout.trimEnd('\n', '\r')
        }
    }

    private suspend fun writeProtected(value: String): Unit = withContext(Dispatchers.IO) {
        check(persistent) { "系统没有可用的安全凭据存储，不能记住密码或自动登录" }
        if (windows) {
            val plaintext = value.toByteArray(Charsets.UTF_8)
            require(plaintext.size <= MAX_BYTES) { "凭据数据过大" }
            val encrypted = try { invokeDpapi("Protect", Base64.getEncoder().encodeToString(plaintext)) } finally { plaintext.fill(0) }
            Files.createDirectories(directory)
            val temporary = Files.createTempFile(directory, ".credential-", ".tmp")
            try {
                Files.writeString(temporary, encrypted, Charsets.US_ASCII)
                java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE).use { it.force(true) }
                Files.move(temporary, directory.resolve("credentials-v2.dpapi"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        } else {
            validateSecretToolEnvelope(value)
            val output = command(listOf(requireNotNull(secretTool), "store", "--label=CopyManga_Neo sessions",
                "application", applicationId, "mode", "sessions-v2"), value)
            check(output.exit == 0) { "安全凭据保存失败，请确认Secret Service正在运行且已解锁；登录信息没有写入普通文件" }
        }
        Unit
    }

    private fun invokeDpapi(operation: String, input: String): String {
        require(operation == "Protect" || operation == "Unprotect")
        val script = """
            ${'$'}ErrorActionPreference = 'Stop';
            Add-Type -AssemblyName System.Security;
            ${'$'}inputBytes = [Convert]::FromBase64String([Console]::In.ReadLine());
            ${'$'}entropy = [Text.Encoding]::UTF8.GetBytes('$applicationId');
            ${'$'}outputBytes = [Security.Cryptography.ProtectedData]::$operation(${'$'}inputBytes, ${'$'}entropy, [Security.Cryptography.DataProtectionScope]::CurrentUser);
            [Console]::Out.WriteLine([Convert]::ToBase64String(${'$'}outputBytes));
            [Array]::Clear(${'$'}inputBytes, 0, ${'$'}inputBytes.Length);
            [Array]::Clear(${'$'}outputBytes, 0, ${'$'}outputBytes.Length);
        """.trimIndent()
        val output = command(listOf(requireNotNull(powershell), "-NoProfile", "-NonInteractive", "-Command", script), input)
        check(output.exit == 0) { "Windows DPAPI无法访问此用户的加密凭据" }
        return output.stdout.trim().also { require(it.isNotEmpty()) { "DPAPI返回了空结果" } }
    }

    private data class Output(val exit: Int, val stdout: String, val stderr: String)
    private fun command(arguments: List<String>, input: String? = null): Output {
        require(input == null || input.toByteArray().size <= MAX_TRANSPORT_BYTES)
        val process = ProcessBuilder(arguments).start()
        val executor = Executors.newFixedThreadPool(2) { Thread(it, "CopyManga-secure-storage").apply { isDaemon = true } }
        try {
            val stdout = executor.submit<String> {
                process.inputStream.use { stream ->
                    val bytes = stream.readNBytes(MAX_TRANSPORT_BYTES + 1)
                    check(bytes.size <= MAX_TRANSPORT_BYTES) { "安全存储返回的数据过大" }
                    bytes.toString(Charsets.UTF_8)
                }
            }
            val stderr = executor.submit<String> {
                process.errorStream.use { it.readNBytes(16384).toString(Charsets.UTF_8) }
            }
            process.outputStream.use { stream -> if (input != null) { stream.write(input.toByteArray(Charsets.UTF_8)); stream.write('\n'.code) } }
            check(process.waitFor(30, TimeUnit.SECONDS)) { "安全密钥环访问超时，请先解锁系统密钥环" }
            return Output(process.exitValue(), stdout.get(2, TimeUnit.SECONDS), stderr.get(2, TimeUnit.SECONDS))
        } finally {
            if (process.isAlive) process.destroyForcibly()
            executor.shutdownNow()
        }
    }

    companion object {
        private const val MAX_BYTES = EnvelopeSecureCredentialStorage.MAX_BYTES
        private const val MAX_TRANSPORT_BYTES = 2 * MAX_BYTES
        private fun findExecutable(name: String): String? = System.getenv("PATH").orEmpty()
            .split(java.io.File.pathSeparator).map { Path.of(it).resolve(name) }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }?.toString()
    }
}

/** secret-tool search succeeds with no output only when no item matches. Never log its output. */
internal fun secretToolConfirmsAbsence(exit: Int, stdout: String, stderr: String): Boolean =
    exit == 0 && stdout.isBlank() && stderr.isBlank()

/** libsecret secret-tool reads at most 8192 stdin bytes; leave room for the terminating newline. */
internal fun validateSecretToolEnvelope(value: String) {
    require(value.encodeToByteArray().size < 8191) { "登录凭据超过系统 secret-tool 安全写入上限，原凭据已保留" }
}
