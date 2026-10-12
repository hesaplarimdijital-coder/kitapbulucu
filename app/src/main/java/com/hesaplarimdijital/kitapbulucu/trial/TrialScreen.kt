package com.hesaplarimdijital.kitapbulucu.trial

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hesaplarimdijital.kitapbulucu.R
import desktopkit.components.DeskButton
import desktopkit.screens.PageHeader
import desktopkit.settings.SettingsSection
import desktopkit.theme.DeskTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Temporary screen for the Sprint 1 trial: does Google Drive let the app pick, read and write a
 * file, keep the permission, and list a folder? The log is copied into the chat as the result.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrialScreen(pageTitle: String) {
    val context = LocalContext.current
    val actions = remember(context) { TrialActions(context.applicationContext) }
    val log = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val colors = DeskTheme.colors
    val type = DeskTheme.type

    fun note(line: String) {
        log.add(0, "${actions.time(System.currentTimeMillis()).substringAfter(' ')} · $line")
    }

    fun readFile(uri: Uri) = scope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { actions.read(uri) } }
        result.onSuccess { read ->
            val info = read.info
            note("Okundu (${read.millis} ms): ${info?.name ?: "?"}, ${info?.size ?: -1} bayt, dosya değişme zamanı " +
                (info?.lastModified?.takeIf { it > 0 }?.let(actions::time) ?: "bilinmiyor"))
            read.cells.forEach { row -> note("  Hücre: " + row.joinToString(" | ")) }
        }.onFailure { note("OKUMA HATASI: ${it.javaClass.simpleName}: ${it.message}") }
    }

    fun writeFile(uri: Uri) = scope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { actions.write(uri) } }
        result.onSuccess { note("Yazıldı (${it.millis} ms, kip ${it.mode}): sayaç ${it.count}, saat ${it.stamp}") }
            .onFailure { note("YAZMA HATASI: ${it.javaClass.simpleName}: ${it.message}") }
        readFile(uri)
    }

    fun listFolder(uri: Uri) = scope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { actions.listFolder(uri) } }
        result.onSuccess { names ->
            note("Klasörde ${names.size} öğe: ${actions.describe(uri)}")
            names.forEach { note("  $it") }
        }.onFailure { note("KLASÖR HATASI: ${it.javaClass.simpleName}: ${it.message}") }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TrialActions.MIME_XLSX)) { uri ->
        if (uri == null) { note("Dosya oluşturma iptal edildi"); return@rememberLauncherForActivityResult }
        note("Dosya oluşturuldu: ${actions.describe(uri)}")
        note(actions.keepPermission(uri))
        actions.fileUri = uri
        writeFile(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { note("Dosya seçme iptal edildi"); return@rememberLauncherForActivityResult }
        note("Dosya seçildi: ${actions.describe(uri)}")
        note(actions.keepPermission(uri))
        actions.fileUri = uri
        readFile(uri)
    }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) { note("Klasör seçme iptal edildi"); return@rememberLauncherForActivityResult }
        note("Klasör seçildi: ${actions.describe(uri)}")
        note(actions.keepPermission(uri))
        actions.treeUri = uri
        listFolder(uri)
    }

    fun withFile(action: (Uri) -> Unit) {
        val uri = actions.fileUri
        if (uri == null) note("Önce bir dosya oluştur ya da seç") else action(uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(title = pageTitle)
        Text(
            stringResource(R.string.trial_intro),
            style = type.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = DeskTheme.dimens.pagePadding),
        )

        SettingsSection(stringResource(R.string.trial_file)) {
            Text(actions.fileUri?.let(actions::describe) ?: stringResource(R.string.trial_none), style = type.labelSmall, color = colors.textTertiary)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeskButton(stringResource(R.string.trial_create), { create.launch(TrialActions.SUGGESTED_NAME) }, primary = true)
                DeskButton(stringResource(R.string.trial_open), { open.launch(arrayOf(TrialActions.MIME_XLSX, "*/*")) })
                DeskButton(stringResource(R.string.trial_read), { withFile { readFile(it) } })
                DeskButton(stringResource(R.string.trial_write), { withFile { writeFile(it) } })
            }
        }

        SettingsSection(stringResource(R.string.trial_folder)) {
            Text(actions.treeUri?.let(actions::describe) ?: stringResource(R.string.trial_none), style = type.labelSmall, color = colors.textTertiary)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeskButton(stringResource(R.string.trial_pick_folder), { tree.launch(null) })
                DeskButton(stringResource(R.string.trial_list_folder), {
                    val uri = actions.treeUri
                    if (uri == null) note("Önce bir klasör seç") else listFolder(uri)
                })
                DeskButton(stringResource(R.string.trial_permissions), {
                    val permissions = actions.permissions()
                    note("Hatırlanan izin sayısı: ${permissions.size}")
                    permissions.forEach { note("  $it") }
                })
            }
        }

        SettingsSection(stringResource(R.string.trial_log)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeskButton(stringResource(R.string.trial_copy), {
                    val header = "Kitap Bulucu deneme · ${actions.deviceName} · Android ${Build.VERSION.RELEASE} · ${actions.time(System.currentTimeMillis())}"
                    clipboard.setText(AnnotatedString((listOf(header) + log.reversed()).joinToString("\n")))
                    note("Günlük panoya kopyalandı")
                }, primary = log.isNotEmpty())
                DeskButton(stringResource(R.string.trial_clear), { log.clear() })
            }
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Column(Modifier.fillMaxWidth()) {
                    if (log.isEmpty()) {
                        Text(stringResource(R.string.trial_log_empty), style = type.labelSmall, color = colors.textTertiary)
                    }
                    log.forEach { line ->
                        Text(line, style = type.mono, color = if ("HATA" in line) colors.danger else colors.text)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
