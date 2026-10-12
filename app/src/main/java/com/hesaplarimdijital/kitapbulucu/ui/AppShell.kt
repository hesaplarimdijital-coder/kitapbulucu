package com.hesaplarimdijital.kitapbulucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.store.LoadFailure
import com.hesaplarimdijital.kitapbulucu.store.LoadStatus
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.components.SegmentedChoice
import desktopkit.components.SettingRow
import desktopkit.input.CommonShortcuts
import desktopkit.screens.MessageScreen
import desktopkit.screens.PageHeader
import desktopkit.settings.AppearanceSection
import desktopkit.settings.DeskPreferences
import desktopkit.settings.InputSection
import desktopkit.settings.SettingsSection
import desktopkit.settings.ShortcutListSection
import desktopkit.shell.DesktopShell
import desktopkit.shell.NavItem
import desktopkit.shell.NavSection
import desktopkit.shell.ShellSpec
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskTheme

/** Top-level screens. The route names never reach the user. */
object Routes {
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SHELVES = "shelves"
    const val SETTINGS = "settings"
}

@Composable
fun rememberAppSpec(): ShellSpec {
    val appName = stringResource(R.string.app_name)
    val section = stringResource(R.string.nav_section_library)
    val search = stringResource(R.string.nav_search)
    val library = stringResource(R.string.nav_library)
    val shelves = stringResource(R.string.nav_shelves)
    val settings = stringResource(R.string.nav_settings)
    return remember(appName, section, search, library, shelves, settings) {
        ShellSpec(
            appTitle = appName,
            sections = listOf(
                NavSection(
                    section,
                    listOf(
                        NavItem(Routes.SEARCH, search, DeskIcons.Search),
                        NavItem(Routes.LIBRARY, library, AppIcons.Book),
                        NavItem(Routes.SHELVES, shelves, AppIcons.Shelf),
                    ),
                ),
            ),
            footer = NavItem(Routes.SETTINGS, settings, DeskIcons.Settings),
            bottomBar = listOf(Routes.SEARCH, Routes.LIBRARY, Routes.SHELVES, Routes.SETTINGS),
        )
    }
}

/**
 * The whole interface inside desktop-kit's shell: menu bar and sidebar on DeX, a rail on medium
 * windows, a bottom bar on phones. [onShortcut] runs a shortcut from [AppShortcuts.registry].
 * Without a [store] (some screenshot tests) the screens show their placeholders only.
 */
@Composable
fun AppShell(
    prefs: DeskPreferences,
    widthClass: WidthClass,
    navController: NavHostController = rememberNavController(),
    store: LibraryStore? = null,
    settings: AppSettings? = null,
    picker: FilePicker? = null,
    onMap: (Owner) -> Unit = {},
    onShortcut: (String) -> Unit = {},
) {
    val spec = rememberAppSpec()
    val resources = LocalContext.current.resources
    DesktopShell(
        spec = spec,
        widthClass = widthClass,
        navController = navController,
        sidebarCollapsed = prefs.sidebarCollapsed,
        onSidebarCollapsedChange = prefs::updateSidebarCollapsed,
        menus = AppShortcuts.registry.menus(resources, perform = onShortcut),
        onSearch = { onShortcut(CommonShortcuts.SEARCH) },
    ) { route, _ ->
        Column(Modifier.fillMaxSize()) {
            if (store != null && picker != null) LibraryNotices(store, picker, onMap)
            Box(Modifier.weight(1f)) {
                when (route) {
                    Routes.SEARCH -> MessageScreen(
                        title = stringResource(R.string.placeholder_search_title),
                        icon = DeskIcons.Search,
                        pageTitle = spec.item(route)?.label,
                        description = stringResource(R.string.placeholder_search_text),
                    )
                    Routes.LIBRARY -> MessageScreen(
                        title = stringResource(R.string.placeholder_library_title),
                        icon = AppIcons.Book,
                        pageTitle = spec.item(route)?.label,
                        description = if (store == null) stringResource(R.string.placeholder_library_text) else libraryStatusText(store),
                    )
                    Routes.SHELVES -> MessageScreen(
                        title = stringResource(R.string.placeholder_shelves_title),
                        icon = AppIcons.Shelf,
                        pageTitle = spec.item(route)?.label,
                        description = stringResource(R.string.placeholder_shelves_text),
                    )
                    Routes.SETTINGS -> SettingsScreen(prefs, spec.item(route)?.label.orEmpty(), store, settings, picker)
                }
            }
        }
    }
}

