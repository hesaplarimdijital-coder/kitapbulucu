package com.hesaplarimdijital.kitapbulucu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import desktopkit.components.DeskButton
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.SettingRow
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.theme.DeskTheme

/**
 * Phone layout helpers. The phone is a window narrower than 600 dp (the kit's Compact class);
 * everything else keeps the tablet and DeX layouts. [AppShell] provides [LocalPhone] from the
 * shell's width class; screens drawn outside the shell (setup, column mapping) and dialogs fall
 * back to the window width.
 */
val LocalPhone = compositionLocalOf<Boolean?> { null }

@Composable
fun isPhone(): Boolean = LocalPhone.current ?: (LocalConfiguration.current.screenWidthDp < 600)

@Composable
fun PhoneProvider(phone: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPhone provides phone, content = content)
}

/** The app's own line icons for the phone (same 16×16 grid as the kit's). */
object PhoneIcons {
    /** A pencil. */
    val Pencil: ImageVector by lazy {
        DeskIcons.build(
            "Pencil",
            DeskIcons.stroke("M2.5 13.5h2.8L13 5.8 10.2 3 2.5 10.7z"),
            DeskIcons.stroke("M8.6 4.6l2.8 2.8"),
        )
    }
}

/**
 * A flat button for phones: no border, at least 48 dp tall, the label in the accent colour when
 * [primary] and in the danger colour when [danger]. Rows of these replace rows of big buttons.
 */
@Composable
fun PhoneTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    contentPadding: Dp = DeskTheme.dimens.gapM,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val tint = (if (danger) colors.danger else if (primary) colors.accentText else colors.text).let { if (enabled) it else it.copy(alpha = 0.4f) }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .deskClickable(interaction, onClick, enabled = enabled, role = Role.Button)
            .padding(horizontal = contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(dimens.icon))
            Spacer(Modifier.width(dimens.gapXs + 2.dp))
        }
        Text(
            text,
            style = DeskTheme.type.label.copy(fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The kit's button on tablets and DeX, a flat [PhoneTextButton] on phones (for notices and list
 * headers). [phoneAccent] colours the phone button in the accent without making the tablet
 * button a filled one.
 */
@Composable
fun AdaptiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    phoneAccent: Boolean = primary,
) {
    if (isPhone()) {
        PhoneTextButton(text, onClick, modifier, icon = icon, primary = phoneAccent, enabled = enabled)
    } else {
        DeskButton(text, onClick, modifier, icon = icon, primary = primary, enabled = enabled)
    }
}

/**
 * Buttons in one line when they fit, one under the other (full width) when they do not; never
 * a wrapped grid. Children are given in stacking order (the main action first); with
 * [reverseInRow] a one-line row shows them the other way round, so the main action ends up at
 * the right edge as in Android dialogs.
 */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    endAligned: Boolean = true,
    reverseInRow: Boolean = endAligned,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val total = natural.sum() + gap * (measurables.size - 1).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else total
        if (total <= width) {
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints(minWidth = natural[index], maxWidth = natural[index]))
            }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                var x = if (endAligned) width - total else 0
                val ordered = if (reverseInRow) placeables.reversed() else placeables
                for (placeable in ordered) {
                    placeable.placeRelative(x, (height - placeable.height) / 2)
                    x += placeable.width + gap
                }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val height = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
            layout(width, height) {
                var y = 0
                for (placeable in placeables) {
                    placeable.placeRelative(0, y)
                    y += placeable.height + gap
                }
            }
        }
    }
}

/** The strip at the bottom of a phone form: it stays above the keyboard (the root pads for it). */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    Column(modifier.fillMaxWidth().background(colors.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapM),
            verticalArrangement = Arrangement.spacedBy(dimens.gapS),
            content = content,
        )
    }
}

/** A full-width main button for [BottomActionBar]. */
@Composable
fun BottomPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    DeskButton(text, onClick, modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp), icon = icon, primary = true, enabled = enabled)
}

/** The bottom bar's one line: an optional flat button on the left, the main button taking the rest. */
@Composable
fun BottomActions(primary: @Composable (Modifier) -> Unit, secondary: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DeskTheme.dimens.gapS), verticalAlignment = Alignment.CenterVertically) {
        if (secondary != null) secondary()
        primary(Modifier.weight(1f))
    }
}

/** Title (and a close button) for phone screens that sit outside the shell, which has no top bar for them. */
@Composable
fun PhoneHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onClose: (() -> Unit)? = null) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onClose != null) dimens.gapXs else dimens.pagePadding, end = dimens.pagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onClose != null) {
            DeskIconButton(DeskIcons.Close, stringResource(R.string.phone_screens_close), onClose)
            Spacer(Modifier.width(dimens.gapS))
        }
        Column(Modifier.weight(1f).padding(vertical = dimens.gapS)) {
            Text(title, style = DeskTheme.type.headline, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = DeskTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** An empty or "nothing found" state that looks meant: a round icon, a title, a sentence, one action. */
@Composable
fun PhoneEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    Column(
        modifier.fillMaxWidth().padding(top = dimens.gapXl, bottom = dimens.gapM),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.gapM),
    ) {
        Box(
            Modifier.size(72.dp).background(colors.surface, CircleShape).border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(32.dp))
        }
        Text(
            title,
            style = TextStyle(fontFamily = AppFonts.Literata, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        if (text != null) Text(text, style = DeskTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(dimens.gapXs))
            action()
        }
    }
}

/**
 * A settings line. Beside the text on tablets and DeX (the kit's row); on phones the control sits
 * below the text, left-aligned and at its normal size, so nothing is squeezed.
 */
@Composable
fun SettingLine(title: String, modifier: Modifier = Modifier, subtitle: String? = null, control: @Composable () -> Unit) {
    if (!isPhone()) {
        SettingRow(title, modifier, subtitle, control)
        return
    }
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    Column(modifier.fillMaxWidth().padding(vertical = dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        Column {
            Text(title, style = DeskTheme.type.bodyStrong, color = colors.text)
            if (subtitle != null) Text(subtitle, style = DeskTheme.type.labelSmall, color = colors.textTertiary)
        }
        control()
    }
}

/** Opens its content once with a short expand and fade, then behaves like plain content. */
@Composable
fun Appear(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = expandVertically(androidx.compose.animation.core.tween(200)) + fadeIn(androidx.compose.animation.core.tween(200)),
    ) { content() }
}
