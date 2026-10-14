package com.hesaplarimdijital.kitapbulucu.ui.importing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.importing.MergePlan
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookColumn
import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The merge screen's Turkish sentences. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr")
class ImportTextsTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun theResultIsOneLine() {
        assertEquals("47 kitap eklendi, 3 kitap güncellendi, 0 kitap silindi.", ImportTexts.result(resources, MergePlan.Result(47, 3, 0)))
        assertEquals("Uygulanınca 2 kitap eklenecek, 1 kitap güncellenecek, 0 kitap silinecek.", ImportTexts.preview(resources, MergePlan.Result(2, 1, 0)))
    }

    @Test
    fun changesShowOldAndNewSideBySide() {
        assertEquals("Raf: 1B → 2A", ImportTexts.field(resources, MergePlan.FieldChange(BookColumn.SHELF, "1B", "2A")))
        assertEquals("Raf: (boş) → 2A", ImportTexts.field(resources, MergePlan.FieldChange(BookColumn.SHELF, "", "2A")))
        assertEquals("İmzalı: Hayır → Evet", ImportTexts.field(resources, MergePlan.FieldChange(BookColumn.SIGNED, "", "Evet")))
        assertEquals("Adet: 1 → 2", ImportTexts.field(resources, MergePlan.FieldChange(BookColumn.COPIES, "1", "2")))
    }

    @Test
    fun booksAreToldApartByTheirDetails() {
        val book = Book("Çatıdakiler", "Leyla Umut", "3C", copies = 2, signed = true)
        assertEquals("Leyla Umut · Raf 3C · 2 adet · İmzalı", ImportTexts.details(resources, book))
        assertEquals("Çatıdakiler — Leyla Umut · Raf 3C · 2 adet · İmzalı", ImportTexts.short(resources, book))
        assertEquals("Adsız Yazarsız", ImportTexts.short(resources, Book("Adsız Yazarsız", "")))
    }

    @Test
    fun theSummaryCountsEveryKind() {
        val plan = MergePlan.Plan(Owner.ME, listOf(Book("A", "")), emptyList(), listOf(Book("B", ""), Book("C", "")), emptyList(), mergedDuplicates = 2)
        assertEquals(
            "1 yeni, 0 değişen, 2 dosyada olmayan kitap; 0 soru. Dosyada aynı kitabın 2 tekrar eden satırı tek satır oldu.",
            ImportTexts.summary(resources, plan),
        )
    }
}
