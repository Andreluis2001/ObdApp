package com.app.drivedata.metrics

import com.app.drivedata.logging.AppLog
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/** Outcome of an explicit "start/continue collection" action from the UI. */
enum class CollectionStartResult {
    STARTED,
    ALREADY_COLLECTING,
    NO_ACTIVE_VEHICLE,
    NOT_READY,
    NO_ADAPTER,
    ADAPTER_BUSY,
    CONNECTION_FAILED,
    ECU_UNAVAILABLE,
    ;

    val isFailure: Boolean
        get() =
            when (this) {
                STARTED,
                ALREADY_COLLECTING,
                ADAPTER_BUSY,
                -> false

                NO_ACTIVE_VEHICLE,
                NOT_READY,
                NO_ADAPTER,
                CONNECTION_FAILED,
                ECU_UNAVAILABLE,
                -> true
            }
}

/**
 * Starts collection after an explicit user action, reconnecting to the active vehicle's saved
 * adapter address only when the live link is no longer usable.
 *
 * A normal pause keeps the adapter socket open, so resuming does not reconnect at all. After an
 * app restart or a dropped link, the IP/port saved with the active vehicle are reused without
 * asking the user or re-running PID discovery.
 */
class MetricsCollectionStarter(
    private val activeVehicle: StateFlow<ActiveVehicleCollection?>,
    private val connectionState: StateFlow<ObdConnectionState>,
    private val connect: suspend (ObdWifiConfig) -> Unit,
    private val controller: MetricsCollectionController,
) {
    suspend fun start(): CollectionStartResult {
        if (controller.state.value.isCollecting) return CollectionStartResult.ALREADY_COLLECTING

        val vehicle = activeVehicle.value ?: return CollectionStartResult.NO_ACTIVE_VEHICLE
        if (!vehicle.canCollect) return CollectionStartResult.NOT_READY
        val config = vehicle.adapterConfig ?: return CollectionStartResult.NO_ADAPTER

        when (val current = connectionState.value) {
            is ObdConnectionState.Connecting,
            is ObdConnectionState.Initializing,
            -> return CollectionStartResult.ADAPTER_BUSY

            is ObdConnectionState.Connected -> {
                if (vehicle.matches(current.config)) {
                    if (current.ecuStatus != EcuStatus.VERIFIED) {
                        return CollectionStartResult.ECU_UNAVAILABLE
                    }
                    return beginCollection()
                }
            }

            else -> Unit
        }

        try {
            connect(config)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.w("Could not reconnect to the saved adapter address on resume", error)
            return CollectionStartResult.CONNECTION_FAILED
        }

        val current = connectionState.value
        if (current !is ObdConnectionState.Connected || !vehicle.matches(current.config)) {
            return CollectionStartResult.CONNECTION_FAILED
        }
        if (current.ecuStatus != EcuStatus.VERIFIED) {
            return CollectionStartResult.ECU_UNAVAILABLE
        }
        return beginCollection()
    }

    fun stop() {
        controller.stopCollection()
    }

    private fun beginCollection(): CollectionStartResult {
        controller.startCollection()
        return CollectionStartResult.STARTED
    }
}
