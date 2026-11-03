package com.app.drivedata.metrics

import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricsRepositoryTest {

    @Test
    fun save_persistsEveryMetricWithOwnerAndPendingStatus() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            val repository = MetricsRepository(dao, idGenerator = { "record-1" })

            repository.save(
                snapshot(
                    vin = "93YBSR7RHEJ298116",
                    readings =
                        listOf(
                            reading(MetricId.RPM, ReadingValue.Available(1065.0), "RPM"),
                            reading(MetricId.SPEED, ReadingValue.Available(42.0), "km/h"),
                            reading(MetricId.COOLANT_TEMPERATURE, ReadingValue.Available(91.0), "\u00B0C"),
                        ),
                    collectedAtMillis = 1_700_000_000_000L,
                ),
                ownerUsername = "admin",
            )

            val record = dao.records.single()
            assertEquals("record-1", record.id)
            assertEquals("admin", record.ownerUsername)
            assertEquals("93YBSR7RHEJ298116", record.vin)
            assertEquals(1_700_000_000_000L, record.collectedAtMillis)
            assertEquals(UploadStatus.PENDING, record.uploadStatus)
            assertNull(record.uploadedAtMillis)

            val saved = dao.readingsFor("record-1")
            assertEquals(3, saved.size)
            assertEquals(setOf("RPM", "SPEED", "COOLANT_TEMPERATURE"), saved.map { it.metricId }.toSet())
            assertEquals(1065.0, saved.first { it.metricId == "RPM" }.value!!, 0.001)
        }

    @Test
    fun save_preservesUnavailableMetricsExplicitly() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            val repository = MetricsRepository(dao, idGenerator = { "record-1" })

            repository.save(
                snapshot(
                    vin = null,
                    readings =
                        listOf(
                            reading(MetricId.RPM, ReadingValue.Available(900.0), "RPM"),
                            reading(MetricId.FUEL_LEVEL, ReadingValue.Unavailable, "%"),
                        ),
                    collectedAtMillis = 1L,
                ),
                ownerUsername = "admin",
            )

            val fuel = dao.readingsFor("record-1").first { it.metricId == "FUEL_LEVEL" }
            assertFalse(fuel.isAvailable)
            assertNull("unavailable must never be stored as zero", fuel.value)
        }

    @Test
    fun save_givesEachRecordAStableDistinctId() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            val repository = MetricsRepository(dao)

            repository.save(
                snapshot(
                    readings = listOf(reading(MetricId.RPM, ReadingValue.Available(800.0), "RPM")),
                    collectedAtMillis = 1L,
                ),
                "admin",
            )
            repository.save(
                snapshot(
                    readings = listOf(reading(MetricId.RPM, ReadingValue.Available(900.0), "RPM")),
                    collectedAtMillis = 2L,
                ),
                "admin",
            )

            assertEquals(2, dao.records.size)
            assertNotEquals(dao.records[0].id, dao.records[1].id)
        }

    @Test
    fun pruneUploadedBefore_removesOnlyOldUploadedRecords() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            val repository = MetricsRepository(dao, clock = { 10_000L })
            dao.add("old-uploaded", collectedAtMillis = 1L, status = UploadStatus.UPLOADED, uploadedAtMillis = 1_000L)
            dao.add("new-uploaded", collectedAtMillis = 2L, status = UploadStatus.UPLOADED, uploadedAtMillis = 9_000L)
            dao.add("old-pending", collectedAtMillis = 3L, status = UploadStatus.PENDING, uploadedAtMillis = 1_000L)

            val pruned = repository.pruneUploadedBefore(cutoffMillis = 5_000L)

            assertEquals(1, pruned)
            assertEquals(setOf("new-uploaded", "old-pending"), dao.records.map { it.id }.toSet())
        }

    private fun snapshot(
        vin: String? = null,
        readings: List<MetricReading>,
        collectedAtMillis: Long,
    ): MetricsSnapshot =
        MetricsSnapshot(
            timestampMillis = collectedAtMillis,
            vin = vin,
            readings = readings,
        )

    private fun reading(id: MetricId, value: ReadingValue, unit: String): MetricReading =
        MetricReading(id = id, unit = unit, value = value)
}
