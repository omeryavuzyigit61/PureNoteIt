package com.notes.purenoteit.domain.model

data class AutomationRule(
    val id: Int = 0,
    val title: String,
    val triggerType: String,
    val triggerConfig: String? = null,
    val actionType: String,
    val actionConfig: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = 0L
)
