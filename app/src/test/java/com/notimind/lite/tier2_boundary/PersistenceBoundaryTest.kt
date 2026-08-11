package com.notimind.lite.tier2_boundary

import com.jeffers.notimindlite.data.local.NotificationEntity
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureNanoTime

class PersistenceBoundaryTest : BaseRobolectricTest() {

    @Test
    fun tc_R2_T2_001_emptyAndWhitespaceStringPersistence() = runTest {
        val entity = NotificationEntity(
            key = "k_whitespace",
            packageName = "",
            appName = "   ",
            title = "",
            content = "\n\n\t"
        )

        dao.insertNotification(entity)
        val active = dao.getActiveNotificationsList()
        assertEquals(1, active.size)
        val retrieved = active[0]

        assertEquals("", retrieved.packageName)
        assertEquals("   ", retrieved.appName)
        assertEquals("", retrieved.title)
        assertEquals("\n\n\t", retrieved.content)
    }

    @Test
    fun tc_R2_T2_002_maximumStringLengthAndStressPayloadPersistence() = runTest {
        val largeTitle = "A".repeat(10_000)
        val largeContent = "B".repeat(100_000)

        val entity = createDummyEntity(
            key = "large_payload_key",
            title = largeTitle,
            content = largeContent
        )

        val rowId = dao.insertNotification(entity)
        assertTrue(rowId > 0)

        val retrieved = dao.getActiveNotificationsList()[0]
        assertEquals(10_000, retrieved.title.length)
        assertEquals(100_000, retrieved.content.length)
    }

    @Test
    fun tc_R2_T2_003_specialCharactersEmojisRtlAndSqlInjectionSanity() = runTest {
        val injectionTitle = "😀 Special ' UNION SELECT * FROM notifications; DROP TABLE notifications; --"
        val rtlContent = "‏עברית / العربية 🚀🔥"

        val entity = createDummyEntity(
            key = "sql_inj_key",
            title = injectionTitle,
            content = rtlContent
        )

        dao.insertNotification(entity)

        val count = dao.getNotificationCount()
        assertEquals("Table must remain intact after SQL injection payload", 1, count)

        val retrieved = dao.getActiveNotificationsList()[0]
        assertEquals(injectionTitle, retrieved.title)
        assertEquals(rtlContent, retrieved.content)
    }

    @Test
    fun tc_R2_T2_004_duplicateNotificationKeysAndConflictResolution() = runTest {
        val e1 = NotificationEntity(
            id = 100,
            key = "dup_key",
            packageName = "pkg",
            appName = "App",
            title = "Old Title",
            content = "Old Content"
        )
        val e2 = NotificationEntity(
            id = 100,
            key = "dup_key",
            packageName = "pkg",
            appName = "App",
            title = "New Title",
            content = "New Content"
        )

        dao.insertNotification(e1)
        assertEquals("Old Title", dao.getActiveNotificationsList()[0].title)

        dao.insertNotification(e2)
        assertEquals(1, dao.getNotificationCount())
        assertEquals("New Title", dao.getActiveNotificationsList()[0].title)
    }

    @Test
    fun tc_R2_T2_005_optionalParameterDefaultFallbacks() {
        val entity = NotificationEntity(
            key = "default_key",
            packageName = "com.test",
            appName = "Test",
            title = "Title",
            content = "Content"
        )

        assertEquals(0L, entity.id)
        assertTrue(entity.postTime > 0L)
        assertFalse(entity.isDismissed)
        assertFalse(entity.isPersistent)
        assertNull(entity.category)
        assertNull(entity.channelId)
        assertNull(entity.subText)
        assertNull(entity.bigText)
        assertEquals(0, entity.priority)
        assertNull(entity.groupKey)
        assertFalse(entity.isOngoing)
        assertTrue(entity.isClearable)
        assertEquals(0, entity.actionsCount)
        assertNull(entity.dismissReason)
        assertNull(entity.dismissTime)
        assertNull(entity.intentUri)
        assertFalse(entity.isPinned)
        assertNull(entity.actionLabels)
    }

