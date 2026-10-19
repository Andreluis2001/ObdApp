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

internal suspend fun initializeAdapter(connection: ObdDeviceConnection) {
    connection.run(ResetAdapterCommand())
    connection.run(SetEchoCommand(Switcher.OFF))
    connection.run(SetLineFeedCommand(Switcher.OFF))
    connection.run(SetSpacesCommand(Switcher.OFF))
    connection.run(SetHeadersCommand(Switcher.OFF))
    connection.run(SetAdaptiveTimingCommand(AdaptiveTimingMode.AUTO_1))
    connection.run(SelectProtocolCommand(ObdProtocols.AUTO))
}

internal suspend fun describeProtocol(connection: ObdDeviceConnection): String? =
    runCatching { connection.run(DescribeProtocolCommand()).value.trim().ifBlank { null } }.getOrNull()

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

    runCatching { connection.run(RPMCommand()) }
    return EcuStatus.VERIFIED
}
