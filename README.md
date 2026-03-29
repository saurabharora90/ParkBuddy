# ParkBuddy

ParkBuddy helps drivers avoid street cleaning tickets and parking fines. It automatically detects
when you park, monitors restrictions in real time, and sends reminders before you get a ticket.

Built as a **Kotlin Multiplatform (KMP)** app targeting **Android** and **iOS** with a shared
codebase.

## Features

- **Automated Parking Detection**: Records your parking location when your car's Bluetooth
  disconnects (Android: broadcast receiver, iOS: Apple Shortcuts automation).
- **Timeline-Based Parking Rules**: Pre-resolves overlapping regulations, meters, and tow zones
  into a clean weekly timeline during data sync. No more duplicate or conflicting rules.
- **Real-Time Map Colors**: Street segments update color based on what's happening right now
  (green = free, gold = metered, purple = time-limited, red = restricted/no parking).
- **True Limit Calculation**: If you park in a 2-hour zone but tow-away starts in 1 hour, the
  app tells you "1 hour" (not "2 hours").
- **Tow Zone Awareness**: High-urgency warnings for Tow Away zones based on city schedules.
- **RPP Zone Integration**: Syncs with city data for Residential Parking Permit zone info. Permit
  holders see "Free for you" when their zone is exempt from time limits.
- **Universal Safety Net**: Monitors street cleaning schedules even on unrestricted streets.
- **Smart Reminders**: Notifications before street cleaning or when timed parking is about to
  expire. Effective deadline accounts for upcoming tow zones and cleaning.
- **Interactive Map**: Visualize parking zones and real-time restriction status (Google Maps on
  Android, Apple MapKit on iOS).

## Architecture

The project is city-agnostic with San Francisco as the first implementation. Adding a new city
(e.g., `core/data/nyc-impl`) requires only a new data layer implementation. The timeline
resolver, evaluator, UI, and reminder system are all city-agnostic.

All business logic, ViewModels, data layer, and most UI live in `commonMain` (shared Kotlin).
Platform-specific code is minimal: `androidMain` for Android APIs, `iosMain` for iOS/Darwin APIs.

### How It Works

1. **Pre-built DB**: Each city ships a pre-built Room database as a bundled asset. First launch is
   instant, no network or parsing required.
2. **Refresh**: A background worker periodically downloads fresh data from city APIs, rebuilds the
   DB, and cleans up temporary files.
3. **Resolve**: Overlapping rules are flattened into non-overlapping `ParkingInterval`s using
   priority: FORBIDDEN > RESTRICTED > METERED > LIMITED > OPEN.
4. **Evaluate**: At runtime, finds the active interval for "right now" and checks permits.
5. **Remind**: Schedules alarms for the earliest deadline (time limit, tow zone, or cleaning).

### Tech Stack

