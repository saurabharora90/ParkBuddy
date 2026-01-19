package dev.bongballe.parkbuddy

import android.app.Application
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.bongballe.parkbuddy.data.DataRefreshWorker
import dev.bongballe.parkbuddy.di.AppGraph
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication
import java.util.concurrent.TimeUnit

class ParkBuddyApplication : Application(), MetroApplication, Configuration.Provider {

  private val appGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }

  override val appComponentProviders: MetroAppComponentProviders
    get() = appGraph

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(appGraph.workerFactory).build()

  override fun onCreate() {
    super.onCreate()
    setupPeriodicDataRefresh()
  }

  private fun setupPeriodicDataRefresh() {
    val constraints =
      Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    val refreshRequest =
      PeriodicWorkRequestBuilder<DataRefreshWorker>(7, TimeUnit.DAYS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(this)
      .enqueueUniquePeriodicWork(
        "DataRefreshWorker",
        ExistingPeriodicWorkPolicy.KEEP,
        refreshRequest,
      )
  }
}
