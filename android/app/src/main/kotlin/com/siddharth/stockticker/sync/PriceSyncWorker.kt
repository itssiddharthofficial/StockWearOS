package com.siddharth.stockticker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.siddharth.stockticker.data.repository.StockRepository
import com.siddharth.stockticker.util.Resource
import java.util.concurrent.TimeUnit

/**
 * Background price refresh.
 *
 * The spec asks for five-minute syncing; Android enforces a **15-minute floor**
 * on periodic work, which is a platform constraint and not configurable. So
 * freshness is delivered in two parts:
 *
 *  - this worker, at the 15-minute floor, keeps the cache warm for a cold open;
 *  - a refresh on every screen open (and on tapping the centre of the home
 *    orbit) keeps data fresh *when it is actually being looked at*.
 *
 * It writes straight to Room, so the UI updates reactively with no extra
 * plumbing, and it never runs without a network — WorkManager batches it into
 * the system's own Doze-aware windows instead of fighting them.
 */
class PriceSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = StockRepository.get(applicationContext)
        return when (repository.refreshWatchlist(force = true)) {
            is Resource.Success -> Result.success()
            // Let WorkManager apply its own backoff rather than retrying in a loop.
            is Resource.Error -> if (runAttemptCount < 3) Result.retry() else Result.success()
            Resource.Loading -> Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "price_sync"

        /** 15 minutes is the platform minimum for periodic work. */
        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceSyncWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
