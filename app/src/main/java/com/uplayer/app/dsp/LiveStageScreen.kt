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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
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
import com.uplayer.app.focus.FocusMixSettingsStore
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
 val focusSettingsStore = remember(context) { FocusMixSettingsStore(context.applicationContext) }
 val scope = rememberCoroutineScope()
 var stageProgress by remember(mediaUri) { mutableStateOf<Float?>(null) }
 var stageStatus by remember(mediaUri) { mutableStateOf<String?>(null) }
 var focusReady by remember(trackId) { mutableStateOf(trackId.isNotBlank() && focusAnalyzer.isReady(trackId)) }
 var focusProgress by remember(trackId) { mutableStateOf<FocusAnalysisProgress?>(null) }
 var focusError by remember(trackId) { mutableStateOf<String?>(null) }
 val initialFocusSettings = remember(trackId) { focusSettingsStore.load(trackId) }
 var focusSettings by remember(trackId) { mutableStateOf(initialFocusSettings) }
 var rendering by remember(trackId) { mutableStateOf(false) }
 var hasFocusChanges by remember(trackId) { mutableStateOf(false) }
 var latestSessionFile by remember(trackId, focusReady) {
  mutableStateOf(
   if (focusReady) FocusSessionMixer.cachedMix(focusAnalyzer.sessionDirectory(trackId), initialFocusSettings)
   else null
  )
 }

 fun updateFocus(value: FocusMixSettings) {
  focusSettings = value
  focusSettingsStore.save(trackId, value)
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

  StageVisual(settings)

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
   ) { Text(if (stageProgress == null) "AUTO TUNE STAGE" else "TUNING STAGE…", color = StageUltra, fontSize = 9.sp) }
   stageStatus?.let { Text(it, color = StageMuted, fontSize = 8.sp) }
  }
  Text(
   "FAST ANALYSIS · WIDTH / DISTANCE / IMPACT / AIR",
   color = StageMuted,
   fontSize = 7.sp,
   modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 3.dp)
  )
  stageProgress?.let {
   LinearProgressIndicator(
    progress = { it },
    color = StageUltra,
    trackColor = StageHairline,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(1.dp)
   )
  }

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
     LinearProgressIndicator(
      progress = { it.fraction },
      color = StageUltra,
      trackColor = StageHairline,
      modifier = Modifier.fillMaxWidth().height(1.dp)
     )
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
    ) { Text(if (focusProgress == null) "BUILD 4-PART MIX" else "SEPARATING AUDIO…", color = StageUltra, fontSize = 9.sp) }
    if (focusProgress == null) {
     Text("VOCAL · DRUMS · BASS · GUITAR · ONE-TIME ANALYSIS", color = StageMuted, fontSize = 7.sp)
    }
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
private fun StageVisual(settings: LiveStageSettings) {
 Box(Modifier.fillMaxWidth().height(168.dp).padding(horizontal = 22.dp, vertical = 2.dp)) {
  Canvas(Modifier.fillMaxSize()) {
   val venueScale = when (settings.venue) {
    StageVenue.CLUB -> 0.72f
    StageVenue.HALL -> 0.82f
    StageVenue.ARENA -> 0.92f
    StageVenue.STADIUM -> 1f
   }
   val widthFraction = ((settings.stageWidth - 0.7f) / 1.1f).coerceIn(0f, 1f)
   val stageWidth = size.width * (0.46f + widthFraction * 0.48f) * venueScale
   val stageLeft = (size.width - stageWidth) / 2f
   val stageRight = stageLeft + stageWidth
   val roofY = size.height * (0.18f - settings.air * 0.025f)
   val floorY = size.height * 0.58f

   drawRect(
    brush = Brush.radialGradient(
     listOf(StageUltra.copy(alpha = 0.11f + settings.air * 0.09f), Color.Transparent),
     center = Offset(size.width / 2f, floorY * 0.75f),
     radius = stageWidth * 0.62f
    )
   )
   drawLine(StageUltra.copy(alpha = 0.65f), Offset(stageLeft, roofY), Offset(stageRight, roofY), 0.8.dp.toPx())
   drawLine(StageUltra.copy(alpha = 0.28f), Offset(stageLeft, roofY), Offset(stageLeft * 0.86f, floorY), 0.65.dp.toPx())
   drawLine(StageUltra.copy(alpha = 0.28f), Offset(stageRight, roofY), Offset(size.width - stageLeft * 0.86f, floorY), 0.65.dp.toPx())
   drawLine(StageUltra.copy(alpha = 0.38f), Offset(stageLeft * 0.86f, floorY), Offset(size.width - stageLeft * 0.86f, floorY), 0.7.dp.toPx())

   val speakerWidth = 8.dp.toPx()
   val speakerHeight = 25.dp.toPx()
   listOf(stageLeft + speakerWidth, stageRight - speakerWidth * 2f).forEach { speakerX ->
    drawRoundRect(
     color = Color(0xFF111827),
     topLeft = Offset(speakerX, floorY - speakerHeight),
     size = Size(speakerWidth, speakerHeight),
     cornerRadius = CornerRadius(2.dp.toPx())
    )
    drawCircle(StageUltra.copy(alpha = 0.45f + settings.impact * 0.45f), 2.2.dp.toPx(), Offset(speakerX + speakerWidth / 2f, floorY - speakerHeight * 0.62f))
    val waveCount = 1 + (settings.impact * 3f).roundToInt()
    repeat(waveCount) { wave ->
     val radius = (8f + wave * 7f) * density
     drawArc(
      color = StageUltra.copy(alpha = (0.55f - wave * 0.1f) * settings.impact.coerceAtLeast(0.2f)),
      startAngle = if (speakerX < size.width / 2f) 285f else 105f,
      sweepAngle = 150f,
      useCenter = false,
      topLeft = Offset(speakerX + speakerWidth / 2f - radius, floorY - speakerHeight * 0.62f - radius),
      size = Size(radius * 2f, radius * 2f),
      style = Stroke(0.7.dp.toPx())
     )
    }
   }

   val crowdCount = when (settings.venue) {
    StageVenue.CLUB -> 10
    StageVenue.HALL -> 16
    StageVenue.ARENA -> 24
    StageVenue.STADIUM -> 32
   }
   repeat(crowdCount) { index ->
    val row = index / 8
    val column = index % 8
    val x = size.width * (0.12f + column / 9.2f) + (row % 2) * 5.dp.toPx()
    val y = size.height * (0.69f + row * 0.055f)
    drawCircle(StageUltra.copy(alpha = 0.18f + (index % 3) * 0.08f), 1.1.dp.toPx(), Offset(x, y))
   }

   val listenerY = size.height * (0.64f + settings.distance * 0.22f)
   val listenerScale = 0.65f + settings.distance * 0.65f
   val headRadius = 3.2.dp.toPx() * listenerScale
   val listener = Offset(size.width / 2f, listenerY)
   drawCircle(Color(0xFF9CA8C4), headRadius, listener)
   drawLine(
    Color(0xFF77829C),
    Offset(listener.x, listener.y + headRadius),
    Offset(listener.x, listener.y + headRadius + 12.dp.toPx() * listenerScale),
    1.2.dp.toPx() * listenerScale,
    cap = StrokeCap.Round
   )

   val particleCount = (settings.air * 18f).roundToInt()
   repeat(particleCount) { index ->
    val x = stageLeft + stageWidth * ((index * 37 % 97) / 97f)
    val y = roofY + (floorY - roofY) * ((index * 53 % 89) / 89f)
    drawCircle(Color(0xFF69A1FF).copy(alpha = 0.18f + settings.air * 0.35f), 0.8.dp.toPx(), Offset(x, y))
   }
  }
 }
}
