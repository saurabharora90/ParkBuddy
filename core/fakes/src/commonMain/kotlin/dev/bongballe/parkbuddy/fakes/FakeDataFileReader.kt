package dev.bongballe.parkbuddy.fakes

import dev.bongballe.parkbuddy.data.io.DataFileReader
import okio.Buffer
import okio.BufferedSource

class FakeDataFileReader : DataFileReader {

  private val files = mutableMapOf<String, String>()

  override suspend fun read(fileName: String): BufferedSource {
    val content = files[fileName] ?: error("FakeDataFileReader: no file '$fileName'")
    return Buffer().writeUtf8(content)
  }

  override suspend fun write(fileName: String, content: String) {
    files[fileName] = content
  }

  override suspend fun deleteAll() {
    files.clear()
  }
}
