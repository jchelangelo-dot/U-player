package com.uplayer.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.uplayer.app.library.AudioRepository
import com.uplayer.app.library.MusicGroup
import com.uplayer.app.library.MusicGroupRepository
import com.uplayer.app.library.Track
import com.uplayer.app.library.UserMusicFolderStore
import com.uplayer.app.dsp.EqCommand
import com.uplayer.app.dsp.EqScreen
import com.uplayer.app.dsp.EqSettings
import com.uplayer.app.dsp.EqSettingsStore
import com.uplayer.app.dsp.EqUserPresetStore
import com.uplayer.app.dsp.LiveStageCommand
import com.uplayer.app.dsp.LiveStageScreen
import com.uplayer.app.dsp.LiveStageSettings
import com.uplayer.app.dsp.LiveStageSettingsStore
import com.uplayer.app.lyrics.LyricsScreen
import com.uplayer.app.playback.PlaybackService
import com.uplayer.app.playback.PlaybackStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val AppBackground = Color(0xFF02040A)
private val Ultramarine = Color(0xFF315CFF)
private val SecondaryText = Color(0xFF7D8495)

private enum class AppScreen { LIBRARY, PLAYER, EQ, LIVE_STAGE, LYRICS }
private enum class LibrarySort(val label: String) { TITLE("TITLE"), ARTIST("ARTIST"), ALBUM("ALBUM") }
private enum class LibraryCategory(val label: String) { SONGS("SONGS"), ALBUMS("ALBUMS"), ARTISTS("ARTISTS") }

class MainActivity : ComponentActivity() {
 private var controller by mutableStateOf<MediaController?>(null)
 private var tracks by mutableStateOf<List<Track>>(emptyList())
 private var controllerFuture: ListenableFuture<MediaController>? = null
 private val folderStore by lazy { UserMusicFolderStore(applicationContext) }

 private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
  if (it) refreshLibrary()
 }

 private val folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
  uri ?: return@registerForActivityResult
  runCatching {
   contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  folderStore.add(uri)
  refreshLibrary()
 }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  connectController()
  requestAudioAndLoad()
  setContent { UPlayerApp(tracks, controller, onAddFolder = { folderLauncher.launch(null) }) }
 }

 override fun onResume() {
  super.onResume()
  if (hasAudioPermission()) refreshLibrary()
 }

 private fun connectController() {
  val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
  controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
   future.addListener({
    runCatching { future.get() }.onSuccess { controller = it }
   }, MoreExecutors.directExecutor())
  }
 }

 private fun audioPermission() =
  if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
  else Manifest.permission.READ_EXTERNAL_STORAGE

 private fun hasAudioPermission() =
  ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

 private fun requestAudioAndLoad() {
  if (hasAudioPermission()) refreshLibrary() else permissionLauncher.launch(audioPermission())
 }

 private fun refreshLibrary() {
  lifecycleScope.launch {
   tracks = withContext(Dispatchers.IO) {
    AudioRepository(applicationContext).loadTracks(folderStore.load())
   }
  }
 }

 override fun onDestroy() {
  controllerFuture?.let { MediaController.releaseFuture(it) }
  controllerFuture = null
  controller = null
  super.onDestroy()
 }
}

