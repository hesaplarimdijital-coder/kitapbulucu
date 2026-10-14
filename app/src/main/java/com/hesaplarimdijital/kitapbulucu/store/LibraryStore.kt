package com.hesaplarimdijital.kitapbulucu.store

import android.net.Uri
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.ColumnMapping
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.search.SearchIndex
import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import com.hesaplarimdijital.kitapbulucu.xlsx.FileKind
import com.hesaplarimdijital.kitapbulucu.xlsx.FileKinds
import com.hesaplarimdijital.kitapbulucu.xlsx.Sheet
import com.hesaplarimdijital.kitapbulucu.xlsx.SheetSpec
import com.hesaplarimdijital.kitapbulucu.xlsx.SpreadsheetException
import com.hesaplarimdijital.kitapbulucu.xlsx.SpreadsheetReader
import com.hesaplarimdijital.kitapbulucu.xlsx.Workbook
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxReader
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The two libraries as the screens see them (ARCHITECTURE 7, 8). [start] shows the cached
 * copies at once and then reads the files; [refresh] reads them again (on every return to the
 * foreground and on F5) and writes whatever changes are still pending. A read that finds the
 * same bytes changes nothing on screen; a read that fails keeps the last good list and says
 * why. The table a screen sees is the file's table with the pending changes already applied.
 * State changes happen on [scope]'s thread.
 */
