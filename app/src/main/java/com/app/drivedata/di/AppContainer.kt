package com.app.drivedata.di

import android.content.Context
import com.app.drivedata.metrics.ObdMetricsCollector
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.settings.AppSettingsRepository

/**
 * Minimal manual dependency container held by the [com.app.drivedata.DriveDataApplication].
 *
 * Kept tiny on purpose: there are only a couple of app-scoped singletons, so a DI framework
 * would add more ceremony than value.
 */
class AppContainer(context: Context) {
    val settingsRepository = AppSettingsRepository(context)
    val obdConnectionManager = ObdConnectionManager()
    val metricsCollector = ObdMetricsCollector(obdConnectionManager)
}
