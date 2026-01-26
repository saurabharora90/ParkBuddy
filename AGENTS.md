# Park Buddy - Agent Context

This document provides a high-level overview of the **Park Buddy** project to assist AI agents in understanding the codebase, architecture, and conventions.

## Domain Knowledge & Core Features

**Park Buddy** helps drivers avoid street cleaning tickets. The architecture is **city-agnostic** with San Francisco as the initial implementation.

*   **Data Source**: City open data APIs (currently SF Open Data).
    *   Data includes parking spots (`geometry`), sweeping schedules (`weekday`, `fromHour`, `toHour`), and week flags (`week1`-`week5`, `holidays`).
    *   Parking regulations are matched to sweeping schedules via coordinate proximity (`CoordinateMatcher`).
*   **Parking Detection**:
    *   Triggered by **Bluetooth Disconnection** from a user-selected device (Car Audio).
    *   Implemented in `BluetoothConnectionReceiver` using `goAsync` for immediate execution.
    *   Fetches high-accuracy location and matches it against the user's **Watchlist** (< 30m distance).
*   **Reminders**:
    *   Users "watch" streets by selecting an RPP (Residential Parking Permit) zone.
    *   Users configure reminder offsets (e.g., "24 hours before").
    *   Alarms are scheduled via `AlarmManager` (Exact Alarms) based on the next valid cleaning time.
    *   **Logic**: Handles 24h time formats, week specificity, and holidays (`HolidayUtils`).

## Architecture

The project follows a modularized architecture with clear separation of concerns.

### Multi-City Design

The data layer separates city-agnostic interfaces from city-specific implementations:

```
core/data/
├── api/          # Interfaces (ParkingRepository, PreferencesRepository)
├── impl/         # Shared implementations (DataRefreshWorker, PreferencesRepositoryImpl)
└── sf-impl/      # SF-specific: database, API client, repository impl
```

**Why this structure?**
- Database schemas are city-specific (SF uses week1-week5 booleans, CNN identifiers)
- API clients vary by city (SF Open Data vs NYC Open Data vs LA GeoHub)
- Features depend only on `core:data:api` interfaces, not implementations
- Adding a new city means creating `core/data/nyc-impl` without touching existing code

**SF-specific code** (`sf-impl`):
- `SfOpenDataApi`: Retrofit client for SF Open Data endpoints
- `ParkingRepositoryImpl`: Fetches parking regulations + sweeping schedules, matches via coordinates
- `CoordinateMatcher`: Spatial matching using R-tree index
- Room database with `ParkingSpotEntity`, `SweepingScheduleEntity`, `UserPreferencesEntity`

### Module Structure

```
app/                    # Application entry point, AppGraph DI wiring
feature/
├── map/                # Map view showing parking spots (Google Maps)
├── reminders/          # Watchlist, reminder settings, ParkingManager
└── onboarding/         # Setup flow (permissions, Bluetooth device selection)
core/
├── model/              # Domain entities (ParkingSpot, SweepingSchedule, Geometry)
├── data/
│   ├── api/            # Repository interfaces (city-agnostic)
│   ├── impl/           # Shared implementations (city-agnostic)
│   └── sf-impl/        # San Francisco implementation
├── network/            # Shared network utilities (OkHttp, JSON config)
├── work-manager/       # WorkManager utilities and Metro integration
├── theme/              # UI design system and Compose theming
├── base/               # Common base classes, dispatchers, utilities
└── bluetooth/          # Bluetooth utilities
```

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose (Material3) |
| Navigation | AndroidX Navigation 3 |
| DI | [Metro](https://github.com/zacsweers/metro) |
| Async | Kotlin Coroutines & Flow |
| Network | Retrofit, OkHttp, Kotlinx Serialization |
| Database | Room |
| Maps | Maps Compose (Google Maps) |

## Build & Tooling

*   **Build System**: Gradle (Kotlin DSL) with Version Catalogs (`libs.versions.toml`)
*   **Convention Plugins**: `com.slack.foundry` for build config, `dev.zacsweers.metro` for DI
*   **Static Analysis**: Detekt, Spotless (formatting)
*   **Testing**: JUnit 4, Truth, Robolectric, Roborazzi (screenshot testing)
    *   **NO MOCKS**: Use Fakes or real implementations. Mockito is forbidden.

### Common Commands

```bash
./gradlew assembleDebug          # Build
./gradlew test                   # Run tests
./gradlew spotlessApply          # Format code
./gradlew detekt                 # Static analysis
```

## Key Conventions

### Dependency Injection (Metro)

Use `@DependencyGraph` and Metro's graph generation instead of Hilt/Dagger.

**ViewModels** must be annotated:
```kotlin
@ContributesIntoMap(AppScope::class)
@ViewModelKey(MyViewModel::class)
@Inject
class MyViewModel(...) : ViewModel()
```

### Compose

*   Always add `@Preview` functions for different states (empty, loading, populated)
*   Use naming convention `MyComponentPreview_State`

### Code Style

*   Adhere to Spotless formatting (ktfmt)
*   Navigation uses the newer Navigation 3 type-safe APIs

## Creating New Modules

Follow patterns of existing modules (e.g., `:feature:map` or `:core:model`).

**Key rules:**
1.  Apply `alias(libs.plugins.foundry.base)` - handles compileSdk, kotlinOptions, etc.
2.  Only provide `android { namespace = "..." }` - nothing else in android block
3.  Use `foundry { features { ... } }` for Compose/Metro

**Example:**
```kotlin
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.foundry.base)
}

android {
  namespace = "dev.bongballe.parkbuddy.feature.newfeature"
}

foundry {
  features {
    compose()
    metro()
  }
}

dependencies {
  implementation(project(":core:data:api"))
  implementation(project(":core:model"))
}
```
