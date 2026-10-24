package me.rerere.rikkahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ppisky.molite.text

@Composable
fun ChatPage(state: UiState, wide: Boolean, onMenu: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Surface(onClick = state::renameChat, color = Color.Transparent) { Column {
                        Text(state.chatTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.model.isNotEmpty()) {
                            Text("${state.assistant} / ${state.model}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = LocalContentColor.current.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } }
                },
                navigationIcon = { if (!wide) IconButton(onMenu) { UiIcon(Glyph.MENU, "打开菜单") } },
                actions = {
                    IconButton({ state.chatOptions = !state.chatOptions }) { UiIcon(if (state.chatOptions) Glyph.CLOSE else Glyph.OPTIONS, "会话选项") }
                    IconButton({ state.newChat() }, Modifier.testTag("new-chat")) { UiIcon(Glyph.NEW_CHAT, "新建会话") }
                },
            )
        },
        bottomBar = { ChatInput(state) },
    ) { padding ->
        val list = rememberLazyListState()
        LaunchedEffect(state.activeChat, state.messages.size, state.streaming.length) {
            if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.lastIndex)
        }
        if (state.chatOptions) {
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                OutlinedTextField(state.search, { state.search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索") }, leadingIcon = { UiIcon(Glyph.SEARCH, modifier = Modifier.size(20.dp)) }, singleLine = true, shape = CircleShape)
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.messages.filter { it.text.contains(state.search, true) }) { _, message ->
                        OutlinedCard(onClick = { state.chatOptions = false }) { Text(message.text, Modifier.padding(16.dp), maxLines = 5) }
                    }
                }
            }
        } else LazyColumn(Modifier.fillMaxSize().testTag("messages"), state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.messages.isEmpty() && !state.generating) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("MOLite", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Text("让对话拥有记忆", style = MaterialTheme.typography.titleMedium)
                    if (state.model.isEmpty()) FilledTonalButton({ state.navigate(Page.PROVIDERS) }) { Text("开始配置模型") }
                }
            }
            itemsIndexed(state.messages) { _, message -> MessageBubble(message, state) }
            if (state.generating) {
                item { MessageBubble(MessageUi(state.pendingText, true), state) }
                item { MessageBubble(MessageUi(state.streaming.ifEmpty { "正在生成…" }), state) }
            }
        }
    }
}

@Composable
private fun ChatInput(state: UiState) {
    val containerShape = MaterialTheme.shapes.largeIncreased
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 8.dp).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.attachments.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.attachments.toList().forEach { file -> InputChip(selected = true, onClick = { if (!state.busy) state.attachments.remove(file) }, label = { Text(file.text("name")) }, trailingIcon = { UiIcon(Glyph.CLOSE, modifier = Modifier.size(14.dp)) }) }
        }
        Surface(Modifier.fillMaxWidth(), shape = containerShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextField(
                    value = state.draft, onValueChange = { state.draft = it },
                    modifier = Modifier.fillMaxWidth().testTag("chat-input").onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && it.isCtrlPressed) { state.send(); true } else false
                    },
                    shape = MaterialTheme.shapes.largeIncreased,
                    placeholder = { Text("输入消息与AI聊天") },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                    colors = TextFieldDefaults.colors(
                        unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton({ state.modelPicker = true }, Modifier.testTag("model-picker")) {
                            if (state.model.isBlank()) UiIcon(Glyph.BRAIN, "选择模型", Modifier.size(20.dp))
                            else UiIcon(Glyph.BRAIN, "选择模型参数组", Modifier.size(24.dp))
                        }
                        IconButton({ state.assistantPicker = true }) { UiIcon(Glyph.PERSON, "选择角色") }
                        IconButton({ state.navigate(Page.MEMORY) }) { UiIcon(Glyph.BRAIN, "对话记忆") }
                    }
                    Surface(onClick = state::addAttachment, Modifier.size(30.dp), enabled = !state.busy,
                        shape = CircleShape, color = Color.Transparent) {
                        Box(contentAlignment = Alignment.Center) { UiIcon(Glyph.PLUS, "添加附件") }
                    }
                    val empty = state.draft.isBlank()
                    Surface(onClick = { if (state.generating) state.cancel() else state.send() }, enabled = state.generating || (!empty && state.ready && !state.busy),
                        modifier = Modifier.size(30.dp).testTag("send-message"), shape = CircleShape,
                        color = if (empty) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                        contentColor = if (empty) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onPrimary) {
                        Box(contentAlignment = Alignment.Center) { UiIcon(if (state.generating) Glyph.CLOSE else Glyph.SEND, if (state.generating) "停止生成" else "发送消息", Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageUi, state: UiState) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.user) Alignment.End else Alignment.Start) {
        if (!message.user) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiIcon(Glyph.SPARK, modifier = Modifier.size(20.dp))
                Text(state.assistant, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
        }
        Surface(shape = RoundedCornerShape(22.dp), color = if (message.user) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 680.dp)) {
            SelectionContainer { Text(message.text, Modifier.padding(if (message.user) 16.dp else 4.dp), style = MaterialTheme.typography.bodyLarge) }
        }
        Row {
            IconButton({ clipboard.setText(AnnotatedString(message.text)) }, Modifier.size(36.dp)) { UiIcon(Glyph.COPY, "复制消息", Modifier.size(16.dp)) }
            if (message.exchange != null) {
                IconButton({ state.edit("编辑消息", message.text) { text -> state.revise(message, text) } }, Modifier.size(36.dp), enabled = !state.busy) { UiIcon(Glyph.EDIT, "编辑消息", Modifier.size(16.dp)) }
                if (!message.user) IconButton({ state.regenerate(message) }, Modifier.size(36.dp), enabled = !state.busy) { UiIcon(Glyph.HISTORY, "重新生成", Modifier.size(16.dp)) }
            }
        }
    }
}
