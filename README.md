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

1. **Sync**: Fetches four SF Open Data APIs in parallel (sweeping, regulations, meters, schedules)
2. **Resolve**: Flattens overlapping rules into non-overlapping `ParkingInterval`s using priority:
   FORBIDDEN > RESTRICTED > METERED > LIMITED > OPEN
3. **Store**: Timeline stored as JSON on each parking spot in Room
4. **Evaluate**: At runtime, finds the active interval for "right now" and checks permits
5. **Remind**: Schedules alarms for the earliest deadline (time limit, tow zone, or cleaning)

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

## Testing

```bash
./gradlew test                        # All unit tests
./gradlew globalCiVerifyRoborazzi     # Screenshot tests
```

**No mocks.** Use fakes (in `:core:testing`) or real implementations. Mockito is forbidden.

Raw SF data is available in `full_api_data/` for writing tests against real API responses.
