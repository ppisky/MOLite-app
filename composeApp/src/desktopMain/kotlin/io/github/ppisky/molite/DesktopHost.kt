package io.github.ppisky.molite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DesktopHost : FileHost(File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "MOLite")) {
    private suspend fun choose(save: Boolean, name: String = ""): File? = suspendCancellableCoroutine { continuation ->
        SwingUtilities.invokeLater {
            if (!continuation.isActive) return@invokeLater
            try {
                val picker = JFileChooser().apply { if (name.isNotEmpty()) selectedFile = File(name) }
                val result = if (save) picker.showSaveDialog(null) else picker.showOpenDialog(null)
                if (continuation.isActive) continuation.resume(if (result == JFileChooser.APPROVE_OPTION) picker.selectedFile else null)
            } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
        }
    }
    override suspend fun readDocument(): DocumentData? {
        val file = choose(false) ?: return null
        return withContext(Dispatchers.IO) { DocumentData(file.name, Files.probeContentType(file.toPath()) ?: if (file.extension.lowercase() in listOf("md", "txt", "csv", "json")) "text/plain" else "application/octet-stream", file.inputStream().use(::readBounded)) }
    }
    override suspend fun writeDocument(name: String, mime: String, bytes: ByteArray): Boolean {
        val file = choose(true, name) ?: return false
        return withContext(Dispatchers.IO) { file.writeBytes(bytes); true }
    }
}
