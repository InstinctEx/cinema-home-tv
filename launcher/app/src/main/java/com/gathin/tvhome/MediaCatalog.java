package com.gathin.tvhome;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Public movie metadata only. No streaming URLs, account access or watch-history claims. */
final class MediaCatalog {
    static final class Title {
        final String name, type, id;
        Title(String name, String type, String id) {
            this.name = name;
            this.type = type;
            this.id = id;
        }
    }

    static List<Title> load(Context context, int columns) {
        List<Title> titles = new ArrayList<>();
        for (String type : new String[]{"movie", "series"}) {
            File cache = new File(context.getCacheDir(), "catalog-" + type + ".json");
            String json = null;
            try {
                if (cache.isFile()) json = new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
            if (json == null || System.currentTimeMillis() - cache.lastModified() > 6 * 60 * 60 * 1000L) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(
                            "https://v3-cinemeta.strem.io/catalog/" + type + "/top.json").openConnection();
                    connection.setConnectTimeout(4000);
                    connection.setReadTimeout(4000);
                    if (connection.getResponseCode() == 200) {
                        String fresh;
                        try (InputStream input = connection.getInputStream();
                             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[8192];
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                if (bytes.size() + count > 2_000_000) throw new java.io.IOException("Catalog too large");
                                bytes.write(buffer, 0, count);
                            }
                            fresh = bytes.toString("UTF-8");
                        }
                        // Validate before replacing a working offline cache.
                        new JSONObject(fresh).getJSONArray("metas");
                        json = fresh;
                        Files.write(cache.toPath(), fresh.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception ignored) {
                    // Keep stale data if the service or connection is unavailable.
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            try {
                JSONArray metas = new JSONObject(json).getJSONArray("metas");
                int added = 0;
                for (int i = 0; i < metas.length() && added < columns; i++) {
                    JSONObject meta = metas.getJSONObject(i);
                    String id = meta.optString("id");
                    String name = meta.optString("name");
                    if (!id.matches("tt[0-9]+") || name.trim().isEmpty()) continue;
                    titles.add(new Title(name, type, id));
                    added++;
                }
            } catch (Exception ignored) {}
        }
        return titles;
    }

    private MediaCatalog() {}
}
