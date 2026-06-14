package com.local.smsllm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DirectionSum(val direction: String?, val total: Double)

data class CategorySum(val category: String?, val total: Double, val count: Int)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(txn: TransactionEntity): Long

    @Update
    suspend fun update(txn: TransactionEntity): Int

    @Transaction
    suspend fun upsert(txn: TransactionEntity): Long {
        val insertedId = insertIfAbsent(txn)
        if (insertedId != -1L) return insertedId
        val existing = getBySmsId(txn.smsId)!!
        update(txn.copy(id = existing.id, createdAt = existing.createdAt))
        return existing.id
    }

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE smsId = :smsId")
    suspend fun getBySmsId(smsId: Long): TransactionEntity?

    @Query(
        "SELECT direction, SUM(amount) as total " +
            "FROM transactions " +
            "WHERE includedInAnalytics = 1 AND amount IS NOT NULL " +
            "GROUP BY direction",
    )
    fun sumByDirection(): Flow<List<DirectionSum>>

    @Query(
        "SELECT category, SUM(amount) as total, COUNT(*) as count " +
            "FROM transactions " +
            "WHERE includedInAnalytics = 1 AND amount IS NOT NULL " +
            "GROUP BY category",
    )
    fun byCategory(): Flow<List<CategorySum>>

    @Query(
        "SELECT * FROM transactions WHERE includedInAnalytics = 1 ORDER BY createdAt DESC",
    )
    fun observeIncluded(): Flow<List<TransactionEntity>>

    @Query(
        "UPDATE transactions " +
            "SET category = :category, userEdited = 1, updatedAt = :now " +
            "WHERE id = :id",
    )
    suspend fun setCategory(id: Long, category: String?, now: Long): Int

    @Query(
        "UPDATE transactions " +
            "SET includedInAnalytics = :included, updatedAt = :now " +
            "WHERE id = :id",
    )
    suspend fun setIncluded(id: Long, included: Boolean, now: Long): Int

    @Query(
        "UPDATE transactions SET userEdited = 1, updatedAt = :now WHERE id = :id",
    )
    suspend fun markUserEdited(id: Long, now: Long): Int
}
