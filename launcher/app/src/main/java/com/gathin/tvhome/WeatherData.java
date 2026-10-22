package com.gathin.tvhome;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Explicitly configured forecast, cached for one hour. No location is bundled. */
final class WeatherData {
    final String summary, detail;
    final int sunrise, sunset;
    private WeatherData(String summary, String detail, int sunrise, int sunset) {
        this.summary = summary; this.detail = detail; this.sunrise = sunrise; this.sunset = sunset;
    }
    private static int minute(String timestamp) {
        String time = timestamp.substring(timestamp.indexOf('T') + 1);
        return Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3, 5));
    }

    static boolean configured(Context context) {
        return WeatherLocation.valid(context.getString(R.string.weather_location_name),
                context.getString(R.string.weather_latitude), context.getString(R.string.weather_longitude));
    }

    static File cacheFile(Context context) {
        if (!configured(context)) return null;
        String location = context.getString(R.string.weather_location_name) + ":"
                + context.getString(R.string.weather_latitude) + ":" + context.getString(R.string.weather_longitude);
        // Separate configurations must not reuse one another's forecasts.
        return new File(context.getCacheDir(), "weather-" + java.util.UUID.nameUUIDFromBytes(
                location.getBytes(StandardCharsets.UTF_8)) + ".json");
    }

    static WeatherData load(Context context) {
        File cache = cacheFile(context);
        if (cache == null) return null;
        String latitude = Double.toString(Double.parseDouble(context.getString(R.string.weather_latitude)));
        String longitude = Double.toString(Double.parseDouble(context.getString(R.string.weather_longitude)));
        String raw = null;
        try { if (cache.isFile()) raw = new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8); }
        catch (Exception ignored) {}
        if (raw == null || System.currentTimeMillis() - cache.lastModified() > 3_600_000L) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://api.open-meteo.com/v1/forecast"
                        + "?latitude=" + latitude + "&longitude=" + longitude + "&current=temperature_2m,weather_code"
                        + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=1&timezone=auto").openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                if (connection.getResponseCode() == 200) {
                    byte[] bytes;
                    try (InputStream input = connection.getInputStream(); java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                        byte[] chunk = new byte[4096]; int count;
                        while ((count = input.read(chunk)) != -1) {
                            if (buffer.size() + count > 100_000) throw new java.io.IOException("Forecast too large");
                            buffer.write(chunk, 0, count);
                        }
                        bytes = buffer.toByteArray();
                    }
                    String fresh = new String(bytes, StandardCharsets.UTF_8);
                    new JSONObject(fresh).getJSONObject("current");
                    raw = fresh;
                    Files.write(cache.toPath(), bytes);
                }
            } catch (Exception ignored) { /* Use the cached forecast while offline. */ }
            finally { if (connection != null) connection.disconnect(); }
        }
        try {
            if (!cache.isFile() || System.currentTimeMillis() - cache.lastModified() > 6 * 3_600_000L) return null;
            JSONObject json = new JSONObject(raw);
            JSONObject current = json.getJSONObject("current"), daily = json.getJSONObject("daily");
            String summary = context.getString(R.string.weather_location_name) + "  ·  " + Math.round(current.getDouble("temperature_2m")) + "°  ·  "
                    + HomePolicy.weather(current.getInt("weather_code"));
            String rise = daily.getJSONArray("sunrise").getString(0);
            String set = daily.getJSONArray("sunset").getString(0);
            String detail = summary + "\n\nHigh " + Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0))
                    + "°  ·  Low " + Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0))
                    + "°\nSunrise " + rise.substring(rise.indexOf('T') + 1)
                    + "  ·  Sunset " + set.substring(set.indexOf('T') + 1) + "\n\nForecast by Open-Meteo · refreshed hourly";
            return new WeatherData(summary, detail, minute(rise), minute(set));
        } catch (Exception ignored) { return null; }
    }
}
