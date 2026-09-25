package com.uplayer.app.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MusicGroup(
 val id: String,
 val name: String,
 val trackIds: Set<Long>
)

class MusicGroupRepository(context: Context) {
 private val preferences = context.getSharedPreferences("uplayer_music_groups", Context.MODE_PRIVATE)

 fun load(): List<MusicGroup> = runCatching {
  val groups = JSONArray(preferences.getString(KEY_GROUPS, "[]"))
  buildList {
   for (index in 0 until groups.length()) {
    val item = groups.getJSONObject(index)
    val ids = item.optJSONArray("trackIds") ?: JSONArray()
    add(
     MusicGroup(
      id = item.getString("id"),
      name = item.getString("name"),
      trackIds = buildSet {
       for (trackIndex in 0 until ids.length()) add(ids.getLong(trackIndex))
      }
     )
    )
   }
  }
 }.getOrDefault(emptyList())

 fun create(name: String, current: List<MusicGroup>): List<MusicGroup> {
  val cleanName = name.trim().take(40)
  if (cleanName.isEmpty()) return current
  return (current + MusicGroup(UUID.randomUUID().toString(), cleanName, emptySet())).also(::save)
 }

 fun update(group: MusicGroup, current: List<MusicGroup>): List<MusicGroup> =
  current.map { if (it.id == group.id) group else it }.also(::save)

 fun delete(groupId: String, current: List<MusicGroup>): List<MusicGroup> =
  current.filterNot { it.id == groupId }.also(::save)

 fun loadFavorites(): Set<Long> = preferences.getStringSet(KEY_FAVORITES, emptySet())
  .orEmpty()
  .mapNotNull(String::toLongOrNull)
  .toSet()

 fun toggleFavorite(trackId: Long, current: Set<Long>): Set<Long> {
  val updated = if (trackId in current) current - trackId else current + trackId
  preferences.edit().putStringSet(KEY_FAVORITES, updated.map(Long::toString).toSet()).apply()
  return updated
 }

 private fun save(groups: List<MusicGroup>) {
  val json = JSONArray()
  groups.forEach { group ->
   json.put(
    JSONObject()
     .put("id", group.id)
     .put("name", group.name)
     .put("trackIds", JSONArray(group.trackIds.toList()))
   )
  }
  preferences.edit().putString(KEY_GROUPS, json.toString()).apply()
 }

 private companion object {
  const val KEY_GROUPS = "groups"
  const val KEY_FAVORITES = "favorites"
 }
}
