package io.github.passvarsling

import android.app.Application
import android.util.Log
import io.github.passvarsling.net.LegacyTls
import kotlinx.coroutines.launch

class PassvarslingApp : Application() {

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
                Log.w("Passvarsling", "Could not schedule background checks", e)
            }
        }
    }
}
