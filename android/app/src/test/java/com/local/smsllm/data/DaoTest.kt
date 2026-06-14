package com.local.smsllm.data

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.local.smsllm.domain.ProcessingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var smsDao: SmsDao
    private lateinit var txnDao: TransactionDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        smsDao = db.smsDao()
        txnDao = db.transactionDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // ----- Helpers -----

    private fun sms(
        sender: String = "VM-HDFC",
        body: String = "INR 1000 debited",
        receivedAt: Long = 1_000_000L,
        status: ProcessingStatus = ProcessingStatus.PENDING,
        gatePassed: Boolean = true,
        source: String = "LIVE",
    ) = SmsMessageEntity(
        sender = sender,
        body = body,
        receivedAt = receivedAt,
        source = source,
        gatePassed = gatePassed,
        status = status,
    )

    private fun txn(
        smsId: Long,
        includedInAnalytics: Boolean = true,
        amount: Double? = 500.0,
        category: String? = "food",
        direction: String? = "DEBIT",
        now: Long = System.currentTimeMillis(),
    ) = TransactionEntity(
        smsId = smsId,
        isTransaction = true,
        direction = direction,
        amount = amount,
        currency = "INR",
        confidence = 0.9,
        rawModelOutput = """{"isTransaction":true}""",
        modelId = "qwen3-0.6b",
        backend = "LITERTLM",
        includedInAnalytics = includedInAnalytics,
        category = category,
        createdAt = now,
        updatedAt = now,
    )

    // ----- SmsDao tests -----

    @Test
    fun `insert PENDING sms appears in pendingForProcessing`() = runTest {
        val id = smsDao.insert(sms(status = ProcessingStatus.PENDING))
        val pending = smsDao.pendingForProcessing(10)
        assertEquals(1, pending.size)
        assertEquals(id, pending[0].id)
    }

    @Test
    fun `setStatus PROCESSED removes sms from pending`() = runTest {
        val id = smsDao.insert(sms(status = ProcessingStatus.PENDING))
        smsDao.setStatus(id, ProcessingStatus.PROCESSED, System.currentTimeMillis(), null)
        val pending = smsDao.pendingForProcessing(10)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `insertIgnore deduplicates on sender+body+receivedAt`() = runTest {
        val base = sms(sender = "VM-HDFC", body = "Credit 500", receivedAt = 12345L)
        val id1 = smsDao.insertIgnore(base)
        val id2 = smsDao.insertIgnore(base.copy(source = "IMPORT"))
        assertTrue(id1 > 0)
        assertEquals(-1L, id2)
        // Only one row should exist
        val all = smsDao.pendingForProcessing(100)
        assertEquals(1, all.size)
    }

    @Test
    fun `NEEDS_REVERIFY and ERROR appear in pending but GATE_REJECTED does not`() = runTest {
        smsDao.insert(sms(status = ProcessingStatus.NEEDS_REVERIFY, body = "body1", receivedAt = 1L))
        smsDao.insert(sms(status = ProcessingStatus.ERROR, body = "body2", receivedAt = 2L))
        smsDao.insert(sms(status = ProcessingStatus.GATE_REJECTED, body = "body3", receivedAt = 3L))

        val pending = smsDao.pendingForProcessing(100)
        assertEquals(2, pending.size)
        assertTrue(pending.none { it.status == ProcessingStatus.GATE_REJECTED })
    }

    @Test
    fun `ProcessingStatus TypeConverter round-trips`() = runTest {
        val id = smsDao.insert(sms(status = ProcessingStatus.NEEDS_REVERIFY))
        val retrieved = smsDao.getById(id)
        assertNotNull(retrieved)
        assertEquals(ProcessingStatus.NEEDS_REVERIFY, retrieved!!.status)
    }

    // ----- TransactionDao tests -----

    @Test
    fun `observeIncluded returns only includedInAnalytics=true rows`() = runTest {
        val smsId1 = smsDao.insert(sms(body = "body1", receivedAt = 1L))
        val smsId2 = smsDao.insert(sms(body = "body2", receivedAt = 2L))

        txnDao.upsert(txn(smsId = smsId1, includedInAnalytics = true))
        txnDao.upsert(txn(smsId = smsId2, includedInAnalytics = false))

        val included = txnDao.observeIncluded().first()
        assertEquals(1, included.size)
        assertEquals(smsId1, included[0].smsId)
    }

    @Test
    fun `byCategory excludes includedInAnalytics=false rows`() = runTest {
        val smsId1 = smsDao.insert(sms(body = "body1", receivedAt = 1L))
        val smsId2 = smsDao.insert(sms(body = "body2", receivedAt = 2L))

        txnDao.upsert(txn(smsId = smsId1, includedInAnalytics = true, amount = 200.0, category = "food"))
        txnDao.upsert(txn(smsId = smsId2, includedInAnalytics = false, amount = 999.0, category = "food"))

        val cats = txnDao.byCategory().first()
        val foodEntry = cats.firstOrNull { it.category == "food" }
        assertNotNull(foodEntry)
        // Should only include the 200.0 row, not the 999.0 excluded row
        assertEquals(200.0, foodEntry!!.total, 0.001)
        assertEquals(1, foodEntry.count)
    }

    @Test
    fun `upsert twice for same smsId keeps only one row with second values`() = runTest {
        val smsId = smsDao.insert(sms())

        val now = System.currentTimeMillis()
        val firstId = txnDao.upsert(txn(smsId = smsId, amount = 100.0, category = "food", now = now))
        val secondId = txnDao.upsert(txn(smsId = smsId, amount = 250.0, category = "shopping", now = now + 1000))

        val all = txnDao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(250.0, all[0].amount!!, 0.001)
        assertEquals("shopping", all[0].category)
        // id must be stable across re-extraction
        assertEquals(firstId, secondId)
        assertEquals(firstId, all[0].id)
        // createdAt is preserved; updatedAt reflects the new value
        assertEquals(now, all[0].createdAt)
        assertEquals(now + 1000, all[0].updatedAt)
    }

    @Test
    fun `sumByDirection excludes includedInAnalytics=false and sums correctly`() = runTest {
        val smsId1 = smsDao.insert(sms(body = "debit body", receivedAt = 10L))
        val smsId2 = smsDao.insert(sms(body = "credit body", receivedAt = 11L))
        val smsId3 = smsDao.insert(sms(body = "excluded body", receivedAt = 12L))

        txnDao.upsert(txn(smsId = smsId1, direction = "DEBIT", amount = 300.0, includedInAnalytics = true))
        txnDao.upsert(txn(smsId = smsId2, direction = "CREDIT", amount = 150.0, includedInAnalytics = true))
        txnDao.upsert(txn(smsId = smsId3, direction = "DEBIT", amount = 999.0, includedInAnalytics = false))

        val sums = txnDao.sumByDirection().first()
        val debitSum = sums.firstOrNull { it.direction == "DEBIT" }
        val creditSum = sums.firstOrNull { it.direction == "CREDIT" }
        assertNotNull(debitSum)
        assertNotNull(creditSum)
        assertEquals(300.0, debitSum!!.total, 0.001)
        assertEquals(150.0, creditSum!!.total, 0.001)
    }

    @Test
    fun `getBySmsId returns inserted transaction`() = runTest {
        val smsId = smsDao.insert(sms())
        txnDao.upsert(txn(smsId = smsId))
        val result = txnDao.getBySmsId(smsId)
        assertNotNull(result)
        assertEquals(smsId, result!!.smsId)
    }

    @Test
    fun `setCategory updates category and marks userEdited`() = runTest {
        val smsId = smsDao.insert(sms())
        val txnId = txnDao.upsert(txn(smsId = smsId, category = "food"))
        val now = System.currentTimeMillis()
        txnDao.setCategory(txnId, "transport", now)

        val updated = txnDao.observeById(txnId).first()
        assertNotNull(updated)
        assertEquals("transport", updated!!.category)
        assertTrue(updated.userEdited)
    }

    @Test
    fun `setIncluded toggles includedInAnalytics`() = runTest {
        val smsId = smsDao.insert(sms())
        val txnId = txnDao.upsert(txn(smsId = smsId, includedInAnalytics = true))
        val now = System.currentTimeMillis()
        txnDao.setIncluded(txnId, false, now)

        val included = txnDao.observeIncluded().first()
        assertTrue(included.isEmpty())
    }

    @Test
    fun `countPending flow reflects current state`() = runTest {
        assertEquals(0, smsDao.countPending().first())
        smsDao.insert(sms(status = ProcessingStatus.PENDING, body = "b1", receivedAt = 1L))
        smsDao.insert(sms(status = ProcessingStatus.ERROR, body = "b2", receivedAt = 2L))
        assertEquals(2, smsDao.countPending().first())
    }

    @Test
    fun `updateStatus changes status correctly`() = runTest {
        val id = smsDao.insert(sms(status = ProcessingStatus.PENDING))
        smsDao.updateStatus(id, ProcessingStatus.NEEDS_REVERIFY)
        val entity = smsDao.getById(id)
        assertEquals(ProcessingStatus.NEEDS_REVERIFY, entity!!.status)
    }
}
