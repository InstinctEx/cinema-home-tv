package com.gathin.tvhome;

import android.content.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Catalogue IDs are the only accepted scene identities; UI and playback share these filters. */
final class SceneLibrary {
  private static JSONArray catalogue;

  static synchronized JSONArray catalogue(Context context) throws Exception {
    if (catalogue == null)
      try (InputStream input = context.getAssets().open("aerials.json");
          ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        byte[] chunk = new byte[8192];
        int n;
        while ((n = input.read(chunk)) != -1) bytes.write(chunk, 0, n);
        catalogue = new JSONObject(bytes.toString("UTF-8")).getJSONArray("videos");
      }
    return catalogue;
  }

  static LinkedHashMap<String, String> categories(JSONArray videos) {
    LinkedHashMap<String, String> result = new LinkedHashMap<>();
    for (int i = 0; i < videos.length(); i++) {
      JSONObject scene = videos.optJSONObject(i);
      if (scene != null) result.put(scene.optString("id"), scene.optString("category"));
    }
    return result;
  }

  static JSONObject preferences(SharedPreferences prefs) throws JSONException {
    return new JSONObject()
        .put("favorites", array(prefs, "scene_favorites"))
        .put("excluded", array(prefs, "scene_excluded"))
        .put("excludedCategories", array(prefs, "scene_excluded_categories"))
        .put("selected", prefs.getString("selected_scene", ""));
  }

  private static JSONArray array(SharedPreferences prefs, String key) {
    List<String> values = new ArrayList<>(prefs.getStringSet(key, Collections.emptySet()));
    Collections.sort(values);
    return new JSONArray(values);
  }

  static List<String> eligible(JSONArray videos, SharedPreferences prefs) {
    return SceneQueue.allowed(
        categories(videos),
        prefs.getStringSet("scene_favorites", Collections.emptySet()),
        prefs.getStringSet("scene_excluded", Collections.emptySet()),
        prefs.getStringSet("scene_excluded_categories", Collections.emptySet()),
        prefs.getBoolean("scene_favorites_only", false));
  }

  static JSONObject find(JSONArray videos, String id) {
    if (id == null) return null;
    for (int i = 0; i < videos.length(); i++) {
      JSONObject scene = videos.optJSONObject(i);
      if (scene != null && id.equals(scene.optString("id"))) return scene;
    }
    return null;
  }

  static void change(
      Context context, SharedPreferences prefs, String kind, String id, boolean enabled)
      throws Exception {
    Map<String, String> ids = categories(catalogue(context));
    String key;
    switch (kind) {
      case "favorite":
        key = "scene_favorites";
        break;
      case "exclude":
        key = "scene_excluded";
        break;
      case "category":
        key = "scene_excluded_categories";
        break;
      default:
        throw new IllegalArgumentException("Unknown scene preference");
    }
    if (kind.equals("category") ? !ids.containsValue(id) : !ids.containsKey(id))
      throw new IllegalArgumentException("Unknown scene");
    Set<String> values = new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
    if (enabled) values.add(id);
    else values.remove(id);
    prefs.edit().putStringSet(key, values).apply();
  }
}
