package com.hesaplarimdijital.kitapbulucu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import desktopkit.components.DeskIcons
import desktopkit.components.MenuEntry
import desktopkit.components.MenuPopup
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.theme.DeskTheme

/**
 * A drop-down built from the kit's parts: a field that shows the chosen option and opens the
 * kit's menu below it. Works with mouse, touch and keyboard (Tab to focus, Enter to open).
 */
@Composable
fun <T> DropdownChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    menuWidth: Dp = 260.dp,
) {
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val label = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Box(modifier) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
                .onSizeChanged { height = it.height }
                .focusRing(state, shape)
                .background(stateBackground(state, rest = colors.surfaceRaised), shape)
                .border(1.dp, colors.borderStrong, shape)
                .deskClickable(interaction, { open = true }, role = Role.DropdownList)
                .padding(horizontal = dimens.gapM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = DeskTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(dimens.gapS))
            Icon(DeskIcons.ChevronDown, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.icon))
        }
        MenuPopup(
            visible = open,
            onDismiss = { open = false },
            entries = options.map { (value, text) -> MenuEntry.Item(text, { onSelect(value) }, checked = value == selected) },
            offset = IntOffset(0, height),
            minWidth = menuWidth,
        )
    }
}

/** A card with a title, a sentence and actions, for things the user should know now. */
@Composable
fun NoticeCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = DeskTheme.colors.accentText,
    actions: @Composable () -> Unit = {},
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Row(
        modifier = modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(tone))
        Column(Modifier.padding(dimens.gapM).weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            Text(title, style = DeskTheme.type.title, color = colors.text)
            Text(text, style = DeskTheme.type.body, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) { actions() }
        }
    }
}
