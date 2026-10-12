package com.hesaplarimdijital.kitapbulucu

import android.content.pm.ActivityInfo
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.hesaplarimdijital.kitapbulucu.ui.AppShell
import com.hesaplarimdijital.kitapbulucu.ui.AppShortcuts
import desktopkit.DesktopKitRoot
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.ShellNavigator

class MainActivity : ComponentActivity() {

    /** Set by the composition; runs a shortcut's action. */
    private var perform: (String) -> Boolean = { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones are used upright only; tablets and DeX windows stay free.
        val screen = screenSizePx()
        if (isPhoneSized(screen.x, screen.y, resources.displayMetrics.density)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        setContent {
            val prefs = rememberDeskPreferences()
            val navController = rememberNavController()
            val navigator = remember(navController) { ShellNavigator(navController) }
            perform = { id -> AppShortcuts.perform(id, navigator, prefs) }
            DesktopKitRoot(prefs) { widthClass ->
                AppShell(prefs, widthClass, navController, onShortcut = { perform(it) })
            }
        }
    }

    /** Ctrl/Alt/Meta chords and F keys only: plain keys must keep typing into text fields. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        AppShortcuts.registry.matchNative(event)?.let { perform(it.id) } == true || super.dispatchKeyEvent(event)

    /** Meta+/ (or the system's keyboard shortcuts screen) lists the same table. */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        data.addAll(AppShortcuts.registry.keyboardShortcutGroups(resources))
    }

    /** Size of the whole display, not of the (possibly resized) window. */
    private fun screenSizePx(): Point =
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealSize(it)
            }
        }
}

/** A device whose display is narrower than 600 dp on its short side is a phone. */
fun isPhoneSized(widthPx: Int, heightPx: Int, density: Float): Boolean = minOf(widthPx, heightPx) / density < 600f
