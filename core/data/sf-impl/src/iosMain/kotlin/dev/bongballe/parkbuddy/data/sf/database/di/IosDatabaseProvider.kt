package dev.bongballe.parkbuddy.data.sf.database.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.data.sf.database.ParkBuddyDatabase
import dev.bongballe.parkbuddy.data.sf.database.ParkingDao
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineDispatcher
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

private const val DB_NAME = "park_buddy_db"

@ContributesTo(AppScope::class)
interface IosDatabaseProvider {

  @Provides
  fun provideDatabase(
    @WithDispatcherType(DispatcherType.IO) ioDispatcher: CoroutineDispatcher
  ): ParkBuddyDatabase {
    val dbPath = NSHomeDirectory() + "/Documents/$DB_NAME"
    copyPrebuiltDbIfNeeded(dbPath)
    return Room.databaseBuilder<ParkBuddyDatabase>(name = dbPath)
      .setDriver(BundledSQLiteDriver())
      .setQueryCoroutineContext(ioDispatcher)
      .fallbackToDestructiveMigration(true)
      .build()
  }

  @Provides
  fun provideParkingDao(database: ParkBuddyDatabase): ParkingDao {
    return database.parkingDao()
  }
}

private fun copyPrebuiltDbIfNeeded(dbPath: String) {
  val fileManager = NSFileManager.defaultManager
  val versionPath = "$dbPath.version"
  val expectedVersion = ParkBuddyDatabase.DB_VERSION.toString()

  if (fileManager.fileExistsAtPath(dbPath) && fileManager.fileExistsAtPath(versionPath)) {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val installed =
      NSString.stringWithContentsOfFile(versionPath, NSUTF8StringEncoding, null) as? String
    if (installed?.trim() == expectedVersion) return
    fileManager.removeItemAtPath(dbPath, error = null)
    fileManager.removeItemAtPath("$dbPath-shm", error = null)
    fileManager.removeItemAtPath("$dbPath-wal", error = null)
  }

  val bundledPath = NSBundle.mainBundle.pathForResource(DB_NAME, ofType = null) ?: return
  fileManager.copyItemAtPath(bundledPath, toPath = dbPath, error = null)

  @Suppress("CAST_NEVER_SUCCEEDS")
  (expectedVersion as NSString).writeToFile(versionPath, atomically = true)
}
