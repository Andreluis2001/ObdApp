package com.app.drivedata.obd

import com.app.drivedata.logging.AppLog
import com.github.eltonvs.obd.command.NonNumericResponseException
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.connection.ObdDeviceConnection

/**
 * Runs [command] and retries it once when the adapter answered with a `SEARCHING...` preamble.
 *
 * Real ELM327 adapters (and the Ircama emulator) prefix the first OBD response after a
 * connection with `SEARCHING...`. kotlin-obd-api removes only the `SEARCHING` word, leaving
 * the trailing dots, which makes the otherwise valid response fail its hex validation with a
 * [NonNumericResponseException]. The preamble is only emitted once per connection, so a single
 * retry returns the clean response.
 */
internal suspend fun ObdDeviceConnection.runWithSearchingRetry(command: ObdCommand): ObdResponse =
    try {
        run(command)
    } catch (error: NonNumericResponseException) {
        AppLog.w(
            "OBD '${command.rawCommand}' received a SEARCHING... preamble, retrying once: $error",
        )
        run(command)
    }
