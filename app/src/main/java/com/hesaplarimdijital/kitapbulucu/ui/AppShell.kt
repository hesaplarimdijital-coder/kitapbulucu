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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.hesaplarimdijital.kitapbulucu.store.StoreNotice
import com.hesaplarimdijital.kitapbulucu.store.WriteFailure
import com.hesaplarimdijital.kitapbulucu.ui.add.AddBookScreen
import com.hesaplarimdijital.kitapbulucu.ui.add.EditTarget
import com.hesaplarimdijital.kitapbulucu.ui.importing.ImportScreen
import com.hesaplarimdijital.kitapbulucu.ui.library.LibraryScreen
import com.hesaplarimdijital.kitapbulucu.ui.shelves.ShelfTarget
import com.hesaplarimdijital.kitapbulucu.ui.shelves.ShelvesScreen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.hesaplarimdijital.kitapbulucu.ui.search.SearchScreen
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.components.SegmentedChoice
import desktopkit.input.CommonShortcuts
import desktopkit.screens.MessageScreen
import desktopkit.screens.PageHeader
import desktopkit.settings.AppearanceSection
import desktopkit.settings.DeskPreferences
import desktopkit.settings.InputSection
import desktopkit.settings.SettingsSection
import desktopkit.settings.ShortcutListSection
import desktopkit.shell.CompactTopBar
import desktopkit.shell.DesktopShell
import desktopkit.shell.NavItem
import desktopkit.shell.NavSection
import desktopkit.shell.ShellSpec
import desktopkit.shell.TopBarBackIcon
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskTheme

/** Top-level screens. The route names never reach the user. */
object Routes {
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SHELVES = "shelves"
    const val SETTINGS = "settings"
}

