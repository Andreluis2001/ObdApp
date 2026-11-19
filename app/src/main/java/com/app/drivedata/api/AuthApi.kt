package com.app.drivedata.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/token")
    suspend fun login(@Body request: LoginRequest): TokenPair

    /** The refresh token travels as the bearer credential; each one can only be used once. */
    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Header("Authorization") authorization: String): TokenPair

    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") authorization: String)
}
