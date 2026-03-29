package dev.bongballe.parkbuddy.data.io

import android.content.Context
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.GzipSource
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
      if (diskFile.exists()) {
        return@withContext GzipSource(diskFile.source()).buffer()
      }

      val stream =
        javaClass.classLoader!!.getResourceAsStream("$subdirectory/$fileName")
          ?: error("Bundled asset missing: $subdirectory/$fileName")
      GzipSource(stream.source()).buffer()
    }

  override suspend fun write(fileName: String, content: String) {
    withContext(ioDispatcher) {
      GZIPOutputStream(File(dataDir, fileName).outputStream()).bufferedWriter().use {
        it.write(content)
      }
    }
  }
}
