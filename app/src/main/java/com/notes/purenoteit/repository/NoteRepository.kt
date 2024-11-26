package com.notes.purenoteit.repository

import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.db.NoteData
import javax.inject.Inject

class NoteRepository @Inject constructor(private val noteDao: NoteDao) {
    fun upsert(item: NoteData) = noteDao.upsert(item)
    fun delete(item: NoteData) = noteDao.delete(item)
    fun getNotes() = noteDao.getNotes()
    fun searchNote(query: String) = noteDao.searchNote(query)
    fun getNoteById(id: Int) = noteDao.getNoteById(id)
    fun deleteNoteById(id: Int) = noteDao.deleteNoteById(id)
    fun getNotesBetweenDates(startDate: Long, endDate: Long) = noteDao.getNotesBetweenDates(startDate, endDate)
    fun getNotesByCategory(categoryName: String) = noteDao.getNotesByCategory(categoryName)
}