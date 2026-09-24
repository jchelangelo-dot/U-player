package com.uplayer.app.dsp

import android.content.Context
import android.os.Bundle
import kotlin.math.max

enum class EqFilterType(val label: String) {
 PEAK("PEAK"),
 LOW_SHELF("LOW SHELF"),
 HIGH_SHELF("HIGH SHELF");

 fun next(): EqFilterType = entries[(ordinal + 1) % entries.size]
}

data class EqBand(
 val frequencyHz: Float,
 val gainDb: Float = 0f,
 val q: Float = 1f,
 val type: EqFilterType = EqFilterType.PEAK
)

data class EqSettings(
 val enabled: Boolean = true,
 val preampDb: Float = 0f,
 val autoHeadroom: Boolean = true,
 val limiterEnabled: Boolean = true,
 val bands: List<EqBand> = defaultBands()
) {
 val headroomDb: Float
  get() = if (autoHeadroom) max(0f, preampDb + (bands.maxOfOrNull { it.gainDb } ?: 0f)) else 0f

 val effectivePreampDb: Float
  get() = preampDb - headroomDb

 companion object {
  private val frequencies = listOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)

  fun defaultBands(): List<EqBand> = frequencies.mapIndexed { index, frequency ->
   EqBand(
    frequencyHz = frequency,
    type = when (index) {
     0 -> EqFilterType.LOW_SHELF
     9 -> EqFilterType.HIGH_SHELF
     else -> EqFilterType.PEAK
    }
   )
  }
 }
}

class EqSettingsStore(context: Context) {
 private val preferences = context.getSharedPreferences("uplayer_eq", Context.MODE_PRIVATE)

 fun load(): EqSettings {
  val defaults = EqSettings.defaultBands()
  val bands = defaults.mapIndexed { index, default ->
   EqBand(
    frequencyHz = preferences.getFloat("band_${index}_frequency", default.frequencyHz),
    gainDb = preferences.getFloat("band_${index}_gain", default.gainDb),
    q = preferences.getFloat("band_${index}_q", default.q),
    type = runCatching {
     EqFilterType.valueOf(preferences.getString("band_${index}_type", default.type.name)!!)
    }.getOrDefault(default.type)
   ).sanitized()
  }
  return EqSettings(
   enabled = preferences.getBoolean("enabled", true),
   preampDb = preferences.getFloat("preamp", 0f).coerceIn(-12f, 6f),
   autoHeadroom = preferences.getBoolean("auto_headroom", true),
   limiterEnabled = preferences.getBoolean("limiter", true),
   bands = bands
  )
 }

 fun save(settings: EqSettings) {
  preferences.edit().apply {
   putBoolean("enabled", settings.enabled)
   putFloat("preamp", settings.preampDb)
   putBoolean("auto_headroom", settings.autoHeadroom)
   putBoolean("limiter", settings.limiterEnabled)
   settings.bands.forEachIndexed { index, band ->
    putFloat("band_${index}_frequency", band.frequencyHz)
    putFloat("band_${index}_gain", band.gainDb)
    putFloat("band_${index}_q", band.q)
    putString("band_${index}_type", band.type.name)
   }
  }.apply()
 }
}

object EqCommand {
 const val ACTION_UPDATE = "com.uplayer.app.UPDATE_EQ"
 private const val KEY_ENABLED = "enabled"
 private const val KEY_PREAMP = "preamp"
 private const val KEY_AUTO_HEADROOM = "auto_headroom"
 private const val KEY_LIMITER = "limiter"
 private const val KEY_FREQUENCIES = "frequencies"
 private const val KEY_GAINS = "gains"
 private const val KEY_Q_VALUES = "q_values"
 private const val KEY_TYPES = "types"

 fun toBundle(settings: EqSettings) = Bundle().apply {
  putBoolean(KEY_ENABLED, settings.enabled)
  putFloat(KEY_PREAMP, settings.preampDb)
  putBoolean(KEY_AUTO_HEADROOM, settings.autoHeadroom)
  putBoolean(KEY_LIMITER, settings.limiterEnabled)
  putFloatArray(KEY_FREQUENCIES, settings.bands.map { it.frequencyHz }.toFloatArray())
  putFloatArray(KEY_GAINS, settings.bands.map { it.gainDb }.toFloatArray())
  putFloatArray(KEY_Q_VALUES, settings.bands.map { it.q }.toFloatArray())
  putIntArray(KEY_TYPES, settings.bands.map { it.type.ordinal }.toIntArray())
 }

 fun fromBundle(bundle: Bundle): EqSettings? {
  val frequencies = bundle.getFloatArray(KEY_FREQUENCIES) ?: return null
  val gains = bundle.getFloatArray(KEY_GAINS) ?: return null
  val qValues = bundle.getFloatArray(KEY_Q_VALUES) ?: return null
  val types = bundle.getIntArray(KEY_TYPES) ?: return null
  if (frequencies.size != 10 || gains.size != 10 || qValues.size != 10 || types.size != 10) return null
  return EqSettings(
   enabled = bundle.getBoolean(KEY_ENABLED, true),
   preampDb = bundle.getFloat(KEY_PREAMP, 0f).coerceIn(-12f, 6f),
   autoHeadroom = bundle.getBoolean(KEY_AUTO_HEADROOM, true),
   limiterEnabled = bundle.getBoolean(KEY_LIMITER, true),
   bands = List(10) { index ->
    EqBand(
     frequencyHz = frequencies[index],
     gainDb = gains[index],
     q = qValues[index],
     type = EqFilterType.entries.getOrElse(types[index]) { EqFilterType.PEAK }
    ).sanitized()
   }
  )
 }
}

private fun EqBand.sanitized() = copy(
 frequencyHz = frequencyHz.coerceIn(20f, 20_000f),
 gainDb = gainDb.coerceIn(-12f, 12f),
 q = q.coerceIn(0.2f, 10f)
)
