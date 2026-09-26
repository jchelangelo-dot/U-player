package com.uplayer.app.focus

import android.content.Context
import java.io.File

data class FocusCacheInfo(
 val currentStemBytes: Long = 0L,
 val currentMixBytes: Long = 0L,
 val allSessionBytes: Long = 0L,
 val analyzedTrackCount: Int = 0,
 val modelBytes: Long = 0L
)

class FocusCacheManager(private val context: Context) {
 private val sessionsRoot get() = File(context.filesDir, "focus_sessions")
 private val modelRoot get() = File(context.noBackupFilesDir, "focus_models")

 fun snapshot(trackId: String): FocusCacheInfo {
  val currentDirectory = safeSessionDirectory(trackId)
  val sessionDirectories = sessionsRoot.listFiles()?.filter(File::isDirectory).orEmpty()
  return FocusCacheInfo(
   currentStemBytes = currentDirectory?.filesMatching { it.name in STEM_FILE_NAMES }?.sumOf(File::length) ?: 0L,
   currentMixBytes = currentDirectory?.filesMatching(::isRenderedMix)?.sumOf(File::length) ?: 0L,
   allSessionBytes = sessionDirectories.sumOf(::directorySize),
   analyzedTrackCount = sessionDirectories.count { directory ->
    STEM_FILE_NAMES.all { File(directory, it).let { file -> file.isFile && file.length() > WAV_HEADER_BYTES } }
   },
   modelBytes = directorySize(modelRoot)
  )
 }

 fun clearRenderedMixes(trackId: String) {
  safeSessionDirectory(trackId)?.filesMatching(::isRenderedMix)?.forEach(File::delete)
 }

 fun clearTrackAnalysis(trackId: String) {
  safeSessionDirectory(trackId)?.deleteRecursively()
 }

 fun clearAllAnalyses() {
  sessionsRoot.listFiles()?.filter(File::isDirectory)?.forEach(File::deleteRecursively)
 }

 /** Keeps recent analyses while preventing stems from growing without a storage bound. */
 fun markUsedAndTrim(trackId: String) {
  val current = safeSessionDirectory(trackId) ?: return
  if (current.isDirectory) current.setLastModified(System.currentTimeMillis())
  val directories = sessionsRoot.listFiles()?.filter(File::isDirectory).orEmpty()
  val entries = directories.map { SessionCacheEntry(it.name, directorySize(it), it.lastModified()) }
  val evictions = sessionsToEvict(entries, current.name, MAX_SESSION_COUNT, MAX_SESSION_BYTES)
  directories.filter { it.name in evictions }.forEach { candidate ->
   candidate.deleteRecursively()
  }
 }

 private fun safeSessionDirectory(trackId: String): File? = runCatching {
  if (trackId.isBlank()) return null
  val root = sessionsRoot.canonicalFile
  File(root, trackId).canonicalFile.takeIf { it.parentFile == root }
 }.getOrNull()

 private fun File.filesMatching(predicate: (File) -> Boolean): List<File> =
  listFiles()?.filter { it.isFile && predicate(it) }.orEmpty()

 private fun isRenderedMix(file: File): Boolean =
  file.name == "session_mix.wav" || file.name.startsWith("session_mix_")

 private fun directorySize(directory: File): Long =
  directory.listFiles()?.sumOf { if (it.isDirectory) directorySize(it) else it.length() } ?: 0L

 private companion object {
  const val WAV_HEADER_BYTES = 44L
  const val MAX_SESSION_COUNT = 8
  const val MAX_SESSION_BYTES = 1_500L * 1_024L * 1_024L
  val STEM_FILE_NAMES = FocusStem.entries.map(FocusStem::fileName).toSet()
 }
}

internal data class SessionCacheEntry(val id: String, val bytes: Long, val lastUsed: Long)

internal fun sessionsToEvict(
 entries: List<SessionCacheEntry>,
 currentId: String,
 maxCount: Int,
 maxBytes: Long
): Set<String> {
 var remainingBytes = entries.sumOf(SessionCacheEntry::bytes)
 var remainingCount = entries.size
 return buildSet {
  entries.sortedBy(SessionCacheEntry::lastUsed).forEach { entry ->
   if (entry.id != currentId && (remainingCount > maxCount || remainingBytes > maxBytes)) {
    add(entry.id)
    remainingBytes -= entry.bytes
    remainingCount--
   }
  }
 }
}
