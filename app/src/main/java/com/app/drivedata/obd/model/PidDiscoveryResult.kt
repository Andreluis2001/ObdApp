package com.app.drivedata.obd.model

/** Outcome of a full supported-PIDs walk. A failure never carries a partial PID set. */
sealed interface PidDiscoveryResult {
    data class Success(
        val pids: Set<Int>,
        val discoveredAtMillis: Long,
    ) : PidDiscoveryResult

    data class Failure(
        val stage: DiscoveryStage,
        val reason: DiscoveryFailure,
    ) : PidDiscoveryResult
}

enum class DiscoveryStage {
    ECU_TEST,
    RANGE_QUERY,
}

enum class DiscoveryFailure {
    ECU_NO_DATA,
    SERVICE_MISMATCH,
    TRUNCATED,
    MALFORMED,
    TIMEOUT,
    CONNECTION_LOST,
    UNKNOWN,
}
