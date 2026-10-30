package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface AutomationRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(rule: AutomationRuleData): Single<Long>

    @Update
    fun update(rule: AutomationRuleData): Completable

    @Delete
    fun delete(rule: AutomationRuleData): Completable

    @Query("SELECT * FROM automation_rules ORDER BY createdAt DESC")
    fun getAllRules(): Single<List<AutomationRuleData>>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    fun getActiveRules(): Single<List<AutomationRuleData>>

    @Query("SELECT * FROM automation_rules WHERE triggerType = :triggerType AND isEnabled = 1")
    fun getRulesByTrigger(triggerType: String): Single<List<AutomationRuleData>>
}
