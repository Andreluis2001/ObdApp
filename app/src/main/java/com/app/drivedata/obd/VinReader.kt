package com.app.drivedata.obd

import com.app.drivedata.logging.AppLog
import com.github.eltonvs.obd.command.BadResponseException
import com.github.eltonvs.obd.command.control.VINCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Reads the raw VIN from the ECU (OBD mode 09, PID 02).
 *
 * The command runs under the connection manager's exclusive session so it cannot interleave with
 * PID discovery or the metrics poll. A vehicle that does not support the VIN query is not an error;
 * this returns `null` and the caller continues without identity data.
 */
class VinReader(
    private val connectionManager: ObdConnectionManager,
) {

    suspend fun read(): String? =
        withContext(Dispatchers.IO) {
            try {
                connectionManager.withExclusiveSession { runner ->
                    runner(VINCommand())?.value?.trim()?.ifBlank { null }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: BadResponseException) {
                AppLog.i("VIN query returned no usable data: $error")
                null
            } catch (error: IllegalArgumentException) {
                AppLog.i("VIN query returned a malformed value: $error")
                null
            } catch (error: IndexOutOfBoundsException) {
                AppLog.i("VIN query returned a truncated value: $error")
                null
            } catch (error: IOException) {
                AppLog.w("VIN query failed with an I/O error", error)
                null
            } catch (error: Exception) {
                AppLog.w("VIN query failed for an unknown reason", error)
                null
            }
        }
}
