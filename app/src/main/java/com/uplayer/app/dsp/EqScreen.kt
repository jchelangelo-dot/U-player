package com.uplayer.app.dsp

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val EqBackground = Color(0xFF02040A)
private val EqUltra = Color(0xFF315CFF)
private val EqText = Color(0xFF7D8495)
private val EqHairline = Color(0xFF182038)

@Composable
fun EqScreen(
 settings: EqSettings,
 onSettingsChanged: (EqSettings) -> Unit,
 onBack: () -> Unit
) {
 var selectedBand by remember { mutableIntStateOf(0) }
 BackHandler(onBack = onBack)

 Column(
  Modifier
   .fillMaxSize()
   .background(EqBackground)
   .statusBarsPadding()
   .verticalScroll(rememberScrollState())
   .padding(horizontal = 24.dp)
 ) {
  Row(
   Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = onBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Player", tint = Color.White)
   }
   Column(Modifier.weight(1f).padding(start = 8.dp)) {
    Text("PARAMETRIC EQ", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    Text("10 BAND  •  REAL-TIME DSP", color = EqUltra, fontSize = 10.sp, letterSpacing = 1.4.sp)
   }
   TextButton(onClick = { onSettingsChanged(settings.copy(enabled = !settings.enabled)) }) {
    Text(if (settings.enabled) "EQ ON" else "ORIGINAL", color = if (settings.enabled) EqUltra else EqText)
   }
  }

  EqResponseGraph(
   settings = settings,
   selectedBand = selectedBand,
   onSelectedBandChanged = { selectedBand = it },
   onBandChanged = { index, band -> onSettingsChanged(settings.withBand(index, band)) }
  )

  Row(
   Modifier.fillMaxWidth().padding(top = 10.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   Column(Modifier.weight(1f)) {
    Text("그래프 숫자와 아래 BAND 번호가 같은 점입니다.", color = Color.White, fontSize = 10.sp)
    Text("좌우: 주파수  •  위아래: 강조/감소", color = EqText, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
   }
   TextButton(onClick = { onSettingsChanged(EqSettings()) }) {
    Text("RESET ALL", color = EqUltra, fontSize = 11.sp)
   }
  }

  Row(
   Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 16.dp),
   horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
   settings.bands.forEachIndexed { index, band ->
    TextButton(onClick = { selectedBand = index }) {
     Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
       (index + 1).toString().padStart(2, '0'),
       color = if (selectedBand == index) EqUltra else EqText,
       fontSize = 11.sp
      )
      Text(formatFrequency(band.frequencyHz), color = if (selectedBand == index) Color.White else EqText, fontSize = 9.sp)
      Text(bandRole(band.frequencyHz), color = EqText, fontSize = 8.sp)
     }
    }
   }
  }

  HorizontalDivider(color = EqHairline, thickness = 0.5.dp)
  val band = settings.bands[selectedBand]
  Row(
   Modifier.fillMaxWidth().padding(top = 12.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   Column(Modifier.weight(1f)) {
    Text(
     "BAND ${(selectedBand + 1).toString().padStart(2, '0')}  •  ${bandRole(band.frequencyHz)}",
     color = EqUltra,
     fontSize = 11.sp,
     letterSpacing = 1.2.sp
    )
    Text(bandRoleDescription(band.frequencyHz), color = EqText, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
   }
   TextButton(onClick = {
    onSettingsChanged(settings.withBand(selectedBand, EqSettings.defaultBands()[selectedBand]))
   }) {
    Text("RESET BAND", color = EqText, fontSize = 10.sp)
   }
  }

  EqSlider(
   label = "FREQUENCY",
   helpText = "어느 음역을 조절할지 선택",
   valueText = formatFrequency(band.frequencyHz),
   value = log10(band.frequencyHz),
   range = log10(20f)..log10(20_000f),
   onValueChanged = { onSettingsChanged(settings.withBand(selectedBand, band.copy(frequencyHz = 10f.pow(it)))) }
  )
  EqSlider(
   label = "GAIN",
   helpText = "선택한 음역을 키우거나 줄임",
   valueText = signedDb(band.gainDb),
   value = band.gainDb,
   range = -12f..12f,
   onValueChanged = { onSettingsChanged(settings.withBand(selectedBand, band.copy(gainDb = it))) }
  )
  EqSlider(
   label = "Q",
   helpText = "낮을수록 넓게, 높을수록 좁게 조절",
   valueText = String.format("%.2f", band.q),
   value = log10(band.q),
   range = log10(0.2f)..log10(10f),
   onValueChanged = { onSettingsChanged(settings.withBand(selectedBand, band.copy(q = 10f.pow(it)))) }
  )

  Row(
   Modifier.fillMaxWidth().clickable {
    onSettingsChanged(settings.withBand(selectedBand, band.copy(type = band.type.next())))
   }.padding(vertical = 18.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   Text("FILTER TYPE", color = EqText, fontSize = 11.sp, modifier = Modifier.weight(1f))
   Text(band.type.label, color = EqUltra, fontSize = 12.sp)
  }

  HorizontalDivider(color = EqHairline, thickness = 0.5.dp)
  Text("GAIN STAGING", color = EqUltra, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 20.dp))
  EqSlider(
   label = "PREAMP",
   helpText = "EQ 전체 출력 음량을 미리 조절",
   valueText = signedDb(settings.preampDb),
   value = settings.preampDb,
   range = -12f..6f,
   onValueChanged = { onSettingsChanged(settings.copy(preampDb = it)) }
  )
  if (settings.autoHeadroom) {
   Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text("AUTO HEADROOM", color = EqText, fontSize = 10.sp)
    Text("-${String.format("%.1f", settings.headroomDb)} dB", color = EqUltra, fontSize = 10.sp)
   }
  }
  EqToggle(
   label = "AUTO HEADROOM",
   detail = "Reduces preamp when boosts could clip",
   checked = settings.autoHeadroom,
   onToggle = { onSettingsChanged(settings.copy(autoHeadroom = !settings.autoHeadroom)) }
  )
  EqToggle(
   label = "OUTPUT LIMITER",
   detail = "Final peak protection",
   checked = settings.limiterEnabled,
   onToggle = { onSettingsChanged(settings.copy(limiterEnabled = !settings.limiterEnabled)) }
  )

  Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
   Text(
    "RESET ALL은 모든 점과 설정을 기본값으로 되돌립니다.",
    color = EqText,
    fontSize = 9.sp,
    modifier = Modifier.weight(1f).padding(top = 14.dp)
   )
   TextButton(onClick = { onSettingsChanged(settings.copy(enabled = !settings.enabled)) }) {
    Text(if (settings.enabled) "A/B: EQ" else "A/B: ORIGINAL", color = EqUltra, fontSize = 11.sp)
   }
  }
 }
}

