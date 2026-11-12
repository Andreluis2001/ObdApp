package com.app.drivedata.obd

import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdRawResponse

/**
 * Supported-PID request that keeps the raw adapter frame in `rawResponse.value`.
 *
 * The library's `AvailablePIDsCommand` already flattens the bitmap in its handler and fails on
 * multi-ECU frames before the raw value is available, which would hide the data [PidBitmap] needs.
 */
internal class RawAvailablePidsCommand(base: Int) : ObdCommand() {
    private val baseByte = base and 0xFF

    override val tag = "AVAILABLE_PIDS_RAW_%02X".format(baseByte)
    override val name = "Available PIDs (raw) 0x%02X".format(baseByte)
    override val mode = "01"
    override val pid = "%02X".format(baseByte)
    override val handler: (ObdRawResponse) -> String = { it.value }
}