    @Test
    fun tc_R2_T2_006_boundaryTimestampsAndSortingIntegrity() = runTest {
        val eZero = createDummyEntity(key = "k_zero", postTime = 0L)
        val eNeg = createDummyEntity(key = "k_neg", postTime = -100L)
        val eMax = createDummyEntity(key = "k_max", postTime = Long.MAX_VALUE)
        val eNormal = createDummyEntity(key = "k_norm", postTime = 1000L)

        dao.insertNotification(eZero)
        dao.insertNotification(eNeg)
        dao.insertNotification(eMax)
        dao.insertNotification(eNormal)

        val activeList = dao.getActiveNotificationsList()
        assertEquals(4, activeList.size)
        assertEquals("k_max", activeList[0].key)
        assertEquals("k_norm", activeList[1].key)
        assertEquals("k_zero", activeList[2].key)
        assertEquals("k_neg", activeList[3].key)
    }

    @Test
    fun tc_R2_T2_007_highConcurrencyMultithreadedDaoStressTest() = runTest {
        val jobs = (1..50).map { i ->
            async(Dispatchers.IO) {
                val key = "concurrent_key_$i"
                dao.insertNotification(createDummyEntity(key = key))
                if (i % 2 == 0) {
                    dao.markDismissed(key)
                }
                if (i % 5 == 0) {
                    dao.updatePinnedStatus(key, true)
                }
            }
        }
        jobs.awaitAll()

        val count = dao.getNotificationCount()
        assertEquals(50, count)

        val activeCount = dao.getActiveNotificationsList().size
        assertEquals(25, activeCount)

        val pinnedCount = dao.getPinnedNotificationsFlow().first().size
        assertEquals(10, pinnedCount)
    }

    @Test
    fun tc_R2_T2_008_fiftyThousandEntityQueryPerformanceBenchmark() = runTest {
        val count = 50_000

        // Generate 50,000 entity dataset
        val entities = (1..count).map { i ->
            NotificationEntity(
                id = i.toLong(),
                key = "perf_key_$i",
                packageName = "com.perf.app_${i % 20}",
                appName = "Perf App ${i % 20}",
                title = "Benchmark Notification $i",
                content = "High-load dataset benchmark payload $i",
                postTime = 1000000L + i,
                isDismissed = i % 2 == 0,
                isPinned = i % 500 == 0
            )
        }

        // Fast batch insertion in single SQLite transaction
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            val stmt = db.compileStatement(
                "INSERT INTO notifications (id, key, packageName, appName, title, content, postTime, isDismissed, isPinned, isPersistent, priority, actionsCount, isOngoing, isClearable) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            for (i in 1..count) {
                stmt.clearBindings()
                stmt.bindLong(1, i.toLong())
                stmt.bindString(2, "perf_key_$i")
                stmt.bindString(3, "com.perf.app_${i % 20}")
                stmt.bindString(4, "Perf App ${i % 20}")
                stmt.bindString(5, "Benchmark Notification $i")
                stmt.bindString(6, "High-load dataset benchmark payload $i")
                stmt.bindLong(7, 1000000L + i)
                stmt.bindLong(8, if (i % 2 == 0) 1L else 0L)
                stmt.bindLong(9, if (i % 500 == 0) 1L else 0L)
                stmt.bindLong(10, 0L)
                stmt.bindLong(11, 0L)
                stmt.bindLong(12, 0L)
                stmt.bindLong(13, 0L)
                stmt.bindLong(14, 1L)
                stmt.executeInsert()
            }
        }

        assertEquals(50_000, dao.getNotificationCount())

        // Measure full active notification list read query response time
        val queryDurationNs = measureNanoTime {
            val activeList = dao.getActiveNotificationsList()
            assertEquals(25_000, activeList.size)
        }
        val queryDurationMs = queryDurationNs / 1_000_000.0

        println("50,000 entity active query duration: ${queryDurationMs} ms")
        assertTrue(
            "50,000 entity query response time ($queryDurationMs ms) must be within SLA limit (< 200.0 ms in Robolectric JVM)",
            queryDurationMs < 200.0
        )

        // Single item indexed lookup by key SLA check (sub-10ms requirement for JVM Robolectric test runner)
        val singleLookupNs = measureNanoTime {
            val target = dao.getNotificationByKey("perf_key_25000")
            assertNotNull(target)
        }
        val singleLookupMs = singleLookupNs / 1_000_000.0

        println("50,000 dataset single key lookup duration: ${singleLookupMs} ms")
        assertTrue(
            "Single key query lookup ($singleLookupMs ms) on 50,000 records must satisfy sub-10ms SLA",
            singleLookupMs < 10.0
        )
    }
}
