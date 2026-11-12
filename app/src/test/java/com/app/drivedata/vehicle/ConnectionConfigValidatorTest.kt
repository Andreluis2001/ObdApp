package com.app.drivedata.vehicle

import com.app.drivedata.obd.model.ObdWifiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionConfigValidatorTest {

    @Test
    fun blankHostAndPort_areUnconfigured() {
        assertEquals(
            ConnectionConfigValidation.Unconfigured,
            ConnectionConfigValidator.validate(host = "", port = ""),
        )
        assertEquals(
            ConnectionConfigValidation.Unconfigured,
            ConnectionConfigValidator.validate(host = "   ", port = "  "),
        )
    }

    @Test
    fun validIpv4AndPort_areAcceptedAndTrimmed() {
        assertEquals(
            ConnectionConfigValidation.Valid(ObdWifiConfig(host = "192.168.1.119", port = 35000)),
            ConnectionConfigValidator.validate(host = " 192.168.1.119 ", port = " 35000 "),
        )
    }

    @Test
    fun validHostname_isAccepted() {
        assertEquals(
            ConnectionConfigValidation.Valid(ObdWifiConfig(host = "elm327.local", port = 1)),
            ConnectionConfigValidator.validate(host = "elm327.local", port = "1"),
        )
    }

    @Test
    fun portWithoutHost_marksBothFieldsAsInvalid() {
        assertEquals(
            ConnectionConfigValidation.Invalid(
                ConnectionConfigErrors(host = true, port = false),
            ),
            ConnectionConfigValidator.validate(host = "", port = "35000"),
        )
    }

    @Test
    fun hostWithoutPort_marksThePortAsInvalid() {
        assertEquals(
            ConnectionConfigValidation.Invalid(
                ConnectionConfigErrors(host = false, port = true),
            ),
            ConnectionConfigValidator.validate(host = "192.168.1.119", port = ""),
        )
    }

    @Test
    fun malformedHost_isRejected() {
        val result =
            ConnectionConfigValidator.validate(host = "192.168.1", port = "35000")

        assertEquals(
            ConnectionConfigValidation.Invalid(
                ConnectionConfigErrors(host = true, port = false),
            ),
            result,
        )
    }

    @Test
    fun outOfRangePorts_areRejected() {
        listOf("0", "65536", "-1").forEach { port ->
            val result = ConnectionConfigValidator.validate(host = "192.168.1.119", port = port)

            assertEquals(
                "port=$port",
                ConnectionConfigValidation.Invalid(
                    ConnectionConfigErrors(host = false, port = true),
                ),
                result,
            )
        }
    }

    @Test
    fun numericOutOfRangeIpv4Octet_isRejected() {
        assertFalse(ConnectionConfigValidator.isValidHost("192.168.1.256"))
        assertFalse(ConnectionConfigValidator.isValidHost("256.0.0.1"))
        assertTrue(ConnectionConfigValidator.isValidHost("255.255.255.255"))
    }

    @Test
    fun hostnameWithUnderscoreOrLeadingHyphen_isRejected() {
        assertFalse(ConnectionConfigValidator.isValidHost("elm_327.local"))
        assertFalse(ConnectionConfigValidator.isValidHost("-elm327.local"))
    }
}
