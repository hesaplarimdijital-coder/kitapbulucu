package com.hesaplarimdijital.kitapbulucu.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.hesaplarimdijital.kitapbulucu.model.Suggestions
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/**
 * A [FormField] that offers values already in the lists while you type (Yazar, Yayınevi). Free
 * typing is unchanged; a suggestion only fills the field when it is chosen.
 *
 * Mouse and keyboard (DeX): the list floats under the field in a window that never takes the focus,
 * so typing goes on. ↓/↑ move through the list, Enter or Tab take the marked suggestion (Enter
 * with nothing marked still runs [onEnter]), Esc closes the list. Touch: the list sits right under
 * the field inside the form, which scrolls, so the soft keyboard cannot hide it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuggestField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: Suggestions,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    onEnter: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    // the text the list was last closed for (Esc or a pick); typing opens it again
    var closedFor by remember { mutableStateOf<String?>(null) }
    var widthPx by remember { mutableIntStateOf(0) }
    val choices = remember(suggestions, value) { suggestions.suggest(value) }
    var marked by remember(choices) { mutableIntStateOf(-1) }
    val visible = focused && choices.isNotEmpty() && closedFor != value
    val touch = DeskTheme.inputMode == InputMode.Touch

    fun pick(text: String) {
        closedFor = text
        marked = -1
        onValueChange(text)
    }

    Box(modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }) {
        Column {
            FormField(
                label, value,
                { closedFor = null; onValueChange(it) },
                modifier = Modifier
                    .onFocusChanged { focused = it.hasFocus }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (!visible) {
                            // ↓ opens a list that was closed with Esc
                            return@onPreviewKeyEvent if (event.key == Key.DirectionDown && focused && choices.isNotEmpty()) {
                                closedFor = null
                                true
                            } else {
                                false
                            }
                        }
                        when (event.key) {
                            Key.DirectionDown -> { marked = (marked + 1).coerceAtMost(choices.lastIndex); true }
                            Key.DirectionUp -> { marked = (marked - 1).coerceAtLeast(-1); true }
                            Key.Escape -> { closedFor = value; marked = -1; true }
                            Key.Enter, Key.NumPadEnter -> if (marked in choices.indices) { pick(choices[marked]); true } else false
                            Key.Tab -> if (!event.isShiftPressed && marked in choices.indices) { pick(choices[marked]); true } else false
                            else -> false
                        }
                    },
                focusRequester = focusRequester,
                capitalization = capitalization,
                onEnter = onEnter,
            )
            if (visible && touch) {
                val bring = remember { BringIntoViewRequester() }
                LaunchedEffect(choices) { runCatching { bring.bringIntoView() } }
                SuggestionList(choices, value, suggestions, marked, ::pick, Modifier.padding(top = 4.dp).bringIntoViewRequester(bring))
            }
        }
        if (visible && !touch) {
            val width = with(LocalDensity.current) { widthPx.toDp() }
            Popup(
                popupPositionProvider = remember { BelowAnchor },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = false, dismissOnBackPress = false),
            ) {
                SuggestionList(choices, value, suggestions, marked, ::pick, Modifier.width(width))
            }
        }
    }
}

/** Under the anchor, or above it when the screen has no room below. */
private object BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val gap = 4
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(anchorBounds.left, y)
    }
}

@Composable
private fun SuggestionList(
    choices: List<String>,
    query: String,
    suggestions: Suggestions,
    marked: Int,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val touch = DeskTheme.inputMode == InputMode.Touch
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(vertical = 4.dp),
    ) {
        choices.forEachIndexed { index, text ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val chosen = index == marked
            val background = when {
                chosen -> colors.selection
                hovered -> colors.hover
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            val textColor = if (chosen) colors.onSelection else colors.text
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(background)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onPick(text) }
                    .defaultMinSize(minHeight = if (touch) 48.dp else 32.dp)
                    .padding(horizontal = dimens.gapM, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(emphasised(text, suggestions.highlights(text, query)), style = DeskTheme.type.body, color = textColor)
            }
        }
    }
}

/** [text] with the [marks] ranges in bold. */
private fun emphasised(text: String, marks: List<IntRange>): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (mark in marks) {
        if (mark.first < at || mark.last >= text.length) continue
        append(text.substring(at, mark.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(mark.first, mark.last + 1)) }
        at = mark.last + 1
    }
    append(text.substring(at))
}
