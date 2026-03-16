package dev.bongballe.parkbuddy.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosAlarmScheduler : AlarmScheduler {

  override fun setAlarm(
    index: Int,
    triggerAt: Instant,
    spotName: String,
    spotId: String,
    message: String,
  ) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val content =
      UNMutableNotificationContent().apply {
        setTitle(spotName)
        setBody(message)
        setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
      }

    val local = triggerAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val dateComponents =
      platform.Foundation.NSDateComponents().apply {
        setYear(local.year.toLong())
        setMonth(local.monthNumber.toLong())
        setDay(local.dayOfMonth.toLong())
        setHour(local.hour.toLong())
        setMinute(local.minute.toLong())
        setSecond(local.second.toLong())
      }

    val trigger =
      UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
        dateComponents,
        repeats = false,
      )
    val request =
      UNNotificationRequest.requestWithIdentifier("parkbuddy_alarm_$index", content, trigger)

    center.addNotificationRequest(request, withCompletionHandler = null)
  }

  override fun cancelAll() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val ids = (0 until IOS_MAX_REMINDERS).map { "parkbuddy_alarm_$it" }
    center.removePendingNotificationRequestsWithIdentifiers(ids)
  }

  companion object {
    const val IOS_MAX_REMINDERS = 60
  }
}
