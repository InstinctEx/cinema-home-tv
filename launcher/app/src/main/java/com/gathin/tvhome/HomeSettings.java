package com.gathin.tvhome;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Portable visual settings only: never imports timers, URI grants or remote key interception. */
final class HomeSettings {
  static Map<String, Object> defaults() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("wallpaper_selection", "misty");
    values.put("wallpaper_dim", 10);
    values.put("wallpaper_interval", 60);
    values.put("glass_style", "frosted");
    values.put("media_app", "nuvio");
    for (String key :
        new String[] {
          "cinematic_artwork", "shelf_enabled", "navigation_sounds", "idle_clock", "music_strip"
        }) values.put(key, true);
    values.put("navigation_sounds", false);
    values.put("weather_enabled", false);
    values.put("reduced_motion", false);
    values.put("wallpaper_motion", "idle");
    values.put("screensaver_mode", "system");
    values.put("screensaver_minutes", 5);
    values.put("screensaver_clock", true);
    values.put("back_screensaver", false);
    values.put("clock_right", false);
    values.put("two_rows", false);
    values.put("peek_row", false);
    values.put("large_clock", false);
    values.put("show_names", true);
    values.put("home_plate", true);
    values.put("high_contrast", false);
    values.put("effects", "full");
    values.put("aerial_quality", "1080p");
    values.put("scene_shuffle", true);
    values.put("scene_favorites_only", false);
    return values;
  }

  static Map<String, Object> validate(Map<String, ?> input) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, ?> item : input.entrySet()) {
      String key = item.getKey();
      Object value = item.getValue();
      Object fallback = defaults().get(key);
      if (fallback == null && !"dock_apps".equals(key)) continue;
      if (fallback instanceof Boolean) {
        if (!(value instanceof Boolean))
          throw new IllegalArgumentException("Invalid toggle: " + key);
      } else if (fallback instanceof Integer) {
        if (!(value instanceof Number)
            || !Double.isFinite(((Number) value).doubleValue())
            || ((Number) value).doubleValue() != ((Number) value).intValue())
          throw new IllegalArgumentException("Invalid number: " + key);
        int number = ((Number) value).intValue();
        value = number;
        if ("wallpaper_dim".equals(key)
            ? number < 0 || number > 80
            : "screensaver_minutes".equals(key)
                ? !Arrays.asList(0, 2, 5, 10, 20).contains(number)
                : !Arrays.asList(30, 60, 180).contains(number))
          throw new IllegalArgumentException("Invalid range: " + key);
      } else {
        if (!(value instanceof String))
          throw new IllegalArgumentException("Invalid setting: " + key);
        String text = (String) value;
        if ("wallpaper_selection".equals(key)
            && !Arrays.asList(
                    "misty",
                    "aurora",
                    "moon_lake",
                    "dunes",
                    "dusk_peaks",
                    "golden_coast",
                    "clouds",
                    "automatic",
                    "rotation",
                    "aerial",
                    "video",
                    "photos",
                    "photo_daily",
                    "daily",
                    "follow_app")
                .contains(text)) throw new IllegalArgumentException("Unknown scene");
        if ("wallpaper_motion".equals(key)
            && !Arrays.asList("off", "idle", "always").contains(text))
          throw new IllegalArgumentException("Unknown motion mode");
        if ("screensaver_mode".equals(key)
            && !Arrays.asList("clock", "photos", "aerial", "video", "system").contains(text))
          throw new IllegalArgumentException("Unknown screensaver");
        if ("effects".equals(key) && !Arrays.asList("auto", "full", "lite").contains(text))
          throw new IllegalArgumentException("Unknown effects");
        if ("aerial_quality".equals(key)
            && !Arrays.asList("auto", "1080p", "4k", "dolby_vision").contains(text))
          throw new IllegalArgumentException("Unknown quality");
        if ("glass_style".equals(key) && !Arrays.asList("soft", "frosted", "solid").contains(text))
          throw new IllegalArgumentException("Unknown glass style");
        if ("media_app".equals(key) && !Arrays.asList("nuvio", "stremio").contains(text))
          throw new IllegalArgumentException("Unknown media app");
        if ("dock_apps".equals(key) && !text.isEmpty()) {
          String[] packages = text.split(",", -1);
          if (packages.length > 5) throw new IllegalArgumentException("Too many dock apps");
          for (String pkg : packages)
            if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
              throw new IllegalArgumentException("Invalid app package");
        }
      }
      result.put(key, value);
    }
    return result;
  }

  private HomeSettings() {}
}
