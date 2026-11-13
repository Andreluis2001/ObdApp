package com.app.drivedata.ui.connection

import com.app.drivedata.metrics.MetricDefinition
import com.app.drivedata.metrics.defaultMetricCatalog
import com.app.drivedata.vehicle.model.Vehicle

/** A discovered catalog PID, including whether collection is currently enabled for it. */
data class SupportedPidItem(
    val definition: MetricDefinition,
    val enabledForCollection: Boolean,
)

/**
 * The active vehicle's discovered capabilities, reduced to the PIDs the app knows how to collect.
 *
 * Unmapped PIDs are persisted by the discovery layer but intentionally hidden here.
 */
data class SupportedPidsSummary(
    val vehicleName: String? = null,
    val discoveredAtMillis: Long? = null,
    val pids: List<SupportedPidItem> = emptyList(),
)

fun Vehicle?.toSupportedPidsSummary(
    catalog: List<MetricDefinition> = defaultMetricCatalog,
): SupportedPidsSummary {
    val vehicle = this ?: return SupportedPidsSummary()
    val supportedByPid = vehicle.capabilities.supportedPids.associateBy { it.pid }
    return SupportedPidsSummary(
        vehicleName = vehicle.displayName,
        discoveredAtMillis = vehicle.capabilities.lastDiscoveredAtMillis,
        pids =
            catalog.mapNotNull { definition ->
                supportedByPid[definition.pid]?.let { supported ->
                    SupportedPidItem(
                        definition = definition,
                        enabledForCollection = supported.enabledForCollection,
                    )
                }
            },
    )
}
