package io.github.passvarsling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch

/**
 * Debug builds only. Pretends the earliest free date at every followed office just
 * opened up and runs a check, so you can see a notification without waiting:
 *
 *   adb shell am broadcast -n io.github.passvarsling.debug/io.github.passvarsling.DebugReceiver
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PassvarslingApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.repository.forgetEarliestKnownDates()
                Log.i("Passvarsling", "Simulated check: ${container.repository.checkWatches()}")
            } finally {
                pending.finish()
            }
        }
    }
}
