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
  val currentDirectory = trackId.takeIf(String::isNotBlank)?.let { File(sessionsRoot, it) }
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
  if (trackId.isBlank()) return
  File(sessionsRoot, trackId).filesMatching(::isRenderedMix).forEach(File::delete)
 }

 fun clearTrackAnalysis(trackId: String) {
  if (trackId.isBlank()) return
  val directory = File(sessionsRoot, trackId)
  if (directory.parentFile?.canonicalFile == sessionsRoot.canonicalFile) directory.deleteRecursively()
 }

 private fun File.filesMatching(predicate: (File) -> Boolean): List<File> =
  listFiles()?.filter { it.isFile && predicate(it) }.orEmpty()

 private fun isRenderedMix(file: File): Boolean =
  file.name == "session_mix.wav" || file.name.startsWith("session_mix_")

 private fun directorySize(directory: File): Long =
  directory.listFiles()?.sumOf { if (it.isDirectory) directorySize(it) else it.length() } ?: 0L

 private companion object {
  const val WAV_HEADER_BYTES = 44L
  val STEM_FILE_NAMES = FocusStem.entries.map(FocusStem::fileName).toSet()
 }
}
