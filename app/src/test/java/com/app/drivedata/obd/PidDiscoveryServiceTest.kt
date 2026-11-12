package com.app.drivedata.obd

import com.app.drivedata.obd.model.DiscoveryFailure
import com.app.drivedata.obd.model.DiscoveryStage
import com.app.drivedata.obd.model.PidDiscoveryResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PidDiscoveryServiceTest {

    @Test
    fun walksTheAdvertisedContinuationAndStops() =
        runTest {
            val requested = mutableListOf<Int>()
            val responses =
                mapOf(
                    0x00 to "4100 BE1FA813",
                    0x20 to "4120 80000000",
                )
            val service =
                PidDiscoveryService(
                    query = { base ->
                        requested += base
                        responses[base]
                    },
                    clock = { 1_234L },
                )

            val result = service.discover()

            assertEquals(listOf(0x00, 0x20), requested)
            result as PidDiscoveryResult.Success
            assertEquals(1_234L, result.discoveredAtMillis)
            assertTrue(0x0C in result.pids)
            assertTrue(0x21 in result.pids)
            assertTrue(0x40 !in result.pids)
        }

    @Test
    fun stopsAtTheFirstFailureWithoutReturningAPartialSet() =
        runTest {
            val requested = mutableListOf<Int>()
            val service =
                PidDiscoveryService(
                    query = { base ->
                        requested += base
                        if (base == 0x00) "4100 BE1FA813" else "4120 8000"
                    },
                )

            val result = service.discover()

            assertEquals(listOf(0x00, 0x20), requested)
            assertEquals(
                PidDiscoveryResult.Failure(DiscoveryStage.RANGE_QUERY, DiscoveryFailure.TRUNCATED),
                result,
            )
        }

    @Test
    fun doesNotQueryFurtherRangesWhenTheFirstOneFails() =
        runTest {
            val requested = mutableListOf<Int>()
            val service =
                PidDiscoveryService(
                    query = { base ->
                        requested += base
                        "NO DATA"
                    },
                )

            val result = service.discover()

            assertEquals(listOf(0x00), requested)
            assertEquals(
                PidDiscoveryResult.Failure(DiscoveryStage.RANGE_QUERY, DiscoveryFailure.ECU_NO_DATA),
                result,
            )
        }

    @Test
    fun capsTheWalkAtFiveRanges() =
        runTest {
            val requested = mutableListOf<Int>()
            val service =
                PidDiscoveryService(
                    query = { base ->
                        requested += base
                        "41%02X 00000001".format(base)
                    },
                )

            val result = service.discover()

            assertEquals(listOf(0x00, 0x20, 0x40, 0x60, 0x80), requested)
            assertTrue(result is PidDiscoveryResult.Success)
        }

    @Test
    fun reportsProgressBeforeEachRangeQuery() =
        runTest {
            val progress = mutableListOf<Pair<Int, Int>>()
            val responses =
                mapOf(
                    0x00 to "4100 BE1FA813",
                    0x20 to "4120 80000000",
                )
            val service =
                PidDiscoveryService(
                    query = { base -> responses[base] },
                    onRange = { base, found -> progress += base to found },
                )

            val result = service.discover()

            assertEquals(listOf(0x00, 0x20), progress.map { it.first })
            assertEquals(0, progress.first().second)
            assertTrue(progress.last().second > 0)
            result as PidDiscoveryResult.Success
            assertTrue(0x0C in result.pids)
            assertTrue(0x21 in result.pids)
        }

    @Test
    fun reportsProgressOnlyForRangesItActuallyQueries() =
        runTest {
            val progress = mutableListOf<Int>()
            val service =
                PidDiscoveryService(
                    query = { "41%02X 00000001".format(it) },
                    onRange = { base, _ -> progress += base },
                )

            service.discover()

            assertEquals(listOf(0x00, 0x20, 0x40, 0x60, 0x80), progress)
        }

    @Test
    fun mapsTransportErrorsToConnectionLost() =
        runTest {
            val service = PidDiscoveryService(query = { throw IOException("socket closed") })

            assertEquals(
                PidDiscoveryResult.Failure(DiscoveryStage.RANGE_QUERY, DiscoveryFailure.CONNECTION_LOST),
                service.discover(),
            )
        }

    @Test
    fun mapsAMissingConnectionToConnectionLost() =
        runTest {
            val service = PidDiscoveryService(query = { null })

            assertEquals(
                PidDiscoveryResult.Failure(DiscoveryStage.RANGE_QUERY, DiscoveryFailure.CONNECTION_LOST),
                service.discover(),
            )
        }
}
