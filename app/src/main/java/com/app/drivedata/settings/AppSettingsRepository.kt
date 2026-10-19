package com.app.drivedata.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.drivedata.obd.model.ObdWifiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drivedata_settings")

class AppSettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore
 
    val wifiConfig: Flow<ObdWifiConfig> =
        dataStore.data.map { preferences ->
            ObdWifiConfig(
                host = preferences[KEY_HOST] ?: DEFAULT_HOST,
                port = preferences[KEY_PORT] ?: DEFAULT_PORT,
            )
        }

    suspend fun currentWifiConfig(): ObdWifiConfig = wifiConfig.first()

    suspend fun saveWifiConfig(config: ObdWifiConfig) {
        dataStore.edit { preferences ->
            preferences[KEY_HOST] = config.host.trim()
            preferences[KEY_PORT] = config.port
        }
    }

    private companion object {
        const val DEFAULT_HOST = "192.168.0.10"
        const val DEFAULT_PORT = 35000
        val KEY_HOST = stringPreferencesKey("wifi_host")
        val KEY_PORT = intPreferencesKey("wifi_port")
    }
}