/** The form shown over the current screen: a new book (with a title to start from) or an edit. */
sealed class FormRequest {
    data class Add(val title: String = "") : FormRequest()
    data class Edit(val owner: Owner, val book: com.hesaplarimdijital.kitapbulucu.model.Book) : FormRequest()
    /** "Excel birleştir" (Ctrl+Shift+O, or from Settings). */
    object Import : FormRequest()
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
    /** For screenshots: what the search field starts with. */
    initialSearch: String = "",
    /** Changes on every Ctrl+K: the search field takes the cursor again. */
    searchFocus: Int = 0,
    /** For screenshots: the first result starts opened. */
    initialSearchOpen: Boolean = false,
    /** For screenshots: this result row starts opened. */
    initialSearchOpenAt: Int = -1,
    /** Changes on every Ctrl+N: the add form takes the cursor again. */
    addFocus: Int = 0,
    /** Changes on every Ctrl+Shift+O: the merge screen opens. */
    mergeFocus: Int = 0,
    /** For screenshots: the form shown from the start. */
    initialForm: FormRequest? = null,
    /** For screenshots: the merge screen's file and target. */
    importPreview: Pair<com.hesaplarimdijital.kitapbulucu.ui.importing.PickedFile, Owner>? = null,
    /** For screenshots: the shelf the Shelves screen opens on, and the add-books search text. */
    initialShelf: ShelfTarget? = null,
    initialShelfQuery: String? = null,
    /** For screenshots: the "Rafa koy…" picker open for these books. */
    initialPlace: Pair<Owner, List<com.hesaplarimdijital.kitapbulucu.model.Book>>? = null,
) {
    val spec = rememberAppSpec()
    val resources = LocalContext.current.resources
    // add or edit form over the current screen; it closes when another screen is chosen
    var form by remember { mutableStateOf(initialForm) }
    var shelfTarget by remember { mutableStateOf(initialShelf) }
    val actions = store?.let { rememberBookActions(it, settings) { owner, book -> form = FormRequest.Edit(owner, book) } }
    LaunchedEffect(actions, initialPlace) { if (actions != null && initialPlace != null) actions.placeOnShelf(initialPlace.first, initialPlace.second) }
    LaunchedEffect(addFocus) { if (addFocus > 0) form = FormRequest.Add() }
    LaunchedEffect(mergeFocus) { if (mergeFocus > 0) form = FormRequest.Import }
    BackHandler(enabled = form != null) { form = null }
    val phone = widthClass == WidthClass.Compact
    // phone sheets are drawn in the app's window, over the whole shell (SheetHost)
    PhoneProvider(phone) {
        SheetHost {
            DesktopShell(
                spec = spec,
                widthClass = widthClass,
                navController = navController,
                sidebarCollapsed = prefs.sidebarCollapsed,
                onSidebarCollapsedChange = prefs::updateSidebarCollapsed,
                menus = AppShortcuts.registry.menus(resources, perform = onShortcut),
                onSearch = { onShortcut(CommonShortcuts.SEARCH) },
            ) { route, navigator ->
                DisposableEffect(route) { onDispose { form = null } }
                Column(Modifier.fillMaxSize()) {
                    if (store != null && picker != null) LibraryNotices(store, picker, onMap)
                    Box(Modifier.weight(1f)) {
                        // the add, edit and merge forms open over the screen: slide up and fade on the phone, fade on DeX
                        AnimatedContent(
                            targetState = form,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = { overlayTransition(phone) },
                            contentKey = { it?.overlayKey() ?: 0 },
                            label = "form overlay",
                        ) { currentForm ->
                            if (store != null && currentForm == FormRequest.Import) {
                                OverlayPage(phone, stringResource(R.string.import_title), onClose = { form = null }) {
                                    ImportScreen(
                                        store,
                                        settings,
                                        onClose = { form = null },
                                        initialFile = importPreview?.first,
                                        initialTarget = importPreview?.second,
                                    )
                                }
                            } else if (store != null && currentForm != null) {
                                val editing = currentForm is FormRequest.Edit
                                OverlayPage(phone, stringResource(if (editing) R.string.edit_title else R.string.add_title), onClose = { form = null }) {
                                    AddBookScreen(
                                        store,
                                        settings,
                                        initialTitle = (currentForm as? FormRequest.Add)?.title.orEmpty(),
                                        focusKey = addFocus,
                                        editing = (currentForm as? FormRequest.Edit)?.let { EditTarget(it.owner, it.book) },
                                        actions = actions,
                                        onEdited = { form = null },
                                    )
                                }
                            } else {
                                when (route) {
                                    Routes.SEARCH -> if (store != null) {
                                        // the search field is on the screen; the top bar's search button would only repeat it
                                        if (phone) CompactTopBar(showSearch = false)
                                        SearchScreen(
                                            store,
                                            initialQuery = initialSearch,
                                            focusKey = searchFocus,
                                            openFirst = initialSearchOpen,
                                            openAt = initialSearchOpenAt,
                                            actions = actions,
                                            onAdd = { title -> form = FormRequest.Add(title) },
                                        )
                                    } else {
                                        MessageScreen(
                                            title = stringResource(R.string.placeholder_search_title),
                                            icon = DeskIcons.Search,
                                            pageTitle = spec.item(route)?.label,
                                            description = stringResource(R.string.placeholder_search_text),
                                        )
                                    }
                                    Routes.LIBRARY -> if (store != null && actions != null) {
                                        LibraryScreen(
                                            store, settings, actions, spec.item(route)?.label.orEmpty(),
                                            onAdd = { form = FormRequest.Add() },
                                            onShelf = { owner, shelf ->
                                                shelfTarget = ShelfTarget(owner, shelf)
                                                navigator.navigate(Routes.SHELVES)
                                            },
                                        )
                                    } else {
                                        MessageScreen(
                                            title = stringResource(R.string.placeholder_library_title),
                                            icon = AppIcons.Book,
                                            pageTitle = spec.item(route)?.label,
                                            description = stringResource(R.string.placeholder_library_text),
                                        )
                                    }
                                    Routes.SHELVES -> if (store != null && actions != null) {
                                        ShelvesScreen(store, settings, actions, spec.item(route)?.label.orEmpty(), shelfTarget, initialAddQuery = initialShelfQuery)
                                    } else {
                                        MessageScreen(
                                            title = stringResource(R.string.placeholder_shelves_title),
                                            icon = AppIcons.Shelf,
                                            pageTitle = spec.item(route)?.label,
                                            description = stringResource(R.string.placeholder_shelves_text),
                                        )
                                    }
                                    Routes.SETTINGS -> SettingsScreen(prefs, spec.item(route)?.label.orEmpty(), store, settings, picker, onMerge = { form = FormRequest.Import })
                                }
                            }
                        }
                    }
                    if (store != null) UndoBar(store)
                }
            }
            actions?.Dialogs()
        }
    }
}

/** Which kind of form a request is: forms of one kind swap in place, a different kind fades through. */
private fun FormRequest.overlayKey(): Int = when (this) {
    is FormRequest.Add -> 1
    is FormRequest.Edit -> 2
    FormRequest.Import -> 3
}

