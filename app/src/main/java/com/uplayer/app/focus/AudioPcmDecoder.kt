package com.uplayer.app.focus

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class AudioProfile(
 val stereoRatio: Float,
 val bassRatio: Float,
 val airRatio: Float,
 val transientRatio: Float,
 val dynamicRange: Float
)

internal data class DecodedPcmFile(
 val file: File,
 val sampleRate: Int,
 val frameCount: Long,
 val profile: AudioProfile
) : AutoCloseable {
 val targetFrameCount: Int
  get() = (frameCount * TARGET_SAMPLE_RATE / sampleRate).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

 fun readModelInput(startFrame: Int, frameCount: Int, destination: FloatArray) {
  destination.fill(0f)
  if (startFrame >= targetFrameCount || frameCount <= 0) return
  val actualFrames = minOf(frameCount, targetFrameCount - startFrame)
  val firstSource = floor(startFrame.toDouble() * sampleRate / TARGET_SAMPLE_RATE).toLong()
  val lastTarget = startFrame.toLong() + actualFrames
  val lastSource = ceil(lastTarget.toDouble() * sampleRate / TARGET_SAMPLE_RATE).toLong() + 1L
  val sourceFrames = (lastSource - firstSource).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  val bytes = ByteArray(sourceFrames * BYTES_PER_FRAME)
  RandomAccessFile(file, "r").use { input ->
   input.seek(firstSource * BYTES_PER_FRAME)
   var offset = 0
   while (offset < bytes.size) {
    val count = input.read(bytes, offset, bytes.size - offset)
    if (count < 0) break
    offset += count
   }
  }
  repeat(actualFrames) { index ->
   val sourcePosition = (startFrame + index).toDouble() * sampleRate / TARGET_SAMPLE_RATE - firstSource
   val base = floor(sourcePosition).toInt().coerceIn(0, sourceFrames - 1)
   val next = (base + 1).coerceAtMost(sourceFrames - 1)
   val fraction = (sourcePosition - base).toFloat()
   destination[index] = sample(bytes, base, 0) * (1f - fraction) + sample(bytes, next, 0) * fraction
   destination[MODEL_SEGMENT_FRAMES + index] =
    sample(bytes, base, 1) * (1f - fraction) + sample(bytes, next, 1) * fraction
  }
 }

 override fun close() {
  file.delete()
 }

 private fun sample(bytes: ByteArray, frame: Int, channel: Int): Float {
  val offset = frame * BYTES_PER_FRAME + channel * 2
  if (offset + 1 >= bytes.size) return 0f
  val value = ((bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)).toShort()
  return value / 32768f
 }

 private companion object {
  const val TARGET_SAMPLE_RATE = 44_100L
  const val BYTES_PER_FRAME = 4
  const val MODEL_SEGMENT_FRAMES = 343_980
 }
}