/** A list that could not be read and stray files next to the lists, above every screen. */
@Composable
private fun LibraryNotices(store: LibraryStore, picker: FilePicker, onMap: (Owner) -> Unit) {
    val states by store.states.collectAsState()
    val warning by store.warning.collectAsState()
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val failed = Owner.entries.map { states.getValue(it) }.filter { it.status == LoadStatus.FAILED }
    val stray = warning
    if (failed.isEmpty() && stray == null) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        for (state in failed) {
            NoticeCard(
                title = LibraryTexts.listName(resources, state.owner),
                text = LibraryTexts.failure(resources, state.failure ?: LoadFailure.IO),
                tone = colors.danger,
            ) {
                if (state.failure == LoadFailure.NO_HEADER) {
                    DeskButton(stringResource(R.string.setup_map_columns), { onMap(state.owner) }, primary = true)
                } else {
                    DeskButton(stringResource(R.string.action_retry), store::refresh)
                }
                DeskButton(stringResource(R.string.action_choose_other_file), { picker.pick(state.owner) })
            }
        }
        if (stray != null) {
            NoticeCard(
                title = stringResource(R.string.stray_files_title),
                text = stringResource(R.string.stray_files_text, stray.names.joinToString(", ")),
                tone = colors.warning,
            ) {
                DeskButton(stringResource(R.string.action_understood), store::dismissWarning)
            }
        }
    }
}

/** Until the library screen exists: one line per list, "Babam'ın listesi 3 dk önce güncellendi · 6775 kitap". */
@Composable
private fun libraryStatusText(store: LibraryStore): String {
    val states by store.states.collectAsState()
    val resources = LocalContext.current.resources
    val now = System.currentTimeMillis()
    return Owner.entries.joinToString("\n") { owner ->
        val state = states.getValue(owner)
        LibraryTexts.updated(resources, owner, state, now) + " · " + LibraryTexts.status(resources, state)
    }
}

@Composable
private fun SettingsScreen(prefs: DeskPreferences, title: String, store: LibraryStore?, settings: AppSettings?, picker: FilePicker?) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageHeader(title) }
        if (store != null && settings != null && picker != null) item { LibrarySection(store, settings, picker) }
        item { AppearanceSection(prefs) }
        item { InputSection(prefs) }
        item { ShortcutListSection(AppShortcuts.registry) }
    }
}

/** Whose device this is, the two files and the optional folder. */
@Composable
private fun LibrarySection(store: LibraryStore, settings: AppSettings, picker: FilePicker) {
    val states by store.states.collectAsState()
    val resources = LocalContext.current.resources
    SettingsSection(stringResource(R.string.settings_library)) {
        SettingRow(stringResource(R.string.setup_who_title), subtitle = stringResource(R.string.setup_who_text)) {
            SegmentedChoice(
                options = listOf<Pair<Owner?, String>>(Owner.DAD to stringResource(R.string.setup_who_dad), Owner.ME to stringResource(R.string.setup_who_me)),
                selected = settings.me,
                onSelect = { owner -> if (owner != null) settings.updateMe(owner) },
            )
        }
        for (owner in Owner.entries) {
            val state = states.getValue(owner)
            val subtitle = listOfNotNull(state.displayName, LibraryTexts.status(resources, state)).joinToString(" · ")
            SettingRow(LibraryTexts.listName(resources, owner), subtitle = subtitle) {
                DeskButton(
                    stringResource(if (state.isLinked) R.string.setup_change else R.string.setup_pick),
                    { picker.pick(owner) },
                    icon = DeskIcons.Folder,
                )
            }
        }
        val folderText = stringResource(R.string.settings_folder_text) +
            if (store.treeUri == null) " " + stringResource(R.string.settings_folder_none) + "." else ""
        SettingRow(stringResource(R.string.settings_folder), subtitle = folderText) {
            DeskButton(stringResource(R.string.settings_folder_pick), picker::pickFolder, icon = DeskIcons.Folder)
        }
        SettingRow(stringResource(R.string.setup_title)) {
            DeskButton(stringResource(R.string.settings_reopen_setup), { settings.updateSetupDone(false) })
        }
    }
}
