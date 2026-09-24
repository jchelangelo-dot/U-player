package com.uplayer.app.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import java.util.Locale

private val LyricsBackground = Color(0xFF02040A)
private val LyricsUltra = Color(0xFF315CFF)
private val LyricsSecondary = Color(0xFF7D8495)
private val LyricsHairline = Color(0xFF182038)

private enum class LyricsDisplayMode(val label: String) {
 ALL("전체"),
 ORIGINAL_READING("원문+독음"),
 ORIGINAL_TRANSLATION("원문+번역"),
 ORIGINAL("원문")
}

@Composable
fun LyricsScreen(
 trackId: String,
 title: String,
 artist: String,
 positionMs: Long,
 durationMs: Long,
 onBack: () -> Unit
) {
 val context = LocalContext.current
 val repository = remember(context) { LyricsRepository(context.applicationContext) }
 var document by remember(trackId) { mutableStateOf(repository.load(trackId) ?: LyricsDocument()) }
 var editing by remember(trackId) { mutableStateOf(document.isEmpty) }
 var combinedLyrics by remember(trackId) { mutableStateOf(document.toCombinedText()) }
 var displayMode by remember { mutableStateOf(LyricsDisplayMode.ALL) }
 var readerFontSize by remember { mutableFloatStateOf(repository.loadFontSizeSp()) }

 BackHandler(onBack = onBack)

 Column(Modifier.fillMaxSize().background(LyricsBackground).statusBarsPadding()) {
  Row(
   Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = onBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Player", tint = Color.White)
   }
   Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    Text(artist, color = LyricsSecondary, fontSize = 10.sp, maxLines = 1)
   }
   TextButton(onClick = {
    if (editing) {
     document = parseCombinedLyrics(combinedLyrics)
     repository.save(trackId, document)
    } else {
     combinedLyrics = document.toCombinedText()
    }
    editing = !editing
   }) {
    Text(if (editing) "SAVE" else "EDIT", color = LyricsUltra, fontSize = 11.sp)
   }
  }
  HorizontalDivider(color = LyricsHairline, thickness = 0.5.dp)

  if (editing) {
   LyricsEditor(
    value = combinedLyrics,
    onValueChanged = { combinedLyrics = it }
   )
  } else {
   Row(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp)
   ) {
    LyricsDisplayMode.entries.forEach { mode ->
     TextButton(onClick = { displayMode = mode }) {
      Text(mode.label, color = if (displayMode == mode) Color.White else LyricsSecondary, fontSize = 10.sp)
     }
    }
   }
   Row(
    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    verticalAlignment = Alignment.CenterVertically
   ) {
    Text("TEXT SIZE", color = LyricsSecondary, fontSize = 9.sp, modifier = Modifier.weight(1f))
    TextButton(onClick = {
     readerFontSize = (readerFontSize - 2f).coerceAtLeast(12f)
     repository.saveFontSizeSp(readerFontSize)
    }) {
     Text("−", color = LyricsUltra, fontSize = 18.sp)
    }
    Text("${readerFontSize.roundToInt()}", color = Color.White, fontSize = 10.sp)
    TextButton(onClick = {
     readerFontSize = (readerFontSize + 2f).coerceAtMost(26f)
     repository.saveFontSizeSp(readerFontSize)
    }) {
     Text("+", color = LyricsUltra, fontSize = 18.sp)
    }
   }
   LyricsReader(document, displayMode, readerFontSize, positionMs, durationMs)
  }
 }
}

@Composable
private fun LyricsEditor(value: String, onValueChanged: (String) -> Unit) {
 val nonEmptyLineCount = value.lines().count { it.isNotBlank() }
 val completeGroups = nonEmptyLineCount / 3
 val remainingLines = nonEmptyLineCount % 3
 Column(
  Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
  verticalArrangement = Arrangement.spacedBy(24.dp)
 ) {
  Text("COMBINED LYRICS", color = LyricsUltra, fontSize = 10.sp, letterSpacing = 1.2.sp)
  Text("원문 → 한글 독음 → 한국어 번역 순서로 전체 가사를 붙여넣으세요.", color = Color.White, fontSize = 11.sp)
  Text("빈 줄은 자동으로 무시하고 3줄씩 한 묶음으로 나눕니다.", color = LyricsSecondary, fontSize = 10.sp)
  Text("선택: 원문 앞에 [00:12.50]을 붙이면 해당 시간에 정확히 맞춰집니다.", color = LyricsSecondary, fontSize = 10.sp)
  Text(
   if (remainingLines == 0) "${completeGroups}개 가사 묶음 인식" else "${completeGroups}개 묶음 인식 · 마지막 ${remainingLines}줄 확인 필요",
   color = if (remainingLines == 0) LyricsUltra else Color(0xFFFFB86B),
   fontSize = 10.sp
  )
  LyricsTextEditor(
   label = "PASTE ALL",
   hint = "深い闇解き放って…\n후카이 야미 토키하낫테…\n깊은 어둠을 떨쳐버리고…\n\n強く果てない未来へ\n츠요쿠 하테나이 미라이에\n강하고 끝없는 미래로",
   value = value,
   onValueChanged = onValueChanged
  )
 }
}

@Composable
private fun LyricsTextEditor(
 label: String,
 hint: String,
 value: String,
 onValueChanged: (String) -> Unit
) {
 Column {
  Text(label, color = LyricsUltra, fontSize = 10.sp, letterSpacing = 1.2.sp)
  BasicTextField(
   value = value,
   onValueChange = onValueChanged,
   textStyle = TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 22.sp),
   cursorBrush = SolidColor(LyricsUltra),
   modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp).padding(top = 10.dp),
   decorationBox = { innerTextField ->
    if (value.isEmpty()) Text(hint, color = LyricsSecondary, fontSize = 12.sp)
    innerTextField()
   }
  )
  HorizontalDivider(color = LyricsHairline, thickness = 0.5.dp)
 }
}

