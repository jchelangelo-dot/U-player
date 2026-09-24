package com.uplayer.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.uplayer.app.library.AudioRepository
import com.uplayer.app.library.Track
import com.uplayer.app.dsp.EqCommand
import com.uplayer.app.dsp.EqScreen
import com.uplayer.app.dsp.EqSettings
import com.uplayer.app.dsp.EqSettingsStore
import com.uplayer.app.lyrics.LyricsScreen
import com.uplayer.app.playback.PlaybackService
import kotlinx.coroutines.delay
import java.util.Locale

private val AppBackground = Color(0xFF02040A)
private val Ultramarine = Color(0xFF315CFF)
private val SecondaryText = Color(0xFF7D8495)

private enum class AppScreen { LIBRARY, PLAYER, EQ, LYRICS }

class MainActivity : ComponentActivity() {
 private var controller by mutableStateOf<MediaController?>(null)
 private var tracks by mutableStateOf<List<Track>>(emptyList())
 private var controllerFuture: ListenableFuture<MediaController>? = null

 private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
  if (it) refreshLibrary()
 }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  connectController()
  requestAudioAndLoad()
  setContent { UPlayerApp(tracks, controller, ::refreshLibrary) }
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
  tracks = AudioRepository(this).loadTracks()
 }

 override fun onDestroy() {
  controllerFuture?.let { MediaController.releaseFuture(it) }
  controllerFuture = null
  controller = null
  super.onDestroy()
 }
}

