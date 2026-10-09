package io.github.passvarsling.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.passvarsling.PassvarslingApp
import kotlinx.coroutines.CancellationException

/** Periodic job: checks the watched offices and notifies about new free dates. */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as PassvarslingApp).container.repository
        try {
            val result = repository.checkWatches()
            Log.i("Passvarsling", "Background check: $result")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next periodic run tries again; failing here would only add a backoff.
            Log.w("Passvarsling", "Background check failed", e)
        }
        return Result.success()
    }
}
