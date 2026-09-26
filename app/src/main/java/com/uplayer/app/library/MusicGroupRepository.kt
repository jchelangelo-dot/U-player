package com.uplayer.app.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MusicGroup(
 val id: String,
 val name: String,
 val trackIds: List<Long>
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
      trackIds = buildList {
       for (trackIndex in 0 until ids.length()) add(ids.getLong(trackIndex))
      }.distinct()
     )
    )
   }
  }
 }.getOrDefault(emptyList())

 fun create(name: String, current: List<MusicGroup>): List<MusicGroup> {
  val cleanName = name.trim().take(40)
  if (cleanName.isEmpty()) return current
  return (current + MusicGroup(UUID.randomUUID().toString(), cleanName, emptyList())).also(::save)
 }

 fun update(group: MusicGroup, current: List<MusicGroup>): List<MusicGroup> =
  current.map { if (it.id == group.id) group else it }.also(::save)

 fun addTracks(groupId: String, trackIds: Collection<Long>, current: List<MusicGroup>): List<MusicGroup> =
  current.map { group ->
   if (group.id == groupId) group.copy(trackIds = (group.trackIds + trackIds).distinct()) else group
  }.also(::save)

 fun rename(groupId: String, name: String, current: List<MusicGroup>): List<MusicGroup> {
  val cleanName = name.trim().take(40)
  if (cleanName.isEmpty()) return current
  return current.map { if (it.id == groupId) it.copy(name = cleanName) else it }.also(::save)
 }

 fun moveTrack(groupId: String, fromIndex: Int, toIndex: Int, current: List<MusicGroup>): List<MusicGroup> =
  current.map { group ->
   if (group.id != groupId || fromIndex !in group.trackIds.indices || toIndex !in group.trackIds.indices) group
   else group.copy(trackIds = group.trackIds.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
  }.also(::save)

 fun removeTrack(groupId: String, trackId: Long, current: List<MusicGroup>): List<MusicGroup> =
  current.map { group ->
   if (group.id == groupId) group.copy(trackIds = group.trackIds.filterNot { it == trackId }) else group
  }.also(::save)

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
