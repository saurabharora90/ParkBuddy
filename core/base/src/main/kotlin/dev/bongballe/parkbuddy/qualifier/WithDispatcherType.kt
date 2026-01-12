package dev.bongballe.parkbuddy.qualifier

import dev.bongballe.parkbuddy.DispatcherType
import dev.zacsweers.metro.Qualifier

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WithDispatcherType(val value: DispatcherType)
