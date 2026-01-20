package dev.bongballe.parkbuddy.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.bongballe.parkbuddy.data.repository.StreetCleaningRepository
import dev.parkbuddy.core.workmanager.MetroWorkerFactory
import dev.parkbuddy.core.workmanager.WorkerKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding

@AssistedInject
class DataRefreshWorker(
  context: Context,
  private val repository: StreetCleaningRepository,
  @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result {
    val result = repository.refreshData()
    return if (result) Result.success() else Result.retry()
  }

  @WorkerKey(DataRefreshWorker::class)
  @ContributesIntoMap(
    AppScope::class,
    binding = binding<MetroWorkerFactory.WorkerInstanceFactory<*>>(),
  )
  @AssistedFactory
  abstract class Factory : MetroWorkerFactory.WorkerInstanceFactory<DataRefreshWorker>
}
