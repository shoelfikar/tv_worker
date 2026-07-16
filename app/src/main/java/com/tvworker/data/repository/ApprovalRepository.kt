package com.tvworker.data.repository

import com.tvworker.data.local.ApprovalLogDao
import com.tvworker.data.local.ApprovalLogEntity
import com.tvworker.data.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow

class ApprovalRepository(
    private val dao: ApprovalLogDao,
    private val prefs: AppPreferences
) {
    val logs: Flow<List<ApprovalLogEntity>> = dao.getAll()
    val approvedCount: Flow<Int> = dao.getApprovedCount()
    val autoApproveEnabled: Flow<Boolean> = prefs.autoApproveEnabled

    suspend fun logApproval(packageName: String, action: String) {
        dao.insert(
            ApprovalLogEntity(
                packageName = packageName,
                action = action,
                method = "accessibility",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun setAutoApprove(enabled: Boolean) {
        prefs.setAutoApprove(enabled)
    }

    suspend fun clearLogs() {
        dao.clearAll()
    }
}
