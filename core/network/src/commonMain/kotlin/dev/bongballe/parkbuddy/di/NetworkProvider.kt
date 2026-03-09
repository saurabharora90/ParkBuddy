package dev.bongballe.parkbuddy.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@ContributesTo(AppScope::class)
interface NetworkProvider {

  @Provides
  fun provideJson(): Json {
    return Json { ignoreUnknownKeys = true }
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideHttpClient(json: Json): HttpClient = HttpClient {
    install(ContentNegotiation) { json(json) }
  }
}
