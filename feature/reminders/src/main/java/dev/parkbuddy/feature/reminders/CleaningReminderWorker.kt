package dev.parkbuddy.feature.reminders

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CleaningReminderWorker(context: Context, workerParams: WorkerParameters) :
  CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result {
    // In a real app, inject this properly.
    // val repository = (applicationContext as
    // ParkBuddyApplication).dataGraph.streetCleaningRepository
    // Since we don't have easy access to the graph here without casting, let's skip the actual DB
    // call for the worker logic
    // and just demonstrate the notification part.

    // Mock logic: Send a notification
    sendNotification("Street Cleaning Reminder", "Check your watchlist for upcoming cleaning!")

    return Result.success()
  }

  private fun sendNotification(title: String, message: String) {
    val notificationManager =
      applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "street_cleaning_reminders"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          channelId,
          "Street Cleaning Reminders",
          NotificationManager.IMPORTANCE_DEFAULT,
        )
      notificationManager.createNotificationChannel(channel)
    }

    val notification =
      NotificationCompat.Builder(applicationContext, channelId)
        .setSmallIcon(R.drawable.ic_dialog_info) // Use a proper icon
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    notificationManager.notify(1, notification)
  }
}
