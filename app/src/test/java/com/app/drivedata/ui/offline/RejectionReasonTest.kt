package com.app.drivedata.ui.offline

import com.app.drivedata.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RejectionReasonTest {

    @Test
    fun mapsServerAndLocalRejectionReasons() {
        assertEquals(
            R.string.offline_rejection_vehicle_not_authorized,
            rejectionReasonRes("vehicle_not_authorized"),
        )
        assertEquals(
            R.string.offline_rejection_vehicle_not_authorized,
            rejectionReasonRes(" Vehicle_Not_Authorized "),
        )
        assertEquals(R.string.offline_rejection_missing_vin, rejectionReasonRes("missing vin"))
        assertEquals(
            R.string.offline_rejection_generic,
            rejectionReasonRes("record rejected by the server"),
        )
        assertEquals(
            R.string.offline_rejection_generic,
            rejectionReasonRes("request rejected by the server (HTTP 422)"),
        )
    }

    @Test
    fun leavesUnknownAndMissingReasonsUnmapped() {
        assertNull(rejectionReasonRes("future_backend_code"))
        assertNull(rejectionReasonRes(null))
    }
}
