package me.rerere.rikkahub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ppisky.molite.*
import kotlinx.serialization.json.*
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.resources.Res

@Composable
fun ModelListSheet(state: UiState) {
    var search by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { state.modelPicker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("选择对话参数组", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().testTag("model-search"), placeholder = { Text("搜索参数组或模型") }, singleLine = true)
            val groups = state.resources["group"].orEmpty()
            if (groups.isEmpty()) {
                Text("尚无模型参数组，请先完成模型配置")
                Button({ state.modelPicker = false; state.navigate(Page.PROVIDERS) }) { Text("配置模型") }
            }
            CardGroup { groups.filter { it.text("id").contains(search, true) || it.text("model_id").contains(search, true) }.forEach { group ->
                item(onClick = { state.selectGroup(group.text("id")) }, headlineContent = { Text(group.text("id")) }, supportingContent = { Text("${group.text("model_id")} · ${group.text("protocol")}") }, trailingContent = { if (state.model == group.text("id")) UiIcon(Glyph.CHECK) })
            } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun CharacterPickerSheet(state: UiState) {
    val selected = remember { mutableStateListOf<String>().apply { addAll((state.activeResource?.get("character_ids") as? JsonArray).orEmpty().map { it.jsonPrimitive.content }) } }
    ModalBottomSheet(onDismissRequest = { state.assistantPicker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("选择对话角色", style = MaterialTheme.typography.titleLarge)
            Text("支持多角色，留空则不附加角色。请先新建对话再保存。")
            CardGroup { state.resources["character"].orEmpty().forEach { character ->
                val id = character.text("id")
                item(onClick = { if (id in selected) selected.remove(id) else selected.add(id) }, headlineContent = { Text(character.text("name")) },
                    supportingContent = { Text(character.text("character_markdown").take(100)) }, trailingContent = { Checkbox(id in selected, null) })
            } }
            Button({ state.selectCharacters(selected.toList()) }, enabled = state.activeChat.isNotEmpty() && !state.busy) { Text("保存角色") }
            TextButton({ state.assistantPicker = false; state.navigate(Page.CHARACTERS) }) { Text("管理角色") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ResourceEditSheet(state: UiState, editor: ResourceEditor) {
    val value = editor.value
    val kind = editor.kind
    val form = remember(editor) { mutableStateMapOf<String, String>() }
    fun get(key: String, default: String = "") = form[key] ?: value?.text(key, default) ?: default
    val generatedId = remember(editor) { newId() }
    val envName = remember(editor) { value?.text("api_key_env")?.takeIf { it.startsWith("MOLITE_APP_KEY_") } ?: "MOLITE_APP_KEY_${newId().replace("-", "")}" }
    var auth by remember(editor) { mutableStateOf(value == null || value["api_key_env"] is JsonPrimitive) }
    var vision by remember(editor) { mutableStateOf(value?.get("vision")?.jsonPrimitive?.booleanOrNull ?: false) }
    var stream by remember(editor) { mutableStateOf(value?.get("stream")?.jsonPrimitive?.booleanOrNull ?: true) }
    var key by remember(editor) { mutableStateOf("") }
    var validation by remember(editor) { mutableStateOf<String?>(null) }
    val protocols = listOf("chat_completions", "responses", "anthropic")
    var protocol by remember(editor) { mutableStateOf(value?.text("protocol") ?: protocols.first()) }
    val pretty = remember { Json { prettyPrint = true } }
    var parameters by remember(editor) { mutableStateOf(value?.get("parameters")?.let { pretty.encodeToString(JsonElement.serializer(), it) } ?: "{\"temperature\": 0.7}") }
    val title = when (kind) { "provider" -> "服务商"; "model" -> "模型"; "group" -> "参数组"; else -> "角色" }
    ModalBottomSheet(onDismissRequest = { if (!state.busy) { key = ""; state.resourceEditor = null } }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${if (value == null) "添加" else "编辑"}$title", style = MaterialTheme.typography.headlineSmall)
            @Composable fun field(name: String, label: String, default: String = "", multiline: Boolean = false) {
                OutlinedTextField(get(name, default), { form[name] = it }, Modifier.fillMaxWidth().testTag("resource-$name"), label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 4 else 1, maxLines = if (multiline) 12 else 1,
                    readOnly = name == "id" && value != null)
            }
            if (kind != "character") field("id", "名称 / 标识")
            when (kind) {
                "provider" -> {
                    field("base_url", "API 地址前缀", "https://api.openai.com/v1")
                    Text("支持 Chat Completions、Responses 和 Anthropic Messages；协议在参数组中选择。", style = MaterialTheme.typography.bodySmall)
                    Row { Checkbox(auth, { auth = it }); Text("接口需要 API Key", Modifier.padding(top = 12.dp)) }
                    if (auth) {
                        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth().testTag("resource-key"), label = { Text("API Key（仅本次进程）") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Text("密钥不写入数据库或 MOC，重新启动应用后需在此重新输入。编辑其他配置时留空保留当前会话密钥。", style = MaterialTheme.typography.bodySmall)
                    }
                }
                "model" -> {
                    SelectField("服务商", get("provider_id"), state.resources["provider"].orEmpty().map { it.text("id") }) { form["provider_id"] = it }
                    field("model", "接口模型 ID")
                    Row { Checkbox(vision, { vision = it }); Text("支持视觉输入", Modifier.padding(top = 12.dp)) }
                }
                "group" -> {
                    SelectField("模型", get("model_id"), state.resources["model"].orEmpty().map { it.text("id") }) { form["model_id"] = it }
                    SelectField("请求协议", protocol, protocols) { protocol = it }
                    Row { Checkbox(stream, { stream = it }); Text("流式输出", Modifier.padding(top = 12.dp)) }
                    field("timeout_seconds", "超时秒数（1–600）", "120")
                    OutlinedTextField(parameters, { parameters = it }, Modifier.fillMaxWidth(), label = { Text("模型原生参数（JSON 对象）") }, minLines = 4, maxLines = 10)
                    Text("参数名按所选协议填写。例如 Responses 使用 max_output_tokens，Anthropic 使用 max_tokens。", style = MaterialTheme.typography.bodySmall)
                }
                "character" -> {
                    field("name", "角色名称"); field("version", "版本", "1.0.0"); field("author_name", "作者", "MOLite 用户")
                    field("character_markdown", "角色设定", multiline = true); field("user_markdown", "用户设定（可选）", multiline = true)
                    field("opening_markdown", "开场白（可选）", multiline = true)
                }
            }
            validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button({
                try {
                    val id = if (kind == "character") value?.text("id") ?: generatedId else get("id").trim().also { require(it.isNotBlank()) { "请填写名称" } }
                    val resource = buildJsonObject {
                        put("id", id)
                        when (kind) {
                            "provider" -> { put("base_url", get("base_url", "https://api.openai.com/v1").trim()); put("api_key_env", if (auth) JsonPrimitive(envName) else JsonNull); put("protocols", JsonArray(protocols.map(::JsonPrimitive))) }
                            "model" -> { require(get("provider_id").isNotBlank() && get("model").isNotBlank()) { "请选择服务商并填写模型 ID" }; put("provider_id", get("provider_id")); put("model", get("model").trim()); put("vision", vision) }
                            "group" -> {
                                require(get("model_id").isNotBlank()) { "请选择模型" }
                                val timeout = get("timeout_seconds", "120").toLongOrNull()
                                require(timeout != null && timeout in 1..600) { "超时范围为 1–600 秒" }
                                val params = Json.parseToJsonElement(parameters); require(params is JsonObject) { "参数必须为 JSON 对象" }
                                put("model_id", get("model_id")); put("protocol", protocol); put("stream", stream); put("timeout_seconds", timeout); put("parameters", params)
                            }
                            "character" -> {
                                require(get("name").isNotBlank() && get("character_markdown").isNotBlank()) { "请填写角色名称和设定" }
                                put("name", get("name")); put("version", get("version", "1.0.0")); put("author_name", get("author_name", "MOLite 用户")); put("character_markdown", get("character_markdown")); put("user_markdown", get("user_markdown")); put("opening_markdown", get("opening_markdown").ifBlank { null }); put("classification", value?.get("classification") ?: buildJsonObject {})
                            }
                        }
                    }
                    state.saveResource(kind, resource, if (auth) key else ""); key = ""
                } catch (e: Exception) { validation = e.message ?: "配置无效" }
            }, enabled = state.ready && !state.busy, modifier = Modifier.fillMaxWidth().testTag("save-resource")) { Text("保存到 MOLite Core") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun PromptEditSheet(state: UiState, prompt: JsonObject) {
    var content by remember(prompt) { mutableStateOf(prompt.text("content")) }
    ModalBottomSheet(onDismissRequest = { state.promptEditor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(promptName(prompt.text("id")), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), minLines = 10, maxLines = 20, label = { Text("提示词") })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ state.savePrompt(prompt, content) }, enabled = !state.busy && content.isNotBlank()) { Text("保存") }
                OutlinedButton({ state.savePrompt(prompt, content, reset = true) }, enabled = !state.busy) { Text("恢复内核默认") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun LicenseSheet(onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf("THIRD_PARTY_NOTICES.txt") }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(selected) { text = Res.readBytes("files/licenses/$selected").decodeToString() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("离线开源许可", style = MaterialTheme.typography.titleLarge)
            SelectField("文档", selected, listOf("THIRD_PARTY_NOTICES.txt", "AGPL-3.0.txt", "Apache-2.0.txt", "NOTICE.txt", "OFL-1.1.txt", "Hugeicons-MIT.txt")) { selected = it }
            SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 32.dp)) }
        }
    }
}
