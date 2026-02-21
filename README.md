# ParkBuddy 🚗

ParkBuddy is a smart Android application designed to simplify urban parking. It helps users track their vehicle's location, monitor Residential Parking Permit (RPP) zones, and receive timely reminders to avoid parking tickets.

## Features

- **Automated Parking Detection:** Automatically records your parking location when your car's Bluetooth disconnects.
- **RPP Zone Integration:** Syncs with city data to provide up-to-date information on parking restrictions.
- **Permit Zone Management:** Monitor your residential permit zone and receive notifications before restrictions take effect.
- **Interactive Map:** Visualize your parked location and nearby parking zones.
- **Modern Stack:** Built with Kotlin, Jetpack Compose, and a modular architecture.

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer.
- Android SDK 34+.
- A Google Cloud Project with the **Maps SDK for Android** enabled.
- A Firebase Project.

### Configuration

To protect sensitive keys, this project uses configuration files that are not tracked in version control.

#### 1. Google Maps API Key
1. Create a file named `local.defaults.properties` in the root directory (if it doesn't exist).
2. Add your Google Maps API key:
   ```properties
   MAPS_API_KEY=your_api_key_here
   ```

#### 2. Firebase Configuration
1. Register the app (`dev.bongballe.parkbuddy`) in your Firebase console.
2. Download the `google-services.json` file.
3. Place it in the `app/` directory:
   ```text
   app/google-services.json
   ```

### Building

1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Run the `app` configuration on a physical device or emulator.

## Architecture

The project follows a modular architecture to ensure scalability and maintainability:
- `app/`: Main application entry point and navigation.
- `feature/`: UI features like `map`, `reminders`, `onboarding`, and `settings`.
- `core/`: Shared logic including `data`, `network`, `bluetooth`, and `ui-theme`.

For development conventions and agent-specific instructions, please refer to [AGENTS.md](./AGENTS.md).
