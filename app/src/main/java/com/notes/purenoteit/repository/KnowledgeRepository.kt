package com.notes.purenoteit.repository

import com.notes.purenoteit.data.db.AutomationRuleDao
import com.notes.purenoteit.data.db.AutomationRuleData
import com.notes.purenoteit.data.db.NoteLinkDao
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.data.db.StreamItemDao
import com.notes.purenoteit.data.db.StreamItemData
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepository @Inject constructor(
    private val noteLinkDao: NoteLinkDao,
    private val automationRuleDao: AutomationRuleDao,
    private val streamItemDao: StreamItemDao
) {
    // --- Note Links & Graph Data ---
    fun insertLink(link: NoteLinkData): Single<Long> = noteLinkDao.insert(link)
    fun insertAllLinks(links: List<NoteLinkData>): Completable = noteLinkDao.insertAll(links)
    fun deleteLinksFromSource(noteId: Int): Completable = noteLinkDao.deleteLinksFromSource(noteId)
    fun getOutgoingLinks(noteId: Int): Single<List<NoteLinkData>> = noteLinkDao.getOutgoingLinks(noteId)
    fun getBacklinks(noteId: Int): Single<List<NoteLinkData>> = noteLinkDao.getBacklinks(noteId)
    fun getAllLinks(): Single<List<NoteLinkData>> = noteLinkDao.getAllLinks()

    // --- Automation Rules ---
    fun insertRule(rule: AutomationRuleData): Single<Long> = automationRuleDao.insert(rule)
    fun updateRule(rule: AutomationRuleData): Completable = automationRuleDao.update(rule)
    fun deleteRule(rule: AutomationRuleData): Completable = automationRuleDao.delete(rule)
    fun getAllRules(): Single<List<AutomationRuleData>> = automationRuleDao.getAllRules()
    fun getActiveRules(): Single<List<AutomationRuleData>> = automationRuleDao.getActiveRules()
    fun getRulesByTrigger(triggerType: String): Single<List<AutomationRuleData>> =
        automationRuleDao.getRulesByTrigger(triggerType)

    // --- Quick Inbox Stream ---
    fun insertStreamItem(item: StreamItemData): Single<Long> = streamItemDao.insert(item)
    fun deleteStreamItem(item: StreamItemData): Completable = streamItemDao.delete(item)
    fun getAllStreamItems(): Single<List<StreamItemData>> = streamItemDao.getAllItems()
    fun getUnprocessedStreamItems(): Single<List<StreamItemData>> = streamItemDao.getUnprocessedItems()
    fun markStreamItemsProcessed(ids: List<Int>, noteId: Int): Completable =
        streamItemDao.markAsProcessed(ids, noteId)
    fun deleteStreamItemById(id: Int): Completable = streamItemDao.deleteById(id)
}
