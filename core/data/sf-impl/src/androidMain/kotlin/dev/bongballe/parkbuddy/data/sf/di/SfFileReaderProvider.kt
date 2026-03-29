package dev.bongballe.parkbuddy.data.sf.di

import android.content.Context
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.data.io.AndroidDataFileReader
import dev.bongballe.parkbuddy.data.io.DataFileReader
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

@ContributesTo(AppScope::class)
interface SfFileReaderProvider {

  @Provides
  @SingleIn(AppScope::class)
  fun provideDataFileReader(
    context: Context,
    @WithDispatcherType(DispatcherType.IO) ioDispatcher: CoroutineDispatcher,
  ): DataFileReader = AndroidDataFileReader(context, "sf-data", ioDispatcher)
}