@Composable
private fun UPlayerApp(tracks: List<Track>, player: MediaController?, onRefresh: () -> Unit) {
 var screen by remember { mutableStateOf(AppScreen.LIBRARY) }
 val playback = rememberPlaybackState(player)
 val context = LocalContext.current
 val eqStore = remember(context) { EqSettingsStore(context.applicationContext) }
 var eqSettings by remember(eqStore) { mutableStateOf(eqStore.load()) }

 fun updateEq(settings: EqSettings) {
  eqSettings = settings
  eqStore.save(settings)
  player?.sendCustomCommand(
   SessionCommand(EqCommand.ACTION_UPDATE, Bundle.EMPTY),
   EqCommand.toBundle(settings)
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
     onSettingsChanged = ::updateEq,
     onBack = { screen = AppScreen.PLAYER }
    )
    AppScreen.PLAYER -> PlayerScreen(
     player = player,
     playback = playback,
     eqEnabled = eqSettings.enabled,
     onBack = { screen = AppScreen.LIBRARY },
     onOpenEq = { screen = AppScreen.EQ },
     onOpenLyrics = { if (playback.hasMedia) screen = AppScreen.LYRICS }
    )
    AppScreen.LIBRARY -> LibraryScreen(
      tracks = tracks,
      player = player,
      playback = playback,
      onRefresh = onRefresh,
      onTrackSelected = { index ->
       player?.apply {
        setMediaItems(tracks.map(Track::asMediaItem), index, 0L)
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

@Composable
private fun LibraryScreen(
 tracks: List<Track>,
 player: Player?,
 playback: PlaybackUiState,
 onRefresh: () -> Unit,
 onTrackSelected: (Int) -> Unit,
 onOpenPlayer: () -> Unit
) {
 Column(Modifier.fillMaxSize().statusBarsPadding()) {
  Row(
   Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   Text("U-player", color = Color.White, fontSize = 26.sp, modifier = Modifier.weight(1f))
   TextButton(onClick = onRefresh) { Text("REFRESH", color = Ultramarine, fontSize = 11.sp) }
  }
  Text(
   if (player == null) "CONNECTING PLAYER..." else "LIBRARY  •  ${tracks.size} TRACKS",
   color = Ultramarine,
   fontSize = 11.sp,
   modifier = Modifier.padding(horizontal = 24.dp)
  )
  LazyColumn(Modifier.weight(1f).padding(top = 12.dp)) {
   itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
    Column(
     Modifier
      .fillMaxWidth()
      .clickable(enabled = player != null) { onTrackSelected(index) }
      .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
     Text(
      track.title,
      color = if (player == null) SecondaryText else Color.White,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
     )
     Text(track.artist, color = SecondaryText, fontSize = 12.sp, maxLines = 1)
    }
   }
  }
  MiniPlayer(player, playback, onOpenPlayer)
 }
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
 onBack: () -> Unit,
 onOpenEq: () -> Unit,
 onOpenLyrics: () -> Unit
) {
 Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
  Row(
   Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 22.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = onBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Library", tint = Color.White)
   }
   Text(
    "NOW PLAYING",
    color = Ultramarine,
    fontSize = 11.sp,
    letterSpacing = 1.8.sp,
    modifier = Modifier.padding(start = 8.dp).weight(1f)
    )
   TextButton(onClick = onOpenEq) {
    Text(if (eqEnabled) "EQ ON" else "EQ OFF", color = if (eqEnabled) Ultramarine else SecondaryText, fontSize = 11.sp)
   }
  }

  AlbumArtPlaceholder(onClick = onOpenLyrics, enabled = playback.hasMedia)

  Column(Modifier.padding(top = 34.dp)) {
   Text(
    playback.title ?: "NO TRACK",
    color = Color.White,
    fontSize = 25.sp,
    fontWeight = FontWeight.Medium,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis
   )
   Text(
    playback.artist ?: "Unknown Artist",
    color = SecondaryText,
    fontSize = 14.sp,
    modifier = Modifier.padding(top = 8.dp),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
   )
  }

  Column(Modifier.padding(top = 28.dp)) {
   Slider(
    value = playback.positionMs.coerceIn(0L, playback.durationMs.coerceAtLeast(1L)).toFloat(),
    onValueChange = { player?.seekTo(it.toLong()) },
    valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat(),
    enabled = player != null && playback.durationMs > 0L,
    modifier = Modifier.fillMaxWidth()
   )
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(formatTime(playback.positionMs), color = SecondaryText, fontSize = 11.sp)
    Text(formatTime(playback.durationMs), color = SecondaryText, fontSize = 11.sp)
   }
  }

  Row(
   Modifier.fillMaxWidth().padding(top = 28.dp),
   horizontalArrangement = Arrangement.SpaceEvenly,
   verticalAlignment = Alignment.CenterVertically
  ) {
   IconButton(onClick = { player?.seekToPreviousMediaItem() }, enabled = playback.hasPrevious) {
    Icon(
     Icons.Default.SkipPrevious,
     contentDescription = "Previous track",
     tint = controlColor(playback.hasPrevious),
     modifier = Modifier.size(31.dp)
    )
   }
   IconButton(
    onClick = { togglePlayback(player) },
    enabled = player != null && playback.hasMedia,
    modifier = Modifier.size(68.dp)
   ) {
    Icon(
     if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
     contentDescription = if (playback.isPlaying) "Pause" else "Play",
     tint = controlColor(player != null && playback.hasMedia),
     modifier = Modifier.size(50.dp)
    )
   }
   IconButton(onClick = { player?.seekToNextMediaItem() }, enabled = playback.hasNext) {
    Icon(
     Icons.Default.SkipNext,
     contentDescription = "Next track",
     tint = controlColor(playback.hasNext),
     modifier = Modifier.size(31.dp)
    )
   }
  }

  Row(
   Modifier.fillMaxWidth().padding(top = 24.dp),
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
 }
}

@Composable
private fun AlbumArtPlaceholder(onClick: () -> Unit, enabled: Boolean) {
 Box(
  Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF07133F)).clickable(enabled = enabled, onClick = onClick),
  contentAlignment = Alignment.Center
 ) {
  Box(
   Modifier.fillMaxSize().padding(1.dp).background(Color(0xFF091A59)),
   contentAlignment = Alignment.Center
  ) {
   Icon(
    Icons.Default.GraphicEq,
    contentDescription = "Album art placeholder",
    tint = Ultramarine,
    modifier = Modifier.size(84.dp)
   )
   Text(
    "TAP FOR LYRICS",
    color = Ultramarine,
    fontSize = 9.sp,
    letterSpacing = 1.4.sp,
    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
   )
  }
 }
}

private data class PlaybackUiState(
 val mediaId: String? = null,
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
