package io.github.ppisky.molite

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class CoreIntegrationTest {
    @Test fun nativeStreamingUnicodePersistencePromptAndMocRoundTrip(): Unit = runBlocking {
        val root = Files.createTempDirectory("molite-integration-").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val receivedAuth = AtomicReference<String?>()
        server.executor = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        server.createContext("/v1/chat/completions") { exchange ->
            receivedAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            exchange.requestBody.readBytes()
            val response = "data: {\"choices\":[{\"delta\":{\"content\":\"你好，MOLite 🌙\"}}]}\n\ndata: [DONE]\n\n".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        val client = CoreClient(createCoreBridge())
        suspend fun call(op: String, vararg values: Pair<String, JsonElement>) = client.request(operation(op, *values))
        suspend fun put(kind: String, text: String) = call("$kind.put", "value" to Json.parseToJsonElement(text))
        try {
            client.open(root.resolve("source-中文🌙").path)
            client.secret("MOLITE_APP_KEY_INTEGRATION", "test-native-session-secret")
            put("provider", """{"id":"local","base_url":"http://127.0.0.1:${server.address.port}/v1","api_key_env":"MOLITE_APP_KEY_INTEGRATION","protocols":["chat_completions"]}""")
            put("model", """{"id":"text","provider_id":"local","model":"mock","vision":false}""")
            put("group", """{"id":"chat","model_id":"text","protocol":"chat_completions","stream":true,"parameters":{}}""")
            call("purposes.set", "value" to Json.parseToJsonElement("""{"conversation":"chat","memory":"chat","vision_fallback":null}"""))
            put("conversation", """{"id":"c","title":"中文🌙对话"}""")
            val deltas = mutableListOf<String>()
            val chat = operation("chat", "request" to Json.parseToJsonElement("""{"request_id":"r1","conversation_id":"c","text":"你好🌙","attachments":[]}"""))
            client.request(chat) { if (it.text("type") == "text.delta") deltas.add(it.text("text")) }
            assertEquals("你好，MOLite 🌙", deltas.joinToString(""))
            assertEquals("Bearer test-native-session-secret", receivedAuth.get())
            val history = call("history", "conversation_id" to JsonPrimitive("c")).jsonArray
            assertEquals("你好🌙", history.single().text("user"))
            assertEquals("你好，MOLite 🌙", history.single().text("assistant"))
            // Retrying the frozen request is idempotent and does not append a second exchange.
            client.request(chat)
            assertEquals(1, call("history", "conversation_id" to JsonPrimitive("c")).jsonArray.size)
            val prompt = call("prompt_spaces.get", "id" to JsonPrimitive("assistant")).jsonObject
            call("prompt_spaces.replace", "id" to JsonPrimitive("assistant"), "content" to JsonPrimitive("测试提示词 🌙"), "revision" to prompt.getValue("revision"))
            assertEquals("测试提示词 🌙", call("prompt_spaces.get", "id" to JsonPrimitive("assistant")).text("content"))
            val archive = root.resolve("roundtrip.moc")
            call("moc.export", "path" to JsonPrimitive(archive.path), "include_files" to JsonPrimitive(true), "passphrase" to JsonPrimitive("test-password"))
            client.close()
            client.open(root.resolve("source-中文🌙").path)
            assertEquals(1, call("history", "conversation_id" to JsonPrimitive("c")).jsonArray.size)
            val missingKey = assertFailsWith<IllegalStateException> {
                call("chat", "request" to Json.parseToJsonElement("""{"request_id":"needs-key-after-reopen","conversation_id":"c","text":"check","attachments":[]}"""))
            }
            assertTrue(missingKey.message.orEmpty().contains("environment variable missing"))
            client.close()
            client.open(root.resolve("imported").path)
            call("moc.import", "path" to JsonPrimitive(archive.path), "passphrase" to JsonPrimitive("test-password"))
            assertEquals(1, call("history", "conversation_id" to JsonPrimitive("c")).jsonArray.size)
            assertEquals("builtin", call("prompt_spaces.get", "id" to JsonPrimitive("assistant")).text("source"))
            assertFailsWith<IllegalStateException> { call("unknown.operation") }
            // A failed operation also releases its task, allowing close/reopen below.
        } finally { client.close(); server.stop(0); root.deleteRecursively() }
    }

    @Test fun cancelledPollingReleasesTaskBeforeEngineCloses() = runBlocking {
        val root = Files.createTempDirectory("molite-cancel-").toFile()
        val client = CoreClient(createCoreBridge())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        server.createContext("/chat/completions") { exchange ->
            try {
                exchange.requestBody.readBytes()
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use {
                    it.write("data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n".toByteArray()); it.flush()
                    Thread.sleep(2000)
                }
            } catch (_: Exception) { /* expected disconnect after cancellation */ }
        }
        server.start()
        try {
            client.open(root.path)
            suspend fun put(kind: String, value: String) = client.request(operation("$kind.put", "value" to Json.parseToJsonElement(value)))
            put("provider", """{"id":"p","base_url":"http://127.0.0.1:${server.address.port}","api_key_env":null,"protocols":["chat_completions"]}""")
            put("model", """{"id":"m","provider_id":"p","model":"mock"}""")
            put("group", """{"id":"g","model_id":"m","protocol":"chat_completions"}""")
            client.request(operation("purposes.set", "value" to Json.parseToJsonElement("""{"conversation":"g","memory":"g"}""")))
            put("conversation", """{"id":"c","title":"Cancel"}""")
            val started = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.Default) {
                client.request(operation("chat", "request" to Json.parseToJsonElement("""{"request_id":"cancel-me","conversation_id":"c","text":"stop","attachments":[]}"""))) {
                    if (it.text("type") == "text.delta") started.complete(Unit)
                }
            }
            withTimeout(5000) { started.await() }
            job.cancelAndJoin()
            client.close()
            client.open(root.path)
            assertEquals(0, client.request(operation("history", "conversation_id" to JsonPrimitive("c"))).jsonArray.size)
        } finally { client.close(); server.stop(0); root.deleteRecursively() }
    }
}
