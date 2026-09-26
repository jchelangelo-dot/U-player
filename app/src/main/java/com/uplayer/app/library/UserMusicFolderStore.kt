package com.uplayer.app.library

import android.content.Context
import android.net.Uri

class UserMusicFolderStore(context: Context) {
 private val preferences = context.getSharedPreferences("uplayer_music_folders", Context.MODE_PRIVATE)

 fun load(): List<Uri> = preferences.getStringSet(KEY_FOLDERS, emptySet())
  .orEmpty()
  .map(Uri::parse)
  .sortedBy(Uri::toString)

 fun add(uri: Uri): List<Uri> {
  val updated = (load() + uri).distinctBy(Uri::toString)
  preferences.edit().putStringSet(KEY_FOLDERS, updated.map(Uri::toString).toSet()).apply()
  return updated
 }

 fun remove(uri: Uri): List<Uri> {
  val updated = load().filterNot { it == uri }
  preferences.edit().putStringSet(KEY_FOLDERS, updated.map(Uri::toString).toSet()).apply()
  return updated
 }

 private companion object {
  const val KEY_FOLDERS = "folders"
 }
}
