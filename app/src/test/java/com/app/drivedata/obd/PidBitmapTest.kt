package com.app.drivedata.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PidBitmapTest {

    @Test
    fun decodesSpacedBitmapAndDetectsTheNextRange() {
        val result = decodeOk("4100 BE1FA813")

        assertEquals(
            setOf(
                0x01, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x0C, 0x0D, 0x0E, 0x0F, 0x10,
                0x11, 0x13, 0x15,
                0x1C, 0x1F, 0x20,
            ),
            result.pids,
        )
        assertTrue(result.supportsNextRange)
    }

    @Test
    fun reportsNoContinuationWhenTheNextRangeBitIsClear() {
        val result = decodeOk("4100 BE1FA812")

        assertFalse(result.supportsNextRange)
        assertFalse(0x20 in result.pids)
    }

    @Test
    fun decodesSubsequentRanges() {
        val result = decodeOk("4120901FF015", base = 0x20)

        assertEquals(setOf(0x21, 0x24, 0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x3C, 0x3E, 0x40), result.pids)
        assertTrue(result.supportsNextRange)
    }

    @Test
    fun rejectsATruncatedBitmap() {
        assertEquals(BitmapFailure.TRUNCATED, decodeFailure("4100 BE1F"))
        assertEquals(BitmapFailure.TRUNCATED, decodeFailure("4100BE1FA81300"))
    }

    @Test
    fun rejectsAWrongService() {
        assertEquals(BitmapFailure.SERVICE_MISMATCH, decodeFailure("4000 BE1FA813"))
    }

    @Test
    fun rejectsAWrongPidEcho() {
        assertEquals(BitmapFailure.SERVICE_MISMATCH, decodeFailure("4101 BE1FA813"))
    }

    @Test
    fun rejectsMalformedHex() {
        assertEquals(BitmapFailure.MALFORMED, decodeFailure("4100 BE1FA81Z"))
    }

    @Test
    fun reportsNoDataForEmptyOrNoDataAnswers() {
        assertEquals(BitmapFailure.NO_DATA, decodeFailure("NO DATA"))
        assertEquals(BitmapFailure.NO_DATA, decodeFailure(""))
        assertEquals(BitmapFailure.NO_DATA, decodeFailure(null))
    }

    @Test
    fun normalizesCaseSpacingAndTheSearchingPreamble() {
        val result = decodeOk("  searching...\r\n41 00 be 1f a8 13 ")

        assertTrue(0x0C in result.pids)
        assertTrue(result.supportsNextRange)
    }

    @Test
    fun ignoresEchoedCommandLines() {
        val result = decodeOk("0100\r4100BE1FA813")

        assertTrue(0x0C in result.pids)
    }

    @Test
    fun selectsTheFirstMatchingFrameFromMultiEcuResponses() {
        val result = decodeOk("4100 BE1FA813\r\r41 00 00 00 00 00")

        assertTrue(0x0C in result.pids)
        assertTrue(result.supportsNextRange)
    }

    private fun decodeOk(raw: String?, base: Int = 0x00): BitmapResult.Ok =
        PidBitmap.decode(raw, base) as? BitmapResult.Ok
            ?: throw AssertionError("expected Ok for '$raw', got ${PidBitmap.decode(raw, base)}")

    private fun decodeFailure(raw: String?, base: Int = 0x00): BitmapFailure =
        (PidBitmap.decode(raw, base) as? BitmapResult.Failure)?.reason
            ?: throw AssertionError("expected Failure for '$raw'")
}
