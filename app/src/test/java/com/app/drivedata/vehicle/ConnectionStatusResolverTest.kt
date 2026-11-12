package com.app.drivedata.vehicle

import com.app.drivedata.vehicle.model.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStatusResolverTest {

    @Test
    fun unconfiguredAdapter_isNotConfigured() {
        assertEquals(
            ConnectionStatus.NOT_CONFIGURED,
            resolveConnectionStatus(
                configured = false,
                attempted = false,
                currentlyConnected = false,
            ),
        )
        assertEquals(
            ConnectionStatus.NOT_CONFIGURED,
            resolveConnectionStatus(
                configured = false,
                attempted = true,
                currentlyConnected = false,
            ),
        )
    }

    @Test
    fun configuredButNeverTested_isDisconnected() {
        assertEquals(
            ConnectionStatus.DISCONNECTED,
            resolveConnectionStatus(
                configured = true,
                attempted = false,
                currentlyConnected = false,
            ),
        )
    }

    @Test
    fun configuredAndCurrentlyConnected_isConnected() {
        assertEquals(
            ConnectionStatus.CONNECTED,
            resolveConnectionStatus(
                configured = true,
                attempted = true,
                currentlyConnected = true,
            ),
        )
    }

    @Test
    fun configuredButAttemptFailed_isError() {
        assertEquals(
            ConnectionStatus.ERROR,
            resolveConnectionStatus(
                configured = true,
                attempted = true,
                currentlyConnected = false,
            ),
        )
    }
}
