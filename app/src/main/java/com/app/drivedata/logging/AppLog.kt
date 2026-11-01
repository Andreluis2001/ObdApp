package com.app.drivedata.logging

import android.util.Log

/**
 * Small logging facade for the OBD data path.
 *
 * Falls back to stdout when [android.util.Log] is not available (local JVM unit tests),
 * so the instrumentation points used to verify adapter traffic do not break the test suite.
 *
 * Filter logcat with `adb logcat -s DriveData`.
 */
object AppLog {
    private const val TAG = "DriveData"

    fun d(message: String) = write(Log.DEBUG, message, null)

    fun i(message: String) = write(Log.INFO, message, null)

    fun w(message: String, error: Throwable? = null) = write(Log.WARN, message, error)

    fun e(message: String, error: Throwable? = null) = write(Log.ERROR, message, error)

    /** Renders raw adapter output on a single line so it stays readable in logcat. */
    fun raw(value: String?): String =
        value.orEmpty()
            .replace("\r", "\\r")
            .replace("\n", "\\n")

    private fun write(priority: Int, message: String, error: Throwable?) {
        try {
            if (error == null) {
                Log.println(priority, TAG, message)
            } else {
                Log.println(priority, TAG, "$message: ${Log.getStackTraceString(error)}")
            }
        } catch (_: Throwable) {
            println("$TAG: $message")
            error?.printStackTrace()
        }
    }
}
