package com.hesaplarimdijital.kitapbulucu.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.model.Shelves
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.Change
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.store.ShelfPlacement
import com.hesaplarimdijital.kitapbulucu.ui.shelves.ShelfFormDialog
import com.hesaplarimdijital.kitapbulucu.ui.shelves.ShelfPickerDialog

/**
 * Delete, move, edit and shelving for any screen (S-18, S-19, S-34, S-35): asks first, then
 * hands the changes to the store as one undoable step. Screens call [delete], [move], [edit],
 * [placeOnShelf] or [putOnShelf]; [Dialogs] shows the question or shelf picker that is open.
 */
class BookActions internal constructor(
    private val store: LibraryStore,
    private val settings: AppSettings?,
    private val resources: Resources,
    private val onEdit: (Owner, Book) -> Unit,
) {
    private var question by mutableStateOf<Question?>(null)

    private class Question(val title: String, val text: String, val actions: List<DialogAction>)

    fun delete(owner: Owner, book: Book) {
        val actions = ArrayList<DialogAction>()
        if (book.copies > 1) {
            actions.add(
                DialogAction(resources.getString(R.string.delete_one_copy, book.copies - 1), primary = true) {
                    store.submit(Change.Edit(owner, book, book.copy(copies = book.copies - 1)), resources.getString(R.string.undo_one_copy, book.title))
                },
            )
            actions.add(DialogAction(resources.getString(R.string.delete_all, book.copies)) { remove(owner, book) })
        } else {
            actions.add(DialogAction(resources.getString(R.string.delete_confirm), primary = true) { remove(owner, book) })
        }
        actions.add(DialogAction(resources.getString(R.string.action_cancel)) {})
        question = Question(
            resources.getString(R.string.delete_title, book.title),
            resources.getString(R.string.delete_text, from(owner)) + otherWarning(owner),
            actions,
        )
    }

    fun move(owner: Owner, book: Book) {
        val target = other(owner)
        question = Question(
            resources.getString(R.string.move_title, book.title),
            resources.getString(R.string.move_text, to(target), from(owner)) + otherWarning(owner, target),
            listOf(
                DialogAction(resources.getString(R.string.move_confirm), primary = true) { moveNow(owner, book, target, book) },
                DialogAction(resources.getString(R.string.action_cancel)) {},
            ),
        )
    }

    fun edit(owner: Owner, book: Book) = onEdit(owner, book)

    /**
     * Saves an edit made on the form. Same list: one Edit. Another list: the new values go to
     * the other file first, then the old row leaves (a move). The other person's list asks first.
     */
    fun saveEdit(fromOwner: Owner, before: Book, toOwner: Owner, after: Book, onSaved: () -> Unit) {
        val apply = {
            if (fromOwner == toOwner) {
                store.submit(Change.Edit(fromOwner, before, after), resources.getString(R.string.undo_edited, after.title))
            } else {
                moveNow(fromOwner, before, toOwner, after)
            }
            onSaved()
        }
        val me = settings?.me
        val touchesOther = me != null && (fromOwner != me || toOwner != me)
        if (!touchesOther) {
            apply()
            return
        }
        val other = if (fromOwner != me) fromOwner else toOwner
        question = Question(
            after.title,
            resources.getString(R.string.other_list_warning, LibraryTexts.listName(resources, other)),
            listOf(DialogAction(resources.getString(R.string.add_confirm), primary = true) { apply() }, DialogAction(resources.getString(R.string.action_cancel)) {}),
        )
    }

    // ------------------------------------------------------------------ shelves (S-34, S-35)

    private class Picking(val owner: Owner, val books: List<Book>, val creating: Boolean = false, val code: String = "")

    private var picking by mutableStateOf<Picking?>(null)

    /** Lists whose owner already said yes to shelf changes in this session: filling a shelf asks once. */
    private val shelfOtherConfirmed = HashSet<Owner>()

    /**
     * "Rafa koy…": lets the user choose one of [owner]'s shelves (or make a new one) and puts all
     * [books] of that list on it as one undoable step. Takes several books, for a multi-row
     * selection in the library table.
     */
    fun placeOnShelf(owner: Owner, books: List<Book>) {
        if (books.isEmpty()) return
        picking = Picking(owner, books)
    }

    /**
     * Puts [books] of [owner]'s list on [shelf] as one undoable step. Asks first when [askMove]
     * and a book stands on another shelf ("B2 rafından B3 rafına taşınsın mı?"), when [confirm]
     * (many books at once) and when the list is the other person's (once per session).
     * [onPlaced] gets the sentence to show once the change is submitted (or the book was there).
     */
    fun putOnShelf(owner: Owner, books: List<Book>, shelf: String, askMove: Boolean = true, confirm: Boolean = false, onPlaced: (String) -> Unit = {}) {
        val plan = ShelfPlacement.plan(owner, books, shelf)
        val target = plan.shelf
        if (plan.changes.isEmpty()) {
            books.singleOrNull()?.let { onPlaced(resources.getString(R.string.shelf_already, it.title, target)) }
            return
        }
        val count = plan.changes.size
        val label = if (count == 1) resources.getString(R.string.shelf_placed, plan.changes[0].after.title, target)
        else resources.getString(R.string.undo_shelf, count, target)
        val me = settings?.me
        val needOther = me != null && owner != me && owner !in shelfOtherConfirmed
        val apply = {
            if (needOther) shelfOtherConfirmed.add(owner)
            store.submitAll(plan.changes, label)
            onPlaced(label)
        }
        val moving = if (askMove) plan.moving else emptyList()
        if (!confirm && moving.isEmpty() && !needOther) {
            apply()
            return
        }
        val oneMove = count == 1 && moving.size == 1
        val title: String
        val text = StringBuilder()
        when {
            oneMove -> {
                title = resources.getString(R.string.shelf_move_title, moving[0].shelf, target)
                text.append(resources.getString(R.string.shelf_move_text, moving[0].title, moving[0].shelf))
            }
            confirm || count > 1 -> {
                title = resources.getString(R.string.fill_assign_all_title, count, target)
                text.append(resources.getString(R.string.shelf_put_text, target))
                if (moving.isNotEmpty()) {
                    text.append(" ").append(resources.getString(R.string.shelf_move_many, moving.size, plan.fromShelves.joinToString(", "), target))
                }
            }
            else -> title = resources.getString(R.string.shelf_put_title, plan.changes[0].after.title, target)
        }
        if (needOther) {
            if (text.isNotEmpty()) text.append("\n\n")
            text.append(resources.getString(R.string.other_list_warning, LibraryTexts.listName(resources, owner)))
        }
        question = Question(
            title,
            text.toString(),
            listOf(
                DialogAction(resources.getString(if (oneMove) R.string.move_confirm else R.string.shelf_put_confirm), primary = true) { apply() },
                DialogAction(resources.getString(R.string.action_cancel)) {},
            ),
        )
    }

    private fun subject(books: List<Book>): String {
        val one = books.singleOrNull() ?: return resources.getString(R.string.picker_many, books.size)
        return if (one.shelf.isBlank()) resources.getString(R.string.picker_one_none, one.title)
        else resources.getString(R.string.picker_one, one.title, one.shelf)
    }

    @Composable
    private fun Picker(pick: Picking) {
        val states by store.states.collectAsState()
        val catalog by store.shelfCatalog.entries.collectAsState()
        val books = states.getValue(pick.owner).table?.books.orEmpty()
        val saved = catalog[pick.owner].orEmpty()
        val shelves = remember(books, saved) { Shelves.overview(books, saved).filterNot { it.isUnshelved } }
        if (pick.creating) {
            val what = pick.books.singleOrNull()?.let { "“" + it.title + "”" } ?: resources.getString(R.string.picker_many, pick.books.size)
            ShelfFormDialog(
                title = resources.getString(R.string.shelves_new),
                existing = shelves.map { it.shelf },
                text = resources.getString(R.string.picker_new_text, what),
                initialCode = pick.code,
                onSave = { code, note ->
                    store.shelfCatalog.add(pick.owner, code, note)
                    putOnShelf(pick.owner, pick.books, code, askMove = false)
                },
                onDismiss = { picking = null },
            )
        } else {
            ShelfPickerDialog(
                title = resources.getString(R.string.picker_title),
                subtitle = subject(pick.books),
                shelves = shelves,
                current = pick.books.singleOrNull()?.shelf,
                onPick = { code -> putOnShelf(pick.owner, pick.books, code, askMove = false) },
                onNew = { code -> picking = Picking(pick.owner, pick.books, creating = true, code = code) },
                onDismiss = { picking = null },
            )
        }
    }

    private fun remove(owner: Owner, book: Book) {
        store.submit(Change.Remove(owner, book), resources.getString(R.string.undo_deleted, book.title))
    }

    private fun moveNow(fromOwner: Owner, before: Book, toOwner: Owner, after: Book) {
        val add = Change.Add(toOwner, after)
        store.submitAll(listOf(add, Change.Remove(fromOwner, before, waitFor = add.id)), resources.getString(R.string.undo_moved, after.title))
    }

    private fun otherWarning(vararg owners: Owner): String {
        val me = settings?.me ?: return ""
        val other = owners.firstOrNull { it != me } ?: return ""
        return "\n\n" + resources.getString(R.string.other_list_warning, LibraryTexts.listName(resources, other))
    }

    private fun from(owner: Owner) = resources.getString(if (owner == Owner.DAD) R.string.from_dad else R.string.from_me)
    private fun to(owner: Owner) = resources.getString(if (owner == Owner.DAD) R.string.list_to_dad else R.string.list_to_me)
    private fun other(owner: Owner) = if (owner == Owner.DAD) Owner.ME else Owner.DAD

    @Composable
    fun Dialogs() {
        val open = question
        if (open != null) {
            ConfirmDialog(open.title, open.text, open.actions, onDismiss = { question = null })
            return
        }
        picking?.let { Picker(it) }
    }
}

@Composable
fun rememberBookActions(store: LibraryStore, settings: AppSettings?, onEdit: (Owner, Book) -> Unit): BookActions {
    val resources = LocalContext.current.resources
    return remember(store, settings) { BookActions(store, settings, resources, onEdit) }
}
