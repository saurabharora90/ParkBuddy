package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.database.ReminderDao
import dev.bongballe.parkbuddy.database.ReminderSettingEntity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class RemindersRepositoryImpl(private val reminderDao: ReminderDao) : RemindersRepository {

  override fun getReminders(): Flow<List<Int>> {
    return reminderDao.getReminders().map { list -> list.map { it.minutesBefore } }
  }

  override suspend fun addReminder(minutesBefore: Int) {
    reminderDao.insertReminder(ReminderSettingEntity(minutesBefore = minutesBefore))
  }

  override suspend fun removeReminder(minutesBefore: Int) {
    reminderDao.deleteReminderByMinutes(minutesBefore)
  }
}
