package dev.bongballe.parkbuddy.analytics

interface AnalyticsTracker {
  fun logEvent(name: String, params: Map<String, Any?> = emptyMap())

  fun setUserProperty(name: String, value: String?)

  fun logNonFatal(throwable: Throwable, message: String? = null)

  fun setCustomKey(key: String, value: String)
}
