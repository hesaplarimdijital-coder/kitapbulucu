package com.hesaplarimdijital.kitapbulucu.ui

import android.content.res.Resources
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.Change
import com.hesaplarimdijital.kitapbulucu.store.ChangeApplier
import com.hesaplarimdijital.kitapbulucu.store.LibraryState
import com.hesaplarimdijital.kitapbulucu.store.LoadFailure
import com.hesaplarimdijital.kitapbulucu.store.LoadStatus
import com.hesaplarimdijital.kitapbulucu.store.RelativeTime
import com.hesaplarimdijital.kitapbulucu.store.WriteFailure
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The sentences the screens show about a library's state. */
object LibraryTexts {

    fun ownerLabel(resources: Resources, owner: Owner): String = when (owner) {
        Owner.DAD -> resources.getString(R.string.owner_dad)
        Owner.ME -> resources.getString(R.string.owner_me)
    }

    fun listName(resources: Resources, owner: Owner): String = when (owner) {
        Owner.DAD -> resources.getString(R.string.list_of_dad)
        Owner.ME -> resources.getString(R.string.list_of_me)
    }

    /** "MKE'nin listesi 3 dk önce güncellendi". */
    fun updated(resources: Resources, owner: Owner, state: LibraryState, now: Long): String {
        val list = listName(resources, owner)
        val at = state.updatedAt ?: state.readAt ?: return resources.getString(R.string.read_never, list)
        return when (val time = RelativeTime.of(at, now)) {
            RelativeTime.JustNow -> resources.getString(R.string.updated_just_now, list)
            is RelativeTime.Minutes -> resources.getString(R.string.updated_minutes_ago, list, time.minutes)
            is RelativeTime.Hours -> resources.getString(R.string.updated_hours_ago, list, time.hours)
            RelativeTime.Yesterday -> resources.getString(R.string.updated_yesterday, list)
            is RelativeTime.Days -> resources.getString(R.string.updated_days_ago, list, time.days)
            is RelativeTime.OnDate -> resources.getString(R.string.updated_on_date, list, dateText(time.at))
        }
    }

    /** One line about where the list stands: book count, reading, or what went wrong. */
    fun status(resources: Resources, state: LibraryState): String = when (state.status) {
        LoadStatus.NOT_LINKED -> resources.getString(R.string.status_not_linked)
        LoadStatus.LOADING -> if (state.table != null) resources.getString(R.string.status_from_cache) else resources.getString(R.string.status_loading)
        LoadStatus.READY -> resources.getString(R.string.status_books, state.bookCount)
        LoadStatus.FAILED -> failure(resources, state.failure ?: LoadFailure.IO)
    }

    fun failure(resources: Resources, failure: LoadFailure): String = resources.getString(
        when (failure) {
            LoadFailure.NOT_FOUND -> R.string.failure_not_found
            LoadFailure.NO_PERMISSION -> R.string.failure_no_permission
            LoadFailure.IO -> R.string.failure_io
            LoadFailure.TOO_LARGE -> R.string.failure_too_large
            LoadFailure.OLD_XLS -> R.string.failure_old_xls
            LoadFailure.NOT_A_SPREADSHEET -> R.string.failure_not_a_spreadsheet
            LoadFailure.DAMAGED -> R.string.failure_damaged
            LoadFailure.NO_HEADER -> R.string.failure_no_header
        },
    )

    fun writeFailure(resources: Resources, failure: WriteFailure): String = resources.getString(
        when (failure) {
            WriteFailure.CANNOT_READ -> R.string.write_failure_cannot_read
            WriteFailure.NOT_XLSX -> R.string.write_failure_not_xlsx
            WriteFailure.VERIFY -> R.string.write_failure_verify
            WriteFailure.BACKUP -> R.string.write_failure_backup
            WriteFailure.NO_PERMISSION -> R.string.write_failure_no_permission
            WriteFailure.CANNOT_WRITE -> R.string.write_failure_cannot_write
            WriteFailure.READ_BACK -> R.string.write_failure_read_back
        },
    )

    /** One line per change that did not land exactly as made. */
    fun landed(resources: Resources, note: ChangeApplier.Note): String {
        val title = when (val change = note.change) {
            is Change.Add -> change.book.title
            is Change.Edit -> change.after.title
            is Change.Remove -> change.before.title
        }
        return when (note.outcome) {
            ChangeApplier.Outcome.MERGED_INTO_EXISTING -> resources.getString(R.string.landed_merged, title)
            ChangeApplier.Outcome.EDITED_LOOSE_MATCH -> resources.getString(R.string.landed_loose_edit, title)
            ChangeApplier.Outcome.ADDED_INSTEAD_OF_EDIT -> resources.getString(R.string.landed_added_instead, title)
            ChangeApplier.Outcome.ALREADY_GONE -> resources.getString(R.string.landed_already_gone, title)
            ChangeApplier.Outcome.APPLIED -> title
        }
    }

    private fun dateText(at: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale("tr", "TR")).format(Date(at))
}