@Composable
private fun EqResponseGraph(
 settings: EqSettings,
 selectedBand: Int,
 onSelectedBandChanged: (Int) -> Unit,
 onBandChanged: (Int, EqBand) -> Unit
) {
 val currentSettings by rememberUpdatedState(settings)
 val currentBandChanged by rememberUpdatedState(onBandChanged)
 val currentSelectionChanged by rememberUpdatedState(onSelectedBandChanged)
 val density = LocalDensity.current
 val pointLabelPaint = remember(density) {
  Paint(Paint.ANTI_ALIAS_FLAG).apply {
   textSize = with(density) { 9.sp.toPx() }
  }
 }
 Canvas(
  Modifier
   .fillMaxWidth()
   .height(230.dp)
   .background(Color(0xFF040814))
   .pointerInput(Unit) {
    var activeBand = selectedBand
    detectDragGestures(
     onDragStart = { position ->
      activeBand = currentSettings.bands.indices.minByOrNull { index ->
       val point = pointForBand(currentSettings.bands[index], size.width.toFloat(), size.height.toFloat())
       (point - position).getDistance()
      } ?: selectedBand
      currentSelectionChanged(activeBand)
     },
     onDrag = { change, _ ->
      change.consume()
      val frequency = xToFrequency(change.position.x, size.width.toFloat())
      val gain = yToGain(change.position.y, size.height.toFloat())
      currentBandChanged(
       activeBand,
       currentSettings.bands[activeBand].copy(frequencyHz = frequency, gainDb = gain)
      )
     }
    )
   }
 ) {
  val width = size.width
  val height = size.height
  listOf(-12f, -6f, 0f, 6f, 12f).forEach { gain ->
   val y = gainToY(gain, height)
   drawLine(
    color = if (gain == 0f) Color(0xFF303B63) else EqHairline,
    start = Offset(0f, y),
    end = Offset(width, y),
    strokeWidth = if (gain == 0f) 1.2f else 0.7f
   )
  }
  listOf(20f, 100f, 1_000f, 10_000f, 20_000f).forEach { frequency ->
   val x = frequencyToX(frequency, width)
   drawLine(EqHairline, Offset(x, 0f), Offset(x, height), strokeWidth = 0.7f)
  }

  val path = Path()
  val points = 180
  repeat(points) { index ->
   val x = index * width / (points - 1)
   val frequency = xToFrequency(x, width)
   val response = settings.responseDb(frequency.toDouble()).toFloat().coerceIn(-12f, 12f)
   val y = gainToY(response, height)
   if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  drawPath(path, color = if (settings.enabled) EqUltra else EqText, style = Stroke(width = 2f, cap = StrokeCap.Round))

  settings.bands.forEachIndexed { index, band ->
   val point = pointForBand(band, width, height)
   drawCircle(
    color = if (index == selectedBand) Color.White else EqUltra,
    radius = if (index == selectedBand) 8f else 5f,
    center = point
   )
   if (index == selectedBand) drawCircle(EqUltra, radius = 13f, center = point, style = Stroke(width = 1f))
   pointLabelPaint.color = (if (index == selectedBand) Color.White else EqText).toArgb()
   drawContext.canvas.nativeCanvas.drawText(
    (index + 1).toString().padStart(2, '0'),
    point.x.coerceIn(15f, width - 15f) - 7f,
    (point.y - 11f).coerceAtLeast(12f),
    pointLabelPaint
   )
  }
 }
}

@Composable
private fun EqSlider(
 label: String,
 helpText: String,
 valueText: String,
 value: Float,
 range: ClosedFloatingPointRange<Float>,
 onValueChanged: (Float) -> Unit
) {
 Column(Modifier.padding(top = 14.dp)) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {
    Text(label, color = Color.White, fontSize = 11.sp)
    Text(helpText, color = EqText, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
   }
   Text(valueText, color = Color.White, fontSize = 11.sp)
  }
  Slider(value = value, onValueChange = onValueChanged, valueRange = range, modifier = Modifier.fillMaxWidth())
 }
}

