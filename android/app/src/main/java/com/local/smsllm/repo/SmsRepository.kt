package com.local.smsllm.repo

import com.local.smsllm.data.SmsDao
import com.local.smsllm.data.SmsMessageEntity
import com.local.smsllm.domain.ProcessingStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(private val dao: SmsDao) {

    suspend fun insertLive(
        sender: String,
        body: String,
        receivedAt: Long,
        gatePassed: Boolean,
    ): Long {
        val entity = SmsMessageEntity(
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            source = "LIVE",
            gatePassed = gatePassed,
            status = if (gatePassed) ProcessingStatus.PENDING else ProcessingStatus.GATE_REJECTED,
        )
        return dao.insert(entity)
    }

    suspend fun insertImported(
        sender: String,
        body: String,
        receivedAt: Long,
        gatePassed: Boolean,
    ): Long {
        val entity = SmsMessageEntity(
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            source = "IMPORT",
            gatePassed = gatePassed,
            status = if (gatePassed) ProcessingStatus.PENDING else ProcessingStatus.GATE_REJECTED,
        )
        return dao.insertIgnore(entity)
    }

    suspend fun pending(limit: Int): List<SmsMessageEntity> =
        dao.pendingForProcessing(limit)

    suspend fun setStatus(
        id: Long,
        status: ProcessingStatus,
        processedAt: Long? = null,
        error: String? = null,
    ) = dao.setStatus(id, status, processedAt, error)

    fun countPending(): Flow<Int> = dao.countPending()

    suspend fun getById(id: Long): SmsMessageEntity? = dao.getById(id)

    suspend fun requeueForReverify(id: Long) =
        dao.setStatus(id, ProcessingStatus.NEEDS_REVERIFY, null, null)
}
