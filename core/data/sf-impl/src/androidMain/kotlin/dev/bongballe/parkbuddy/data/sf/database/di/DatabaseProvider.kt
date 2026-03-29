package dev.bongballe.parkbuddy.data.sf.database.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.data.sf.database.ParkBuddyDatabase
import dev.bongballe.parkbuddy.data.sf.database.ParkingDao
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

private const val DB_NAME = "park_buddy_db"
private const val BUNDLED_ASSET = "sf-data/park_buddy_db"

@ContributesTo(AppScope::class)
interface DatabaseProvider {

  @Provides
  fun provideDatabase(
    context: Context,
    @WithDispatcherType(DispatcherType.IO) ioDispatcher: CoroutineDispatcher,
  ): ParkBuddyDatabase {
    copyPrebuiltDbIfNeeded(context)
    return Room.databaseBuilder(context, ParkBuddyDatabase::class.java, DB_NAME)
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

/**
 * Copies the bundled pre-built database if:
 * - No database exists yet (first install), or
 * - The schema version has changed (app update with bumped DB version).
 *
 * On version change, the old DB is deleted so Room doesn't attempt a migration against stale data.
 * This replicates the behavior of Room's `createFromAsset` + `fallbackToDestructiveMigration`,
 * which is unavailable when using `BundledSQLiteDriver`.
 */
private fun copyPrebuiltDbIfNeeded(context: Context) {
  val dbFile = context.getDatabasePath(DB_NAME)
  val versionFile = File(dbFile.parentFile, "${DB_NAME}.version")
  val expectedVersion = ParkBuddyDatabase.DB_VERSION

  if (dbFile.exists() && versionFile.exists()) {
    val installedVersion = versionFile.readText().trim().toIntOrNull()
    if (installedVersion == expectedVersion) return
    // Version mismatch: delete the old DB so we can re-copy the new one.
    dbFile.delete()
    File(dbFile.path + "-shm").delete()
    File(dbFile.path + "-wal").delete()
    File(dbFile.path + "-journal").delete()
  }

  val stream =
    DatabaseProvider::class.java.classLoader!!.getResourceAsStream(BUNDLED_ASSET) ?: return
  dbFile.parentFile?.mkdirs()
  stream.use { input -> dbFile.outputStream().use { output -> input.copyTo(output) } }
  versionFile.writeText(expectedVersion.toString())
}
