package com.app.drivedata.metrics

/** Bounds and default for the user-configurable interval between OBD snapshots. */
object CollectionInterval {
    const val MIN_SECONDS = 1L
    const val MAX_SECONDS = 60L
    const val DEFAULT_SECONDS = 1L

    const val MIN_MILLIS = MIN_SECONDS * 1_000L
    const val MAX_MILLIS = MAX_SECONDS * 1_000L
    const val DEFAULT_MILLIS = DEFAULT_SECONDS * 1_000L
}
