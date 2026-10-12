package com.hesaplarimdijital.kitapbulucu.store

import android.net.Uri
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.search.SearchIndex
import com.hesaplarimdijital.kitapbulucu.xlsx.SpreadsheetException
import com.hesaplarimdijital.kitapbulucu.xlsx.SpreadsheetReader
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
 * foreground and on F5). A read that finds the same bytes changes nothing on screen; a read
 * that fails keeps the last good list and says why. State changes happen on [scope]'s thread.
 */
class LibraryStore(
    private val links: FileLinks,
    private val documents: Documents,
    private val cache: LibraryCache,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _states = MutableStateFlow(Owner.entries.associateWith { LibraryState.notLinked(it) })
    val states: StateFlow<Map<Owner, LibraryState>> = _states.asStateFlow()

    private val _index = MutableStateFlow<SearchIndex?>(null)
    /** Search over both libraries; null until anything was read. */
    val index: StateFlow<SearchIndex?> = _index.asStateFlow()

    private val _warning = MutableStateFlow<StrayFileWarning?>(null)
    val warning: StateFlow<StrayFileWarning?> = _warning.asStateFlow()

    /** Hash of the bytes behind each shown table. */
    private val hashes = HashMap<Owner, String>()
    private val indexed = HashMap<Owner, BookTable?>()
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

    /** Reads both files again; calls while a read runs are folded into one more pass. */
    fun refresh() {
        scope.launch { refreshNow() }
    }

    fun relink(owner: Owner, uri: Uri, displayName: String?) {
        links.set(owner, uri, displayName)
        links.setSheetName(owner, null)
        cache.clear(owner)
        hashes.remove(owner)
        update(LibraryState(owner, LoadStatus.LOADING, displayName = displayName))
        refresh()
    }

    fun unlink(owner: Owner) {
        links.clear(owner)
        cache.clear(owner)
        hashes.remove(owner)
        update(LibraryState.notLinked(owner))
        scope.launch { rebuildIndex() }
    }

    fun chooseSheet(owner: Owner, sheetName: String?) {
        links.setSheetName(owner, sheetName)
        hashes.remove(owner) // forces a parse of the chosen sheet
        refresh()
    }

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
                update(LibraryState(owner, LoadStatus.LOADING, displayName = link.displayName))
                continue
            }
            when (val parsed = withContext(io) { parse(owner, entry.bytes, entry.meta.displayName ?: link.displayName) }) {
                is Parsed.Ok -> {
                    hashes[owner] = entry.meta.hash
                    update(
                        LibraryState(
                            owner, LoadStatus.LOADING, parsed.table, fromCache = true, displayName = entry.meta.displayName ?: link.displayName,
                            readAt = entry.meta.readAt, updatedAt = latest(entry.meta.fileModifiedAt, entry.meta.changedAt),
                            problems = parsed.problems, sheetNames = parsed.sheetNames,
                        ),
                    )
                }
                is Parsed.Failed -> update(LibraryState(owner, LoadStatus.LOADING, displayName = link.displayName))
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
                for (owner in Owner.entries) read(owner)
                scanStrayFiles()
                rebuildIndex()
            } while (refreshAgain)
        } finally {
            refreshing = false
        }
    }

    private suspend fun read(owner: Owner) {
        val link = links.link(owner)
        if (link == null) {
            hashes.remove(owner)
            update(LibraryState.notLinked(owner))
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
                is Parsed.Failed -> update(state(owner).copy(status = LoadStatus.FAILED, failure = parsed.failure))
                is Parsed.Ok -> {
                    val current = state(owner)
                    val changedAt = if (knownHash != null) now else null // the first read is not a change
                    val updatedAt = latest(latest(current.updatedAt, outcome.info?.lastModified), changedAt)
                    val displayName = outcome.info?.displayName ?: current.displayName ?: link.displayName
                    hashes[owner] = outcome.hash
                    withContext(io) { cache.save(owner, outcome.bytes, LibraryCache.Meta(outcome.hash, now, changedAt, outcome.info?.lastModified, displayName)) }
                    update(
                        LibraryState(
                            owner, LoadStatus.READY, parsed.table, fromCache = false, displayName = displayName, readAt = now,
                            updatedAt = updatedAt, problems = parsed.problems, sheetNames = parsed.sheetNames,
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
        class Ok(val table: BookTable, val problems: List<BookTable.Problem>, val sheetNames: List<String>) : Parsed()
        class Failed(val failure: LoadFailure) : Parsed()
    }

    /** On the IO thread: the bytes, their hash, and the parsed table when the bytes are new. */
    private fun fetch(owner: Owner, link: FileLinks.Link, knownHash: String?): Fetched {
        val bytes = try {
            documents.readBytes(link.uri)
        } catch (e: DocumentException) {
            return Fetched.Failed(
                when (e.failure) {
                    DocumentFailure.NOT_FOUND -> LoadFailure.NOT_FOUND
                    DocumentFailure.NO_PERMISSION -> LoadFailure.NO_PERMISSION
                    DocumentFailure.IO -> LoadFailure.IO
                    DocumentFailure.TOO_LARGE -> LoadFailure.TOO_LARGE
                },
            )
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
        val parsed = BookTable.parse(sheet) ?: return Parsed.Failed(LoadFailure.NO_HEADER)
        return Parsed.Ok(parsed.table, parsed.problems, if (filled.size > 1) filled.map { it.name } else emptyList())
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

    private fun update(state: LibraryState) {
        _states.value = _states.value + (state.owner to state)
    }

    companion object {
        /** Excel's lock file, Drive's and Dropbox's conflicted copies. */
        fun isStrayFile(name: String): Boolean {
            val lower = name.lowercase()
            return name.startsWith("~$") || "çakışan kopya" in lower || "conflicted copy" in lower || "çakisan kopya" in lower
        }

        private fun latest(a: Long?, b: Long?): Long? = when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }
    }
}
