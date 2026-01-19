package dev.bongballe.parkbuddy.di

import android.app.Application
import android.content.Context
import androidx.work.ListenableWorker
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.bongballe.parkbuddy.qualifier.WithScope
import dev.parkbuddy.core.workmanager.MetroWorkerFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@DependencyGraph(AppScope::class)
interface AppGraph : MetroAppComponentProviders, ViewModelGraph {

  @Provides fun provideApplicationContext(application: Application): Context = application

  @Multibinds
  val workerProviders:
    Map<KClass<out ListenableWorker>, Provider<MetroWorkerFactory.WorkerInstanceFactory<*>>>

  val workerFactory: MetroWorkerFactory

  @Provides
  @WithScope(AppScope::class)
  fun providesApplicationCoroutineScope(
    @WithDispatcherType(DispatcherType.MAIN) dispatcher: CoroutineDispatcher
  ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides application: Application): AppGraph
  }
}
