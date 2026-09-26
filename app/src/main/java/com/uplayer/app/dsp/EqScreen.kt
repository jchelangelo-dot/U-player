package com.uplayer.app.dsp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.hypot

private val V2Background = Color(0xFF02040A)
private val V2Ultra = Color(0xFF315CFF)
private val V2Label = Color(0xFF8A91A3)
private val V2Muted = Color(0xFF626979)
private val V2Grid = Color(0xFF182038)

@Composable
fun EqScreen(
 settings: EqSettings,
 userPresets: List<EqUserPreset>,
 onSettingsChanged: (EqSettings) -> Unit,
 onSaveUserPreset: (String, EqSettings) -> Unit,
 onDeleteUserPreset: (String) -> Unit,
 onBack: () -> Unit
) {
 var selectedBand by remember { mutableIntStateOf(0) }
 var presetMenu by remember { mutableStateOf(false) }
 var showSave by remember { mutableStateOf(false) }
 var presetName by remember { mutableStateOf("") }
 BackHandler(onBack = onBack)

 Column(
  Modifier.fillMaxSize().background(V2Background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
 ) {
  Row(
   Modifier.fillMaxWidth().padding(vertical = 2.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Player", tint = V2Label) }
   Text("EQUALIZER", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
   Switch(checked = settings.enabled, onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) }, modifier = Modifier.scale(0.72f))
  }

  Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
   Box(Modifier.weight(1f)) {
    Text(
     settings.displayName ?: settings.preset.label,
     color = V2Ultra,
     fontSize = 9.sp,
     modifier = Modifier.clickable { presetMenu = true }.padding(vertical = 10.dp)
    )
    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
     EqPreset.entries.forEach { preset ->
      DropdownMenuItem(
       text = { Text(preset.label, color = if (preset == settings.preset) V2Ultra else V2Label, fontSize = 10.sp) },
       onClick = { onSettingsChanged(preset.settings()); presetMenu = false }
      )
     }
     userPresets.forEach { preset ->
      DropdownMenuItem(
       text = { Text(preset.name, color = if (settings.displayName == preset.name) V2Ultra else V2Label, fontSize = 10.sp) },
       onClick = { onSettingsChanged(preset.settings.copy(displayName = preset.name)); presetMenu = false }
      )
     }
    }
   }
   Text("SAVE", color = V2Muted, fontSize = 8.sp, modifier = Modifier.clickable { showSave = true }.padding(10.dp))
   Text("RESET", color = V2Muted, fontSize = 8.sp, modifier = Modifier.clickable { onSettingsChanged(EqSettings()) }.padding(10.dp))
  }

  CompactEqGraph(
   settings = settings,
   selectedBand = selectedBand,
   onSelected = { selectedBand = it },
   onGainChanged = { index, gain ->
    onSettingsChanged(settings.withBandV2(index, settings.bands[index].copy(gainDb = gain)))
   }
  )

  val band = settings.bands[selectedBand]
  val definition = EqSettings.bandDefinitions[selectedBand]
  Row(
   Modifier.fillMaxWidth().padding(top = 12.dp),
   horizontalArrangement = Arrangement.SpaceEvenly
  ) {
   RotaryKnob("FREQ", compactFrequency(band.frequencyHz), band.frequencyHz, definition.frequencyRange, {
    onSettingsChanged(settings.withBandV2(selectedBand, band.copy(frequencyHz = it)))
   }, Modifier.weight(1f), 68.dp)
   RotaryKnob("GAIN", signedDbV2(band.gainDb), band.gainDb, -12f..12f, {
    onSettingsChanged(settings.withBandV2(selectedBand, band.copy(gainDb = it)))
   }, Modifier.weight(1f), 68.dp)
   RotaryKnob("Q", String.format(Locale.US, "%.1f", band.q), log10(band.q), log10(0.2f)..log10(10f), {
    onSettingsChanged(settings.withBandV2(selectedBand, band.copy(q = 10f.pow(it))))
   }, Modifier.weight(1f), 68.dp)
   RotaryKnob("PREAMP", signedDbV2(settings.preampDb), settings.preampDb, -12f..6f, {
    onSettingsChanged(settings.copy(preampDb = it).customized())
   }, Modifier.weight(1f), 68.dp)
  }

  Row(
   Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
   horizontalArrangement = Arrangement.SpaceBetween,
   verticalAlignment = Alignment.CenterVertically
  ) {
   Text("${definition.shortLabel} · ${definition.role}", color = V2Muted, fontSize = 8.sp)
   Text(
    band.type.label,
    color = V2Label,
    fontSize = 8.sp,
    modifier = Modifier.clickable { onSettingsChanged(settings.withBandV2(selectedBand, band.copy(type = band.type.next()))) }.padding(8.dp)
   )
  }

  Column(
   Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
   horizontalAlignment = Alignment.CenterHorizontally
  ) {
   Row(verticalAlignment = Alignment.CenterVertically) {
    Text("LIMITER", color = V2Label, fontSize = 9.sp)
    Switch(
     checked = settings.limiterEnabled,
     onCheckedChange = { onSettingsChanged(settings.copy(limiterEnabled = it).customized()) },
     modifier = Modifier.padding(start = 8.dp).scale(0.72f)
    )
   }
   Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(4.dp).background(Color(0xFF52D98A), androidx.compose.foundation.shape.CircleShape))
    Text("OUTPUT SAFELY LIMITED", color = V2Muted, fontSize = 7.sp, modifier = Modifier.padding(start = 6.dp))
   }
  }
 }

 if (showSave) {
  AlertDialog(
   onDismissRequest = { showSave = false },
   title = { Text("EQ 프리셋 저장") },
   text = { OutlinedTextField(presetName, { if (it.length <= 32) presetName = it }, singleLine = true) },
   confirmButton = {
    TextButton(
     enabled = presetName.isNotBlank(),
     onClick = {
      val name = presetName.trim()
      onSaveUserPreset(name, settings.copy(displayName = name))
      showSave = false
     }
    ) { Text("저장") }
   },
   dismissButton = { TextButton(onClick = { showSave = false }) { Text("취소") } },
   containerColor = Color(0xFF080C16)
  )
 }
}

