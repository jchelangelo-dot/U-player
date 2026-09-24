package com.uplayer.app.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
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
 onBack: () -> Unit
) {
 val context = LocalContext.current
 val repository = remember(context) { LyricsRepository(context.applicationContext) }
 var document by remember(trackId) { mutableStateOf(repository.load(trackId) ?: LyricsDocument()) }
 var editing by remember(trackId) { mutableStateOf(document.isEmpty) }
 var displayMode by remember { mutableStateOf(LyricsDisplayMode.ALL) }

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
    if (editing) repository.save(trackId, document)
    editing = !editing
   }) {
    Text(if (editing) "SAVE" else "EDIT", color = LyricsUltra, fontSize = 11.sp)
   }
  }
  HorizontalDivider(color = LyricsHairline, thickness = 0.5.dp)

  if (editing) {
   LyricsEditor(
    document = document,
    onDocumentChanged = { document = it }
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
   LyricsReader(document, displayMode)
  }
 }
}

@Composable
private fun LyricsEditor(document: LyricsDocument, onDocumentChanged: (LyricsDocument) -> Unit) {
 Column(
  Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
  verticalArrangement = Arrangement.spacedBy(24.dp)
 ) {
  Text("각 영역의 줄 순서를 맞추면 가사 화면에서 한 묶음으로 표시됩니다.", color = LyricsSecondary, fontSize = 10.sp)
  LyricsTextEditor(
   label = "JAPANESE ORIGINAL",
   hint = "일본어 원문 가사를 붙여넣으세요.",
   value = document.original,
   onValueChanged = { onDocumentChanged(document.copy(original = it)) }
  )
  LyricsTextEditor(
   label = "KOREAN READING",
   hint = "한글 독음을 같은 줄 순서로 입력하세요.",
   value = document.pronunciation,
   onValueChanged = { onDocumentChanged(document.copy(pronunciation = it)) }
  )
  LyricsTextEditor(
   label = "KOREAN TRANSLATION",
   hint = "한국어 번역을 같은 줄 순서로 입력하세요.",
   value = document.translation,
   onValueChanged = { onDocumentChanged(document.copy(translation = it)) }
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
   modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 10.dp),
   decorationBox = { innerTextField ->
    if (value.isEmpty()) Text(hint, color = LyricsSecondary, fontSize = 12.sp)
    innerTextField()
   }
  )
  HorizontalDivider(color = LyricsHairline, thickness = 0.5.dp)
 }
}

private data class LyricsRow(val original: String, val pronunciation: String, val translation: String)

@Composable
private fun LyricsReader(document: LyricsDocument, mode: LyricsDisplayMode) {
 val rows = remember(document) { document.toRows() }
 if (rows.all { it.original.isBlank() && it.pronunciation.isBlank() && it.translation.isBlank() }) {
  Column(
   Modifier.fillMaxSize().clickable { }.padding(32.dp),
   horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.Center
  ) {
   Text("NO SAVED LYRICS", color = Color.White, fontSize = 18.sp)
   Text("EDIT에서 원문·독음·번역을 입력할 수 있습니다.", color = LyricsSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
  }
  return
 }

 LazyColumn(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
  items(rows) { row ->
   Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
    if (mode != LyricsDisplayMode.ORIGINAL_TRANSLATION || row.original.isNotBlank()) {
     Text(row.original, color = Color.White, fontSize = 20.sp, lineHeight = 28.sp)
    }
    if ((mode == LyricsDisplayMode.ALL || mode == LyricsDisplayMode.ORIGINAL_READING) && row.pronunciation.isNotBlank()) {
     Text(row.pronunciation, color = LyricsUltra, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp))
    }
    if ((mode == LyricsDisplayMode.ALL || mode == LyricsDisplayMode.ORIGINAL_TRANSLATION) && row.translation.isNotBlank()) {
     Text(row.translation, color = LyricsSecondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 5.dp))
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
   translation = translations.getOrElse(index) { "" }
  )
 }
}
