package com.hesaplarimdijital.kitapbulucu.ui

import desktopkit.input.CommonShortcuts
import desktopkit.input.ShortcutRegistry
import desktopkit.settings.DeskPreferences
import desktopkit.shell.ShellNavigator

/**
 * Every keyboard shortcut of the app in one table: the menu bar, the Meta+/ list and the list in
 * Settings are all built from it. For now only the kit's common ones (Ctrl+K, Ctrl+, Ctrl+B, Ctrl+/);
 * the app's own (Ctrl+N, F5, …) arrive with their screens.
 */
object AppShortcuts {
    val registry = ShortcutRegistry(
        groups = listOf(CommonShortcuts.ViewGroup, CommonShortcuts.HelpGroup),
        shortcuts = CommonShortcuts.all,
    )

    /** Runs the action of shortcut [id]; false when the id is unknown. */
    fun perform(id: String, navigator: ShellNavigator, prefs: DeskPreferences): Boolean {
        when (id) {
            CommonShortcuts.SEARCH -> navigator.navigate(Routes.SEARCH)
            CommonShortcuts.SETTINGS, CommonShortcuts.SHORTCUT_LIST -> navigator.navigate(Routes.SETTINGS)
            CommonShortcuts.TOGGLE_SIDEBAR -> prefs.updateSidebarCollapsed(!prefs.sidebarCollapsed)
            else -> return false
        }
        return true
    }
}
