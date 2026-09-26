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
class ParametricEqAudioProcessor(
 initialSettings: EqSettings,
 initialLiveStage: LiveStageSettings = LiveStageSettings()
) : BaseAudioProcessor() {
 private val settingsReference = AtomicReference(initialSettings)
 private val liveStageReference = AtomicReference(initialLiveStage)
 private var appliedSettings: EqSettings? = null
 private var appliedLiveStage: LiveStageSettings? = null
 private var filters: Array<Biquad> = emptyArray()
 private var channelStates: Array<Array<FilterState>> = emptyArray()
 private var targetLinearPreamp = 1.0
 private var smoothedLinearPreamp = 1.0
 private var preampSmoothing = 1.0
 private var stageProcessor: SpatialStageProcessor? = null
 private var previousStageProcessor: SpatialStageProcessor? = null
 private var stageTransitionFrames = 0
 private var limiter = LinkedPeakLimiter()

 fun updateSettings(settings: EqSettings) {
  settingsReference.set(settings)
 }

 fun updateLiveStage(settings: LiveStageSettings) {
  liveStageReference.set(settings)
 }

 override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
  if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
   throw AudioProcessor.UnhandledAudioFormatException(
    "Parametric EQ requires 16-bit or float PCM",
    inputAudioFormat
   )
  }
  preampSmoothing = 1.0 - kotlin.math.exp(-1.0 / (inputAudioFormat.sampleRate * 0.012))
  limiter.configure(inputAudioFormat.sampleRate)
  return inputAudioFormat
 }

 override fun isActive(): Boolean = super.isActive()

 override fun queueInput(inputBuffer: ByteBuffer) {
  if (!inputBuffer.hasRemaining()) return
  val settings = settingsReference.get()
  val liveStage = liveStageReference.get()
  if (settings != appliedSettings || channelStates.size != inputAudioFormat.channelCount) {
   configureFilters(settings)
  }
  if (liveStage != appliedLiveStage || stageProcessor == null) {
   val replacement = SpatialStageProcessor(inputAudioFormat.sampleRate, liveStage)
   if (stageProcessor != null && appliedLiveStage != null) {
    previousStageProcessor = stageProcessor
    stageTransitionFrames = STAGE_TRANSITION_FRAMES
   }
   stageProcessor = replacement
   appliedLiveStage = liveStage
  }

  val output = replaceOutputBuffer(inputBuffer.remaining())
  if (!settings.enabled && !liveStage.enabled && previousStageProcessor == null && !settings.limiterEnabled) {
   output.put(inputBuffer)
   output.flip()
   return
  }

  while (inputBuffer.hasRemaining()) {
   smoothedLinearPreamp += (targetLinearPreamp - smoothedLinearPreamp) * preampSmoothing
   val frame = DoubleArray(inputAudioFormat.channelCount) { channel ->
    val input = if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
     inputBuffer.getShort() / 32768.0
    } else inputBuffer.getFloat().toDouble()
    if (settings.enabled) {
     var processed = input * smoothedLinearPreamp
     filters.forEachIndexed { index, filter ->
      processed = filter.process(processed, channelStates[channel][index])
     }
     processed
    } else {
     input
    }
   }
   processStage(frame, liveStage.enabled)
   if (settings.limiterEnabled) limiter.process(frame) else limiter.reset()
   repeat(inputAudioFormat.channelCount) { channel ->
    val processed = frame[channel]
    if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
     output.putShort((processed.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort())
    } else output.putFloat(processed.coerceIn(-1.0, 1.0).toFloat())
   }
  }
  output.flip()
 }

 override fun onFlush() {
  clearStates()
  stageProcessor?.clear()
  previousStageProcessor?.clear()
  previousStageProcessor = null
  stageTransitionFrames = 0
  limiter.reset()
 }

 override fun onReset() {
  appliedSettings = null
  filters = emptyArray()
  channelStates = emptyArray()
  appliedLiveStage = null
  stageProcessor = null
  previousStageProcessor = null
  stageTransitionFrames = 0
  limiter.reset()
 }

 private fun configureFilters(settings: EqSettings) {
  val sampleRate = inputAudioFormat.sampleRate.toDouble()
  filters = settings.bands.map { Biquad.create(it, sampleRate) }.toTypedArray()
  targetLinearPreamp = 10.0.pow(settings.effectivePreampDb / 20.0)
  if (appliedSettings == null) smoothedLinearPreamp = targetLinearPreamp
  if (channelStates.size != inputAudioFormat.channelCount || channelStates.firstOrNull()?.size != filters.size) {
   channelStates = Array(inputAudioFormat.channelCount) { Array(filters.size) { FilterState() } }
  }
  appliedSettings = settings
 }

 private fun clearStates() {
  channelStates.forEach { channel -> channel.forEach(FilterState::clear) }
 }

 private fun processStage(frame: DoubleArray, enabled: Boolean) {
  val previous = previousStageProcessor
  if (previous != null && stageTransitionFrames > 0) {
   val oldFrame = frame.copyOf()
   val newFrame = frame.copyOf()
   if (previous.settings.enabled) previous.process(oldFrame)
   if (enabled) stageProcessor?.process(newFrame)
   val mix = 1.0 - stageTransitionFrames.toDouble() / STAGE_TRANSITION_FRAMES
   frame.indices.forEach { channel ->
    frame[channel] = oldFrame[channel] * (1.0 - mix) + newFrame[channel] * mix
   }
   stageTransitionFrames--
   if (stageTransitionFrames == 0) previousStageProcessor = null
  } else if (enabled) {
   stageProcessor?.process(frame)
  }
 }

 private companion object {
  const val STAGE_TRANSITION_FRAMES = 1_024
 }
}

