package io.github.passradar

import android.content.Context
import io.github.passradar.data.BundledOffices
import io.github.passradar.data.PolitietApi
import io.github.passradar.data.Repository
import io.github.passradar.data.Store
import io.github.passradar.notify.Notifier
import io.github.passradar.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/** Creates the app's long-lived objects once and hands them out. */
class AppContainer(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val notifier = Notifier(context)
    val repository = Repository(
        api = PolitietApi(),
        store = Store(context, json),
        bundled = BundledOffices(context, json),
        scheduler = Scheduler(context),
        notifier = notifier,
    )
}
