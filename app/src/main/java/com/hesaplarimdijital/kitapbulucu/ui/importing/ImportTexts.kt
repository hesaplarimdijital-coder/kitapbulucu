package com.hesaplarimdijital.kitapbulucu.ui.importing

import android.content.res.Resources
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.importing.MergePlan
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookColumn

/** The sentences of the merge screen, kept apart so they are tested without a screen. */
object ImportTexts {

    /** "47 kitap eklendi, 3 kitap güncellendi, 0 kitap silindi." */
    fun result(resources: Resources, result: MergePlan.Result): String =
        resources.getString(R.string.import_result, result.added, result.changed, result.removed)

    fun preview(resources: Resources, result: MergePlan.Result): String =
        resources.getString(R.string.import_preview, result.added, result.changed, result.removed)

    fun summary(resources: Resources, plan: MergePlan.Plan): String {
        val counts = resources.getString(R.string.import_summary, plan.added.size, plan.changed.size, plan.missing.size, plan.ambiguous.size)
        return if (plan.mergedDuplicates > 0) counts + " " + resources.getString(R.string.import_duplicates, plan.mergedDuplicates) else counts
    }

    /** "Raf: A-1 → B-2"; an empty value reads "(boş)", the signed mark "Evet" / "Hayır". */
    fun field(resources: Resources, change: MergePlan.FieldChange): String {
        fun value(text: String): String = when {
            change.column == BookColumn.SIGNED -> resources.getString(if (text.isNotEmpty()) R.string.add_yes else R.string.add_no)
            text.isBlank() -> resources.getString(R.string.import_empty_value)
            else -> text
        }
        return "${change.column.header}: ${value(change.old)} → ${value(change.new)}"
    }

    /** "Yazar · Raf A-1 · 2 adet · İmzalı": what tells a book apart in a list of titles. */
    fun details(resources: Resources, book: Book): String = listOfNotNull(
        book.author.takeIf { it.isNotBlank() },
        book.shelf.takeIf { it.isNotBlank() }?.let { resources.getString(R.string.search_shelf) + " " + it },
        if (book.copies > 1) resources.getString(R.string.import_copies, book.copies) else null,
        if (book.signed) BookColumn.SIGNED.header else null,
    ).joinToString(" · ")

    /** "Suç ve Ceza — Dostoyevski · Raf A-1" for the questions. */
    fun short(resources: Resources, book: Book): String {
        val details = details(resources, book)
        return if (details.isEmpty()) book.title else book.title + " — " + details
    }
}
