package com.hesaplarimdijital.kitapbulucu

import android.content.pm.ActivityInfo
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.AutoRefresh
import com.hesaplarimdijital.kitapbulucu.ui.AppShell
import com.hesaplarimdijital.kitapbulucu.ui.rememberFilePicker
import com.hesaplarimdijital.kitapbulucu.ui.setup.MappingScreen
import com.hesaplarimdijital.kitapbulucu.ui.setup.SetupScreen
import com.hesaplarimdijital.kitapbulucu.ui.AppShortcuts
import desktopkit.DesktopKitRoot
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.ShellNavigator
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Set by the composition; runs a shortcut's action. */
    private var perform: (String) -> Boolean = { false }

    /** Reads both files again on start, on window focus and every minute (ARCHITECTURE 7). */
    private val autoRefresh by lazy {
        val store = App.store(this)
        AutoRefresh(store::refresh, store::refreshInBackground, SystemClock::elapsedRealtime)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the timer runs only while the activity is resumed and stops on its own when it is not
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { autoRefresh.whileResumed() } }
        // Phones are used upright only; tablets and DeX windows stay free.
        val screen = screenSizePx()
        if (isPhoneSized(screen.x, screen.y, resources.displayMetrics.density)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        setContent {
            val prefs = rememberDeskPreferences(accents = AppPalette.accents)
            val navController = rememberNavController()
            val navigator = remember(navController) { ShellNavigator(navController) }
            val store = App.store(this)
            val settings = App.settings(this)
            // the owner whose columns are being mapped; the mapping screen covers everything else
            var mapping by rememberSaveable { mutableStateOf<Owner?>(null) }
            // every Ctrl+K puts the cursor in the search field again, even when the search screen is already open
            var searchFocus by remember { mutableIntStateOf(0) }
            var addFocus by remember { mutableIntStateOf(0) }
            var mergeFocus by remember { mutableIntStateOf(0) }
            var openFile by remember { mutableIntStateOf(0) }
            perform = { id ->
                AppShortcuts.perform(
                    id, navigator, prefs,
                    onRefresh = store::refresh,
                    onSearch = { searchFocus++ },
                    onAdd = { addFocus++ },
                    onUndo = { store.undo() },
                    onOpen = { openFile++ },
                    onMerge = { mergeFocus++ },
                )
            }
            DesktopKitRoot(prefs, fonts = AppFonts.desk, type = AppFonts.type, colors = { AppPalette.warm(it, prefs.accent) }) { widthClass ->
                val picker = rememberFilePicker(store)
                // Ctrl+O: choose the file of this device's own list again
                LaunchedEffect(openFile) { if (openFile > 0) picker.pick(settings.me ?: Owner.DAD) }
                val mappingOwner = mapping
                when {
                    mappingOwner != null -> MappingScreen(mappingOwner, store, onClose = { mapping = null })
                    !settings.setupDone -> SetupScreen(store, settings, picker, onMap = { mapping = it }, onDone = { settings.updateSetupDone(true) })
                    else -> AppShell(prefs, widthClass, navController, store, settings, picker, onMap = { mapping = it }, onShortcut = { perform(it) }, searchFocus = searchFocus, addFocus = addFocus, mergeFocus = mergeFocus)
                }
            }
        }
    }

    /** Every return to the foreground reads both files again (ARCHITECTURE 7). */
    override fun onStart() {
        super.onStart()
        autoRefresh.onStart()
    }

    /** On DeX and in split screen the activity stays started while other windows are used: focus reads again too. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) autoRefresh.onFocusGained()
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
