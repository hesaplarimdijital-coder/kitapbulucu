package com.hesaplarimdijital.kitapbulucu.ui

import androidx.compose.ui.input.key.Key
import com.hesaplarimdijital.kitapbulucu.R
import desktopkit.input.CommonShortcuts
import desktopkit.input.KeyChord
import desktopkit.input.Shortcut
import desktopkit.input.ShortcutGroup
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
    const val ADD = "kb.add"
    const val UNDO = "kb.undo"

    val FileGroup = ShortcutGroup("file", R.string.menu_file)
    val EditGroup = ShortcutGroup("edit", R.string.menu_edit)

    val Refresh = Shortcut(REFRESH, CommonShortcuts.ViewGroup, R.string.shortcut_refresh, KeyChord(Key.F5))
    val Add = Shortcut(ADD, FileGroup, R.string.shortcut_add, KeyChord(Key.N, ctrl = true))
    val Undo = Shortcut(UNDO, EditGroup, R.string.shortcut_undo, KeyChord(Key.Z, ctrl = true))

    val registry = ShortcutRegistry(
        groups = listOf(FileGroup, EditGroup, CommonShortcuts.ViewGroup, CommonShortcuts.HelpGroup),
        shortcuts = listOf(Add, Undo, Refresh) + CommonShortcuts.all,
    )

    /** Runs the action of shortcut [id]; false when the id is unknown. */
    fun perform(
        id: String,
        navigator: ShellNavigator,
        prefs: DeskPreferences,
        onRefresh: () -> Unit = {},
        onSearch: () -> Unit = {},
        onAdd: () -> Unit = {},
        onUndo: () -> Unit = {},
    ): Boolean {
        when (id) {
            CommonShortcuts.SEARCH -> {
                navigator.navigate(Routes.SEARCH)
                onSearch()
            }
            CommonShortcuts.SETTINGS, CommonShortcuts.SHORTCUT_LIST -> navigator.navigate(Routes.SETTINGS)
            CommonShortcuts.TOGGLE_SIDEBAR -> prefs.updateSidebarCollapsed(!prefs.sidebarCollapsed)
            REFRESH -> onRefresh()
            ADD -> onAdd()
            UNDO -> onUndo()
            else -> return false
        }
        return true
    }
}
