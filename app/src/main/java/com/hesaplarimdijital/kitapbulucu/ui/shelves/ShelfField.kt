package com.hesaplarimdijital.kitapbulucu.ui.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.ShelfGroup
import com.hesaplarimdijital.kitapbulucu.model.Shelves
import com.hesaplarimdijital.kitapbulucu.ui.FormField
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.MenuEntry
import desktopkit.components.MenuPopup

/**
 * The Raf field of the add and edit form (S-35): free typing as before, plus the list's shelves
 * in a drop-down (the arrow button, or ↓ in the field). Typing narrows the drop-down.
 */
@Composable
fun ShelfField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    shelves: List<ShelfGroup>,
    modifier: Modifier = Modifier,
    onEnter: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    val choices = remember(shelves, value) {
        val key = Shelves.key(value)
        val narrowed = filterShelves(shelves, value)
        if (narrowed.isEmpty() || shelves.any { Shelves.key(it.shelf) == key }) shelves else narrowed
    }
    Box(modifier.onSizeChanged { height = it.height }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            FormField(
                label, value, onValueChange,
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && shelves.isNotEmpty()) {
                        open = true
                        true
                    } else {
                        false
                    }
                },
                capitalization = KeyboardCapitalization.Characters,
                monospace = true,
                onEnter = onEnter,
            )
            DeskIconButton(DeskIcons.ChevronDown, stringResource(R.string.shelf_list_open), { open = true }, enabled = shelves.isNotEmpty())
        }
        MenuPopup(
            visible = open,
            onDismiss = { open = false },
            entries = choices.map { group ->
                MenuEntry.Item(
                    if (group.note.isEmpty()) group.shelf else group.shelf + " · " + group.note,
                    { onValueChange(group.shelf) },
                    checked = Shelves.key(group.shelf) == Shelves.key(value),
                )
            },
            offset = IntOffset(0, height),
            minWidth = 200.dp,
        )
    }
}
