package dev.bongballe.parkbuddy.qualifier

import dev.zacsweers.metro.Qualifier
import kotlin.reflect.KClass

/**
 * A Qualifier used to select as instance of an object based on its injection scope. See [Qualifier]
 * for more info
 */
@Target(
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.CLASS,
  AnnotationTarget.PROPERTY_GETTER,
)
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WithScope(val scope: KClass<*>)
