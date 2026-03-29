package dev.bongballe.parkbuddy.data.io

import okio.BufferedSource

/**
 * Reads and writes gzip-compressed data files for the parking data pipeline.
 *
 * Each city implementation stores its raw API data as `.json.gz` files. On first launch, the
 * bundled files (shipped with the app) are used. Background workers download fresh data to the
 * writable data directory, which takes priority on subsequent reads.
 */
interface DataFileReader {

  /**
   * Opens [fileName] and returns a decompressed [BufferedSource] for streaming reads. Checks
   * writable storage first, falls back to bundled assets.
   *
   * Throws if the file is missing: bundled assets must always be present (a missing file is a
   * build/packaging bug).
   *
   * The caller is responsible for closing the returned source.
   */
  suspend fun read(fileName: String): BufferedSource

  /** Gzip-compresses [content] and writes it to writable storage for [fileName]. */
  suspend fun write(fileName: String, content: String)
}
