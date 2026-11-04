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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import desktopkit.components.DeskButton
import desktopkit.components.DeskDialog
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.theme.DeskTheme
import desktopkit.shell.currentWidthClass
import desktopkit.shell.keepsKeyboardOpen
import desktopkit.shell.WidthClass

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
    if (currentWidthClass() == WidthClass.Compact) {
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
    // The field keeps its own cursor and selection. When the caller replaces the text (a chosen
    // suggestion, a cleared form) the cursor goes to the end of the new text instead of staying
    // at its old index; typing keeps the cursor where the user put it.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = DeskTheme.type.label, color = if (error) colors.danger else colors.textSecondary)
        androidx.compose.foundation.text.BasicTextField(
            value = shown,
            onValueChange = { next ->
                field = next
                if (next.text != value) onValueChange(next.text)
            },
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
                // counted as one of the kit's fields: a tap on it keeps the on-screen keyboard open
                .keepsKeyboardOpen()
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
 * The kit's dialog ([DeskDialog]) with a sentence and buttons: Esc, the dialog's own close button
 * (× on touch, "Esc" with a keyboard) and a tap outside cancel; the first primary button takes the
 * keyboard focus, so Enter confirms. A button closes the dialog and then runs its action. The
 * buttons sit in one line at the end (cancel left, main action right) or, when they do not fit
 * (a phone with three of them), one under the other.
 */
@Composable
fun ConfirmDialog(title: String, text: String, actions: List<DialogAction>, onDismiss: () -> Unit) {
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstPrimary = actions.indexOfFirst { it.primary }
    DeskDialog(
        onDismiss = onDismiss,
        title = title,
        maxWidth = 480.dp,
        actions = {
            ButtonRow(Modifier.weight(1f)) {
                actions.forEachIndexed { index, action ->
                    DeskButton(
                        action.label,
                        {
                            onDismiss()
                            action.onClick()
                        },
                        modifier = if (index == firstPrimary) Modifier.focusRequester(first) else Modifier,
                        primary = action.primary,
                    )
                }
            }
        },
    ) {
        Text(text, style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
