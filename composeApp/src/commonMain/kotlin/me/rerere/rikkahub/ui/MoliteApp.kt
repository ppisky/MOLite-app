package me.rerere.rikkahub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.resources.Res
import me.rerere.rikkahub.resources.molite
import me.rerere.rikkahub.ui.theme.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun MOLiteApp(state: UiState) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.start(scope) }
    LaunchedEffect(state.themeId, state.dynamicColor, state.colorMode, state.amoled, state.ready) { state.savePreferences() }
    RikkahubTheme(state.themeId, state.colorMode, state.amoled, state.dynamicColor) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.notice) {
            state.notice?.let { snackbar.showSnackbar(it, withDismissAction = true); state.notice = null }
        }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        Surface(Modifier.fillMaxSize().testTag("app")) {
            BoxWithConstraints {
                val wide = maxWidth > maxHeight && maxWidth >= 1100.dp
                val page: @Composable () -> Unit = {
                    Box {
                        if (state.page == Page.CHAT) ChatPage(state, wide) { scope.launch { drawer.open() } }
                        else PageHost(state)
                        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                    }
                }
                if (state.page != Page.CHAT) page()
                else if (wide) Row(Modifier.fillMaxSize()) {
                    PermanentDrawerSheet(Modifier.width(300.dp)) { Sidebar(state) }
                    Box(Modifier.weight(1f)) { page() }
                } else ModalNavigationDrawer(drawerState = drawer, drawerContent = {
                    ModalDrawerSheet(Modifier.width(300.dp)) { Sidebar(state) { scope.launch { drawer.close() } } }
                }, content = page)
            }
        }
        state.error?.let { error -> AlertDialog(onDismissRequest = { state.error = null }, title = { Text("操作未完成") }, text = { Text(error) },
            confirmButton = { TextButton({ state.error = null }) { Text("知道了") } }) }
        state.editor?.let { editor ->
            var value by remember(editor) { mutableStateOf(editor.initial) }
            AlertDialog(onDismissRequest = { state.editor = null }, title = { Text(editor.title) },
                text = { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth().testTag("editor-input"), minLines = 3, maxLines = 12) },
                confirmButton = { TextButton({ if (value.isNotBlank()) { editor.onSave(value.trim()); state.editor = null } }, enabled = value.isNotBlank()) { Text("保存") } },
                dismissButton = { TextButton({ state.editor = null }) { Text("取消") } })
        }
        if (state.modelPicker) ModelListSheet(state)
        if (state.assistantPicker) CharacterPickerSheet(state)
        state.resourceEditor?.let { ResourceEditSheet(state, it) }
        state.promptEditor?.let { PromptEditSheet(state, it) }
    }
}

@Composable
fun Sidebar(state: UiState, onNavigate: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(Res.drawable.molite), "MOLite", Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
            Column { Text("MOLite", style = MaterialTheme.typography.titleLarge); Text("让对话拥有记忆", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        FilledTonalButton({ state.newChat(); onNavigate() }, Modifier.fillMaxWidth(), enabled = state.ready && !state.busy) {
            UiIcon(Glyph.NEW_CHAT); Spacer(Modifier.width(8.dp)); Text("新建对话")
        }
        listOf(Page.HISTORY, Page.CHARACTERS, Page.MEMORY, Page.PROMPTS).forEach { destination ->
            NavigationDrawerItem(label = { Text(destination.title) }, selected = false, icon = { UiIcon(destination.icon) },
                onClick = { state.navigate(destination); onNavigate() }, modifier = Modifier.testTag("nav-${destination.name}"))
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.conversations.isEmpty()) item { Text("尚无对话", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.conversations, key = { it.id }) { chat ->
                NavigationDrawerItem(label = { Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = state.activeChat == chat.id,
                    onClick = { state.goChat(chat.id); onNavigate() }, icon = { UiIcon(Glyph.CHAT) })
            }
        }
        NavigationDrawerItem(label = { Text("设置") }, selected = false, icon = { UiIcon(Glyph.SETTINGS) }, onClick = { state.navigate(Page.SETTINGS); onNavigate() }, modifier = Modifier.testTag("nav-SETTINGS"))
        Text(if (state.ready) "MOLite Core · C ABI v1" else "正在加载 MOLite Core…", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GradientAvatar(name: String, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    val hue = avatarHue(name.ifBlank { "?" })
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(Color.hsl(hue, 0.65f, 0.55f), Color.hsl((hue + 120f) % 360f, 0.65f, 0.55f)))))
}
expect fun avatarHue(name: String): Float
expect fun localGreeting(): String