@Composable
private fun UPlayerApp(tracks: List<Track>, player: MediaController?, onAddFolder: () -> Unit) {
 var screen by remember { mutableStateOf(AppScreen.LIBRARY) }
 var focusOriginalItem by remember { mutableStateOf<MediaItem?>(null) }
 var focusSessionActive by remember { mutableStateOf(false) }
 val playback = rememberPlaybackState(player)
 val context = LocalContext.current
 val playbackStateStore = remember(context) { PlaybackStateStore(context.applicationContext) }
 var playbackRestored by remember(player) { mutableStateOf(false) }
 val eqStore = remember(context) { EqSettingsStore(context.applicationContext) }
 var eqSettings by remember(eqStore) { mutableStateOf(eqStore.load()) }
 val eqUserPresetStore = remember(context) { EqUserPresetStore(context.applicationContext) }
 var eqUserPresets by remember(eqUserPresetStore) { mutableStateOf(eqUserPresetStore.load()) }
 val liveStageStore = remember(context) { LiveStageSettingsStore(context.applicationContext) }
 var liveStageSettings by remember(liveStageStore) { mutableStateOf(liveStageStore.load()) }

 LaunchedEffect(player, tracks, playbackRestored) {
  if (player == null || tracks.isEmpty() || playbackRestored) return@LaunchedEffect
  if (player.currentMediaItem == null) {
   playbackStateStore.load()?.let { saved ->
    val tracksById = tracks.associateBy(Track::id)
    val queue = saved.mediaIds.mapNotNull(tracksById::get)
    if (queue.isNotEmpty()) {
     val restoredIndex = queue.indexOfFirst { it.id == saved.currentMediaId }.coerceAtLeast(0)
     player.setMediaItems(queue.map(Track::asMediaItem), restoredIndex, saved.positionMs)
     player.repeatMode = saved.repeatMode
     player.shuffleModeEnabled = saved.shuffle
     player.prepare()
     player.pause()
    }
   }
  }
  playbackRestored = true
 }

 LaunchedEffect(player, playback.hasMedia, focusSessionActive) {
  while (player != null && playback.hasMedia) {
   if (!focusSessionActive) playbackStateStore.save(player)
   delay(2_000L)
  }
 }

 fun updateEq(settings: EqSettings) {
  eqSettings = settings
  eqStore.save(settings)
  player?.sendCustomCommand(
   SessionCommand(EqCommand.ACTION_UPDATE, Bundle.EMPTY),
   EqCommand.toBundle(settings)
  )
 }

 fun updateLiveStage(settings: LiveStageSettings) {
  liveStageSettings = settings
  liveStageStore.save(settings)
  player?.sendCustomCommand(
   SessionCommand(LiveStageCommand.ACTION_UPDATE, Bundle.EMPTY),
   LiveStageCommand.toBundle(settings)
  )
 }

 BackHandler(enabled = screen == AppScreen.PLAYER) { screen = AppScreen.LIBRARY }

 MaterialTheme(
  colorScheme = darkColorScheme(
   primary = Ultramarine,
   background = AppBackground,
   surface = AppBackground
  )
 ) {
  Surface(Modifier.fillMaxSize(), color = AppBackground) {
   when (screen) {
    AppScreen.LYRICS -> LyricsScreen(
     trackId = playback.mediaId.orEmpty(),
     title = playback.title ?: "Unknown Track",
     artist = playback.artist ?: "Unknown Artist",
     positionMs = playback.positionMs,
     durationMs = playback.durationMs,
     onBack = { screen = AppScreen.PLAYER }
    )
    AppScreen.EQ -> EqScreen(
     settings = eqSettings,
     userPresets = eqUserPresets,
     onSettingsChanged = ::updateEq,
     onSaveUserPreset = { name, namedSettings ->
      updateEq(namedSettings)
      eqUserPresets = eqUserPresetStore.add(name, namedSettings, eqUserPresets)
     },
     onDeleteUserPreset = { id -> eqUserPresets = eqUserPresetStore.delete(id, eqUserPresets) },
     onBack = { screen = AppScreen.PLAYER }
    )
    AppScreen.LIVE_STAGE -> LiveStageScreen(
     settings = liveStageSettings,
     mediaUri = (if (focusSessionActive) focusOriginalItem else player?.currentMediaItem)?.localConfiguration?.uri,
     trackId = (focusOriginalItem ?: player?.currentMediaItem)?.mediaId.orEmpty(),
     sessionActive = focusSessionActive,
     onSettingsChanged = ::updateLiveStage,
     onPlaySession = { file ->
      val original = focusOriginalItem ?: player?.currentMediaItem
      original?.let { source ->
       if (!focusSessionActive) focusOriginalItem = source
        val sessionItem = MediaItem.Builder()
         .setMediaId("focus:${source.mediaId}")
         .setUri(Uri.fromFile(file))
         .setMediaMetadata(source.mediaMetadata)
         .build()
        replaceCurrentItem(player, sessionItem)
        focusSessionActive = true
      }
     },
     onPlayOriginal = {
      focusOriginalItem?.let { replaceCurrentItem(player, it) }
      focusSessionActive = false
     },
     onBack = { screen = AppScreen.PLAYER }
    )
    AppScreen.PLAYER -> PlayerScreen(
     player = player,
     playback = playback,
     eqEnabled = eqSettings.enabled,
     liveStageEnabled = liveStageSettings.enabled,
     onBack = { screen = AppScreen.LIBRARY },
     onOpenEq = { screen = AppScreen.EQ },
     onOpenLiveStage = {
      player?.currentMediaItem?.let {
       focusOriginalItem = if (focusSessionActive) focusOriginalItem else it
       screen = AppScreen.LIVE_STAGE
      }
     },
     onOpenLyrics = { if (playback.hasMedia) screen = AppScreen.LYRICS }
    )
    AppScreen.LIBRARY -> LibraryScreen(
      tracks = tracks,
      player = player,
      playback = playback,
      onAddFolder = onAddFolder,
      onTrackSelected = { queue, index ->
       player?.apply {
        setMediaItems(queue.map(Track::asMediaItem), index, 0L)
        prepare()
        play()
       }
       screen = AppScreen.PLAYER
      },
      onOpenPlayer = { if (player?.currentMediaItem != null) screen = AppScreen.PLAYER }
     )
   }
  }
 }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryScreen(
 tracks: List<Track>,
 player: Player?,
 playback: PlaybackUiState,
 onAddFolder: () -> Unit,
 onTrackSelected: (List<Track>, Int) -> Unit,
 onOpenPlayer: () -> Unit
) {
 val context = LocalContext.current
 val groupRepository = remember(context) { MusicGroupRepository(context.applicationContext) }
 var query by remember { mutableStateOf("") }
 var sort by remember { mutableStateOf(LibrarySort.TITLE) }
 var category by remember { mutableStateOf(LibraryCategory.SONGS) }
 var groups by remember { mutableStateOf(groupRepository.load()) }
 var favorites by remember { mutableStateOf(groupRepository.loadFavorites()) }
 var selectedGroupId by remember { mutableStateOf<String?>(null) }
 var showCreateGroup by remember { mutableStateOf(false) }
 var groupToEdit by remember { mutableStateOf<MusicGroup?>(null) }
 var groupToDelete by remember { mutableStateOf<MusicGroup?>(null) }
 var selectingSongs by remember { mutableStateOf(false) }
 var selectedTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
 var tracksToAdd by remember { mutableStateOf<Set<Long>>(emptySet()) }
 var showPlaylistPicker by remember { mutableStateOf(false) }
 val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
 val visibleTracks = remember(tracks, query, sort, category, selectedGroup, favorites) {
  val groupedTracks = when {
   selectedGroup != null -> tracks.filter { it.id in selectedGroup.trackIds }
   else -> tracks
  }
  groupedTracks
   .filter { track ->
    query.isBlank() || track.title.contains(query, ignoreCase = true) ||
     track.artist.contains(query, ignoreCase = true) || track.album.contains(query, ignoreCase = true) ||
     track.folder.contains(query, ignoreCase = true)
   }
   .let { filtered ->
    when (category) {
     LibraryCategory.ALBUMS -> filtered.sortedWith(compareBy<Track> { it.album.lowercase(Locale.getDefault()) }.thenBy { it.title.lowercase(Locale.getDefault()) })
     LibraryCategory.ARTISTS -> filtered.sortedWith(compareBy<Track> { it.artist.lowercase(Locale.getDefault()) }.thenBy { it.title.lowercase(Locale.getDefault()) })
     LibraryCategory.SONGS -> when (sort) {
      LibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
      LibrarySort.ARTIST -> filtered.sortedWith(compareBy<Track> { it.artist.lowercase(Locale.getDefault()) }.thenBy { it.title.lowercase(Locale.getDefault()) })
      LibrarySort.ALBUM -> filtered.sortedWith(compareBy<Track> { it.album.lowercase(Locale.getDefault()) }.thenBy { it.title.lowercase(Locale.getDefault()) })
     }
    }
   }
 }
 Column(Modifier.fillMaxSize().statusBarsPadding()) {
  Row(
   Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   Text("PLAYLISTS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
   if (selectingSongs) {
    TextButton(onClick = { selectingSongs = false; selectedTrackIds = emptySet() }) {
     Text("CANCEL", color = SecondaryText, fontSize = 8.sp)
    }
    TextButton(
     enabled = selectedTrackIds.isNotEmpty(),
     onClick = { tracksToAdd = selectedTrackIds; showPlaylistPicker = true }
    ) { Text("ADD ${selectedTrackIds.size}", color = if (selectedTrackIds.isEmpty()) SecondaryText else Ultramarine, fontSize = 8.sp) }
   } else {
    IconButton(onClick = onAddFolder, modifier = Modifier.size(36.dp)) {
     Icon(Icons.Default.CreateNewFolder, contentDescription = "Add music folder", tint = SecondaryText, modifier = Modifier.size(17.dp))
    }
    TextButton(
     onClick = {
      if (selectedGroup != null) groupToEdit = selectedGroup else selectingSongs = true
     },
     enabled = selectedGroup != null || category == LibraryCategory.SONGS
    ) { Text("EDIT", color = if (selectedGroup != null || category == LibraryCategory.SONGS) Ultramarine else SecondaryText, fontSize = 8.sp) }
   }
   Text("${tracks.size} TRACKS", color = Color(0xFF626979), fontSize = 8.sp, modifier = Modifier.padding(start = 6.dp))
  }
  Row(
   Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   Icon(Icons.Default.Search, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp))
   BasicTextField(
    value = query,
    onValueChange = { query = it },
    singleLine = true,
    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
    cursorBrush = SolidColor(Ultramarine),
    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
    decorationBox = { inner ->
     if (query.isBlank()) Text("곡, 아티스트 또는 앨범 검색", color = SecondaryText, fontSize = 12.sp)
     inner()
    }
   )
   if (query.isNotEmpty()) {
    IconButton(onClick = { query = "" }, modifier = Modifier.size(30.dp)) {
     Icon(Icons.Default.Close, contentDescription = "Clear search", tint = SecondaryText, modifier = Modifier.size(16.dp))
    }
   }
  }
  HorizontalDivider(color = Color(0xFF182038), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
  Row(
   Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   LibraryCategory.entries.forEach { option ->
    TextButton(onClick = { category = option; selectedGroupId = null; selectingSongs = false; selectedTrackIds = emptySet() }) {
     Text(option.label, color = if (category == option && selectedGroupId == null) Ultramarine else Color(0xFF626979), fontSize = 9.sp)
    }
   }
   groups.forEach { group ->
    TextButton(onClick = { selectedGroupId = group.id; category = LibraryCategory.SONGS; selectingSongs = false; selectedTrackIds = emptySet() }) {
     Text(group.name.uppercase(), color = if (selectedGroupId == group.id) Ultramarine else Color(0xFF626979), fontSize = 9.sp, maxLines = 1)
    }
   }
   TextButton(onClick = { showCreateGroup = true }) { Text("+", color = Ultramarine, fontSize = 13.sp) }
  }
  LazyColumn(Modifier.weight(1f).padding(top = 4.dp)) {
   itemsIndexed(visibleTracks, key = { _, track -> track.id }) { index, track ->
    val section = when (category) {
     LibraryCategory.ALBUMS -> track.album
     LibraryCategory.ARTISTS -> track.artist
     LibraryCategory.SONGS -> null
    }
    val previousSection = if (index > 0) when (category) {
     LibraryCategory.ALBUMS -> visibleTracks[index - 1].album
     LibraryCategory.ARTISTS -> visibleTracks[index - 1].artist
     LibraryCategory.SONGS -> null
    } else null
    if (section != null && section != previousSection) {
     Text(
      section.uppercase(),
      color = Ultramarine,
      fontSize = 10.sp,
      letterSpacing = 1.1.sp,
      modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
     )
    }
    Row(
     Modifier
      .fillMaxWidth()
      .combinedClickable(
       enabled = player != null || selectingSongs,
       onClick = {
        if (selectingSongs) {
         selectedTrackIds = if (track.id in selectedTrackIds) selectedTrackIds - track.id else selectedTrackIds + track.id
        } else {
         onTrackSelected(visibleTracks, index)
        }
       },
       onLongClick = {
        if (selectedGroup == null) {
         tracksToAdd = setOf(track.id)
         showPlaylistPicker = true
        }
       }
      )
      .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     if (selectingSongs) {
      Checkbox(
       checked = track.id in selectedTrackIds,
       onCheckedChange = { checked ->
        selectedTrackIds = if (checked) selectedTrackIds + track.id else selectedTrackIds - track.id
       }
      )
     }
     Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
      Text(
       track.title,
       color = if (player == null) SecondaryText else Color.White,
       maxLines = 1,
       overflow = TextOverflow.Ellipsis
      )
      Text("${track.artist}  ·  ${track.album}", color = SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
     }
     if (!selectingSongs) IconButton(onClick = { favorites = groupRepository.toggleFavorite(track.id, favorites) }) {
      Icon(
       if (track.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
       contentDescription = if (track.id in favorites) "Remove from favorites" else "Add to favorites",
       tint = if (track.id in favorites) Ultramarine else SecondaryText,
       modifier = Modifier.size(19.dp)
      )
     }
    }
   }
   if (visibleTracks.isEmpty() && tracks.isNotEmpty()) {
    item {
     Text(
      when {
       query.isNotBlank() -> "검색 결과가 없습니다."
       selectedGroup != null -> "아래의 곡 추가하기를 눌러 플레이리스트를 채워보세요."
       else -> "표시할 곡이 없습니다."
      },
      color = SecondaryText,
      fontSize = 12.sp,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
     )
    }
   }
  }
  if (selectedGroup != null) {
   TextButton(
    onClick = { groupToEdit = selectedGroup },
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
   ) {
    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Ultramarine, modifier = Modifier.size(17.dp))
    Text("곡 추가하기", color = Ultramarine, fontSize = 10.sp, modifier = Modifier.padding(start = 7.dp))
   }
  }
  MiniPlayer(player, playback, onOpenPlayer)
 }

 if (showCreateGroup) {
  CreateGroupDialog(
   onDismiss = { showCreateGroup = false },
   onCreate = { name ->
    var updated = groupRepository.create(name, groups)
    val created = updated.lastOrNull()
    if (created != null && tracksToAdd.isNotEmpty()) {
     updated = groupRepository.addTracks(created.id, tracksToAdd, updated)
    }
    groups = updated
    selectedGroupId = created?.id
    category = LibraryCategory.SONGS
    tracksToAdd = emptySet()
    showCreateGroup = false
   }
  )
 }
 if (showPlaylistPicker) {
  PlaylistPickerDialog(
   groups = groups,
   onDismiss = { showPlaylistPicker = false },
   onPlaylistSelected = { groupId ->
    groups = groupRepository.addTracks(groupId, tracksToAdd, groups)
    showPlaylistPicker = false
    selectingSongs = false
    selectedTrackIds = emptySet()
    tracksToAdd = emptySet()
   },
   onCreatePlaylist = {
    showPlaylistPicker = false
    showCreateGroup = true
   }
  )
 }
 groupToEdit?.let { group ->
  EditGroupTracksDialog(
   group = group,
   tracks = tracks,
   onDismiss = { groupToEdit = null },
   onSave = { trackIds ->
    groups = groupRepository.update(group.copy(trackIds = trackIds), groups)
    groupToEdit = null
   }
  )
 }
 groupToDelete?.let { group ->
  DeleteGroupDialog(
   group = group,
   onDismiss = { groupToDelete = null },
   onDelete = {
    groups = groupRepository.delete(group.id, groups)
    if (selectedGroupId == group.id) selectedGroupId = null
    groupToDelete = null
   }
  )
 }
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
 var name by remember { mutableStateOf("") }
 AlertDialog(
  onDismissRequest = onDismiss,
  title = { Text("새 플레이리스트", color = Color.White) },
  text = {
   OutlinedTextField(
    value = name,
    onValueChange = { if (it.length <= 40) name = it },
    label = { Text("플레이리스트 이름") },
    singleLine = true
   )
  },
  confirmButton = {
   TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
    Text("만들기", color = if (name.isNotBlank()) Ultramarine else SecondaryText)
   }
  },
  dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = SecondaryText) } },
  containerColor = Color(0xFF080C16)
 )
}

