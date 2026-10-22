package com.gathin.tvhome;

/** Explicit build-time weather location; never guesses a location or uses GPS. */
final class WeatherLocation {
    static boolean valid(String label, String latitude, String longitude) {
        if (label == null || label.trim().isEmpty() || latitude == null || longitude == null
                || latitude.trim().isEmpty() || longitude.trim().isEmpty()) return false;
        try {
            double lat = Double.parseDouble(latitude), lon = Double.parseDouble(longitude);
            return Double.isFinite(lat) && Double.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
        } catch (NumberFormatException ignored) { return false; }
    }
    private WeatherLocation() {}
}
