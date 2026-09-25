package com.uplayer.app.dsp

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplayer.app.focus.FocusAnalysisProgress
import com.uplayer.app.focus.FocusChannelSettings
import com.uplayer.app.focus.FocusMixSettings
import com.uplayer.app.focus.FocusSessionAnalyzer
import com.uplayer.app.focus.FocusSessionMixer
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val StageBackground = Color(0xFF02040A)
private val StageUltra = Color(0xFF315CFF)
private val StageLabel = Color(0xFF8A91A3)
private val StageMuted = Color(0xFF626979)
private val StageHairline = Color(0xFF182038)
private val StageMute = Color(0xFFFF4D57)

@Composable
fun LiveStageScreen(
 settings: LiveStageSettings,
 mediaUri: Uri?,
 trackId: String,
 sessionActive: Boolean,
 onSettingsChanged: (LiveStageSettings) -> Unit,
 onPlaySession: (File) -> Unit,
 onPlayOriginal: () -> Unit,
 onBack: () -> Unit
) {
 val context = LocalContext.current
 val stageAnalyzer = remember(context) { LiveStageAutoAnalyzer(context.applicationContext) }
 val focusAnalyzer = remember(context) { FocusSessionAnalyzer(context.applicationContext) }
 val scope = rememberCoroutineScope()
 var stageProgress by remember(mediaUri) { mutableStateOf<Float?>(null) }
 var stageStatus by remember(mediaUri) { mutableStateOf<String?>(null) }
 var focusReady by remember(trackId) { mutableStateOf(trackId.isNotBlank() && focusAnalyzer.isReady(trackId)) }
 var focusProgress by remember(trackId) { mutableStateOf<FocusAnalysisProgress?>(null) }
 var focusError by remember(trackId) { mutableStateOf<String?>(null) }
 var focusSettings by remember(trackId) { mutableStateOf(FocusMixSettings()) }
 var rendering by remember(trackId) { mutableStateOf(false) }
 var hasFocusChanges by remember(trackId) { mutableStateOf(false) }
 var latestSessionFile by remember(trackId) { mutableStateOf<File?>(null) }

 fun updateFocus(value: FocusMixSettings) {
  focusSettings = value
  hasFocusChanges = true
 }
 fun leave() {
  if (sessionActive) onPlayOriginal()
  onBack()
 }
 BackHandler(onBack = ::leave)

 LaunchedEffect(focusSettings, focusReady, hasFocusChanges) {
  if (!focusReady || !hasFocusChanges) return@LaunchedEffect
  delay(180L)
  rendering = true
  focusError = null
  try {
   val file = withContext(Dispatchers.IO) {
    FocusSessionMixer.render(focusAnalyzer.sessionDirectory(trackId), focusSettings)
   }
   latestSessionFile = file
   onPlaySession(file)
  } catch (cancelled: CancellationException) {
   throw cancelled
  } catch (failure: Throwable) {
   focusError = failure.message ?: "Session 생성에 실패했습니다"
  } finally {
   rendering = false
  }
 }

 Column(
  Modifier.fillMaxSize().background(StageBackground).statusBarsPadding().verticalScroll(rememberScrollState())
 ) {
  Row(
   Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = ::leave) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Player", tint = StageLabel)
   }
   Text(
    "LIVE MIX",
    color = Color.White,
    fontSize = 14.sp,
    fontWeight = FontWeight.Normal,
    modifier = Modifier.weight(1f),
    textAlign = TextAlign.Center
   )
   Switch(
    checked = settings.enabled,
    onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) },
    modifier = Modifier.scale(0.72f)
   )
  }

  StageVisual()

  Row(
   Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 2.dp),
   horizontalArrangement = Arrangement.SpaceBetween
  ) {
   StageVenue.entries.forEach { venue ->
    Text(
     venue.label,
     color = if (venue == settings.venue) StageUltra else StageMuted,
     fontSize = 9.sp,
     modifier = Modifier.clickable { onSettingsChanged(settings.withVenue(venue)) }.padding(8.dp)
    )
   }
  }

  Row(
   Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
   horizontalArrangement = Arrangement.SpaceEvenly
  ) {
   RotaryKnob("WIDTH", "${(settings.stageWidth * 100).roundToInt()}%", settings.stageWidth, 0.7f..1.8f, {
    onSettingsChanged(settings.copy(stageWidth = it))
   }, Modifier.weight(1f), 68.dp)
   RotaryKnob("DISTANCE", "${(5f + settings.distance * 45f).roundToInt()} m", settings.distance, 0f..1f, {
    onSettingsChanged(settings.copy(distance = it))
   }, Modifier.weight(1f), 68.dp)
   RotaryKnob("IMPACT", "${(settings.impact * 100).roundToInt()}%", settings.impact, 0f..1f, {
    onSettingsChanged(settings.copy(impact = it))
   }, Modifier.weight(1f), 68.dp)
   RotaryKnob("AIR", "${(settings.air * 100).roundToInt()}%", settings.air, 0f..1f, {
    onSettingsChanged(settings.copy(air = it))
   }, Modifier.weight(1f), 68.dp)
  }

  Row(
   Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   TextButton(
    enabled = mediaUri != null && stageProgress == null,
    onClick = {
     val uri = mediaUri ?: return@TextButton
     stageProgress = 0f
     stageStatus = null
     scope.launch {
      runCatching {
       withContext(Dispatchers.IO) {
        stageAnalyzer.analyze(uri) { value -> scope.launch { stageProgress = value } }
       }
      }.onSuccess { recommendation ->
       onSettingsChanged(recommendation.settings)
       stageStatus = "AUTO TUNED"
      }.onFailure { stageStatus = it.message ?: "분석 실패" }
      stageProgress = null
     }
    },
    modifier = Modifier.weight(1f)
   ) { Text(if (stageProgress == null) "ANALYZE TRACK" else "ANALYZING…", color = StageUltra, fontSize = 9.sp) }
   stageStatus?.let { Text(it, color = StageMuted, fontSize = 8.sp) }
  }
  stageProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) }

  HorizontalDivider(
   color = StageHairline,
   thickness = 0.5.dp,
   modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp)
  )

  if (!focusReady) {
   Column(
    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
   ) {
    focusProgress?.let {
     LinearProgressIndicator(progress = { it.fraction }, modifier = Modifier.fillMaxWidth())
     Text(it.message, color = StageMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 5.dp))
    }
    TextButton(
     enabled = focusProgress == null && mediaUri != null && trackId.isNotBlank(),
     onClick = {
      val uri = mediaUri ?: return@TextButton
      focusError = null
      focusProgress = FocusAnalysisProgress(0f, "분석 준비 중")
      scope.launch {
       runCatching {
        withContext(Dispatchers.IO) {
         focusAnalyzer.analyze(trackId, uri) { value -> scope.launch { focusProgress = value } }
        }
       }.onSuccess { focusReady = true }.onFailure { focusError = it.message ?: "분석 실패" }
       focusProgress = null
      }
     }
    ) { Text(if (focusProgress == null) "BUILD 4-PART MIX" else "ANALYZING…", color = StageUltra, fontSize = 9.sp) }
    focusError?.let { Text(it, color = StageMute, fontSize = 8.sp) }
   }
  } else {
   Row(
    Modifier.fillMaxWidth().padding(horizontal = 10.dp),
    horizontalArrangement = Arrangement.SpaceEvenly
   ) {
    FocusChannel("VOCAL", focusSettings.vocal, Modifier.weight(1f)) { updateFocus(focusSettings.copy(vocal = it)) }
    FocusChannel("DRUMS", focusSettings.drums, Modifier.weight(1f)) { updateFocus(focusSettings.copy(drums = it)) }
    FocusChannel("BASS", focusSettings.bass, Modifier.weight(1f)) { updateFocus(focusSettings.copy(bass = it)) }
    FocusChannel("GUITAR", focusSettings.guitar, Modifier.weight(1f)) { updateFocus(focusSettings.copy(guitar = it)) }
   }
   TextButton(
    enabled = !rendering,
    onClick = {
     if (sessionActive) onPlayOriginal() else latestSessionFile?.takeIf(File::isFile)?.let(onPlaySession)
    },
    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 3.dp, bottom = 10.dp)
   ) {
    Text(
     if (rendering) "UPDATING…" else if (sessionActive) "ORIGINAL · OFF" else "ORIGINAL · ON",
     color = if (sessionActive) StageMuted else StageLabel,
     fontSize = 9.sp
    )
   }
   focusError?.let { Text(it, color = StageMute, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 24.dp)) }
  }
 }
}

