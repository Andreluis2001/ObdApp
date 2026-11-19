package com.app.drivedata.auth

import com.app.drivedata.api.AuthApi
import com.app.drivedata.api.LoginRequest
import com.app.drivedata.api.TokenPair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionTest {

    @Test
    fun login_storesTheSessionAndExposesTheAccessToken() =
        runTest {
            val api = FakeAuthApi()
            val store = FakeTokenStore()
            val session = AuthSession(api, store, this)

            val result = session.login(" admin ", "password")

            assertEquals(LoginResult.SUCCESS, result)
            assertEquals("admin", session.signedInUser.value)
            assertEquals("access-1", session.currentToken())
            assertEquals(StoredSession("admin", "access-1", "refresh-1"), store.stored)
            assertEquals("admin", api.lastLoginRequest?.name)
        }

    @Test
    fun login_mapsUnauthorizedToInvalidCredentials() =
        runTest {
            val api = FakeAuthApi().apply { loginHandler = { throw httpError(401) } }
            val store = FakeTokenStore()
            val session = AuthSession(api, store, this)

            assertEquals(LoginResult.INVALID_CREDENTIALS, session.login("admin", "bad"))
            assertNull(session.signedInUser.value)
            assertNull(store.stored)
        }

    @Test
    fun login_keepsTheUserSignedOutWhenTheServerIsUnreachable() =
        runTest {
            val api = FakeAuthApi().apply { loginHandler = { throw IOException("offline") } }
            val session = AuthSession(api, FakeTokenStore(), this)

            assertEquals(LoginResult.UNAVAILABLE, session.login("admin", "password"))
            assertNull(session.signedInUser.value)
        }

    @Test
    fun restore_loadsAPersistedSessionOnce() =
        runTest {
            val store =
                FakeTokenStore().apply {
                    stored = StoredSession("admin", "old-access", "old-refresh")
                }
            val session = AuthSession(FakeAuthApi(), store, this)

            session.ensureRestored()
            store.stored = StoredSession("other", "new-access", "new-refresh")
            session.ensureRestored()

            assertEquals("admin", session.signedInUser.value)
            assertEquals("old-access", session.currentToken())
            assertTrue(session.isReady.value)
        }

    @Test
    fun refresh_rotatesAndPersistsTheNewTokenPair() =
        runTest {
            val api = FakeAuthApi()
            val store = FakeTokenStore()
            val session = AuthSession(api, store, this)
            session.login("admin", "password")

            val result = session.refresh()

            assertEquals(RefreshResult.Refreshed, result)
            assertEquals("access-2", session.currentToken())
            assertEquals("Bearer refresh-1", api.lastRefreshHeader)
            assertEquals(StoredSession("admin", "access-2", "refresh-2"), store.stored)
        }

    @Test
    fun refresh_rejectionClearsTheSessionAndRequiresReauthentication() =
        runTest {
            val api = FakeAuthApi().apply { refreshHandler = { throw httpError(401) } }
            val store = FakeTokenStore()
            val session = AuthSession(api, store, this)
            session.login("admin", "password")

            assertEquals(RefreshResult.ReauthenticationRequired, session.refresh())
            assertNull(session.signedInUser.value)
            assertNull(session.currentToken())
            assertNull(store.stored)
            assertEquals("Bearer refresh-1", api.lastLogoutHeader)
        }

    @Test
    fun refresh_networkFailureKeepsTheSessionForARetry() =
        runTest {
            val api = FakeAuthApi().apply { refreshHandler = { throw IOException("offline") } }
            val store = FakeTokenStore()
            val session = AuthSession(api, store, this)
            session.login("admin", "password")

            assertEquals(RefreshResult.Unavailable, session.refresh())
            assertEquals("admin", session.signedInUser.value)
            assertEquals("access-1", session.currentToken())
            assertEquals("refresh-1", store.stored?.refreshToken)
        }

    @Test
    fun aDelayedSignOutDoesNotEraseANewSession() =
        runTest {
            val api = FakeAuthApi()
            val store = FakeTokenStore()
            val session = AuthSession(api, store, this)
            session.login("admin", "password")

            session.signOut()
            session.login("other", "password")
            advanceUntilIdle()

            assertEquals("other", session.signedInUser.value)
            assertEquals(StoredSession("other", "access-1", "refresh-1"), store.stored)
        }

    @Test
    fun signOut_clearsLocalAndStoredSessionsAndLogsOut() =
        runTest {
            val api = FakeAuthApi()
            val store = FakeTokenStore()
            val session = AuthSession(api, store, this)
            session.login("admin", "password")

            session.signOut()
            advanceUntilIdle()

            assertNull(session.signedInUser.value)
            assertNull(session.currentToken())
            assertNull(store.stored)
            assertEquals("Bearer refresh-1", api.lastLogoutHeader)
        }
}

private class FakeAuthApi : AuthApi {
    var loginHandler: suspend (LoginRequest) -> TokenPair = { tokenPair() }
    var refreshHandler: suspend (String) -> TokenPair = { tokenPair("access-2", "refresh-2") }
    var lastLoginRequest: LoginRequest? = null
    var lastRefreshHeader: String? = null
    var lastLogoutHeader: String? = null

    override suspend fun login(request: LoginRequest): TokenPair {
        lastLoginRequest = request
        return loginHandler(request)
    }

    override suspend fun refresh(authorization: String): TokenPair {
        lastRefreshHeader = authorization
        return refreshHandler(authorization)
    }

    override suspend fun logout(authorization: String) {
        lastLogoutHeader = authorization
    }
}

private class FakeTokenStore : AuthTokenStore {
    var stored: StoredSession? = null

    override suspend fun read(): StoredSession? = stored

    override suspend fun write(session: StoredSession) {
        stored = session
    }

    override suspend fun clear() {
        stored = null
    }
}

private fun tokenPair(
    accessToken: String = "access-1",
    refreshToken: String = "refresh-1",
) = TokenPair(accessToken = accessToken, refreshToken = refreshToken)

private fun httpError(code: Int): HttpException =
    HttpException(
        Response.error<TokenPair>(code, "".toResponseBody("application/json".toMediaType())),
    )
