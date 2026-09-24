package com.uplayer.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.uplayer.app.library.AudioRepository
import com.uplayer.app.library.Track
import com.uplayer.app.playback.PlaybackService

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

@Composable private fun UPlayerApp(tracks: List<Track>, player: Player?, onRefresh: () -> Unit) {
 val bg=Color(0xFF02040A); val ultra=Color(0xFF315CFF)
 var selectedTrack by remember { mutableStateOf<Track?>(null) }

 MaterialTheme(colorScheme=darkColorScheme(primary=ultra,background=bg,surface=bg)) {
  Surface(Modifier.fillMaxSize(),color=bg) {
   Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(
     Modifier.fillMaxWidth().padding(horizontal=24.dp, vertical=18.dp),
     verticalAlignment=Alignment.CenterVertically
    ) {
     Text("U-player",color=Color.White,fontSize=26.sp,modifier=Modifier.weight(1f))
     TextButton(onClick=onRefresh) { Text("REFRESH",color=ultra,fontSize=11.sp) }
    }
    Text(
     if (player == null) "CONNECTING PLAYER..." else "LIBRARY  •  " + tracks.size + " TRACKS",
     color=ultra,fontSize=11.sp,modifier=Modifier.padding(horizontal=24.dp)
    )
    LazyColumn(Modifier.weight(1f).padding(top=12.dp)) {
     items(tracks,key={it.id}) { track ->
      Column(Modifier.fillMaxWidth().clickable(enabled = player != null) {
       selectedTrack = track
       player?.apply {
        val item: MediaItem = track.asMediaItem()
        setMediaItem(item)
        prepare()
        play()
       }
      }.padding(horizontal=24.dp,vertical=12.dp)) {
       Text(track.title,color=if(player==null) Color(0xFF7D8495) else Color.White,maxLines=1)
       Text(track.artist,color=Color(0xFF7D8495),fontSize=12.sp,maxLines=1)
      }
     }
    }
    MiniPlayer(player,selectedTrack,ultra)
   }
  }
 }
}

@Composable private fun MiniPlayer(player: Player?, selectedTrack: Track?, ultra: Color) {
 var playing by remember { mutableStateOf(false) }
 var currentTitle by remember { mutableStateOf<String?>(null) }
 var currentArtist by remember { mutableStateOf<String?>(null) }

 DisposableEffect(player) {
  val listener=object:Player.Listener {
   override fun onIsPlayingChanged(isPlaying:Boolean){ playing=isPlaying }
   override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    currentTitle=mediaItem?.mediaMetadata?.title?.toString()
    currentArtist=mediaItem?.mediaMetadata?.artist?.toString()
   }
  }
  player?.addListener(listener)
  playing=player?.isPlaying==true
  currentTitle=player?.currentMediaItem?.mediaMetadata?.title?.toString()
  currentArtist=player?.currentMediaItem?.mediaMetadata?.artist?.toString()
  onDispose { player?.removeListener(listener) }
 }

 val title = currentTitle ?: selectedTrack?.title ?: if(player==null) "PLAYER CONNECTING" else "NO TRACK"
 val artist = currentArtist ?: selectedTrack?.artist ?: if(player==null) "Please wait" else "Select music from Library"

 Row(Modifier.fillMaxWidth().background(Color(0xFF060A14)).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
  Column(Modifier.weight(1f)) {
   Text(title,color=Color.White,maxLines=1)
   Text(artist,color=Color(0xFF72798A),fontSize=11.sp,maxLines=1)
  }
  IconButton(enabled=player!=null,onClick={if(player?.isPlaying==true)player.pause() else player?.play()}) {
   Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=if(player==null)Color(0xFF343A48) else ultra)
  }
 }
}