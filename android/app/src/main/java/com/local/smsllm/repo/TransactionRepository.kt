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

    suspend fun getBySmsId(smsId: Long): TransactionEntity? = dao.getBySmsId(smsId)

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
        // Re-verify: if an existing row was user-edited, preserve the user's fields and keep
        // userEdited=true. Only model-provenance fields are refreshed from the new extraction.
        val existing = dao.getBySmsId(smsId)
        val (mergedDirection, mergedAmount, mergedDateText, mergedCounterparty, mergedCategory,
            mergedUserEdited, mergedIncludedInAnalytics) =
            if (existing != null && existing.userEdited) {
                // User's edits win for user-facing fields; model provenance always updates.
                // includedInAnalytics is also preserved when the user has edited the row.
                UserEditedFields(
                    direction = existing.direction,
                    amount = existing.amount,
                    dateText = existing.dateText,
                    counterparty = existing.counterparty,
                    category = existing.category,
                    userEdited = true,
                    includedInAnalytics = existing.includedInAnalytics,
                )
            } else {
                UserEditedFields(
                    direction = direction,
                    amount = amount,
                    dateText = dateText,
                    counterparty = counterparty,
                    category = category,
                    userEdited = false,
                    includedInAnalytics = includedInAnalytics,
                )
            }

        val entity = TransactionEntity(
            smsId = smsId,
            isTransaction = isTransaction,
            direction = mergedDirection,
            amount = mergedAmount,
            currency = currency,
            dateText = mergedDateText,
            dateEpoch = dateEpoch,
            counterparty = mergedCounterparty,
            category = mergedCategory,
            confidence = confidence,
            rawModelOutput = rawModelOutput,
            modelId = modelId,
            backend = backend,
            userEdited = mergedUserEdited,
            includedInAnalytics = mergedIncludedInAnalytics,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
        return dao.upsert(entity)
    }

    private data class UserEditedFields(
        val direction: String?,
        val amount: Double?,
        val dateText: String?,
        val counterparty: String?,
        val category: String?,
        val userEdited: Boolean,
        val includedInAnalytics: Boolean,
    )

    suspend fun setCategory(id: Long, category: String?, now: Long) =
        dao.setCategory(id, category, now)

    suspend fun setIncluded(id: Long, included: Boolean, now: Long) =
        dao.setIncluded(id, included, now)

    suspend fun markUserEdited(id: Long, now: Long) =
        dao.markUserEdited(id, now)

    suspend fun editFields(
        id: Long,
        direction: String?,
        amount: Double?,
        dateText: String?,
        counterparty: String?,
        now: Long,
    ) = dao.editFields(id, direction, amount, dateText, counterparty, now)
}
