package com.gathin.tvhome;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Portable visual settings only: never imports timers, URI grants or remote key interception. */
final class HomeSettings {
    static Map<String, Object> defaults() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("wallpaper_selection", "aurora"); values.put("wallpaper_dim", 25);
        values.put("wallpaper_interval", 60); values.put("glass_style", "frosted");
        values.put("media_app", "nuvio");
        for (String key : new String[]{"cinematic_artwork", "shelf_enabled", "navigation_sounds", "idle_clock", "music_strip"}) values.put(key, true);
        values.put("weather_enabled", false); values.put("reduced_motion", false);
        return values;
    }

    static Map<String, Object> validate(Map<String, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> item : input.entrySet()) {
            String key = item.getKey(); Object value = item.getValue();
            Object fallback = defaults().get(key);
            if (fallback == null && !"dock_apps".equals(key)) continue;
            if (fallback instanceof Boolean) {
                if (!(value instanceof Boolean)) throw new IllegalArgumentException("Invalid toggle: " + key);
            } else if (fallback instanceof Integer) {
                if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())
                        || ((Number) value).doubleValue() != ((Number) value).intValue()) throw new IllegalArgumentException("Invalid number: " + key);
                int number = ((Number) value).intValue(); value = number;
                if ("wallpaper_dim".equals(key) ? number < 0 || number > 80 : !Arrays.asList(30, 60, 180).contains(number)) throw new IllegalArgumentException("Invalid range: " + key);
            } else {
                if (!(value instanceof String)) throw new IllegalArgumentException("Invalid setting: " + key);
                String text = (String) value;
                if ("wallpaper_selection".equals(key) && !Arrays.asList("misty", "aurora", "moon_lake", "dunes", "dusk_peaks", "golden_coast", "clouds", "automatic", "rotation").contains(text)) throw new IllegalArgumentException("Unknown scene");
                if ("glass_style".equals(key) && !Arrays.asList("soft", "frosted", "solid").contains(text)) throw new IllegalArgumentException("Unknown glass style");
                if ("media_app".equals(key) && !Arrays.asList("nuvio", "stremio").contains(text)) throw new IllegalArgumentException("Unknown media app");
                if ("dock_apps".equals(key) && !text.isEmpty()) {
                    String[] packages = text.split(",", -1);
                    if (packages.length > 5) throw new IllegalArgumentException("Too many dock apps");
                    for (String pkg : packages) if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) throw new IllegalArgumentException("Invalid app package");
                }
            }
            result.put(key, value);
        }
        return result;
    }
    private HomeSettings() {}
}
