package com.uplayer.app.dsp

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val StageBackground = Color(0xFF02040A)
private val StageUltra = Color(0xFF315CFF)
private val StageText = Color(0xFF7D8495)
private val StageHairline = Color(0xFF182038)

@Composable
fun LiveStageScreen(
 settings: LiveStageSettings,
 mediaUri: Uri?,
 onSettingsChanged: (LiveStageSettings) -> Unit,
 onBack: () -> Unit
) {
 val context = LocalContext.current
 val analyzer = remember(context) { LiveStageAutoAnalyzer(context.applicationContext) }
 val scope = rememberCoroutineScope()
 var analysisProgress by remember(mediaUri) { mutableStateOf<Float?>(null) }
 var analysisSummary by remember(mediaUri) { mutableStateOf<String?>(null) }
 var analysisError by remember(mediaUri) { mutableStateOf<String?>(null) }
 BackHandler(onBack = onBack)
 Column(
  Modifier.fillMaxSize().background(StageBackground).statusBarsPadding().verticalScroll(rememberScrollState())
 ) {
  Row(
   Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = onBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Player", tint = Color.White)
   }
   Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
    Text("LIVE STAGE", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Medium)
    Text("STEREO SPACE · ROOM DSP", color = StageUltra, fontSize = 9.sp, letterSpacing = 1.3.sp)
   }
   Switch(checked = settings.enabled, onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) })
  }
  HorizontalDivider(color = StageHairline, thickness = 0.5.dp)

  StageVisual(settings)

  Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
   TextButton(
    enabled = mediaUri != null && analysisProgress == null,
    onClick = {
     val uri = mediaUri ?: return@TextButton
     analysisError = null
     analysisSummary = null
     analysisProgress = 0f
     scope.launch {
      runCatching {
       withContext(Dispatchers.IO) {
        analyzer.analyze(uri) { value -> scope.launch { analysisProgress = value } }
       }
      }.onSuccess { recommendation ->
       onSettingsChanged(recommendation.settings)
       analysisSummary = recommendation.summary
      }.onFailure { analysisError = it.message ?: "곡 분석에 실패했습니다" }
      analysisProgress = null
     }
    }
   ) {
    Text(if (analysisProgress == null) "ANALYZE & AUTO TUNE" else "ANALYZING…", color = if (analysisProgress == null) StageUltra else StageText)
   }
   analysisProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
   Text(
    analysisSummary ?: analysisError ?: "곡의 스테레오 폭·저역·타격감·다이내믹을 읽어 효과를 자동 조정합니다.",
    color = if (analysisError != null) Color(0xFFFF6B6B) else StageText,
    fontSize = 9.sp,
    lineHeight = 15.sp
   )
  }

  Text("VENUE", color = StageUltra, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 24.dp))
  Row(
   Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
   horizontalArrangement = Arrangement.spacedBy(3.dp)
  ) {
   StageVenue.entries.forEach { venue ->
    TextButton(onClick = { onSettingsChanged(settings.withVenue(venue)) }) {
     Text(venue.label, color = if (venue == settings.venue) Color.White else StageText, fontSize = 10.sp)
    }
   }
  }

  Column(Modifier.padding(horizontal = 24.dp)) {
   StageSlider("STAGE WIDTH", "좌우 무대 폭", "${(settings.stageWidth * 100).roundToInt()}%", settings.stageWidth, 0.7f..1.8f) {
    onSettingsChanged(settings.copy(stageWidth = it))
   }
   StageSlider("DISTANCE", "무대가 앞뒤로 느껴지는 거리", "${(settings.distance * 100).roundToInt()}%", settings.distance, 0f..1f) {
    onSettingsChanged(settings.copy(distance = it))
   }
   StageSlider("IMPACT", "킥과 어택의 타격감", "${(settings.impact * 100).roundToInt()}%", settings.impact, 0f..1f) {
    onSettingsChanged(settings.copy(impact = it))
   }
   StageSlider("SUB IMPACT", "아주 낮은 저역의 무게", "${(settings.subImpact * 100).roundToInt()}%", settings.subImpact, 0f..1f) {
    onSettingsChanged(settings.copy(subImpact = it))
   }
   StageSlider("AIR", "공간의 밝기와 잔향", "${(settings.air * 100).roundToInt()}%", settings.air, 0f..1f) {
    onSettingsChanged(settings.copy(air = it))
   }
   HorizontalDivider(color = StageHairline, thickness = 0.5.dp, modifier = Modifier.padding(top = 12.dp))
   Row(
    Modifier.fillMaxWidth().clickable { onSettingsChanged(settings.copy(enabled = !settings.enabled)) }.padding(vertical = 22.dp),
    verticalAlignment = Alignment.CenterVertically
   ) {
    Text("ORIGINAL", color = if (!settings.enabled) Color.White else StageText, fontSize = 11.sp)
    Box(
     Modifier.weight(1f).padding(horizontal = 14.dp).height(1.dp).background(
      Brush.horizontalGradient(listOf(StageText, StageUltra))
     )
    )
    Text("SESSION", color = if (settings.enabled) StageUltra else StageText, fontSize = 11.sp)
   }
   Text(
    "ORIGINAL ↔ SESSION을 눌러 공간 효과를 즉시 비교할 수 있습니다.",
    color = StageText,
    fontSize = 9.sp,
    modifier = Modifier.padding(bottom = 30.dp)
   )
  }
 }
}

@Composable
private fun StageVisual(settings: LiveStageSettings) {
 Box(
  Modifier.fillMaxWidth().height(190.dp).padding(24.dp).background(
   Brush.verticalGradient(listOf(Color(0xFF07133F), Color(0xFF02040A)))
  ),
  contentAlignment = Alignment.Center
 ) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
   Text(settings.venue.label, color = Color.White, fontSize = 18.sp, letterSpacing = 2.sp)
   Text(
    if (settings.enabled) "LIVE STAGE ACTIVE" else "ORIGINAL SIGNAL",
    color = if (settings.enabled) StageUltra else StageText,
    fontSize = 10.sp,
    modifier = Modifier.padding(top = 8.dp)
   )
   Box(
    Modifier.padding(top = 24.dp).fillMaxWidth(settings.stageWidth.coerceIn(0.7f, 1f)).height(2.dp).background(StageUltra)
   )
  }
 }
}

@Composable
private fun StageSlider(
 label: String,
 help: String,
 valueText: String,
 value: Float,
 range: ClosedFloatingPointRange<Float>,
 onChanged: (Float) -> Unit
) {
 Column(Modifier.padding(top = 14.dp)) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {
    Text(label, color = Color.White, fontSize = 11.sp)
    Text(help, color = StageText, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
   }
   Text(valueText, color = Color.White, fontSize = 11.sp)
  }
  Slider(value = value, onValueChange = onChanged, valueRange = range)
 }
}
