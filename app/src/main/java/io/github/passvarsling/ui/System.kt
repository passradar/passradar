package io.github.passvarsling.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.passvarsling.R
import io.github.passvarsling.data.Office
import android.view.View
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.link_error, Toast.LENGTH_SHORT).show()
    }
}

fun Context.openUrl(url: String) = startSafely(Intent(Intent.ACTION_VIEW, url.toUri()))

fun Context.openInMaps(office: Office) {
    val uri = if (office.lat != null && office.lon != null) {
        "geo:${office.lat},${office.lon}?q=${office.lat},${office.lon}(${Uri.encode(office.name)})"
    } else {
        "geo:0,0?q=${Uri.encode(office.addressLine)}"
    }
    startSafely(Intent(Intent.ACTION_VIEW, uri.toUri()))
}

fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
    }
    startSafely(intent)
}

/** Android 6.0+ can stop background work for apps it considers idle. */
fun Context.isBatteryOptimized(): Boolean {
    if (Build.VERSION.SDK_INT < 23) return false
    val power = ContextCompat.getSystemService(this, PowerManager::class.java) ?: return false
    return !power.isIgnoringBatteryOptimizations(packageName)
}

fun Context.openBatterySettings() {
    if (Build.VERSION.SDK_INT >= 23) startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

/**
 * Android's own dialog asking to let this app run in the background. Builds without the
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission (Google Play limits it) open the list instead.
 */
@SuppressLint("BatteryLife") // the app's whole job is checking in the background
fun Context.requestBackgroundRunning() {
    if (Build.VERSION.SDK_INT < 23) return
    val mayAsk = ContextCompat.checkSelfPermission(this, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (!mayAsk) return openBackgroundSettingsWithHint()
    try {
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        openBatterySettings()
    }
}

/** Where to turn battery optimisation off by hand, with a hint on what to pick there. */
private fun Context.openBackgroundSettingsWithHint() {
    if (Build.VERSION.SDK_INT >= 31) {
        // Android 12+: the app's own page, where "App battery usage" has "Unrestricted".
        startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        Toast.makeText(this, R.string.bg_hint_app_info, Toast.LENGTH_LONG).show()
    } else {
        openBatterySettings()
        Toast.makeText(this, getString(R.string.bg_hint_list, getString(R.string.app_name)), Toast.LENGTH_LONG).show()
    }
}

/** Battery Saver postpones background work while it is on. */
fun Context.isPowerSaveMode(): Boolean =
    ContextCompat.getSystemService(this, PowerManager::class.java)?.isPowerSaveMode == true

fun Context.openBatterySaverSettings() {
    startSafely(Intent(if (Build.VERSION.SDK_INT >= 22) Settings.ACTION_BATTERY_SAVER_SETTINGS else Intent.ACTION_POWER_USAGE_SUMMARY))
}

/**
 * Phone makers that add their own app killers on top of Android (Samsung's sleeping
 * apps, Xiaomi's autostart and so on), with the dontkillmyapp.com guide for each.
 */
data class Vendor(val name: String, val guideUrl: String, val isSamsung: Boolean)

fun phoneVendor(): Vendor? {
    val maker = Build.MANUFACTURER.lowercase()
    val slug = when (maker) {
        "samsung" -> "samsung"
        "xiaomi", "redmi", "poco" -> "xiaomi"
        "huawei", "honor" -> "huawei"
        "oneplus" -> "oneplus"
        "oppo" -> "oppo"
        "realme" -> "realme"
        "vivo" -> "vivo"
        "sony" -> "sony"
        "nokia", "hmd global" -> "nokia"
        "asus" -> "asus"
        "motorola" -> "motorola"
        "meizu" -> "meizu"
        "lenovo" -> "lenovo"
        else -> return null
    }
    return Vendor(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, "https://dontkillmyapp.com/$slug", slug == "samsung")
}

/** Samsung's battery page, where "Background usage limits" (sleeping apps) lives; else the app's own page. */
fun Context.openVendorBatterySettings(vendor: Vendor) {
    if (vendor.isSamsung) {
        val pages = listOf(
            "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.sm.ui.battery.BatteryActivity",
        )
        for (page in pages) {
            try {
                startActivity(Intent().setComponent(ComponentName("com.samsung.android.lool", page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next One UI version's page.
            } catch (e: SecurityException) {
                // Not exported on this version.
            }
        }
    }
    startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
}

/** Whether the phone is on a network with internet, so failures can be told apart from the police's system being down. */
fun Context.isOnline(): Boolean {
    val connectivity = ContextCompat.getSystemService(this, ConnectivityManager::class.java) ?: return true
    if (Build.VERSION.SDK_INT >= 23) {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    @Suppress("DEPRECATION")
    return connectivity.activeNetworkInfo?.isConnected == true
}

fun Context.notificationsAllowed(): Boolean {
    val permitted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(this).areNotificationsEnabled()
}

/** Returns a function that asks for the notification permission (Android 13+) if missing. */
@Composable
fun rememberNotificationPermission(): () -> Unit {
    if (Build.VERSION.SDK_INT < 33) return remember { {} }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Android 8.1 and older hand focus to the first focusable view when a window opens, and
 * again whenever focus is cleared, which keeps putting the cursor and the keyboard in a
 * search field. Lets the window's content frame take that focus instead.
 */
fun View.letContentFrameTakeFocus() {
    if (Build.VERSION.SDK_INT >= 28) return
    rootView.findViewById<View>(android.R.id.content)?.apply {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}

/** [letContentFrameTakeFocus] for the window this composable is in, such as a bottom sheet. */
@Composable
fun KeepFocusOutOfFields() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.letContentFrameTakeFocus()
        onDispose { }
    }
}
