package io.github.ppisky.molite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class FileHost(root: File) : AppHost {
    private val preferenceLock = Mutex()
    protected val root = root.apply { mkdirs() }
    override val dataDirectory: String = root.absolutePath
    override fun loadPreferences(): Map<String, String> {
        val file = File(root, "appearance.properties")
        if (!file.exists()) return emptyMap()
        return Properties().apply { file.inputStream().use { load(it) } }.entries.associate { it.key.toString() to it.value.toString() }
    }
    override suspend fun savePreferences(values: Map<String, String>) = withContext(Dispatchers.IO) {
      preferenceLock.withLock {
        val temp = File(root, "appearance.tmp")
        Properties().apply { putAll(values) }.also { properties -> temp.outputStream().use { properties.store(it, "MOLite appearance; no credentials") } }
        Files.move(temp.toPath(), File(root, "appearance.properties").toPath(), StandardCopyOption.REPLACE_EXISTING)
        Unit
      }
    }
    private fun privateFile(name: String): File {
        require(name.matches(Regex("transfer-[a-f0-9-]+\\.moc")))
        return File(root, name)
    }
    override suspend fun readPrivate(name: String): ByteArray = withContext(Dispatchers.IO) { privateFile(name).inputStream().use(::readBounded) }
    override suspend fun writePrivate(name: String, bytes: ByteArray): String = withContext(Dispatchers.IO) { privateFile(name).also { it.writeBytes(bytes) }.absolutePath }
    override suspend fun removePrivate(name: String) = withContext(Dispatchers.IO) { Files.deleteIfExists(privateFile(name).toPath()); Unit }
    protected fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= 128 * 1024 * 1024) { "当前应用单次导入最多 128 MiB" }
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }
}
