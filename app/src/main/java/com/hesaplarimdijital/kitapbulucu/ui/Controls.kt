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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
    val phone = isPhone()
    Box(modifier) {
        Row(
            modifier = Modifier
                .then(if (phone) Modifier.fillMaxWidth() else Modifier)
                .defaultMinSize(minHeight = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
                .onSizeChanged { height = it.height }
                .focusRing(state, shape)
                .background(stateBackground(state, rest = colors.surfaceRaised), shape)
                .border(1.dp, colors.borderStrong, shape)
                .deskClickable(interaction, { open = true }, role = Role.DropdownList)
                .padding(horizontal = dimens.gapM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = DeskTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = phone))
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
    if (isPhone()) {
        // the accent stripe is laid over the card, so the card needs no intrinsic height
        Box(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.border, shape),
        ) {
            Box(Modifier.matchParentSize()) { Box(Modifier.width(4.dp).fillMaxHeight().background(tone)) }
            Column(Modifier.padding(start = dimens.gapM + 4.dp, end = dimens.gapM, top = dimens.gapM, bottom = dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                Text(title, style = DeskTheme.type.title, color = colors.text)
                Text(text, style = DeskTheme.type.body, color = colors.textSecondary)
                // flat buttons, one line when they fit and one under the other when they do not
                ButtonRow { actions() }
            }
        }
        return
    }
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

/**
 * A labelled one-line text field in the kit's look. Tab and Shift+Tab move between fields,
 * Enter runs [onEnter] (the form's save).
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
    monospace: Boolean = false,
    error: Boolean = false,
    onEnter: () -> Unit = {},
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(dimens.radiusControl)
    val touch = DeskTheme.inputMode == desktopkit.theme.InputMode.Touch
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = DeskTheme.type.label, color = if (error) colors.danger else colors.textSecondary)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = DeskTheme.type.body.copy(
                color = colors.text,
                fontFamily = if (monospace) DeskTheme.type.mono.fontFamily else DeskTheme.type.body.fontFamily,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        androidx.compose.ui.input.key.Key.Tab -> {
                            focusManager.moveFocus(if (event.isShiftPressed) androidx.compose.ui.focus.FocusDirection.Previous else androidx.compose.ui.focus.FocusDirection.Next)
                            true
                        }
                        androidx.compose.ui.input.key.Key.Enter, androidx.compose.ui.input.key.Key.NumPadEnter -> {
                            onEnter()
                            true
                        }
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = if (touch) 48.dp else 36.dp)
                        .background(colors.surfaceRaised, shape)
                        .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else if (error) colors.danger else colors.borderStrong, shape)
                        .padding(horizontal = dimens.gapM, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

/** One button of a [ConfirmDialog]. */
class DialogAction(val label: String, val primary: Boolean = false, val onClick: () -> Unit)

/**
 * A small dialog in the kit's look: title, sentence, buttons. Esc and a tap outside cancel;
 * the first primary button takes the keyboard focus, so Enter confirms. It fades and grows in
 * and fades out (the action runs once the exit has finished, a tenth of a second later). On a
 * phone it is as wide as the screen less 16 dp margins, with the buttons right-aligned in one
 * line (cancel left, main action right) or stacked when they do not fit.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ConfirmDialog(title: String, text: String, actions: List<DialogAction>, onDismiss: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    val phone = isPhone()
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    val visible = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    // every way out first plays the exit, then runs what the caller asked for; a second click meanwhile does nothing
    fun leave(after: () -> Unit) {
        if (pending != null) return
        pending = after
        visible.targetState = false
    }
    androidx.compose.runtime.LaunchedEffect(visible.currentState, visible.targetState) {
        val after = pending
        if (after != null && !visible.currentState && !visible.targetState) {
            pending = null
            after()
        }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { leave(onDismiss) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !phone),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = visible,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(150), initialScale = 0.94f),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)) +
                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(100), targetScale = 0.96f),
        ) {
            Column(
                Modifier
                    .then(if (phone) Modifier.padding(horizontal = dimens.gapL) else Modifier.widthIn(max = 480.dp))
                    .fillMaxWidth()
                    .background(colors.surfaceRaised, shape)
                    .border(1.dp, colors.borderStrong, shape)
                    .padding(dimens.gapL),
                verticalArrangement = Arrangement.spacedBy(dimens.gapM),
            ) {
                Text(title, style = DeskTheme.type.headline, color = colors.text)
                Text(text, style = DeskTheme.type.body, color = colors.textSecondary)
                val firstPrimary = actions.indexOfFirst { it.primary }
                val run: (DialogAction) -> Unit = { action ->
                    leave {
                        onDismiss()
                        action.onClick()
                    }
                }
                if (phone) {
                    ButtonRow(Modifier.padding(top = dimens.gapXs)) {
                        actions.forEachIndexed { index, action ->
                            val modifier = if (index == firstPrimary) Modifier.focusRequester(first) else Modifier
                            if (action.primary) {
                                desktopkit.components.DeskButton(action.label, { run(action) }, modifier = modifier, primary = true)
                            } else {
                                PhoneTextButton(action.label, { run(action) }, modifier = modifier)
                            }
                        }
                    }
                } else {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
                        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
                    ) {
                        actions.forEachIndexed { index, action ->
                            desktopkit.components.DeskButton(
                                action.label,
                                { run(action) },
                                modifier = if (index == firstPrimary) Modifier.focusRequester(first) else Modifier,
                                primary = action.primary,
                            )
                        }
                    }
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * A row with a check box in the kit's look: click, tap, Space or Enter toggles it. The kit has
 * no check box of its own, and one row per book reads better than a switch.
 */
@Composable
fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(stateBackground(state))
            .focusRing(state, shape)
            .deskClickable(interaction, { onChange(!checked) }, role = Role.Checkbox)
            .defaultMinSize(minHeight = if (isPhone()) 48.dp else 0.dp)
            .padding(horizontal = dimens.gapS, vertical = dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val box = RoundedCornerShape(4.dp)
        Box(
            Modifier
                .size(18.dp)
                .background(if (checked) colors.accent else colors.surface, box)
                .border(1.5.dp, if (checked) colors.accent else colors.borderStrong, box),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(DeskIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(dimens.gapM))
        Box(Modifier.weight(1f)) { content() }
    }
}