@Composable
private fun FocusChannel(
 label: String,
 value: FocusChannelSettings,
 modifier: Modifier = Modifier,
 onChange: (FocusChannelSettings) -> Unit
) {
 Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
  RotaryKnob(
   label,
   String.format(Locale.US, if (value.gainDb >= 0f) "+%.1f dB" else "%.1f dB", value.gainDb),
   value.gainDb,
   -12f..6f,
   { onChange(value.copy(gainDb = it)) },
   size = 68.dp
  )
  Text(
   "SOLO",
   color = if (value.solo) StageUltra else StageMuted,
   fontSize = 8.sp,
   modifier = Modifier.clickable { onChange(value.copy(solo = !value.solo)) }.padding(top = 5.dp, bottom = 4.dp)
  )
  Text(
   "MUTE",
   color = if (value.muted) StageMute else StageMuted,
   fontSize = 8.sp,
   modifier = Modifier.clickable { onChange(value.copy(muted = !value.muted)) }.padding(vertical = 4.dp)
  )
 }
}

@Composable
private fun StageVisual() {
 Box(Modifier.fillMaxWidth().height(118.dp).padding(horizontal = 22.dp)) {
  Canvas(Modifier.fillMaxSize()) {
   repeat(3) { index ->
    val inset = index * 18f
    drawArc(
     color = StageUltra.copy(alpha = 0.55f - index * 0.12f),
     startAngle = 190f,
     sweepAngle = 160f,
     useCenter = false,
     topLeft = Offset(inset, 8f + index * 13f),
     size = Size(size.width - inset * 2f, 80f - index * 8f),
     style = Stroke(0.8.dp.toPx(), cap = StrokeCap.Round)
    )
   }
   repeat(24) { index ->
    val x = size.width * (0.05f + (index % 12) / 13f)
    val y = size.height * (0.68f + (index / 12) * 0.16f)
    drawCircle(StageUltra.copy(alpha = 0.25f + (index % 4) * 0.12f), 1.2.dp.toPx(), Offset(x, y))
   }
  }
 }
}
