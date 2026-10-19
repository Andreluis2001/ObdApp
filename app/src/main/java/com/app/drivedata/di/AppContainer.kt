package com.app.drivedata.di

import android.content.Context
import com.app.drivedata.auth.AuthSession
import com.app.drivedata.auth.UserRepository
import com.app.drivedata.connectivity.ConnectivityObserver
import com.app.drivedata.metrics.MetricsCollectionController
import com.app.drivedata.metrics.MetricsRepository
import com.app.drivedata.metrics.ObdMetricsCollector
import com.app.drivedata.metrics.dao.DriveDataDatabase
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.settings.AppSettingsRepository
import com.app.drivedata.storage.StorageStatsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = AppSettingsRepository(appContext)
    val obdConnectionManager = ObdConnectionManager()
    val metricsCollector = ObdMetricsCollector(obdConnectionManager)
    val connectivityObserver = ConnectivityObserver(appContext)

    private val database = DriveDataDatabase.build(appContext)
    val metricsRepository = MetricsRepository(database.metricRecordDao())
    val usersRepository = UserRepository(database.userDao())
    val authSession = AuthSession()

    val storageStatsProvider =
        StorageStatsProvider(appContext.getDatabasePath(DriveDataDatabase.DATABASE_NAME))

    val metricsCollectionController =
        MetricsCollectionController(
            scope = applicationScope,
            connectionState = obdConnectionManager.state,
            streamFactory = metricsCollector::stream,
            repository = metricsRepository,
        )
}
