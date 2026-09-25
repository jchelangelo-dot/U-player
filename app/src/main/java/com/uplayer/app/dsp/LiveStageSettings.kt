package com.uplayer.app.dsp

import android.content.Context
import android.os.Bundle

enum class StageVenue(
 val label: String,
 val width: Float,
 val distance: Float,
 val impact: Float,
 val subImpact: Float,
 val air: Float
) {
 CLUB("CLUB", 1.08f, 0.16f, 0.62f, 0.58f, 0.28f),
 HALL("HALL", 1.25f, 0.32f, 0.48f, 0.42f, 0.42f),
 ARENA("ARENA", 1.38f, 0.42f, 0.68f, 0.52f, 0.30f),
 STADIUM("STADIUM", 1.55f, 0.56f, 0.58f, 0.48f, 0.50f)
}

data class LiveStageSettings(
 val enabled: Boolean = false,
 val venue: StageVenue = StageVenue.ARENA,
 val stageWidth: Float = StageVenue.ARENA.width,
 val distance: Float = StageVenue.ARENA.distance,
 val impact: Float = StageVenue.ARENA.impact,
 val subImpact: Float = StageVenue.ARENA.subImpact,
 val air: Float = StageVenue.ARENA.air
) {
 fun withVenue(value: StageVenue) = copy(
  venue = value,
  stageWidth = value.width,
  distance = value.distance,
  impact = value.impact,
  subImpact = value.subImpact,
  air = value.air
 )
}

class LiveStageSettingsStore(context: Context) {
 private val preferences = context.getSharedPreferences("uplayer_live_stage", Context.MODE_PRIVATE)

 fun load() = LiveStageSettings(
  enabled = preferences.getBoolean("enabled", false),
  venue = runCatching { StageVenue.valueOf(preferences.getString("venue", StageVenue.ARENA.name)!!) }
   .getOrDefault(StageVenue.ARENA),
  stageWidth = preferences.getFloat("width", StageVenue.ARENA.width).coerceIn(0.7f, 1.8f),
  distance = preferences.getFloat("distance", StageVenue.ARENA.distance).coerceIn(0f, 1f),
  impact = preferences.getFloat("impact", StageVenue.ARENA.impact).coerceIn(0f, 1f),
  subImpact = preferences.getFloat("sub_impact", StageVenue.ARENA.subImpact).coerceIn(0f, 1f),
  air = preferences.getFloat("air", StageVenue.ARENA.air).coerceIn(0f, 1f)
 )

 fun save(settings: LiveStageSettings) {
  preferences.edit()
   .putBoolean("enabled", settings.enabled)
   .putString("venue", settings.venue.name)
   .putFloat("width", settings.stageWidth)
   .putFloat("distance", settings.distance)
   .putFloat("impact", settings.impact)
   .putFloat("sub_impact", settings.subImpact)
   .putFloat("air", settings.air)
   .apply()
 }
}

object LiveStageCommand {
 const val ACTION_UPDATE = "com.uplayer.app.UPDATE_LIVE_STAGE"

 fun toBundle(settings: LiveStageSettings) = Bundle().apply {
  putBoolean("enabled", settings.enabled)
  putString("venue", settings.venue.name)
  putFloat("width", settings.stageWidth)
  putFloat("distance", settings.distance)
  putFloat("impact", settings.impact)
  putFloat("sub_impact", settings.subImpact)
  putFloat("air", settings.air)
 }

 fun fromBundle(bundle: Bundle) = LiveStageSettings(
  enabled = bundle.getBoolean("enabled", false),
  venue = runCatching { StageVenue.valueOf(bundle.getString("venue", StageVenue.ARENA.name)) }
   .getOrDefault(StageVenue.ARENA),
  stageWidth = bundle.getFloat("width", StageVenue.ARENA.width).coerceIn(0.7f, 1.8f),
  distance = bundle.getFloat("distance", StageVenue.ARENA.distance).coerceIn(0f, 1f),
  impact = bundle.getFloat("impact", StageVenue.ARENA.impact).coerceIn(0f, 1f),
  subImpact = bundle.getFloat("sub_impact", StageVenue.ARENA.subImpact).coerceIn(0f, 1f),
  air = bundle.getFloat("air", StageVenue.ARENA.air).coerceIn(0f, 1f)
 )
}
