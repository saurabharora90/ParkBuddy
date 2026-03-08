package dev.parkbuddy.feature.onboarding.setup

enum class StepStatus {
  PENDING,
  CURRENT,
  GRANTED,
  SKIPPED,
}

enum class SetupStep(
  val label: String,
  val description: String,
  val headline: String,
  val subtitle: String,
  val buttonLabel: String,
) {
  BLUETOOTH(
    label = "Bluetooth",
    description = "Detects when you park and leave",
    headline = "Detect your car automatically",
    subtitle =
      "When you disconnect from your car's Bluetooth, ParkBuddy knows you just parked and checks the rules for that spot. No tapping, no typing.",
    buttonLabel = "Allow Bluetooth",
  ),
  LOCATION(
    label = "Location",
    description = "Finds the parking rules for your spot",
    headline = "Know every rule on your block",
    subtitle =
      "ParkBuddy matches your exact location to SF's parking signs, meters, and street cleaning schedules. Your location stays on your device.",
    buttonLabel = "Allow Location",
  ),
  BACKGROUND_LOCATION(
    label = "Background access",
    description = "Works even when your phone is locked",
    headline = "Stay protected while you're away",
    subtitle =
      "Parking doesn't pause when you lock your phone. Background access lets ParkBuddy keep watching the clock so you don't have to.",
    buttonLabel = "Allow Background Access",
  ),
  NOTIFICATIONS(
    label = "Notifications",
    description = "Alerts you before you get a ticket",
    headline = "Get warned before it's too late",
    subtitle =
      "ParkBuddy sends a notification before your meter expires or street cleaning starts, giving you time to move your car.",
    buttonLabel = "Allow Notifications",
  ),
  PRECISE_TIMING(
    label = "Precise timing",
    description = "Reminders arrive at the exact right minute",
    headline = "Down to the minute",
    subtitle =
      "Your phone sometimes delays notifications to save battery. This ensures your move-your-car reminder arrives exactly on time, not five minutes late.",
    buttonLabel = "Open Settings",
  ),
  BATTERY(
    label = "Battery",
    description = "Keeps ParkBuddy running reliably",
    headline = "Don't let your phone kill the app",
    subtitle =
      "Some phones aggressively close background apps. This prevents Android from putting ParkBuddy to sleep while your car is parked.",
    buttonLabel = "Open Settings",
  ),
}
