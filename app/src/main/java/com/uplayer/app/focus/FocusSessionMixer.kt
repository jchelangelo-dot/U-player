package com.uplayer.app.focus

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.pow

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
 fun render(directory: File, settings: FocusMixSettings): File {
  val sources = FocusStem.entries.associateWith { RandomAccessFile(File(directory, it.fileName), "r") }
  val outputFile = File(directory, "session_mix.wav")
  val output = RandomAccessFile(outputFile, "rw").apply { setLength(0); write(ByteArray(44)) }
  val anySolo = listOf(settings.vocal, settings.drums, settings.bass, settings.guitar).any(FocusChannelSettings::solo)
  val totalFrames = ((sources.values.minOf { it.length() } - 44L) / 4L).coerceAtLeast(0L)
  sources.values.forEach { it.seek(44L) }
  val framesPerBlock = 16_384
  val sourceBuffers = FocusStem.entries.associateWith { ByteArray(framesPerBlock * 4) }
  var framesWritten = 0L
  try {
   while (framesWritten < totalFrames) {
    val frameCount = minOf(framesPerBlock.toLong(), totalFrames - framesWritten).toInt()
    FocusStem.entries.forEach { stem -> sources.getValue(stem).readFully(sourceBuffers.getValue(stem), 0, frameCount * 4) }
    val mixed = ByteArray(frameCount * 4)
    repeat(frameCount) { frame ->
     var left = 0f
     var right = 0f
     FocusStem.entries.forEach { stem ->
      val channel = settings.channel(stem)
      val audible = when {
       stem == FocusStem.RESIDUAL -> !anySolo
       anySolo -> channel.solo
       else -> !channel.muted
      }
      if (audible) {
       val gain = 10f.pow(channel.gainDb / 20f)
       val bytes = sourceBuffers.getValue(stem)
       val offset = frame * 4
       left += shortAt(bytes, offset) / 32768f * gain
       right += shortAt(bytes, offset + 2) / 32768f * gain
      }
     }
     putShort(mixed, frame * 4, (left.coerceIn(-1f, 1f) * 32767f).toInt())
     putShort(mixed, frame * 4 + 2, (right.coerceIn(-1f, 1f) * 32767f).toInt())
    }
    output.write(mixed)
    framesWritten += frameCount
   }
   output.seek(0L)
   output.write(wavHeader(framesWritten * 4L))
  } finally {
   sources.values.forEach { it.close() }
   output.close()
  }
  return outputFile
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
}
