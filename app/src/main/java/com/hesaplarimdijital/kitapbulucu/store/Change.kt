package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * What the user did, kept as an operation rather than a snapshot (ARCHITECTURE 7), so it can
 * be applied to the file as it is at writing time, after someone else may have changed it.
 * A move is an [Add] to the target and, once that is written, a [Remove] from the source.
 */
sealed interface Change {
    val owner: Owner
    val id: String
    val at: Long

    data class Add(override val owner: Owner, val book: Book, override val id: String = newId(), override val at: Long = 0L) : Change
    data class Edit(override val owner: Owner, val before: Book, val after: Book, override val id: String = newId(), override val at: Long = 0L) : Change
    data class Remove(override val owner: Owner, val before: Book, override val id: String = newId(), override val at: Long = 0L) : Change

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/** Changes and books as JSON, for the pending file. */
object ChangeJson {
    fun toJson(change: Change): JSONObject {
        val json = JSONObject().put("id", change.id).put("owner", change.owner.name).put("at", change.at)
        when (change) {
            is Change.Add -> json.put("type", "add").put("book", book(change.book))
            is Change.Edit -> json.put("type", "edit").put("before", book(change.before)).put("after", book(change.after))
            is Change.Remove -> json.put("type", "remove").put("before", book(change.before))
        }
        return json
    }

    /** Null for a record this version does not understand; the rest of the file still loads. */
    fun fromJson(json: JSONObject): Change? {
        val owner = Owner.entries.firstOrNull { it.name == json.optString("owner") } ?: return null
        val id = json.optString("id").ifEmpty { return null }
        val at = json.optLong("at", 0L)
        return when (json.optString("type")) {
            "add" -> Change.Add(owner, book(json.optJSONObject("book") ?: return null), id, at)
            "edit" -> Change.Edit(owner, book(json.optJSONObject("before") ?: return null), book(json.optJSONObject("after") ?: return null), id, at)
            "remove" -> Change.Remove(owner, book(json.optJSONObject("before") ?: return null), id, at)
            else -> null
        }
    }

    fun book(book: Book): JSONObject = JSONObject()
        .put("title", book.title)
        .put("author", book.author)
        .put("shelf", book.shelf)
        .put("copies", book.copies)
        .put("signed", book.signed)
        .put("publisher", book.publisher)
        .put("year", book.year)
        .put("isbn", book.isbn)
        .put("note", book.note)
        .put("extra", JSONArray(book.extra))

    fun book(json: JSONObject): Book {
        val extra = json.optJSONArray("extra") ?: JSONArray()
        return Book(
            title = json.optString("title"),
            author = json.optString("author"),
            shelf = json.optString("shelf"),
            copies = json.optInt("copies", 1).coerceAtLeast(1),
            signed = json.optBoolean("signed", false),
            publisher = json.optString("publisher"),
            year = json.optString("year"),
            isbn = json.optString("isbn"),
            note = json.optString("note"),
            extra = List(extra.length()) { extra.optString(it) },
        )
    }
}
