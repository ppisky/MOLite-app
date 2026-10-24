package io.github.ppisky.molite

data class DocumentData(val name: String, val mime: String, val bytes: ByteArray)
interface AppHost {
    val dataDirectory: String
    fun loadPreferences(): Map<String, String>
    suspend fun savePreferences(values: Map<String, String>)
    suspend fun readDocument(): DocumentData?
    suspend fun writeDocument(name: String, mime: String, bytes: ByteArray): Boolean
    suspend fun readPrivate(name: String): ByteArray
    suspend fun writePrivate(name: String, bytes: ByteArray): String
    suspend fun removePrivate(name: String)
}
expect fun newId(): String
expect fun encodeBase64(bytes: ByteArray): String
expect fun decodeBase64(text: String): ByteArray
