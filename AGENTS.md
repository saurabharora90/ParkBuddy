# Park Buddy - Agent Context

This document provides a high-level overview of the **Park Buddy** project to assist AI agents in
understanding the codebase, architecture, and conventions.

## Domain Knowledge & Core Features

**Park Buddy** helps drivers avoid street cleaning tickets and parking fines. The architecture is
**city-agnostic** with San Francisco as the initial implementation.

* **Timeline Normalization**: During data sync, overlapping regulations and meter schedules are
  pre-resolved into a flat `List<ParkingInterval>` using priority-based resolution:
  `FORBIDDEN(4) > RESTRICTED(3) > METERED(2) > LIMITED(1) > OPEN(0)`.
  The evaluator is a thin lookup over this timeline. Gaps are implicitly OPEN.
* **Sweeping Kept Separate**: Street cleaning schedules carry week-of-month semantics (week1-week5)
  that a weekly timeline cannot represent, so they stay as `List<SweepingSchedule>` on `ParkingSpot`.
  The evaluator checks sweeping first, then the timeline.
* **Parking Detection**:
    * **Android**: Triggered by Bluetooth disconnection from a user-selected device.
    * **iOS**: Triggered via Apple Shortcuts automation.
    * Both platforms fetch high-accuracy location and match against the nearest parking spot.
* **Reminders**: True limit = min(time limit expiry, next FORBIDDEN start, next cleaning start).
  Universal safety net: even on free streets, cleaning reminders are scheduled.

## Architecture

**Kotlin Multiplatform (KMP)** targeting Android and iOS. All business logic, ViewModels, data
layer, and most UI (Compose Multiplatform) live in `commonMain`. Platform-specific code is minimal.

### Multi-City Design

```
core/data/
├── api/          # Interfaces + city-agnostic logic
│                 # TimelineResolver, ParkingRestrictionEvaluator
├── impl/         # Shared implementations (reminders, preferences)
└── sf-impl/      # SF-specific: database, API client, DayParser, TimeParser,
                  # ParkingRegulation enum, repository impl
```

Adding a new city means creating `core/data/nyc-impl` without touching existing code.

### Key Domain Types

```
IntervalType (sealed interface, core:model)
├── Open(0)  ├── Limited(1)  ├── Metered(2)  ├── Restricted(3)  └── Forbidden(4)

ParkingInterval  - type, days, startTime, endTime, exemptPermitZones, source
ParkingSpot      - timeline, sweepingSchedules, rppAreas
                   Derived: isParkable, hasMeters, isCommercial (from timeline)
ParkingRestrictionState (sealed class) - what it means for the user RIGHT NOW
  CleaningActive, Unrestricted, PermitSafe, ActiveTimed, PendingTimed, Forbidden
```

## Tech Stack