@Composable
private fun PlaylistPickerDialog(
 groups: List<MusicGroup>,
 onDismiss: () -> Unit,
 onPlaylistSelected: (String) -> Unit,
 onCreatePlaylist: () -> Unit
) {
 AlertDialog(
  onDismissRequest = onDismiss,
  title = { Text("플레이리스트에 추가", color = Color.White) },
  text = {
   Column {
    if (groups.isEmpty()) {
     Text("먼저 플레이리스트를 만들어주세요.", color = SecondaryText, fontSize = 11.sp)
    } else {
     groups.forEach { group ->
      Row(
       Modifier.fillMaxWidth().clickable { onPlaylistSelected(group.id) }.padding(vertical = 12.dp),
       verticalAlignment = Alignment.CenterVertically
      ) {
       Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Ultramarine, modifier = Modifier.size(18.dp))
       Text(group.name, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
      }
     }
    }
   }
  },
  confirmButton = { TextButton(onClick = onCreatePlaylist) { Text("새 플레이리스트", color = Ultramarine) } },
  dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = SecondaryText) } },
  containerColor = Color(0xFF080C16)
 )
}

@Composable
private fun EditGroupTracksDialog(
 group: MusicGroup,
 tracks: List<Track>,
 onDismiss: () -> Unit,
 onSave: (Set<Long>) -> Unit
) {
 var selectedIds by remember(group.id) { mutableStateOf(group.trackIds) }
 AlertDialog(
  onDismissRequest = onDismiss,
  title = { Text(group.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) },
  text = {
   Column {
    Text("플레이리스트에 넣을 곡을 선택하세요.", color = SecondaryText, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
     itemsIndexed(tracks, key = { _, track -> track.id }) { _, track ->
      Row(
       Modifier.fillMaxWidth().clickable {
        selectedIds = if (track.id in selectedIds) selectedIds - track.id else selectedIds + track.id
       }.padding(vertical = 5.dp),
       verticalAlignment = Alignment.CenterVertically
      ) {
       Checkbox(
        checked = track.id in selectedIds,
        onCheckedChange = { checked ->
         selectedIds = if (checked) selectedIds + track.id else selectedIds - track.id
        }
       )
       Column(Modifier.weight(1f)) {
        Text(track.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, color = SecondaryText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
       }
      }
     }
    }
   }
  },
  confirmButton = { TextButton(onClick = { onSave(selectedIds) }) { Text("저장", color = Ultramarine) } },
  dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = SecondaryText) } },
  containerColor = Color(0xFF080C16)
 )
}

