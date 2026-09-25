package com.uplayer.app.focus

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class FocusStem(val fileName: String) {
 VOCAL("vocals.wav"), DRUMS("drums.wav"), BASS("bass.wav"), GUITAR("guitar.wav"), RESIDUAL("residual.wav")
}

data class FocusAnalysisProgress(val fraction: Float, val message: String)

class FocusSessionAnalyzer(private val context: Context) {
 private val modelFile = File(context.noBackupFilesDir, "focus_models/htdemucs_6s_fp16weights.onnx")

 fun sessionDirectory(trackId: String) = File(context.filesDir, "focus_sessions/$trackId")

 fun isReady(trackId: String): Boolean = FocusStem.entries.all { File(sessionDirectory(trackId), it.fileName).isFile }

 suspend fun analyze(trackId: String, uri: Uri, onProgress: (FocusAnalysisProgress) -> Unit) {
  ensureModel(onProgress)
  onProgress(FocusAnalysisProgress(0.16f, "음원을 PCM으로 준비하는 중"))
  val audio = decodeAudio(uri)
  val directory = sessionDirectory(trackId).apply { mkdirs() }
  val writers = FocusStem.entries.associateWith { WavStreamWriter(File(directory, it.fileName), SAMPLE_RATE) }
  try {
   runModel(audio, writers, onProgress)
  } catch (error: Throwable) {
   writers.values.forEach { it.abort() }
   directory.deleteRecursively()
   throw error
  } finally {
   writers.values.forEach { it.close() }
  }
  onProgress(FocusAnalysisProgress(1f, "Focus Session 준비 완료"))
 }

 private suspend fun ensureModel(onProgress: (FocusAnalysisProgress) -> Unit) {
  if (modelFile.isFile && modelFile.sha256() == MODEL_SHA256) return
  modelFile.delete()
  modelFile.parentFile?.mkdirs()
  val temporary = File(modelFile.parentFile, "${modelFile.name}.download")
  val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
  connection.connectTimeout = 20_000
  connection.readTimeout = 60_000
  connection.instanceFollowRedirects = true
  connection.connect()
  if (connection.responseCode !in 200..299) error("모델 다운로드 실패 (${connection.responseCode})")
  val total = connection.contentLengthLong.coerceAtLeast(1L)
  connection.inputStream.use { input ->
   temporary.outputStream().buffered().use { output ->
    val buffer = ByteArray(256 * 1024)
    var copied = 0L
    while (true) {
     currentCoroutineContext().ensureActive()
     val count = input.read(buffer)
     if (count < 0) break
     output.write(buffer, 0, count)
     copied += count
     onProgress(FocusAnalysisProgress((copied.toFloat() / total * 0.14f).coerceIn(0f, 0.14f), "분리 모델 다운로드 ${(copied * 100 / total).coerceIn(0, 100)}%"))
    }
   }
  }
  if (temporary.sha256() != MODEL_SHA256) {
   temporary.delete()
   error("분리 모델 무결성 검사에 실패했습니다")
  }
  if (!temporary.renameTo(modelFile)) {
   temporary.copyTo(modelFile, overwrite = true)
   temporary.delete()
  }
 }