| Category   | Technology                                                 |
|------------|------------------------------------------------------------|
| Language   | Kotlin (shared), Swift (iOS shell)                         |
| UI         | Compose Multiplatform                                      |
| DI         | [Metro](https://github.com/ZacSweers/metro)                |
| Async      | Kotlin Coroutines & Flow                                   |
| Network    | Ktor (OkHttp on Android, Darwin engine on iOS)             |
| Database   | Room KMP (BundledSQLiteDriver on iOS)                      |
| Maps       | kmp-maps (Software Mansion): Google Maps / Apple MapKit    |
| Navigation | AndroidX Navigation 3 (Android), CMP navigation (iOS, TBD) |
| Prefs      | AndroidX DataStore (KMP)                                   |

## Prerequisites

### Common

- JDK 21
- Clone the repo and ensure Gradle wrapper is executable: `chmod +x gradlew`

### Android

- Android Studio Ladybug or newer
- Android SDK 34+
- A Google Cloud project with **Maps SDK for Android** enabled
- A Firebase project (for analytics/crashlytics)

### iOS

- Xcode 16+ (macOS only)
- [xcodegen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`
- iOS 18.0+ deployment target

## Configuration

### Google Maps API Key (Android)

Create `local.defaults.properties` in the project root:

```properties
MAPS_API_KEY=your_api_key_here
```

### Firebase (Android)

1. Register `dev.bongballe.parkbuddy` in your Firebase console.
2. Download `google-services.json` and place it in `app/`.

### Firebase (iOS)

TODO: Add `GoogleService-Info.plist` via SPM once Firebase iOS integration is complete.

## Building & Running

### Android

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
./gradlew ktfmtFormat            # Format code
./gradlew detekt                 # Static analysis
```

Or open the project in Android Studio and run the `app` configuration.

### iOS

**First time only**, generate the Xcode project:

```bash
cd iosApp && xcodegen generate
```

Then open `iosApp/ParkBuddy.xcodeproj` in Xcode and hit Run. The Xcode build automatically:

1. Runs Gradle to compile Kotlin and link the KMP framework (pre-build script)
2. Syncs Compose resources (fonts, images) into the app bundle (post-compile script)

You do not need to run Gradle manually. Xcode handles it.

**From the command line** (useful for CI):

```bash
./gradlew :iosExport:linkDebugFrameworkIosSimulatorArm64

cd iosApp && xcodegen generate

xcodebuild build \
  -project iosApp/ParkBuddy.xcodeproj \
  -scheme ParkBuddy \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.6'
```

### iOS Build Notes

- **Xcode project**: Generated by xcodegen from `iosApp/project.yml`. Edit the YAML, not the
  `.xcodeproj` directly.
- **Framework**: Static framework at
  `iosExport/build/bin/iosSimulatorArm64/debugFramework/ParkBuddyShared.framework`
- **Compose resources**: The `syncComposeResourcesForIos` Gradle task runs inside Xcode (needs
  `$BUILT_PRODUCTS_DIR`). It copies fonts and images into the app bundle.
- **SKIE**: Disabled until it supports Kotlin 2.3.20. Kotlin suspend functions export as ObjC
  completion handlers instead of Swift async/await.
- **Stale builds**: Kotlin/Native incremental builds can occasionally miss changes. Use
  `--rerun-tasks` if edits don't seem to take effect.

## Data Pipeline

Each city module (`core/data/<city>-impl`) owns its parking data: raw API sources, parsing logic,
spatial matching, and database schema. The app ships a **pre-built Room database** per city so first
launch is instant.

### How it works

```
sf-data/*.json ──▶ GeneratePrebuiltDb ──▶ park_buddy_db
                                               │
                                 bundled into app (KMP resources)
                                               │
                              first launch: copy to DB path if needed
                                               │
                                         Room opens DB ──▶ App ready
```

On first launch, the database provider copies the bundled `park_buddy_db` to the app's database
directory and writes a `.version` marker file. On subsequent launches, the marker is checked and
the copy is skipped. On app updates with a new `DB_VERSION`, the old DB is deleted and re-copied.

At runtime, a background worker periodically downloads fresh data from city APIs, rebuilds the DB,
and deletes the temporary files.

### Regenerating the pre-built database

Each city module has two data directories:

- **`sf-data/`** (module root): Source `.json` files from city APIs (tracked in git, never bundled
  into the app)
- **`src/commonMain/resources/sf-data/`**: Generated `park_buddy_db` (tracked in git, bundled into
  the app via KMP resources)

Using SF as an example:

```bash
# Regenerate after updating source .json files or changing the pipeline:
./gradlew :core:data:sf-impl:testAndroidHostTest --tests "*GeneratePrebuiltDb*"
```

This reads the `.json` files from `core/data/sf-impl/sf-data/`, runs the full pipeline (spatial
matching, timeline resolution, etc.), and outputs `park_buddy_db` to
`src/commonMain/resources/sf-data/`.

### Updating the pre-built database for an app release

There are two reasons to regenerate the pre-built database:

**New source data** (city APIs changed, new streets, updated regulations):
1. Update the `.json` source files in `sf-data/` (download fresh data from city APIs or pull from
   a device that ran `refreshData`).
2. Regenerate the pre-built database (see above).
3. Bump `DB_VERSION` in the city's `Database` class (e.g., `ParkBuddyDatabase`).
4. Commit the updated `.json` files, regenerated `park_buddy_db`, and version bump.

**Pipeline bug fix** (changed parsing, spatial matching, timeline resolution, etc.):
1. Fix the bug in the pipeline code.
2. Regenerate the pre-built database from the existing `.json` files (see above).
3. Bump `DB_VERSION` in the city's `Database` class.
4. Commit the code fix, regenerated `park_buddy_db`, and version bump.

In both cases, bumping `DB_VERSION` is what triggers existing installs to replace their on-device
DB with the new bundled copy on next launch.

### Adding a new city

1. Create `core/data/<city>-impl` following the SF module's structure.
2. Add `.json` source files to `<city>-data/` (module root, tracked in git).
3. Implement the data pipeline: API models, parsers, spatial matching, `ParkingRepositoryImpl`.
4. Create a Room database class with its own `DB_VERSION` constant.
5. Create `DatabaseProvider` (Android) and `IosDatabaseProvider` (iOS) with the version-marker
   copy logic (see SF's implementations for reference).
6. Create a `GeneratePrebuiltDb` test in `androidHostTest/.../tools/` that reads the `.json`
   files, runs the pipeline, and outputs `park_buddy_db` to `src/commonMain/resources/<city>-data/`.
7. Add a Gradle copy task in `build.gradle.kts` to copy the `.db` to `iosMain/resources/` for iOS
   (Android picks it up automatically from `commonMain/resources/`).

## Testing

```bash
./gradlew test                        # All unit tests
./gradlew globalCiVerifyRoborazzi     # Screenshot tests
```

**No mocks.** Use fakes (in `:core:fakes`) or real implementations. Mockito is forbidden.
