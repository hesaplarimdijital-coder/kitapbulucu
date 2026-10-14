package com.hesaplarimdijital.kitapbulucu.ui

import androidx.compose.ui.input.key.Key
import com.hesaplarimdijital.kitapbulucu.R
import desktopkit.input.CommonShortcuts
import desktopkit.input.KeyChord
import desktopkit.input.Shortcut
import desktopkit.input.ShortcutRegistry
import desktopkit.settings.DeskPreferences
import desktopkit.shell.ShellNavigator

/**
 * Every keyboard shortcut of the app in one table: the menu bar, the Meta+/ list and the list in
 * Settings are all built from it. The kit's common ones (Ctrl+K, Ctrl+, Ctrl+B, Ctrl+/) plus the
 * app's own; more arrive with their screens.
 */
object AppShortcuts {
    const val REFRESH = "kb.refresh"

    val Refresh = Shortcut(REFRESH, CommonShortcuts.ViewGroup, R.string.shortcut_refresh, KeyChord(Key.F5))

    val registry = ShortcutRegistry(
        groups = listOf(CommonShortcuts.ViewGroup, CommonShortcuts.HelpGroup),
        shortcuts = listOf(Refresh) + CommonShortcuts.all,
    )

    /** Runs the action of shortcut [id]; false when the id is unknown. */
    fun perform(id: String, navigator: ShellNavigator, prefs: DeskPreferences, onRefresh: () -> Unit = {}, onSearch: () -> Unit = {}): Boolean {
        when (id) {
            CommonShortcuts.SEARCH -> {
                navigator.navigate(Routes.SEARCH)
                onSearch()
            }
            CommonShortcuts.SETTINGS, CommonShortcuts.SHORTCUT_LIST -> navigator.navigate(Routes.SETTINGS)
            CommonShortcuts.TOGGLE_SIDEBAR -> prefs.updateSidebarCollapsed(!prefs.sidebarCollapsed)
            REFRESH -> onRefresh()
            else -> return false
        }
        return true
    }
}
