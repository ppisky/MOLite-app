package io.github.ppisky.molite

actual fun newId(): String = java.util.UUID.randomUUID().toString()
actual fun encodeBase64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
actual fun decodeBase64(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)
