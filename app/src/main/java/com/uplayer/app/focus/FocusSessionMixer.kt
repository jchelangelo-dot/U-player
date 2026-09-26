package com.uplayer.app.focus

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class FocusChannelSettings(
 val gainDb: Float = 0f,
 val muted: Boolean = false,
 val solo: Boolean = false
)

data class FocusMixSettings(
 val vocal: FocusChannelSettings = FocusChannelSettings(),
 val drums: FocusChannelSettings = FocusChannelSettings(),
 val bass: FocusChannelSettings = FocusChannelSettings(),
 val guitar: FocusChannelSettings = FocusChannelSettings()
) {
 fun channel(stem: FocusStem) = when (stem) {
  FocusStem.VOCAL -> vocal
  FocusStem.DRUMS -> drums
  FocusStem.BASS -> bass
  FocusStem.GUITAR -> guitar
  FocusStem.RESIDUAL -> FocusChannelSettings()
 }
}

object FocusSessionMixer {
 fun cachedMix(directory: File, settings: FocusMixSettings): File? =
  mixFile(directory, settings).takeIf { cached ->
   val expectedLength = FocusStem.entries.minOfOrNull { File(directory, it.fileName).length() } ?: 0L
   expectedLength > WAV_HEADER_BYTES && cached.isFile && cached.length() == expectedLength
  }

 suspend fun render(directory: File, settings: FocusMixSettings): File {
  cachedMix(directory, settings)?.let {
   it.setLastModified(System.currentTimeMillis())
   return it
  }

  val stemFiles = FocusStem.entries.associateWith { File(directory, it.fileName) }
  val anySolo = listOf(settings.vocal, settings.drums, settings.bass, settings.guitar)
   .any(FocusChannelSettings::solo)
  val activeStems = FocusStem.entries.filter { stem ->
   val channel = settings.channel(stem)
   when {
    stem == FocusStem.RESIDUAL -> !anySolo
    anySolo -> channel.solo
    else -> !channel.muted
   }
  }
  val sources = activeStems.map { stem ->
   MixSource(
    file = RandomAccessFile(stemFiles.getValue(stem), "r"),
    gain = if (stem == FocusStem.RESIDUAL) 1f else 10f.pow(settings.channel(stem).gainDb / 20f),
    buffer = ByteArray(FRAMES_PER_BLOCK * BYTES_PER_FRAME)
   )
  }
  val version = System.nanoTime()
  val temporaryFile = File(directory, "session_mix_$version.tmp")
  val outputFile = mixFile(directory, settings)
  val output = RandomAccessFile(temporaryFile, "rw").apply { setLength(0); write(ByteArray(44)) }
  val totalFrames = ((stemFiles.values.minOf { it.length() } - WAV_HEADER_BYTES) / BYTES_PER_FRAME)
   .coerceAtLeast(0L)
  sources.forEach { it.file.seek(WAV_HEADER_BYTES) }
  var framesWritten = 0L
  var completed = false
  var limiterGain = 1f
  val release = exp(-1.0 / (44_100.0 * 0.22)).toFloat()
  try {
   while (framesWritten < totalFrames) {
    currentCoroutineContext().ensureActive()
    val frameCount = minOf(FRAMES_PER_BLOCK.toLong(), totalFrames - framesWritten).toInt()
    sources.forEach { it.file.readFully(it.buffer, 0, frameCount * BYTES_PER_FRAME) }
    val mixed = ByteArray(frameCount * BYTES_PER_FRAME)
    repeat(frameCount) { frame ->
     var left = 0f
     var right = 0f
     val offset = frame * BYTES_PER_FRAME
     sources.forEach { source ->
      left += shortAt(source.buffer, offset) * source.gain
      right += shortAt(source.buffer, offset + 2) * source.gain
     }
     val peak = maxOf(abs(left), abs(right))
     val targetGain = if (peak > MIX_CEILING) MIX_CEILING / peak else 1f
     limiterGain = if (targetGain < limiterGain) targetGain else targetGain + release * (limiterGain - targetGain)
     putShort(mixed, offset, (left * limiterGain).coerceIn(-MIX_CEILING, MIX_CEILING).toInt())
     putShort(mixed, offset + 2, (right * limiterGain).coerceIn(-MIX_CEILING, MIX_CEILING).toInt())
    }
    output.write(mixed)
    framesWritten += frameCount
   }
   output.seek(0L)
   output.write(wavHeader(framesWritten * 4L))
   completed = true
  } finally {
   sources.forEach { it.file.close() }
   output.close()
   if (!completed) temporaryFile.delete()
  }
  if (!temporaryFile.renameTo(outputFile)) {
   temporaryFile.copyTo(outputFile, overwrite = true)
   temporaryFile.delete()
  }
  cleanupOldMixes(directory, outputFile)
  return outputFile
 }

 private data class MixSource(
  val file: RandomAccessFile,
  val gain: Float,
  val buffer: ByteArray
 )

 private fun mixFile(directory: File, settings: FocusMixSettings) =
  File(directory, "session_mix_v2_${Integer.toUnsignedString(settings.hashCode(), 36)}.wav")

 private fun cleanupOldMixes(directory: File, current: File) {
  directory.listFiles()?.filter { file ->
   file != current && (file.name == "session_mix.wav" ||
    (file.name.startsWith("session_mix_") && file.extension == "wav"))
  }?.sortedByDescending(File::lastModified)?.drop(1)?.forEach(File::delete)
 }

 private fun shortAt(bytes: ByteArray, offset: Int): Short =
  ((bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)).toShort()

 private fun putShort(bytes: ByteArray, offset: Int, value: Int) {
  bytes[offset] = value.toByte()
  bytes[offset + 1] = (value ushr 8).toByte()
 }

 private fun wavHeader(dataSize: Long): ByteArray {
  val header = ByteArray(44)
  fun text(offset: Int, value: String) = value.forEachIndexed { index, char -> header[offset + index] = char.code.toByte() }
  fun int(offset: Int, value: Long, count: Int) = repeat(count) { header[offset + it] = (value ushr (8 * it)).toByte() }
  text(0, "RIFF"); int(4, 36 + dataSize, 4); text(8, "WAVE"); text(12, "fmt ")
  int(16, 16, 4); int(20, 1, 2); int(22, 2, 2); int(24, 44_100, 4)
  int(28, 176_400, 4); int(32, 4, 2); int(34, 16, 2); text(36, "data"); int(40, dataSize, 4)
  return header
 }

 private const val WAV_HEADER_BYTES = 44L
 private const val BYTES_PER_FRAME = 4
 private const val FRAMES_PER_BLOCK = 65_536
 private const val MIX_CEILING = 31_128f
}
