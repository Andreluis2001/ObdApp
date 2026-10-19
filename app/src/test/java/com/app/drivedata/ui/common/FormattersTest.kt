package com.app.drivedata.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {

    @Test
    fun formatBytes_usesTheExpectedUnitForEachRange() {
        assertEquals("512 B", formatBytes(512L, Locale.US))
        assertEquals("48 KB", formatBytes(49_152L, Locale.US))
        assertEquals("1 MB", formatBytes(1_048_576L, Locale.US))
        assertEquals("12 MB", formatBytes(12_582_912L, Locale.US))
        assertEquals("1 GB", formatBytes(1_073_741_824L, Locale.US))
    }

    @Test
    fun formatMetricValue_groupsThousandsAndKeepsAtMostOneDecimal() {
        assertEquals("1,248", formatMetricValue(1_248.0, Locale.US))
        assertEquals("0", formatMetricValue(0.0, Locale.US))
        assertEquals("1.5", formatMetricValue(1.5, Locale.US))
    }
}
