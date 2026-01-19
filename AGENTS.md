# Park Buddy - Agent Context

This document provides a high-level overview of the **Park Buddy** project to assist AI agents in understanding the codebase, architecture, and conventions.

## Architecture
The project follows a modularized architecture with a clear separation of concerns:

### Module Structure
*   **`:app`**: The main application entry point.
*   **`:feature`**: Contains functional features of the app.
    *   `Feature modules depend on `core` modules.`
*   **`:core`**: Shared components and infrastructure.
    *   **`:core:model`**: Domain entities and data classes shared across modules.
    *   **`:core:database`**: Local data persistence using **Room**.
    *   **`:core:network`**: Network communication using **Retrofit**.
    *   **`:core:data:api`** & **`:core:data:impl`**: Data repository layer (Repository Pattern) with interface/implementation separation.
    *   **`:core:theme`**: UI design system and Compose theming.
    *   **`:core:base`**: Common base classes and utilities.

## Tech Stack
*   **Language**: Kotlin
*   **UI Toolkit**: Jetpack Compose (Material3)
*   **Navigation**: AndroidX Navigation 3 (`androidx.navigation3`)
*   **Dependency Injection**: [Metro](https://github.com/zacsweers/metro) (Dependency Graph)
*   **Asynchronous Processing**: Kotlin Coroutines & Flow
*   **Networking**: Retrofit, OkHttp, Kotlinx Serialization
*   **Database**: Room
*   **Maps**: Maps Compose (Google Maps)

## Build & Tooling
*   **Build System**: Gradle (Kotlin DSL) with Version Catalogs (`libs.versions.toml`).
*   **Convention Plugins**: Uses `com.slack.foundry` for standardized build configuration and `dev.zacsweers.metro` for DI.
*   **Static Analysis**: Detekt, Spotless (formatting).
*   **Testing**:
    *   Unit: JUnit 4, Truth.
    *   Integration/UI: Robolectric, Roborazzi (Screenshot testing).

## Key Conventions
*   **Dependency Injection**: Use `@DependencyGraph` and Metro's graph generation instead of Hilt/Dagger.
    *   **ViewModels**: MUST be annotated with `@ContributesIntoMap(AppScope::class)`, `@ViewModelKey(MyViewModel::class)`, and `@Inject` to be properly discoverable by the ViewModel factory.
*   **Code Style**: Adhere to the formatting enforced by Spotless (likely ktfmt).
*   **Navigation**: Uses the newer Navigation 3 type-safe APIs.

## Development Tasks
*   **Build**: `./gradlew assembleDebug`
*   **Test**: `./gradlew test`
*   **Lint/Format**: `./gradlew spotlessApply` / `./gradlew detekt`

## Creating New Modules
When creating a new module, **strictly follow the patterns of existing modules** (e.g., see `:feature:map` or `:core:model`).

1.  **Use Foundry Convention Plugins**: Apply `alias(libs.plugins.foundry.base)` in the `plugins` block. This plugin encapsulates common build logic.
2.  **Minimal Configuration**:
    *   **Do NOT** include full `android { ... }` or `kotlin { ... }` configuration blocks (e.g., `compileSdk`, `kotlinOptions`). These are handled by Foundry.
    *   **DO** provide a minimal `android` block **only** to define the `namespace`.
3.  **Feature Configuration**: Use the `foundry { ... }` block to enable specific capabilities like Compose or Metro (DI).

**Example `build.gradle.kts`:**
```kotlin
plugins {
  alias(libs.plugins.android.library) // or android.application
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.foundry.base)
  // ... other plugins like serialization
}

android {
  namespace = "com.parkbuddy.feature.newfeature"
}

foundry {
  features {
    compose() // Enable Jetpack Compose
    metro()   // Enable Metro DI
  }
}

dependencies {
  // ...
}
```

## Notes for Agents
*   When adding new features, prefer creating a new module under `:feature` if the scope warrants it.
*   Always check `libs.versions.toml` for available library versions before adding new dependencies.
*   Respect the `api` vs `impl` separation in the `data` layer to maintain loose coupling.