@Composable
private fun DeleteGroupDialog(group: MusicGroup, onDismiss: () -> Unit, onDelete: () -> Unit) {
 AlertDialog(
  onDismissRequest = onDismiss,
  title = { Text("플레이리스트 삭제", color = Color.White) },
  text = { Text("‘${group.name}’ 플레이리스트를 삭제할까요? 음악 파일은 삭제되지 않습니다.", color = SecondaryText) },
  confirmButton = { TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFFF6B6B)) } },
  dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = SecondaryText) } },
  containerColor = Color(0xFF080C16)
 )
}

@Composable
private fun MiniPlayer(player: Player?, playback: PlaybackUiState, onOpenPlayer: () -> Unit) {
 val title = playback.title ?: if (player == null) "PLAYER CONNECTING" else "NO TRACK"
 val artist = playback.artist ?: if (player == null) "Please wait" else "Select music from Library"

 Row(
  Modifier
   .fillMaxWidth()
   .background(Color(0xFF060A14))
   .clickable(enabled = playback.hasMedia) { onOpenPlayer() }
   .padding(horizontal = 20.dp, vertical = 14.dp),
  verticalAlignment = Alignment.CenterVertically
 ) {
  Column(Modifier.weight(1f)) {
   Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
   Text(artist, color = SecondaryText, fontSize = 11.sp, maxLines = 1)
  }
  IconButton(
   enabled = player != null && playback.hasMedia,
   onClick = { togglePlayback(player) }
  ) {
   Icon(
    if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
    contentDescription = if (playback.isPlaying) "Pause" else "Play",
    tint = if (player == null || !playback.hasMedia) Color(0xFF343A48) else Ultramarine
   )
  }
 }
}

