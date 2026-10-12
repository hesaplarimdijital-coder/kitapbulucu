package com.hesaplarimdijital.kitapbulucu.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.trial.TrialScreen
import desktopkit.components.DeskIcons
import desktopkit.input.CommonShortcuts
import desktopkit.screens.MessageScreen
import desktopkit.screens.PageHeader
import desktopkit.settings.AppearanceSection
import desktopkit.settings.DeskPreferences
import desktopkit.settings.InputSection
import desktopkit.settings.ShortcutListSection
import desktopkit.shell.DesktopShell
import desktopkit.shell.NavItem
import desktopkit.shell.NavSection
import desktopkit.shell.ShellSpec
import desktopkit.shell.WidthClass

/** Top-level screens. The route names never reach the user. */
object Routes {
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SHELVES = "shelves"
    const val SETTINGS = "settings"

    /** Temporary: the Sprint 1 Drive trial. Removed once the setup screen exists. */
    const val TRIAL = "trial"
}

@Composable
fun rememberAppSpec(): ShellSpec {
    val appName = stringResource(R.string.app_name)
    val section = stringResource(R.string.nav_section_library)
    val search = stringResource(R.string.nav_search)
    val library = stringResource(R.string.nav_library)
    val shelves = stringResource(R.string.nav_shelves)
    val settings = stringResource(R.string.nav_settings)
    val trial = stringResource(R.string.nav_trial)
    return remember(appName, section, search, library, shelves, settings, trial) {
        ShellSpec(
            appTitle = appName,
            sections = listOf(
                NavSection(
                    section,
                    listOf(
                        NavItem(Routes.SEARCH, search, DeskIcons.Search),
                        NavItem(Routes.LIBRARY, library, AppIcons.Book),
                        NavItem(Routes.SHELVES, shelves, AppIcons.Shelf),
                        NavItem(Routes.TRIAL, trial, DeskIcons.TvLab),
                    ),
                ),
            ),
            footer = NavItem(Routes.SETTINGS, settings, DeskIcons.Settings),
            bottomBar = listOf(Routes.SEARCH, Routes.LIBRARY, Routes.TRIAL, Routes.SETTINGS),
        )
    }
}

/**
 * The whole interface inside desktop-kit's shell: menu bar and sidebar on DeX, a rail on medium
 * windows, a bottom bar on phones. [onShortcut] runs a shortcut from [AppShortcuts.registry].
 */
@Composable
fun AppShell(
    prefs: DeskPreferences,
    widthClass: WidthClass,
    navController: NavHostController = rememberNavController(),
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
                description = stringResource(R.string.placeholder_library_text),
            )
            Routes.SHELVES -> MessageScreen(
                title = stringResource(R.string.placeholder_shelves_title),
                icon = AppIcons.Shelf,
                pageTitle = spec.item(route)?.label,
                description = stringResource(R.string.placeholder_shelves_text),
            )
            Routes.TRIAL -> TrialScreen(spec.item(route)?.label.orEmpty())
            Routes.SETTINGS -> SettingsScreen(prefs, spec.item(route)?.label.orEmpty())
        }
    }
}

@Composable
private fun SettingsScreen(prefs: DeskPreferences, title: String) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageHeader(title) }
        item { AppearanceSection(prefs) }
        item { InputSection(prefs) }
        item { ShortcutListSection(AppShortcuts.registry) }
    }
}
