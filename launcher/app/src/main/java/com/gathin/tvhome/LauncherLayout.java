package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** App identities survive updates, renames and temporary unavailability. */
final class LauncherLayout {
  private final SharedPreferences prefs;
  private JSONObject state;

  LauncherLayout(Context context) {
    prefs = context.getSharedPreferences("cinema_layout", Context.MODE_PRIVATE);
    try {
      state = new JSONObject(prefs.getString("state", "{}"));
    } catch (Exception e) {
      state = new JSONObject();
    }
  }

  synchronized List<String> list(String name) {
    List<String> result = new ArrayList<>();
    JSONArray array = state.optJSONArray(name);
    if (array != null)
      for (int i = 0; i < array.length(); i++) {
        String key = array.optString(i);
        if (!key.isEmpty() && !result.contains(key)) result.add(key);
      }
    return result;
  }

  synchronized void setList(String name, List<String> values) {
    try {
      state.put(name, new JSONArray(new ArrayList<>(new LinkedHashSet<>(values))));
      save();
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  synchronized String name(String key, String fallback) {
    JSONObject names = state.optJSONObject("names");
    return names == null ? fallback : names.optString(key, fallback);
  }

  synchronized void rename(String key, String value) {
    try {
      JSONObject names = state.optJSONObject("names");
      if (names == null) names = new JSONObject();
      names.put(key, FeaturePolicy.label(value));
      state.put("names", names);
      save();
    } catch (org.json.JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  synchronized void hide(String key, boolean hidden) {
    List<String> values = list("hidden");
    if (hidden && !values.contains(key)) values.add(key);
    else if (!hidden) values.remove(key);
    setList("hidden", values);
  }

  synchronized List<String> ordered(Set<String> installed) {
    List<String> order = list("order");
    boolean changed = false;
    for (String key : installed)
      if (!order.contains(key)) {
        order.add(key);
        changed = true;
      }
    if (changed) setList("order", order);
    List<String> visible = new ArrayList<>();
    for (String key : order)
      if (installed.contains(key) && !list("hidden").contains(key)) visible.add(key);
    return visible;
  }

  synchronized void folder(String key, String name) {
    try {
      JSONObject folders = state.optJSONObject("folders");
      if (folders == null) folders = new JSONObject();
      java.util.Iterator<String> names = folders.keys();
      List<String> empty = new ArrayList<>();
      while (names.hasNext()) {
        String n = names.next();
        JSONArray a = folders.getJSONArray(n);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < a.length(); i++)
          if (!key.equals(a.getString(i))) keys.add(a.getString(i));
        folders.put(n, new JSONArray(keys));
        if (keys.isEmpty()) empty.add(n);
      }
      for (String n : empty) folders.remove(n);
      if (name != null && !name.trim().isEmpty()) {
        String clean = FeaturePolicy.label(name);
        JSONArray a = folders.optJSONArray(clean);
        if (a == null) a = new JSONArray();
        a.put(key);
        folders.put(clean, a);
      }
      state.put("folders", folders);
      save();
    } catch (org.json.JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  synchronized JSONObject folders() {
    try {
      return new JSONObject(
          state.optJSONObject("folders") == null
              ? "{}"
              : state.getJSONObject("folders").toString());
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  private void save() {
    prefs.edit().putString("state", state.toString()).apply();
  }
}
