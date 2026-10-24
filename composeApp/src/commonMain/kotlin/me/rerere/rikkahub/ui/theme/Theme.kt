package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.ui.theme.presets.*

data class PresetTheme(
    val id: String,
    val name: @Composable () -> Unit,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
) {
    fun getColorScheme(dark: Boolean) = if (dark) standardDark else standardLight
}

val PresetThemes by lazy {
    listOf(MoliteThemePreset, SakuraThemePreset, OceanThemePreset, SpringThemePreset, AutumnThemePreset,
        BlackThemePreset, MinimalThemePreset, ClaudeThemePreset)
}
val themeNames = listOf("MOLite 紫", "樱花粉", "海湾蓝", "原野绿", "秋黄", "中性黑", "极简白", "Claude")

val MoliteThemePreset = PresetTheme("molite", { androidx.compose.material3.Text("MOLite 紫") },
    lightColorScheme(primary = Color(0xFF6750A4), onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71), secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF7D5260), tertiaryContainer = Color(0xFFFFD8E4),
        background = Color(0xFFFEF7FF), surface = Color(0xFFFEF7FF), surfaceBright = Color(0xFFFEF7FF),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F2FA), surfaceContainer = Color(0xFFF3EDF7),
        surfaceContainerHigh = Color(0xFFECE6F0), surfaceContainerHighest = Color(0xFFE6E0E9)),
    darkColorScheme(primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFCCC2DC), secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8), tertiaryContainer = Color(0xFF633B48),
        background = Color(0xFF141218), surface = Color(0xFF141218), surfaceBright = Color(0xFF3B383E),
        surfaceContainerLowest = Color(0xFF0F0D13), surfaceContainerLow = Color(0xFF1D1B20), surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2B2930), surfaceContainerHighest = Color(0xFF36343B)))
enum class ColorMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
val LocalDarkMode = compositionLocalOf { false }

@Composable
expect fun platformColorScheme(dark: Boolean): ColorScheme?

@Composable
expect fun UpdateSystemBars(dark: Boolean)

@Composable
fun RikkahubTheme(themeId: String, mode: ColorMode, amoled: Boolean, dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val preset = PresetThemes.firstOrNull { it.id == themeId } ?: MoliteThemePreset
    val dynamic = platformColorScheme(dark)
    val scheme = if (dynamicColor && dynamic != null) dynamic else if (dark) preset.standardDark else preset.standardLight
    UpdateSystemBars(dark)
    CompositionLocalProvider(LocalDarkMode provides dark) {
      MaterialExpressiveTheme(
        colorScheme = if (dark && amoled) scheme.copy(surface = Color.Black, background = Color.Black) else scheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(),
        content = content,
      )
    }
}

object CustomColors {
    val listItemColors: ListItemColors
        @Composable get() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright)
    val topBarColors: TopAppBarColors
        @Composable get() = if (!LocalDarkMode.current) TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) else TopAppBarDefaults.topAppBarColors()
}
