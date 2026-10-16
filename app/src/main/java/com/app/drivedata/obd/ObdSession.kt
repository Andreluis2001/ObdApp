package com.app.drivedata.obd

import com.app.drivedata.obd.model.EcuStatus
import com.github.eltonvs.obd.command.AdaptiveTimingMode
import com.github.eltonvs.obd.command.BadResponseException
import com.github.eltonvs.obd.command.ObdProtocols
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

/**
 * Runs the recommended low-noise ELM327 setup sequence.
 *
 * Throws on adapter-level failures so the caller can distinguish a broken adapter
 * link from an unreachable ECU.
 */
internal suspend fun initializeAdapter(connection: ObdDeviceConnection) {
    connection.run(ResetAdapterCommand())
    connection.run(SetEchoCommand(Switcher.OFF))
    connection.run(SetLineFeedCommand(Switcher.OFF))
    connection.run(SetSpacesCommand(Switcher.OFF))
    connection.run(SetHeadersCommand(Switcher.OFF))
    connection.run(SetAdaptiveTimingCommand(AdaptiveTimingMode.AUTO_1))
    connection.run(SelectProtocolCommand(ObdProtocols.AUTO))
}

/** Best-effort negotiated protocol description; null when the adapter will not report it. */
internal suspend fun describeProtocol(connection: ObdDeviceConnection): String? =
    runCatching { connection.run(DescribeProtocolCommand()).value.trim().ifBlank { null } }.getOrNull()

/**
 * Confirms the adapter can actually talk to the ECU by querying the mandatory
 * "supported PIDs (01-20)" range.
 *
 * A `NO DATA` / `UNABLE TO CONNECT` style response means the ECU is not answering and is
 * reported as [EcuStatus.NO_COMMUNICATION] (never as a valid zero reading). Transport-level
 * `IOException`s are left to propagate so the caller can report a broken adapter link instead.
 */
internal suspend fun verifyEcuCommunication(connection: ObdDeviceConnection): EcuStatus {
    val supportedPids =
        try {
            connection.run(AvailablePIDsCommand(AvailablePIDsCommand.AvailablePIDsRanges.PIDS_01_TO_20))
        } catch (_: BadResponseException) {
            return EcuStatus.NO_COMMUNICATION
        }
    if (supportedPids.value.isBlank()) {
        return EcuStatus.NO_COMMUNICATION
    }

    // The ECU answered a real Mode 01 request, so it is communicating. RPM is a best-effort
    // extra smoke test and may legitimately be unsupported on some vehicles.
    runCatching { connection.run(RPMCommand()) }
    return EcuStatus.VERIFIED
}
