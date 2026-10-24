package me.rerere.rikkahub.preview

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.ppisky.molite.*
import kotlinx.coroutines.*
import java.io.File

class AndroidHost(private val context: Context) : FileHost(File(context.filesDir, "molite")) {
    init { NativeCore.androidInit(context.applicationContext) }
    var openDocument: (() -> Unit)? = null
    var createDocument: ((String) -> Unit)? = null
    private var pending: CompletableDeferred<Uri?>? = null
    fun selected(uri: Uri?) { pending?.complete(uri) }
    fun detached() { pending?.cancel(); pending = null; openDocument = null; createDocument = null }
    private suspend fun choose(name: String? = null): Uri? {
        check(pending == null) { "文件选择器正在打开" }
        val result = CompletableDeferred<Uri?>()
        pending = result
        try {
            withContext(Dispatchers.Main) {
                if (name == null) checkNotNull(openDocument) { "文件选择器尚未就绪" }.invoke()
                else checkNotNull(createDocument) { "文件选择器尚未就绪" }.invoke(name)
            }
            return result.await()
        } finally { pending = null }
    }
    override suspend fun readDocument(): DocumentData? {
        val uri = choose() ?: return null
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "document"
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val bytes = checkNotNull(resolver.openInputStream(uri)).use(::readBounded)
            DocumentData(name, mime, bytes)
        }
    }
    override suspend fun writeDocument(name: String, mime: String, bytes: ByteArray): Boolean {
        val uri = choose(name) ?: return false
        return withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }; true }
    }
}
