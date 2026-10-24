package me.rerere.rikkahub

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.MOLiteApp
import me.rerere.rikkahub.ui.UiState
import io.github.ppisky.molite.*
import me.rerere.rikkahub.resources.Res
import me.rerere.rikkahub.resources.molite
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--verify-native") {
        runBlocking {
            val core = CoreClient(createCoreBridge())
            try {
                core.open(args.getOrNull(1) ?: error("Smoke data directory required"))
                check(core.request(operation("resource.list", "kind" to JsonPrimitive("conversation"))).jsonArray.isEmpty())
            } finally { core.close() }
        }
        return
    }
    runMOLite()
}

private fun runMOLite() = application {
    val state = remember { UiState(core = CoreClient(createCoreBridge()), host = DesktopHost()) }
    val scope = rememberCoroutineScope()
    Window(
        onCloseRequest = { scope.launch { state.stop(); exitApplication() } },
        title = "MOLite",
        icon = painterResource(Res.drawable.molite),
        state = rememberWindowState(width = 1200.dp, height = 820.dp),
    ) { MOLiteApp(state) }
}
