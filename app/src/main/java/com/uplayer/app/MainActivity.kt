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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.uplayer.app.library.AudioRepository
import com.uplayer.app.library.Track
import com.uplayer.app.playback.PlaybackService

class MainActivity : ComponentActivity() {
 private var controller by mutableStateOf<MediaController?>(null)
 private var tracks by mutableStateOf<List<Track>>(emptyList())
 private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) tracks = AudioRepository(this).loadTracks() }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  connectController(); requestAudioAndLoad()
  setContent { UPlayerApp(tracks, controller) }
 }
 private fun connectController() {
  val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
  val future = MediaController.Builder(this, token).buildAsync()
  future.addListener({ controller = future.get() }, MoreExecutors.directExecutor())
 }
 private fun requestAudioAndLoad() {
  val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
  if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) tracks = AudioRepository(this).loadTracks() else permissionLauncher.launch(permission)
 }
 override fun onDestroy() { controller?.release(); controller = null; super.onDestroy() }
}

@Composable private fun UPlayerApp(tracks: List<Track>, player: Player?) {
 val bg=Color(0xFF02040A); val ultra=Color(0xFF315CFF)
 MaterialTheme(colorScheme=darkColorScheme(primary=ultra,background=bg,surface=bg)) {
  Surface(Modifier.fillMaxSize(),color=bg) {
   Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Text("U-player",color=Color.White,fontSize=26.sp,modifier=Modifier.padding(24.dp))
    Text("LIBRARY  •  " + tracks.size + " TRACKS",color=ultra,fontSize=11.sp,modifier=Modifier.padding(horizontal=24.dp))
    LazyColumn(Modifier.weight(1f).padding(top=12.dp)) {
     items(tracks,key={it.id}) { track ->
      Column(Modifier.fillMaxWidth().clickable {
       player?.apply { setMediaItems(tracks.map{it.asMediaItem()},tracks.indexOf(track),0); prepare(); play() }
      }.padding(horizontal=24.dp,vertical=12.dp)) {
       Text(track.title,color=Color.White,maxLines=1)
       Text(track.artist,color=Color(0xFF7D8495),fontSize=12.sp,maxLines=1)
      }
     }
    }
    MiniPlayer(player,ultra)
   }
  }
 }
}

@Composable private fun MiniPlayer(player: Player?, ultra: Color) {
 var playing by remember { mutableStateOf(player?.isPlaying==true) }
 DisposableEffect(player) {
  val listener=object:Player.Listener { override fun onIsPlayingChanged(isPlaying:Boolean){playing=isPlaying} }
  player?.addListener(listener); playing=player?.isPlaying==true
  onDispose { player?.removeListener(listener) }
 }
 Row(Modifier.fillMaxWidth().background(Color(0xFF060A14)).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
  Column(Modifier.weight(1f)) {
   Text(player?.mediaMetadata?.title?.toString()?:"NO TRACK",color=Color.White,maxLines=1)
   Text(player?.mediaMetadata?.artist?.toString()?:"Select music from Library",color=Color(0xFF72798A),fontSize=11.sp)
  }
  IconButton(onClick={if(player?.isPlaying==true)player.pause() else player?.play()}) {
   Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=ultra)
  }
 }
}