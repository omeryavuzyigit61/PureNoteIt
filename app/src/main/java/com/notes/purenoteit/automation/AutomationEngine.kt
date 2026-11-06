package com.notes.purenoteit.automation

import android.util.Log
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.repository.CategoryRepository
import com.notes.purenoteit.repository.KnowledgeRepository
import com.notes.purenoteit.repository.NoteRepository
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
    private val categoryRepository: CategoryRepository
) {
    /**
     * Evaluates note-level triggers when a note is created or updated.
     * E.g.: Auto-completes note status when all checklist tasks are done.
     */
    fun onNoteUpdated(note: NoteData, onStatusUpdated: ((String) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rules = knowledgeRepository.getRulesByTriggerSuspend("ALL_TASKS_COMPLETED")
                val activeRule = rules.firstOrNull { it.isEnabled }
                if (activeRule != null) {
                    if (note.totalTasks > 0 && note.completedTasks == note.totalTasks && note.status != "DONE") {
                        note.status = "DONE"
                        noteRepository.upsertSuspend(note)
                        withContext(Dispatchers.Main) {
                            onStatusUpdated?.invoke("DONE")
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e("AutomationEngine", "Error checking rules: ${error.message}")
            }
        }
    }

    /**
     * Creates an automated "Günün Ajandası / Planı" template note.
     */
    fun createDailyAgendaNote(onSuccess: (NoteData) -> Unit) {
        val dateFormat = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr"))
        val todayStr = dateFormat.format(Date())

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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val categories = categoryRepository.getAllCategoriesSuspend()
                val dailyCat = categories.firstOrNull { it.name.equals("Günlük", ignoreCase = true) }
                val targetCategoryId = dailyCat?.id ?: categories.firstOrNull()?.id

                val newNote = NoteData(
                    title = "Ajanda: $todayStr",
                    description = BlockParser.serialize(blocks),
                    date = System.currentTimeMillis(),
                    icon = "📅",
                    categoryId = targetCategoryId,
                    status = "TODO",
                    totalTasks = 3,
                    completedTasks = 0
                )

                val generatedId = noteRepository.insertNoteSuspend(newNote)
                newNote.id = generatedId.toInt()

                withContext(Dispatchers.Main) {
                    onSuccess(newNote)
                }
            } catch (error: Exception) {
                Log.e("AutomationEngine", "Error creating daily agenda note: ${error.message}", error)
            }
        }
    }
}