 private suspend fun runModel(
  audio: StereoPcm,
  writers: Map<FocusStem, WavStreamWriter>,
  onProgress: (FocusAnalysisProgress) -> Unit
 ) {
  val environment = OrtEnvironment.getEnvironment()
  val options = OrtSession.SessionOptions().apply { setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT) }
  environment.createSession(modelFile.absolutePath, options).use { session ->
   val totalSamples = audio.left.size
   val chunks = max(1, ceil(totalSamples.toDouble() / STRIDE).toInt())
   val input = FloatArray(2 * SEGMENT_SAMPLES)
   repeat(chunks) { chunkIndex ->
    currentCoroutineContext().ensureActive()
    val start = chunkIndex * STRIDE
    val chunkLength = minOf(SEGMENT_SAMPLES, totalSamples - start)
    input.fill(0f)
    audio.left.copyInto(input, 0, start, start + chunkLength)
    audio.right.copyInto(input, SEGMENT_SAMPLES, start, start + chunkLength)
    OnnxTensor.createTensor(environment, java.nio.FloatBuffer.wrap(input), longArrayOf(1, 2, SEGMENT_SAMPLES.toLong())).use { tensor ->
     session.run(mapOf("mix" to tensor)).use { result ->
      val output = (result[0] as OnnxTensor).floatBuffer
      fun stem(row: Int): Pair<FloatArray, FloatArray> {
       val left = FloatArray(chunkLength)
       val right = FloatArray(chunkLength)
       val base = row * 2 * SEGMENT_SAMPLES
       for (sample in 0 until chunkLength) {
        left[sample] = output.get(base + sample)
        right[sample] = output.get(base + SEGMENT_SAMPLES + sample)
       }
       return left to right
      }
      val drums = stem(0)
      val bass = stem(1)
      val other = stem(2)
      val vocals = stem(3)
      val guitar = stem(4)
      val piano = stem(5)
      val residualLeft = FloatArray(chunkLength) { other.first[it] + piano.first[it] }
      val residualRight = FloatArray(chunkLength) { other.second[it] + piano.second[it] }
      writers.getValue(FocusStem.DRUMS).append(drums.first, drums.second, chunkIndex, chunks)
      writers.getValue(FocusStem.BASS).append(bass.first, bass.second, chunkIndex, chunks)
      writers.getValue(FocusStem.VOCAL).append(vocals.first, vocals.second, chunkIndex, chunks)
      writers.getValue(FocusStem.GUITAR).append(guitar.first, guitar.second, chunkIndex, chunks)
      writers.getValue(FocusStem.RESIDUAL).append(residualLeft, residualRight, chunkIndex, chunks)
     }
    }
    val progress = 0.20f + ((chunkIndex + 1f) / chunks) * 0.78f
    onProgress(FocusAnalysisProgress(progress, "음원 분리 ${chunkIndex + 1}/$chunks"))
   }
  }
 }

 private fun decodeAudio(uri: Uri): StereoPcm {
  val extractor = MediaExtractor()
  extractor.setDataSource(context, uri, null)
  val trackIndex = (0 until extractor.trackCount).firstOrNull {
   extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
  } ?: error("재생 가능한 오디오 트랙이 없습니다")
  extractor.selectTrack(trackIndex)
  val format = extractor.getTrackFormat(trackIndex)
  val mime = format.getString(MediaFormat.KEY_MIME) ?: error("오디오 형식을 확인할 수 없습니다")
  val decoder = MediaCodec.createDecoderByType(mime)
  decoder.configure(format, null, null, 0)
  decoder.start()
  val samples = FloatCollector()
  var inputEnded = false
  var outputEnded = false
  var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
  var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
  var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
  val info = MediaCodec.BufferInfo()
  try {
   while (!outputEnded) {
    if (!inputEnded) {
     val inputIndex = decoder.dequeueInputBuffer(10_000)
     if (inputIndex >= 0) {
      val buffer = decoder.getInputBuffer(inputIndex)!!
      val size = extractor.readSampleData(buffer, 0)
      if (size < 0) {
       decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
       inputEnded = true
      } else {
       decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
       extractor.advance()
      }
     }
    }
    when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
     MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
      val outputFormat = decoder.outputFormat
      channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
     }
     else -> if (outputIndex >= 0) {
      decoder.getOutputBuffer(outputIndex)?.apply {
       order(ByteOrder.LITTLE_ENDIAN)
       position(info.offset)
       limit(info.offset + info.size)
       if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
        while (remaining() >= 4) samples.add(float)
       } else {
        while (remaining() >= 2) samples.add(short / 32768f)
       }
      }
      outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
      decoder.releaseOutputBuffer(outputIndex, false)
     }
    }
   }
  } finally {
   decoder.stop()
   decoder.release()
   extractor.release()
  }
  return samples.toStereo(channels, sampleRate).resample(SAMPLE_RATE)
 }

 private companion object {
  const val SAMPLE_RATE = 44_100
  const val SEGMENT_SAMPLES = 343_980
  const val OVERLAP = SEGMENT_SAMPLES / 4
  const val STRIDE = SEGMENT_SAMPLES - OVERLAP
  const val MODEL_URL = "https://huggingface.co/StemSplitio/htdemucs-6s-onnx/resolve/main/htdemucs_6s_fp16weights.onnx"
  const val MODEL_SHA256 = "7ce55792e2231c93fbf92de95f5fd5b3a5e6c89f7db690dfd693e8f1dce56869"
 }
}

private fun File.sha256(): String {
 if (!isFile) return ""
 val digest = MessageDigest.getInstance("SHA-256")
 inputStream().buffered().use { input ->
  val buffer = ByteArray(256 * 1024)
  while (true) {
   val count = input.read(buffer)
   if (count < 0) break
   digest.update(buffer, 0, count)
  }
 }
 return digest.digest().joinToString("") { "%02x".format(it) }
}

private data class StereoPcm(val left: FloatArray, val right: FloatArray, val sampleRate: Int) {
 fun resample(targetRate: Int): StereoPcm {
  if (sampleRate == targetRate) return this
  val targetSize = (left.size.toLong() * targetRate / sampleRate).toInt()
  fun channel(source: FloatArray) = FloatArray(targetSize) { index ->
   val position = index.toDouble() * sampleRate / targetRate
   val base = position.toInt().coerceIn(0, source.lastIndex)
   val next = (base + 1).coerceAtMost(source.lastIndex)
   val fraction = (position - base).toFloat()
   source[base] * (1f - fraction) + source[next] * fraction
  }
  return StereoPcm(channel(left), channel(right), targetRate)
 }
}

