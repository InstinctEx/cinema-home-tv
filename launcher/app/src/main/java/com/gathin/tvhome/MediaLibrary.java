package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import java.io.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Private, bounded uploads. Originals never become executable files or arbitrary filesystem paths.
 */
final class MediaLibrary {
  private static final java.util.concurrent.atomic.AtomicBoolean cleaned =
      new java.util.concurrent.atomic.AtomicBoolean();
  private final Context context;
  final File directory;

  MediaLibrary(Context context) {
    this.context = context.getApplicationContext();
    directory = new File(context.getFilesDir(), "personal-media");
    directory.mkdirs();
    if (cleaned.compareAndSet(false, true)) {
      File[] stale = directory.listFiles((dir, name) -> name.endsWith(".part"));
      if (stale != null) for (File file : stale) file.delete();
    }
  }

  File file(String id) {
    if (!FeaturePolicy.mediaId(id)) throw new IllegalArgumentException("Invalid media ID");
    return new File(directory, id);
  }

  List<File> files(boolean video) {
    File[] all =
        directory.listFiles(
            (dir, name) -> FeaturePolicy.mediaId(name) && name.endsWith(video ? ".mp4" : ".jpg"));
    List<File> result = new ArrayList<>();
    if (all != null) Collections.addAll(result, all);
    result.sort(Comparator.comparing(File::getName));
    return result;
  }

  long used() {
    long total = 0;
    File[] all = directory.listFiles();
    if (all != null) for (File f : all) total += f.length();
    return total;
  }

  synchronized String upload(InputStream input, long length, boolean video) throws Exception {
    if (!FeaturePolicy.uploadAllowed(length, video, used())
        || directory.getUsableSpace() < length + 20 * 1024 * 1024)
      throw new IllegalArgumentException("Upload exceeds file or available storage limit");
    File temp = File.createTempFile("upload-", ".part", directory);
    String id = UUID.randomUUID() + (video ? ".mp4" : ".jpg");
    File target = file(id);
    boolean complete = false;
    try {
      try (OutputStream out = new FileOutputStream(temp)) {
        byte[] chunk = new byte[32768];
        long remaining = length;
        while (remaining > 0) {
          int n = input.read(chunk, 0, (int) Math.min(chunk.length, remaining));
          if (n < 0) throw new EOFException("Incomplete upload");
          out.write(chunk, 0, n);
          remaining -= n;
        }
      }
      if (video) {
        MediaMetadataRetriever reader = new MediaMetadataRetriever();
        try {
          reader.setDataSource(temp.getPath());
          String duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
          if (duration == null
              || Long.parseLong(duration) <= 0
              || reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) == null)
            throw new IllegalArgumentException("Not a supported video");
        } finally {
          reader.release();
        }
        if (!temp.renameTo(target)) throw new IOException("Could not save video");
      } else {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(temp.getPath(), bounds);
        if (bounds.outWidth <= 0
            || bounds.outHeight <= 0
            || (long) bounds.outWidth * bounds.outHeight > 100_000_000L)
          throw new IllegalArgumentException("Not a supported image (maximum 100 megapixels)");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize =
            HomePolicy.artworkSampleSize(bounds.outWidth, bounds.outHeight, 1280, 720);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap image = BitmapFactory.decodeFile(temp.getPath(), options);
        if (image == null) throw new IllegalArgumentException("Image could not be decoded");
        try (OutputStream out = new FileOutputStream(temp)) {
          if (!image.compress(Bitmap.CompressFormat.JPEG, 90, out))
            throw new IOException("Could not save photo");
        } finally {
          image.recycle();
        }
        if (used() > FeaturePolicy.STORAGE_LIMIT)
          throw new IOException("Library storage limit reached");
        if (!temp.renameTo(target)) throw new IOException("Could not save photo");
      }
      complete = true;
      return id;
    } finally {
      temp.delete();
      if (!complete) target.delete();
    }
  }

  void delete(String id) throws IOException {
    File f = file(id);
    if (f.exists() && !f.delete()) throw new IOException("Could not delete media");
  }

  JSONArray state() {
    JSONArray result = new JSONArray();
    for (boolean video : new boolean[] {false, true})
      for (File file : files(video))
        try {
          result.put(
              new JSONObject()
                  .put("id", file.getName())
                  .put("video", video)
                  .put("bytes", file.length()));
        } catch (Exception ignored) {
        }
    return result;
  }
}
