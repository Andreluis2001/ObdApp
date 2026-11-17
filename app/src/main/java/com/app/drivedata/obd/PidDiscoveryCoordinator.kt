package com.app.drivedata.obd

import com.app.drivedata.obd.model.PidDiscoveryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
