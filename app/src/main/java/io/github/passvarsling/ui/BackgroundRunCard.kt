package io.github.passvarsling.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.passvarsling.R

/**
 * Everything that can stop the background check from running, each with a way to fix
 * it: notifications, Android's battery optimisation, Battery Saver, and the phone
 * maker's own app killers (Samsung's sleeping apps and the like).
 */
@Composable
fun BackgroundRunCard(alwaysShow: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(context.notificationsAllowed()) }
    var optimized by remember { mutableStateOf(context.isBatteryOptimized()) }
    var powerSave by remember { mutableStateOf(context.isPowerSaveMode()) }
    val vendor = remember { phoneVendor() }

    LifecycleResumeEffect(context) {
        notificationsOn = context.notificationsAllowed()
        optimized = context.isBatteryOptimized()
        powerSave = context.isPowerSaveMode()
        onPauseOrDispose { }
    }

    val problems = !notificationsOn || optimized || powerSave
    if (!alwaysShow && !problems) return

    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (problems) colors.tertiaryContainer else colors.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.bg_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.bg_intro), style = MaterialTheme.typography.bodyMedium)

            CheckRow(
                ok = notificationsOn,
                title = stringResource(R.string.settings_notifications),
                status = stringResource(if (notificationsOn) R.string.settings_notifications_on else R.string.bg_notifications_off),
                action = stringResource(R.string.bg_turn_on),
                onAction = { context.openNotificationSettings() },
            )
            if (Build.VERSION.SDK_INT >= 23) {
                CheckRow(
                    ok = !optimized,
                    title = stringResource(R.string.bg_running),
                    status = stringResource(if (optimized) R.string.bg_running_limited else R.string.bg_running_ok),
                    action = stringResource(R.string.bg_allow),
                    onAction = { context.requestBackgroundRunning() },
                )
            }
            CheckRow(
                ok = !powerSave,
                title = stringResource(R.string.bg_power_save),
                status = stringResource(if (powerSave) R.string.bg_power_save_on else R.string.bg_power_save_off),
                action = stringResource(R.string.bg_open),
                onAction = { context.openBatterySaverSettings() },
            )
            if (vendor != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (vendor.isSamsung) stringResource(R.string.bg_samsung_title) else stringResource(R.string.bg_vendor_title, vendor.name),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (vendor.isSamsung) stringResource(R.string.bg_samsung_body, stringResource(R.string.app_name)) else stringResource(R.string.bg_vendor_body, vendor.name),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { context.openVendorBatterySettings(vendor) }) {
                            Text(stringResource(if (vendor.isSamsung) R.string.bg_open_battery else R.string.bg_open_app_info))
                        }
                        OutlinedButton(onClick = { context.openUrl(vendor.guideUrl) }) {
                            Text(stringResource(R.string.bg_guide))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(ok: Boolean, title: String, status: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        if (!ok) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}
