package com.uplayer.app.dsp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class EqUserPreset(val id: String, val name: String, val settings: EqSettings)

class EqUserPresetStore(context: Context) {
 private val preferences = context.getSharedPreferences("uplayer_eq_user_presets", Context.MODE_PRIVATE)

 fun load(): List<EqUserPreset> = runCatching {
  val root = JSONArray(preferences.getString("presets", "[]"))
  buildList {
   for (index in 0 until root.length()) {
    val item = root.getJSONObject(index)
    decodeSettings(item.getJSONObject("settings"))?.let { settings ->
     add(EqUserPreset(item.getString("id"), item.getString("name"), settings))
    }
   }
  }
 }.getOrDefault(emptyList())

 fun add(name: String, settings: EqSettings, current: List<EqUserPreset>): List<EqUserPreset> {
  val cleanName = name.trim().take(32)
  if (cleanName.isEmpty()) return current
  return (current + EqUserPreset(UUID.randomUUID().toString(), cleanName, settings)).also(::save)
 }

 fun delete(id: String, current: List<EqUserPreset>): List<EqUserPreset> =
  current.filterNot { it.id == id }.also(::save)

 private fun save(presets: List<EqUserPreset>) {
  val root = JSONArray()
  presets.forEach { preset ->
   root.put(JSONObject().put("id", preset.id).put("name", preset.name).put("settings", encodeSettings(preset.settings)))
  }
  preferences.edit().putString("presets", root.toString()).apply()
 }

 private fun encodeSettings(settings: EqSettings) = JSONObject()
  .put("enabled", settings.enabled)
  .put("preamp", settings.preampDb)
  .put("autoHeadroom", settings.autoHeadroom)
  .put("limiter", settings.limiterEnabled)
  .put("bands", JSONArray().apply {
   settings.bands.forEach { band ->
    put(
     JSONObject()
      .put("frequency", band.frequencyHz)
      .put("gain", band.gainDb)
      .put("q", band.q)
      .put("type", band.type.name)
    )
   }
  })

 private fun decodeSettings(json: JSONObject): EqSettings? {
  val bandsJson = json.optJSONArray("bands") ?: return null
  if (bandsJson.length() != EqSettings.bandCount) return null
  val bands = List(EqSettings.bandCount) { index ->
   val band = bandsJson.getJSONObject(index)
   EqBand(
    frequencyHz = band.getDouble("frequency").toFloat(),
    gainDb = band.getDouble("gain").toFloat(),
    q = band.getDouble("q").toFloat(),
    type = runCatching { EqFilterType.valueOf(band.getString("type")) }.getOrDefault(EqFilterType.PEAK)
   ).sanitized(index)
  }
  return EqSettings(
   enabled = json.optBoolean("enabled", true),
   preampDb = json.optDouble("preamp", 0.0).toFloat().coerceIn(-12f, 6f),
   autoHeadroom = json.optBoolean("autoHeadroom", false),
   limiterEnabled = json.optBoolean("limiter", true),
   preset = EqPreset.FLAT,
   bands = bands
  )
 }
}
