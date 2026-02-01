package dev.bongballe.parkbuddy

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
@Inject
class AppConfigImpl : AppConfig {
  override val versionName: String = BuildConfig.VERSION_NAME
  override val versionCode: Int = BuildConfig.VERSION_CODE
}
