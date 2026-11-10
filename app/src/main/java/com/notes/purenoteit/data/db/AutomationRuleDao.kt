package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface AutomationRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuspend(rule: AutomationRuleData): Long

    @Update
    suspend fun updateSuspend(rule: AutomationRuleData)

    @Upsert
    suspend fun upsertSuspend(rule: AutomationRuleData)

    @Query(
        """
        UPDATE automation_rules
        SET deletedAt = :deletedAt,
            updatedAt = :updatedAt,
            syncVersion = syncVersion + 1
        WHERE id = :id AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteWithTombstoneSuspend(id: Int, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM automation_rules WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAllRulesSuspend(): List<AutomationRuleData>

    @Query("SELECT * FROM automation_rules WHERE deletedAt IS NULL AND isEnabled = 1")
    suspend fun getActiveRulesSuspend(): List<AutomationRuleData>

    @Query("SELECT * FROM automation_rules WHERE deletedAt IS NULL AND triggerType = :triggerType AND isEnabled = 1")
    suspend fun getRulesByTriggerSuspend(triggerType: String): List<AutomationRuleData>

    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun getByIdIncludingDeletedSuspend(id: Int): AutomationRuleData?

    @Query("SELECT * FROM automation_rules WHERE syncUuid = :syncUuid LIMIT 1")
    suspend fun getRuleBySyncUuidIncludingDeletedSuspend(syncUuid: String): AutomationRuleData?

    @Query("SELECT * FROM automation_rules WHERE updatedAt > :sinceMillis ORDER BY updatedAt ASC")
    suspend fun getRulesModifiedSinceSuspend(sinceMillis: Long): List<AutomationRuleData>
}
