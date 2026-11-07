package com.notes.purenoteit.automation

import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.ApplicationScope
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.repository.CategoryRepository
import com.notes.purenoteit.domain.repository.KnowledgeRepository
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationEngine @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
    private val noteRepository: NoteRepository,
    private val categoryRepository: CategoryRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val clock: AppClock
) {
    /**
     * Evaluates note-level triggers when a note is created or updated.
     */
    fun onNoteUpdated(note: NoteData, onStatusUpdated: ((String) -> Unit)? = null) {
        applicationScope.launch {
            try {
                val rules = knowledgeRepository.getRulesByTrigger("ALL_TASKS_COMPLETED")
                val activeRule = rules.firstOrNull { it.isEnabled }
                if (activeRule != null) {
                    if (note.totalTasks > 0 && note.completedTasks == note.totalTasks && note.status != "DONE") {
                        note.status = "DONE"
                        val domainNote = Note(
                            id = note.id,
                            title = note.title,
                            description = note.description,
                            date = note.date,
                            isPassword = note.isPassword,
                            images = note.images,
                            categoryId = note.categoryId,
                            sounds = note.sounds,
                            isPinned = note.isPinned,
                            icon = note.icon,
                            isEncrypted = note.isEncrypted,
                            totalTasks = note.totalTasks,
                            completedTasks = note.completedTasks,
                            status = "DONE",
                            reminderTime = note.reminderTime,
                            reminderType = note.reminderType,
                            reminderSound = note.reminderSound,
                            isReminderDone = note.isReminderDone,
                            richBlocksJson = note.richBlocksJson
                        )
                        noteRepository.upsertNote(domainNote)
                        withContext(Dispatchers.Main) {
                            onStatusUpdated?.invoke("DONE")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Creates an automated "Günün Ajandası / Planı" template note.
     */
    fun createDailyAgendaNote(onSuccess: (NoteData) -> Unit) {
        val now = clock.nowMillis()
        val dateFormat = SimpleDateFormat("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr"))
        val todayStr = dateFormat.format(Date(now))

        val blocks = listOf(
            NoteBlock(
                type = BlockType.CALLOUT,
                calloutIcon = "☀️",
                content = "Günün Mottosu: Küçük adımlar, büyük zaferler doğurur."
            ),
            NoteBlock(
                type = BlockType.HEADING_2,
                content = "Öncelikli Görevler"
            ),
            NoteBlock(
                type = BlockType.TODO,
                content = "En kritik 1. işi tamamla",
                isChecked = false
            ),
            NoteBlock(
                type = BlockType.TODO,
                content = "Önemli e-postaları yanıtla",
                isChecked = false
            ),
            NoteBlock(
                type = BlockType.TODO,
                content = "Günün değerlendirmesini yap",
                isChecked = false
            ),
            NoteBlock(
                type = BlockType.DIVIDER
            ),
            NoteBlock(
                type = BlockType.HEADING_2,
                content = "Günün Notları & Düşünceler"
            ),
            NoteBlock(
                type = BlockType.PARAGRAPH,
                content = "Bugün dikkatimi çekenler ve öğrendiklerim..."
            )
        )

        applicationScope.launch {
            try {
                val categories = categoryRepository.getAllCategories()
                val dailyCat = categories.firstOrNull { it.name.equals("Günlük", ignoreCase = true) }
                val targetCategoryId = dailyCat?.id ?: categories.firstOrNull()?.id

                val domainNote = Note(
                    title = "Ajanda: $todayStr",
                    description = BlockParser.serialize(blocks),
                    date = now,
                    icon = "📅",
                    categoryId = targetCategoryId,
                    status = "TODO",
                    totalTasks = 3,
                    completedTasks = 0
                )

                val generatedId = noteRepository.insertNote(domainNote)
                val savedNote = domainNote.copy(id = generatedId.toInt()).toEntity()

                withContext(Dispatchers.Main) {
                    onSuccess(savedNote)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}
