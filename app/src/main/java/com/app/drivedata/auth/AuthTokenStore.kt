package com.app.drivedata.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "drivedata_auth")

data class StoredSession(
    val username: String,
    val accessToken: String,
    val refreshToken: String,
)

interface AuthTokenStore {
    suspend fun read(): StoredSession?
    suspend fun write(session: StoredSession)
    suspend fun clear()
}

class DataStoreAuthTokenStore(context: Context) : AuthTokenStore {

    private val dataStore = context.applicationContext.authDataStore

    override suspend fun read(): StoredSession? {
        val preferences = dataStore.data.first()
        val username = preferences[KEY_USERNAME] ?: return null
        val accessToken = preferences[KEY_ACCESS_TOKEN] ?: return null
        val refreshToken = preferences[KEY_REFRESH_TOKEN] ?: return null
        if (username.isBlank() || accessToken.isBlank() || refreshToken.isBlank()) return null
        return StoredSession(username, accessToken, refreshToken)
    }

    override suspend fun write(session: StoredSession) {
        dataStore.edit { preferences ->
            preferences[KEY_USERNAME] = session.username
            preferences[KEY_ACCESS_TOKEN] = session.accessToken
            preferences[KEY_REFRESH_TOKEN] = session.refreshToken
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_USERNAME)
            preferences.remove(KEY_ACCESS_TOKEN)
            preferences.remove(KEY_REFRESH_TOKEN)
        }
    }

    private companion object {
        val KEY_USERNAME = stringPreferencesKey("auth_username")
        val KEY_ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
    }
}
