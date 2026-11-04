package com.hesaplarimdijital.kitapbulucu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import desktopkit.shell.LocalWidthClass
import desktopkit.shell.WidthClass

/**
 * The window's width class, as the kit's `DesktopKitRoot` gives it to the app: tests that build
 * the theme by hand (no root) wrap their content in this, so screens outside the shell (setup,
 * mapping, dialogs) take the phone layout at phone width.
 */
@Composable
fun WindowWidth(content: @Composable () -> Unit) {
    val width = WidthClass.fromWidth(LocalConfiguration.current.screenWidthDp.toFloat())
    CompositionLocalProvider(LocalWidthClass provides width, content = content)
}
