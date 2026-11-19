package com.app.drivedata.di

import android.content.Context
import com.app.drivedata.api.ApiClient
import com.app.drivedata.auth.AuthSession
import com.app.drivedata.auth.DataStoreAuthTokenStore
import com.app.drivedata.connectivity.ConnectivityObserver
import com.app.drivedata.metrics.ActiveVehicleCollection
import com.app.drivedata.metrics.MetricsCollectionController
import com.app.drivedata.metrics.MetricsRepository
import com.app.drivedata.metrics.ObdMetricsCollector
import com.app.drivedata.metrics.toActiveVehicleCollection
import com.app.drivedata.metrics.dao.DriveDataDatabase
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.PidDiscoveryCoordinator
import com.app.drivedata.obd.VinReader
import com.app.drivedata.obd.transport.WifiNetworkLookup
import com.app.drivedata.settings.AppSettingsRepository
import com.app.drivedata.storage.StorageStatsProvider
import com.app.drivedata.sync.SyncScheduler
import com.app.drivedata.sync.TelemetrySyncEngine
import com.app.drivedata.vehicle.ActiveVehicleSwitcher
import com.app.drivedata.vehicle.VehicleIdentityExtractor
import com.app.drivedata.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = AppSettingsRepository(appContext)
    val obdConnectionManager = ObdConnectionManager(WifiNetworkLookup(appContext))
    val pidDiscoveryCoordinator = PidDiscoveryCoordinator(obdConnectionManager)
    val vehicleIdentityExtractor = VehicleIdentityExtractor(VinReader(obdConnectionManager)::read)
    val metricsCollector = ObdMetricsCollector(obdConnectionManager)
    val connectivityObserver = ConnectivityObserver(appContext)

    private val database = DriveDataDatabase.build(appContext)
    val metricsRepository = MetricsRepository(database.metricRecordDao())

    val authSession =
        AuthSession(
            api = ApiClient().authApi,
            store = DataStoreAuthTokenStore(appContext),
            scope = applicationScope,
        )
    val vehicleRepository = VehicleRepository(database.vehicleDao(), authSession.signedInUser)
    val activeVehicleSwitcher = ActiveVehicleSwitcher(vehicleRepository, obdConnectionManager)

    val activeVehicleCollection: StateFlow<ActiveVehicleCollection?> =
        vehicleRepository
            .observeActiveVehicle()
            .map { it?.toActiveVehicleCollection() }
            .stateIn(applicationScope, SharingStarted.Eagerly, null)

    private val apiClient = ApiClient(tokenProvider = authSession)
    val telemetrySyncEngine =
        TelemetrySyncEngine(
            dao = database.metricRecordDao(),
            api = apiClient.telemetryApi,
            refreshAuth = authSession::refresh,
            onReauthenticationRequired = authSession::expireSession,
        )
    val syncScheduler = SyncScheduler(appContext)

    val storageStatsProvider =
        StorageStatsProvider(appContext.getDatabasePath(DriveDataDatabase.DATABASE_NAME))

    val metricsCollectionController =
        MetricsCollectionController(
            scope = applicationScope,
            connectionState = obdConnectionManager.state,
            activeVehicle = activeVehicleCollection,
            streamFactory = metricsCollector::stream,
            repository = metricsRepository,
            signedInUser = authSession.signedInUser,
            disconnect = obdConnectionManager::disconnect,
            onSnapshotPersisted = syncScheduler::requestSync,
        )

    init {
        applicationScope.launch { authSession.ensureRestored() }
        applicationScope.launch {
            metricsRepository.pruneUploadedBefore()
        }
        applicationScope.launch {
            authSession.signedInUser.collect { user ->
                if (user == null) {
                    metricsCollectionController.stopCollection()
                    syncScheduler.cancel()
                    obdConnectionManager.disconnect()
                } else {
                    // Collection starts on its own once the active vehicle has a verified
                    // connection and discovered capabilities. Do not auto-connect here.
                    metricsCollectionController.startCollection()
                    syncScheduler.syncNow()
                }
            }
        }
        applicationScope.launch {
            connectivityObserver.isAvailable.collect { available ->
                if (available) syncScheduler.onConnectivityAvailable()
            }
        }
    }
}
