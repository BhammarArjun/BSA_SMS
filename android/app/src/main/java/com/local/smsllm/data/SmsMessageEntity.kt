package com.local.smsllm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.local.smsllm.domain.ProcessingStatus

@Entity(
    tableName = "sms_messages",
    indices = [Index(value = ["sender", "body", "receivedAt"], unique = true)],
)
data class SmsMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    /** "LIVE" or "IMPORT" */
    val source: String,
    val gatePassed: Boolean,
    val status: ProcessingStatus,
    val processedAt: Long? = null,
    val error: String? = null,
)
