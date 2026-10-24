package me.rerere.rikkahub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ppisky.molite.*
import kotlinx.serialization.json.*
import me.rerere.rikkahub.resources.Res
import me.rerere.rikkahub.resources.molite
import me.rerere.rikkahub.ui.components.ui.*
import me.rerere.rikkahub.ui.theme.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun PageHost(state: UiState) {
    LaunchedEffect(state.page, state.ready) { if (state.ready && state.page == Page.MEMORY) state.loadMemory() }
    Scaffold(containerColor = CustomColors.topBarColors.containerColor, topBar = {
        LargeFlexibleTopAppBar(title = { Text(state.page.title) }, navigationIcon = {
            FilledTonalIconButton(state::back, Modifier.testTag("back")) { UiIcon(Glyph.BACK, "返回") }
        }, colors = CustomColors.topBarColors)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("page-${state.page.name}"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (state.page) {
                Page.SETTINGS -> SettingsPage(state)
                Page.THEMES -> ThemePage(state)
                Page.HISTORY -> {
                    TextField(state.search, { state.search = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索对话标题") }, singleLine = true)
                    if (state.conversations.isEmpty()) EmptyState("尚无对话，开始一次新的交流")
                    CardGroup { state.conversations.filter { it.title.contains(state.search, true) }.forEach { chat -> item(onClick = { state.goChat(chat.id) }, headlineContent = { Text(chat.title) }, leadingContent = { CardGroupIcon(Glyph.CHAT.vector) }) } }
                }
                Page.PROVIDERS -> ResourceList(state, "provider", "服务商", "添加兼容 Chat Completions、Responses 或 Anthropic Messages 的接口。")
                Page.MODELS -> ResourceList(state, "model", "模型", "填写服务商支持的模型 ID，可标记视觉能力。")
                Page.GROUPS -> ResourceList(state, "group", "参数组", "独立指定协议、流式、超时及模型原生参数。")
                Page.CHARACTERS -> ResourceList(state, "character", "角色", "角色设置可供多个对话复用；记忆由各自对话持有。")
                Page.PURPOSES -> PurposesPage(state)
                Page.MEMORY -> MemoryPage(state)
                Page.PROMPTS -> {
                    Text("四个空间直接使用 core 的内置提示词。修改持久保存，下一次新请求生效。")
                    CardGroup { state.prompts.forEach { prompt -> item(onClick = { state.promptEditor = prompt }, headlineContent = { Text(promptName(prompt.text("id"))) }, supportingContent = { Text(if (prompt.text("source") == "builtin") "内核默认" else "已自定义") }, leadingContent = { CardGroupIcon(Glyph.PROMPT_BOOK.vector) }) } }
                }
                Page.FILES -> FilesPage(state)
                Page.BACKUP -> {
                    Section("原生 MOC") {
                        Text("包含角色、对话、DMW、来源、计数和文件。API Key、应用配色与提示词空间不包含在导出包中。旧 Space 对话和记忆树尚不迁移，导入后会报告跳过的模块。")
                        UiField(state, "moc-password", "密码（留空则不加密）", secret = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button({ state.transferMoc(false) }, enabled = state.ready && !state.busy) { Text("导出 MOC") }
                            OutlinedButton({ state.transferMoc(true) }, enabled = state.ready && !state.busy) { Text("导入 MOC") }
                        }
                    }
                }
                Page.PLAN -> PlanPage()
                Page.ABOUT -> AboutPage()
                else -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsPage(state: UiState) {
    if (state.model.isEmpty()) Section("开始使用") {
        Text("依次配置服务商 → 模型 → 参数组 → 用途，然后开始对话。")
        Button({ state.navigate(Page.PROVIDERS) }) { Text("配置服务商") }
    }
    CardGroup(title = { Text("外观") }) {
        formItem(headlineContent = { Text("颜色模式") }) {
            ConnectedChoiceButtons(ColorMode.entries, state.colorMode, { state.colorMode = it }) { mode, selected -> ConnectedChoiceLabel(mode.label, selected) }
        }
        link(Page.THEMES, state, "默认 MOLite 紫，可选择预设配色或壁纸动态色")
    }
    CardGroup(title = { Text("模型与角色") }) {
        listOf(Page.PROVIDERS, Page.MODELS, Page.GROUPS, Page.PURPOSES, Page.CHARACTERS, Page.PROMPTS).forEach { link(it, state) }
    }
    CardGroup(title = { Text("对话与数据") }) { listOf(Page.MEMORY, Page.FILES, Page.BACKUP).forEach { link(it, state) } }
    CardGroup(title = { Text("项目") }) { link(Page.PLAN, state, "已接入、暂留及后续安排"); link(Page.ABOUT, state, "版本、源码与开源许可") }
}

@Composable
fun ThemePage(state: UiState) {
    CardGroup {
        switchItem(state.dynamicColor, { state.dynamicColor = it }, headlineContent = { Text("动态颜色") }, supportingContent = { Text("Android 12 及以上可跟随壁纸；其他平台使用所选预设") })
        switchItem(state.amoled, { state.amoled = it }, headlineContent = { Text("AMOLED 暗色模式") })
    }
    Text("预设主题", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceBright) { PresetThemeButtonGroup(state.themeId, onChangeTheme = { state.themeId = it; state.dynamicColor = false }) }
    Text("配色会保存在本机，下次启动继续使用。", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ResourceList(state: UiState, kind: String, name: String, description: String) {
    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button({ state.resourceEditor = ResourceEditor(kind) }, enabled = state.ready && !state.busy, modifier = Modifier.testTag("add-$kind")) { UiIcon(Glyph.PLUS); Spacer(Modifier.width(8.dp)); Text("添加$name") }
    val values = state.resources[kind].orEmpty()
    if (values.isEmpty()) EmptyState("尚未配置$name")
    CardGroup { values.forEach { value ->
        item(onClick = { state.resourceEditor = ResourceEditor(kind, value) }, headlineContent = { Text(value.text("name", value.text("id"))) }, supportingContent = {
            Text(when (kind) { "provider" -> value.text("base_url"); "model" -> "${value.text("provider_id")} · ${value.text("model")}"; "group" -> "${value.text("model_id")} · ${value.text("protocol")}"; else -> value.text("character_markdown").take(100) })
        }, leadingContent = { CardGroupIcon((if (kind == "character") Glyph.PERSON else Glyph.BRAIN).vector) })
    } }
}

@Composable
private fun PurposesPage(state: UiState) {
    var conversation by remember(state.purposes) { mutableStateOf(state.purposes.text("conversation")) }
    var memory by remember(state.purposes) { mutableStateOf(state.purposes.text("memory")) }
    var vision by remember(state.purposes) { mutableStateOf(state.purposes.text("vision_fallback")) }
    val groups = state.resources["group"].orEmpty().map { it.text("id") }
    Section("三个独立用途") {
        SelectField("对话", conversation, groups) { conversation = it }
        SelectField("记忆提炼", memory, groups) { memory = it }
        SelectField("视觉回退（可不选）", vision, listOf("") + groups) { vision = it }
        Text("视觉回退需选择使用视觉模型的参数组。模型与用途引用由 core 校验。", style = MaterialTheme.typography.bodySmall)
        Button({ state.savePurposes(buildJsonObject { put("conversation", conversation); put("memory", memory); put("vision_fallback", if (vision.isBlank()) JsonNull else JsonPrimitive(vision)) }) }, enabled = !state.busy && conversation.isNotEmpty() && memory.isNotEmpty()) { Text("保存用途") }
    }
}

@Composable
private fun MemoryPage(state: UiState) {
    Text("当前对话：${state.chatTitle}", style = MaterialTheme.typography.titleMedium)
    if (state.activeChat.isEmpty()) { EmptyState("请先创建或选择对话"); return }
    Section("轮次维护") {
        Text("默认每 12 轮提炼。编辑消息会让关联记忆失效并修复，互不相关的记忆继续保留。")
        UiField(state, "every-turns", "提炼间隔（1–200 轮）", "12")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ state.action {
                val turns = field("every-turns", "12").toIntOrNull()
                require(turns != null && turns in 1..200) { "请输入 1–200 之间的轮数" }
                call("extraction.configure", "conversation_id" to JsonPrimitive(activeChat), "every_turns" to JsonPrimitive(turns)); notice = "提炼间隔已保存"
            } }, enabled = !state.busy) { Text("保存间隔") }
            Button({ state.action { call("distill", "conversation_id" to JsonPrimitive(activeChat), "early" to JsonPrimitive(true)); notice = "提前提炼已完成" } }, enabled = !state.busy) { Text("提前提炼") }
        }
        OutlinedButton(state::loadMemory, enabled = !state.busy) { Text("刷新记忆") }
        Details("维护状态", state.memoryStatus)
    }
    if (state.memories.isEmpty()) EmptyState("暂无提炼记忆")
    state.memories.forEach { memory -> val doc = memory["document"]?.jsonObject ?: return@forEach
        Section(doc.text("title")) {
            Text(doc.text("body")); Text("${doc.text("kind")} · 有效 ${memory.text("valid")} · 归档 ${memory.text("archived")}", style = MaterialTheme.typography.labelSmall)
        }
    }
    Details("五维快照与上下文", state.snapshot)
}

@Composable
private fun FilesPage(state: UiState) {
    Text("UTF-8 文本和图片可作为聊天附件。PDF 可存储并随 MOC 导出，暂不解析内容。")
    Button({ state.action {
        val doc = host!!.readDocument() ?: return@action
        require(doc.bytes.size <= 64 * 1024 * 1024) { "单文件最多 64 MiB" }
        call("file.add", "name" to JsonPrimitive(doc.name), "mime" to JsonPrimitive(doc.mime), "base64" to JsonPrimitive(encodeBase64(doc.bytes))); refresh()
    } }, enabled = state.ready && !state.busy) { Text("导入文件") }
    if (state.resources["file"].isNullOrEmpty()) EmptyState("尚无文件")
    CardGroup { state.resources["file"].orEmpty().forEach { file -> item(onClick = { state.action {
        val data = call("file.read", "id" to file.getValue("id")).jsonObject
        host!!.writeDocument(file.text("name"), file.text("mime"), decodeBase64(data.text("base64")))
    } }, headlineContent = { Text(file.text("name")) }, supportingContent = { Text("${file.text("mime")} · ${file.text("bytes")} 字节 · 点击导出") }, leadingContent = { CardGroupIcon(Glyph.FILE.vector) }) } }
}

@Composable
private fun PlanPage() {
    Section("已接入 core") { Text("服务商与模型 · 参数组与用途 · 流式对话与停止 · 多角色选择 · 消息编辑与重生 · DMW 记忆与维护 · 提示词空间 · 文件与附件 · 原生 MOC") }
    Section("优先完善") { Text("1. 系统安全凭据库：目前 API Key 只在当前进程保留，重启后需重新输入。\n2. 对话分类、注入范围和叙事状态的可视化编辑。\n3. 生成失败后的冻结请求重试，以及记忆来源跳转。") }
    Section("有价值，暂留计划") { Text("快捷消息和本机消息收藏：先列入计划，当前未接入。\n记忆五维图与来源时间线：当前可查看 core 返回的数据，图形视图后续实现。") }
    Section("已从界面移除") { Text("MCP、Agent Skills、终端/SSH、联网搜索、媒体生成、语音服务、Web 服务器、云同步、翻译专页和旧助手工具。core 未提供这些能力。") }
}

@Composable
private fun AboutPage() {
    val uri = LocalUriHandler.current
    var licenses by remember { mutableStateOf(false) }
    if (licenses) LicenseSheet { licenses = false }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(Res.drawable.molite), "MOLite 图标", Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
        Text("MOLite", style = MaterialTheme.typography.headlineLarge)
        Text("0.1.0 · MOLite Core 0.1.0 · ABI v1")
    }
    Section("开源与署名") {
        Text("应用基于 RikkaHub 重绘版，遵循 GNU AGPL-3.0。保留 RikkaHub contributors 的版权、主题、组件和 HugeIcons 来源署名。")
        Text("MOLite-core 独立采用 Apache-2.0，包含其自身 NOTICE 与第三方许可。通过动态库连接不改变 core 自身的许可证；应用组合分发保留 AGPL 条款。")
        Text("图标由本项目用户提供。JetBrains Mono 使用 SIL OFL-1.1。完整文本见源码仓库 LICENSE、NOTICE、THIRD_PARTY_NOTICES.md 与 licenses/。")
        FilledTonalButton({ licenses = true }) { Text("离线开源许可") }
        TextButton({ uri.openUri("https://github.com/ppisky/MOLite-app") }) { Text("MOLite 应用源码与许可证") }
        TextButton({ uri.openUri("https://github.com/ppisky/MOLite-core") }) { Text("MOLite-core 源码") }
        TextButton({ uri.openUri("https://github.com/rikkahub/rikkahub") }) { Text("RikkaHub 上游项目") }
    }
}

fun CardGroupScope.link(page: Page, state: UiState, description: String? = null) {
    item(onClick = { state.navigate(page) }, headlineContent = { Text(page.title) }, supportingContent = description?.let { { Text(it) } }, leadingContent = { CardGroupIcon(page.icon.vector) })
}
@Composable fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
    }
}
@Composable fun UiField(state: UiState, key: String, label: String, default: String = "", secret: Boolean = false) {
    OutlinedTextField(state.field(key, default), { state.fields[key] = it }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None, shape = RoundedCornerShape(16.dp))
}
@Composable fun SelectField(label: String, value: String, values: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text(value.ifEmpty { "未选择" }, Modifier.weight(1f)); UiIcon(Glyph.DOWN) }
            DropdownMenu(expanded, { expanded = false }) { values.forEach { option -> DropdownMenuItem(text = { Text(option.ifEmpty { "不使用" }) }, onClick = { onChange(option); expanded = false }) } }
        }
    }
}
@Composable private fun EmptyState(text: String) { Text(text, Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun Details(title: String, value: JsonElement) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (expanded) SelectionContainer { Text(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), value), style = MaterialTheme.typography.bodySmall) }
    } }
}
fun promptName(id: String) = when (id) { "assistant" -> "助手"; "roleplay_director" -> "角色扮演指导"; "memory_distillation" -> "记忆提炼"; "vision_fallback" -> "视觉回退"; else -> id }
