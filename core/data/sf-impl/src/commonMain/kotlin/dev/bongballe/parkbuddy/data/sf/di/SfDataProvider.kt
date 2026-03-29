package dev.bongballe.parkbuddy.data.sf.di

import dev.bongballe.parkbuddy.data.sf.network.SfOpenDataApi
import dev.bongballe.parkbuddy.data.sf.network.SfmtaArcGisApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient

@ContributesTo(AppScope::class)
interface SfDataProvider {

  @Provides
  @SingleIn(AppScope::class)
  fun provideSocrataApi(httpClient: HttpClient): SfOpenDataApi {
    return SfOpenDataApi(httpClient, baseUrl = "https://data.sfgov.org/")
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideArcGisApi(httpClient: HttpClient): SfmtaArcGisApi {
    return SfmtaArcGisApi(
      httpClient,
      baseUrl = "https://services.sfmta.com/arcgis/rest/services/Parking/",
    )
  }
}
