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

data class EqBandDefinition(
 val shortLabel: String,
 val role: String,
 val description: String,
 val defaultFrequencyHz: Float,
 val frequencyRange: ClosedFloatingPointRange<Float>,
 val defaultType: EqFilterType = EqFilterType.PEAK
)

data class EqSettings(
 val enabled: Boolean = true,
 val preampDb: Float = 0f,
 val autoHeadroom: Boolean = false,
 val limiterEnabled: Boolean = true,
 val preset: EqPreset = EqPreset.FLAT,
 val displayName: String? = null,
 val bands: List<EqBand> = defaultBands()
) {
 val headroomDb: Float
  get() = if (autoHeadroom) max(0f, preampDb + (bands.maxOfOrNull { it.gainDb } ?: 0f)) else 0f

 val effectivePreampDb: Float
  get() = preampDb - headroomDb

 companion object {
  val bandDefinitions = listOf(
   EqBandDefinition("SUB", "초저역", "킥의 깊이와 아주 낮은 베이스", 40f, 20f..80f, EqFilterType.LOW_SHELF),
   EqBandDefinition("BASS", "저역", "베이스와 킥의 무게감", 100f, 60f..180f),
   EqBandDefinition("LOW MID", "저중역", "보컬과 악기의 두께·먹먹함", 250f, 150f..500f),
   EqBandDefinition("MID", "중역", "보컬과 기타의 중심 음색", 630f, 350f..1_200f),
   EqBandDefinition("HIGH MID", "중고역", "보컬·기타의 선명함", 1_600f, 800f..3_000f),
   EqBandDefinition("PRESENCE", "존재감", "스네어 어택과 보컬의 존재감", 4_000f, 2_000f..7_000f),
   EqBandDefinition("TREBLE", "고역", "심벌과 디테일·밝기", 8_000f, 5_000f..12_000f),
   EqBandDefinition("AIR", "공기감", "공간감과 반짝이는 느낌", 14_000f, 9_000f..20_000f, EqFilterType.HIGH_SHELF)
  )
  val bandCount: Int get() = bandDefinitions.size

  fun defaultBands(): List<EqBand> = bandDefinitions.map { definition ->
   EqBand(
    frequencyHz = definition.defaultFrequencyHz,
    type = definition.defaultType
   )
  }
 }
}

private fun gainRanges(vararg values: Pair<Float, Float>) = values.map { it.first..it.second }

enum class EqPreset(
 val label: String,
 val summary: String,
 val gainRanges: List<ClosedFloatingPointRange<Float>>
) {
 FLAT("FLAT", "원음 기준", gainRanges(0f to 0f, 0f to 0f, 0f to 0f, 0f to 0f, 0f to 0f, 0f to 0f, 0f to 0f, 0f to 0f)),
 POP("POP", "저역과 선명도를 가볍게 강조", gainRanges(1f to 3f, 1f to 3f, -1f to 1f, -1f to 1f, 1f to 2f, 1f to 3f, 1f to 2f, 0f to 2f)),
 VOCAL("VOCAL", "저역을 정리하고 목소리를 앞으로", gainRanges(-3f to -1f, -2f to 0f, -1f to 1f, 1f to 3f, 2f to 4f, 1f to 3f, 0f to 2f, 0f to 1f)),
 HEAVY_METAL("HEAVY METAL", "킥·기타 어택과 심벌을 강조", gainRanges(1f to 3f, 2f to 4f, -3f to -1f, -1f to 1f, 1f to 3f, 2f to 4f, 1f to 3f, 0f to 2f)),
 JAZZ("JAZZ", "자연스러운 저역과 악기 질감", gainRanges(-2f to 0f, 0f to 2f, 0f to 2f, -1f to 1f, 0f to 2f, 0f to 2f, 0f to 2f, 0f to 2f)),
 DANCE("DANCE", "서브베이스와 고역 에너지를 강조", gainRanges(3f to 5f, 2f to 4f, -2f to 0f, -2f to 0f, 0f to 2f, 1f to 3f, 2f to 4f, 1f to 3f)),
 CLASSICAL("CLASSICAL", "균형을 유지하며 공간감을 강조", gainRanges(-1f to 1f, 0f to 2f, -1f to 1f, 0f to 2f, 0f to 2f, -1f to 1f, 0f to 2f, 1f to 3f));

 fun settings(): EqSettings = EqSettings(
  preset = this,
  bands = EqSettings.defaultBands().mapIndexed { index, band ->
   val range = gainRanges[index]
   band.copy(gainDb = (range.start + range.endInclusive) / 2f)
  }
 )

}

class EqSettingsStore(context: Context) {
 private val preferences = context.getSharedPreferences("uplayer_eq", Context.MODE_PRIVATE)

