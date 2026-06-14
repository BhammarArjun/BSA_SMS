package com.local.smsllm.work

import com.local.smsllm.data.CategorySum
import com.local.smsllm.data.DirectionSum
import com.local.smsllm.data.SmsDao
import com.local.smsllm.data.SmsMessageEntity
import com.local.smsllm.data.TransactionDao
import com.local.smsllm.data.TransactionEntity
import com.local.smsllm.domain.ExtractionResult
import com.local.smsllm.domain.ProcessingStatus
import com.local.smsllm.llm.BackendChoice
import com.local.smsllm.llm.LlmService
import com.local.smsllm.repo.SettingsAccess
import com.local.smsllm.repo.SettingsSnapshot
import com.local.smsllm.repo.SmsRepository
import com.local.smsllm.repo.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)

class ExtractionProcessorTest {

    private lateinit var fakeSmsDao: FakeSmsDao
    private lateinit var fakeTxnDao: FakeTransactionDao
    private lateinit var fakeLlm: FakeLlmService
    private lateinit var fakeSettings: FakeSettingsAccess
    private lateinit var processor: ExtractionProcessor

    @Before
    fun setUp() {
        fakeSmsDao = FakeSmsDao()
        fakeTxnDao = FakeTransactionDao()
        fakeLlm = FakeLlmService()
        fakeSettings = FakeSettingsAccess()
        processor = ExtractionProcessor(
            smsRepo = SmsRepository(fakeSmsDao),
            txnRepo = TransactionRepository(fakeTxnDao),
            llm = fakeLlm,
            settings = fakeSettings,
        )
    }

    // --- Acceptance criterion #4: skip model load when empty ---

    @Test
    fun `runOnce skips ensureLoaded when pending is empty`() = runTest {
        fakeSmsDao.pendingMessages = emptyList()

        val stats = processor.runOnce()

        assertTrue("Should be marked skipped", stats.skipped)
        assertEquals(0, stats.processed)
        assertEquals(0, stats.errors)
        assertFalse("ensureLoaded must NOT be called when queue is empty", fakeLlm.ensureLoadedCalled)
        assertFalse("close must NOT be called when queue is empty", fakeLlm.closeCalled)
    }

    @Test
    fun `runOnce returns success when pending is empty`() = runTest {
        fakeSmsDao.pendingMessages = emptyList()
        val stats = processor.runOnce()
        assertTrue(stats.skipped)
    }

    // --- Normal batch processing ---

    @Test
    fun `runOnce loads model and processes all pending messages`() = runTest {
        fakeSmsDao.pendingMessages = listOf(smsEntity(id = 1L), smsEntity(id = 2L))

        val stats = processor.runOnce()

        assertTrue("ensureLoaded must be called when there are pending messages", fakeLlm.ensureLoadedCalled)
        assertTrue("close must be called after the batch", fakeLlm.closeCalled)
        assertFalse(stats.skipped)
        assertEquals(2, stats.processed)
        assertEquals(0, stats.errors)
    }

    @Test
    fun `runOnce marks transaction messages with PROCESSED status`() = runTest {
        fakeLlm.extractResult = ExtractionResult(
            isTransaction = true, direction = null, amount = 100.0,
            currency = "INR", dateText = null, counterparty = null,
            category = null, confidence = 0.9, raw = "{}",
        )
        fakeSmsDao.pendingMessages = listOf(smsEntity(id = 1L))

        processor.runOnce()

        val recorded = fakeSmsDao.statusUpdates[1L]
        assertEquals(ProcessingStatus.PROCESSED, recorded?.first)
    }

    @Test
    fun `runOnce marks non-transaction messages with NON_TXN status`() = runTest {
        fakeLlm.extractResult = ExtractionResult(
            isTransaction = false, direction = null, amount = null,
            currency = null, dateText = null, counterparty = null,
            category = null, confidence = 0.1, raw = "{}",
        )
        fakeSmsDao.pendingMessages = listOf(smsEntity(id = 5L))

        processor.runOnce()

        val recorded = fakeSmsDao.statusUpdates[5L]
        assertEquals(ProcessingStatus.NON_TXN, recorded?.first)
    }

    // --- Per-message error isolation ---

    @Test
    fun `runOnce continues processing after a per-message error`() = runTest {
        fakeSmsDao.pendingMessages = listOf(
            smsEntity(id = 10L),
            smsEntity(id = 11L),
            smsEntity(id = 12L),
        )
        var callCount = 0
        fakeLlm.extractAction = {
            callCount++
            if (callCount == 2) throw RuntimeException("simulated extraction failure")
            ExtractionResult(
                isTransaction = true, direction = null, amount = 50.0,
                currency = "INR", dateText = null, counterparty = null,
                category = null, confidence = 0.8, raw = "{}",
            )
        }

        val stats = processor.runOnce()

        assertEquals(2, stats.processed)
        assertEquals(1, stats.errors)
        assertEquals(ProcessingStatus.PROCESSED, fakeSmsDao.statusUpdates[10L]?.first)
        assertEquals(ProcessingStatus.ERROR, fakeSmsDao.statusUpdates[11L]?.first)
        assertEquals(ProcessingStatus.PROCESSED, fakeSmsDao.statusUpdates[12L]?.first)
    }

    @Test
    fun `runOnce stores error class name (not message) for failed message`() = runTest {
        fakeSmsDao.pendingMessages = listOf(smsEntity(id = 20L))
        fakeLlm.extractAction = { throw RuntimeException("bad parse - contains SMS body") }

        processor.runOnce()

        val (status, error) = fakeSmsDao.statusUpdates[20L]!!
        assertEquals(ProcessingStatus.ERROR, status)
        // §13 privacy: only the exception class name is stored, never the message (which could echo SMS content)
        assertEquals("RuntimeException", error)
        assertFalse("error must NOT contain the exception message text", error?.contains("bad parse") == true)
    }

