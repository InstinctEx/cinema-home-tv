package com.gathin.tvhome;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Opt-in daily photograph, bounded fetch and offline fallback. */
final class DailyWallpaper {
  static File file(Context context) {
    return new File(context.getCacheDir(), "daily-wallpaper.jpg");
  }

  static synchronized File load(Context context) throws Exception {
    File target = file(context);
    if (target.isFile() && System.currentTimeMillis() - target.lastModified() < 24 * 3600_000L)
      return target;
    try {
      byte[] metadata =
          get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US", 100000);
      String path =
          new JSONObject(new String(metadata, StandardCharsets.UTF_8))
              .getJSONArray("images")
              .getJSONObject(0)
              .getString("url");
      if (!path.startsWith("/th?")) throw new IOException("Unexpected daily photo URL");
      byte[] image = get("https://www.bing.com" + path, 8_000_000);
      android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      android.graphics.BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
        throw new IOException("Invalid daily photo");
      android.util.AtomicFile atomic = new android.util.AtomicFile(target);
      FileOutputStream out = null;
      try {
        out = atomic.startWrite();
        out.write(image);
        atomic.finishWrite(out);
      } catch (Exception e) {
        atomic.failWrite(out);
        throw e;
      }
    } catch (Exception e) {
      if (!target.isFile()) throw e;
    }
    return target;
  }

  static HttpURLConnection open(String address) throws IOException {
    URL target = new URL(address);
    for (int hop = 0; hop < 5; hop++) {
      if (!"https".equals(target.getProtocol())) throw new IOException("HTTPS required");
      HttpURLConnection connection = (HttpURLConnection) target.openConnection();
      connection.setConnectTimeout(6000);
      connection.setReadTimeout(15000);
      connection.setInstanceFollowRedirects(false);
      int status;
      try {
        status = connection.getResponseCode();
      } catch (IOException e) {
        connection.disconnect();
        throw e;
      }
      if (status == 200) return connection;
      String next = connection.getHeaderField("Location");
      connection.disconnect();
      if (status < 300 || status > 399 || next == null) throw new IOException("Source unavailable");
      target = new URL(target, next);
    }
    throw new IOException("Too many redirects");
  }

  static byte[] get(String url, int limit) throws IOException {
    HttpURLConnection connection = open(url);
    try {
      if (connection.getResponseCode() != 200) throw new IOException("Source unavailable");
      try (InputStream input = connection.getInputStream();
          ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        byte[] chunk = new byte[8192];
        int n;
        while ((n = input.read(chunk)) != -1) {
          if (bytes.size() + n > limit) throw new IOException("Response exceeds limit");
          bytes.write(chunk, 0, n);
        }
        return bytes.toByteArray();
      }
    } finally {
      connection.disconnect();
    }
  }
}