/** Stereo-linked limiter: both channels keep the same gain so the sound image does not move. */
internal class LinkedPeakLimiter {
 private var gain = 1.0
 private var releaseCoefficient = 0.0

 fun configure(sampleRate: Int) {
  releaseCoefficient = kotlin.math.exp(-1.0 / (sampleRate * 0.22))
  reset()
 }

 fun process(frame: DoubleArray) {
  val peak = frame.maxOfOrNull { abs(it) } ?: return
  val targetGain = if (peak > LIMITER_CEILING) LIMITER_CEILING / peak else 1.0
  gain = if (targetGain < gain) {
   targetGain // instant attack prevents a transient from hard-clipping
  } else {
   targetGain + releaseCoefficient * (gain - targetGain)
  }
  frame.indices.forEach { channel -> frame[channel] *= gain }
 }

 fun reset() {
  gain = 1.0
 }

 private companion object {
  const val LIMITER_CEILING = 0.96
 }
}

private class SpatialStageProcessor(
 private val sampleRate: Int,
 val settings: LiveStageSettings
) {
 private val maxDelay = (sampleRate * 0.18f).toInt().coerceAtLeast(1)
 private val leftDelay = DoubleArray(maxDelay)
 private val rightDelay = DoubleArray(maxDelay)
 private val lowState = DoubleArray(2)
 private val subState = DoubleArray(2)
 private val airState = DoubleArray(2)
 private val distanceState = DoubleArray(2)
 private var delayIndex = 0

 fun process(frame: DoubleArray) {
  if (frame.size < 2) return
  var left = tone(frame[0], 0)
  var right = tone(frame[1], 1)

  val mid = (left + right) * 0.5
  val side = (left - right) * 0.5 * settings.stageWidth
  left = mid + side
  right = mid - side

  val distanceAmount = settings.distance * 0.55
  distanceState[0] += 0.18 * (left - distanceState[0])
  distanceState[1] += 0.18 * (right - distanceState[1])
  left = left * (1.0 - distanceAmount) + distanceState[0] * distanceAmount
  right = right * (1.0 - distanceAmount) + distanceState[1] * distanceAmount

  val delaySamples = (sampleRate * venueDelaySeconds()).toInt().coerceIn(1, maxDelay - 1)
  val readIndex = (delayIndex - delaySamples + maxDelay) % maxDelay
  val delayedLeft = leftDelay[readIndex]
  val delayedRight = rightDelay[readIndex]
  val feedback = 0.22 + settings.distance * 0.28
  leftDelay[delayIndex] = left + delayedRight * feedback
  rightDelay[delayIndex] = right + delayedLeft * feedback
  delayIndex = (delayIndex + 1) % maxDelay

  val wet = venueWetMix() * (0.62 + settings.distance * 0.58)
  frame[0] = left * (1.0 - wet) + delayedLeft * wet
  frame[1] = right * (1.0 - wet) + delayedRight * wet
 }

 fun clear() {
  leftDelay.fill(0.0)
  rightDelay.fill(0.0)
  lowState.fill(0.0)
  subState.fill(0.0)
  airState.fill(0.0)
  distanceState.fill(0.0)
  delayIndex = 0
 }

 private fun tone(input: Double, channel: Int): Double {
  val lowAlpha = onePoleAlpha(180.0)
  val subAlpha = onePoleAlpha(70.0)
  val airAlpha = onePoleAlpha(6_000.0)
  lowState[channel] += lowAlpha * (input - lowState[channel])
  subState[channel] += subAlpha * (input - subState[channel])
  airState[channel] += airAlpha * (input - airState[channel])
  val punch = lowState[channel] - subState[channel]
  val high = input - airState[channel]
  return input + punch * ((settings.impact - 0.5) * 0.56) +
   subState[channel] * ((settings.subImpact - 0.5) * 0.62) +
   high * ((settings.air - 0.5) * 0.46)
 }

 private fun onePoleAlpha(frequency: Double): Double =
  1.0 - kotlin.math.exp(-2.0 * PI * frequency / sampleRate)

 private fun venueDelaySeconds() = when (settings.venue) {
  StageVenue.CLUB -> 0.024f
  StageVenue.HALL -> 0.047f
  StageVenue.ARENA -> 0.073f
  StageVenue.STADIUM -> 0.118f
 }

 private fun venueWetMix() = when (settings.venue) {
  StageVenue.CLUB -> 0.14
  StageVenue.HALL -> 0.21
  StageVenue.ARENA -> 0.27
  StageVenue.STADIUM -> 0.32
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
