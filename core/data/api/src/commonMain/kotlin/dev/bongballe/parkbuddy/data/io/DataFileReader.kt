package dev.bongballe.parkbuddy.data.io

import okio.BufferedSource

/**
 * Reads and writes JSON data files for the parking data pipeline.
 *
 * Used by background refresh workers that download fresh API data to the writable data directory.
 * First-launch data comes from a pre-built Room database, not from these files.
 */
interface DataFileReader {

  /**
   * Opens [fileName] from writable storage and returns a [BufferedSource] for streaming reads.
   *
   * Throws if the file does not exist in writable storage.
   *
   * The caller is responsible for closing the returned source.
   */
  suspend fun read(fileName: String): BufferedSource

  /** Writes [content] to writable storage for [fileName]. */
  suspend fun write(fileName: String, content: String)

  /** Deletes all data files from writable storage. */
  suspend fun deleteAll()
}
