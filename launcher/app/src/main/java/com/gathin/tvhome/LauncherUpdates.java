package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.json.JSONObject;

/**
 * Explicit HTTPS release source. Only this package with exactly the current signing certificate is
 * installable.
 */
final class LauncherUpdates {
  private static long code(PackageInfo info) {
    return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
  }

  static void check(
      Activity activity, SharedPreferences prefs, Executor worker, Consumer<String> result) {
    Handler ui = new Handler(Looper.getMainLooper());
    String source = prefs.getString("release_manifest", "");
    if (!source.startsWith("https://")) {
      result.accept("Pair your phone and configure an HTTPS release manifest first");
      return;
    }
    worker.execute(
        () -> {
          try {
            JSONObject manifest =
                new JSONObject(
                    new String(DailyWallpaper.get(source, 65536), StandardCharsets.UTF_8));
            long version = manifest.getLong("versionCode");
            PackageInfo installed =
                activity
                    .getPackageManager()
                    .getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
            if (version <= code(installed)) {
              ui.post(() -> result.accept("Cinema Home is up to date"));
              return;
            }
            String apk = manifest.getString("apk");
            if (!apk.startsWith("https://")) throw new IOException("APK must use HTTPS");
            ui.post(
                () -> {
                  if (activity.isFinishing() || activity.isDestroyed()) return;
                  new AlertDialog.Builder(activity)
                      .setTitle("Cinema Home update")
                      .setMessage(
                          "Download signed version "
                              + version
                              + "? Android will ask before installation.")
                      .setPositiveButton(
                          "Download",
                          (dialog, which) ->
                              worker.execute(() -> download(activity, apk, version, result)))
                      .setNegativeButton("Cancel", null)
                      .show();
                });
          } catch (Exception e) {
            ui.post(() -> result.accept("Could not check the configured release"));
          }
        });
  }

  @SuppressWarnings("deprecation")
  private static void download(
      Activity activity, String url, long version, Consumer<String> result) {
    Handler ui = new Handler(Looper.getMainLooper());
    File folder = new File(activity.getCacheDir(), "updates");
    folder.mkdirs();
    File partial = new File(folder, "cinema.part"), target = new File(folder, "cinema.apk");
    HttpURLConnection connection = null;
    try {
      connection = DailyWallpaper.open(url);
      try (InputStream input = connection.getInputStream();
          OutputStream out = new FileOutputStream(partial)) {
        long total = 0;
        byte[] chunk = new byte[32768];
        int n;
        while ((n = input.read(chunk)) != -1) {
          if ((total += n) > 100 * 1024 * 1024) throw new IOException("APK exceeds limit");
          out.write(chunk, 0, n);
        }
      }
      PackageManager pm = activity.getPackageManager();
      PackageInfo
          archive = pm.getPackageArchiveInfo(partial.getPath(), PackageManager.GET_SIGNATURES),
          installed = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
      if (archive == null
          || !activity.getPackageName().equals(archive.packageName)
          || code(archive) != version
          || version <= code(installed)
          || archive.signatures == null
          || installed.signatures == null
          || archive.signatures.length == 0
          || !new HashSet<>(Arrays.asList(archive.signatures))
              .equals(new HashSet<>(Arrays.asList(installed.signatures))))
        throw new IOException("Update identity or signature rejected");
      java.nio.file.Files.move(
          partial.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      ui.post(
          () -> {
            try {
              android.net.Uri uri =
                  androidx.core.content.FileProvider.getUriForFile(
                      activity, activity.getPackageName() + ".updates", target);
              activity.startActivity(
                  new Intent(Intent.ACTION_VIEW)
                      .setDataAndType(uri, "application/vnd.android.package-archive")
                      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (Exception e) {
              result.accept("TV blocked the installer; use the existing ADB installer");
            }
          });
    } catch (Exception e) {
      ui.post(() -> result.accept("Update rejected or download unavailable"));
    } finally {
      partial.delete();
      if (connection != null) connection.disconnect();
    }
  }
}
