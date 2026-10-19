package io.github.passradar

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import io.github.passradar.net.LegacyTls
import kotlinx.coroutines.launch

class PassradarApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        LegacyTls.install(this)
        container = AppContainer(this)
        container.notifier.createChannel()
        container.appScope.launch {
            try {
                container.repository.reschedule()
            } catch (e: Exception) {
                Log.w("Passradar", "Could not schedule background checks", e)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The channel's name shows in Android's settings, so it follows the language.
        container.notifier.createChannel()
    }
}
