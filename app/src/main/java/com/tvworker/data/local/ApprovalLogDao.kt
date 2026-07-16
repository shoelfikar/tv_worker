package com.tvworker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApprovalLogDao {

    @Insert
    suspend fun insert(log: ApprovalLogEntity)

    @Query("SELECT * FROM approval_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ApprovalLogEntity>>

    @Query("SELECT COUNT(*) FROM approval_logs WHERE action = 'approved'")
    fun getApprovedCount(): Flow<Int>

    @Query("DELETE FROM approval_logs")
    suspend fun clearAll()
}
