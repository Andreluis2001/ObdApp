package com.app.drivedata.obd

object PidBitmap {

    fun decode(raw: String?, requestedPidBase: Int): BitmapResult {
        val normalized = raw.orEmpty().uppercase()
        if (normalized.contains(NO_DATA)) return BitmapResult.Failure(BitmapFailure.NO_DATA)

        val frames =
            normalized
                .split(LINE_BREAK)
                .map { line -> line.replace(SEARCHING, "").replace(SEPARATORS, "") }
                .filter { it.isNotEmpty() }
        if (frames.isEmpty()) return BitmapResult.Failure(BitmapFailure.NO_DATA)

        val header = SERVICE_SUPPORTED_PIDS + "%02X".format(requestedPidBase)
        val frame =
            frames.firstOrNull { it.startsWith(header) }
                ?: return BitmapResult.Failure(
                    if (frames.any { candidate -> candidate.any { !it.isHexDigit() } }) {
                        BitmapFailure.MALFORMED
                    } else {
                        BitmapFailure.SERVICE_MISMATCH
                    },
                )

        val payload = frame.removePrefix(header)
        if (payload.length != DATA_HEX_LENGTH) return BitmapResult.Failure(BitmapFailure.TRUNCATED)
        if (payload.any { !it.isHexDigit() }) return BitmapResult.Failure(BitmapFailure.MALFORMED)

        val pids =
            buildSet {
                payload.chunked(2).forEachIndexed { byteIndex, byteText ->
                    val byte = byteText.toInt(radix = 16)
                    repeat(BITS_PER_BYTE) { bit ->
                        val mask = 1 shl (BITS_PER_BYTE - 1 - bit)
                        if (byte and mask != 0) {
                            add(requestedPidBase + 1 + byteIndex * BITS_PER_BYTE + bit)
                        }
                    }
                }
            }
        return BitmapResult.Ok(pids, supportsNextRange = requestedPidBase + RANGE_SIZE in pids)
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'A'..'F'

    private const val SERVICE_SUPPORTED_PIDS = "41"
    private const val NO_DATA = "NO DATA"
    private const val SEARCHING = "SEARCHING"
    private const val DATA_BYTES = 4
    private const val DATA_HEX_LENGTH = DATA_BYTES * 2
    private const val BITS_PER_BYTE = 8
    private const val RANGE_SIZE = 0x20

    private val LINE_BREAK = Regex("[\\r\\n]+")
    private val SEPARATORS = Regex("[\\s:.]")
}

sealed interface BitmapResult {
    data class Ok(val pids: Set<Int>, val supportsNextRange: Boolean) : BitmapResult

    data class Failure(val reason: BitmapFailure) : BitmapResult
}

enum class BitmapFailure {
    NO_DATA,
    SERVICE_MISMATCH,
    TRUNCATED,
    MALFORMED,
}
