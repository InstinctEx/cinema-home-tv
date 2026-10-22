package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import java.util.List;

/** Event-driven session observer. Only Spotify metadata is passed to Home. */
final class SpotifySessions {
    interface Listener { void changed(MediaController controller); }
    private final Context context;
    private final MediaSessionManager manager;
    private final Listener listener;
    private MediaController controller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean subscribed, running;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { if (running) listener.changed(controller); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { if (running) listener.changed(controller); }
        @Override public void onSessionDestroyed() { refresh(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::select;

    SpotifySessions(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    void start() {
        if (manager == null || running) return;
        running = true;
        try {
            manager.addOnActiveSessionsChangedListener(sessions, component(), handler);
            subscribed = true;
        } catch (SecurityException ignored) { /* The local notification-access setup has not been granted yet. */ }
        refresh();
    }

    void refresh() {
        if (manager == null) return;
        try { select(manager.getActiveSessions(component())); }
        catch (SecurityException ignored) { select(java.util.Collections.emptyList()); }
    }

    private ComponentName component() { return new ComponentName(context, MusicAccessService.class); }
    private void select(List<MediaController> controllers) {
        if (!running) return;
        MediaController selected = null;
        if (controllers != null) for (MediaController candidate : controllers) {
            if ("com.spotify.tv.android".equals(candidate.getPackageName())) { selected = candidate; break; }
        }
        if (controller != null) controller.unregisterCallback(callback);
        controller = selected;
        if (controller != null) controller.registerCallback(callback, handler);
        listener.changed(controller);
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (manager != null && subscribed) manager.removeOnActiveSessionsChangedListener(sessions);
        if (controller != null) controller.unregisterCallback(callback);
        subscribed = false;
        controller = null;
    }
}
