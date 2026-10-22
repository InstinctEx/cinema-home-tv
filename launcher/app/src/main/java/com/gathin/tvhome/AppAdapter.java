package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App tiles. The first {@link ClassicActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for D-pad Up/Down on a tile. Return true when handled. */
        boolean onTileVerticalKey(int position, int keyCode);

        void onInputsTileClicked();
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        final boolean isMedia;
        final String mediaType;
        final String mediaId;
        android.graphics.Bitmap posterBitmap;
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(label, component, banner, icon, false, null, null, 0);
        }

        Entry(String label, ComponentName component, Drawable banner, Drawable icon,
              boolean isMedia, String mediaType, String mediaId, int ambient) {
            this.label = label;
            this.component = component;
            this.pkg = component != null ? component.getPackageName() : "";
            this.banner = banner;
            this.icon = icon;
            this.isMedia = isMedia;
            this.mediaType = mediaType;
            this.mediaId = mediaId;
            this.ambient = ambient;
        }
    }

    static final float FOCUS_SCALE = 1.10f;

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (res/values/config.xml, dock_apps). */
    private final List<Entry> dockChoices = new ArrayList<>();
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();
    private final android.util.LruCache<String, Bitmap> posterCache = new android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private List<MediaCatalog.Title> catalogTitles = new ArrayList<>();
    private volatile int generation;
    private volatile boolean closed;
    private boolean catalogLoaded;

    void close() {
        closed = true;
        generation++;
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    void refreshCatalog() {
        if (catalogLoaded || closed || !shelfEnabled()) return;
        catalogLoaded = true;
        worker.execute(() -> {
            List<MediaCatalog.Title> result = MediaCatalog.load(ctx, dockSize);
            main.post(() -> {
                if (closed || result.isEmpty()) return;
                catalogTitles = result;
                reload();
            });
        });
    }

    private boolean shelfEnabled() {
        return ctx.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE).getBoolean("shelf_enabled", true);
    }

    AppAdapter(Context ctx, TileListener listener, int dockSize) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
    }

    List<Entry> dockChoices() { return new ArrayList<>(dockChoices); }

    List<String> dockPackages() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(dockSize, items.size()); i++) {
            if (!items.get(i).isMedia) result.add(items.get(i).pkg);
        }
        return result;
    }

    void reload() {
        List<String> defaults = Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps));
        String stored = ctx.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE).getString("dock_apps", null);
        List<String> favourites = stored == null ? defaults : Arrays.asList(stored.split(","));
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<Entry> fresh = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }
        fresh.add(new Entry("Inputs", new ComponentName("com.gathin.tvhome.inputs", "InputsActivity"),
                ctx.getDrawable(R.drawable.banner_inputs), ctx.getDrawable(R.drawable.ic_input)));

        dockChoices.clear();
        dockChoices.addAll(fresh);
        Collections.sort(dockChoices, (a, b) -> a.label.compareToIgnoreCase(b.label));

        // Row 0: Dock Core Apps
        List<Entry> row0 = new ArrayList<>();
        List<Entry> otherApps = new ArrayList<>();
        for (String fav : favourites) {
            for (Entry e : fresh) {
                if (fav.equals(e.pkg) && !row0.contains(e)) {
                    row0.add(e);
                    break;
                }
            }
        }
        for (Entry e : fresh) {
            if (!row0.contains(e)) otherApps.add(e);
        }
        Collections.sort(otherApps, (a, b) -> a.label.compareToIgnoreCase(b.label));

        // Guarantee row0 has exactly COLUMNS items so rows stay aligned and Stremio shelf sits in row 1
        while (row0.size() < ClassicActivity.COLUMNS && !otherApps.isEmpty()) {
            row0.add(otherApps.remove(0));
        }
        while (row0.size() > ClassicActivity.COLUMNS) {
            otherApps.add(0, row0.remove(row0.size() - 1));
        }

        // Movie and series rows use public catalogs, with configurable offline favourites as a fallback.
        List<Entry> mediaRow = new ArrayList<>();
        if (shelfEnabled()) {
            if (!catalogTitles.isEmpty()) {
                for (MediaCatalog.Title title : catalogTitles) {
                    mediaRow.add(new Entry(title.name, null, null, null,
                            true, title.type, title.id, 0xFF6C63CE));
                }
            } else {
                for (String spec : ctx.getResources().getStringArray(R.array.media_favourites)) {
                    String[] parts = spec.split("\\|", 3);
                    if (parts.length != 3 || !parts[1].matches("tt[0-9]+")
                            || !("movie".equals(parts[0]) || "series".equals(parts[0]))) continue;
                    mediaRow.add(new Entry(parts[2], null, null, null,
                            true, parts[0], parts[1], 0xFF6C63CE));
                }
            }
        }
        for (Entry entry : mediaRow) entry.posterBitmap = posterCache.get(entry.mediaId);

        items.clear();
        items.addAll(row0);
        items.addAll(mediaRow);
        items.addAll(otherApps);
        notifyDataSetChanged();

        fetchPostersAsync(mediaRow);
    }

    private void fetchPostersAsync(List<Entry> entries) {
        final int requestedGeneration = ++generation;
        worker.execute(() -> {
            for (Entry entry : entries) {
                if (closed || requestedGeneration != generation) return;
                if (posterCache.get(entry.mediaId) != null) continue;
                java.net.HttpURLConnection connection = null;
                try {
                    java.net.URL url = new java.net.URL("https://images.metahub.space/poster/small/" + entry.mediaId + "/img");
                    connection = (java.net.HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(3000);
                    connection.setReadTimeout(4000);
                    if (connection.getResponseCode() == 200) {
                        Bitmap bitmap;
                        try (java.io.InputStream input = connection.getInputStream()) {
                            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                            options.inPreferredConfig = Bitmap.Config.RGB_565;
                            bitmap = android.graphics.BitmapFactory.decodeStream(input, null, options);
                        }
                        if (bitmap != null) {
                            posterCache.put(entry.mediaId, bitmap);
                            main.post(() -> {
                                if (closed || requestedGeneration != generation) return;
                                entry.posterBitmap = bitmap;
                                int index = items.indexOf(entry);
                                if (index >= 0) notifyItemChanged(index);
                            });
                        }
                    }
                } catch (Exception ignored) {
                    // Labels and local fallback artwork remain usable offline.
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    @Override
    public int getItemViewType(int position) { return items.get(position).isMedia ? 1 : 0; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(viewType == 1 ? R.layout.item_poster : R.layout.item_app, parent, false);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        boolean focused = h.card.isFocused();
        float density = h.itemView.getResources().getDisplayMetrics().density;
        h.name.setVisibility(View.VISIBLE);
        h.name.setAlpha(focused ? 1.0f : 0.0f);
        h.name.setTranslationY(focused ? 0f : -density * 4f);
        h.name.setScaleX(focused ? 1.04f : 1.0f);
        h.name.setScaleY(focused ? 1.04f : 1.0f);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        if (e.posterBitmap != null) {
            h.banner.setImageBitmap(e.posterBitmap);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon != null ? e.icon : ctx.getDrawable(R.mipmap.ic_launcher));
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setContentDescription(e.isMedia ? e.label + " · " + MediaApps.name(ctx) : e.label);
        h.card.setOnClickListener(v -> {
            if ("com.gathin.tvhome.inputs".equals(e.pkg)) {
                if (listener != null) listener.onInputsTileClicked();
            } else if (e.isMedia) {
                MediaApps.open(ctx, e.mediaType, e.mediaId);
            } else {
                launch(e);
            }
        });
        h.card.setOnLongClickListener(v -> {
            if (e.isMedia || "com.gathin.tvhome.inputs".equals(e.pkg)) return true;
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        });
    }

    private void launch(Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if ("com.gathin.tvhome.inputs".equals(e.pkg)) return 0xFF2563EB;
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            final float radius = density * 16f;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().scaleX(s).scaleY(s)
                        .setDuration(focused ? 260 : 180)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                        .start();
                name.animate()
                        .alpha(focused ? 1.0f : 0.0f)
                        .translationY(focused ? 0f : -density * 4f)
                        .scaleX(focused ? 1.04f : 1.0f)
                        .scaleY(focused ? 1.04f : 1.0f)
                        .setDuration(focused ? 200 : 150).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
