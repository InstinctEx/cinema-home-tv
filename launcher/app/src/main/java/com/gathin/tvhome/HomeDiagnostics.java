package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local ADB health check, protected by the system DUMP permission. No network or telemetry. */
public final class HomeDiagnostics extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            JSONObject report = new JSONObject();
            java.util.List<ContinueWatching.Item> items = ContinueWatching.read(context);
            report.put("continueWatching", items.size());
            JSONArray sources = new JSONArray();
            for (ContinueWatching.Item item : items) {
                Uri image = Uri.parse(item.artwork);
                sources.put(new JSONObject().put("scheme", image.getScheme()).put("host", image.getHost())
                        .put("progress", item.progress()));
            }
            report.put("artworkSources", sources);
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            boolean spotify = false, metadata = false;
            try {
                if (manager != null) for (MediaController controller : manager.getActiveSessions(new ComponentName(context, MusicAccessService.class))) {
                    if ("com.spotify.tv.android".equals(controller.getPackageName())) {
                        spotify = true; metadata = controller.getMetadata() != null;
                        android.media.session.PlaybackState state = controller.getPlaybackState();
                        report.put("spotifyState", state == null ? 0 : state.getState());
                        if (metadata) report.put("spotifyDuration", controller.getMetadata().getLong(android.media.MediaMetadata.METADATA_KEY_DURATION));
                    }
                }
                report.put("mediaAccess", true);
            } catch (SecurityException e) { report.put("mediaAccess", false); }
            report.put("spotifySession", spotify).put("spotifyMetadata", metadata);
            report.put("remoteControlsConnected", TvAccessibilityService.available()).put("standbySupported", TvAccessibilityService.canStandby());
            java.io.File forecast = WeatherData.cacheFile(context);
            report.put("weatherCached", forecast != null && forecast.isFile());
            java.io.File[] artwork = new java.io.File(context.getCacheDir(), "artwork-v1").listFiles();
            long bytes = 0; if (artwork != null) for (java.io.File entry : artwork) bytes += entry.length();
            report.put("artworkDiskBytes", bytes);
            report.put("settingsRestorePoint", SettingsBackup.snapshot(context).isFile());
            report.put("settingsBackupValid", !SettingsBackup.parse(SettingsBackup.export(context.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE))).isEmpty());
            setResultCode(-1); setResultData(report.toString());
        } catch (Exception e) { setResultCode(0); setResultData(e.getClass().getSimpleName()); }
    }
}
