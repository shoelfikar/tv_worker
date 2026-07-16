package com.tvworker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "approval_logs")
data class ApprovalLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val action: String,
    val method: String,
    val timestamp: Long
)
