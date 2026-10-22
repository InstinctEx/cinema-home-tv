package com.gathin.tvhome;

/** Small deterministic rules shared by Home and its JVM tests. */
final class HomePolicy {
    static String wallpaper(String selection, int hour, int index) {
        if ("automatic".equals(selection)) {
            if (hour < 7 || hour >= 19) return "moon_lake";
            if (hour < 10 || hour >= 17) return "dunes";
            return "aurora";
        }
        if ("rotation".equals(selection)) {
            String[] scenes = {"aurora", "moon_lake", "dunes", "dusk_peaks", "golden_coast", "clouds"};
            return scenes[Math.floorMod(index, scenes.length)];
        }
        return selection;
    }

    static String dayScene(int minute, int sunrise, int sunset) {
        if (sunrise < 0 || sunset <= sunrise || sunset > 1440) return wallpaper("automatic", minute / 60, 0);
        if (minute < sunrise - 40 || minute >= sunset + 40) return "moon_lake";
        if (minute < sunrise + 60 || minute >= sunset - 60) return "dunes";
        return "aurora";
    }

    static boolean trustedMediaPackage(String pkg) {
        return "com.nuvio.tv".equals(pkg) || "com.nuvio.app".equals(pkg)
                || "com.stremio.one".equals(pkg) || "com.netflix.ninja".equals(pkg);
    }

    static int progress(long position, long duration) {
        return duration <= 0 ? 0 : (int) Math.min(100, Math.max(0, position * 100d / duration));
    }

    static long playbackPosition(long position, long updatedAt, float speed, boolean playing, long now, long duration) {
        double value = Math.max(0, position);
        if (playing && updatedAt > 0 && Float.isFinite(speed) && speed > 0) value += Math.max(0, (double) now - updatedAt) * speed;
        return (long) Math.max(0, Math.min(duration > 0 ? duration : Long.MAX_VALUE, value));
    }

    static String weather(int code) {
        if (code == 0) return "Clear";
        if (code <= 3) return "Cloudy";
        if (code <= 48) return "Fog";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorms";
    }

    /** Keep enough pixels for the card, with a hard ceiling for extreme aspect ratios. */
    static int artworkSampleSize(int sourceWidth, int sourceHeight, int width, int height) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid artwork size");
        int sample = 1;
        while (sample < (1 << 30) && sourceWidth / (sample * 2L) >= width
                && sourceHeight / (sample * 2L) >= height) sample *= 2;
        long pixelBudget = Math.min((long) width * height, Long.MAX_VALUE / 4) * 4;
        while ((long) (sourceWidth / sample) * (sourceHeight / sample) > pixelBudget
                && sample < (1 << 30)) sample *= 2;
        return sample;
    }

    static boolean continueChannel(String pkg, String providerId) {
        return ("com.nuvio.tv".equals(pkg) || "com.nuvio.app".equals(pkg)) && pkg.equals(providerId);
    }

    static String posterKey(String type, String id, String pkg, String title) {
        if (id != null && id.matches("tt[0-9]+")) return type + ":" + id;
        return "app:" + pkg + ":" + title.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private HomePolicy() {}
}
