package com.app.drivedata.obd

import com.app.drivedata.logging.AppLog
import com.app.drivedata.obd.model.EcuStatus
import com.github.eltonvs.obd.command.AdaptiveTimingMode
import com.github.eltonvs.obd.command.BadResponseException
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdProtocols
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.Switcher
import com.github.eltonvs.obd.command.at.DescribeProtocolCommand
import com.github.eltonvs.obd.command.at.ResetAdapterCommand
import com.github.eltonvs.obd.command.at.SelectProtocolCommand
import com.github.eltonvs.obd.command.at.SetAdaptiveTimingCommand
import com.github.eltonvs.obd.command.at.SetEchoCommand
import com.github.eltonvs.obd.command.at.SetHeadersCommand
import com.github.eltonvs.obd.command.at.SetLineFeedCommand
import com.github.eltonvs.obd.command.at.SetSpacesCommand
import com.github.eltonvs.obd.command.control.AvailablePIDsCommand
import com.github.eltonvs.obd.command.engine.RPMCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.CancellationException
import java.io.IOException

internal suspend fun initializeAdapter(connection: ObdDeviceConnection) {
    runLogged(connection, ResetAdapterCommand())
    runLogged(connection, SetEchoCommand(Switcher.OFF))
    runLogged(connection, SetLineFeedCommand(Switcher.OFF))
    runLogged(connection, SetSpacesCommand(Switcher.OFF))
    runLogged(connection, SetHeadersCommand(Switcher.OFF))
    runLogged(connection, SetAdaptiveTimingCommand(AdaptiveTimingMode.AUTO_1))
    runLogged(connection, SelectProtocolCommand(ObdProtocols.AUTO))
}

internal suspend fun describeProtocol(connection: ObdDeviceConnection): String? =
    try {
        runLogged(connection, DescribeProtocolCommand()).value.trim().ifBlank { null }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AppLog.w("OBD protocol description failed", error)
        null
    }

internal suspend fun verifyEcuCommunication(connection: ObdDeviceConnection): EcuStatus {
    val ecuAnswered =
        try {
            runLogged(
                connection,
                AvailablePIDsCommand(AvailablePIDsCommand.AvailablePIDsRanges.PIDS_01_TO_20),
                retryOnSearching = true,
            )
                .value
                .isNotBlank()
        } catch (_: BadResponseException) {
            false
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            true
        }
    if (!ecuAnswered) {
        AppLog.w("OBD ECU did not answer the supported-PIDs request")
        return EcuStatus.NO_COMMUNICATION
    }

    try {
        runLogged(connection, RPMCommand(), retryOnSearching = true)
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        throw error
    } catch (error: Exception) {
        AppLog.w("OBD RPM probe failed", error)
    }
    return EcuStatus.VERIFIED
}

/** Runs an adapter command and logs the raw bytes the ELM327 answered with. */
private suspend fun runLogged(
    connection: ObdDeviceConnection,
    command: ObdCommand,
    retryOnSearching: Boolean = false,
): ObdResponse {
    val response =
        if (retryOnSearching) {
            connection.runWithSearchingRetry(command)
        } else {
            connection.run(command)
        }
    AppLog.d(
        "OBD '${command.rawCommand}' -> raw='${AppLog.raw(response.rawResponse.value)}' " +
            "value='${response.formattedValue}'",
    )
    return response
}
