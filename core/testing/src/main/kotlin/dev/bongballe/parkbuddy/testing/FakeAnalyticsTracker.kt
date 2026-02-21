package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.analytics.AnalyticsTracker

class FakeAnalyticsTracker : AnalyticsTracker {
  override fun logEvent(name: String, params: Map<String, Any?>) {
    // No-op
  }

  override fun setUserProperty(name: String, value: String?) {
    // No-op
  }

  override fun logNonFatal(throwable: Throwable, message: String?) {
    // No-op
  }

  override fun setCustomKey(key: String, value: String) {
    // No-op
  }
}
