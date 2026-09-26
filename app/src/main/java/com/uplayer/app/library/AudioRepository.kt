package com.uplayer.app.library

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.Locale

data class Track(
 val id: Long,
 val title: String,
 val artist: String,
 val album: String,
 val folder: String,
 val durationMs: Long,
 val uri: Uri
) {
 fun asMediaItem() = MediaItem.Builder().setMediaId(id.toString()).setUri(uri)
  .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build()).build()
}

class AudioRepository(private val context: Context) {
 fun loadTracks(extraFolders: List<Uri> = emptyList()): List<Track> =
  (loadMediaStoreTracks() + extraFolders.flatMap(::loadDocumentTreeTracks))
   .distinctBy(Track::duplicateKey)
   .sortedBy { it.title.lowercase() }

 private fun loadMediaStoreTracks(): List<Track> {
  val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
   MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
  } else {
   MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  }
  val projection = arrayOf(
   MediaStore.Audio.Media._ID,
   MediaStore.Audio.Media.TITLE,
   MediaStore.Audio.Media.DISPLAY_NAME,
   MediaStore.Audio.Media.ARTIST,
   MediaStore.Audio.Media.ALBUM,
   MediaStore.Audio.Media.RELATIVE_PATH,
   MediaStore.Audio.Media.DURATION,
   MediaStore.Audio.Media.IS_MUSIC
  )
  val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
  return context.contentResolver.query(
   collection,
   projection,
   selection,
   arrayOf(MIN_MUSIC_DURATION_MS.toString()),
   MediaStore.Audio.Media.DISPLAY_NAME + " COLLATE NOCASE ASC"
  )?.use { cursor ->
   val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
   val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
   val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
   val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
   val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
   val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
   val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
   buildList {
    while (cursor.moveToNext()) {
     val relativePath = cursor.getString(pathColumn).orEmpty()
     if (relativePath.isExcludedAutomaticAudioPath()) continue
     val trackId = cursor.getLong(idColumn)
     add(
      Track(
       id = trackId,
       title = cursor.getString(titleColumn)?.takeIf(String::isNotBlank)
        ?: cursor.getString(displayNameColumn)?.substringBeforeLast('.')
        ?: "Unknown",
       artist = cursor.getString(artistColumn)?.takeIf(String::isNotBlank) ?: "Unknown Artist",
       album = cursor.getString(albumColumn)?.takeIf(String::isNotBlank) ?: "Unknown Album",
       folder = relativePath.trimEnd('/').substringAfterLast('/').takeIf(String::isNotBlank) ?: "Device",
       durationMs = cursor.getLong(durationColumn),
       uri = ContentUris.withAppendedId(collection, trackId)
      )
     )
    }
   }
  }.orEmpty()
 }

 private fun loadDocumentTreeTracks(treeUri: Uri): List<Track> = runCatching {
  val rootId = DocumentsContract.getTreeDocumentId(treeUri)
  val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
  val result = mutableListOf<Track>()
  scanDocumentDirectory(treeUri, root, "Selected Folder", result)
  result
 }.getOrDefault(emptyList())

 private fun scanDocumentDirectory(treeUri: Uri, directoryUri: Uri, folderName: String, output: MutableList<Track>) {
  val directoryId = DocumentsContract.getDocumentId(directoryUri)
  val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
  val projection = arrayOf(
   DocumentsContract.Document.COLUMN_DOCUMENT_ID,
   DocumentsContract.Document.COLUMN_DISPLAY_NAME,
   DocumentsContract.Document.COLUMN_MIME_TYPE
  )
  context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
   val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
   val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
   val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
   while (cursor.moveToNext()) {
    val documentId = cursor.getString(idColumn)
    val displayName = cursor.getString(nameColumn).orEmpty()
    val mimeType = cursor.getString(mimeColumn).orEmpty()
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
     scanDocumentDirectory(treeUri, documentUri, displayName.ifBlank { folderName }, output)
    } else if (mimeType.startsWith("audio/") || displayName.hasAudioExtension()) {
     readDocumentTrack(documentUri, displayName, folderName)?.let(output::add)
    }
   }
  }
 }

 private fun readDocumentTrack(uri: Uri, displayName: String, folderName: String): Track? = runCatching {
  val retriever = MediaMetadataRetriever()
  try {
   retriever.setDataSource(context, uri)
   val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
   if (duration < MIN_SELECTED_FOLDER_DURATION_MS) return null
   Track(
    id = stableFolderTrackId(uri),
    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
     ?: displayName.substringBeforeLast('.').ifBlank { "Unknown" },
    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank)
     ?: "Unknown Artist",
    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf(String::isNotBlank)
     ?: "Selected Folder",
    folder = folderName,
    durationMs = duration,
    uri = uri
   )
  } finally {
   retriever.release()
  }
 }.getOrNull()

 private fun String.isExcludedAutomaticAudioPath(): Boolean {
  val path = lowercase().replace('\\', '/')
  return EXCLUDED_PATH_PARTS.any(path::contains)
 }

 private fun String.hasAudioExtension(): Boolean = AUDIO_EXTENSIONS.any { lowercase().endsWith(it) }

 private fun stableFolderTrackId(uri: Uri): Long {
  var hash = 1_125_899_906_842_597L
  uri.toString().forEach { hash = 31L * hash + it.code }
  return hash or Long.MIN_VALUE
 }

 private companion object {
  const val MIN_MUSIC_DURATION_MS = 30_000L
  const val MIN_SELECTED_FOLDER_DURATION_MS = 5_000L
  val EXCLUDED_PATH_PARTS = listOf(
   "/ringtones/", "/notifications/", "/alarms/", "/ui/", "/system/",
   "android/data/", "android/media/", "/cache/", "/sound_effects/"
  )
  val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".aac", ".flac", ".ogg", ".wav", ".opus")
 }
}

private fun Track.duplicateKey(): String = listOf(
 title.trim().lowercase(Locale.ROOT),
 artist.trim().lowercase(Locale.ROOT),
 album.trim().lowercase(Locale.ROOT),
 (durationMs / 1_000L).toString()
).joinToString("\u0000")
