package com.notes.purenoteit.repository

import com.notes.purenoteit.data.db.AutomationRuleDao
import com.notes.purenoteit.data.db.AutomationRuleData
import com.notes.purenoteit.data.db.NoteLinkDao
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.data.db.StreamItemDao
import com.notes.purenoteit.data.db.StreamItemData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepository @Inject constructor(
    private val noteLinkDao: NoteLinkDao,
    private val automationRuleDao: AutomationRuleDao,
    private val streamItemDao: StreamItemDao
) {
    // --- Note Links & Graph Data ---
    suspend fun insertLinkSuspend(link: NoteLinkData): Long = noteLinkDao.insertSuspend(link)
    suspend fun insertAllLinksSuspend(links: List<NoteLinkData>) = noteLinkDao.insertAllSuspend(links)
    suspend fun deleteLinksFromSourceSuspend(noteId: Int) = noteLinkDao.deleteLinksFromSourceSuspend(noteId)
    suspend fun deleteLinksForNoteSuspend(noteId: Int) = noteLinkDao.deleteLinksForNoteSuspend(noteId)
    suspend fun getOutgoingLinksSuspend(noteId: Int): List<NoteLinkData> = noteLinkDao.getOutgoingLinksSuspend(noteId)
    suspend fun getBacklinksSuspend(noteId: Int): List<NoteLinkData> = noteLinkDao.getBacklinksSuspend(noteId)
    suspend fun updateTargetLinkTextSuspend(targetNoteId: Int, newTitle: String) =
        noteLinkDao.updateTargetLinkTextSuspend(targetNoteId, newTitle)
    suspend fun getAllLinksSuspend(): List<NoteLinkData> = noteLinkDao.getAllLinksSuspend()

    // --- Automation Rules ---
    suspend fun insertRuleSuspend(rule: AutomationRuleData): Long = automationRuleDao.insertSuspend(rule)
    suspend fun updateRuleSuspend(rule: AutomationRuleData) = automationRuleDao.updateSuspend(rule)
    suspend fun deleteRuleSuspend(rule: AutomationRuleData) = automationRuleDao.deleteSuspend(rule)
    suspend fun getAllRulesSuspend(): List<AutomationRuleData> = automationRuleDao.getAllRulesSuspend()
    suspend fun getActiveRulesSuspend(): List<AutomationRuleData> = automationRuleDao.getActiveRulesSuspend()
    suspend fun getRulesByTriggerSuspend(triggerType: String): List<AutomationRuleData> =
        automationRuleDao.getRulesByTriggerSuspend(triggerType)

    // --- Quick Inbox Stream ---
    suspend fun insertStreamItemSuspend(item: StreamItemData): Long = streamItemDao.insertSuspend(item)
    suspend fun insertAllStreamItemsSuspend(items: List<StreamItemData>) = streamItemDao.insertAllSuspend(items)
    suspend fun deleteStreamItemSuspend(item: StreamItemData) = streamItemDao.deleteSuspend(item)
    suspend fun getAllStreamItemsSuspend(): List<StreamItemData> = streamItemDao.getAllItemsSuspend()
    suspend fun getUnprocessedStreamItemsSuspend(): List<StreamItemData> = streamItemDao.getUnprocessedItemsSuspend()
    suspend fun markStreamItemsProcessedSuspend(ids: List<Int>, noteId: Int) =
        streamItemDao.markAsProcessedSuspend(ids, noteId)
    suspend fun deleteStreamItemByIdSuspend(id: Int) = streamItemDao.deleteByIdSuspend(id)
}
