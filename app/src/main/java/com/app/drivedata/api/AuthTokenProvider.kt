package com.app.drivedata.api

/**
 * Supplies the backend bearer token.
 *
 * The app still authenticates locally, so the default provider returns no token. The sync engine
 * already sends this header when a provider is configured, which lets JWT support slot in later
 * without touching the upload path.
 */
fun interface AuthTokenProvider {
    fun currentToken(): String?

    companion object {
        val None = AuthTokenProvider { null }
    }
}
