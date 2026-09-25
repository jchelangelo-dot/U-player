package com.uplayer.app.playback

import android.content.Context
import androidx.media3.common.Player

data class SavedPlaybackState(
 val mediaIds: List<Long>,
 val currentMediaId: Long,
 val positionMs: Long,
 val repeatMode: Int,
 val shuffle: Boolean
)

class PlaybackStateStore(context: Context) {
 private val preferences = context.getSharedPreferences("uplayer_playback_state", Context.MODE_PRIVATE)

 fun load(): SavedPlaybackState? {
  val ids = preferences.getString("media_ids", "")
   .orEmpty()
   .split(',')
   .mapNotNull(String::toLongOrNull)
  if (ids.isEmpty()) return null
  return SavedPlaybackState(
   mediaIds = ids,
   currentMediaId = preferences.getLong("current_media_id", ids.first()),
   positionMs = preferences.getLong("position_ms", 0L).coerceAtLeast(0L),
   repeatMode = preferences.getInt("repeat_mode", Player.REPEAT_MODE_OFF),
   shuffle = preferences.getBoolean("shuffle", false)
  )
 }

 fun save(player: Player) {
  if (player.mediaItemCount == 0) return
  val ids = buildList {
   for (index in 0 until player.mediaItemCount) {
    player.getMediaItemAt(index).mediaId.toLongOrNull()?.let(::add)
   }
  }
  if (ids.isEmpty()) return
  preferences.edit()
   .putString("media_ids", ids.joinToString(","))
   .putLong("current_media_id", player.currentMediaItem?.mediaId?.toLongOrNull() ?: ids.first())
   .putLong("position_ms", player.currentPosition.coerceAtLeast(0L))
   .putInt("repeat_mode", player.repeatMode)
   .putBoolean("shuffle", player.shuffleModeEnabled)
   .apply()
 }
}
