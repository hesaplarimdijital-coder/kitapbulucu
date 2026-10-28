package com.hesaplarimdijital.kitapbulucu.ui

import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import desktopkit.theme.DeskMotion
import desktopkit.theme.InputMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
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
import desktopkit.components.pressScale
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
        DeskMotion.color(),
        label = "fab",
    )
    Row(
        modifier
            .pressScale(state.pressed)
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
            enter = fadeIn(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.StandardDecelerate)) +
                expandHorizontally(tween(DeskMotion.MEDIUM_MS + 50, easing = DeskMotion.EmphasizedDecelerate)),
            exit = fadeOut(tween(DeskMotion.SHORT_MS, easing = DeskMotion.StandardAccelerate)) +
                shrinkHorizontally(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.EmphasizedAccelerate)),
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
        enter = fadeIn(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.StandardDecelerate)) +
            scaleIn(tween(DeskMotion.MEDIUM_MS + 50, easing = DeskMotion.EmphasizedDecelerate), initialScale = 0.8f),
        exit = fadeOut(tween(DeskMotion.SHORT_MS + 20, easing = DeskMotion.StandardAccelerate)) +
            scaleOut(tween(DeskMotion.SHORT_MS + 20, easing = DeskMotion.EmphasizedAccelerate), targetScale = 0.8f),
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
            val background by animateColorAsState(stateBackground(state, isSelected), DeskMotion.color(), label = "segment")
            val text by animateColorAsState(if (isSelected) colors.onSelection else colors.textSecondary, DeskMotion.color(), label = "segmentText")
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
 * Where a sheet is and where it is going, in pixels: [offset] is how far it is pushed down from its
 * open place, 0 when open and [height] when gone. Entering, closing, a finger dragging it and the
 * Back gesture all move this one value, so they can take over from each other at any moment
 * (a drag catches a sheet that is still sliding in, a let-go sheet springs back from where it is).
 */
@Stable
internal class SheetMotion(private val scope: CoroutineScope, private val flingPx: Float) {
    /** The sheet's height; 0 until it has been measured (it is not drawn before). */
    var height by mutableFloatStateOf(0f)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    /** Set once [close] has started; nothing moves the sheet back after that. */
    var closing by mutableStateOf(false)
        private set

    /** Runs when the sheet has gone off the screen. */
    var onClosed: () -> Unit = {}

    private var job: Job? = null

    /** 1 while open, 0 when gone: the dimmed area behind follows it. */
    val shown: Float get() = if (height <= 0f) 0f else (1f - offset / height).coerceIn(0f, 1f)

    /** The first measure starts the sheet just below the window and slides it in. */
    fun measured(newHeight: Float) {
        if (newHeight <= 0f) return
        val first = height <= 0f
        height = newHeight
        if (first && !closing) {
            offset = newHeight
            animateTo(0f, tween(SheetMotionSpec.ENTER_MS, easing = DeskMotion.EmphasizedDecelerate))
        } else if (offset > newHeight) {
            offset = newHeight
        }
    }

    /** Moves the sheet with a finger by [delta] (down is positive); returns what it took. */
    fun dragBy(delta: Float): Float {
        if (closing || height <= 0f) return 0f
        job?.cancel()
        val before = offset
        offset = (offset + delta).coerceIn(0f, height)
        return offset - before
    }

    /**
     * The finger let go: a downward fling or a sheet pulled down past a third of its height
     * closes, anything else springs back open.
     */
    fun release(velocity: Float) {
        if (closing || height <= 0f) return
        val dismiss = velocity > flingPx || (offset > height * SheetMotionSpec.CLOSE_FRACTION && velocity > -flingPx)
        if (dismiss) close(velocity) else if (offset > 0f) animateTo(0f, DeskMotion.spatial(), velocity)
    }

    /** Back gesture in progress (Android 14+): the sheet follows the finger a short way down. */
    fun backProgress(progress: Float) {
        if (closing || height <= 0f) return
        job?.cancel()
        offset = height * SheetMotionSpec.BACK_FRACTION * progress
    }

    /** The Back gesture was cancelled: back to open. */
    fun backCancelled() {
        if (!closing && offset > 0f) animateTo(0f, DeskMotion.spatial())
    }

    /** Slides the sheet out (faster from a fling or when it is already half way), then [onClosed]. */
    fun close(velocity: Float = 0f) {
        if (closing) return
        closing = true
        if (height <= 0f) {
            job?.cancel()
            onClosed()
            return
        }
        val left = ((height - offset) / height).coerceIn(0f, 1f)
        val millis = (SheetMotionSpec.EXIT_MS * left).toInt().coerceAtLeast(DeskMotion.SHORT_MS)
        val easing = if (velocity > 0f) DeskMotion.StandardDecelerate else DeskMotion.EmphasizedAccelerate
        animateTo(height, tween(millis, easing = easing), then = { onClosed() })
    }

    private fun animateTo(target: Float, spec: AnimationSpec<Float>, velocity: Float = 0f, then: () -> Unit = {}) {
        job?.cancel()
        job = scope.launch {
            animate(offset, target, velocity, spec) { value, _ -> offset = value }
            then()
        }
    }
}

/** Timing and thresholds of [AppSheet], from the kit's motion tokens (Material 3 bottom sheets). */
internal object SheetMotionSpec {
    const val ENTER_MS = DeskMotion.LONG_MS
    const val EXIT_MS = DeskMotion.MEDIUM_MS
    /** Pulled down further than this share of its height, the sheet closes when let go. */
    const val CLOSE_FRACTION = 1f / 3f
    /** How far the Back gesture moves the sheet before it is let go. */
    const val BACK_FRACTION = 0.12f
    /** A downward fling faster than this closes the sheet, whatever its position. */
    val FlingVelocity = 600.dp
}

