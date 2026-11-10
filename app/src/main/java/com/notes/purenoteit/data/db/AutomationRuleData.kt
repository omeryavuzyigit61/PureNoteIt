package com.notes.purenoteit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.UUID

@Entity(
    tableName = "automation_rules",
    indices = [Index(value = ["syncUuid"], unique = true)]
)
data class AutomationRuleData(
    @ColumnInfo(name = "title")
    var title: String,
    @ColumnInfo(name = "triggerType")
    var triggerType: String, // ALL_TASKS_COMPLETED, DAILY_SCHEDULED, KEYWORD_DETECTED
    @ColumnInfo(name = "triggerConfig")
    var triggerConfig: String? = null, // e.g. "08:30" or "#bütçe"
    @ColumnInfo(name = "actionType")
    var actionType: String, // SET_STATUS_DONE, CREATE_TEMPLATE, ASSIGN_CATEGORY
    @ColumnInfo(name = "actionConfig")
    var actionConfig: String? = null, // e.g. "DONE" or template title
    @ColumnInfo(name = "isEnabled", defaultValue = "1")
    var isEnabled: Boolean = true,
    @ColumnInfo(name = "createdAt")
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "syncUuid", defaultValue = "''")
    var syncUuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "updatedAt", defaultValue = "0")
    var updatedAt: Long = 0L,
    @ColumnInfo(name = "deletedAt")
    var deletedAt: Long? = null,
    @ColumnInfo(name = "syncVersion", defaultValue = "1")
    var syncVersion: Long = 1L
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}
