package com.hesaplarimdijital.kitapbulucu.ui

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import desktopkit.components.DeskIcons
import desktopkit.components.GlassBottomSheet
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.pressScale
import desktopkit.components.animatedStateBackground
import desktopkit.components.stateBackground
import desktopkit.shell.rememberShellContentPadding
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalMotionTier

/*
 * Shared pieces of the phone layout (compact windows): a full-width segmented choice, the kit's
 * bottom sheet for sheets shown while composed, and the multi-selection state of a list. The
 * floating button is the kit's ShellPrimaryAction. The DeX and tablet layouts do not use them.
 */

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

// ---------------------------------------------------------------------------------- segmented

/**
 * The kit's segmented choice stretched over the full width, each option an equal part (the kit's
 * [desktopkit.components.SegmentedChoice] keeps its options at their own width, still in 2.8.0).
 */
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
            // the kit's colour: instant under a mouse, eased on touch
            val background by animatedStateBackground(state, isSelected)
            val text by animateColorAsState(if (isSelected) colors.onSelection else colors.textSecondary, DeskMotion.fade(LocalMotionTier.current, DeskMotion.color()), label = "segmentText")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .pressScale(state.pressed)
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
 * The kit's [GlassBottomSheet] for a sheet that is shown while it is in composition: it slides in
 * when composed; Esc, Back, a tap above it or a pull down close it. [content] gets a [SheetClose],
 * so an action of the sheet runs once the sheet has gone; [onDismiss] comes at the same moment.
 * Inside the kit's shell the sheet is drawn in the shell's window (above the navigation bar and
 * the keyboard). The content is composed where the sheet is drawn: a focus request for a field
 * in it belongs inside [content].
 */
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.(close: SheetClose) -> Unit,
) {
    // false for the first frame, so the kit's sheet slides in instead of appearing
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val closing = remember { SheetClosing() }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val close: SheetClose = { step ->
        if (!closing.requested) {
            closing.requested = true
            closing.after = step
            visible = false
        }
    }
    GlassBottomSheet(visible = visible, onDismiss = { close {} }, modifier = modifier, title = title) {
        // the kit stops composing the sheet's content once its exit has finished: that is when the sheet is gone
        DisposableEffect(Unit) {
            onDispose {
                if (closing.requested && !closing.done) {
                    closing.done = true
                    latestDismiss()
                    closing.after()
                }
            }
        }
        content(close)
    }
}

/** Whether a sheet has been asked to close, and what runs once it has gone. */
private class SheetClosing {
    var requested = false
    var done = false
    var after: () -> Unit = {}
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

