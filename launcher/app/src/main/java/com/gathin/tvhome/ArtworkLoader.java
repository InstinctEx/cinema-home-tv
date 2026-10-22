package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Bounded decode, HTTPS-only remote art, one worker on low-RAM devices, no UI-thread image I/O. */
final class ArtworkLoader {
    interface Callback { void loaded(Bitmap bitmap); }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker;
    private final ArtworkDiskCache disk;
    private final boolean prefetchEnabled;
    private volatile boolean closed, paused;
    private volatile int generation;
    private long sequence;
    private final java.util.Map<String, Request> waiting = new java.util.HashMap<>();
    private final LruCache<String, Bitmap> cache;

    ArtworkLoader(Context context) {
        this.context = context.getApplicationContext();
        android.app.ActivityManager memory = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        boolean lowRam = memory != null && memory.isLowRamDevice();
        prefetchEnabled = !lowRam;
        int workers = lowRam ? 1 : 2;
        worker = new ThreadPoolExecutor(workers, workers, 20, TimeUnit.SECONDS, new PriorityBlockingQueue<>());
        cache = new LruCache<String, Bitmap>((lowRam ? 4 : 8) * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
        };
        disk = new ArtworkDiskCache(new java.io.File(context.getCacheDir(), "artwork-v1"), 48 * 1024 * 1024L);
        worker.allowCoreThreadTimeOut(true);
    }

    void load(String location, int width, int height, Callback callback) { request(location, width, height, callback, 1); }
    void loadBackdrop(String location, Callback callback) { request(location, 1280, 720, callback, 0); }
    void prefetch(String location) { if (prefetchEnabled) request(location, 1280, 720, null, 2); }

    private void request(String location, int width, int height, Callback callback, int priority) {
        if (closed || paused || location == null || location.isEmpty() || width <= 0 || height <= 0) return;
        String key = location + ":" + width + ":" + height;
        Bitmap hit = cache.get(key);
        if (hit != null) { if (callback != null) callback.loaded(hit); return; }
        Request pending = waiting.get(key);
        if (pending != null) {
            if (callback != null) pending.callbacks.add(callback);
            if (priority < pending.priority && worker.getQueue().remove(pending)) {
                pending.priority = priority; worker.execute(pending);
            }
            return;
        }
        Request job = new Request(key, location, width, height, priority, sequence++);
        if (callback != null) job.callbacks.add(callback);
        waiting.put(key, job); worker.execute(job);
    }