class LibraryStore(
    private val links: FileLinks,
    private val documents: Documents,
    private val cache: LibraryCache,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pending: PendingChanges = PendingChanges(cache.dir.resolve("pending.json")),
    private val backups: Backups = Backups(cache.dir.resolve("backups")),
) {
    private val _states = MutableStateFlow(Owner.entries.associateWith { LibraryState.notLinked(it) })
    val states: StateFlow<Map<Owner, LibraryState>> = _states.asStateFlow()

    private val _index = MutableStateFlow<SearchIndex?>(null)
    /** Search over both libraries; null until anything was read. */
    val index: StateFlow<SearchIndex?> = _index.asStateFlow()

    private val _warning = MutableStateFlow<StrayFileWarning?>(null)
    val warning: StateFlow<StrayFileWarning?> = _warning.asStateFlow()

    private val _notices = MutableStateFlow<List<StoreNotice>>(emptyList())
    /** Things the user should know about writing, newest last; each is dismissed by hand. */
    val notices: StateFlow<List<StoreNotice>> = _notices.asStateFlow()

    private val _previews = MutableStateFlow<Map<Owner, WorkbookPreview>>(emptyMap())
    /** The first rows of every filled sheet of each file, for choosing a sheet and mapping columns. */
    val previews: StateFlow<Map<Owner, WorkbookPreview>> = _previews.asStateFlow()

    /** Hash of the bytes behind each shown table. */
    private val hashes = HashMap<Owner, String>()
    /** The table as the file (or its cached copy) holds it, before pending changes. */
    private val baseTables = HashMap<Owner, BookTable?>()
    private val indexed = HashMap<Owner, BookTable?>()
    private val otherSheetsWarned = HashSet<Owner>()
    private val undoStack = ArrayDeque<UndoStep>()

    private class UndoStep(val label: String, val inverses: List<Change>)

    /** The step just made, for the "Geri al" bar; null once undone or dismissed. */
    data class LastStep(val label: String, val at: Long)

    private val _lastStep = MutableStateFlow<LastStep?>(null)
    val lastStep: StateFlow<LastStep?> = _lastStep.asStateFlow()
    private var refreshing = false
    private var refreshAgain = false

    fun state(owner: Owner): LibraryState = _states.value.getValue(owner)

    /** Cached copies first, then the files. */
    fun start() {
        scope.launch {
            loadCache()
            refreshNow()
        }
    }

    /** Reads both files again and writes pending changes; calls while a pass runs are folded into one more pass. */
    fun refresh() {
        scope.launch { refreshNow() }
    }

    // ------------------------------------------------------------------ changes

    /**
     * Records what the user did and shows it at once; the file is written right after, or on the
     * next refresh when that fails. Text fields are tidied the way a read would tidy them, so
     * the write can be checked against what the file reads back as.
     */
    fun submit(change: Change, label: String? = null) = submitAll(listOf(change), label)

    /**
     * Records changes made together (a move is an Add and a Remove, a shelf given to many books
     * is many Edits) as one step: shown at once, written right after, undone together. A Remove
     * in the group waits for the group's Add to the other file, so a moved book is never missing
     * from both. [label] is what the undo bar says; null keeps the step out of the undo history.
     */
    fun submitAll(changes: List<Change>, label: String? = null) {
        if (changes.isEmpty()) return
        val now = clock()
        var tidy = changes.map { change ->
            when (change) {
                is Change.Add -> change.copy(book = tidy(change.book), at = now)
                is Change.Edit -> change.copy(after = tidy(change.after), at = now)
                is Change.Remove -> change.copy(at = now)
            }
        }
        val firstAdd = tidy.filterIsInstance<Change.Add>().groupBy { it.owner }.mapValues { it.value.first() }
        tidy = tidy.map { change ->
            if (change is Change.Remove && change.waitFor == null) {
                val add = firstAdd.entries.firstOrNull { it.key != change.owner }?.value
                if (add != null) change.copy(waitFor = add.id) else change
            } else {
                change
            }
        }
        if (label != null) {
            val inverses = inverses(tidy)
            undoStack.addLast(UndoStep(label, inverses))
            while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
            _lastStep.value = LastStep(label, now)
        }
        pending.addAll(tidy)
        if (!links.formatNoticeShown) {
            links.formatNoticeShown = true
            notice(StoreNotice.FirstWrite)
        }
        tidy.map { it.owner }.distinct().forEach(::refreshShown)
        scope.launch {
            rebuildIndex()
            refreshNow()
        }
    }

    /** Takes back the last step (Ctrl+Z, "Geri al"); false when there is nothing to undo. */
    fun undo(): Boolean {
        val step = undoStack.removeLastOrNull() ?: return false
        _lastStep.value = null
        submitAll(step.inverses, label = null)
        return true
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** Forgets the "Geri al" bar (the step can still be undone with Ctrl+Z). */
    fun dismissLastStep() {
        _lastStep.value = null
    }

    /** What undoes [changes], computed against what the screens show right now. */
    private fun inverses(changes: List<Change>): List<Change> {
        val tables = HashMap<Owner, ChangeApplier.Rows?>()
        val result = ArrayList<Change>(changes.size)
        for (change in changes) {
            val rows = tables.getOrPut(change.owner) { state(change.owner).table?.let { ChangeApplier.Rows(it.books) } }
            val inverse = when (change) {
                is Change.Add -> {
                    val existing = rows?.loose(change.book)?.takeIf { it >= 0 }?.let { rows.books[it] }
                    if (existing != null) Change.Edit(change.owner, existing.copy(copies = existing.copies + change.book.copies), existing)
                    else Change.Remove(change.owner, change.book)
                }
                is Change.Edit -> Change.Edit(change.owner, change.after, change.before)
                is Change.Remove -> Change.Add(change.owner, change.before)
            }
            result.add(inverse)
            rows?.apply(change)
        }
        return result.reversed()
    }

    /** Changes still waiting to be written to [owner]'s file. */
    fun pendingChanges(owner: Owner): List<Change> = pending.forOwner(owner)

    /** Copies of [owner]'s file taken right before each write, newest first. */
    fun backups(owner: Owner): List<Backups.Backup> = backups.list(owner)

    fun dismiss(notice: StoreNotice) {
        _notices.value = _notices.value - notice
    }

    // ------------------------------------------------------------------ links

    fun relink(owner: Owner, uri: Uri, displayName: String?) {
        documents.keepPermission(uri)
        links.set(owner, uri, displayName)
        links.setSheetName(owner, null)
        links.setMapping(owner, null)
        _previews.value = _previews.value - owner
        cache.clear(owner)
        hashes.remove(owner)
        baseTables.remove(owner)
        update(LibraryState(owner, LoadStatus.LOADING, displayName = displayName, pendingChanges = pending.forOwner(owner).size))
        refresh()
    }

    fun unlink(owner: Owner) {
        links.clear(owner)
        _previews.value = _previews.value - owner
        cache.clear(owner)
        hashes.remove(owner)
        baseTables.remove(owner)
        update(LibraryState.notLinked(owner).copy(pendingChanges = pending.forOwner(owner).size))
        scope.launch { rebuildIndex() }
    }

    fun chooseSheet(owner: Owner, sheetName: String?) {
        links.setSheetName(owner, sheetName)
        hashes.remove(owner) // forces a parse of the chosen sheet
        refresh()
    }

    fun setMapping(owner: Owner, mapping: ColumnMapping?) {
        links.setMapping(owner, mapping)
        hashes.remove(owner)
        refresh()
    }

    /** The folder next to the files, only used to notice lock files and conflicted copies. */
    fun setTree(uri: Uri?) {
        if (uri != null) documents.keepPermission(uri)
        links.treeUri = uri
        refresh()
    }

    /**
     * Fills a document the user has just created with an empty list (headers only) and links it.
     * [onResult] gets false when the document could not be written.
     */
    fun createEmpty(owner: Owner, uri: Uri, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            val bytes = XlsxWriter.write(listOf(BookTable(emptyList()).toSheetSpec(owner.label)))
            val written = withContext(io) {
                try {
                    documents.writeBytes(uri, bytes)
                    true
                } catch (e: DocumentException) {
                    false
                }
            }
            if (written) relink(owner, uri, null)
            onResult(written)
        }
    }

    // ------------------------------------------------------------------ backups, export, import

    /**
     * Puts a backup back (S-24): the file as it is now is backed up first, then the backup's
     * bytes are written and read back. Changes still pending for that list are dropped, since
     * they were made on top of what is being replaced. [onResult] gets false on any failure.
     */
    fun restore(owner: Owner, backup: java.io.File, onResult: (Boolean) -> Unit = {}) {
        val link = links.link(owner) ?: return onResult(false)
        scope.launch {
            val ok = withContext(io) {
                try {
                    val bytes = backup.readBytes()
                    val current = try {
                        documents.readBytes(link.uri)
                    } catch (e: DocumentException) {
                        null
                    }
                    if (current != null) backups.save(owner, current, clock())
                    documents.writeBytes(link.uri, bytes)
                    LibraryCache.hashOf(documents.readBytes(link.uri)) == LibraryCache.hashOf(bytes)
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                pending.clear(owner)
                hashes.remove(owner)
                refreshNow()
            }
            onResult(ok)
        }
    }

    /** Both lists in one workbook with Babam and Benim sheets (S-25); the app never reads it back. */
    fun exportTo(uri: Uri, onResult: (Boolean) -> Unit = {}) {
        val sheets = Owner.entries.map { owner -> (state(owner).table ?: BookTable(emptyList())).toSheetSpec(owner.label) }
        scope.launch {
            val ok = withContext(io) {
                try {
                    documents.writeBytes(uri, XlsxWriter.write(sheets))
                    true
                } catch (e: DocumentException) {
                    false
                }
            }
            onResult(ok)
        }
    }

    /** Reads a document the user picked for merging; throws [DocumentException]. */
    suspend fun readDocument(uri: Uri): ByteArray = withContext(io) { documents.readBytes(uri) }

    /** How many books a backup holds, read the way the list itself is read; null when unreadable. */
    suspend fun backupBookCount(owner: Owner, backup: java.io.File): Int? = withContext(io) {
        try {
            (parse(owner, backup.readBytes(), backup.name) as? Parsed.Ok)?.table?.books?.size
        } catch (e: java.io.IOException) {
            null
        }
    }

    /** The picked document's name ("Eski liste.xlsx"), when the provider tells it. */
    suspend fun documentName(uri: Uri): String? = withContext(io) { documents.info(uri)?.displayName }

    /** Remembered answers to "is this the same book?" from earlier merges. */
    var mergeDecisions: Map<String, Boolean>
        get() = links.mergeDecisions
        set(value) {
            links.mergeDecisions = value
        }

    /** The user's column mapping for [owner]'s file, if one was made. */
    fun mapping(owner: Owner): ColumnMapping? = links.mapping(owner)

    /** The folder chosen next to the files, if any. */
    val treeUri: Uri? get() = links.treeUri

    fun dismissWarning() {
        val names = _warning.value?.names ?: return
        links.warnedFileNames = links.warnedFileNames + names
        _warning.value = null
    }

    // ------------------------------------------------------------------ reading

    private suspend fun loadCache() {
        for (owner in Owner.entries) {
            val link = links.link(owner) ?: continue
            val entry = withContext(io) { cache.load(owner) }
            if (entry == null) {
                update(LibraryState(owner, LoadStatus.LOADING, displayName = link.displayName, pendingChanges = pending.forOwner(owner).size))
                continue
            }
            when (val parsed = withContext(io) { parse(owner, entry.bytes, entry.meta.displayName ?: link.displayName) }) {
                is Parsed.Ok -> {
                    hashes[owner] = entry.meta.hash
                    setPreview(owner, parsed.preview)
                    setBase(owner, parsed.table)
                    update(
                        LibraryState(
                            owner, LoadStatus.LOADING, shown(owner), fromCache = true, displayName = entry.meta.displayName ?: link.displayName,
                            readAt = entry.meta.readAt, updatedAt = latest(entry.meta.fileModifiedAt, entry.meta.changedAt),
                            problems = parsed.problems, sheetNames = parsed.sheetNames, pendingChanges = pending.forOwner(owner).size,
                        ),
                    )
                }
                is Parsed.Failed -> update(LibraryState(owner, LoadStatus.LOADING, displayName = link.displayName, pendingChanges = pending.forOwner(owner).size))
            }
        }
        rebuildIndex()
    }

    private suspend fun refreshNow() {
        if (refreshing) {
            refreshAgain = true
            return
        }
        refreshing = true
        try {
            do {
                refreshAgain = false
                val pendingBefore = pending.all().size
                for (owner in Owner.entries) {
                    if (readyChanges(owner).isNotEmpty() && links.link(owner) != null) write(owner) else read(owner)
                }
                scanStrayFiles()
                rebuildIndex()
                // a Remove held back for its Add can go once that Add is written
                val released = pending.all().size < pendingBefore && Owner.entries.any { readyChanges(it).isNotEmpty() && links.link(it) != null }
                if (released) refreshAgain = true
            } while (refreshAgain)
        } finally {
            refreshing = false
        }
    }

    private suspend fun read(owner: Owner) {
        val link = links.link(owner)
        if (link == null) {
            hashes.remove(owner)
            baseTables.remove(owner)
            update(LibraryState.notLinked(owner).copy(pendingChanges = pending.forOwner(owner).size))
            return
        }
        val before = state(owner)
        update(before.copy(status = LoadStatus.LOADING, displayName = before.displayName ?: link.displayName))
        val knownHash = hashes[owner]
        val outcome = withContext(io) { fetch(owner, link, knownHash) }
        val now = clock()
        when (outcome) {
            is Fetched.Failed -> update(state(owner).copy(status = LoadStatus.FAILED, failure = outcome.failure))
            is Fetched.Unchanged -> {
                val current = state(owner)
                val updatedAt = latest(current.updatedAt, outcome.info?.lastModified)
                withContext(io) { cache.saveMeta(owner, LibraryCache.Meta(outcome.hash, now, current.updatedAt, outcome.info?.lastModified, outcome.info?.displayName ?: current.displayName)) }
                update(current.copy(status = LoadStatus.READY, fromCache = false, failure = null, readAt = now, updatedAt = updatedAt, displayName = outcome.info?.displayName ?: current.displayName))
            }
            is Fetched.Changed -> when (val parsed = outcome.parsed) {
                is Parsed.Failed -> {
                    setPreview(owner, parsed.preview)
                    update(state(owner).copy(status = LoadStatus.FAILED, failure = parsed.failure, sheetNames = parsed.preview?.filledSheetNames.orEmpty(), fromCache = state(owner).table != null))
                }
                is Parsed.Ok -> {
                    setPreview(owner, parsed.preview)
                    val current = state(owner)
                    val changedAt = if (knownHash != null) now else null // the first read is not a change
                    val updatedAt = latest(latest(current.updatedAt, outcome.info?.lastModified), changedAt)
                    val displayName = outcome.info?.displayName ?: current.displayName ?: link.displayName
                    hashes[owner] = outcome.hash
                    if (displayName != link.displayName && displayName != null) links.set(owner, link.uri, displayName)
                    withContext(io) { cache.save(owner, outcome.bytes, LibraryCache.Meta(outcome.hash, now, changedAt, outcome.info?.lastModified, displayName)) }
                    setBase(owner, parsed.table)
                    update(
                        LibraryState(
                            owner, LoadStatus.READY, shown(owner), fromCache = false, displayName = displayName, readAt = now,
                            updatedAt = updatedAt, problems = parsed.problems, sheetNames = parsed.sheetNames,
                            pendingChanges = pending.forOwner(owner).size, writeFailure = current.writeFailure,
                        ),
                    )
                }
            }
        }
    }

    private sealed class Fetched {
        class Failed(val failure: LoadFailure) : Fetched()
        class Unchanged(val hash: String, val info: DocumentInfo?) : Fetched()
        class Changed(val bytes: ByteArray, val hash: String, val info: DocumentInfo?, val parsed: Parsed) : Fetched()
    }

    private sealed class Parsed {
        class Ok(val table: BookTable, val problems: List<BookTable.Problem>, val sheetNames: List<String>, val preview: WorkbookPreview, val workbook: Workbook, val sheetName: String) : Parsed()
        class Failed(val failure: LoadFailure, val preview: WorkbookPreview? = null) : Parsed()
    }

    /** On the IO thread: the bytes, their hash, and the parsed table when the bytes are new. */
    private fun fetch(owner: Owner, link: FileLinks.Link, knownHash: String?): Fetched {
        val bytes = try {
            documents.readBytes(link.uri)
        } catch (e: DocumentException) {
            return Fetched.Failed(loadFailure(e))
        }
        val info = documents.info(link.uri)
        val hash = LibraryCache.hashOf(bytes)
        if (hash == knownHash) return Fetched.Unchanged(hash, info)
        return Fetched.Changed(bytes, hash, info, parse(owner, bytes, info?.displayName ?: link.displayName))
    }

    private fun parse(owner: Owner, bytes: ByteArray, displayName: String?): Parsed {
        val workbook = try {
            SpreadsheetReader.read(bytes, displayName)
        } catch (e: SpreadsheetException) {
            return Parsed.Failed(
                when (e.reason) {
                    SpreadsheetException.Reason.OLD_XLS -> LoadFailure.OLD_XLS
                    SpreadsheetException.Reason.NOT_A_SPREADSHEET -> LoadFailure.NOT_A_SPREADSHEET
                    SpreadsheetException.Reason.DAMAGED -> LoadFailure.DAMAGED
                    SpreadsheetException.Reason.TOO_LARGE -> LoadFailure.TOO_LARGE
                },
            )
        }
        val filled = workbook.filledSheets
        val wanted = links.sheetName(owner)
        val sheet = filled.firstOrNull { it.name == wanted } ?: filled.firstOrNull() ?: workbook.sheets.firstOrNull()
            ?: return Parsed.Failed(LoadFailure.NO_HEADER)
        val preview = WorkbookPreview(
            sheets = (filled.ifEmpty { listOf(sheet) }).map { Sheet(it.name, it.rows.take(WorkbookPreview.ROWS)) },
            selected = sheet.name,
        )
        if (sheet.isEmpty) return Parsed.Ok(BookTable(emptyList()), emptyList(), preview.filledSheetNames, preview, workbook, sheet.name)
        val parsed = BookTable.parse(sheet, links.mapping(owner)) ?: return Parsed.Failed(LoadFailure.NO_HEADER, preview)
        return Parsed.Ok(parsed.table, parsed.problems, preview.filledSheetNames, preview, workbook, sheet.name)
    }

    /** A document with nothing in it yet is an empty list on a sheet named after its owner. */
    private fun emptyParsed(owner: Owner): Parsed.Ok {
        val sheet = Sheet(owner.label, emptyList())
        return Parsed.Ok(BookTable(emptyList()), emptyList(), emptyList(), WorkbookPreview(listOf(sheet), sheet.name), Workbook(listOf(sheet)), sheet.name)
    }

    // ------------------------------------------------------------------ writing

    private sealed class Written {
        class Ok(
            val bytes: ByteArray,
            val hash: String,
            val parsed: Parsed.Ok,
            val info: DocumentInfo?,
            val applied: ChangeApplier.Result,
            val otherSheets: List<String>,
        ) : Written()

        class Failed(val failure: WriteFailure, val detail: LoadFailure? = null) : Written()
    }

    /**
     * ARCHITECTURE 7: read the file again, apply the pending changes to what it holds now, build
     * the new workbook and check it reads back as intended, keep a backup of the old bytes,
     * write, read the document back and compare. Only then are the changes dropped.
     */
    /** [owner]'s pending changes up to the first one still waiting for another file. */
    private fun readyChanges(owner: Owner): List<Change> {
        val all = pending.all()
        val ids = all.mapTo(HashSet()) { it.id }
        return all.filter { it.owner == owner }.takeWhile { !(it is Change.Remove && it.waitFor != null && it.waitFor in ids) }
    }

    private suspend fun write(owner: Owner) {
        val link = links.link(owner) ?: return
        val changes = readyChanges(owner)
        if (changes.isEmpty()) return
        val before = state(owner)
        update(before.copy(status = LoadStatus.LOADING, displayName = before.displayName ?: link.displayName, pendingChanges = changes.size))
        val now = clock()
        val outcome = withContext(io) { commit(owner, link, changes, now) }
        when (outcome) {
            is Written.Failed -> {
                val current = state(owner)
                // a file that cannot even be read shows that too
                val failure = outcome.detail
                update(
                    current.copy(
                        status = if (failure != null) LoadStatus.FAILED else if (current.table != null) LoadStatus.READY else LoadStatus.FAILED,
                        failure = failure ?: current.failure,
                        writeFailure = outcome.failure,
                        pendingChanges = changes.size,
                    ),
                )
            }
            is Written.Ok -> {
                pending.remove(changes.map { it.id })
                val current = state(owner)
                val displayName = outcome.info?.displayName ?: current.displayName ?: link.displayName
                hashes[owner] = outcome.hash
                withContext(io) { cache.save(owner, outcome.bytes, LibraryCache.Meta(outcome.hash, now, now, outcome.info?.lastModified, displayName)) }
                setPreview(owner, outcome.parsed.preview)
                setBase(owner, outcome.applied.table)
                update(
                    LibraryState(
                        owner, LoadStatus.READY, shown(owner), fromCache = false, displayName = displayName, readAt = now,
                        updatedAt = now, problems = outcome.parsed.problems, sheetNames = outcome.parsed.sheetNames,
                        pendingChanges = pending.forOwner(owner).size, writeFailure = null,
                    ),
                )
                if (outcome.applied.surprises.isNotEmpty()) notice(StoreNotice.ChangesLanded(owner, outcome.applied.surprises))
                if (outcome.otherSheets.isNotEmpty() && otherSheetsWarned.add(owner)) notice(StoreNotice.OtherSheetsKept(owner, outcome.otherSheets))
            }
        }
    }

    /** On the IO thread. */
    private fun commit(owner: Owner, link: FileLinks.Link, changes: List<Change>, now: Long): Written {
        // 1. the file as it is right now
        val original = try {
            documents.readBytes(link.uri)
        } catch (e: DocumentException) {
            return Written.Failed(WriteFailure.CANNOT_READ, loadFailure(e))
        }
        val info = documents.info(link.uri)
        when (FileKinds.detect(original)) {
            FileKind.XLSX -> Unit
            FileKind.XLS, FileKind.CSV -> return Written.Failed(WriteFailure.NOT_XLSX)
            FileKind.UNKNOWN -> if (original.isNotEmpty()) return Written.Failed(WriteFailure.CANNOT_READ, LoadFailure.DAMAGED)
        }
        val parsed = if (original.isEmpty()) {
            emptyParsed(owner)
        } else {
            when (val p = parse(owner, original, info?.displayName ?: link.displayName)) {
                is Parsed.Ok -> p
                is Parsed.Failed -> return Written.Failed(WriteFailure.CANNOT_READ, p.failure)
            }
        }
        // 2. the changes on top of that
        val applied = ChangeApplier.apply(parsed.table, changes)
        // 3. the new workbook, checked by reading it back before anything is touched
        val sheets = parsed.workbook.sheets.map { sheet ->
            if (sheet.name == parsed.sheetName) applied.table.toSheetSpec(sheet.name) else SheetSpec.plain(sheet.name, sheet.rows)
        }
        val bytes = XlsxWriter.write(sheets)
        if (!readsBackAs(bytes, parsed.sheetName, applied.table)) return Written.Failed(WriteFailure.VERIFY)
        val otherSheets = parsed.workbook.sheets.filter { it.name != parsed.sheetName && !it.isEmpty }.map { it.name }
        // 4. the old bytes are kept
        try {
            backups.save(owner, original, now)
        } catch (e: Exception) {
            return Written.Failed(WriteFailure.BACKUP)
        }
        // 5. the write itself
        try {
            documents.writeBytes(link.uri, bytes)
        } catch (e: DocumentException) {
            return Written.Failed(if (e.failure == DocumentFailure.NO_PERMISSION) WriteFailure.NO_PERMISSION else WriteFailure.CANNOT_WRITE)
        }
        // 6. what the document holds now
        val readBack = try {
            documents.readBytes(link.uri)
        } catch (e: DocumentException) {
            return Written.Failed(WriteFailure.READ_BACK)
        }
        val hash = LibraryCache.hashOf(bytes)
        if (LibraryCache.hashOf(readBack) != hash && !readsBackAs(readBack, parsed.sheetName, applied.table)) {
            return Written.Failed(WriteFailure.READ_BACK)
        }
        return Written.Ok(bytes, hash, parsed, info, applied, otherSheets)
    }

    /** True when [bytes] hold [table] on sheet [sheetName] the way the app's own reader sees them. */
    private fun readsBackAs(bytes: ByteArray, sheetName: String, table: BookTable): Boolean {
        val workbook = try {
            XlsxReader.read(bytes.inputStream())
        } catch (e: SpreadsheetException) {
            return false
        }
        val sheet = workbook.sheets.firstOrNull { it.name == sheetName } ?: return false
        val parsed = BookTable.parse(sheet) ?: return false
        return parsed.table.books == table.books && parsed.table.extraHeaders == table.extraHeaders
    }

    // ------------------------------------------------------------------ index and stray files

    private suspend fun rebuildIndex() {
        val tables = Owner.entries.associateWith { state(it).table }
        if (indexed.isNotEmpty() && Owner.entries.all { indexed[it] === tables[it] }) return
        val built = withContext(io) { SearchIndex.build(tables.mapValues { it.value?.books.orEmpty() }) }
        indexed.clear()
        indexed.putAll(tables)
        _index.value = built
    }

    private suspend fun scanStrayFiles() {
        val tree = links.treeUri ?: return
        val names = withContext(io) { documents.childNames(tree) }.filter(::isStrayFile)
        val fresh = names.filter { it !in links.warnedFileNames }
        if (fresh.isNotEmpty()) _warning.value = StrayFileWarning(fresh)
    }

    // ------------------------------------------------------------------ helpers

    private fun setBase(owner: Owner, table: BookTable?) {
        baseTables[owner] = table
    }

    /** The file's table with the pending changes applied: what the screens show. */
    private fun shown(owner: Owner): BookTable? = ChangeApplier.preview(baseTables[owner], pending.forOwner(owner))

    private fun refreshShown(owner: Owner) {
        val current = state(owner)
        update(current.copy(table = shown(owner) ?: current.table, pendingChanges = pending.forOwner(owner).size))
    }

    private fun notice(notice: StoreNotice) {
        _notices.value = _notices.value + notice
    }

    private fun setPreview(owner: Owner, preview: WorkbookPreview?) {
        _previews.value = if (preview == null) _previews.value - owner else _previews.value + (owner to preview)
    }

    private fun update(state: LibraryState) {
        _states.value = _states.value + (state.owner to state)
    }

    companion object {
        const val UNDO_LIMIT = 50

        /** Excel's lock file, Drive's and Dropbox's conflicted copies. */
        fun isStrayFile(name: String): Boolean {
            val lower = name.lowercase()
            return name.startsWith("~$") || "çakışan kopya" in lower || "conflicted copy" in lower || "çakisan kopya" in lower
        }

        /** Text fields trimmed and squeezed, as a read would deliver them. */
        fun tidy(book: Book): Book = book.copy(
            title = TurkishText.squeeze(book.title),
            author = TurkishText.squeeze(book.author),
            shelf = TurkishText.squeeze(book.shelf),
            copies = book.copies.coerceAtLeast(1),
            publisher = TurkishText.squeeze(book.publisher),
            year = TurkishText.squeeze(book.year),
            isbn = TurkishText.squeeze(book.isbn),
            note = TurkishText.squeeze(book.note),
        )

        private fun loadFailure(e: DocumentException): LoadFailure = when (e.failure) {
            DocumentFailure.NOT_FOUND -> LoadFailure.NOT_FOUND
            DocumentFailure.NO_PERMISSION -> LoadFailure.NO_PERMISSION
            DocumentFailure.IO -> LoadFailure.IO
            DocumentFailure.TOO_LARGE -> LoadFailure.TOO_LARGE
        }

        private fun latest(a: Long?, b: Long?): Long? = when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }
    }
}
