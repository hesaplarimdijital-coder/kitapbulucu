package com.hesaplarimdijital.kitapbulucu.store

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hesaplarimdijital.kitapbulucu.model.Owner

/** The app's own choices, observable from Compose: whose device this is and whether setup is done. */
class AppSettings(private val prefs: SharedPreferences) {

    /** The owner whose file this device writes to; the other file is read only. Null until chosen. */
    var me: Owner? by mutableStateOf(prefs.getString(KEY_ME, null)?.let { name -> Owner.entries.firstOrNull { it.name == name } })
        private set

    var setupDone: Boolean by mutableStateOf(prefs.getBoolean(KEY_SETUP_DONE, false))
        private set

    fun updateMe(owner: Owner) {
        me = owner
        prefs.edit().putString(KEY_ME, owner.name).apply()
    }

    fun updateSetupDone(done: Boolean) {
        setupDone = done
        prefs.edit().putBoolean(KEY_SETUP_DONE, done).apply()
    }

    companion object {
        const val FILE = "app"
        private const val KEY_ME = "me"
        private const val KEY_SETUP_DONE = "setup_done"

        fun of(context: Context): AppSettings = AppSettings(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
