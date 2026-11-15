package com.app.drivedata.vehicle

import com.app.drivedata.logging.AppLog
import com.app.drivedata.vehicle.model.VehicleIdentity
import kotlinx.coroutines.CancellationException

/**
 * Combines the raw OBD VIN read with the offline decode.
 *
 * Both steps are injected so the pairing flow can be tested without a socket, and so a failed read
 * degrades to `null` instead of failing the setup.
 */
class VehicleIdentityExtractor(
    private val readRawVin: suspend () -> String?,
    private val decode: (String?) -> VehicleIdentity? = VehicleIdentityDecoder::decode,
) {

    suspend fun read(): VehicleIdentity? =
        try {
            decode(readRawVin())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.w("Vehicle identity could not be extracted", error)
            null
        }
}