/**
 * Hands the sheet the parts of a drag its scrolling content does not use: pulled down at the top
 * of the content the sheet follows the finger, pushed up again it opens first and the content
 * scrolls after. Only for fingers ([touch]): a mouse wheel never moves the sheet.
 */
private class SheetNestedScroll(private val motion: SheetMotion, private val touch: () -> Boolean) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f && source == NestedScrollSource.UserInput && motion.offset > 0f && touch()) {
            Offset(0f, motion.dragBy(available.y))
        } else {
            Offset.Zero
        }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y > 0f && source == NestedScrollSource.UserInput && touch()) Offset(0f, motion.dragBy(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (motion.offset <= 0f || motion.closing) return Velocity.Zero
        motion.release(available.y)
        return available
    }
}

/**
 * A modal bottom sheet in the kit's look: it slides up over a dimmed screen and never takes more
 * than nine tenths of the height (the content scrolls). It closes with a tap above it, Back (on
 * Android 14+ the sheet follows the Back gesture), Esc, or by dragging it down by its handle or
 * body: let go past a third of its height or flung down it closes, otherwise it springs back. In
 * scrolling content a downward drag scrolls to the top first, then pulls the sheet. The dimmed
 * area fades with the sheet. [title] adds a heading with a close button. [content] gets a
 * [SheetClose] so that an action runs after the sheet has gone. Inside a [SheetHost] (the app
 * shell) the sheet is drawn in the app's window, above the navigation bar and the keyboard;
 * elsewhere it opens a dialog window. The content is composed where the sheet is drawn: a focus
 * request for a field in it belongs inside [content].
 */
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.(close: SheetClose) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val flingPx = with(LocalDensity.current) { SheetMotionSpec.FlingVelocity.toPx() }
    val motion = remember { SheetMotion(scope, flingPx) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    var after by remember { mutableStateOf<(() -> Unit)?>(null) }
    motion.onClosed = {
        latestDismiss()
        after?.invoke()
    }
    val close: SheetClose = { step ->
        if (!motion.closing) {
            after = step
            motion.close()
        }
    }
    val host = LocalSheetHost.current
    if (host != null) {
        // drawn by the host, with this place's composition locals (theme, phone layout, …)
        val locals = currentCompositionLocalContext
        val entry = remember { SheetEntry() }
        entry.content = {
            CompositionLocalProvider(locals) {
                SheetBack(motion)
                SheetFrame(motion, close, title, modifier, inWindow = true, content = content)
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
            SheetFrame(motion, close, title, modifier, inWindow = false, content = content)
        }
    }
}

/** Back closes the sheet; on Android 14+ the sheet follows the predictive Back gesture first. */
@Composable
private fun SheetBack(motion: SheetMotion) {
    // stays on while the sheet slides out, so a second Back does not reach the screen below yet
    PredictiveBackHandler { events ->
        try {
            events.collect { event -> motion.backProgress(event.progress) }
            motion.close()
        } catch (e: CancellationException) {
            motion.backCancelled()
            throw e
        }
    }
}

@Composable
private fun SheetFrame(
    motion: SheetMotion,
    close: SheetClose,
    title: String?,
    modifier: Modifier,
    inWindow: Boolean,
    content: @Composable ColumnScope.(close: SheetClose) -> Unit,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch by rememberUpdatedState(DeskTheme.inputMode == InputMode.Touch)
    val nested = remember(motion) { SheetNestedScroll(motion) { touch } }
    val drag = rememberDraggableState { delta -> motion.dragBy(delta) }
    // In the app's window the root has already padded for the system bars and the keyboard; the
    // dimmed area and the sheet's surface are drawn on into that padding (nothing clips them), so
    // the status bar is dimmed too and the sheet seems to run on under the navigation bar while
    // its rows stay above it. A dialog window of its own reaches under the bars and pads itself.
    val safe = WindowInsets.safeDrawing
    val frameInsets = if (inWindow) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)
    val sheetInsets = if (inWindow) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
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
                        // the dimmed area fades in and out with the sheet, and while it is dragged
                        Modifier.drawBehind {
                            val shown = motion.shown
                            if (shown > 0f) {
                                val left = safe.getLeft(this, layoutDirection).toFloat()
                                val top = safe.getTop(this).toFloat()
                                val right = safe.getRight(this, layoutDirection).toFloat()
                                val bottom = safe.getBottom(this).toFloat()
                                drawRect(colors.scrim, topLeft = Offset(-left, -top), size = Size(size.width + left + right, size.height + top + bottom), alpha = shown)
                            }
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
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = maxSheet)
                .onSizeChanged { motion.measured(it.height.toFloat()) }
                // drawn only once measured, from just below the window; moved by the offset alone (no relayout)
                .graphicsLayer {
                    translationY = motion.offset
                    alpha = if (motion.height > 0f) 1f else 0f
                }
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
                // a drag anywhere on the sheet moves it; the scrolling content passes on what it does not use
                .draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    enabled = !motion.closing,
                    onDragStopped = { velocity -> motion.release(velocity) },
                )
                .nestedScroll(nested)
                .then(sheetInsets)
                .padding(bottom = dimens.gapS),
        ) {
            // the handle: a wider strip than it looks, so it is easy to catch
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.gapS, bottom = dimens.gapXs),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(colors.borderStrong, CircleShape))
            }
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
