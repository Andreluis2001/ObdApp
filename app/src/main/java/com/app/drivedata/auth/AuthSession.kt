package com.app.drivedata.auth

import com.app.drivedata.api.AuthApi
import com.app.drivedata.api.AuthTokenProvider
import com.app.drivedata.api.LoginRequest
import com.app.drivedata.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

enum class LoginResult {
    SUCCESS,
    INVALID_CREDENTIALS,
    UNAVAILABLE,
}

sealed interface RefreshResult {
    data object Refreshed : RefreshResult
    data object ReauthenticationRequired : RefreshResult
    data object Unavailable : RefreshResult
}

/**
 * Holds the backend session. Tokens are restored from local storage so a restart can continue
 * syncing the pending outbox without asking the user to sign in again.
 */
class AuthSession(
    private val api: AuthApi,
    private val store: AuthTokenStore,
    private val scope: CoroutineScope,
) : AuthTokenProvider {

    private val restoreMutex = Mutex()
    private val refreshMutex = Mutex()
    private val persistenceMutex = Mutex()

    /** Invalidates pending writes from a sign-out that ran after a newer session was created. */
    private var sessionGeneration = 0

    private val _signedInUser = MutableStateFlow<String?>(null)
    val signedInUser: StateFlow<String?> = _signedInUser.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    @Volatile
    private var session: StoredSession? = null

    override fun currentToken(): String? = session?.accessToken

    /** Safe to call from both the UI and the sync worker; only the first caller reads storage. */
    suspend fun ensureRestored() {
        if (_isReady.value) return
        restoreMutex.withLock {
            if (_isReady.value) return@withLock
            val stored =
                persistenceMutex.withLock {
                    runCatching { store.read() }
                        .onFailure { AppLog.w("Stored session could not be read", it) }
                        .getOrNull()
                }
            if (stored != null) activateInMemory(stored)
            _isReady.value = true
        }
    }

    suspend fun login(username: String, password: String): LoginResult {
        ensureRestored()
        val name = username.trim()
        if (name.isBlank()) return LoginResult.INVALID_CREDENTIALS

        return try {
            val tokens = api.login(LoginRequest(name, password))
            activate(StoredSession(name, tokens.accessToken, tokens.refreshToken))
            LoginResult.SUCCESS
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: HttpException) {
            if (error.code() in 401..403) {
                LoginResult.INVALID_CREDENTIALS
            } else {
                AppLog.w("Login rejected by the server (HTTP ${error.code()})")
                LoginResult.UNAVAILABLE
            }
        } catch (error: IOException) {
            AppLog.w("Login could not reach the server", error)
            LoginResult.UNAVAILABLE
        } catch (error: Exception) {
            AppLog.e("Login failed unexpectedly", error)
            LoginResult.UNAVAILABLE
        }
    }

    /** Rotates the access token; failures that are not auth failures leave the session intact. */
    suspend fun refresh(): RefreshResult =
        refreshMutex.withLock {
            val current = session ?: return@withLock RefreshResult.ReauthenticationRequired
            try {
                val tokens = api.refresh("Bearer ${current.refreshToken}")
                activate(
                    current.copy(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                    ),
                )
                RefreshResult.Refreshed
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: HttpException) {
                if (error.code() == 401 || error.code() == 403) {
                    AppLog.w("Refresh token rejected; reauthentication is required")
                    expireSession()
                    RefreshResult.ReauthenticationRequired
                } else {
                    RefreshResult.Unavailable
                }
            } catch (error: IOException) {
                AppLog.w("Token refresh could not reach the server", error)
                RefreshResult.Unavailable
            } catch (error: Exception) {
                AppLog.e("Token refresh failed unexpectedly", error)
                RefreshResult.Unavailable
            }
        }

    /** UI sign-out: clears the session immediately and persists the change in the background. */
    fun signOut() {
        val refreshToken = session?.refreshToken
        val generation = ++sessionGeneration
        clearInMemory()
        scope.launch { clearPersisted(refreshToken, generation) }
    }

    /** Auth failure inside the sync engine: awaits persistence before sync stops. */
    suspend fun expireSession() {
        val refreshToken = session?.refreshToken
        val generation = ++sessionGeneration
        clearInMemory()
        clearPersisted(refreshToken, generation)
    }

    private fun clearInMemory() {
        session = null
        _signedInUser.value = null
    }

    private suspend fun clearPersisted(refreshToken: String?, generation: Int) {
        persistenceMutex.withLock {
            if (generation != sessionGeneration) return@withLock
            runCatching { store.clear() }
                .onFailure { AppLog.w("Stored session could not be cleared", it) }
            if (!refreshToken.isNullOrBlank()) {
                runCatching { api.logout("Bearer $refreshToken") }
                    .onFailure { AppLog.w("Backend logout failed", it) }
            }
        }
    }

    private suspend fun activate(newSession: StoredSession) {
        val generation = ++sessionGeneration
        session = newSession
        _signedInUser.value = newSession.username
        persistenceMutex.withLock {
            if (generation == sessionGeneration) {
                runCatching { store.write(newSession) }
                    .onFailure { AppLog.w("Storing the session failed; it will be lost on restart", it) }
            }
        }
    }

    private fun activateInMemory(newSession: StoredSession) {
        session = newSession
        _signedInUser.value = newSession.username
    }
}