@Composable
private fun PlayerScreen(
 player: Player?,
 playback: PlaybackUiState,
 eqEnabled: Boolean,
 liveStageEnabled: Boolean,
 onBack: () -> Unit,
 onOpenEq: () -> Unit,
 onOpenLiveStage: () -> Unit,
 onOpenLyrics: () -> Unit
) {
 var showQueue by remember { mutableStateOf(false) }
 Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
  Row(
   Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = onBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Library", tint = Color.White)
   }
   Text(
    "NOW PLAYING",
    color = Color.White,
    fontSize = 14.sp,
    fontWeight = FontWeight.Normal,
    modifier = Modifier.weight(1f),
    textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
   Box(Modifier.size(48.dp))
  }

  AlbumArtwork(
   mediaUri = playback.mediaUri,
   title = playback.title,
   onClick = onOpenLyrics,
   enabled = playback.hasMedia
  )

  Column(Modifier.padding(top = 22.dp)) {
   Text(
    playback.title ?: "NO TRACK",
    color = Color.White,
    fontSize = 20.sp,
    fontWeight = FontWeight.Normal,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis
   )
   Text(
    playback.artist ?: "Unknown Artist",
    color = SecondaryText,
    fontSize = 12.sp,
    modifier = Modifier.padding(top = 6.dp),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
   )
  }

  Column(Modifier.padding(top = 18.dp)) {
   ThinPlaybackProgress(
    positionMs = playback.positionMs,
    durationMs = playback.durationMs,
    enabled = player != null && playback.durationMs > 0L,
    onSeek = { player?.seekTo(it) }
   )
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(formatTime(playback.positionMs), color = SecondaryText, fontSize = 11.sp)
    Text(formatTime(playback.durationMs), color = SecondaryText, fontSize = 11.sp)
   }
  }

  Row(
   Modifier.fillMaxWidth().padding(top = 18.dp),
   horizontalArrangement = Arrangement.SpaceEvenly,
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = { player?.seekToPreviousMediaItem() }, enabled = playback.hasPrevious) {
    Icon(
     Icons.Default.SkipPrevious,
     contentDescription = "Previous track",
     tint = controlColor(playback.hasPrevious),
     modifier = Modifier.size(25.dp)
    )
   }
   IconButton(
    onClick = { togglePlayback(player) },
    enabled = player != null && playback.hasMedia,
    modifier = Modifier.size(58.dp)
   ) {
    Icon(
     if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
     contentDescription = if (playback.isPlaying) "Pause" else "Play",
     tint = controlColor(player != null && playback.hasMedia),
     modifier = Modifier.size(42.dp)
    )
   }
   IconButton(onClick = { player?.seekToNextMediaItem() }, enabled = playback.hasNext) {
    Icon(
     Icons.Default.SkipNext,
     contentDescription = "Next track",
     tint = controlColor(playback.hasNext),
     modifier = Modifier.size(25.dp)
    )
   }
  }

  Row(
   Modifier.fillMaxWidth().padding(top = 12.dp),
   horizontalArrangement = Arrangement.SpaceBetween
  ) {
   IconButton(
    onClick = { player?.shuffleModeEnabled = playback.shuffle.not() },
    enabled = player != null && playback.hasMedia
   ) {
    Icon(
     Icons.Default.Shuffle,
     contentDescription = "Shuffle",
     tint = if (playback.shuffle) Ultramarine else SecondaryText
    )
   }
   IconButton(onClick = { showQueue = true }, enabled = player != null && playback.hasMedia) {
    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playback queue", tint = SecondaryText)
   }
   IconButton(
    onClick = { player?.repeatMode = nextRepeatMode(playback.repeatMode) },
    enabled = player != null && playback.hasMedia
   ) {
    Icon(
     if (playback.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
     contentDescription = "Repeat",
     tint = if (playback.repeatMode == Player.REPEAT_MODE_OFF) SecondaryText else Ultramarine
    )
   }
  }

  Row(
   Modifier.fillMaxWidth().padding(top = 10.dp),
   horizontalArrangement = Arrangement.SpaceEvenly,
   verticalAlignment = Alignment.CenterVertically
  ) {
   PlayerShortcut("EQ", Icons.Default.GraphicEq, active = eqEnabled, onClick = onOpenEq)
   PlayerShortcut("LIVE MIX", Icons.Default.SurroundSound, active = liveStageEnabled, onClick = onOpenLiveStage)
  }
 }
 if (showQueue && player != null) {
  PlaybackQueueDialog(player = player, onDismiss = { showQueue = false })
 }
}

