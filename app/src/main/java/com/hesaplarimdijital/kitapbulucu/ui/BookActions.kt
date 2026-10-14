package com.hesaplarimdijital.kitapbulucu.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.Change
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore

/**
 * Delete, move and edit for any screen (S-18, S-19): asks first, then hands the changes to the
 * store as one undoable step. Screens call [delete], [move] or [edit]; [Dialogs] shows the
 * question that is open.
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
        val open = question ?: return
        ConfirmDialog(open.title, open.text, open.actions, onDismiss = { question = null })
    }
}

@Composable
fun rememberBookActions(store: LibraryStore, settings: AppSettings?, onEdit: (Owner, Book) -> Unit): BookActions {
    val resources = LocalContext.current.resources
    return remember(store, settings) { BookActions(store, settings, resources, onEdit) }
}
