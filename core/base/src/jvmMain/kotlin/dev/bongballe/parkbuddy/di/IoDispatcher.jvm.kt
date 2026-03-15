package dev.bongballe.parkbuddy.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Suppress("InjectDispatcher")
internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
