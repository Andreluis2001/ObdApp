package com.app.drivedata.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Keeps the status bar icons readable over a dark header for as long as this composable is in the
 * composition, restoring the previous appearance afterwards.
 */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()

    DisposableEffect(activity, view) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars

        controller?.isAppearanceLightStatusBars = false

        onDispose {
            if (controller != null) {
                previousLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
