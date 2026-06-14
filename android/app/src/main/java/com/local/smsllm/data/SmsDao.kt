package com.local.smsllm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.smsllm.domain.ProcessingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(msg: SmsMessageEntity): Long

    @Insert
    suspend fun insert(msg: SmsMessageEntity): Long

    @Query(
        "SELECT * FROM sms_messages " +
            "WHERE status IN ('PENDING','NEEDS_REVERIFY','ERROR') " +
            "ORDER BY receivedAt ASC LIMIT :limit",
    )
    suspend fun pendingForProcessing(limit: Int): List<SmsMessageEntity>

    @Query(
        "UPDATE sms_messages " +
            "SET status = :status, processedAt = :processedAt, error = :error " +
            "WHERE id = :id",
    )
    suspend fun setStatus(id: Long, status: ProcessingStatus, processedAt: Long?, error: String?): Int

    @Query(
        "SELECT COUNT(*) FROM sms_messages " +
            "WHERE status IN ('PENDING','NEEDS_REVERIFY','ERROR')",
    )
    fun countPending(): Flow<Int>

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getById(id: Long): SmsMessageEntity?

    @Query("UPDATE sms_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ProcessingStatus): Int
}