@Composable
private fun PlayerShortcut(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
 Column(
  Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
 horizontalAlignment = Alignment.CenterHorizontally
 ) {
  Icon(icon, contentDescription = label, tint = if (active) Ultramarine else SecondaryText, modifier = Modifier.size(20.dp))
  Text(label, color = if (active) Color(0xFF8A91A3) else Color(0xFF626979), fontSize = 9.sp)
  if (active) Box(Modifier.padding(top = 5.dp).size(4.dp).background(Ultramarine, CircleShape))
 }
}

@Composable
private fun ThinPlaybackProgress(
 positionMs: Long,
 durationMs: Long,
 enabled: Boolean,
 onSeek: (Long) -> Unit
) {
 val safeDuration = durationMs.coerceAtLeast(1L)
 val fraction = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
 Canvas(
  Modifier
   .fillMaxWidth()
   .height(26.dp)
   .pointerInput(enabled, safeDuration) {
    if (!enabled) return@pointerInput
    detectTapGestures { offset -> onSeek((offset.x / size.width * safeDuration).toLong().coerceIn(0L, safeDuration)) }
   }
   .pointerInput(enabled, safeDuration) {
    if (!enabled) return@pointerInput
    detectHorizontalDragGestures { change, _ ->
     change.consume()
     onSeek((change.position.x / size.width * safeDuration).toLong().coerceIn(0L, safeDuration))
    }
   }
 ) {
  val y = size.height / 2f
  val playedX = size.width * fraction
  drawLine(Color(0xFF252B39), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
  if (playedX > 0f) {
   drawLine(
    brush = Brush.horizontalGradient(listOf(Ultramarine.copy(alpha = 0.12f), Ultramarine)),
    start = Offset(0f, y),
    end = Offset(playedX, y),
    strokeWidth = 1.2.dp.toPx()
   )
  }
  drawCircle(if (enabled) Color(0xFF69A1FF) else Color(0xFF343A48), 3.dp.toPx(), Offset(playedX, y))
 }
}

@Composable
private fun PlaybackQueueDialog(player: Player, onDismiss: () -> Unit) {
 var revision by remember { mutableLongStateOf(0L) }
 val items = remember(revision, player.mediaItemCount, player.currentMediaItemIndex) {
  List(player.mediaItemCount) { player.getMediaItemAt(it) }
 }
 AlertDialog(
  onDismissRequest = onDismiss,
  title = { Text("재생 대기열", color = Color.White) },
  text = {
   LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
    itemsIndexed(items) { index, item ->
     Row(
      Modifier.fillMaxWidth().clickable { player.seekToDefaultPosition(index) }.padding(vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically
     ) {
      Column(Modifier.weight(1f)) {
       Text(
        item.mediaMetadata.title?.toString() ?: "Unknown Track",
        color = if (index == player.currentMediaItemIndex) Ultramarine else Color.White,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
       )
       Text(item.mediaMetadata.artist?.toString() ?: "Unknown Artist", color = SecondaryText, fontSize = 10.sp, maxLines = 1)
      }
      IconButton(
       enabled = index > 0,
       onClick = { player.moveMediaItem(index, index - 1); revision++ }
      ) {
       Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = if (index > 0) SecondaryText else Color(0xFF343A48), modifier = Modifier.size(17.dp))
      }
      IconButton(
       enabled = index < items.lastIndex,
       onClick = { player.moveMediaItem(index, index + 1); revision++ }
      ) {
       Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = if (index < items.lastIndex) SecondaryText else Color(0xFF343A48), modifier = Modifier.size(17.dp))
      }
     }
    }
   }
  },
  confirmButton = { TextButton(onClick = onDismiss) { Text("완료", color = Ultramarine) } },
  containerColor = Color(0xFF080C16)
 )
}

