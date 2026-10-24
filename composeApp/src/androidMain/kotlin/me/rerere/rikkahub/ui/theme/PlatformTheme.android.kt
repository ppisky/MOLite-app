package me.rerere.rikkahub.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.View
import android.view.WindowInsetsController

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null
}

@Composable
actual fun UpdateSystemBars(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val activity = context as? Activity
        if (activity != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                activity.window.insetsController?.setSystemBarsAppearance(if (dark) 0 else flags, flags)
            } else {
                @Suppress("DEPRECATION")
                val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                @Suppress("DEPRECATION")
                view.systemUiVisibility = if (dark) view.systemUiVisibility and flags.inv() else view.systemUiVisibility or flags
            }
        }
    }
}
