package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AutomationRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuspend(rule: AutomationRuleData): Long

    @Update
    suspend fun updateSuspend(rule: AutomationRuleData)

    @Delete
    suspend fun deleteSuspend(rule: AutomationRuleData)

    @Query("SELECT * FROM automation_rules ORDER BY createdAt DESC")
    suspend fun getAllRulesSuspend(): List<AutomationRuleData>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    suspend fun getActiveRulesSuspend(): List<AutomationRuleData>

    @Query("SELECT * FROM automation_rules WHERE triggerType = :triggerType AND isEnabled = 1")
    suspend fun getRulesByTriggerSuspend(triggerType: String): List<AutomationRuleData>
}
