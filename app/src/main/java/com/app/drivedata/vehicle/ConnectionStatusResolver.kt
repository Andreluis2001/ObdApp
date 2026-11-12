package com.app.drivedata.vehicle

import com.app.drivedata.vehicle.model.ConnectionStatus

/**
 * Resolves the durable status stored when a vehicle is created.
 *
 * Only the current adapter link determines `CONNECTED`; a configured but failed attempt is stored
 * as `ERROR`, and a configuration that was never tested as `DISCONNECTED`.
 */
internal fun resolveConnectionStatus(
    configured: Boolean,
    attempted: Boolean,
    currentlyConnected: Boolean,
): ConnectionStatus =
    when {
        !configured -> ConnectionStatus.NOT_CONFIGURED
        currentlyConnected -> ConnectionStatus.CONNECTED
        attempted -> ConnectionStatus.ERROR
        else -> ConnectionStatus.DISCONNECTED
    }
