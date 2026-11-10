package com.notes.purenoteit

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.DatabaseMigrations
import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.db.NoteLinkDao
import com.notes.purenoteit.data.repository.CategoryRepositoryImpl
import com.notes.purenoteit.data.repository.NoteRepositoryImpl
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.time.AppClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TombstoneAndMigrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixedClock = AppClock { 1_750_000_000_000L }

    @Test
    fun `not silindiginde fiziksel DELETE yerine Tombstone deletedAt ve iliskili linkler isaretlenmelidir`() = runTest(testDispatcher) {
        val noteDao = mockk<NoteDao>(relaxed = true)
        val categoryDao = mockk<CategoryDao>(relaxed = true)
        val noteLinkDao = mockk<NoteLinkDao>(relaxed = true)
        val repository = NoteRepositoryImpl(noteDao, categoryDao, noteLinkDao, testDispatcher, fixedClock)

        repository.deleteNoteById(42)

        coVerify(exactly = 1) {
            noteDao.softDeleteWithTombstoneSuspend(
                id = 42,
                deletedAt = 1_750_000_000_000L,
                updatedAt = 1_750_000_000_000L
            )
        }
        coVerify(exactly = 1) {
            noteLinkDao.softDeleteLinksForNoteSuspend(
                noteId = 42,
                deletedAt = 1_750_000_000_000L,
                updatedAt = 1_750_000_000_000L
            )
        }
    }

    @Test
    fun `mevcut not guncellendiginde syncUuid korunmali ve syncVersion artirilmalidir`() = runTest(testDispatcher) {
        val noteDao = mockk<NoteDao>(relaxed = true)
        val categoryDao = mockk<CategoryDao>(relaxed = true)
        val noteLinkDao = mockk<NoteLinkDao>(relaxed = true)
        val existingEntity = NoteData(
            title = "Eski",
            description = "İçerik",
            date = 1_000L,
            categoryId = 2,
            syncUuid = "existing-note-uuid-7",
            categorySyncUuid = "cat-default-2",
            updatedAt = 2_000L,
            syncVersion = 3L
        ).apply { id = 7 }

        coEvery { noteDao.getNoteByIdIncludingDeletedSuspend(7) } returns existingEntity
        coEvery { categoryDao.getCategoryByIdIncludingDeletedSuspend(2) } returns
            CategoryData(name = "Günlük", id = 2, syncUuid = "cat-default-2")

        val repository = NoteRepositoryImpl(noteDao, categoryDao, noteLinkDao, testDispatcher, fixedClock)
        repository.updateNote(Note(id = 7, title = "Güncel", description = "Yeni", date = 1_000L, categoryId = 2))

        coVerify(exactly = 1) {
            noteDao.updateNote(match {
                it.syncUuid == "existing-note-uuid-7" &&
                    it.categorySyncUuid == "cat-default-2" &&
                    it.syncVersion == 4L &&
                    it.updatedAt == 1_750_000_000_000L
            })
        }
    }

    @Test
    fun `kategori silindiginde kategori ve altindaki notlar fiziksel silinmeden Tombstone olarak isaretlenmelidir`() = runTest(testDispatcher) {
        val categoryDao = mockk<CategoryDao>(relaxed = true)
        val noteDao = mockk<NoteDao>(relaxed = true)
        val categoryRepo = CategoryRepositoryImpl(categoryDao, noteDao, testDispatcher, fixedClock)

        categoryRepo.deleteCategoryById(9)

        coVerify(exactly = 1) {
            categoryDao.softDeleteWithTombstoneSuspend(
                id = 9,
                deletedAt = 1_750_000_000_000L,
                updatedAt = 1_750_000_000_000L
            )
        }
        coVerify(exactly = 1) {
            noteDao.softDeleteNotesByCategorySuspend(
                categoryId = 9,
                deletedAt = 1_750_000_000_000L,
                updatedAt = 1_750_000_000_000L
            )
        }
    }

    @Test
    fun `MIGRATION_5_6 tum tablolara sutun eklemeli varsayilan kategorilere deterministik UUID atamali ve unique index kurmalidir`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val executedSql = mutableListOf<String>()
        val updateArgs = mutableListOf<Pair<String, Array<out Any?>>>()

        every { db.execSQL(any()) } answers {
            executedSql.add(firstArg())
        }
        every { db.execSQL(any(), any()) } answers {
            updateArgs.add(firstArg<String>() to secondArg())
        }

        // Mock cursors for categories (id=1 'Not', id=2 'Günlük'), empty for others
        val catCursor = mockk<Cursor>(relaxed = true)
        every { catCursor.moveToNext() } returnsMany listOf(true, true, false)
        every { catCursor.getInt(0) } returnsMany listOf(1, 2)
        every { catCursor.isNull(1) } returns false
        every { catCursor.getString(1) } returnsMany listOf("Not", "Günlük")

        val noteCursor = mockk<Cursor>(relaxed = true)
        every { noteCursor.moveToNext() } returnsMany listOf(true, false)
        every { noteCursor.getInt(0) } returns 10
        every { noteCursor.isNull(1) } returns false
        every { noteCursor.getInt(1) } returns 2
        every { noteCursor.isNull(2) } returns false
        every { noteCursor.getLong(2) } returns 555_000L

        val emptyCursor = mockk<Cursor>(relaxed = true)
        every { emptyCursor.moveToNext() } returns false

        every { db.query("SELECT id, name FROM categories") } returns catCursor
        every { db.query("SELECT id, categoryId, date FROM notes") } returns noteCursor
        every { db.query(match<String> { it.contains("FROM note_links") }) } returns emptyCursor
        every { db.query(match<String> { it.contains("FROM stream_items") }) } returns emptyCursor
        every { db.query(match<String> { it.contains("FROM automation_rules") }) } returns emptyCursor

        DatabaseMigrations.MIGRATION_5_6.migrate(db)

        // Check required ALTER TABLE statements across all 5 tables
        assertTrue(executedSql.any { it.contains("ALTER TABLE categories ADD COLUMN syncUuid") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE notes ADD COLUMN categorySyncUuid") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE note_links ADD COLUMN sourceSyncUuid") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE stream_items ADD COLUMN mergedNoteSyncUuid") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE automation_rules ADD COLUMN deletedAt") })

        // Check deterministic default category UUID backfill and note categorySyncUuid link
        val catUpdates = updateArgs.filter { it.first.contains("UPDATE categories") }
        assertEquals(2, catUpdates.size)
        assertEquals("cat-default-1", catUpdates[0].second[0])
        assertEquals("cat-default-2", catUpdates[1].second[0])

        val noteUpdate = updateArgs.first { it.first.contains("UPDATE notes") }
        assertEquals("cat-default-2", noteUpdate.second[1])
        assertEquals(555_000L, noteUpdate.second[2])

        // Check all 5 UNIQUE indices created after backfill
        assertTrue(executedSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_syncUuid ON categories(syncUuid)"))
        assertTrue(executedSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS index_notes_syncUuid ON notes(syncUuid)"))
        assertTrue(executedSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS index_note_links_syncUuid ON note_links(syncUuid)"))
        assertTrue(executedSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS index_stream_items_syncUuid ON stream_items(syncUuid)"))
        assertTrue(executedSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS index_automation_rules_syncUuid ON automation_rules(syncUuid)"))

        verify(exactly = 1) { catCursor.close() }
        verify(exactly = 1) { noteCursor.close() }
    }
}
