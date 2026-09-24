package com.uplayer.app.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(val id: Long, val title: String, val artist: String, val album: String, val durationMs: Long, val uri: Uri) {
 fun asMediaItem() = MediaItem.Builder().setMediaId(id.toString()).setUri(uri)
  .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build()).build()
}

class AudioRepository(private val context: Context) {
 fun loadTracks(): List<Track> {
  val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
  return context.contentResolver.query(collection, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { c ->
   val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
   val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
   val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
   val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
   val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
   buildList {
    while (c.moveToNext()) {
     val trackId = c.getLong(id)
     add(Track(trackId, c.getString(title) ?: "Unknown", c.getString(artist) ?: "Unknown Artist", c.getString(album) ?: "Unknown Album", c.getLong(duration), ContentUris.withAppendedId(collection, trackId)))
    }
   }
  } ?: emptyList()
 }
}