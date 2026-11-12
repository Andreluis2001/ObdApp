package com.app.drivedata.di

import android.content.Context
import com.app.drivedata.api.ApiClient
import com.app.drivedata.auth.AuthSession
import com.app.drivedata.auth.UserRepository
import com.app.drivedata.connectivity.ConnectivityObserver
import com.app.drivedata.metrics.MetricsCollectionController
import com.app.drivedata.metrics.MetricsRepository
import com.app.drivedata.metrics.ObdMetricsCollector
import com.app.drivedata.metrics.dao.DriveDataDatabase
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.PidDiscoveryCoordinator
import com.app.drivedata.obd.transport.WifiNetworkLookup
import com.app.drivedata.settings.AppSettingsRepository
import com.app.drivedata.storage.StorageStatsProvider
import com.app.drivedata.sync.SyncScheduler
import com.app.drivedata.sync.TelemetrySyncEngine
import com.app.drivedata.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = AppSettingsRepository(appContext)
    val obdConnectionManager = ObdConnectionManager(WifiNetworkLookup(appContext))
    val pidDiscoveryCoordinator = PidDiscoveryCoordinator(obdConnectionManager)
    val metricsCollector = ObdMetricsCollector(obdConnectionManager)
    val connectivityObserver = ConnectivityObserver(appContext)

    private val database = DriveDataDatabase.build(appContext)
    val metricsRepository = MetricsRepository(database.metricRecordDao())
    val usersRepository = UserRepository(database.userDao())
    val authSession = AuthSession()
    val vehicleRepository = VehicleRepository(database.vehicleDao(), authSession)

    private val apiClient = ApiClient()
    val telemetrySyncEngine = TelemetrySyncEngine(database.metricRecordDao(), apiClient.telemetryApi)
    val syncScheduler = SyncScheduler(appContext)

    val storageStatsProvider =
        StorageStatsProvider(appContext.getDatabasePath(DriveDataDatabase.DATABASE_NAME))

    val metricsCollectionController =
        MetricsCollectionController(
            scope = applicationScope,
            connectionState = obdConnectionManager.state,
            streamFactory = metricsCollector::stream,
            repository = metricsRepository,
            authSession = authSession,
            onSnapshotPersisted = syncScheduler::requestSync,
        )

    init {
        applicationScope.launch {
            metricsRepository.pruneUploadedBefore()
        }
        applicationScope.launch {
            authSession.signedInUser.collect { user ->
                if (user == null) {
                    metricsCollectionController.stopCollection()
                    syncScheduler.cancel()
                } else {
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