/**
 * Opening a form: the new page slides up a little and fades in over the screen (phone, about
 * 220 ms) or just fades in (DeX, 150 ms). Closing: the form slides down and fades out over the
 * screen that is already there.
 */
private fun AnimatedContentTransitionScope<FormRequest?>.overlayTransition(phone: Boolean): ContentTransform {
    val opening = initialState == null && targetState != null
    val closing = initialState != null && targetState == null
    return when {
        opening -> {
            val enter = if (phone) slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(220)) else fadeIn(tween(150))
            (enter togetherWith ExitTransition.None).apply { targetContentZIndex = 1f }
        }
        closing -> {
            val exit = if (phone) slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 10 } + fadeOut(tween(180)) else fadeOut(tween(150))
            (EnterTransition.None togetherWith exit).apply { targetContentZIndex = 0f }
        }
        else -> fadeIn(tween(150)) togetherWith fadeOut(tween(150))
    }
}

/**
 * A form page. On the phone it gets its own top bar (a close button and the form's title; the
 * system back gesture closes it too) and an opaque background so the screen below never shows through.
 */
@Composable
private fun OverlayPage(phone: Boolean, title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
        if (phone) CompactTopBar(title = title, onBack = onClose, backIcon = TopBarBackIcon.Close, showSearch = false)
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * "“X” silindi · Geri al" for a few seconds after every change; Ctrl+Z works longer. It slides in
 * from the bottom and out again; on the phone it is a rounded snackbar just above the bottom bar.
 */
@Composable
private fun UndoBar(store: LibraryStore) {
    val step by store.lastStep.collectAsState()
    val phone = isPhone()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    // the last step stays on screen while the bar slides out
    val shown = remember { mutableStateOf(step) }
    if (step != null) shown.value = step
    LaunchedEffect(step) {
        if (step != null) {
            kotlinx.coroutines.delay(UNDO_BAR_MILLIS)
            store.dismissLastStep()
        }
    }
    AnimatedVisibility(
        visible = step != null,
        enter = expandVertically(tween(200), expandFrom = Alignment.Bottom) + slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
        exit = shrinkVertically(tween(150), shrinkTowards = Alignment.Bottom) + slideOutVertically(tween(150)) { it } + fadeOut(tween(150)),
    ) {
        val current = shown.value ?: return@AnimatedVisibility
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(dimens.radiusControl)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (phone) dimens.gapM else dimens.pagePadding, vertical = dimens.gapS)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .padding(start = dimens.gapM, end = if (phone) dimens.gapXs else dimens.gapM, top = if (phone) 0.dp else dimens.gapS, bottom = if (phone) 0.dp else dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.label, style = DeskTheme.type.body, color = colors.text, modifier = Modifier.weight(1f).padding(vertical = if (phone) dimens.gapS else 0.dp), maxLines = 2)
            AdaptiveButton(stringResource(R.string.action_undo), { store.undo() }, icon = DeskIcons.Previous, phoneAccent = true)
        }
    }
}

private const val UNDO_BAR_MILLIS = 8_000L

