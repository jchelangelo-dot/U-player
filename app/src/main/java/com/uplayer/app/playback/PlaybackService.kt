package com.uplayer.app.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.uplayer.app.dsp.EqCommand
import com.uplayer.app.dsp.EqSettingsStore
import com.uplayer.app.dsp.LiveStageCommand
import com.uplayer.app.dsp.LiveStageSettingsStore
import com.uplayer.app.dsp.ParametricEqAudioProcessor

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
 private var mediaSession: MediaSession? = null
 private lateinit var eqProcessor: ParametricEqAudioProcessor
 private lateinit var playbackStateStore: PlaybackStateStore

 override fun onCreate() {
  super.onCreate()
  val eqStore = EqSettingsStore(this)
  val liveStageStore = LiveStageSettingsStore(this)
  playbackStateStore = PlaybackStateStore(this)
  eqProcessor = ParametricEqAudioProcessor(eqStore.load(), liveStageStore.load())
  val renderersFactory = object : DefaultRenderersFactory(this) {
   override fun buildAudioSink(
    context: Context,
    enableFloatOutput: Boolean,
    enableAudioTrackPlaybackParams: Boolean
   ): AudioSink = DefaultAudioSink.Builder(context)
    .setEnableFloatOutput(false)
    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
    .setAudioProcessors(arrayOf(eqProcessor))
    .build()
  }
  val attrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
  val player = ExoPlayer.Builder(this, renderersFactory).build().apply {
   setAudioAttributes(attrs, true)
   setHandleAudioBecomingNoisy(true)
  }
  player.addListener(object : Player.Listener {
   override fun onEvents(player: Player, events: Player.Events) {
    if (
     events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
     events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
     events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
     events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
     events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
    ) playbackStateStore.save(player)
   }
  })
  val updateEqCommand = SessionCommand(EqCommand.ACTION_UPDATE, Bundle.EMPTY)
  val updateLiveStageCommand = SessionCommand(LiveStageCommand.ACTION_UPDATE, Bundle.EMPTY)
  val callback = object : MediaSession.Callback {
   override fun onConnect(
    session: MediaSession,
   controller: MediaSession.ControllerInfo
   ): MediaSession.ConnectionResult {
    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().apply {
     if (controller.packageName == packageName) {
      add(updateEqCommand)
      add(updateLiveStageCommand)
     }
    }.build()
    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
     .setAvailableSessionCommands(commands)
     .build()
   }

   override fun onCustomCommand(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    customCommand: SessionCommand,
    args: Bundle
   ): ListenableFuture<SessionResult> {
    when (customCommand.customAction) {
     EqCommand.ACTION_UPDATE -> {
      val settings = EqCommand.fromBundle(args)
       ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
      eqProcessor.updateSettings(settings)
      eqStore.save(settings)
     }
     LiveStageCommand.ACTION_UPDATE -> {
      val settings = LiveStageCommand.fromBundle(args)
      eqProcessor.updateLiveStage(settings)
      liveStageStore.save(settings)
     }
     else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
    }
    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
   }
  }
  mediaSession = MediaSession.Builder(this, player).setCallback(callback).build()
 }
 override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

 override fun onTaskRemoved(rootIntent: Intent?) {
  mediaSession?.player?.let(playbackStateStore::save)
  super.onTaskRemoved(rootIntent)
 }

 override fun onDestroy() {
  mediaSession?.run { playbackStateStore.save(player); player.release(); release() }
  mediaSession = null
  super.onDestroy()
 }
}