    private final class Request implements Runnable, Comparable<Request> {
        final String key, location;
        final int width, height;
        final int epoch = generation;
        final long order;
        int priority;
        final java.util.List<Callback> callbacks = new java.util.ArrayList<>();
        Request(String key, String location, int width, int height, int priority, long order) {
            this.key = key; this.location = location; this.width = width; this.height = height; this.priority = priority; this.order = order;
        }
        @Override public int compareTo(Request other) {
            int rank = Integer.compare(priority, other.priority);
            return rank != 0 ? rank : Long.compare(order, other.order);
        }
        private volatile boolean cancelled;
        private boolean obsolete() { return cancelled || closed || paused || epoch != generation; }
        @Override public void run() {
            if (obsolete()) return;
            Bitmap result = null;
            try {
                boolean remote = location.startsWith("https:");
                byte[] bytes = remote ? disk.read(location) : null;
                boolean fetched = bytes == null;
                if (fetched) {
                    try (InputStream input = stream(location); java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                        byte[] chunk = new byte[8192]; int count;
                        while (!obsolete() && (count = input.read(chunk)) != -1) {
                            if (buffer.size() + count > 8_000_000) throw new java.io.IOException("Image too large");
                            buffer.write(chunk, 0, count);
                        }
                        bytes = buffer.toByteArray();
                    }
                }
                if (obsolete()) return;
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565; options.inSampleSize = 1;
                    options.inSampleSize = HomePolicy.artworkSampleSize(bounds.outWidth, bounds.outHeight, width, height);
                    result = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    if (result != null && !obsolete()) {
                        float scale = Math.max(width / (float) result.getWidth(), height / (float) result.getHeight());
                        if (scale < 1f) {
                            Bitmap decoded = result;
                            result = Bitmap.createScaledBitmap(decoded, Math.max(1, Math.round(decoded.getWidth() * scale)),
                                    Math.max(1, Math.round(decoded.getHeight() * scale)), true);
                            if (result != decoded) decoded.recycle();
                        }
                    }
                    if (result != null && !obsolete() && remote && fetched) disk.write(location, bytes);
                }
            } catch (Exception failure) {
                android.net.Uri source = android.net.Uri.parse(location);
                android.util.Log.w("CinemaArt", source.getScheme() + "://" + source.getHost() + " " + failure.getClass().getSimpleName());
            }
            Bitmap completed = result;
            main.post(() -> {
                // A stopped activity or an older request must not repopulate the cache or
                // remove a newer request for the same image after Home resumes.
                if (obsolete() || waiting.get(key) != this) return;
                waiting.remove(key);
                if (completed != null) {
                    cache.put(key, completed);
                    for (Callback target : callbacks) target.loaded(completed);
                }
            });
        }
    }

    void cancelBackdrops() {
        java.util.Iterator<Request> requests = waiting.values().iterator();
        while (requests.hasNext()) {
            Request request = requests.next();
            if (request.priority != 0) continue;
            request.cancelled = true; request.callbacks.clear();
            worker.getQueue().remove(request); requests.remove();
        }
    }

    void trimMemory() { cache.evictAll(); }

    // Called on the UI thread, like request()/close(). Running I/O is bounded by
    // its timeout; its old epoch prevents delivery and further decoding.
    void pause() {
        paused = true; generation++;
        worker.getQueue().clear();
        for (Request request : waiting.values()) request.callbacks.clear();
        waiting.clear(); main.removeCallbacksAndMessages(null); cache.evictAll();
    }

    void resume() { if (!closed) paused = false; }

    private InputStream stream(String location) throws Exception {
        if (location.startsWith("asset:")) return context.getAssets().open(location.substring(6));
        Uri uri = Uri.parse(location);
        if ("content".equals(uri.getScheme()) || "android.resource".equals(uri.getScheme())) {
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new java.io.IOException("Missing image");
            return input;
        }
        if (!"https".equals(uri.getScheme())) throw new java.io.IOException("Unsupported image source");
        HttpURLConnection connection = null;
        URL target = new URL(location);
        for (int redirects = 0; redirects < 5; redirects++) {
            connection = (HttpURLConnection) target.openConnection();
            connection.setConnectTimeout(3500); connection.setReadTimeout(4500); connection.setInstanceFollowRedirects(false);
            final int status;
            try { status = connection.getResponseCode(); }
            catch (Exception failure) { connection.disconnect(); throw failure; }
            if (status == 200) break;
            String next = connection.getHeaderField("Location"); connection.disconnect();
            if (status < 300 || status > 399 || next == null || redirects == 4) throw new java.io.IOException("Artwork unavailable");
            target = new URL(target, next);
            if (!"https".equals(target.getProtocol())) throw new java.io.IOException("Unsafe image redirect");
        }
        final HttpURLConnection opened = connection;
        final InputStream input;
        try { input = opened.getInputStream(); }
        catch (Exception failure) { opened.disconnect(); throw failure; }
        return new java.io.FilterInputStream(input) {
            private int total;
            private void count(int n) throws java.io.IOException {
                if (n > 0 && (total += n) > 8_000_000) throw new java.io.IOException("Image too large");
            }
            @Override public int read() throws java.io.IOException { int n = super.read(); count(n < 0 ? 0 : 1); return n; }
            @Override public int read(byte[] b, int off, int len) throws java.io.IOException {
                int n = in.read(b, off, len); count(n); return n;
            }
            @Override public void close() throws java.io.IOException { try { super.close(); } finally { opened.disconnect(); } }
        };
    }

    void close() { closed = true; pause(); worker.shutdownNow(); }
}
