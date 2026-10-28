package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/** Rules shared by remote actions, paired editing and JVM tests. */
final class FeaturePolicy {
  static final long PHOTO_LIMIT = 12 * 1024 * 1024L, VIDEO_LIMIT = 150 * 1024 * 1024L;
  static final long STORAGE_LIMIT = 400 * 1024 * 1024L;

  static boolean pin(String value) {
    return value != null && value.matches("[0-9UDLR]{4,12}");
  }

  static boolean mediaId(String value) {
    return value != null && value.matches("[a-f0-9-]{36}\\.(jpg|mp4)");
  }

  static String label(String value) {
    String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "").trim();
    if (clean.isEmpty() || clean.length() > 40)
      throw new IllegalArgumentException("Use a name of 1–40 characters");
    return clean;
  }

  static <T> List<T> move(List<T> values, T key, int delta) {
    List<T> next = new ArrayList<>(values);
    int from = next.indexOf(key);
    if (from < 0) throw new IllegalArgumentException("Item is no longer available");
    next.remove(from);
    next.add(Math.max(0, Math.min(next.size(), from + delta)), key);
    return next;
  }

  static boolean lowEnd(boolean lowRam, long memoryBytes, int cores) {
    return lowRam || memoryBytes < 1800L * 1024 * 1024 || cores <= 2;
  }

  static boolean uploadAllowed(long length, boolean video, long used) {
    return length > 0
        && length <= (video ? VIDEO_LIMIT : PHOTO_LIMIT)
        && used >= 0
        && length <= STORAGE_LIMIT - used;
  }

  private FeaturePolicy() {}
}
