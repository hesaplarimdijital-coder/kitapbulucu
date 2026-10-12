package com.hesaplarimdijital.kitapbulucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore

/** Opens the system's file and folder pickers and hands the result to the store. */
class FilePicker internal constructor(
    private val onPick: (Owner) -> Unit,
    private val onCreate: (Owner) -> Unit,
    private val onPickFolder: () -> Unit,
    /** The owner whose new file could not be written, until the next attempt. */
    val createFailedFor: Owner?,
) {
    fun pick(owner: Owner) = onPick(owner)
    fun create(owner: Owner) = onCreate(owner)
    fun pickFolder() = onPickFolder()

    companion object {
        const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        /** Spreadsheets first; "*" too, because some providers label .xlsx files loosely. */
        val OPEN_TYPES = arrayOf(MIME_XLSX, "application/vnd.ms-excel", "text/csv", "text/comma-separated-values", "*/*")
    }
}

@Composable
fun rememberFilePicker(store: LibraryStore): FilePicker {
    var pending by rememberSaveable { mutableStateOf<Owner?>(null) }
    var failed by remember { mutableStateOf<Owner?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val owner = pending
        if (uri != null && owner != null) store.relink(owner, uri, null)
        pending = null
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FilePicker.MIME_XLSX)) { uri ->
        val owner = pending
        if (uri != null && owner != null) store.createEmpty(owner, uri) { ok -> failed = if (ok) null else owner }
        pending = null
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) store.setTree(uri)
    }
    return FilePicker(
        onPick = { owner ->
            pending = owner
            failed = null
            open.launch(FilePicker.OPEN_TYPES)
        },
        onCreate = { owner ->
            pending = owner
            failed = null
            create.launch(owner.fileName)
        },
        onPickFolder = { folder.launch(null) },
        createFailedFor = failed,
    )
}
