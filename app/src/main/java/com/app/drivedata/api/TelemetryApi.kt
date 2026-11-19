package com.app.drivedata.api

import retrofit2.http.Body
import retrofit2.http.POST

interface TelemetryApi {

    @POST("api/v1/telemetry/batches")
    suspend fun uploadBatch(@Body request: BatchUploadRequest): BatchUploadResponse

    companion object {
        const val BATCH_SIZE = 100
    }
}
