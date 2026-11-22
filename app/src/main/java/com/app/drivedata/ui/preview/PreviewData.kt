package com.app.drivedata.ui.preview

import com.app.drivedata.metrics.defaultMetricCatalog
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.sync.SyncError
import com.app.drivedata.ui.home.HomeUiState
import com.app.drivedata.ui.vehicles.VehicleListError
import com.app.drivedata.ui.vehicles.VehicleListUiState
import com.app.drivedata.ui.vehicles.details.DetailsError
import com.app.drivedata.ui.vehicles.details.DetailsOperation
import com.app.drivedata.ui.vehicles.details.VehicleDetailsUiState
import com.app.drivedata.vehicle.model.ConnectionStatus
import com.app.drivedata.vehicle.model.Elm327Connection
import com.app.drivedata.vehicle.model.SupportedPid
import com.app.drivedata.vehicle.model.Vehicle
import com.app.drivedata.vehicle.model.VehicleCapabilities
import com.app.drivedata.vehicle.model.VehicleDetails
import com.app.drivedata.vehicle.model.VehicleIdentity

private const val PREVIEW_TIME = 1_700_000_000_000L

internal val previewCatalog = defaultMetricCatalog

internal fun previewVehicle(
    id: String = "vehicle-1",
    make: String = "Chevrolet",
    model: String = "S10",
    plate: String? = "GHI-9012",
    vin: String = "9BG126AA0YC123456",
    isActive: Boolean = true,
    status: ConnectionStatus = ConnectionStatus.CONNECTED,
    configured: Boolean = true,
    pids: List<Int> = listOf(0x0C, 0x0D, 0x05, 0x04),
    manufacturer: String? = "Chevrolet",
    serial: String? = "123456",
    discovered: Boolean = true,
): Vehicle {
    val connection =
        if (configured) {
            Elm327Connection(
                host = "192.168.4.1",
                port = 35000,
                lastConnectionAtMillis = PREVIEW_TIME,
                lastEcuCommunicationAtMillis = PREVIEW_TIME,
                status = status,
            )
        } else {
            Elm327Connection.unconfigured()
        }
    return Vehicle(
        id = id,
        ownerUsername = "preview",
        details = VehicleDetails(make = make, model = model, licensePlate = plate),
        identity = VehicleIdentity(vin = vin, manufacturer = manufacturer, serialNumber = serial),
        connection = connection,
        capabilities =
            VehicleCapabilities(
                supportedPids = pids.map { SupportedPid(it) },
                lastDiscoveredAtMillis = if (discovered) PREVIEW_TIME else null,
            ),
        isActive = isActive,
        createdAtMillis = PREVIEW_TIME,
        updatedAtMillis = PREVIEW_TIME,
    )
}

internal fun previewInactiveVehicle(): Vehicle =
    previewVehicle(
        id = "vehicle-2",
        make = "Fiat",
        model = "Strada",
        plate = "JKL-3456",
        vin = "9BD172000YC123456",
        isActive = false,
        status = ConnectionStatus.DISCONNECTED,
        pids = listOf(0x0C, 0x0D),
    )

internal fun previewHomeState(
    collecting: Boolean = true,
    rejectedCount: Int = 2,
    syncError: SyncError? = SyncError.NETWORK,
): HomeUiState {
    val snapshot =
        MetricsSnapshot(
            timestampMillis = PREVIEW_TIME,
            vin = "9BG126AA0YC123456",
            readings =
                listOf(
                    MetricReading(MetricId.RPM, "rpm", ReadingValue.Available(1248.0)),
                    MetricReading(MetricId.SPEED, "km/h", ReadingValue.Available(68.0)),
                    MetricReading(
                        MetricId.COOLANT_TEMPERATURE,
                        "°C",
                        ReadingValue.Available(87.0),
                    ),
                    MetricReading(MetricId.ENGINE_LOAD, "%", ReadingValue.Available(32.0)),
                ),
        )
    return HomeUiState(
        vehicleName = "Chevrolet S10",
        hasVehicle = true,
        vehicles = listOf(previewVehicle(), previewInactiveVehicle()),
        connection =
            ObdConnectionState.Connected(
                config = ObdWifiConfig("192.168.4.1", 35000),
                protocol = "CAN (11/500 kbps)",
                ecuStatus = EcuStatus.VERIFIED,
            ),
        isConnected = true,
        isCollecting = collecting,
        snapshot = snapshot,
        history =
            mapOf(
                MetricId.RPM to listOf(820.0, 1100.0, 960.0, 1248.0, 1180.0),
                MetricId.SPEED to listOf(0.0, 32.0, 55.0, 68.0, 62.0),
                MetricId.COOLANT_TEMPERATURE to listOf(82.0, 84.0, 86.0, 87.0, 87.0),
                MetricId.ENGINE_LOAD to listOf(18.0, 25.0, 22.0, 32.0, 29.0),
            ),
        lastSyncedAtMillis = PREVIEW_TIME,
        pendingCount = 12,
        rejectedCount = rejectedCount,
        internetAvailable = true,
        syncError = syncError,
        storageUsedBytes = 12L * 1024 * 1024,
        storageBudgetBytes = 1024L * 1024 * 1024,
    )
}

internal fun previewHomeIdleState(): HomeUiState =
    HomeUiState(
        vehicleName = "",
        hasVehicle = false,
        vehicles = emptyList(),
        connection = ObdConnectionState.Disconnected,
        isConnected = false,
        isCollecting = false,
        snapshot = null,
        history = emptyMap(),
        lastSyncedAtMillis = null,
        pendingCount = 0,
        rejectedCount = 0,
        internetAvailable = false,
        storageUsedBytes = 0L,
        storageBudgetBytes = 1024L * 1024 * 1024,
    )

internal fun previewVehicleListState(
    vehicles: List<Vehicle> = listOf(previewVehicle(), previewInactiveVehicle()),
    isLoading: Boolean = false,
    error: VehicleListError? = null,
): VehicleListUiState = VehicleListUiState(vehicles = vehicles, isLoading = isLoading, error = error)

internal fun previewVehicleDetailsState(
    vehicle: Vehicle? = previewVehicle(),
    isLoading: Boolean = false,
    operation: DetailsOperation = DetailsOperation.Idle,
    error: DetailsError? = null,
): VehicleDetailsUiState =
    VehicleDetailsUiState(
        vehicle = vehicle,
        isLoading = isLoading,
        operation = operation,
        error = error,
    )
