# Park Buddy - Agent Context

This document provides a high-level overview of the **Park Buddy** project to assist AI agents in
understanding the codebase, architecture, and conventions.

## Domain Knowledge & Core Features

**Park Buddy** helps drivers avoid street cleaning tickets and parking fines. The architecture
is **city-agnostic** with San Francisco as the initial implementation.

* **Data Source**: City open data APIs (currently SF Open Data).
    * Uses a **Four-Way Parallel Sync**: Fetches Regulations, Sweeping, Meter Inventory, and
      Meter Operating Schedules concurrently via `ParkingRepositoryImpl`.
    * Uses high-performance batching (`API_BATCH_LIMIT = 5000`) to maximize data throughput while
      maintaining server stability.
    * Data includes parking spots (`geometry`), sweeping schedules (`weekday`, `fromHour`,
      `toHour`), and week flags (`week1`-`week5`, `holidays`).
    * Metered parking includes specific **Operating Schedules** (hours, days) and
      **Time Limits** (e.g., "60 minutes") from the city's meter database.
    * **Tow Away Zones** are identified from meter schedules and treated as high-urgency
      `Forbidden` states in the `ParkingRestrictionEvaluator`.
* **Unified Segment Model**:
    * Treats **CNN (Centerline ID) + Side (Left/Right)** as the unique "Single Source of Truth"
      for every physical street block.
    * Merges all data sources (RPP, Meters, Sweeping) into a single segment to ensure perfect
      geometric alignment and zero duplicates on the map.
    * Matching threshold is strictly set to **10 meters** for high-density accuracy.
* **Parking Detection**:
    * Triggered by **Bluetooth Disconnection** from a user-selected device (Car Audio).
    * Implemented in `BluetoothConnectionReceiver` using `goAsync` for immediate execution.
    * Fetches high-accuracy location and matches it against the nearest **Parking Spot**.
      distance).
* **Reminders**:
    * Users "watch" streets by selecting an RPP (Residential Parking Permit) zone or by simply parking.
    * Users configure reminder offsets (e.g., "24 hours before").
    * Alarms are scheduled via `AlarmManager` (Exact Alarms) for:
        1. **Street Cleaning**: Before the next valid cleaning time.
        2. **Time Limits**: Before the current `ActiveTimed` restriction expires.
    * **Logic**: Handles 24h time formats, week specificity, and holidays (`HolidayUtils`).
    * **Universal Safety Net**: Even on unrestricted (free) streets, the app identifies and
      schedules reminders for street cleaning to prevent surprise tickets.

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

- `SfOpenDataApi`: Retrofit client for SF Open Data endpoints.
- `ParkingRepositoryImpl`: Standardized parallel fetch and merge logic using the Unified Segment Model.
- `CoordinateMatcher`: Spatial matching using grid-based spatial index. Snaps points and
  polylines to the nearest street centerline.
- Room database with `ParkingSpotEntity`, `SweepingScheduleEntity`, `MeterScheduleEntity`, and

### Module Structure

```
app/                    # Application entry point, AppGraph DI wiring
feature/
├── map/                # Map view showing parking spots (Google Maps)
├── reminders/          # Permit zone management, reminder settings, ParkingManager
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

| Category   | Technology                                  |
|------------|---------------------------------------------|
| Language   | Kotlin                                      |
| UI         | Jetpack Compose (Material3)                 |
| Navigation | AndroidX Navigation 3                       |
| DI         | [Metro](https://github.com/zacsweers.metro) |
| Async      | Kotlin Coroutines & Flow                    |
| Network    | Retrofit, OkHttp, Kotlinx Serialization     |
| Database   | Room                                        |
| Maps       | Maps Compose (Google Maps)                  |

## Build & Tooling

* **Build System**: Gradle (Kotlin DSL) with Version Catalogs (`libs.versions.toml`)
* **Convention Plugins**: `com.slack.foundry` for build config, `dev.zacsweers.metro` for DI
* **Static Analysis**: Detekt, Spotless (formatting)
* **Testing**: JUnit 4, Truth, Robolectric, Roborazzi (screenshot testing)
    * **NO MOCKS**: Use Fakes or real implementations. Mockito is forbidden.

### Common Commands

```bash
./gradlew assembleDebug          # Build
./gradlew test                   # Run tests
./gradlew spotlessApply          # Format code
./gradlew detekt                 # Static analysis
```

## Testing Conventions

Rigor in testing is a core requirement. Follow these patterns to ensure tests are reliable,
idiomatic, and maintainable.

### Core Principles

* **NO MOCKS**: Mockito and other mocking frameworks are forbidden. Use **Fakes** (located in
  `:core:testing`) or real implementations.
* **Robolectric for Framework Logic**: For code that interacts with Android components (`Context`,
  `AlarmManager`, `DataStore`, `PendingIntent`), use **Robolectric**. Avoid abstracting the
  framework just to make it "testable" without Robolectric.
* **State Isolation**: Tests must be independent. For persistent components like `DataStore`,
  explicitly clear state in the setup phase to prevent cross-test leakage.

### Test Structure (TestContext Pattern)

Avoid `lateinit var` for test dependencies. Use a `private class TestContext` to encapsulate setup
logic. This keeps the test class clean and ensures each test starts with a fresh, isolated state.

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

* Always add `@Preview` functions for different states (empty, loading, populated)
* Use naming convention `MyComponentPreview_State`

### Code Style

* Adhere to Spotless formatting (ktfmt)
* Navigation uses the newer Navigation 3 type-safe APIs

## Creating New Modules

Follow patterns of existing modules (e.g., `:feature:map` or `:core:model`).

**Key rules:**

1. Apply `alias(libs.plugins.foundry.base)` - handles compileSdk, kotlinOptions, etc.
2. Only provide `android { namespace = "..." }` - nothing else in android block
3. Use `foundry { features { ... } }` for Compose/Metro

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
