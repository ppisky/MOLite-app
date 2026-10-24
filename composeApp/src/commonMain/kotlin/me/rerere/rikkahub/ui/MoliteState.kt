package me.rerere.rikkahub.ui

import androidx.compose.runtime.*
import io.github.ppisky.molite.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import me.rerere.rikkahub.ui.theme.ColorMode

@Stable
class UiState(initialPage: Page = Page.CHAT, val core: CoreClient? = null, val host: AppHost? = null) {
    val backStack = mutableStateListOf(initialPage)
    val page: Page get() = backStack.last()
    var themeId by mutableStateOf("molite")
    var dynamicColor by mutableStateOf(false)
    var colorMode by mutableStateOf(ColorMode.SYSTEM)
    var amoled by mutableStateOf(false)
    var draft by mutableStateOf("")
    var search by mutableStateOf("")
    var activeChat by mutableStateOf("")
    var notice by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var generating by mutableStateOf(false)
    var modelPicker by mutableStateOf(false)
    var assistantPicker by mutableStateOf(false)
    var chatOptions by mutableStateOf(false)
    var streaming by mutableStateOf("")
    var pendingText by mutableStateOf("")
    var model by mutableStateOf("")
    var assistant by mutableStateOf("无角色")
    var editor: EditorUi? by mutableStateOf(null)
    var resourceEditor: ResourceEditor? by mutableStateOf(null)
    var promptEditor: JsonObject? by mutableStateOf(null)
    val fields = mutableStateMapOf<String, String>()
    val resources = mutableStateMapOf<String, List<JsonObject>>()
    val conversations = mutableStateListOf<ConversationUi>()
    val history = mutableStateListOf<JsonObject>()
    val memories = mutableStateListOf<JsonObject>()
    val prompts = mutableStateListOf<JsonObject>()
    val attachments = mutableStateListOf<JsonObject>()
    var memoryStatus by mutableStateOf<JsonElement>(JsonNull)
    var snapshot by mutableStateOf<JsonElement>(JsonNull)
    var purposes by mutableStateOf<JsonObject>(buildJsonObject {})
    var scope: CoroutineScope? = null
    private var job: Job? = null
    private var started = false
    val messages: List<MessageUi> get() = history.flatMap { e -> listOf(MessageUi(e.text("user"), true, e), MessageUi(e.text("assistant"), false, e)) }
    val chatTitle get() = conversations.firstOrNull { it.id == activeChat }?.title ?: "新对话"
    val activeResource get() = resources["conversation"].orEmpty().firstOrNull { it.text("id") == activeChat }
    fun field(key: String, default: String = "") = fields[key] ?: default
    fun navigate(page: Page) { if (this.page != page) backStack.add(page) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun edit(title: String, initial: String = "", onSave: (String) -> Unit) { editor = EditorUi(title, initial, onSave) }

    suspend fun start(scope: CoroutineScope) {
        if (started || core == null || host == null) return
        this.scope = scope
        started = true
        try {
            val prefs = withContext(Dispatchers.IO) { host.loadPreferences() }
            themeId = prefs["theme"] ?: "molite"
            dynamicColor = prefs["dynamic"] == "true"
            amoled = prefs["amoled"] == "true"
            colorMode = ColorMode.entries.firstOrNull { it.name == prefs["mode"] } ?: ColorMode.SYSTEM
            core.open(host.dataDirectory)
            ready = true
            refresh()
            activeChat = prefs["conversation"].orEmpty().takeIf { id -> conversations.any { it.id == id } } ?: conversations.firstOrNull()?.id.orEmpty()
            loadHistory()
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) { error = "内核启动失败：${e.message}" }
    }
    suspend fun savePreferences() {
        if (ready) host?.savePreferences(mapOf("theme" to themeId, "dynamic" to dynamicColor.toString(), "amoled" to amoled.toString(), "mode" to colorMode.name, "conversation" to activeChat))
    }
    suspend fun stop() {
        job?.cancelAndJoin()
        if (ready) { savePreferences(); core?.close(); ready = false }
    }
    fun action(block: suspend UiState.() -> Unit) {
        if (!ready || core == null) { notice = "内核尚未就绪"; return }
        if (busy) { notice = "请等待当前操作完成"; return }
        busy = true
        job = scope?.launch {
            error = null
            try { block() }
            catch (e: CancellationException) { notice = "操作已停止；已提交的数据以历史记录为准" }
            catch (e: Exception) { error = e.message ?: "内核操作失败" }
            finally { busy = false }
        }
    }
    suspend fun call(op: String, vararg pairs: Pair<String, JsonElement>): JsonElement = core!!.request(operation(op, *pairs))
    suspend fun refresh() {
        for (kind in listOf("provider", "model", "group", "character", "conversation", "file")) {
            resources[kind] = call("resource.list", "kind" to JsonPrimitive(kind)).jsonArray.map { it.jsonObject }
        }
        conversations.clear()
        conversations.addAll(resources["conversation"].orEmpty().map { ConversationUi(it.text("id"), it.text("title")) })
        purposes = try { call("resource.get", "kind" to JsonPrimitive("settings"), "id" to JsonPrimitive("purposes")).jsonObject } catch (_: IllegalStateException) { buildJsonObject {} }
        model = purposes.text("conversation")
        prompts.clear(); prompts.addAll(call("prompt_spaces.list").jsonArray.map { it.jsonObject })
        updateAssistant()
    }
    private fun updateAssistant() {
        val ids = activeResource?.get("character_ids") as? JsonArray ?: JsonArray(emptyList())
        assistant = resources["character"].orEmpty().filter { JsonPrimitive(it.text("id")) in ids }.joinToString("、") { it.text("name") }.ifBlank { "无角色" }
    }
    suspend fun loadHistory() {
        history.clear()
        if (activeChat.isNotBlank()) history.addAll(call("history", "conversation_id" to JsonPrimitive(activeChat)).jsonArray.map { it.jsonObject })
        updateAssistant()
    }
    fun goChat(id: String = activeChat) {
        if (busy && activeChat != id) { notice = "请先停止生成或等待当前操作完成"; return }
        activeChat = id; backStack.clear(); backStack.add(Page.CHAT)
        action { loadHistory(); savePreferences() }
    }
    fun newChat() = action {
        val id = newId()
        call("conversation.put", "value" to buildJsonObject { put("id", id); put("title", "新对话") })
        activeChat = id; draft = ""; attachments.clear(); refresh(); loadHistory()
        backStack.clear(); backStack.add(Page.CHAT); savePreferences()
    }
    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        if (model.isEmpty()) { notice = "请先配置服务商、模型、参数组和用途"; return }
        action {
            if (activeChat.isEmpty()) {
                activeChat = newId()
                call("conversation.put", "value" to buildJsonObject { put("id", activeChat); put("title", text.take(24)) })
            }
            generating = true; pendingText = text; streaming = ""
            val request = buildJsonObject {
                put("request_id", newId()); put("conversation_id", activeChat); put("text", text)
                put("attachments", JsonArray(attachments.map { JsonPrimitive(it.text("id")) }))
            }
            try {
                core!!.request(operation("chat", "request" to request)) { event ->
                    if (event.text("type") == "text.delta") withContext(Dispatchers.Main) { streaming += event.text("text") }
                }
                draft = ""; attachments.clear()
            } finally {
                generating = false; pendingText = ""; streaming = ""
                withContext(NonCancellable) { refresh(); loadHistory() }
            }
        }
    }
    fun cancel() { job?.cancel() }
    fun selectGroup(id: String) = action {
        call("purposes.set", "value" to JsonObject(purposes + ("conversation" to JsonPrimitive(id))))
        refresh(); modelPicker = false
    }
    fun savePurposes(value: JsonObject) = action { call("purposes.set", "value" to value); refresh(); notice = "用途已保存" }
    fun saveResource(kind: String, value: JsonObject, secret: String = "") = action {
        if (kind == "provider" && secret.isNotEmpty()) core!!.secret(value.text("api_key_env"), secret)
        call("$kind.put", "value" to value); refresh(); resourceEditor = null; notice = "已保存"
    }
    fun selectCharacters(ids: List<String>) = action {
        val conversation = activeResource ?: error("请先新建对话")
        call("conversation.put", "value" to JsonObject(conversation + ("character_ids" to JsonArray(ids.map(::JsonPrimitive)))))
        refresh(); assistantPicker = false
    }
    fun renameChat() {
        val resource = activeResource ?: return
        edit("对话标题", chatTitle) { title -> action {
            call("conversation.put", "value" to JsonObject(resource + ("title" to JsonPrimitive(title))))
            refresh()
        } }
    }
    fun revise(message: MessageUi, text: String) = action {
        val e = message.exchange ?: return@action
        call("exchange.revise", "request_id" to e.getValue("request_id"), "revision" to e.getValue("revision"), (if (message.user) "user" else "assistant") to JsonPrimitive(text))
        loadHistory()
    }
    fun regenerate(message: MessageUi) = action {
        val e = message.exchange ?: return@action
        call("exchange.regenerate", "request_id" to e.getValue("request_id"), "revision" to e.getValue("revision")); loadHistory()
    }
    fun loadMemory() = action {
        require(activeChat.isNotBlank()) { "请先创建或选择对话" }
        memories.clear(); memories.addAll(call("memory.list", "conversation_id" to JsonPrimitive(activeChat)).jsonArray.map { it.jsonObject })
        memoryStatus = call("extraction.status", "conversation_id" to JsonPrimitive(activeChat))
        fields["every-turns"] = memoryStatus.text("every_turns", "12")
        snapshot = call("snapshot", "conversation_id" to JsonPrimitive(activeChat))
    }
    fun addAttachment() = action {
        val document = host!!.readDocument() ?: return@action
        require(document.bytes.size <= 16 * 1024 * 1024) { "附件不能超过 16 MiB" }
        require(attachments.size < 16) { "最多 16 个附件" }
        require(attachments.sumOf { (it["bytes"]?.jsonPrimitive?.longOrNull ?: 0).toInt() } + document.bytes.size <= 16 * 1024 * 1024) { "本次附件合计不能超过 16 MiB" }
        require(document.mime.startsWith("text/") || document.mime in listOf("image/png", "image/jpeg", "image/webp", "image/gif")) { "聊天附件支持 UTF-8 文本与 PNG/JPEG/WebP/GIF；PDF 内容暂不解析" }
        val file = call("file.add", "name" to JsonPrimitive(document.name), "mime" to JsonPrimitive(document.mime), "base64" to JsonPrimitive(encodeBase64(document.bytes))).jsonObject
        attachments.add(file); refresh()
    }
    fun savePrompt(value: JsonObject, content: String, reset: Boolean = false) = action {
        call(if (reset) "prompt_spaces.reset" else "prompt_spaces.replace", "id" to value.getValue("id"), "revision" to value.getValue("revision"), "content" to JsonPrimitive(content))
        refresh(); promptEditor = null; notice = "提示词已保存，下一次新请求生效"
    }
    fun transferMoc(import: Boolean) = action {
        val name = "transfer-${newId()}.moc"
        try {
            if (import) {
                val doc = host!!.readDocument() ?: return@action
                val path = host.writePrivate(name, doc.bytes)
                val result = call("moc.import", "path" to JsonPrimitive(path), "passphrase" to (field("moc-password").takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull))
                refresh(); loadHistory(); notice = "导入结果：$result"
            } else {
                val path = "${host!!.dataDirectory}/$name"
                call("moc.export", "path" to JsonPrimitive(path), "include_files" to JsonPrimitive(true), "passphrase" to (field("moc-password").takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull))
                val bytes = host.readPrivate(name)
                if (host.writeDocument("MOLite-${newId().take(8)}.moc", "application/octet-stream", bytes)) notice = "MOC 已导出；API Key 和提示词空间不包含在其中"
            }
        } finally { host!!.removePrivate(name); fields.remove("moc-password") }
    }
}

data class ConversationUi(val id: String, val title: String)
data class MessageUi(val text: String, val user: Boolean = false, val exchange: JsonObject? = null)
class EditorUi(val title: String, val initial: String, val onSave: (String) -> Unit)
data class ResourceEditor(val kind: String, val value: JsonObject? = null)
enum class Page(val title: String, val icon: Glyph = Glyph.SETTINGS) {
    CHAT("对话", Glyph.CHAT), HISTORY("对话历史", Glyph.HISTORY), SETTINGS("设置"), THEMES("主题", Glyph.PALETTE),
    PROVIDERS("服务商", Glyph.SERVER), MODELS("模型", Glyph.BRAIN), GROUPS("模型参数组", Glyph.SETTINGS), PURPOSES("模型用途", Glyph.BRAIN),
    CHARACTERS("角色", Glyph.PERSON), MEMORY("对话记忆", Glyph.BRAIN), PROMPTS("提示词空间", Glyph.PROMPT_BOOK),
    FILES("文件", Glyph.FOLDER), BACKUP("MOC 导入与导出", Glyph.DATABASE), PLAN("功能计划", Glyph.BOOK), ABOUT("关于 MOLite", Glyph.INFO)
}
