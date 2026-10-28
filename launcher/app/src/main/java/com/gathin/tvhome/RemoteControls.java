package com.gathin.tvhome;

import android.content.*;
import android.media.*;
import android.media.session.*;
import java.util.*;
import org.json.JSONObject;

/** Public Android APIs only: own-window navigation, global volume and authorized media sessions. */
final class RemoteControls {
  static final Set<String> KEYS =
      Set.of(
          "up",
          "down",
          "left",
          "right",
          "ok",
          "back",
          "home",
          "menu",
          "settings",
          "volume_up",
          "volume_down",
          "mute",
          "play_pause",
          "previous",
          "next");

  static JSONObject capabilities(Context context, boolean homeVisible) throws Exception {
    boolean access = false;
    try {
      sessions(context);
      access = true;
    } catch (SecurityException ignored) {
    }
    return new JSONObject()
        .put("homeVisible", homeVisible)
        .put("accessibility", TvAccessibilityService.available())
        .put("globalDpad", TvAccessibilityService.globalDpadAvailable())
        .put("mediaAccess", access);
  }

  private static List<MediaController> sessions(Context context) {
    MediaSessionManager manager =
        (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    return manager == null
        ? Collections.emptyList()
        : manager.getActiveSessions(new ComponentName(context, MusicAccessService.class));
  }

  static void execute(Context context, PhoneServer.Host owner, String key) throws Exception {
    if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown remote button");
    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (key.equals("volume_up") || key.equals("volume_down") || key.equals("mute")) {
      audio.adjustStreamVolume(
          AudioManager.STREAM_MUSIC,
          key.equals("mute")
              ? AudioManager.ADJUST_TOGGLE_MUTE
              : key.equals("volume_up") ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
          AudioManager.FLAG_SHOW_UI);
      return;
    }
    if (key.equals("play_pause") || key.equals("previous") || key.equals("next")) {
      List<MediaController> controllers;
      try {
        controllers = sessions(context);
      } catch (SecurityException e) {
        throw new IllegalArgumentException("Enable music control access in Connections on the TV");
      }
      MediaController selected = null;
      for (MediaController controller : controllers) {
        PlaybackState state = controller.getPlaybackState();
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
          selected = controller;
          break;
        }
        if (selected == null) selected = controller;
      }
      if (selected == null) throw new IllegalArgumentException("No active media session");
      PlaybackState state = selected.getPlaybackState();
      long actions = state == null ? 0 : state.getActions();
      MediaController.TransportControls controls = selected.getTransportControls();
      if (key.equals("previous")) {
        if ((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) == 0)
          throw new IllegalArgumentException("This player does not support previous");
        controls.skipToPrevious();
      } else if (key.equals("next")) {
        if ((actions & PlaybackState.ACTION_SKIP_TO_NEXT) == 0)
          throw new IllegalArgumentException("This player does not support next");
        controls.skipToNext();
      } else {
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        long needed = playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
        if ((actions & (needed | PlaybackState.ACTION_PLAY_PAUSE)) == 0)
          throw new IllegalArgumentException("This player does not support play/pause");
        if (playing) controls.pause();
        else controls.play();
      }
      return;
    }
    if (owner != null && owner.active()) {
      owner.navigate(key);
      return;
    }
    if (TvAccessibilityService.remoteAction(key)) return;
    if (key.equals("menu") || key.equals("settings"))
      throw new IllegalArgumentException(
          "Menu and Settings are available while Cinema Home is on screen");
    if (!TvAccessibilityService.available())
      throw new IllegalArgumentException(
          "Enable Cinema Home accessibility access for navigation outside the launcher");
    throw new IllegalArgumentException(
        android.os.Build.VERSION.SDK_INT < 33
            ? "Directional controls outside Home require Android 13 or later"
            : "The TV did not accept this remote button");
  }
}
