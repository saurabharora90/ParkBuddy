package dev.bongballe.parkbuddy.di

import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@ContributesTo(AppScope::class)
interface DispatcherProvider {

  @WithDispatcherType(DispatcherType.MAIN)
  @Provides
  fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

  @WithDispatcherType(DispatcherType.MAIN)
  @Provides
  fun providesMainCoroutineContext(
    @WithDispatcherType(DispatcherType.MAIN) dispatcher: CoroutineDispatcher
  ): CoroutineContext = dispatcher

  @WithDispatcherType(DispatcherType.MAIN_IMMEDIATE)
  @Provides
  fun providesMainImmediateDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

  @WithDispatcherType(DispatcherType.MAIN_IMMEDIATE)
  @Provides
  fun providesMainImmediateCoroutineContext(
    @WithDispatcherType(DispatcherType.MAIN_IMMEDIATE) dispatcher: CoroutineDispatcher
  ): CoroutineContext = dispatcher

  @WithDispatcherType(DispatcherType.IO)
  @Provides
  fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

  @WithDispatcherType(DispatcherType.IO)
  @Provides
  fun providesIoCoroutineContext(
    @WithDispatcherType(DispatcherType.IO) dispatcher: CoroutineDispatcher
  ): CoroutineContext = dispatcher

  @WithDispatcherType(DispatcherType.DEFAULT)
  @Provides
  fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

  @WithDispatcherType(DispatcherType.DEFAULT)
  @Provides
  fun providesDefaultCoroutineContext(
    @WithDispatcherType(DispatcherType.DEFAULT) dispatcher: CoroutineDispatcher
  ): CoroutineContext = dispatcher
}
