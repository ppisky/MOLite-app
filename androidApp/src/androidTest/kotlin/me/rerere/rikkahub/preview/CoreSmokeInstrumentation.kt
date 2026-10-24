package me.rerere.rikkahub.preview

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import io.github.ppisky.molite.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

/** No paid APIs or credentials. Runs the actual packaged .so on the Android device. */
class CoreSmokeInstrumentation : Instrumentation() {
    private var verifyHttps = false
    private var httpsUrl = "https://example.com"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); verifyHttps = arguments?.getString("verify_https") == "true"; httpsUrl = arguments?.getString("https_url") ?: httpsUrl; start() }
    override fun onStart() {
        val result = Bundle()
        val root = File(targetContext.cacheDir, "core-smoke-${newId()}")
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            try { server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                var length = 0
                while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break; if (line.startsWith("Content-Length:", true)) length = line.substringAfter(":").trim().toInt() }
                repeat(length) { input.read() }
                val bytes = "data: {\"choices\":[{\"delta\":{\"content\":\"Android 内核 🌙\"}}]}\n\ndata: [DONE]\n\n".toByteArray()
                socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() }
            } } catch (_: Exception) { }
        }
        try {
            NativeCore.androidInit(targetContext.applicationContext)
            runBlocking {
                val client = CoreClient(createCoreBridge())
                suspend fun call(op: String, vararg pairs: Pair<String, JsonElement>) = client.request(operation(op, *pairs))
                suspend fun put(kind: String, value: String) = call("$kind.put", "value" to Json.parseToJsonElement(value))
                try {
                    client.open(root.path)
                    put("provider", """{"id":"p","base_url":"http://127.0.0.1:${server.localPort}","api_key_env":null,"protocols":["chat_completions"]}""")
                    put("model", """{"id":"m","provider_id":"p","model":"mock"}""")
                    put("group", """{"id":"g","model_id":"m","protocol":"chat_completions","stream":true,"timeout_seconds":20}""")
                    call("purposes.set", "value" to Json.parseToJsonElement("""{"conversation":"g","memory":"g"}"""))
                    put("conversation", """{"id":"c","title":"设备联调"}""")
                    val text = StringBuilder()
                    client.request(operation("chat", "request" to Json.parseToJsonElement("""{"request_id":"r","conversation_id":"c","text":"hello","attachments":[]}"""))) { if (it.text("type") == "text.delta") text.append(it.text("text")) }
                    check(text.toString() == "Android 内核 🌙") { "Streamed Unicode mismatch" }
                    check(call("history", "conversation_id" to JsonPrimitive("c")).jsonArray.size == 1)
                    client.close(); client.open(root.path)
                    check(call("history", "conversation_id" to JsonPrimitive("c")).jsonArray.size == 1)
                    if (verifyHttps) {
                        // Check platform connectivity independently to distinguish TLS plumbing from the device network.
                        val javaHttp = java.net.URL(httpsUrl).openConnection() as javax.net.ssl.HttpsURLConnection
                        javaHttp.connectTimeout = 15000; javaHttp.readTimeout = 15000
                        try { check(javaHttp.responseCode > 0) } finally { javaHttp.disconnect() }
                        put("provider", """{"id":"p","base_url":"$httpsUrl","api_key_env":null,"protocols":["chat_completions"]}""")
                        try {
                            call("chat", "request" to Json.parseToJsonElement("""{"request_id":"tls","conversation_id":"c","text":"TLS check","attachments":[]}"""))
                            error("Expected example.com HTTP error")
                        } catch (error: IllegalStateException) {
                            check(error.message?.contains("provider HTTP") == true) { "TLS did not reach HTTP: ${error.message}" }
                        }
                    }
                } finally { client.close() }
            }
            result.putString("stream", "PASS: packaged native ABI, Android streaming, Unicode, persistence${if (verifyHttps) ", HTTPS system verification" else ""}\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally { server.close(); worker.join(1000); root.deleteRecursively() }
    }
}
