package dev.bongballe.parkbuddy.data.io

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.buffer
import okio.source

class AndroidDataFileReader(
  private val context: Context,
  private val subdirectory: String,
  private val ioDispatcher: CoroutineDispatcher,
) : DataFileReader {

  private val dataDir: File
    get() = File(context.filesDir, subdirectory).also { it.mkdirs() }

  override suspend fun read(fileName: String): BufferedSource =
    withContext(ioDispatcher) {
      val diskFile = File(dataDir, fileName)
      check(diskFile.exists()) { "Data file missing: $subdirectory/$fileName" }
      diskFile.source().buffer()
    }

  override suspend fun write(fileName: String, content: String) {
    withContext(ioDispatcher) { File(dataDir, fileName).writeText(content) }
  }

  override suspend fun deleteAll() {
    withContext(ioDispatcher) {
      val dir = dataDir
      if (dir.exists()) {
        dir.listFiles()?.forEach { it.delete() }
      }
    }
  }
}
