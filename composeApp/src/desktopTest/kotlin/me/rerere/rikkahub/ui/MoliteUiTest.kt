package me.rerere.rikkahub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MoliteUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allSupportedPagesRenderWithoutUnsupportedEntrypoints() {
        val state = UiState()
        compose.setContent { MOLiteApp(state) }
        Page.entries.forEach { page ->
            compose.runOnIdle { state.navigate(page) }
            compose.waitForIdle()
            if (page != Page.CHAT) compose.onNodeWithTag("page-${page.name}").assertExists()
            if (page in listOf(Page.CHAT, Page.SETTINGS, Page.THEMES, Page.PROVIDERS, Page.CHARACTERS, Page.ABOUT)) screenshot(page.name.lowercase())
        }
    }
    @Test fun freshInstallDefaultsToPurpleAndDoesNotInventReplies() {
        val state = UiState()
        compose.setContent { MOLiteApp(state) }
        compose.onNodeWithTag("chat-input").performTextInput("你好")
        compose.onNodeWithTag("send-message").assertIsNotEnabled()
        compose.runOnIdle { assertEquals("molite", state.themeId); assertTrue(!state.dynamicColor); assertTrue(state.messages.isEmpty()) }
        screenshot("fresh-chat")
    }
    @Test fun themeSelectionAndNavigationWorkOnNarrowViewport() {
        val state = UiState(Page.THEMES)
        compose.setContent { Box(Modifier.requiredSize(390.dp, 780.dp)) { MOLiteApp(state) } }
        compose.onNodeWithTag("theme-ocean").performClick()
        compose.runOnIdle { assertEquals("ocean", state.themeId); state.goChat() }
        compose.onNodeWithContentDescription("打开菜单").performClick()
        compose.onNodeWithTag("nav-CHARACTERS").performClick()
        compose.onNodeWithTag("page-CHARACTERS").assertExists()
        screenshot("mobile-characters")
    }
    @Test fun pickersAreBottomSheetsAndEmptyStateLinksToConfiguration() {
        val state = UiState().apply { modelPicker = true }
        compose.setContent { Box(Modifier.requiredSize(390.dp, 780.dp)) { MOLiteApp(state) } }
        compose.onNodeWithTag("model-search").assertExists()
        compose.onNodeWithText("配置模型").performClick()
        compose.runOnIdle { assertEquals(Page.PROVIDERS, state.page); assertTrue(!state.modelPicker) }
        screenshot("mobile-providers")
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = compose.onNodeWithTag("app").captureToImage().asSkiaBitmap()
        val data = Image.makeFromBitmap(image).encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding")
        File(System.getProperty("ui.screenshot.dir")).apply { mkdirs() }.resolve("$name.png").writeBytes(data.bytes)
    }
}
