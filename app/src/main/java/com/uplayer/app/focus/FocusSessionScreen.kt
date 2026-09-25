package com.uplayer.app.focus

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val FocusBackground = Color(0xFF02040A)
private val FocusUltra = Color(0xFF315CFF)
private val FocusText = Color(0xFF7D8495)
private val FocusHairline = Color(0xFF182038)

@Composable
fun FocusSessionScreen(
 trackId: String,
 title: String,
 mediaUri: Uri?,
 sessionActive: Boolean,
 onPlaySession: (File) -> Unit,
 onPlayOriginal: () -> Unit,
 onBack: () -> Unit
) {
 val context = LocalContext.current
 val analyzer = remember(context) { FocusSessionAnalyzer(context.applicationContext) }
 val scope = rememberCoroutineScope()
 var ready by remember(trackId) { mutableStateOf(analyzer.isReady(trackId)) }
 var progress by remember(trackId) { mutableStateOf<FocusAnalysisProgress?>(null) }
 var error by remember(trackId) { mutableStateOf<String?>(null) }
 var settings by remember(trackId) { mutableStateOf(FocusMixSettings()) }
 var rendering by remember(trackId) { mutableStateOf(false) }
 var hasUserChanges by remember(trackId) { mutableStateOf(false) }
 var latestSessionFile by remember(trackId) { mutableStateOf<File?>(null) }

 fun updateSettings(value: FocusMixSettings) {
  settings = value
  hasUserChanges = true
 }

 fun originalAndBack() {
  if (sessionActive) onPlayOriginal()
  onBack()
 }
 BackHandler(onBack = ::originalAndBack)

 LaunchedEffect(settings, ready, hasUserChanges) {
  if (!ready || !hasUserChanges) return@LaunchedEffect
  delay(180L)
  rendering = true
  error = null
  try {
   val file = withContext(Dispatchers.IO) {
    FocusSessionMixer.render(analyzer.sessionDirectory(trackId), settings)
   }
   latestSessionFile = file
   onPlaySession(file)
  } catch (cancelled: CancellationException) {
   throw cancelled
  } catch (failure: Throwable) {
   error = failure.message ?: "Session 생성에 실패했습니다"
  } finally {
   rendering = false
  }
 }

 Column(Modifier.fillMaxSize().background(FocusBackground).statusBarsPadding()) {
  Row(
   Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = ::originalAndBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Player", tint = Color.White)
   }
   Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
    Text("SESSION FOCUS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    Text(title, color = FocusText, fontSize = 10.sp, maxLines = 1)
   }
  }
  HorizontalDivider(color = FocusHairline, thickness = 0.5.dp)

  if (!ready) {
   Column(
    Modifier.fillMaxSize().padding(28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
   ) {
    Text("4-PART FOCUS MIX", color = Color.White, fontSize = 18.sp)
    Text(
     "VOCAL · DRUMS · BASS · GUITAR를 기기에서 한 번 분석한 뒤 저장합니다. 첫 실행에는 약 136MB 모델 다운로드가 필요합니다.",
     color = FocusText,
     fontSize = 11.sp,
     lineHeight = 18.sp,
     modifier = Modifier.padding(top = 12.dp)
    )
    progress?.let {
     LinearProgressIndicator(progress = { it.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
     Text(it.message, color = FocusUltra, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
    }
    error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp)) }
    TextButton(
     enabled = progress == null && mediaUri != null,
     onClick = {
      val uri = mediaUri ?: return@TextButton
      error = null
      progress = FocusAnalysisProgress(0f, "분석 준비 중")
      scope.launch {
       runCatching {
        withContext(Dispatchers.IO) {
         analyzer.analyze(trackId, uri) { update -> scope.launch { progress = update } }
        }
       }.onSuccess { ready = true }.onFailure { error = it.message ?: "분석에 실패했습니다" }
       progress = null
      }
     }
    ) {
     Text(if (progress == null) "ANALYZE TRACK" else "ANALYZING…", color = if (progress == null) FocusUltra else FocusText)
    }
   }
   return@Column
  }

  Text("각 파트의 음량을 조절하거나 SOLO / MUTE로 집중해서 들을 수 있습니다.", color = FocusText, fontSize = 10.sp, modifier = Modifier.padding(24.dp))
  Row(
   Modifier.fillMaxWidth().padding(horizontal = 10.dp),
   horizontalArrangement = Arrangement.SpaceEvenly
  ) {
   FocusChannel("VOCAL", settings.vocal) { updateSettings(settings.copy(vocal = it)) }
   FocusChannel("DRUMS", settings.drums) { updateSettings(settings.copy(drums = it)) }
   FocusChannel("BASS", settings.bass) { updateSettings(settings.copy(bass = it)) }
   FocusChannel("GUITAR", settings.guitar) { updateSettings(settings.copy(guitar = it)) }
  }
  HorizontalDivider(color = FocusHairline, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp))
  TextButton(
   onClick = {
    if (sessionActive) {
     onPlayOriginal()
    } else {
     latestSessionFile?.takeIf(File::isFile)?.let(onPlaySession) ?: run {
     scope.launch {
      rendering = true
      try {
       val file = withContext(Dispatchers.IO) {
        FocusSessionMixer.render(analyzer.sessionDirectory(trackId), settings)
       }
       latestSessionFile = file
       onPlaySession(file)
      } catch (failure: Throwable) {
       error = failure.message ?: "Session 생성에 실패했습니다"
      } finally {
       rendering = false
      }
     }
     }
    }
   },
   enabled = !rendering,
   modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp)
  ) {
   Text(
    when {
     rendering -> "UPDATING SESSION…"
     sessionActive -> "ORIGINAL · OFF"
     else -> "ORIGINAL · ON"
    },
    color = if (sessionActive) FocusText else FocusUltra,
    fontSize = 11.sp
   )
  }
  error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 24.dp)) }
 }
}

@Composable
private fun FocusChannel(label: String, value: FocusChannelSettings, onChange: (FocusChannelSettings) -> Unit) {
 Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
  Text(label, color = Color.White, fontSize = 10.sp)
  TextButton(onClick = { onChange(value.copy(gainDb = (value.gainDb + 0.5f).coerceAtMost(6f))) }) {
   Text("+", color = FocusUltra, fontSize = 18.sp)
  }
  Text(String.format(if (value.gainDb >= 0f) "+%.1f" else "%.1f", value.gainDb), color = FocusUltra, fontSize = 12.sp)
  TextButton(onClick = { onChange(value.copy(gainDb = (value.gainDb - 0.5f).coerceAtLeast(-12f))) }) {
   Text("−", color = FocusUltra, fontSize = 18.sp)
  }
  TextButton(onClick = { onChange(value.copy(solo = !value.solo)) }) {
   Text("SOLO", color = if (value.solo) Color.White else FocusText, fontSize = 9.sp)
  }
  TextButton(onClick = { onChange(value.copy(muted = !value.muted)) }) {
   Text("MUTE", color = if (value.muted) Color(0xFFFF6B6B) else FocusText, fontSize = 9.sp)
  }
 }
}
