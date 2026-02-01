package dev.bongballe.parkbuddy.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
@Inject
class AnalyticsTrackerImpl(context: Context) : AnalyticsTracker {

  private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
  private val crashlytics = FirebaseCrashlytics.getInstance()

  override fun logEvent(name: String, params: Map<String, Any?>) {
    val bundle = Bundle()
    params.forEach { (key, value) ->
      when (value) {
        is String -> bundle.putString(key, value)
        is Int -> bundle.putInt(key, value)
        is Long -> bundle.putLong(key, value)
        is Double -> bundle.putDouble(key, value)
        is Boolean -> bundle.putBoolean(key, value)
      }
    }
    firebaseAnalytics.logEvent(name, bundle)
  }

  override fun setUserProperty(name: String, value: String?) {
    firebaseAnalytics.setUserProperty(name, value)
  }

  override fun logNonFatal(throwable: Throwable, message: String?) {
    message?.let { crashlytics.log(it) }
    crashlytics.recordException(throwable)
  }

  override fun setCustomKey(key: String, value: String) {
    crashlytics.setCustomKey(key, value)
  }
}
