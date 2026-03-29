package dev.bongballe.parkbuddy.data.sf.tools

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.data.io.DataFileReader
import dev.bongballe.parkbuddy.data.sf.database.ParkBuddyDatabase
import dev.bongballe.parkbuddy.data.sf.network.FakeSfOpenDataApi
import dev.bongballe.parkbuddy.data.sf.network.FakeSfmtaArcGisApi
import dev.bongballe.parkbuddy.data.sf.repository.ParkingRepositoryImpl
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Generates the pre-built Room database from the JSON source files in `sf-data/` (module root).
 *
 * This is a build tool, not a regular test. Run it when source data or pipeline changes:
 * ```
 * ./gradlew :core:data:sf-impl:testAndroidHostTest --tests "*GeneratePrebuiltDb*"
 * ```
 *
 * Output: `src/commonMain/resources/sf-data/park_buddy_db`. KMP bundles this into the app
 * automatically on Android. For iOS, a Gradle task copies it to iosMain/resources/.
 */
@RunWith(RobolectricTestRunner::class)
class GeneratePrebuiltDb {

  @Test
  fun generateDb() = runTest {
    val projectDir = File(checkNotNull(System.getProperty("user.dir")))
    val sfDataDir = File(projectDir, "sf-data")
    check(sfDataDir.exists()) { "SF data directory not found: ${sfDataDir.absolutePath}" }

    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbFile = File(projectDir, "build/tmp/park_buddy_db")
    dbFile.parentFile?.mkdirs()
    dbFile.delete()

    val db =
      Room.databaseBuilder(context, ParkBuddyDatabase::class.java, dbFile.absolutePath)
        .allowMainThreadQueries()
        .build()

    val repository =
      ParkingRepositoryImpl(
        dao = db.parkingDao(),
        arcGis = FakeSfmtaArcGisApi(),
        socrata = FakeSfOpenDataApi(),
        fileReader = DirectoryFileReader(sfDataDir),
        json = Json { ignoreUnknownKeys = true },
        ioDispatcher = Dispatchers.Unconfined,
      )

    val success = repository.readFilesAndBuildDb()
    assertThat(success).isTrue()

    val spotCount = db.parkingDao().getSpotCount()
    assertThat(spotCount).isGreaterThan(0)
    println("Generated pre-built DB with $spotCount spots")

    db.close()

    val outputDir = File(projectDir, "src/commonMain/resources/sf-data")
    outputDir.mkdirs()
    val outputFile = File(outputDir, "park_buddy_db")
    dbFile.copyTo(outputFile, overwrite = true)
    println("Wrote pre-built DB to ${outputFile.absolutePath} (${outputFile.length() / 1024} KB)")
  }
}

/** Reads .json files directly from a directory on disk. */
private class DirectoryFileReader(private val dir: File) : DataFileReader {
  override suspend fun read(fileName: String): BufferedSource {
    val file = File(dir, fileName)
    check(file.exists()) { "Missing data file: ${file.absolutePath}" }
    return file.source().buffer()
  }

  override suspend fun write(fileName: String, content: String) {
    File(dir, fileName).writeText(content)
  }

  override suspend fun deleteAll() {
    dir.listFiles()?.filter { it.extension == "json" }?.forEach { it.delete() }
  }
}
