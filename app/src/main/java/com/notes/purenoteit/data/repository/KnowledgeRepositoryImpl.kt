package com.notes.purenoteit.data.repository

import com.notes.purenoteit.data.db.AutomationRuleDao
import com.notes.purenoteit.data.db.NoteLinkDao
import com.notes.purenoteit.data.db.StreamItemDao
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.repository.KnowledgeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class KnowledgeRepositoryImpl @Inject constructor(
    private val noteLinkDao: NoteLinkDao,
    private val automationRuleDao: AutomationRuleDao,
    private val streamItemDao: StreamItemDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : KnowledgeRepository {

    override suspend fun insertLink(link: NoteLink): Long = withContext(ioDispatcher) {
        noteLinkDao.insertSuspend(link.toEntity())
    }

    override suspend fun insertAllLinks(links: List<NoteLink>) = withContext(ioDispatcher) {
        noteLinkDao.insertAllSuspend(links.map { it.toEntity() })
    }

    override suspend fun deleteLinksFromSource(noteId: Int) = withContext(ioDispatcher) {
        noteLinkDao.deleteLinksFromSourceSuspend(noteId)
    }

    override suspend fun deleteLinksForNote(noteId: Int) = withContext(ioDispatcher) {
        noteLinkDao.deleteLinksForNoteSuspend(noteId)
    }

    override suspend fun getOutgoingLinks(noteId: Int): List<NoteLink> = withContext(ioDispatcher) {
        noteLinkDao.getOutgoingLinksSuspend(noteId).map { it.toDomain() }
    }

    override suspend fun getBacklinks(noteId: Int): List<NoteLink> = withContext(ioDispatcher) {
        noteLinkDao.getBacklinksSuspend(noteId).map { it.toDomain() }
    }

    override suspend fun updateTargetLinkText(targetNoteId: Int, newTitle: String) = withContext(ioDispatcher) {
        noteLinkDao.updateTargetLinkTextSuspend(targetNoteId, newTitle)
    }

    override suspend fun getAllLinks(): List<NoteLink> = withContext(ioDispatcher) {
        noteLinkDao.getAllLinksSuspend().map { it.toDomain() }
    }

    override suspend fun insertRule(rule: AutomationRule): Long = withContext(ioDispatcher) {
        automationRuleDao.insertSuspend(rule.toEntity())
    }

    override suspend fun updateRule(rule: AutomationRule) = withContext(ioDispatcher) {
        automationRuleDao.updateSuspend(rule.toEntity())
    }

    override suspend fun deleteRule(rule: AutomationRule) = withContext(ioDispatcher) {
        automationRuleDao.deleteSuspend(rule.toEntity())
    }

    override suspend fun getAllRules(): List<AutomationRule> = withContext(ioDispatcher) {
        automationRuleDao.getAllRulesSuspend().map { it.toDomain() }
    }

    override suspend fun getActiveRules(): List<AutomationRule> = withContext(ioDispatcher) {
        automationRuleDao.getActiveRulesSuspend().map { it.toDomain() }
    }

    override suspend fun getRulesByTrigger(triggerType: String): List<AutomationRule> = withContext(ioDispatcher) {
        automationRuleDao.getRulesByTriggerSuspend(triggerType).map { it.toDomain() }
    }

    override suspend fun insertStreamItem(item: StreamItem): Long = withContext(ioDispatcher) {
        streamItemDao.insertSuspend(item.toEntity())
    }

    override suspend fun insertAllStreamItems(items: List<StreamItem>) = withContext(ioDispatcher) {
        streamItemDao.insertAllSuspend(items.map { it.toEntity() })
    }

    override suspend fun deleteStreamItem(item: StreamItem) = withContext(ioDispatcher) {
        streamItemDao.deleteSuspend(item.toEntity())
    }

    override suspend fun getAllStreamItems(): List<StreamItem> = withContext(ioDispatcher) {
        streamItemDao.getAllItemsSuspend().map { it.toDomain() }
    }

    override suspend fun getUnprocessedStreamItems(): List<StreamItem> = withContext(ioDispatcher) {
        streamItemDao.getUnprocessedItemsSuspend().map { it.toDomain() }
    }

    override suspend fun markStreamItemsProcessed(ids: List<Int>, noteId: Int) = withContext(ioDispatcher) {
        streamItemDao.markAsProcessedSuspend(ids, noteId)
    }

    override suspend fun deleteStreamItemById(id: Int) = withContext(ioDispatcher) {
        streamItemDao.deleteByIdSuspend(id)
    }
}
