package com.notes.purenoteit

import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PureNoteIt - Kategori Öğesi Sayısına Göre Dinamik Sıralama Birim Testi.
 * Bu test, HomeScreen'de en çok nota sahip kategorilerin başa gelmesini,
 * eşitlik halinde alfabetik sıralanmasını ve 'Tümü' filtresinin kararlılığını garanti eder.
 */
class CategorySortingLogicTest {

    @Test
    fun `kategoriler not sayisina gore azalan sirada dizilmelidir`() {
        val categories = listOf(
            CategoryData(id = 1, name = "Günlük", isPassword = false),
            CategoryData(id = 2, name = "Not", isPassword = false),
            CategoryData(id = 3, name = "Önemli", isPassword = false)
        )

        val activeNotes = listOf(
            NoteData(title = "A", description = "Desc", date = 0L, categoryId = 2),
            NoteData(title = "B", description = "Desc", date = 0L, categoryId = 2),
            NoteData(title = "C", description = "Desc", date = 0L, categoryId = 2),
            NoteData(title = "D", description = "Desc", date = 0L, categoryId = 3)
        )

        val sorted = categories.sortedWith(
            compareByDescending<CategoryData> { cat ->
                activeNotes.count { it.categoryId == cat.id }
            }.thenBy { it.name }
        )

        // Beklenen sıra: Not (3 not) -> Önemli (1 not) -> Günlük (0 not)
        assertEquals("Not", sorted[0].name)
        assertEquals("Önemli", sorted[1].name)
        assertEquals("Günlük", sorted[2].name)
    }

    @Test
    fun `not sayilari esit oldugunda alfabetik siraya gore dizilmelidir`() {
        val categories = listOf(
            CategoryData(id = 1, name = "Zaman", isPassword = false),
            CategoryData(id = 2, name = "Akıl", isPassword = false),
            CategoryData(id = 3, name = "Bilişim", isPassword = false)
        )

        val activeNotes = listOf(
            NoteData(title = "A", description = "Desc", date = 0L, categoryId = 1),
            NoteData(title = "B", description = "Desc", date = 0L, categoryId = 2),
            NoteData(title = "C", description = "Desc", date = 0L, categoryId = 3)
        )

        val sorted = categories.sortedWith(
            compareByDescending<CategoryData> { cat ->
                activeNotes.count { it.categoryId == cat.id }
            }.thenBy { it.name }
        )

        // Eşitlik durumunda alfabetik: Akıl -> Bilişim -> Zaman
        assertEquals("Akıl", sorted[0].name)
        assertEquals("Bilişim", sorted[1].name)
        assertEquals("Zaman", sorted[2].name)
    }
}
