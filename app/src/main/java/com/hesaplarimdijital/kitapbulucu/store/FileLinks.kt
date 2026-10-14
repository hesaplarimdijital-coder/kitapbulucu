package com.hesaplarimdijital.kitapbulucu.store

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.hesaplarimdijital.kitapbulucu.model.ColumnMapping
import com.hesaplarimdijital.kitapbulucu.model.Owner

/** The documents the app is linked to: one per owner, plus an optional folder, kept in preferences. */
class FileLinks(private val prefs: SharedPreferences) {

    class Link(val uri: Uri, val displayName: String?)

    fun link(owner: Owner): Link? {
        val uri = prefs.getString(uriKey(owner), null) ?: return null
        return Link(Uri.parse(uri), prefs.getString(nameKey(owner), null))
    }

    fun set(owner: Owner, uri: Uri, displayName: String?) {
        prefs.edit().putString(uriKey(owner), uri.toString()).putString(nameKey(owner), displayName).apply()
    }

    fun clear(owner: Owner) {
        prefs.edit().remove(uriKey(owner)).remove(nameKey(owner)).remove(sheetKey(owner)).remove(mappingKey(owner)).apply()
    }

    /** The sheet to read when a workbook has several filled ones; null means the first filled sheet. */
    fun sheetName(owner: Owner): String? = prefs.getString(sheetKey(owner), null)

    fun setSheetName(owner: Owner, name: String?) {
        prefs.edit().apply { if (name == null) remove(sheetKey(owner)) else putString(sheetKey(owner), name) }.apply()
    }

    /** The user's column mapping for this owner's file, when its headers are not recognised. */
    fun mapping(owner: Owner): ColumnMapping? = ColumnMapping.fromJson(prefs.getString(mappingKey(owner), null))

    fun setMapping(owner: Owner, mapping: ColumnMapping?) {
        prefs.edit().apply { if (mapping == null) remove(mappingKey(owner)) else putString(mappingKey(owner), mapping.toJson()) }.apply()
    }

    var treeUri: Uri?
        get() = prefs.getString(KEY_TREE, null)?.let(Uri::parse)
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_TREE) else putString(KEY_TREE, value.toString()) }.apply()
        }

    /** Whether the one-time "colours and formats are not kept" notice has been shown. */
    var formatNoticeShown: Boolean
        get() = prefs.getBoolean(KEY_FORMAT_NOTICE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FORMAT_NOTICE, value).apply()
        }

    /** Answers given to "is this the same book?" while merging Excel files (S-23), by pair key. */
    var mergeDecisions: Map<String, Boolean>
        get() = try {
            val json = org.json.JSONObject(prefs.getString(KEY_DECISIONS, null) ?: "{}")
            json.keys().asSequence().associateWith { json.getBoolean(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        set(value) {
            prefs.edit().putString(KEY_DECISIONS, org.json.JSONObject(value as Map<*, *>).toString()).apply()
        }

    /** Names of stray files (lock files, conflicted copies) the user has already been told about. */
    var warnedFileNames: Set<String>
        get() = prefs.getStringSet(KEY_WARNED, null)?.toSet().orEmpty()
        set(value) {
            prefs.edit().putStringSet(KEY_WARNED, value).apply()
        }

    private fun uriKey(owner: Owner) = "uri_${owner.name}"
    private fun nameKey(owner: Owner) = "name_${owner.name}"
    private fun sheetKey(owner: Owner) = "sheet_${owner.name}"
    private fun mappingKey(owner: Owner) = "mapping_${owner.name}"

    companion object {
        const val FILE = "files"
        private const val KEY_TREE = "tree_uri"
        private const val KEY_WARNED = "warned_names"
        private const val KEY_FORMAT_NOTICE = "format_notice_shown"
        private const val KEY_DECISIONS = "merge_decisions"

        fun of(context: Context): FileLinks = FileLinks(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
