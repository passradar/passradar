package io.github.passradar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch

/**
 * Debug builds only. Pretends the earliest free date at every followed office just
 * opened up and runs a check, so you can see a notification without waiting:
 *
 *   adb shell am broadcast -n io.github.passradar.debug/io.github.passradar.DebugReceiver
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PassradarApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.repository.forgetEarliestKnownDates()
                Log.i("Passradar", "Simulated check: ${container.repository.checkWatches()}")
            } finally {
                pending.finish()
            }
        }
    }
}
