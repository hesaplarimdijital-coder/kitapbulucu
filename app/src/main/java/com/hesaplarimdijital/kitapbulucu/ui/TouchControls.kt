package com.hesaplarimdijital.kitapbulucu.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.hesaplarimdijital.kitapbulucu.R
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.shell.currentWidthClass
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskTheme

/*
 * Shared pieces of the phone layout (compact windows): the floating action button, a full-width
 * segmented choice, bottom sheets and the multi-selection state of a list. The DeX and tablet
 * layouts do not use them.
 */

/**
 * True in the phone layout: the shell lays out for a compact window. Outside the shell and the
 * kit's root (a screenshot test's dialogs) the window width decides the same way.
 */
@Composable
fun isCompactLayout(): Boolean =
    currentWidthClass() == WidthClass.Compact || LocalConfiguration.current.screenWidthDp < WidthClass.Medium.minWidthDp

/** Icons the phone layout needs beyond the kit's and [AppIcons], in the same 16×16 line style. */
object TouchIcons {
    val Pencil: ImageVector by lazy {
        DeskIcons.build("Pencil", DeskIcons.stroke("M10.5 3 13 5.5 6 12.5H3.5V10z"), DeskIcons.stroke("M9 4.5 11.5 7"))
    }

    /** Two pages, the front one outlined: copy to the clipboard. */
    val Copy: ImageVector by lazy {
        DeskIcons.build(
            "Copy",
            DeskIcons.stroke("M6.5,5.5H12A1,1 0 0 1 13,6.5V13A1,1 0 0 1 12,14H6.5A1,1 0 0 1 5.5,13V6.5A1,1 0 0 1 6.5,5.5Z"),
            DeskIcons.stroke("M3.5 10.5h-.2a.8.8 0 0 1-.8-.8V3.3a.8.8 0 0 1 .8-.8h6.4a.8.8 0 0 1 .8.8v.2"),
        )
    }

    /** Two arrows passing each other: to the other list. */
    val Swap: ImageVector by lazy {
        DeskIcons.build("Swap", DeskIcons.stroke("M2.5 5.5h10l-2.5-2.5"), DeskIcons.stroke("M13.5 10.5h-10l2.5 2.5"))
    }

    /** A box with a tick: select every row. */
    val SelectAll: ImageVector by lazy {
        DeskIcons.build(
            "SelectAll",
            DeskIcons.stroke("M3.5,2.5H12.5A1,1 0 0 1 13.5,3.5V12.5A1,1 0 0 1 12.5,13.5H3.5A1,1 0 0 1 2.5,12.5V3.5A1,1 0 0 1 3.5,2.5Z"),
            DeskIcons.stroke("m5 8.2 2 2 4-4.4"),
        )
    }
}

// ---------------------------------------------------------------------------------- selection

/**
 * The rows ticked on a phone list. A long press starts it, a tap then ticks or unticks a row,
 * and unticking the last row ends it: the list is in selection mode while [active].
 */
data class TouchSelection(val keys: Set<Any> = emptySet()) {
    val active: Boolean get() = keys.isNotEmpty()
    val size: Int get() = keys.size

    operator fun contains(key: Any): Boolean = key in keys

    fun toggle(key: Any): TouchSelection = TouchSelection(if (key in keys) keys - key else keys + key)

    /** Every row of the list; [all] is the whole list as shown. */
    fun selectAll(all: Collection<Any>): TouchSelection = TouchSelection(all.toSet())

    fun hasAll(all: Collection<Any>): Boolean = all.isNotEmpty() && keys.size >= all.size && keys.containsAll(all)

    /** Drops rows that are no longer in the list. */
    fun retain(all: Collection<Any>): TouchSelection {
        if (keys.isEmpty()) return this
        val present = all.toHashSet()
        return if (keys.all { it in present }) this else TouchSelection(keys.filterTo(HashSet()) { it in present })
    }

    companion object {
        val None = TouchSelection()
    }
}

// ---------------------------------------------------------------------------------- FAB

/**
 * The screen's one primary action, bottom right: an extended floating button with a label that
 * folds into the icon while [expanded] is false (the list has been scrolled).
 */
@Composable
fun AppFab(label: String, icon: ImageVector, onClick: () -> Unit, expanded: Boolean, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(16.dp)
    val background by animateColorAsState(
        when {
            state.pressed -> lerp(colors.accent, colors.onAccent, 0.24f)
            state.hovered -> lerp(colors.accent, colors.onAccent, 0.12f)
            else -> colors.accent
        },
        label = "fab",
    )
    Row(
        modifier
            .shadow(6.dp, shape)
            .background(background, shape)
            .focusRing(state, shape)
            .deskClickable(interaction, onClick, role = Role.Button, onClickLabel = label)
            .semantics { if (!expanded) contentDescription = label }
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(24.dp))
        AnimatedVisibility(
            expanded,
            enter = fadeIn(tween(180)) + expandHorizontally(tween(200)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(160)),
        ) {
            Row {
                Spacer(Modifier.width(12.dp))
                Text(label, style = DeskTheme.type.bodyStrong, color = colors.onAccent, maxLines = 1)
            }
        }
    }
}