 fun load(): EqSettings {
  if (preferences.getInt("schema_version", 0) != SCHEMA_VERSION) return EqSettings()
  val defaults = EqSettings.defaultBands()
  val bands = defaults.mapIndexed { index, default ->
   EqBand(
    frequencyHz = preferences.getFloat("band_${index}_frequency", default.frequencyHz),
    gainDb = preferences.getFloat("band_${index}_gain", default.gainDb),
    q = preferences.getFloat("band_${index}_q", default.q),
    type = runCatching {
     EqFilterType.valueOf(preferences.getString("band_${index}_type", default.type.name)!!)
    }.getOrDefault(default.type)
   ).sanitized(index)
  }
  return EqSettings(
   enabled = preferences.getBoolean("enabled", true),
   preampDb = preferences.getFloat("preamp", 0f).coerceIn(-12f, 6f),
   autoHeadroom = if (preferences.getBoolean("auto_headroom_default_off_migrated", false)) {
    preferences.getBoolean("auto_headroom", false)
   } else {
    false
   },
   limiterEnabled = preferences.getBoolean("limiter", true),
   preset = runCatching {
    EqPreset.valueOf(preferences.getString("preset", EqPreset.FLAT.name)!!)
   }.getOrDefault(EqPreset.FLAT),
   displayName = preferences.getString("display_name", null),
   bands = bands
  )
 }

 fun save(settings: EqSettings) {
  preferences.edit().clear().apply {
   putInt("schema_version", SCHEMA_VERSION)
   putBoolean("enabled", settings.enabled)
   putFloat("preamp", settings.preampDb)
   putBoolean("auto_headroom", settings.autoHeadroom)
   putBoolean("auto_headroom_default_off_migrated", true)
   putBoolean("limiter", settings.limiterEnabled)
   putString("preset", settings.preset.name)
   if (settings.displayName == null) remove("display_name") else putString("display_name", settings.displayName)
   settings.bands.forEachIndexed { index, band ->
    putFloat("band_${index}_frequency", band.frequencyHz)
    putFloat("band_${index}_gain", band.gainDb)
    putFloat("band_${index}_q", band.q)
    putString("band_${index}_type", band.type.name)
   }
  }.apply()
 }

 private companion object {
  const val SCHEMA_VERSION = 2
 }
}

object EqCommand {
 const val ACTION_UPDATE = "com.uplayer.app.UPDATE_EQ"
 private const val KEY_ENABLED = "enabled"
 private const val KEY_PREAMP = "preamp"
 private const val KEY_AUTO_HEADROOM = "auto_headroom"
 private const val KEY_LIMITER = "limiter"
 private const val KEY_PRESET = "preset"
 private const val KEY_DISPLAY_NAME = "display_name"
 private const val KEY_FREQUENCIES = "frequencies"
 private const val KEY_GAINS = "gains"
 private const val KEY_Q_VALUES = "q_values"
 private const val KEY_TYPES = "types"

 fun toBundle(settings: EqSettings) = Bundle().apply {
  putBoolean(KEY_ENABLED, settings.enabled)
  putFloat(KEY_PREAMP, settings.preampDb)
  putBoolean(KEY_AUTO_HEADROOM, settings.autoHeadroom)
  putBoolean(KEY_LIMITER, settings.limiterEnabled)
  putString(KEY_PRESET, settings.preset.name)
  settings.displayName?.let { putString(KEY_DISPLAY_NAME, it) }
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
  val count = EqSettings.bandCount
  if (frequencies.size != count || gains.size != count || qValues.size != count || types.size != count) return null
  return EqSettings(
   enabled = bundle.getBoolean(KEY_ENABLED, true),
   preampDb = bundle.getFloat(KEY_PREAMP, 0f).coerceIn(-12f, 6f),
   autoHeadroom = bundle.getBoolean(KEY_AUTO_HEADROOM, false),
   limiterEnabled = bundle.getBoolean(KEY_LIMITER, true),
   preset = runCatching {
    EqPreset.valueOf(bundle.getString(KEY_PRESET, EqPreset.FLAT.name))
   }.getOrDefault(EqPreset.FLAT),
   displayName = bundle.getString(KEY_DISPLAY_NAME),
   bands = List(count) { index ->
    EqBand(
     frequencyHz = frequencies[index],
     gainDb = gains[index],
     q = qValues[index],
     type = EqFilterType.entries.getOrElse(types[index]) { EqFilterType.PEAK }
    ).sanitized(index)
   }
  )
 }
}

internal fun EqBand.sanitized(index: Int): EqBand {
 val definition = EqSettings.bandDefinitions[index]
 return copy(
 frequencyHz = frequencyHz.coerceIn(definition.frequencyRange.start, definition.frequencyRange.endInclusive),
 gainDb = gainDb.coerceIn(-12f, 12f),
 q = q.coerceIn(0.2f, 10f)
 )
}