@Composable
private fun AlbumArtwork(
 mediaUri: Uri?,
 title: String?,
 onClick: () -> Unit,
 enabled: Boolean
) {
 val context = LocalContext.current
 val artwork by produceState<ImageBitmap?>(initialValue = null, mediaUri) {
  value = withContext(Dispatchers.IO) { loadEmbeddedArtwork(context, mediaUri) }
 }
 Box(
  Modifier.fillMaxWidth().aspectRatio(1.18f).background(Color(0xFF050A17)).clickable(enabled = enabled, onClick = onClick),
  contentAlignment = Alignment.Center
 ) {
  if (artwork != null) {
   Image(
    bitmap = artwork!!,
    contentDescription = title?.let { "$it album art" } ?: "Album art",
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxSize()
   )
  } else {
   Box(
    Modifier.fillMaxSize().padding(1.dp).background(
     Brush.radialGradient(listOf(Color(0xFF10265F), Color(0xFF050A17), AppBackground))
    ),
    contentAlignment = Alignment.Center
   ) {
    Icon(
     Icons.Default.GraphicEq,
     contentDescription = "Album art placeholder",
     tint = Ultramarine,
     modifier = Modifier.size(54.dp)
    )
   }
  }
  if (enabled) {
   Box(
    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.48f)).padding(vertical = 10.dp),
    contentAlignment = Alignment.Center
   ) {
    Text("TAP FOR LYRICS", color = Color.White, fontSize = 9.sp, letterSpacing = 1.4.sp)
   }
  }
 }
}

