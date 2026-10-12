package com.hesaplarimdijital.kitapbulucu.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.LibraryState
import com.hesaplarimdijital.kitapbulucu.store.LoadFailure
import com.hesaplarimdijital.kitapbulucu.store.LoadStatus
import com.hesaplarimdijital.kitapbulucu.ui.DropdownChoice
import com.hesaplarimdijital.kitapbulucu.ui.FilePicker
import com.hesaplarimdijital.kitapbulucu.ui.LibraryTexts
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.components.SettingRow
import desktopkit.settings.SettingsSection
import desktopkit.theme.DeskTheme

/**
 * One library file in setup: whose it is, which file is linked and how reading it went, with
 * the ways forward (choose, create, choose a sheet, map the columns, try again).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryFileCard(
    owner: Owner,
    state: LibraryState,
    isMine: Boolean?,
    selectedSheet: String?,
    picker: FilePicker,
    onChooseSheet: (String) -> Unit,
    onMap: () -> Unit,
    onRetry: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val dimens = DeskTheme.dimens
    SettingsSection(LibraryTexts.listName(resources, owner)) {
        if (isMine != null) {
            Text(
                stringResource(if (isMine) R.string.setup_own_list else R.string.setup_other_list),
                style = type.labelSmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(dimens.gapS))
        }
        if (state.isLinked && state.displayName != null) {
            Text(stringResource(R.string.setup_file_name, state.displayName), style = type.body, color = colors.text)
        }
        val failed = state.status == LoadStatus.FAILED
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.status == LoadStatus.READY) {
                Icon(DeskIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(dimens.icon))
                Spacer(Modifier.width(dimens.gapS))
            }
            Text(
                LibraryTexts.status(resources, state),
                style = type.body,
                color = if (failed) colors.danger else colors.textSecondary,
            )
        }
        if (state.problems.isNotEmpty()) {
            Text(stringResource(R.string.setup_problems, state.problems.size), style = type.labelSmall, color = colors.warning)
        }
        if (picker.createFailedFor == owner) {
            Text(stringResource(R.string.setup_create_failed), style = type.body, color = colors.danger)
        }
        if (state.sheetNames.isNotEmpty() && selectedSheet != null) {
            SettingRow(stringResource(R.string.setup_sheet)) {
                DropdownChoice(
                    options = state.sheetNames.map { it to it },
                    selected = selectedSheet,
                    onSelect = onChooseSheet,
                    modifier = Modifier.widthIn(min = 160.dp),
                )
            }
        }
        Spacer(Modifier.height(dimens.gapS))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            if (failed && state.failure == LoadFailure.NO_HEADER) {
                DeskButton(stringResource(R.string.setup_map_columns), onMap, primary = true)
            }
            if (failed && state.failure != LoadFailure.NO_HEADER && state.failure != LoadFailure.OLD_XLS) {
                DeskButton(stringResource(R.string.action_retry), onRetry)
            }
            if (state.isLinked) {
                DeskButton(stringResource(R.string.setup_change), { picker.pick(owner) }, icon = DeskIcons.Folder)
            } else {
                DeskButton(stringResource(R.string.setup_pick), { picker.pick(owner) }, icon = DeskIcons.Folder, primary = true)
            }
            DeskButton(stringResource(R.string.setup_create), { picker.create(owner) }, icon = DeskIcons.Plus)
        }
    }
}
