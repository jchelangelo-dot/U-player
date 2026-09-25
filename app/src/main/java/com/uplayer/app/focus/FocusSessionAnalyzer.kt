package com.uplayer.app.focus

import android.content.Context
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
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

 fun isReady(trackId: String): Boolean = FocusStem.entries.all {
  File(sessionDirectory(trackId), it.fileName).let { file -> file.isFile && file.length() > 44L }
 }

 suspend fun analyze(trackId: String, uri: Uri, onProgress: (FocusAnalysisProgress) -> Unit) {
  ensureModel(onProgress)
  onProgress(FocusAnalysisProgress(0.16f, "음원을 PCM으로 준비하는 중"))
  val directory = sessionDirectory(trackId).apply { mkdirs() }
  val decoded = AudioPcmDecoder.decode(context, uri, File(context.cacheDir, "focus_${trackId.hashCode()}.pcm")) { fraction ->
   onProgress(FocusAnalysisProgress(0.16f + fraction * 0.08f, "음원을 PCM으로 준비하는 중 ${(fraction * 100).toInt()}%"))
  }
  val writers = FocusStem.entries.associateWith { WavStreamWriter(File(directory, it.fileName), SAMPLE_RATE) }
  try {
   runModel(decoded, writers, onProgress)
   writers.values.forEach { it.close() }
  } catch (error: Throwable) {
   writers.values.forEach { it.abort() }
   directory.deleteRecursively()
   throw error
  } finally {
   decoded.close()
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
  audio: DecodedPcmFile,
  writers: Map<FocusStem, WavStreamWriter>,
  onProgress: (FocusAnalysisProgress) -> Unit
 ) {
  val environment = OrtEnvironment.getEnvironment()
  onProgress(FocusAnalysisProgress(0.25f, "저메모리 AI 엔진을 준비하는 중"))
  OrtSession.SessionOptions().use { options ->
   options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
   options.setMemoryPatternOptimization(false)
   options.setCPUArenaAllocator(false)
   options.setInterOpNumThreads(1)
   options.setIntraOpNumThreads(1)
   environment.createSession(modelFile.absolutePath, options).use { session ->
   val totalSamples = audio.targetFrameCount
   val chunks = max(1, ceil(totalSamples.toDouble() / STRIDE).toInt())
   val input = FloatArray(2 * SEGMENT_SAMPLES)
   repeat(chunks) { chunkIndex ->
    currentCoroutineContext().ensureActive()
    val start = chunkIndex * STRIDE
    val chunkLength = minOf(SEGMENT_SAMPLES, totalSamples - start)
    input.fill(0f)
    audio.readModelInput(start, chunkLength, input)
    OnnxTensor.createTensor(environment, java.nio.FloatBuffer.wrap(input), longArrayOf(1, 2, SEGMENT_SAMPLES.toLong())).use { tensor ->
     session.run(mapOf("mix" to tensor)).use { result ->
      val output = (result[0] as OnnxTensor).floatBuffer
      fun writeStem(row: Int, target: FocusStem) {
       val left = FloatArray(chunkLength)
       val right = FloatArray(chunkLength)
       val base = row * 2 * SEGMENT_SAMPLES
       for (sample in 0 until chunkLength) {
        left[sample] = output.get(base + sample)
        right[sample] = output.get(base + SEGMENT_SAMPLES + sample)
       }
       writers.getValue(target).append(left, right, chunkIndex, chunks)
      }
      writeStem(0, FocusStem.DRUMS)
      writeStem(1, FocusStem.BASS)
      writeStem(3, FocusStem.VOCAL)
      writeStem(4, FocusStem.GUITAR)
      val residualLeft = FloatArray(chunkLength)
      val residualRight = FloatArray(chunkLength)
      val otherBase = 2 * 2 * SEGMENT_SAMPLES
      val pianoBase = 5 * 2 * SEGMENT_SAMPLES
      repeat(chunkLength) { sample ->
       residualLeft[sample] = output.get(otherBase + sample) + output.get(pianoBase + sample)
       residualRight[sample] = output.get(otherBase + SEGMENT_SAMPLES + sample) +
        output.get(pianoBase + SEGMENT_SAMPLES + sample)
      }
      writers.getValue(FocusStem.RESIDUAL).append(residualLeft, residualRight, chunkIndex, chunks)
     }
    }
    val progress = 0.24f + ((chunkIndex + 1f) / chunks) * 0.74f
    onProgress(FocusAnalysisProgress(progress, "음원 분리 ${chunkIndex + 1}/$chunks"))
   }
   }
  }
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
