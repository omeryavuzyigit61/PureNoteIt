package com.notes.purenoteit.domain.repository

import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem

interface KnowledgeRepository {
    suspend fun insertLink(link: NoteLink): Long
    suspend fun insertAllLinks(links: List<NoteLink>)
    suspend fun deleteLinksFromSource(noteId: Int)
    suspend fun deleteLinksForNote(noteId: Int)
    suspend fun getOutgoingLinks(noteId: Int): List<NoteLink>
    suspend fun getBacklinks(noteId: Int): List<NoteLink>
    suspend fun updateTargetLinkText(targetNoteId: Int, newTitle: String)
    suspend fun getAllLinks(): List<NoteLink>

    suspend fun insertRule(rule: AutomationRule): Long
    suspend fun updateRule(rule: AutomationRule)
    suspend fun deleteRule(rule: AutomationRule)
    suspend fun getAllRules(): List<AutomationRule>
    suspend fun getActiveRules(): List<AutomationRule>
    suspend fun getRulesByTrigger(triggerType: String): List<AutomationRule>

    suspend fun insertStreamItem(item: StreamItem): Long
    suspend fun insertAllStreamItems(items: List<StreamItem>)
    suspend fun deleteStreamItem(item: StreamItem)
    suspend fun getAllStreamItems(): List<StreamItem>
    suspend fun getUnprocessedStreamItems(): List<StreamItem>
    suspend fun markStreamItemsProcessed(ids: List<Int>, noteId: Int)
    suspend fun deleteStreamItemById(id: Int)
}
