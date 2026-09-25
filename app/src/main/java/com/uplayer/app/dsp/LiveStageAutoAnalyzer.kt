package com.uplayer.app.dsp

import android.content.Context
import android.net.Uri
import com.uplayer.app.focus.AudioPcmDecoder
import java.io.File

data class LiveStageRecommendation(
 val settings: LiveStageSettings,
 val summary: String
)

class LiveStageAutoAnalyzer(private val context: Context) {
 suspend fun analyze(uri: Uri, onProgress: (Float) -> Unit): LiveStageRecommendation {
  val temporary = File(context.cacheDir, "live_stage_${uri.hashCode()}.pcm")
  val decoded = AudioPcmDecoder.decode(context, uri, temporary, onProgress)
  return try {
   val profile = decoded.profile
   val venue = when {
    profile.transientRatio > 0.42f && profile.bassRatio > 0.38f -> StageVenue.CLUB
    profile.dynamicRange > 0.72f && profile.airRatio > 0.32f -> StageVenue.HALL
    profile.stereoRatio < 0.18f -> StageVenue.ARENA
    else -> StageVenue.STADIUM
   }
   val settings = LiveStageSettings(
    enabled = true,
    venue = venue,
    stageWidth = (1.22f + (0.32f - profile.stereoRatio) * 0.9f).coerceIn(1.12f, 1.65f),
    distance = (0.22f + profile.dynamicRange * 0.38f).coerceIn(0.25f, 0.62f),
    impact = (0.62f + profile.transientRatio * 0.65f).coerceIn(0.62f, 0.95f),
    subImpact = (0.52f + profile.bassRatio * 0.62f).coerceIn(0.52f, 0.90f),
    air = (0.82f - profile.airRatio * 0.45f).coerceIn(0.58f, 0.88f)
   )
   LiveStageRecommendation(
    settings,
    "${venue.label} · 스테레오 폭, 저역, 어택과 다이내믹에 맞춰 자동 설정됨"
   )
  } finally {
   decoded.close()
  }
 }
}