    // --- ensureLoaded failure propagation ---

    @Test
    fun `runOnce propagates exception from ensureLoaded without calling close`() = runTest {
        fakeSmsDao.pendingMessages = listOf(smsEntity(id = 40L))
        fakeLlm.ensureLoadedThrows = RuntimeException("model load failed")

        val thrownException = runCatching { processor.runOnce() }.exceptionOrNull()

        // The exception must propagate so the worker can schedule a retry
        assertEquals(
            "Expected ensureLoaded exception to propagate",
            "model load failed",
            thrownException?.message,
        )
        // Nothing was loaded, so nothing needs to be closed
        assertFalse("close must NOT be called when ensureLoaded throws", fakeLlm.closeCalled)
    }

    @Test
    fun `runOnce always closes model even when all messages error`() = runTest {
        fakeSmsDao.pendingMessages = listOf(smsEntity(id = 30L))
        fakeLlm.extractAction = { throw RuntimeException("fail") }

        processor.runOnce()

        assertTrue("close() must always be called", fakeLlm.closeCalled)
    }

    @Test
    fun `runOnce respects maxMessagesPerRun from settings`() = runTest {
        fakeSettings.snap = fakeSettings.snap.copy(maxMessagesPerRun = 2)
        fakeSmsDao.pendingMessages = listOf(smsEntity(1L), smsEntity(2L))

        val stats = processor.runOnce()

        assertEquals(2, fakeSmsDao.lastCapturedLimit)
        assertEquals(2, stats.processed)
    }

    // --- Helpers ---

    private fun smsEntity(id: Long) = SmsMessageEntity(
        id = id,
        sender = "BANK",
        body = "body-$id",
        receivedAt = 0L,
        source = "LIVE",
        gatePassed = true,
        status = ProcessingStatus.PENDING,
    )
}

// ==================== Fakes ====================

private class FakeSmsDao : SmsDao {
    var pendingMessages: List<SmsMessageEntity> = emptyList()
    val statusUpdates = mutableMapOf<Long, Pair<ProcessingStatus, String?>>()
    var lastCapturedLimit: Int = -1

    override suspend fun insertIgnore(msg: SmsMessageEntity): Long = msg.id
    override suspend fun insert(msg: SmsMessageEntity): Long = msg.id

    override suspend fun pendingForProcessing(limit: Int): List<SmsMessageEntity> {
        lastCapturedLimit = limit
        return pendingMessages
    }

    override suspend fun setStatus(
        id: Long,
        status: ProcessingStatus,
        processedAt: Long?,
        error: String?,
    ): Int {
        statusUpdates[id] = Pair(status, error)
        return 1
    }

    override fun countPending(): Flow<Int> = flowOf(pendingMessages.size)
    override suspend fun getById(id: Long): SmsMessageEntity? = pendingMessages.find { it.id == id }
    override suspend fun updateStatus(id: Long, status: ProcessingStatus): Int = 1
}

private class FakeTransactionDao : TransactionDao {
    override suspend fun insertIfAbsent(txn: TransactionEntity): Long = txn.smsId
    override suspend fun update(txn: TransactionEntity): Int = 1
    override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(emptyList())
    override fun observeById(id: Long): Flow<TransactionEntity?> = flowOf(null)
    override suspend fun getBySmsId(smsId: Long): TransactionEntity? = null
    override fun sumByDirection(): Flow<List<DirectionSum>> = flowOf(emptyList())
    override fun byCategory(): Flow<List<CategorySum>> = flowOf(emptyList())
    override fun observeIncluded(): Flow<List<TransactionEntity>> = flowOf(emptyList())
    override suspend fun setCategory(id: Long, category: String?, now: Long): Int = 1
    override suspend fun setIncluded(id: Long, included: Boolean, now: Long): Int = 1
    override suspend fun markUserEdited(id: Long, now: Long): Int = 1
}

private class FakeLlmService : LlmService {
    var ensureLoadedCalled = false
    var closeCalled = false
    var ensureLoadedThrows: Exception? = null
    var extractResult: ExtractionResult = ExtractionResult(
        isTransaction = true, direction = null, amount = 100.0,
        currency = "INR", dateText = null, counterparty = null,
        category = null, confidence = 0.9, raw = "{}",
    )
    var extractAction: (() -> ExtractionResult)? = null

    override suspend fun ensureLoaded(pref: BackendChoice) {
        ensureLoadedThrows?.let { throw it }
        ensureLoadedCalled = true
    }

    override suspend fun extract(sms: String): ExtractionResult =
        extractAction?.invoke() ?: extractResult

    override fun loadedBackend(): String? = "CPU"
    override fun isLoaded(): Boolean = ensureLoadedCalled && !closeCalled
    override fun close() { closeCalled = true }
}

private class FakeSettingsAccess : SettingsAccess {
    var snap = SettingsSnapshot(
        processingIntervalMinutes = 30,
        requiresCharging = false,
        requiresBatteryNotLow = true,
        backendPreference = BackendChoice.AUTO,
        modelId = "qwen3_0_6b",
        maxMessagesPerRun = 50,
        confidenceThreshold = 0.0,
        lastRunAt = 0L,
        lastRunPendingCount = 0,
    )

    override suspend fun snapshot(): SettingsSnapshot = snap
    override suspend fun setLastRunAt(epochMs: Long) {}
    override suspend fun setLastRunPendingCount(count: Int) {}
}
