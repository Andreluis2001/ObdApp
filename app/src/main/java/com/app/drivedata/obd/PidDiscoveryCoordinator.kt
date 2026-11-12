package com.app.drivedata.obd

import com.app.drivedata.obd.model.PidDiscoveryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a supported-PIDs walk while holding the OBD session lock.
 *
 * The locked runner is the only way the discovery queries may reach the adapter, so a metrics poll
 * cannot interleave with the walk. The whole sequence runs on [Dispatchers.IO] because the OBD
 * command execution performs blocking socket I/O.
 */
class PidDiscoveryCoordinator(
    private val connectionManager: ObdConnectionManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun discover(
        onProgress: (rangeBase: Int, foundCount: Int) -> Unit = { _, _ -> },
    ): PidDiscoveryResult =
        withContext(Dispatchers.IO) {
            connectionManager.withExclusiveSession { runner ->
                PidDiscoveryService(
                    query = { base ->
                        runner(RawAvailablePidsCommand(base))?.rawResponse?.value
                    },
                    clock = clock,
                    onRange = onProgress,
                ).discover()
            }
        }
}