@Composable
private fun EqToggle(label: String, detail: String, checked: Boolean, onToggle: () -> Unit) {
 Row(
  Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 14.dp),
  verticalAlignment = Alignment.CenterVertically
 ) {
  Column(Modifier.weight(1f)) {
   Text(label, color = Color.White, fontSize = 12.sp)
   Text(detail, color = EqText, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
  }
  Box(
   Modifier.size(8.dp).background(if (checked) EqUltra else Color(0xFF303746), CircleShape)
  )
 }
}

private fun EqSettings.withBand(index: Int, band: EqBand): EqSettings = copy(
 bands = bands.toMutableList().also { it[index] = band }
)

private fun pointForBand(band: EqBand, width: Float, height: Float) = Offset(
 frequencyToX(band.frequencyHz, width),
 gainToY(band.gainDb, height)
)

private fun frequencyToX(frequency: Float, width: Float): Float =
 ((log10(frequency.coerceIn(20f, 20_000f)) - log10(20f)) / (log10(20_000f) - log10(20f))) * width

private fun xToFrequency(x: Float, width: Float): Float {
 val normalized = (x / width).coerceIn(0f, 1f)
 return 10f.pow(log10(20f) + normalized * (log10(20_000f) - log10(20f)))
}

private fun gainToY(gain: Float, height: Float): Float = height * (12f - gain.coerceIn(-12f, 12f)) / 24f

private fun yToGain(y: Float, height: Float): Float = (12f - (y / height).coerceIn(0f, 1f) * 24f)

private fun formatFrequency(frequency: Float): String = when {
 frequency >= 1_000f -> String.format("%.1fk", frequency / 1_000f).replace(".0k", "k")
 else -> "${frequency.roundToInt()} Hz"
}

private fun bandRole(frequency: Float): String = when {
 frequency < 60f -> "초저역"
 frequency < 150f -> "저역"
 frequency < 400f -> "저중역"
 frequency < 1_000f -> "중역"
 frequency < 2_500f -> "중고역"
 frequency < 6_000f -> "존재감"
 frequency < 12_000f -> "고역"
 else -> "공기감"
}

private fun bandRoleDescription(frequency: Float): String = when {
 frequency < 60f -> "킥의 깊이와 아주 낮은 베이스에 영향을 줍니다."
 frequency < 150f -> "베이스와 킥의 무게감에 영향을 줍니다."
 frequency < 400f -> "보컬과 악기의 두께·따뜻함에 영향을 줍니다."
 frequency < 1_000f -> "보컬과 기타의 중심적인 음색에 영향을 줍니다."
 frequency < 2_500f -> "보컬·기타의 선명함과 공격감에 영향을 줍니다."
 frequency < 6_000f -> "스네어 어택과 보컬의 존재감에 영향을 줍니다."
 frequency < 12_000f -> "심벌과 디테일, 밝기에 영향을 줍니다."
 else -> "공간감과 반짝이는 느낌에 영향을 줍니다."
}

private fun signedDb(value: Float): String = String.format(if (value >= 0f) "+%.1f dB" else "%.1f dB", value)
