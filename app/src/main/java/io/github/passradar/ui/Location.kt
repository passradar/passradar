package io.github.passradar.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Finds out roughly where the phone is, using Android's own location service
 * (no Google services needed). Asks for the coarse location permission first.
 */
@Composable
fun rememberLocationRequest(onResult: (Location?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnResult by rememberUpdatedState(onResult)
    val fetch = remember(context) {
        { scope.launch { latestOnResult(currentLocation(context)) } }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetch() else latestOnResult(null)
    }
    return remember(context, launcher) {
        {
            if (hasLocationPermission(context)) fetch() else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** The current position, or the last known one; null without permission or position. */
@SuppressLint("MissingPermission") // checked first
suspend fun currentLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return null
    // Only the providers this app may use. Before Android 12 the GPS and passive
    // providers need the precise location permission, which the app doesn't ask for.
    val allowed = runCatching { manager.getProviders(false) }.getOrDefault(emptyList())
    val providers = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter { it in allowed }
    val enabled = providers.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    val lastKnown = (providers + LocationManager.PASSIVE_PROVIDER)
        .filter { it in allowed }
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    // A position from the last few minutes is good enough for distances to offices.
    if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < RECENT_MS) return lastKnown
    for (provider in enabled) {
        val fresh: Location? = try {
            withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
            }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null // provider vanished
        }
        if (fresh != null) return fresh
    }
    return lastKnown
}

private const val RECENT_MS = 10 * 60 * 1000L
private const val PROVIDER_TIMEOUT_MS = 8_000L