/**
 * A FAB that appears with a small scale and fade, and leaves the same way. It also steps aside
 * while the on-screen keyboard is open instead of riding on top of it.
 */
@Composable
fun AnimatedFab(visible: Boolean, label: String, icon: ImageVector, onClick: () -> Unit, expanded: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible && !isKeyboardOpen(),
        modifier = modifier,
        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f),
    ) {
        AppFab(label, icon, onClick, expanded)
    }
}

// ---------------------------------------------------------------------------------- segmented

/** The kit's segmented choice stretched over the full width, each option an equal part. */
@Composable
fun <T> WideSegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val outer = RoundedCornerShape(dimens.radiusControl + 2.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface, outer)
            .border(1.dp, colors.border, outer)
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val interaction = remember { MutableInteractionSource() }
            val state = interaction.collectState()
            val isSelected = value == selected
            val shape = RoundedCornerShape(dimens.radiusControl)
            val background by animateColorAsState(stateBackground(state, isSelected), tween(180), label = "segment")
            val text by animateColorAsState(if (isSelected) colors.onSelection else colors.textSecondary, tween(180), label = "segmentText")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .focusRing(state, shape)
                    .background(background, shape)
                    .selectable(selected = isSelected, interactionSource = interaction, indication = null, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = dimens.gapM),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = DeskTheme.type.bodyStrong, color = text, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------------- sheets

/** Closes a sheet with its exit animation, then runs the given step (an action of the sheet). */
typealias SheetClose = (after: () -> Unit) -> Unit

/**
 * Where [AppSheet]s are drawn: over [content], inside the app's own window. The app's root pads
 * that window for the system bars and the on-screen keyboard (`WindowInsets.safeDrawing`), so a
 * sheet drawn here always ends above the navigation bar and moves up with the keyboard. A dialog
 * window of its own did neither on a real phone (Samsung, gesture or button navigation).
 */
@Composable
fun SheetHost(content: @Composable () -> Unit) {
    val host = remember { SheetHostState() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSheetHost provides host, content = content)
        SheetLayer(host)
    }
}

/** The open sheets, in a scope of their own: a sheet that changes does not recompose the app under it. */
@Composable
private fun SheetLayer(host: SheetHostState) {
    for (entry in host.sheets) {
        key(entry) {
            val sheet = entry.content
            sheet()
        }
    }
}

/** The sheets open in a [SheetHost], in the order they were opened. */
class SheetHostState {
    internal val sheets = mutableStateListOf<SheetEntry>()
}

internal class SheetEntry {
    var content: @Composable () -> Unit by mutableStateOf<@Composable () -> Unit>({})
}

/** The [SheetHost] around this composition, if any; without one a sheet opens a dialog window. */
val LocalSheetHost = staticCompositionLocalOf<SheetHostState?> { null }

/**
 * A modal bottom sheet in the kit's look: it slides up over a dimmed screen, a tap above it, Back
 * or Esc closes it, and it never takes more than nine tenths of the height (the content scrolls).
 * [title] adds a heading with a close button. [content] gets a [SheetClose] so that an action
 * runs after the sheet has gone. Inside a [SheetHost] (the app shell) the sheet is drawn in the
 * app's window, above the navigation bar and the keyboard; elsewhere it opens a dialog window.
 * The content is composed where the sheet is drawn: a focus request for a field in it belongs
 * inside [content].
 */
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.(close: SheetClose) -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }
    var after by remember { mutableStateOf<(() -> Unit)?>(null) }
    val close: SheetClose = { step ->
        if (!closing) {
            closing = true
            after = step
            visible.targetState = false
        }
    }
    if (closing && visible.isIdle && !visible.currentState) {
        LaunchedEffect(Unit) {
            onDismiss()
            after?.invoke()
        }
    }
    val host = LocalSheetHost.current
    if (host != null) {
        // drawn by the host, with this place's composition locals (theme, phone layout, …)
        val locals = currentCompositionLocalContext
        val entry = remember { SheetEntry() }
        entry.content = {
            CompositionLocalProvider(locals) {
                BackHandler { close {} }
                SheetFrame(visible, close, title, modifier, inWindow = true, content = content)
            }
        }
        DisposableEffect(host, entry) {
            host.sheets += entry
            onDispose { host.sheets -= entry }
        }
    } else {
        Dialog(
            onDismissRequest = { close {} },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                // the keyboard shrinks the window instead of panning it, so the sheet stays above it
                @Suppress("DEPRECATION")
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            SheetFrame(visible, close, title, modifier, inWindow = false, content = content)
        }
    }
}

