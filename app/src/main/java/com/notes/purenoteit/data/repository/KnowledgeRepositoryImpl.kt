package com.notes.purenoteit.data.repository

import com.notes.purenoteit.data.db.AutomationRuleDao
import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.db.NoteLinkDao
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.data.db.StreamItemDao
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.repository.KnowledgeRepository
import com.notes.purenoteit.domain.time.AppClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class KnowledgeRepositoryImpl @Inject constructor(
    private val noteLinkDao: NoteLinkDao,
    private val automationRuleDao: AutomationRuleDao,
    private val streamItemDao: StreamItemDao,
    private val noteDao: NoteDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: AppClock
) : KnowledgeRepository {

    override suspend fun insertLink(link: NoteLink): Long = withContext(ioDispatcher) {
        noteLinkDao.insertSuspend(prepareLinkEntity(link))
    }

    override suspend fun insertAllLinks(links: List<NoteLink>) = withContext(ioDispatcher) {
        noteLinkDao.insertAllSuspend(links.map { prepareLinkEntity(it) })
    }

    override suspend fun deleteLinksFromSource(noteId: Int) = withContext(ioDispatcher) {
        val now = clock.nowMillis()
        noteLinkDao.softDeleteLinksFromSourceSuspend(noteId, deletedAt = now, updatedAt = now)
    }

    override suspend fun deleteLinksForNote(noteId: Int) = withContext(ioDispatcher) {
        val now = clock.nowMillis()
        noteLinkDao.softDeleteLinksForNoteSuspend(noteId, deletedAt = now, updatedAt = now)
    }

    override suspend fun getOutgoingLinks(noteId: Int): List<NoteLink> = withContext(ioDispatcher) {
        noteLinkDao.getOutgoingLinksSuspend(noteId).map { it.toDomain() }
    }

    override suspend fun getBacklinks(noteId: Int): List<NoteLink> = withContext(ioDispatcher) {
        noteLinkDao.getBacklinksSuspend(noteId).map { it.toDomain() }
    }

    override suspend fun updateTargetLinkText(targetNoteId: Int, newTitle: String) = withContext(ioDispatcher) {
        noteLinkDao.updateTargetLinkTextSuspend(targetNoteId, newTitle, clock.nowMillis())
    }

    override suspend fun getAllLinks(): List<NoteLink> = withContext(ioDispatcher) {
        noteLinkDao.getAllLinksSuspend().map { it.toDomain() }
    }

    override suspend fun insertRule(rule: AutomationRule): Long = withContext(ioDispatcher) {
        val now = clock.nowMillis()
        automationRuleDao.insertSuspend(rule.toEntity(now))
    }

    override suspend fun updateRule(rule: AutomationRule) = withContext(ioDispatcher) {
        val now = clock.nowMillis()
        val existing = if (rule.id > 0) {
            automationRuleDao.getByIdIncludingDeletedSuspend(rule.id)?.takeIf { it.id > 0 }
        } else null
        val enriched = rule.copy(
            syncUuid = rule.syncUuid.ifBlank { existing?.syncUuid?.ifBlank { null } ?: UUID.randomUUID().toString() },
            updatedAt = now,
            syncVersion = if (existing != null) maxOf(rule.syncVersion, existing.syncVersion) + 1L else rule.syncVersion
        )
        automationRuleDao.updateSuspend(enriched.toEntity(now))
    }

    override suspend fun deleteRule(rule: AutomationRule) = withContext(ioDispatcher) {
        if (rule.id > 0) {
            val now = clock.nowMillis()
            automationRuleDao.softDeleteWithTombstoneSuspend(rule.id, deletedAt = now, updatedAt = now)
        }
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
        val now = clock.nowMillis()
        val mergedSyncUuid = item.mergedNoteSyncUuid
            ?: item.mergedNoteId?.let { noteDao.getNoteByIdIncludingDeletedSuspend(it)?.takeIf { n -> n.id > 0 }?.syncUuid }
        streamItemDao.insertSuspend(item.copy(mergedNoteSyncUuid = mergedSyncUuid).toEntity(now))
    }

    override suspend fun insertAllStreamItems(items: List<StreamItem>) = withContext(ioDispatcher) {
        val now = clock.nowMillis()
        streamItemDao.insertAllSuspend(items.map { item ->
            val mergedSyncUuid = item.mergedNoteSyncUuid
                ?: item.mergedNoteId?.let { noteDao.getNoteByIdIncludingDeletedSuspend(it)?.takeIf { n -> n.id > 0 }?.syncUuid }
            item.copy(mergedNoteSyncUuid = mergedSyncUuid).toEntity(now)
        })
    }

    override suspend fun deleteStreamItem(item: StreamItem) = withContext(ioDispatcher) {
        if (item.id > 0) {
            val now = clock.nowMillis()
            streamItemDao.softDeleteWithTombstoneSuspend(item.id, deletedAt = now, updatedAt = now)
        }
    }

    override suspend fun getAllStreamItems(): List<StreamItem> = withContext(ioDispatcher) {
        streamItemDao.getAllItemsSuspend().map { it.toDomain() }
    }

    override suspend fun getUnprocessedStreamItems(): List<StreamItem> = withContext(ioDispatcher) {
        streamItemDao.getUnprocessedItemsSuspend().map { it.toDomain() }
    }

    override suspend fun markStreamItemsProcessed(ids: List<Int>, noteId: Int) = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext
        val now = clock.nowMillis()
        val mergedSyncUuid = noteDao.getNoteByIdIncludingDeletedSuspend(noteId)?.takeIf { it.id > 0 }?.syncUuid
        streamItemDao.markAsProcessedSuspend(ids, noteId, mergedSyncUuid, now)
    }

    override suspend fun deleteStreamItemById(id: Int) = withContext(ioDispatcher) {
        if (id > 0) {
            val now = clock.nowMillis()
            streamItemDao.softDeleteWithTombstoneSuspend(id, deletedAt = now, updatedAt = now)
        }
    }

    override suspend fun getLinksModifiedSince(sinceMillis: Long): List<NoteLink> = withContext(ioDispatcher) {
        noteLinkDao.getLinksModifiedSinceSuspend(sinceMillis).map { it.toDomain() }
    }

    override suspend fun getRulesModifiedSince(sinceMillis: Long): List<AutomationRule> = withContext(ioDispatcher) {
        automationRuleDao.getRulesModifiedSinceSuspend(sinceMillis).map { it.toDomain() }
    }

    override suspend fun getStreamItemsModifiedSince(sinceMillis: Long): List<StreamItem> = withContext(ioDispatcher) {
        streamItemDao.getStreamItemsModifiedSinceSuspend(sinceMillis).map { it.toDomain() }
    }

    private suspend fun prepareLinkEntity(link: NoteLink): NoteLinkData {
        val now = clock.nowMillis()
        val srcSync = link.sourceSyncUuid.ifBlank {
            noteDao.getNoteByIdIncludingDeletedSuspend(link.sourceNoteId)?.takeIf { it.id > 0 }?.syncUuid.orEmpty()
        }
        val tgtSync = link.targetSyncUuid.ifBlank {
            noteDao.getNoteByIdIncludingDeletedSuspend(link.targetNoteId)?.takeIf { it.id > 0 }?.syncUuid.orEmpty()
        }
        return link.copy(
            sourceSyncUuid = srcSync,
            targetSyncUuid = tgtSync
        ).toEntity(now)
    }
}