/** A list that could not be read and stray files next to the lists, above every screen. */
@Composable
private fun LibraryNotices(store: LibraryStore, picker: FilePicker, onMap: (Owner) -> Unit) {
    val states by store.states.collectAsState()
    val warning by store.warning.collectAsState()
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val notices by store.notices.collectAsState()
    val failed = Owner.entries.map { states.getValue(it) }.filter { it.status == LoadStatus.FAILED }
    val unwritten = Owner.entries.map { states.getValue(it) }.filter { it.writeFailure != null && it.pendingChanges > 0 }
    val stray = warning
    val any = failed.isNotEmpty() || unwritten.isNotEmpty() || stray != null || notices.isNotEmpty()
    // a notice grows in when it appears and the space closes smoothly when it goes
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(tween(200))
            .then(if (any) Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapS) else Modifier),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        for (state in unwritten) {
            key("write-" + state.owner) {
                Appear {
                    NoticeCard(
                        title = stringResource(R.string.write_failed_title, LibraryTexts.listName(resources, state.owner), state.pendingChanges),
                        text = LibraryTexts.writeFailure(resources, state.writeFailure!!) + " " + stringResource(R.string.write_retry_note),
                        tone = colors.danger,
                    ) {
                        AdaptiveButton(stringResource(R.string.action_retry), store::refresh, primary = true)
                        if (state.writeFailure == WriteFailure.NOT_XLSX || state.writeFailure == WriteFailure.NO_PERMISSION) {
                            AdaptiveButton(stringResource(R.string.action_choose_other_file), { picker.pick(state.owner) })
                        }
                    }
                }
            }
        }
        for (notice in notices) {
            key(notice) {
                Appear {
                    when (notice) {
                        StoreNotice.FirstWrite -> NoticeCard(
                            title = stringResource(R.string.notice_first_write_title),
                            text = stringResource(R.string.notice_first_write_text),
                        ) { AdaptiveButton(stringResource(R.string.action_understood), { store.dismiss(notice) }, phoneAccent = true) }
                        is StoreNotice.OtherSheetsKept -> NoticeCard(
                            title = stringResource(R.string.notice_other_sheets_title, LibraryTexts.listName(resources, notice.owner)),
                            text = stringResource(R.string.notice_other_sheets_text, notice.names.joinToString(", ")),
                            tone = colors.warning,
                        ) { AdaptiveButton(stringResource(R.string.action_understood), { store.dismiss(notice) }, phoneAccent = true) }
                        is StoreNotice.ChangesLanded -> NoticeCard(
                            title = stringResource(R.string.notice_changes_landed_title),
                            text = notice.notes.joinToString("\n") { LibraryTexts.landed(resources, it) },
                            tone = colors.warning,
                        ) { AdaptiveButton(stringResource(R.string.action_understood), { store.dismiss(notice) }, phoneAccent = true) }
                    }
                }
            }
        }
        for (state in failed) {
            key("failed-" + state.owner) {
                Appear {
                    NoticeCard(
                        title = LibraryTexts.listName(resources, state.owner),
                        text = LibraryTexts.failure(resources, state.failure ?: LoadFailure.IO),
                        tone = colors.danger,
                    ) {
                        if (state.failure == LoadFailure.NO_HEADER) {
                            AdaptiveButton(stringResource(R.string.setup_map_columns), { onMap(state.owner) }, primary = true)
                        } else {
                            AdaptiveButton(stringResource(R.string.action_retry), store::refresh, phoneAccent = true)
                        }
                        AdaptiveButton(stringResource(R.string.action_choose_other_file), { picker.pick(state.owner) })
                    }
                }
            }
        }
        if (stray != null) {
            Appear {
                NoticeCard(
                    title = stringResource(R.string.stray_files_title),
                    text = stringResource(R.string.stray_files_text, stray.names.joinToString(", ")),
                    tone = colors.warning,
                ) {
                    AdaptiveButton(stringResource(R.string.action_understood), store::dismissWarning, phoneAccent = true)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(prefs: DeskPreferences, title: String, store: LibraryStore?, settings: AppSettings?, picker: FilePicker?, onMerge: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageHeader(title) }
        if (store != null && settings != null && picker != null) item { LibrarySection(store, settings, picker) }
        if (store != null) item { DataSection(store, onMerge) }
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
        SettingLine(stringResource(R.string.setup_who_title), subtitle = stringResource(R.string.setup_who_text)) {
            SegmentedChoice(
                options = listOf<Pair<Owner?, String>>(Owner.DAD to stringResource(R.string.setup_who_dad), Owner.ME to stringResource(R.string.setup_who_me)),
                selected = settings.me,
                onSelect = { owner -> if (owner != null) settings.updateMe(owner) },
            )
        }
        for (owner in Owner.entries) {
            val state = states.getValue(owner)
            val subtitle = listOfNotNull(state.displayName, LibraryTexts.status(resources, state)).joinToString(" · ")
            SettingLine(LibraryTexts.listName(resources, owner), subtitle = subtitle) {
                DeskButton(
                    stringResource(if (state.isLinked) R.string.setup_change else R.string.setup_pick),
                    { picker.pick(owner) },
                    icon = DeskIcons.Folder,
                )
            }
        }
        val folderText = stringResource(R.string.settings_folder_text) +
            if (store.treeUri == null) " " + stringResource(R.string.settings_folder_none) + "." else ""
        SettingLine(stringResource(R.string.settings_folder), subtitle = folderText) {
            DeskButton(stringResource(R.string.settings_folder_pick), picker::pickFolder, icon = DeskIcons.Folder)
        }
        SettingLine(stringResource(R.string.setup_title)) {
            DeskButton(stringResource(R.string.settings_reopen_setup), { settings.updateSetupDone(false) })
        }
    }
}
