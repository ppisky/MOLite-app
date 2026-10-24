package io.github.ppisky.molite

import java.io.File

object NativeCore {
    init {
        val directory = System.getProperty("molite.native.dir") ?: System.getProperty("compose.application.resources.dir")?.let { File(it, "native").absolutePath }
        if (directory != null) {
            System.load(File(directory, System.mapLibraryName("molite_core")).absolutePath)
            System.load(File(directory, System.mapLibraryName("molite_jni")).absolutePath)
        } else {
            System.loadLibrary("molite_core")
            System.loadLibrary("molite_jni")
        }
    }
    external fun abi(): Int
    external fun androidInit(context: Any)
    external fun open(path: ByteArray): Long
    external fun submit(engine: Long, json: ByteArray): Long
    external fun next(task: Long): ByteArray?
    external fun cancel(task: Long)
    external fun release(task: Long)
    external fun close(engine: Long)
    external fun secret(name: ByteArray, value: ByteArray)
}

actual fun createCoreBridge(): CoreBridge = object : CoreBridge {
    private var engine = 0L
    private val credentials = mutableSetOf<String>()
    override fun open(path: String) {
        check(engine == 0L)
        check(NativeCore.abi() == 1) { "MOLite 内核 ABI 不兼容" }
        engine = NativeCore.open(path.encodeToByteArray())
    }
    override fun submit(json: String): Long = NativeCore.submit(engine, json.encodeToByteArray())
    override fun next(task: Long): String? = NativeCore.next(task)?.decodeToString()
    override fun cancel(task: Long) = NativeCore.cancel(task)
    override fun release(task: Long) = NativeCore.release(task)
    override fun secret(name: String, value: String) {
        require(name.matches(Regex("MOLITE_APP_KEY_[A-Za-z0-9_]+")))
        require('\u0000' !in value)
        val bytes = value.encodeToByteArray()
        try { NativeCore.secret(name.encodeToByteArray(), bytes); credentials.add(name) } finally { bytes.fill(0) }
    }
    override fun close() {
        if (engine != 0L) { NativeCore.close(engine); engine = 0L }
        credentials.forEach { NativeCore.secret(it.encodeToByteArray(), byteArrayOf()) }
        credentials.clear()
    }
}
