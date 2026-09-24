package com.uplayer.app.lyrics

import android.content.Context

data class LyricsDocument(
 val original: String = "",
 val pronunciation: String = "",
 val translation: String = ""
) {
 val isEmpty: Boolean get() = original.isBlank() && pronunciation.isBlank() && translation.isBlank()
}

interface LyricsProvider {
 suspend fun find(title: String, artist: String): String?
}

interface PronunciationProvider {
 suspend fun createKoreanReading(japaneseLyrics: String): String
}

interface TranslationProvider {
 suspend fun translateToKorean(originalLyrics: String): String
}

class LyricsRepository(context: Context) {
 private val preferences = context.getSharedPreferences("uplayer_lyrics", Context.MODE_PRIVATE)

 fun load(trackId: String): LyricsDocument? {
  if (!preferences.contains(key(trackId, "original"))) return null
  return LyricsDocument(
   original = preferences.getString(key(trackId, "original"), "").orEmpty(),
   pronunciation = preferences.getString(key(trackId, "pronunciation"), "").orEmpty(),
   translation = preferences.getString(key(trackId, "translation"), "").orEmpty()
  )
 }

 fun save(trackId: String, document: LyricsDocument) {
  preferences.edit()
   .putString(key(trackId, "original"), document.original)
   .putString(key(trackId, "pronunciation"), document.pronunciation)
   .putString(key(trackId, "translation"), document.translation)
   .apply()
 }

 fun loadFontSizeSp(): Float = preferences.getFloat("reader_font_size", 16f).coerceIn(12f, 26f)

 fun saveFontSizeSp(size: Float) {
  preferences.edit().putFloat("reader_font_size", size.coerceIn(12f, 26f)).apply()
 }

 private fun key(trackId: String, field: String) = "track_${trackId}_$field"
}
