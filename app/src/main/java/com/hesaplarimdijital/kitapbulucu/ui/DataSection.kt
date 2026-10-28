package com.hesaplarimdijital.kitapbulucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.Backups
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.store.TitleCaseFix
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.settings.SettingsSection
import desktopkit.theme.DeskTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Settings for the files' contents (S-23 … S-25): merging another Excel file, exporting both
 * lists as one workbook, fixing titles and authors typed in capitals, changes still waiting to
 * be written, and restoring a backup.
 */
@Composable
fun DataSection(store: LibraryStore, onMerge: () -> Unit) {
    val states by store.states.collectAsState()
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FilePicker.MIME_XLSX)) { uri ->
        if (uri != null) {
            store.exportTo(uri) { ok -> message = resources.getString(if (ok) R.string.export_done else R.string.export_failed) }
        }
    }

    SettingsSection(stringResource(R.string.settings_data)) {
        SettingLine(stringResource(R.string.settings_merge_title), subtitle = stringResource(R.string.settings_merge_text)) {
            DeskButton(stringResource(R.string.settings_merge), onMerge, icon = AppIcons.Import)
        }
        SettingLine(stringResource(R.string.settings_export_title), subtitle = stringResource(R.string.settings_export_text)) {
            DeskButton(stringResource(R.string.settings_export), { export.launch(resources.getString(R.string.export_file_name)) }, icon = AppIcons.Export)
        }
        TitleCaseLine(store) { message = it }

        val waiting = Owner.entries.map { states.getValue(it) }.filter { it.pendingChanges > 0 }
        val pendingText = if (waiting.isEmpty()) {
            stringResource(R.string.settings_pending_none)
        } else {
            waiting.joinToString("\n") { resources.getString(R.string.settings_pending_count, LibraryTexts.listName(resources, it.owner), it.pendingChanges) }
        }
        SettingLine(stringResource(R.string.settings_pending), subtitle = pendingText) {
            DeskButton(stringResource(R.string.settings_pending_try), store::refresh, icon = DeskIcons.Resume, enabled = waiting.isNotEmpty())
        }

        Text(stringResource(R.string.settings_backups), style = DeskTheme.type.bodyStrong, color = colors.text, modifier = Modifier.padding(top = DeskTheme.dimens.gapS))
        Text(stringResource(R.string.settings_backups_text), style = DeskTheme.type.labelSmall, color = colors.textTertiary)
        for (owner in Owner.entries) BackupList(store, owner) { message = it }

        val shown = message
        if (shown != null) {
            Text(shown, style = DeskTheme.type.body, color = colors.textSecondary, modifier = Modifier.padding(top = DeskTheme.dimens.gapS))
        }
    }
}

/**
 * "Ad ve yazarları düzelt": works out what would change off the main thread, asks with the
 * count per list, a few examples from the lists and, when the other person's list changes too,
 * says so (the question covers both lists), then applies it as one undoable step.
 */
@Composable
private fun TitleCaseLine(store: LibraryStore, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val resources = context.resources
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<TitleCaseFix.Plan?>(null) }
    SettingLine(stringResource(R.string.case_title), subtitle = stringResource(R.string.case_text)) {
        DeskButton(
            stringResource(R.string.case_button),
            {
                busy = true
                scope.launch {
                    val plan = store.planTitleCase()
                    busy = false
                    if (plan.total == 0) onMessage(resources.getString(R.string.case_none)) else asking = plan
                }
            },
            icon = DeskIcons.Convert,
            enabled = !busy,
        )
    }
    val plan = asking ?: return
    val me = remember { AppSettings.of(context).me }
    ConfirmDialog(
        title = stringResource(R.string.case_dialog_title),
        text = titleCaseQuestion(resources, plan, me),
        actions = listOf(
            DialogAction(stringResource(R.string.case_apply), primary = true) {
                store.fixTitleCase(
                    label = { applied -> resources.getString(R.string.case_undo, count(applied.total)) },
                    onDone = { applied -> if (applied.total > 0) onMessage(resources.getString(R.string.case_done, count(applied.total))) else onMessage(resources.getString(R.string.case_none)) },
                )
            },
            DialogAction(stringResource(R.string.mapping_cancel)) {},
        ),
        onDismiss = { asking = null },
    )
}