| Category   | Technology                                                     |
|------------|----------------------------------------------------------------|
| Language   | Kotlin                                                         |
| UI         | Compose Multiplatform                                          |
| DI         | [Metro](https://github.com/ZacSweers/metro)                    |
| Async      | Kotlin Coroutines & Flow                                       |
| Network    | Ktor (Darwin engine on iOS, OkHttp on Android)                 |
| Database   | Room KMP (BundledSQLiteDriver on iOS)                          |
| Maps       | kmp-maps (Software Mansion) - Google Maps / Apple MapKit       |
| Navigation | AndroidX Navigation 3 (Android), CMP navigation (iOS, pending) |
| Prefs      | AndroidX DataStore (KMP)                                       |

### iOS-specific

| Concern       | Technology                                                          |
|---------------|---------------------------------------------------------------------|
| Swift interop | Kotlin/Native ObjC export (SKIE disabled, uses completion handlers) |
| Xcode project | xcodegen from `iosApp/project.yml`                                  |
| Shortcuts     | AppIntents framework (`LogParkingIntent.swift`)                     |
| Background    | BGTaskScheduler (`dev.parkbuddy.datarefresh`)                       |
| Analytics     | Firebase iOS SDK via SPM                                            |

## Build & Tooling

* **Build System**: Gradle (Kotlin DSL) with Version Catalogs (`libs.versions.toml`)
* **Convention Plugins**: `com.slack.foundry` for build config, `dev.zacsweers.metro` for DI
* **Static Analysis**: Detekt, Ktfmt (formatting)
* **Testing**: JUnit 4, Truth, Robolectric, Roborazzi (screenshot testing)
    * **NO MOCKS**: Use Fakes or real implementations. Mockito is forbidden.

### Common Commands

```bash
# Android
./gradlew assembleDebug                                    # Build Android APK
./gradlew test                                             # Run unit tests
./gradlew ktfmtFormat                                      # Format code
./gradlew detekt                                           # Static analysis

# iOS
./gradlew :iosExport:linkDebugFrameworkIosSimulatorArm64   # Build KMP framework for iOS Simulator
cd iosApp && xcodegen generate                             # Generate Xcode project from project.yml
xcodebuild build \
  -project iosApp/ParkBuddy.xcodeproj \
  -scheme ParkBuddy \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.6'  # Build iOS app

# The Xcode build automatically:
#   1. Runs Gradle to link the KMP framework (pre-build script)
#   2. Syncs compose resources (fonts, images) into the app bundle (post-compile script)
```

### iOS Build Notes

* **Xcode project**: Generated by `xcodegen` from `iosApp/project.yml`. Do NOT edit the .xcodeproj
  directly; edit `project.yml` and regenerate.
* **Deployment target**: iOS 18.0
* **Framework**: Static framework at
  `iosExport/build/bin/iosSimulatorArm64/debugFramework/ParkBuddyShared.framework`
* **Compose resources**: The `syncComposeResourcesForIos` Gradle task must run inside Xcode
  (needs `$BUILT_PRODUCTS_DIR`). It copies fonts and images into the app bundle.
* **SKIE**: Disabled until it supports Kotlin 2.3.20. Without SKIE, Kotlin suspend functions
  export as ObjC completion handlers instead of Swift async/await.
* **Gradle caching**: Kotlin/Native incremental builds can be stale. Use `--rerun-tasks` when
  debugging framework changes that don't seem to take effect.

## Testing Conventions

* **NO MOCKS**: Use **Fakes** (in `:core:testing`) or real implementations. Mockito is forbidden.
* **Robolectric** for Android framework logic (`Context`, `AlarmManager`, `DataStore`).
* **State Isolation**: Clear persistent state in setup. Use `private class TestContext` pattern.
* **Real Data**: Raw city data available in `full_api_data/` for edge case tests.

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

**iOS DI**: `IosAppGraph` in `:iosExport` module. Created in Swift via
`IosAppGraphCompanion.shared.create(analyticsTracker:)`. The `metroViewModelFactory` is provided
to CMP via
`CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory)`.

### Compose

* All UI screens are Compose Multiplatform in `commonMain`
* Platform-specific composables use `expect`/`actual` (e.g., `PermissionState`,
  `PlatformThemeEffect`)
* Always add `@Preview` functions for different states (empty, loading, populated)
* Use naming convention `MyComponentPreview_State`

## Creating New Modules

Follow patterns of existing KMP modules (e.g., `:feature:map` or `:core:model`).

**Key rules:**

1. Apply `alias(libs.plugins.kotlin.multiplatform)` and `alias(libs.plugins.foundry.base)`
2. Add `iosArm64()` + `iosSimulatorArm64()` targets alongside `androidTarget()`
3. Use `foundry { features { ... } }` for Compose/Metro
4. Platform-specific implementations go in `androidMain`/`iosMain` source sets

**Example (KMP library module):**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.foundry.base)
    alias(libs.plugins.metro)
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

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:data:api"))
            implementation(project(":core:model"))
        }
    }
}
```