private data class LyricsRow(
 val original: String,
 val pronunciation: String,
 val translation: String,
 val timestampMs: Long
)

@Composable
private fun LyricsReader(
 document: LyricsDocument,
 mode: LyricsDisplayMode,
 fontSize: Float,
 positionMs: Long,
 durationMs: Long
) {
 val rows = remember(document) { document.toRows() }
 val currentIndex = remember(rows, positionMs, durationMs) {
  currentLineIndex(rows, positionMs, durationMs)
 }
 val listState = rememberLazyListState()
 LaunchedEffect(currentIndex) {
  if (currentIndex >= 0) listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
 }
 if (rows.all { it.original.isBlank() && it.pronunciation.isBlank() && it.translation.isBlank() }) {
  Column(
   Modifier.fillMaxSize().padding(32.dp),
   horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.Center
  ) {
   Text("NO SAVED LYRICS", color = Color.White, fontSize = 18.sp)
   Text("EDIT에서 원문·독음·번역을 입력할 수 있습니다.", color = LyricsSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
  }
  return
 }

 LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
  itemsIndexed(rows) { index, row ->
   Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
    Box(
     Modifier
      .width(3.dp)
      .fillMaxHeight()
      .background(if (index == currentIndex) LyricsUltra else Color.Transparent)
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
    if (mode != LyricsDisplayMode.ORIGINAL_TRANSLATION || row.original.isNotBlank()) {
     Text(row.original, color = Color.White, fontSize = fontSize.sp, lineHeight = (fontSize + 7f).sp)
    }
    if ((mode == LyricsDisplayMode.ALL || mode == LyricsDisplayMode.ORIGINAL_READING) && row.pronunciation.isNotBlank()) {
     Text(
      row.pronunciation,
      color = LyricsUltra,
      fontSize = (fontSize - 3f).coerceAtLeast(10f).sp,
      lineHeight = (fontSize + 3f).sp,
      modifier = Modifier.padding(top = 5.dp)
     )
    }
    if ((mode == LyricsDisplayMode.ALL || mode == LyricsDisplayMode.ORIGINAL_TRANSLATION) && row.translation.isNotBlank()) {
     Text(
      row.translation,
      color = LyricsSecondary,
      fontSize = (fontSize - 2f).coerceAtLeast(10f).sp,
      lineHeight = (fontSize + 4f).sp,
      modifier = Modifier.padding(top = 5.dp)
     )
    }
   }
   }
  }
 }
}

private fun LyricsDocument.toRows(): List<LyricsRow> {
 val originals = original.lines()
 val readings = pronunciation.lines()
 val translations = translation.lines()
 val count = maxOf(originals.size, readings.size, translations.size)
 return List(count) { index ->
  LyricsRow(
   original = originals.getOrElse(index) { "" },
   pronunciation = readings.getOrElse(index) { "" },
   translation = translations.getOrElse(index) { "" },
   timestampMs = timestampsMs.getOrElse(index) { -1L }
  )
 }
}

private fun parseCombinedLyrics(text: String): LyricsDocument {
 val lines = text.replace("\r\n", "\n").lines().map(String::trim).filter(String::isNotEmpty)
 val groups = lines.chunked(3)
 val parsedOriginals = groups.map { parseTimestamp(it.getOrElse(0) { "" }) }
 return LyricsDocument(
  original = parsedOriginals.joinToString("\n") { it.second },
  pronunciation = groups.joinToString("\n") { it.getOrElse(1) { "" } },
  translation = groups.joinToString("\n") { it.getOrElse(2) { "" } },
  timestampsMs = parsedOriginals.map { it.first }
 )
}

private fun LyricsDocument.toCombinedText(): String = toRows()
 .filterNot { it.original.isBlank() && it.pronunciation.isBlank() && it.translation.isBlank() }
 .joinToString("\n\n") { row ->
  val original = if (row.timestampMs >= 0L) "${formatTimestamp(row.timestampMs)} ${row.original}" else row.original
  listOf(original, row.pronunciation, row.translation).joinToString("\n")
 }

private val timestampPattern = Regex("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]\\s*(.*)$")

private fun parseTimestamp(line: String): Pair<Long, String> {
 val match = timestampPattern.matchEntire(line) ?: return -1L to line
 val minutes = match.groupValues[1].toLongOrNull() ?: return -1L to line
 val seconds = match.groupValues[2].toLongOrNull() ?: return -1L to line
 val fractionText = match.groupValues[3]
 val milliseconds = when (fractionText.length) {
  1 -> fractionText.toLong() * 100L
  2 -> fractionText.toLong() * 10L
  3 -> fractionText.toLong()
  else -> 0L
 }
 return (minutes * 60_000L + seconds * 1_000L + milliseconds) to match.groupValues[4]
}

private fun formatTimestamp(milliseconds: Long): String {
 val totalSeconds = milliseconds / 1_000L
 val minutes = totalSeconds / 60L
 val seconds = totalSeconds % 60L
 val hundredths = (milliseconds % 1_000L) / 10L
 return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
}

private fun currentLineIndex(rows: List<LyricsRow>, positionMs: Long, durationMs: Long): Int {
 if (rows.isEmpty()) return -1
 val timedRows = rows.withIndex().filter { it.value.timestampMs >= 0L }
 if (timedRows.isNotEmpty()) {
  return timedRows.lastOrNull { it.value.timestampMs <= positionMs }?.index ?: timedRows.first().index
 }
 if (durationMs <= 0L) return 0
 val progress = (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 0.999999)
 return (progress * rows.size).toInt().coerceIn(0, rows.lastIndex)
}
