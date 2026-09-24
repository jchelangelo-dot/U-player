package com.uplayer.app.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
class ParametricEqAudioProcessor(initialSettings: EqSettings) : BaseAudioProcessor() {
 private val settingsReference = AtomicReference(initialSettings)
 private var appliedSettings: EqSettings? = null
 private var filters: Array<Biquad> = emptyArray()
 private var channelStates: Array<Array<FilterState>> = emptyArray()
 private var linearPreamp = 1.0

 fun updateSettings(settings: EqSettings) {
  settingsReference.set(settings)
 }

 override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
  if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
   throw AudioProcessor.UnhandledAudioFormatException(
    "Parametric EQ requires 16-bit or float PCM",
    inputAudioFormat
   )
  }
  return inputAudioFormat
 }

 override fun isActive(): Boolean = super.isActive()

 override fun queueInput(inputBuffer: ByteBuffer) {
  if (!inputBuffer.hasRemaining()) return
  val settings = settingsReference.get()
  if (settings != appliedSettings || channelStates.size != inputAudioFormat.channelCount) {
   configureFilters(settings)
  }

  val output = replaceOutputBuffer(inputBuffer.remaining())
  if (!settings.enabled) {
   output.put(inputBuffer)
   output.flip()
   return
  }

  while (inputBuffer.hasRemaining()) {
   repeat(inputAudioFormat.channelCount) { channel ->
    val sample = when (inputAudioFormat.encoding) {
     C.ENCODING_PCM_16BIT -> inputBuffer.getShort() / 32768.0
     C.ENCODING_PCM_FLOAT -> inputBuffer.getFloat().toDouble()
     else -> 0.0
    }
    var processed = sample * linearPreamp
    filters.forEachIndexed { index, filter ->
     processed = filter.process(processed, channelStates[channel][index])
    }
    if (settings.limiterEnabled) processed = processed.coerceIn(-0.98, 0.98)
    when (inputAudioFormat.encoding) {
     C.ENCODING_PCM_16BIT -> output.putShort((processed.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort())
     C.ENCODING_PCM_FLOAT -> output.putFloat(processed.coerceIn(-1.0, 1.0).toFloat())
    }
   }
  }
  output.flip()
 }

 override fun onFlush() {
  clearStates()
 }

 override fun onReset() {
  appliedSettings = null
  filters = emptyArray()
  channelStates = emptyArray()
 }

 private fun configureFilters(settings: EqSettings) {
  val sampleRate = inputAudioFormat.sampleRate.toDouble()
  filters = settings.bands.map { Biquad.create(it, sampleRate) }.toTypedArray()
  linearPreamp = 10.0.pow(settings.effectivePreampDb / 20.0)
  if (channelStates.size != inputAudioFormat.channelCount || channelStates.firstOrNull()?.size != filters.size) {
   channelStates = Array(inputAudioFormat.channelCount) { Array(filters.size) { FilterState() } }
  }
  appliedSettings = settings
 }

 private fun clearStates() {
  channelStates.forEach { channel -> channel.forEach(FilterState::clear) }
 }
}

private data class FilterState(var z1: Double = 0.0, var z2: Double = 0.0) {
 fun clear() {
  z1 = 0.0
  z2 = 0.0
 }
}

private data class Biquad(
 val b0: Double,
 val b1: Double,
 val b2: Double,
 val a1: Double,
 val a2: Double
) {
 fun process(input: Double, state: FilterState): Double {
  val output = b0 * input + state.z1
  state.z1 = b1 * input - a1 * output + state.z2
  state.z2 = b2 * input - a2 * output
  return if (output.isFinite()) output else 0.0
 }

 fun magnitudeDb(frequencyHz: Double, sampleRate: Double): Double {
  val omega = 2.0 * PI * frequencyHz / sampleRate
  val numeratorReal = b0 + b1 * cos(omega) + b2 * cos(2.0 * omega)
  val numeratorImaginary = -b1 * sin(omega) - b2 * sin(2.0 * omega)
  val denominatorReal = 1.0 + a1 * cos(omega) + a2 * cos(2.0 * omega)
  val denominatorImaginary = -a1 * sin(omega) - a2 * sin(2.0 * omega)
  val numeratorPower = numeratorReal * numeratorReal + numeratorImaginary * numeratorImaginary
  val denominatorPower = denominatorReal * denominatorReal + denominatorImaginary * denominatorImaginary
  return 10.0 * log10((numeratorPower / denominatorPower).coerceAtLeast(1e-12))
 }

 companion object {
  fun create(band: EqBand, sampleRate: Double): Biquad {
   val frequency = band.frequencyHz.toDouble().coerceIn(20.0, sampleRate * 0.45)
   val gain = band.gainDb.toDouble().coerceIn(-12.0, 12.0)
   val q = band.q.toDouble().coerceIn(0.2, 10.0)
   if (abs(gain) < 0.0001) return Biquad(1.0, 0.0, 0.0, 0.0, 0.0)

   val amplitude = 10.0.pow(gain / 40.0)
   val omega = 2.0 * PI * frequency / sampleRate
   val cosine = cos(omega)
   val alpha = sin(omega) / (2.0 * q)
   val sqrtAmplitude = sqrt(amplitude)

   val raw = when (band.type) {
    EqFilterType.PEAK -> RawCoefficients(
     b0 = 1.0 + alpha * amplitude,
     b1 = -2.0 * cosine,
     b2 = 1.0 - alpha * amplitude,
     a0 = 1.0 + alpha / amplitude,
     a1 = -2.0 * cosine,
     a2 = 1.0 - alpha / amplitude
    )
    EqFilterType.LOW_SHELF -> RawCoefficients(
     b0 = amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosine + 2.0 * sqrtAmplitude * alpha),
     b1 = 2.0 * amplitude * ((amplitude - 1.0) - (amplitude + 1.0) * cosine),
     b2 = amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosine - 2.0 * sqrtAmplitude * alpha),
     a0 = (amplitude + 1.0) + (amplitude - 1.0) * cosine + 2.0 * sqrtAmplitude * alpha,
     a1 = -2.0 * ((amplitude - 1.0) + (amplitude + 1.0) * cosine),
     a2 = (amplitude + 1.0) + (amplitude - 1.0) * cosine - 2.0 * sqrtAmplitude * alpha
    )
    EqFilterType.HIGH_SHELF -> RawCoefficients(
     b0 = amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosine + 2.0 * sqrtAmplitude * alpha),
     b1 = -2.0 * amplitude * ((amplitude - 1.0) + (amplitude + 1.0) * cosine),
     b2 = amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosine - 2.0 * sqrtAmplitude * alpha),
     a0 = (amplitude + 1.0) - (amplitude - 1.0) * cosine + 2.0 * sqrtAmplitude * alpha,
     a1 = 2.0 * ((amplitude - 1.0) - (amplitude + 1.0) * cosine),
     a2 = (amplitude + 1.0) - (amplitude - 1.0) * cosine - 2.0 * sqrtAmplitude * alpha
    )
   }
   return raw.normalized()
  }
 }
}

private data class RawCoefficients(
 val b0: Double,
 val b1: Double,
 val b2: Double,
 val a0: Double,
 val a1: Double,
 val a2: Double
) {
 fun normalized() = Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
}

internal fun EqSettings.responseDb(frequencyHz: Double, sampleRate: Double = 48_000.0): Double {
 if (!enabled) return 0.0
 return bands.sumOf { Biquad.create(it, sampleRate).magnitudeDb(frequencyHz, sampleRate) } + effectivePreampDb
}