internal object AudioPcmDecoder {
 suspend fun decode(
  context: Context,
  uri: Uri,
  outputFile: File,
  onProgress: (Float) -> Unit = {}
 ): DecodedPcmFile {
  outputFile.parentFile?.mkdirs()
  val extractor = MediaExtractor()
  extractor.setDataSource(context, uri, null)
  val trackIndex = (0 until extractor.trackCount).firstOrNull {
   extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
  } ?: error("재생 가능한 오디오 트랙이 없습니다")
  extractor.selectTrack(trackIndex)
  val sourceFormat = extractor.getTrackFormat(trackIndex)
  val mime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: error("오디오 형식을 확인할 수 없습니다")
  val durationUs = if (sourceFormat.containsKey(MediaFormat.KEY_DURATION)) {
   sourceFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
  } else 1L
  val decoder = MediaCodec.createDecoderByType(mime)
  decoder.configure(sourceFormat, null, null, 0)
  decoder.start()
  val stats = ProfileAccumulator()
  var channels = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
  var sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
  var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
  var framesWritten = 0L
  var inputEnded = false
  var outputEnded = false
  var lastReportedProgress = -1f
  val info = MediaCodec.BufferInfo()
  stats.configure(sampleRate)
  try {
   outputFile.outputStream().buffered().use { output ->
    while (!outputEnded) {
     currentCoroutineContext().ensureActive()
     if (!inputEnded) {
      val inputIndex = decoder.dequeueInputBuffer(10_000)
      if (inputIndex >= 0) {
       val input = decoder.getInputBuffer(inputIndex)!!
       val size = extractor.readSampleData(input, 0)
       if (size < 0) {
        decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        inputEnded = true
       } else {
        decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
        val progress = (extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f)
        if (progress - lastReportedProgress >= 0.01f) {
         lastReportedProgress = progress
         onProgress(progress)
        }
        extractor.advance()
       }
      }
     }
     when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
      MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
       val format = decoder.outputFormat
       channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
       sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
       pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
       } else AudioFormat.ENCODING_PCM_16BIT
       stats.configure(sampleRate)
      }
      else -> if (outputIndex >= 0) {
       decoder.getOutputBuffer(outputIndex)?.let { buffer ->
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
        val frameCount = buffer.remaining() / (bytesPerSample * channels.coerceAtLeast(1))
        val pcm = ByteArray(frameCount * 4)
        repeat(frameCount) { frame ->
         var left = 0f
         var right = 0f
         repeat(channels.coerceAtLeast(1)) { channel ->
          val value = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f
          if (channel == 0) left = value
          if (channel == 1) right = value
         }
         if (channels == 1) right = left
         val offset = frame * 4
         putPcm16(pcm, offset, left)
         putPcm16(pcm, offset + 2, right)
         stats.add(left, right)
        }
        output.write(pcm)
        framesWritten += frameCount
       }
       outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
       decoder.releaseOutputBuffer(outputIndex, false)
      }
     }
    }
   }
  } catch (error: Throwable) {
   outputFile.delete()
   throw error
  } finally {
   runCatching { decoder.stop() }
   decoder.release()
   extractor.release()
  }
  return DecodedPcmFile(outputFile, sampleRate, framesWritten, stats.finish())
 }

 private fun putPcm16(bytes: ByteArray, offset: Int, value: Float) {
  val sample = (value.coerceIn(-1f, 1f) * 32767f).toInt()
  bytes[offset] = sample.toByte()
  bytes[offset + 1] = (sample ushr 8).toByte()
 }
}

private class ProfileAccumulator {
 private var sampleRate = 44_100
 private var lowState = 0.0
 private var highState = 0.0
 private var previousMid = 0.0
 private var frames = 0L
 private var totalEnergy = 0.0
 private var sideEnergy = 0.0
 private var lowEnergy = 0.0
 private var highEnergy = 0.0
 private var transientEnergy = 0.0
 private var peak = 0.0

 fun configure(value: Int) { sampleRate = value.coerceAtLeast(1) }

 fun add(left: Float, right: Float) {
  val mid = (left + right) * 0.5
  val side = (left - right) * 0.5
  lowState += alpha(180.0) * (mid - lowState)
  highState += alpha(6_000.0) * (mid - highState)
  val high = mid - highState
  val delta = mid - previousMid
  previousMid = mid
  totalEnergy += mid * mid
  sideEnergy += side * side
  lowEnergy += lowState * lowState
  highEnergy += high * high
  transientEnergy += delta * delta
  peak = maxOf(peak, kotlin.math.abs(mid))
  frames++
 }

 fun finish(): AudioProfile {
  val safeTotal = totalEnergy.coerceAtLeast(1e-9)
  val rms = sqrt(safeTotal / frames.coerceAtLeast(1))
  return AudioProfile(
   stereoRatio = sqrt(sideEnergy / safeTotal).toFloat().coerceIn(0f, 1f),
   bassRatio = sqrt(lowEnergy / safeTotal).toFloat().coerceIn(0f, 1f),
   airRatio = sqrt(highEnergy / safeTotal).toFloat().coerceIn(0f, 1f),
   transientRatio = sqrt(transientEnergy / safeTotal).toFloat().coerceIn(0f, 1f),
   dynamicRange = ((peak - rms) / peak.coerceAtLeast(1e-6)).toFloat().coerceIn(0f, 1f)
  )
 }

 private fun alpha(frequency: Double) = 1.0 - exp(-2.0 * PI * frequency / sampleRate)
}
