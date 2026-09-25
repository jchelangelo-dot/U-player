package com.uplayer.app.playback

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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

 override fun onCreate() {
  super.onCreate()
  val eqStore = EqSettingsStore(this)
  val liveStageStore = LiveStageSettingsStore(this)
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
  val updateEqCommand = SessionCommand(EqCommand.ACTION_UPDATE, Bundle.EMPTY)
  val updateLiveStageCommand = SessionCommand(LiveStageCommand.ACTION_UPDATE, Bundle.EMPTY)
  val callback = object : MediaSession.Callback {
   override fun onConnect(
    session: MediaSession,
    controller: MediaSession.ControllerInfo
   ): MediaSession.ConnectionResult {
    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
     .buildUpon()
     .add(updateEqCommand)
     .add(updateLiveStageCommand)
     .build()
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
 override fun onDestroy() {
  mediaSession?.run { player.release(); release() }
  mediaSession = null
  super.onDestroy()
 }
}
