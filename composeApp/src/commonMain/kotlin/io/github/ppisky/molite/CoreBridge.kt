package io.github.ppisky.molite

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.time.TimeSource

interface CoreBridge {
    fun open(path: String)
    fun submit(json: String): Long
    fun next(task: Long): String?
    fun cancel(task: Long)
    fun release(task: Long)
    fun secret(name: String, value: String)
    fun close()
}

expect fun createCoreBridge(): CoreBridge

/** Polls off the UI thread; finally always releases tasks, including on cancellation. */
class CoreClient(private val bridge: CoreBridge) {
    suspend fun open(path: String) = withContext(Dispatchers.IO) { bridge.open(path) }
    suspend fun request(value: JsonObject, event: suspend (JsonObject) -> Unit = {}): JsonElement = withContext(Dispatchers.IO) {
        val task = bridge.submit(JsonObject(value + ("version" to JsonPrimitive(1))).toString())
        val started = TimeSource.Monotonic.markNow()
        try {
            while (true) {
                ensureActive()
                if (started.elapsedNow().inWholeSeconds > 900) error("内核任务超时，请检查历史记录后重试")
                val raw = bridge.next(task) ?: continue
                val item = Json.parseToJsonElement(raw).jsonObject
                when (item["type"]?.jsonPrimitive?.content) {
                    "completed" -> return@withContext item["result"] ?: JsonNull
                    "failed" -> error(item["error"]?.jsonPrimitive?.content ?: "内核操作失败")
                    "cancelled" -> throw CancellationException("已停止生成")
                    else -> event(item)
                }
            }
            @Suppress("UNREACHABLE_CODE") JsonNull
        } finally {
            withContext(NonCancellable) {
                try { bridge.cancel(task) } finally { bridge.release(task) }
            }
        }
    }
    suspend fun secret(name: String, value: String) = withContext(Dispatchers.IO) { bridge.secret(name, value) }
    suspend fun close() = withContext(Dispatchers.IO) { bridge.close() }
}

fun operation(op: String, vararg entries: Pair<String, JsonElement>) = buildJsonObject {
    put("op", op)
    entries.forEach { (key, value) -> put(key, value) }
}
fun JsonElement.text(key: String, default: String = "") = (this as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull } ?: default
