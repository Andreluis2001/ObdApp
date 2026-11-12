package com.app.drivedata.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityMergeTest {

    @Test
    fun buildCapabilityEntities_keepsExistingPreferences() {
        val entities =
            buildCapabilityEntities(
                vehicleId = "v1",
                pids = setOf(0x0C, 0x0D),
                discoveredAtMillis = 20,
                enabledByPid = mapOf(0x0C to true, 0x0D to false),
            )

        val byPid = entities.associateBy { it.pid }
        assertTrue(byPid.getValue(0x0C).enabledForCollection)
        assertFalse(byPid.getValue(0x0D).enabledForCollection)
        assertTrue(entities.all { it.discoveredAtMillis == 20L })
    }

    @Test
    fun buildCapabilityEntities_enablesNewPidsAndDropsRemovedOnes() {
        val entities =
            buildCapabilityEntities(
                vehicleId = "v1",
                pids = setOf(0x05, 0x0C),
                discoveredAtMillis = 30,
                enabledByPid = mapOf(0x0C to false, 0x0D to true),
            )

        assertEquals(listOf(0x05, 0x0C), entities.map { it.pid })
        assertTrue(entities.first { it.pid == 0x05 }.enabledForCollection)
        assertFalse(entities.first { it.pid == 0x0C }.enabledForCollection)
    }

    @Test
    fun buildCapabilityEntities_handlesAnEmptyDiscovery() {
        assertTrue(
            buildCapabilityEntities(
                vehicleId = "v1",
                pids = emptySet(),
                discoveredAtMillis = 40,
                enabledByPid = mapOf(0x0C to true),
            ).isEmpty(),
        )
    }
}
