package com.local.smsllm.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = SmsMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["smsId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("smsId", unique = true),
        Index("category"),
        Index("includedInAnalytics"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsId: Long,
    val isTransaction: Boolean,
    val direction: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val dateText: String? = null,
    val dateEpoch: Long? = null,
    val counterparty: String? = null,
    val category: String? = null,
    val confidence: Double,
    val rawModelOutput: String,
    val modelId: String,
    val backend: String,
    val userEdited: Boolean = false,
    val includedInAnalytics: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
