package com.app.drivedata.obd

import com.app.drivedata.obd.model.DiscoveryFailure
import com.app.drivedata.obd.model.DiscoveryStage
import com.app.drivedata.obd.model.PidDiscoveryResult
import com.github.eltonvs.obd.command.BadResponseException
import com.github.eltonvs.obd.command.NoDataException
import com.github.eltonvs.obd.command.NonNumericResponseException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Walks the supported-PIDs ranges advertised by the ECU.
 *
 * [query] returns the raw adapter response for `01 <base>`; it is injected so the walk can be
 * tested without a socket. In production it must run inside
 * [ObdConnectionManager.withExclusiveSession] so discovery never interleaves with the metrics poll.
 *
 * Discovery stops at the first failure and returns no partial result.
 */
class PidDiscoveryService(
    private val query: suspend (base: Int) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onRange: (rangeBase: Int, foundCount: Int) -> Unit = { _, _ -> },
) {

    suspend fun discover(): PidDiscoveryResult {
        val pids = mutableSetOf<Int>()
        for (base in RANGE_BASES) {
            onRange(base, pids.size)
            val raw =
                try {
                    query(base)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: SocketTimeoutException) {
                    return failure(DiscoveryFailure.TIMEOUT)
                } catch (_: IOException) {
                    return failure(DiscoveryFailure.CONNECTION_LOST)
                } catch (_: NoDataException) {
                    return failure(DiscoveryFailure.ECU_NO_DATA)
                } catch (_: NonNumericResponseException) {
                    return failure(DiscoveryFailure.MALFORMED)
                } catch (_: BadResponseException) {
                    return failure(DiscoveryFailure.MALFORMED)
                } catch (_: IllegalArgumentException) {
                    return failure(DiscoveryFailure.MALFORMED)
                } catch (_: Exception) {
                    return failure(DiscoveryFailure.UNKNOWN)
                }

            if (raw == null) return failure(DiscoveryFailure.CONNECTION_LOST)

            when (val result = PidBitmap.decode(raw, base)) {
                is BitmapResult.Ok -> {
                    pids += result.pids
                    if (!result.supportsNextRange) {
                        return PidDiscoveryResult.Success(pids, clock())
                    }
                }

                is BitmapResult.Failure -> return failure(result.reason.toDiscoveryFailure())
            }
        }
        return PidDiscoveryResult.Success(pids, clock())
    }

    private fun failure(reason: DiscoveryFailure): PidDiscoveryResult.Failure =
        PidDiscoveryResult.Failure(DiscoveryStage.RANGE_QUERY, reason)

    private fun BitmapFailure.toDiscoveryFailure(): DiscoveryFailure =
        when (this) {
            BitmapFailure.NO_DATA -> DiscoveryFailure.ECU_NO_DATA
            BitmapFailure.SERVICE_MISMATCH -> DiscoveryFailure.SERVICE_MISMATCH
            BitmapFailure.TRUNCATED -> DiscoveryFailure.TRUNCATED
            BitmapFailure.MALFORMED -> DiscoveryFailure.MALFORMED
        }

    companion object {
        /** Ranges matching kotlin-obd-api's `AvailablePIDsRanges`; five is also the hard cap. */
        private val RANGE_BASES = listOf(0x00, 0x20, 0x40, 0x60, 0x80)
    }
}
