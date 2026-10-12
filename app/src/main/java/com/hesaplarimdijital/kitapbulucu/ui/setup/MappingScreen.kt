package com.hesaplarimdijital.kitapbulucu.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.BookColumn
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.ColumnMapping
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import com.hesaplarimdijital.kitapbulucu.ui.DropdownChoice
import com.hesaplarimdijital.kitapbulucu.ui.LibraryTexts
import com.hesaplarimdijital.kitapbulucu.xlsx.CellReferences
import com.hesaplarimdijital.kitapbulucu.xlsx.Sheet
import desktopkit.components.DeskButton
import desktopkit.screens.PageHeader
import desktopkit.settings.SettingsSection
import desktopkit.theme.DeskTheme

/**
 * Once per file whose headers the app does not recognise: which row holds the column names
 * (or none), and what each column holds, with the first values of every column as a preview.
 */
@Composable
fun MappingScreen(owner: Owner, store: LibraryStore, onClose: () -> Unit) {
    val previews by store.previews.collectAsState()
    val states by store.states.collectAsState()
    val preview = previews[owner]
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val dimens = DeskTheme.dimens
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(stringResource(R.string.mapping_title), subtitle = LibraryTexts.listName(resources, owner))
        if (preview == null) {
            Text(stringResource(R.string.mapping_waiting), style = type.body, color = colors.textSecondary, modifier = Modifier.padding(horizontal = dimens.pagePadding))
            Spacer(Modifier.height(dimens.gapM))
            DeskButton(stringResource(R.string.mapping_cancel), onClose, modifier = Modifier.padding(horizontal = dimens.pagePadding))
            return@Column
        }
        val sheet = preview.selectedSheet
        val fileName = states.getValue(owner).displayName ?: owner.fileName
        Text(
            stringResource(R.string.mapping_intro, fileName),
            style = type.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = dimens.pagePadding).widthIn(max = 760.dp),
        )
        Spacer(Modifier.height(dimens.gapS))
        MappingEditor(
            sheet = sheet,
            sheetNames = preview.filledSheetNames,
            initial = store.mapping(owner)?.takeIf { it.fits(sheet, BookTable.detectHeaderRow(sheet)) } ?: ColumnMapping.guess(sheet),
            onChooseSheet = { store.chooseSheet(owner, it) },
            onSave = { mapping ->
                store.setMapping(owner, mapping)
                onClose()
            },
            onCancel = onClose,
        )
    }
}

@Composable
private fun MappingEditor(
    sheet: Sheet,
    sheetNames: List<String>,
    initial: ColumnMapping,
    onChooseSheet: (String) -> Unit,
    onSave: (ColumnMapping) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val dimens = DeskTheme.dimens
    // a new sheet starts a new mapping
    var headerRow by remember(sheet) { mutableIntStateOf(initial.headerRow) }
    val columns = remember(sheet) { mutableStateMapOf<Int, BookColumn>().apply { putAll(initial.columns) } }
    val columnCount = BookTable.columnCount(sheet).coerceAtLeast(1)

    if (sheetNames.isNotEmpty()) {
        SettingsSection(stringResource(R.string.setup_sheet)) {
            DropdownChoice(sheetNames.map { it to it }, sheet.name, onChooseSheet, modifier = Modifier.widthIn(min = 200.dp))
        }
    }
    SettingsSection(stringResource(R.string.mapping_header_row)) {
        val rowOptions = buildList<Pair<Int, String>> {
            add(-1 to stringResource(R.string.mapping_no_header))
            for (row in 0 until minOf(sheet.rows.size, BookTable.HEADER_SEARCH_ROWS)) {
                val cells = sheet.rows[row].map(TurkishText::squeeze).filter { it.isNotEmpty() }
                if (cells.isEmpty()) continue
                add(row to stringResource(R.string.mapping_row, row + 1, shorten(cells.take(3).joinToString(", "), 48)))
            }
        }
        DropdownChoice(rowOptions, headerRow, { headerRow = it }, modifier = Modifier.fillMaxWidth(), menuWidth = 360.dp)
    }
    SettingsSection(stringResource(R.string.mapping_title)) {
        val unused = stringResource(R.string.mapping_unused)
        val roleOptions: List<Pair<BookColumn?, String>> = listOf<Pair<BookColumn?, String>>(null to unused) + BookColumn.entries.map { it to it.header }
        val body = sheet.rows.drop(headerRow + 1).take(25)
        val emptySamples = stringResource(R.string.mapping_samples_empty)
        for (column in 0 until columnCount) {
            val letter = CellReferences.columnName(column)
            val named = if (headerRow >= 0) TurkishText.squeeze(sheet.rows.getOrNull(headerRow)?.getOrNull(column).orEmpty()) else ""
            val title = if (named.isNotEmpty()) stringResource(R.string.mapping_column_named, letter, shorten(named, 30)) else stringResource(R.string.mapping_column, letter)
            val samples = body.mapNotNull { row -> row.getOrNull(column)?.let(TurkishText::squeeze)?.takeIf { it.isNotEmpty() } }.take(3)
            val sampleText = if (samples.isEmpty()) emptySamples else samples.joinToString(" · ") { shorten(it, 40) }
            MappingRow(
                title = title,
                samples = sampleText,
                control = {
                    DropdownChoice(
                        options = roleOptions,
                        selected = columns[column],
                        onSelect = { role ->
                            if (role == null) {
                                columns.remove(column)
                            } else {
                                // a field lives in one column only
                                columns.filter { it.value == role && it.key != column }.keys.forEach { columns.remove(it) }
                                columns[column] = role
                            }
                        },
                        modifier = Modifier.widthIn(min = 220.dp),
                    )
                },
            )
        }
    }
    val hasTitle = BookColumn.TITLE in columns.values
    Column(
        Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapM).widthIn(max = 760.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        if (!hasTitle) Text(stringResource(R.string.mapping_need_title), style = type.body, color = colors.danger)
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            DeskButton(
                stringResource(R.string.mapping_save),
                { onSave(ColumnMapping.of(sheet, headerRow, columns.toMap())) },
                primary = true,
                enabled = hasTitle,
            )
            DeskButton(stringResource(R.string.mapping_cancel), onCancel)
        }
    }
    Spacer(Modifier.height(dimens.gapL))
}

/** Column name and samples beside the drop-down on wide windows, above it on narrow ones. */
@Composable
private fun MappingRow(title: String, samples: String, control: @Composable () -> Unit) {
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val dimens = DeskTheme.dimens
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = dimens.gapS / 2)) {
        val text: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text(title, style = type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(samples, style = type.labelSmall, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.gapS / 2)) {
                text(Modifier)
                control()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                text(Modifier.weight(1f))
                Spacer(Modifier.width(dimens.gapM))
                control()
            }
        }
    }
}

private fun shorten(text: String, max: Int): String = if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
