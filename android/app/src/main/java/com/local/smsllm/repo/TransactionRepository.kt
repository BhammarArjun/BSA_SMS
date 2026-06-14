package com.local.smsllm.repo

import com.local.smsllm.data.CategorySum
import com.local.smsllm.data.DirectionSum
import com.local.smsllm.data.TransactionDao
import com.local.smsllm.data.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(private val dao: TransactionDao) {

    fun observeAll(): Flow<List<TransactionEntity>> = dao.observeAll()

    fun observeIncluded(): Flow<List<TransactionEntity>> = dao.observeIncluded()

    fun observeById(id: Long): Flow<TransactionEntity?> = dao.observeById(id)

    fun sumByDirection(): Flow<List<DirectionSum>> = dao.sumByDirection()

    fun byCategory(): Flow<List<CategorySum>> = dao.byCategory()

    suspend fun upsertFromExtraction(
        smsId: Long,
        isTransaction: Boolean,
        direction: String? = null,
        amount: Double? = null,
        currency: String? = null,
        dateText: String? = null,
        dateEpoch: Long? = null,
        counterparty: String? = null,
        category: String? = null,
        confidence: Double,
        rawModelOutput: String,
        modelId: String,
        backend: String,
        includedInAnalytics: Boolean,
        createdAt: Long,
        updatedAt: Long,
    ): Long {
        val entity = TransactionEntity(
            smsId = smsId,
            isTransaction = isTransaction,
            direction = direction,
            amount = amount,
            currency = currency,
            dateText = dateText,
            dateEpoch = dateEpoch,
            counterparty = counterparty,
            category = category,
            confidence = confidence,
            rawModelOutput = rawModelOutput,
            modelId = modelId,
            backend = backend,
            includedInAnalytics = includedInAnalytics,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
        return dao.upsert(entity)
    }

    suspend fun setCategory(id: Long, category: String?, now: Long) =
        dao.setCategory(id, category, now)

    suspend fun setIncluded(id: Long, included: Boolean, now: Long) =
        dao.setIncluded(id, included, now)

    suspend fun markUserEdited(id: Long, now: Long) =
        dao.markUserEdited(id, now)
}
