package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.List;

/** Reads only app-published TV rows. Does not inspect another app's private database. */
final class ContinueWatching {
    static final class Item {
        String title, pkg, intent, artwork, description, episode, type;
        long position, duration, updated, publishedWeight;
        int progress() { return HomePolicy.progress(position, duration); }
    }

    static List<Item> read(Context context) { return read(context, Collections.emptySet()); }

    static List<Item> read(Context context, Set<String> hidden) {
        List<Item> newest = new ArrayList<>();
        Map<Long, Boolean> continueChannels = new HashMap<>();
        for (String table : new String[]{"watch_next_program", "preview_program"}) {
            try (Cursor cursor = context.getContentResolver().query(
                    Uri.parse("content://android.media.tv/" + table), null, null, null, null)) {
                if (cursor == null) continue;
                while (cursor.moveToNext()) {
                    String pkg = string(cursor, "package_name");
                    if (!HomePolicy.trustedMediaPackage(pkg)) continue;
                    Item item = new Item();
                    item.pkg = pkg;
                    item.title = string(cursor, "title");
                    item.intent = string(cursor, "intent_uri");
                    item.artwork = string(cursor, "poster_art_uri");
                    item.description = string(cursor, "long_description");
                    if (item.description.isEmpty()) item.description = string(cursor, "short_description");
                    String season = string(cursor, "season_display_number"), episode = string(cursor, "episode_display_number");
                    item.episode = (season.isEmpty() ? "" : "S" + season) + (episode.isEmpty() ? "" : " · E" + episode);
                    item.type = !season.isEmpty() || !episode.isEmpty() || item.intent.contains("/series/") || item.artwork.contains("episodes.metahub") ? "series" : "movie";
                    item.position = number(cursor, "last_playback_position_millis");
                    item.duration = number(cursor, "duration_millis");
                    item.updated = Math.max(number(cursor, "last_engagement_time_utc_millis"), number(cursor, "last_playback_time_utc_millis"));
                    item.publishedWeight = number(cursor, "weight");
                    if (item.title.isEmpty() || item.intent.isEmpty()) continue;
                    if ("preview_program".equals(table)) {
                        long channelId = number(cursor, "channel_id");
                        if (!continueChannels.computeIfAbsent(channelId, id -> isContinueChannel(context, id))) continue;
                    }
                    if (!hidden.contains(key(item))) offer(newest, item, 12);
                }
            } catch (SecurityException | IllegalArgumentException ignored) { /* Permissions vary by vendor. */ }
        }
        return newest;
    }

    private static boolean isContinueChannel(Context context, long id) {
        // NUVIO's own published channel is Continue Watching. Avoid mixing generic catalog channels.
        try (Cursor channel = context.getContentResolver().query(
                Uri.parse("content://android.media.tv/channel/" + id), null, null, null, null)) {
            if (channel == null || !channel.moveToFirst()) return false;
            String pkg = string(channel, "package_name");
            return HomePolicy.continueChannel(pkg, string(channel, "internal_provider_id"));
        } catch (Exception ignored) { return false; }
    }

    static String imdb(Item item) {
        java.util.regex.Matcher match = IMDB.matcher(item.intent + " " + item.artwork);
        return match.find() ? match.group() : "";
    }
    private static final java.util.regex.Pattern IMDB = java.util.regex.Pattern.compile("tt[0-9]+");

    static String key(Item item) { return HomePolicy.posterKey(item.type, imdb(item), item.pkg, item.title); }

    /** Streaming top-N: examines every published row but retains at most N records. */
    static void offer(List<Item> newest, Item item, int limit) {
        if (limit <= 0) return;
        String identity = key(item);
        for (int i = 0; i < newest.size(); i++) {
            Item old = newest.get(i);
            if (!identity.equals(key(old))) continue;
            if (compare(old, item) >= 0) return;
            newest.remove(i); break;
        }
        int index = 0;
        while (index < newest.size() && compare(newest.get(index), item) >= 0) index++;
        if (index >= limit) return;
        newest.add(index, item);
        if (newest.size() > limit) newest.remove(limit);
    }

    private static int compare(Item a, Item b) {
        int date = Long.compare(a.updated, b.updated);
        return date != 0 ? date : Long.compare(a.publishedWeight, b.publishedWeight);
    }

    private static String string(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }
    private static long number(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getLong(index);
    }
    private ContinueWatching() {}
}
