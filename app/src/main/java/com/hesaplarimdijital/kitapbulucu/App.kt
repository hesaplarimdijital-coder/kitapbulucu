package com.hesaplarimdijital.kitapbulucu

import android.app.Application
import android.content.Context
import com.hesaplarimdijital.kitapbulucu.store.FileLinks
import com.hesaplarimdijital.kitapbulucu.store.LibraryCache
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.store.SafDocuments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Holds the one [LibraryStore]; it starts reading as soon as the process is up. */
class App : Application() {
    lateinit var store: LibraryStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = LibraryStore(
            links = FileLinks.of(this),
            documents = SafDocuments(contentResolver),
            cache = LibraryCache(File(filesDir, "library")),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
        store.start()
    }

    companion object {
        fun store(context: Context): LibraryStore = (context.applicationContext as App).store
    }
}
