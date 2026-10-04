package com.siddharth.stockticker

import android.app.Application
import androidx.work.Configuration
import com.siddharth.stockticker.sync.PriceSyncWorker

/**
 * WorkManager is initialised on demand (the default initialiser is removed in the
 * manifest) so app start does no more work than it has to — cold-start time is
 * what a watch app is judged on.
 */
class StockTickerApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()
        PriceSyncWorker.schedule(this)
    }
}
