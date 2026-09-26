package com.uplayer.app.focus

import android.content.Context
import org.json.JSONObject

class FocusMixSettingsStore(context: Context) {
 private val preferences = context.getSharedPreferences("uplayer_focus_mix", Context.MODE_PRIVATE)

 fun load(trackId: String): FocusMixSettings {
  if (trackId.isBlank()) return FocusMixSettings()
  return runCatching {
   val root = JSONObject(preferences.getString(trackId, null) ?: return@runCatching FocusMixSettings())
   FocusMixSettings(
    vocal = root.channel("vocal"),
    drums = root.channel("drums"),
    bass = root.channel("bass"),
    guitar = root.channel("guitar")
   )
  }.getOrDefault(FocusMixSettings())
 }

 fun save(trackId: String, settings: FocusMixSettings) {
  if (trackId.isBlank()) return
  val root = JSONObject()
   .put("vocal", settings.vocal.toJson())
   .put("drums", settings.drums.toJson())
   .put("bass", settings.bass.toJson())
   .put("guitar", settings.guitar.toJson())
  preferences.edit().putString(trackId, root.toString()).apply()
 }

 private fun JSONObject.channel(name: String): FocusChannelSettings {
  val channel = optJSONObject(name) ?: return FocusChannelSettings()
  return FocusChannelSettings(
   gainDb = channel.optDouble("gainDb", 0.0).toFloat().coerceIn(-12f, 6f),
   muted = channel.optBoolean("muted", false),
   solo = channel.optBoolean("solo", false)
  )
 }

 private fun FocusChannelSettings.toJson() = JSONObject()
  .put("gainDb", gainDb.toDouble())
  .put("muted", muted)
  .put("solo", solo)
}