@Composable
private fun SheetFrame(
    visible: MutableTransitionState<Boolean>,
    close: SheetClose,
    title: String?,
    modifier: Modifier,
    inWindow: Boolean,
    content: @Composable ColumnScope.(close: SheetClose) -> Unit,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    // In the app's window the root has already padded for the system bars and the keyboard; the
    // dimmed area and the sheet's surface are drawn on into that padding (nothing clips them), so
    // the status bar is dimmed too and the sheet seems to run on under the navigation bar while
    // its rows stay above it. A dialog window of its own reaches under the bars and pads itself.
    val safe = WindowInsets.safeDrawing
    val frameInsets = if (inWindow) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)
    val sheetInsets = if (inWindow) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
    // one transition for the whole frame: the dimmed area fades, the sheet also slides
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(frameInsets)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        close {}
                        true
                    } else {
                        false
                    }
                },
        ) {
            val maxSheet = maxHeight * 0.9f
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (inWindow) {
                            Modifier.drawBehind {
                                val left = safe.getLeft(this, layoutDirection).toFloat()
                                val top = safe.getTop(this).toFloat()
                                val right = safe.getRight(this, layoutDirection).toFloat()
                                val bottom = safe.getBottom(this).toFloat()
                                drawRect(colors.scrim, topLeft = Offset(-left, -top), size = Size(size.width + left + right, size.height + top + bottom))
                            }
                        } else {
                            Modifier
                        },
                    )
                    .pointerInput(Unit) { detectTapGestures { close {} } },
            )
            val shape = RoundedCornerShape(topStart = dimens.radiusCard + 8.dp, topEnd = dimens.radiusCard + 8.dp)
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(tween(220)) { it },
                        exit = slideOutVertically(tween(150)) { it },
                    )
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxSheet)
                    .then(
                        if (inWindow) {
                            // the surface goes on under the navigation bar (or the keyboard), over the bottom border line
                            Modifier.drawWithContent {
                                drawContent()
                                val below = safe.getBottom(this).toFloat()
                                if (below > 0f) drawRect(colors.surfaceRaised, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, below + 1.dp.toPx()))
                            }
                        } else {
                            Modifier
                        },
                    )
                    .background(colors.surfaceRaised, shape)
                    .border(1.dp, colors.border, shape)
                    // taps on the sheet itself do not reach the dimmed area behind it
                    .pointerInput(Unit) { detectTapGestures { } }
                    .then(sheetInsets)
                    .padding(bottom = dimens.gapS),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = dimens.gapS, bottom = dimens.gapXs)
                        .size(width = 36.dp, height = 4.dp)
                        .background(colors.borderStrong, CircleShape),
                )
                if (title != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = dimens.pagePadding, end = dimens.gapXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = DeskTheme.type.headline,
                            color = colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        DeskIconButton(DeskIcons.Close, stringResource(R.string.touchlist_close), { close {} })
                    }
                }
                content(close)
            }
        }
    }
}

/** One action of a sheet: icon and label over the full width, 56 dp high; [destructive] in the danger colour. */
@Composable
fun SheetAction(label: String, icon: ImageVector, onClick: () -> Unit, destructive: Boolean = false, enabled: Boolean = true) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val tint = when {
        !enabled -> colors.textTertiary
        destructive -> colors.danger
        else -> colors.text
    }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(stateBackground(state))
            .focusRing(state, RoundedCornerShape(0.dp))
            .deskClickable(interaction, onClick, enabled = enabled, role = Role.Button)
            .padding(horizontal = dimens.pagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (destructive || !enabled) tint else colors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(dimens.gapL))
        Text(label, style = DeskTheme.type.body, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One option of a choosing sheet (a sort order): the label, and a tick on the chosen one. */
@Composable
fun SheetChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(stateBackground(state, selected))
            .focusRing(state, RoundedCornerShape(0.dp))
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = dimens.pagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DeskTheme.type.body, color = if (selected) colors.onSelection else colors.text, maxLines = 1, modifier = Modifier.weight(1f))
        if (selected) Icon(DeskIcons.Check, contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(22.dp))
    }
}

/** A text-only button in the accent colour, at least 48 dp high: a secondary action under a field. */
@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .deskClickable(interaction, onClick, role = Role.Button)
            .padding(horizontal = dimens.gapS),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = DeskTheme.type.bodyStrong, color = colors.accentText)
    }
}

/** A check mark in a circle at the start of a row in selection mode; it fills in when [checked]. */
@Composable
fun SelectionMark(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    val fill by animateColorAsState(if (checked) colors.accent else Color.Transparent, tween(160), label = "markFill")
    val edge by animateColorAsState(if (checked) colors.accent else colors.borderStrong, tween(160), label = "markEdge")
    Box(
        modifier.size(24.dp).background(fill, CircleShape).border(2.dp, edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(checked, enter = fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.5f), exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.5f)) {
            Icon(DeskIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}