@Composable
private fun CompactEqGraph(
 settings: EqSettings,
 selectedBand: Int,
 onSelected: (Int) -> Unit,
 onGainChanged: (Int, Float) -> Unit
) {
 val bands = settings.bands
 val currentBands = rememberUpdatedState(bands)
 val currentSelected = rememberUpdatedState(onSelected)
 val currentGainChanged = rememberUpdatedState(onGainChanged)
 Canvas(
  Modifier
   .fillMaxWidth()
   .height(230.dp)
   .padding(vertical = 12.dp)
   .pointerInput(Unit) {
    detectTapGestures { tap ->
     nearestBandIndex(tap, currentBands.value, size.width.toFloat(), size.height.toFloat(), 30.dp.toPx())?.let(currentSelected.value)
    }
   }
   .pointerInput(Unit) {
    var dragIndex = -1
    var dragGain = 0f
    detectDragGestures(
     onDragStart = { start ->
      val latestBands = currentBands.value
      dragIndex = nearestBandIndex(start, latestBands, size.width.toFloat(), size.height.toFloat(), 34.dp.toPx()) ?: -1
      if (dragIndex >= 0) {
       dragGain = latestBands[dragIndex].gainDb
       currentSelected.value(dragIndex)
      }
     },
     onDrag = { change, dragAmount ->
      if (dragIndex < 0) return@detectDragGestures
      change.consume()
      dragGain = (dragGain - dragAmount.y / size.height * 24f).coerceIn(-12f, 12f)
      currentGainChanged.value(dragIndex, dragGain)
     }
    )
   }
 ) {
  repeat(7) { index ->
   val x = size.width * index / 6f
   drawLine(V2Grid, Offset(x, 0f), Offset(x, size.height), 0.5.dp.toPx())
  }
  repeat(5) { index ->
   val y = size.height * index / 4f
   drawLine(V2Grid, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx())
  }
  val points = bands.map { graphPoint(it, size.width, size.height) }
  val path = Path().apply {
   points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
  }
  drawPath(path, V2Ultra, style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
  points.forEachIndexed { index, point ->
   drawCircle(if (index == selectedBand) Color(0xFF69A1FF) else V2Ultra, if (index == selectedBand) 4.dp.toPx() else 2.5.dp.toPx(), point)
  }
 }
 Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
  bands.forEachIndexed { index, _ ->
   Text(
    EqSettings.bandDefinitions[index].shortLabel,
    color = if (index == selectedBand) V2Ultra else V2Muted,
    fontSize = 7.sp,
    modifier = Modifier.clickable { onSelected(index) }.padding(5.dp)
   )
  }
 }
}

private fun EqSettings.withBandV2(index: Int, band: EqBand) = copy(
 preset = EqPreset.FLAT,
 displayName = "CUSTOM",
 bands = bands.toMutableList().also { it[index] = band }
)

private fun EqSettings.customized() = copy(preset = EqPreset.FLAT, displayName = "CUSTOM")

private fun graphPoint(band: EqBand, width: Float, height: Float): Offset {
 val x = ((log10(band.frequencyHz) - log10(20f)) / (log10(20_000f) - log10(20f))) * width
 val y = height * (12f - band.gainDb.coerceIn(-12f, 12f)) / 24f
 return Offset(x, y)
}

private fun nearestBandIndex(
 point: Offset,
 bands: List<EqBand>,
 width: Float,
 height: Float,
 threshold: Float
): Int? = bands.indices
 .map { index -> index to graphPoint(bands[index], width, height) }
 .minByOrNull { (_, bandPoint) -> hypot(point.x - bandPoint.x, point.y - bandPoint.y) }
 ?.takeIf { (_, bandPoint) -> hypot(point.x - bandPoint.x, point.y - bandPoint.y) <= threshold }
 ?.first

private fun compactFrequency(value: Float) = if (value >= 1_000f) String.format(Locale.US, "%.1fk", value / 1_000f) else "${value.toInt()} Hz"
private fun signedDbV2(value: Float) = String.format(Locale.US, if (value >= 0f) "+%.1f dB" else "%.1f dB", value)
