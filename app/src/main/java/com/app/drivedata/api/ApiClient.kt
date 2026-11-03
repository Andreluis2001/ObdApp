package com.app.drivedata.api

import com.app.drivedata.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(
    baseUrl: String = BuildConfig.API_BASE_URL,
    tokenProvider: AuthTokenProvider = AuthTokenProvider.None,
    debug: Boolean = BuildConfig.DEBUG,
) {

    val telemetryApi: TelemetryApi

    init {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        val client =
            OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val token = tokenProvider.currentToken()
                    val request =
                        if (token.isNullOrBlank()) {
                            chain.request()
                        } else {
                            chain.request().newBuilder()
                                .header("Authorization", "Bearer $token")
                                .build()
                        }
                    chain.proceed(request)
                }
                .apply {
                    if (debug) {
                        // BASIC never logs headers or bodies, so tokens and telemetry stay out of logcat.
                        addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                    }
                }
                .build()

        telemetryApi =
            Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TelemetryApi::class.java)
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
        const val WRITE_TIMEOUT_SECONDS = 30L
    }
}
