package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** A local restore point and portable JSON for visual preferences; no accounts or history. */
final class SettingsBackup {
  static String export(SharedPreferences prefs) throws Exception {
    Map<String, Object> values = HomeSettings.defaults();
    for (Map.Entry<String, ?> item : prefs.getAll().entrySet())
      values.put(item.getKey(), item.getValue());
    if ("custom".equals(values.get("wallpaper_selection"))
        || String.valueOf(values.get("wallpaper_selection")).startsWith("input:"))
      values.put("wallpaper_selection", "misty");
    if (values.get("dock_apps") instanceof String)
      values.put(
          "dock_apps",
          java.util.Arrays.stream(((String) values.get("dock_apps")).split(","))
              .filter(key -> key.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
              .collect(java.util.stream.Collectors.joining(",")));
    return new JSONObject()
        .put("format", "CinemaHomeSettings")
        .put("version", 1)
        .put("settings", new JSONObject(HomeSettings.validate(values)))
        .toString(2);
  }

  static Map<String, Object> parse(String json) throws Exception {
    if (json.length() > 65_536) throw new IllegalArgumentException("Backup too large");
    JSONObject document = new JSONObject(json);
    if (!"CinemaHomeSettings".equals(document.optString("format"))
        || !(document.opt("version") instanceof Number)
        || ((Number) document.get("version")).doubleValue() != 1)
      throw new IllegalArgumentException("Unsupported backup");
    JSONObject settings = document.getJSONObject("settings");
    Map<String, Object> values = new LinkedHashMap<>();
    java.util.Iterator<String> keys = settings.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      values.put(key, settings.get(key));
    }
    Map<String, Object> validated = HomeSettings.validate(values);
    if (validated.isEmpty()) throw new IllegalArgumentException("No Home settings in this file");
    return validated;
  }

  static void apply(SharedPreferences prefs, Map<String, Object> imported) {
    Map<String, Object> values = HomeSettings.defaults();
    values.putAll(HomeSettings.validate(imported));
    SharedPreferences.Editor edit = prefs.edit();
    edit.remove("dock_apps");
    for (Map.Entry<String, Object> item : values.entrySet()) {
      Object value = item.getValue();
      if (value instanceof Boolean) edit.putBoolean(item.getKey(), (Boolean) value);
      else if (value instanceof Integer) edit.putInt(item.getKey(), (Integer) value);
      else edit.putString(item.getKey(), (String) value);
    }
    edit.putLong("wallpaper_changed_at", System.currentTimeMillis()).apply();
  }

  static File snapshot(Context context) {
    return new File(context.getFilesDir(), "home-settings-restore.json");
  }

  static void save(Context context, SharedPreferences prefs) throws Exception {
    byte[] bytes = export(prefs).getBytes(StandardCharsets.UTF_8);
    AtomicFile file = new AtomicFile(snapshot(context));
    FileOutputStream output = null;
    try {
      output = file.startWrite();
      output.write(bytes);
      file.finishWrite(output);
    } catch (Exception failure) {
      file.failWrite(output);
      throw failure;
    }
  }

  static void saveIfAbsent(Context context, SharedPreferences prefs) {
    try {
      if (!snapshot(context).isFile()) save(context, prefs);
    } catch (Exception ignored) {
    }
  }

  private SettingsBackup() {}
}