private fun loadEmbeddedArtwork(context: Context, mediaUri: Uri?): ImageBitmap? {
 if (mediaUri == null) return null
 val bytes = runCatching {
  val retriever = MediaMetadataRetriever()
  try {
   retriever.setDataSource(context, mediaUri)
   retriever.embeddedPicture
  } finally {
   retriever.release()
  }
 }.getOrNull() ?: return null

 val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
 BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
 var sampleSize = 1
 while (bounds.outWidth / sampleSize > 1_200 || bounds.outHeight / sampleSize > 1_200) {
  sampleSize *= 2
 }
 return BitmapFactory.decodeByteArray(
  bytes,
  0,
  bytes.size,
  BitmapFactory.Options().apply { inSampleSize = sampleSize }
 )?.asImageBitmap()
}

private data class PlaybackUiState(
 val mediaId: String? = null,
 val mediaUri: Uri? = null,
 val title: String? = null,
 val artist: String? = null,
 val isPlaying: Boolean = false,
 val hasMedia: Boolean = false,
 val positionMs: Long = 0L,
 val durationMs: Long = 0L,
 val hasPrevious: Boolean = false,
 val hasNext: Boolean = false,
 val repeatMode: Int = Player.REPEAT_MODE_OFF,
 val shuffle: Boolean = false
)

@Composable
private fun rememberPlaybackState(player: Player?): PlaybackUiState {
 var state by remember(player) { mutableStateOf(player.toPlaybackUiState()) }
 var position by remember(player) { mutableLongStateOf(player.safePosition()) }

 DisposableEffect(player) {
  val listener = object : Player.Listener {
   override fun onEvents(player: Player, events: Player.Events) {
    state = player.toPlaybackUiState()
    position = player.safePosition()
   }
  }
  player?.addListener(listener)
  state = player.toPlaybackUiState()
  position = player.safePosition()
  onDispose { player?.removeListener(listener) }
 }

 LaunchedEffect(player, state.hasMedia, state.isPlaying) {
  while (player != null && state.hasMedia) {
   position = player.safePosition()
   if (player.duration != C.TIME_UNSET && player.duration >= 0L && player.duration != state.durationMs) {
    state = player.toPlaybackUiState()
   }
   delay(if (state.isPlaying) 250L else 750L)
  }
 }

 return state.copy(positionMs = position)
}

private fun Player?.toPlaybackUiState(): PlaybackUiState {
 if (this == null) return PlaybackUiState()
 val item = currentMediaItem
 return PlaybackUiState(
 mediaId = item?.mediaId,
  mediaUri = item?.localConfiguration?.uri,
  title = item?.mediaMetadata?.title?.toString(),
  artist = item?.mediaMetadata?.artist?.toString(),
  isPlaying = isPlaying,
  hasMedia = item != null,
  positionMs = safePosition(),
  durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L,
  hasPrevious = hasPreviousMediaItem(),
  hasNext = hasNextMediaItem(),
  repeatMode = repeatMode,
  shuffle = shuffleModeEnabled
 )
}

private fun Player?.safePosition(): Long = this?.currentPosition?.coerceAtLeast(0L) ?: 0L

private fun togglePlayback(player: Player?) {
 if (player?.isPlaying == true) player.pause() else player?.play()
}

private fun replaceCurrentItem(player: Player?, item: MediaItem) {
 if (player == null || player.mediaItemCount == 0) return
 val position = player.currentPosition.coerceAtLeast(0L)
 val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
 val wasPlaying = player.isPlaying
 val queue = List(player.mediaItemCount) { index ->
  if (index == currentIndex) item else player.getMediaItemAt(index)
 }
 player.setMediaItems(queue, currentIndex, position)
 player.prepare()
 if (wasPlaying) player.play()
}

private fun nextRepeatMode(current: Int) = when (current) {
 Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
 Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
 else -> Player.REPEAT_MODE_OFF
}

private fun controlColor(enabled: Boolean) = if (enabled) Ultramarine else Color(0xFF343A48)

private fun formatTime(milliseconds: Long): String {
 val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
 val minutes = totalSeconds / 60L
 val seconds = totalSeconds % 60L
 return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
