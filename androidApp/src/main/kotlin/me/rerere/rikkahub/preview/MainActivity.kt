package me.rerere.rikkahub.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.ppisky.molite.*
import kotlinx.coroutines.*
import me.rerere.rikkahub.ui.MOLiteApp
import me.rerere.rikkahub.ui.UiState

class MainActivity : ComponentActivity() {
    private lateinit var model: MOLiteModel
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { model.host.selected(it) }
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { model.host.selected(it) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MOLiteModel(AndroidHost(applicationContext)) as T
        })[MOLiteModel::class.java]
        model.host.openDocument = { openFile.launch(arrayOf("*/*")) }
        model.host.createDocument = { saveFile.launch(it) }
        setContent {
            val state = model.state
            BackHandler(enabled = state.backStack.size > 1) { state.back() }
            MOLiteApp(state)
        }
    }
    override fun onDestroy() { model.host.detached(); super.onDestroy() }
}

class MOLiteModel(val host: AndroidHost) : ViewModel() {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val state = UiState(core = CoreClient(createCoreBridge()), host = host)
    init { runtimeScope.launch { state.start(runtimeScope) } }
    override fun onCleared() { runtimeScope.launch { try { state.stop() } finally { runtimeScope.cancel() } } }
}