/** "MKE'nin listesinde 6.412 kitap, Barış'ın listesinde 35 kitap değişecek." with examples and the safety note. */
internal fun titleCaseQuestion(resources: android.content.res.Resources, plan: TitleCaseFix.Plan, me: Owner?): String {
    val parts = Owner.entries.filter { plan.count(it) > 0 }.map { owner ->
        resources.getString(if (owner == Owner.DAD) R.string.case_in_dad else R.string.case_in_me, count(plan.count(owner)))
    }
    val text = StringBuilder(resources.getString(R.string.case_counts, parts.joinToString(", ")))
    if (plan.examples.isNotEmpty()) {
        text.append("\n\n").append(resources.getString(R.string.case_examples))
        for ((before, after) in plan.examples) text.append('\n').append(resources.getString(R.string.case_example, before, after))
    }
    val other = Owner.entries.firstOrNull { it != me && me != null && plan.count(it) > 0 }
    if (other != null) text.append("\n\n").append(resources.getString(if (other == Owner.DAD) R.string.case_other_dad else R.string.case_other_me))
    text.append("\n\n").append(resources.getString(R.string.case_safety))
    return text.toString()
}

/** 6412 -> "6.412" in Turkish. */
private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)

/** One list's backups, folded away until asked for; each can be put back after a question. */
@Composable
private fun BackupList(store: LibraryStore, owner: Owner, onMessage: (String) -> Unit) {
    val resources = LocalContext.current.resources
    val states by store.states.collectAsState()
    var open by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<Backups.Backup?>(null) }
    // a write adds a backup: the list is read again whenever the list's state changes
    val backups = remember(states.getValue(owner)) { store.backups(owner) }
    val counts = remember { mutableStateMapOf<String, Int?>() }
    val listName = LibraryTexts.listName(resources, owner)
    val subtitle = if (backups.isEmpty()) stringResource(R.string.settings_backups_none) else backupDate(backups.first().at)
    SettingLine(listName, subtitle = subtitle) {
        if (backups.isNotEmpty()) {
            DeskButton(
                if (open) stringResource(R.string.settings_backups_hide) else stringResource(R.string.settings_backups_show, backups.size),
                { open = !open },
                icon = if (open) DeskIcons.ChevronUp else DeskIcons.ChevronDown,
            )
        }
    }
    LaunchedEffect(open, backups) {
        if (open) {
            for (backup in backups) {
                if (backup.file.name !in counts) counts[backup.file.name] = store.backupBookCount(owner, backup.file) ?: -1
            }
        }
    }
    // the list opens and closes with a short expand and fade
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
    ) {
        Column(Modifier.padding(start = DeskTheme.dimens.gapL)) {
            for (backup in backups) {
                val count = counts[backup.file.name]
                val detail = when {
                    count == null -> "…"
                    count < 0 -> stringResource(R.string.backup_row_unreadable)
                    else -> stringResource(R.string.backup_row_count, count)
                }
                SettingLine(backupDate(backup.at), subtitle = detail) {
                    DeskButton(stringResource(R.string.backup_restore), { asking = backup }, icon = DeskIcons.History)
                }
            }
        }
    }
    val chosen = asking
    if (chosen != null) {
        ConfirmDialog(
            title = stringResource(R.string.backup_confirm_title),
            text = stringResource(R.string.backup_confirm_text, listName, backupDate(chosen.at)),
            actions = listOf(
                DialogAction(stringResource(R.string.backup_restore), primary = true) {
                    store.restore(owner, chosen.file) { ok -> onMessage(resources.getString(if (ok) R.string.backup_done else R.string.backup_failed)) }
                },
                DialogAction(stringResource(R.string.mapping_cancel)) {},
            ),
            onDismiss = { asking = null },
        )
    }
}

private fun backupDate(at: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(at))