private class FloatCollector {
 private var values = FloatArray(1 shl 20)
 private var size = 0
 fun add(value: Float) {
  if (size == values.size) values = values.copyOf(values.size * 2)
  values[size++] = value
 }
 fun toStereo(channels: Int, sampleRate: Int): StereoPcm {
  val frames = size / channels.coerceAtLeast(1)
  val left = FloatArray(frames)
  val right = FloatArray(frames)
  for (frame in 0 until frames) {
   left[frame] = values[frame * channels]
   right[frame] = if (channels > 1) values[frame * channels + 1] else left[frame]
  }
  return StereoPcm(left, right, sampleRate)
 }
}

private class WavStreamWriter(private val file: File, private val sampleRate: Int) : AutoCloseable {
 private val output = RandomAccessFile(file, "rw").apply { setLength(0); write(ByteArray(44)) }
 private var framesWritten = 0L
 private var previousLeft = FloatArray(0)
 private var previousRight = FloatArray(0)

 fun append(left: FloatArray, right: FloatArray, chunkIndex: Int, chunkCount: Int) {
  if (chunkCount == 1) {
   writeFrames(left, right, 0, left.size)
   return
  }
  if (chunkIndex == 0) {
   val bodyEnd = minOf(STRIDE, left.size)
   writeFrames(left, right, 0, bodyEnd)
   previousLeft = left.copyOfRange(bodyEnd, left.size)
   previousRight = right.copyOfRange(bodyEnd, right.size)
   return
  }
  val blendCount = minOf(OVERLAP, previousLeft.size, left.size)
  val blendLeft = FloatArray(blendCount)
  val blendRight = FloatArray(blendCount)
  for (index in 0 until blendCount) {
   val incoming = index.toFloat() / OVERLAP
   blendLeft[index] = previousLeft[index] * (1f - incoming) + left[index] * incoming
   blendRight[index] = previousRight[index] * (1f - incoming) + right[index] * incoming
  }
  writeFrames(blendLeft, blendRight, 0, blendCount)
  val bodyEnd = minOf(STRIDE, left.size)
  if (bodyEnd > blendCount) writeFrames(left, right, blendCount, bodyEnd)
  if (chunkIndex == chunkCount - 1) {
   if (left.size > bodyEnd) writeFrames(left, right, bodyEnd, left.size)
  } else {
   previousLeft = left.copyOfRange(bodyEnd, left.size)
   previousRight = right.copyOfRange(bodyEnd, right.size)
  }
 }

 private fun writeFrames(left: FloatArray, right: FloatArray, start: Int, end: Int) {
  val bytes = ByteArray((end - start) * 4)
  var byteIndex = 0
  for (index in start until end) {
   val l = (left[index].coerceIn(-1f, 1f) * 32767f).toInt().toShort().toInt()
   val r = (right[index].coerceIn(-1f, 1f) * 32767f).toInt().toShort().toInt()
   bytes[byteIndex++] = l.toByte(); bytes[byteIndex++] = (l ushr 8).toByte()
   bytes[byteIndex++] = r.toByte(); bytes[byteIndex++] = (r ushr 8).toByte()
  }
  output.write(bytes)
  framesWritten += end - start
 }

 override fun close() {
  if (output.channel.isOpen) {
   val dataSize = framesWritten * 4
   output.seek(0)
   output.write(wavHeader(dataSize, sampleRate))
   output.close()
  }
 }

 fun abort() {
  runCatching { output.close() }
  file.delete()
 }

 private companion object {
  const val SEGMENT_SAMPLES = 343_980
  const val OVERLAP = SEGMENT_SAMPLES / 4
  const val STRIDE = SEGMENT_SAMPLES - OVERLAP
  fun wavHeader(dataSize: Long, sampleRate: Int): ByteArray {
   val header = ByteArray(44)
   fun text(offset: Int, value: String) = value.forEachIndexed { index, char -> header[offset + index] = char.code.toByte() }
   fun int(offset: Int, value: Long, bytes: Int) = repeat(bytes) { header[offset + it] = (value ushr (8 * it)).toByte() }
   text(0, "RIFF"); int(4, 36 + dataSize, 4); text(8, "WAVE"); text(12, "fmt ")
   int(16, 16, 4); int(20, 1, 2); int(22, 2, 2); int(24, sampleRate.toLong(), 4)
   int(28, sampleRate * 4L, 4); int(32, 4, 2); int(34, 16, 2); text(36, "data"); int(40, dataSize, 4)
   return header
  }
 }
}
