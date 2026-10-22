package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A quiet home: app dock, published viewing progress, and a Spotify pill. */
public class MainActivity extends Activity {
    private static final int ACCENT = 0xFF8EDFE7, MUTED = 0xFFB6C3D3;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private SharedPreferences prefs;
    private ArtworkLoader art;
    private SpotifySessions spotify;
    private MediaController music;
    private FrameLayout root, homeContent;
    private boolean arrivalPending, arrivalRowsReady, arrivalWallpaperReady;
    private final Runnable arrivalFallback = () -> finishHomeArrival(true);
    private ImageView wallpaper, wallpaperNext, musicArt, backdrop;
    private View dockSurface;
    private GlideFocus utilityGlide;
    private int backdropGeneration;
    private String backdropLocation = "";
    private LinearLayout browse;
    private boolean browseOpen;
    private int scrollTarget;
    private android.animation.ObjectAnimator scrollAnimation;
    private LinearLayout rows, musicStrip, musicText, panelFrame;
    private final List<View> glassTiles = new ArrayList<>();
    private ScrollView scroll;
    private TextView weatherLine, musicTitle, musicArtist, musicToggle, musicPrevious, musicNext, centerButton;
    private ProgressBar musicProgress;
    private int rowsGeneration, musicGeneration;
    private long appsDiscoveredAt;
    private String musicIdentity = "";
    private boolean musicFocused;
    private Bitmap wallpaperBitmap, wallpaperPreview;
    private Dialog panel;
    private Typeface font, strongFont;
    private volatile boolean active, closed;
    private boolean observerRegistered;
    private int refreshGeneration, weatherGeneration;
    private final Runnable refreshWeather = this::loadWeather;
    private String wallpaperKey = "";
    private List<ContinueWatching.Item> progress = new ArrayList<>();
    private final List<List<View>> rails = new ArrayList<>();
    private final Map<String, App> apps = new LinkedHashMap<>();
    private final Map<String, View> dockViews = new LinkedHashMap<>();
    private String programSignature = "";
    private String focusedKey;
    private View focusedCard;
    private final ContentObserver channels = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) {
            main.removeCallbacks(refreshPrograms);
            main.postDelayed(refreshPrograms, 900);
        }
    };
    private final Runnable refreshPrograms = this::loadPrograms;
    private final Runnable musicTick = new Runnable() {
        @Override public void run() {
            if (!active || music == null || musicStrip.getVisibility() != View.VISIBLE) return;
            PlaybackState state = music.getPlaybackState(); MediaMetadata data = music.getMetadata();
            if (state == null || data == null) return;
            long duration = data.getLong(MediaMetadata.METADATA_KEY_DURATION);
            long position = HomePolicy.playbackPosition(state.getPosition(), state.getLastPositionUpdateTime(), state.getPlaybackSpeed(),
                    state.getState() == PlaybackState.STATE_PLAYING, android.os.SystemClock.elapsedRealtime(), duration);
            musicProgress.setProgress(duration <= 0 ? 0 : (int) Math.min(1000, position * 1000d / duration));
            if (state.getState() == PlaybackState.STATE_PLAYING) main.postDelayed(this, 1000);
        }
    };
    private static final class App {
        final String pkg, label;
        final ComponentName component;
        final Drawable art;
        App(String pkg, String label, ComponentName component, Drawable art) {
            this.pkg = pkg; this.label = label; this.component = component; this.art = art;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        worker.allowCoreThreadTimeOut(true);
        prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        Typeface baseFont = getResources().getFont(R.font.outfit);
        font = weightedFont(baseFont, 450);
        strongFont = weightedFont(baseFont, 600);
        art = new ArtworkLoader(this);
        spotify = new SpotifySessions(this, this::updateMusic);
        buildHome();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private Typeface weightedFont(Typeface base, int weight) {
        // Typeface.create(weight) leaves this bundled variable font's axis at its thin default.
        TextView sample = new TextView(this); sample.setTypeface(base);
        sample.setFontVariationSettings("'wght' " + weight);
        return sample.getTypeface();
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setTypeface(font);
        view.setIncludeFontPadding(false);
        return view;
    }
    private GradientDrawable glass(boolean focused) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[]{0x48FFFFFF, 0x22FFFFFF} : new int[]{0xE01B252C, 0xE0151F26});
        drawable.setCornerRadius(dp(focused ? 14 : 22));
        drawable.setStroke(dp(1), focused ? 0x44FFFFFF : 0x18FFFFFF);
        return drawable;
    }
    private TextView button(String value, Runnable action) {
        TextView view = text(value, 14, Color.WHITE);
        view.setGravity(Gravity.CENTER); view.setMinHeight(dp(46)); view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setFocusable(true); view.setFocusableInTouchMode(true); view.setDefaultFocusHighlightEnabled(false); view.setClickable(true); view.setBackground(glass(false));
        view.setOnFocusChangeListener((v, focus) -> {
            view.setBackground(glass(focus));
            hover(view, focus, 1.025f);
        });
        view.setOnClickListener(v -> action.run());
        return view;
    }
    private LinearLayout vertical() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }

    private void buildHome() {
        root = new FrameLayout(this); root.setBackgroundColor(0xFF0B141B); setContentView(root);
        wallpaper = new ImageView(this); wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        wallpaperNext = new ImageView(this); wallpaperNext.setScaleType(ImageView.ScaleType.CENTER_CROP); wallpaperNext.setAlpha(0f);
        root.addView(wallpaperNext, new FrameLayout.LayoutParams(-1, -1));
        backdrop = new ImageView(this); backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP); backdrop.setAlpha(0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x4810181C, 0x0010181C, 0x6610181C}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        homeContent = new FrameLayout(this);
        root.addView(homeContent, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, dp(60)); hp.setMargins(dp(54), dp(28), dp(54), 0);
        LinearLayout clockArea = vertical();
        TextClock clock = new TextClock(this); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("h:mm");
        clock.setTextSize(28); clock.setTextColor(Color.WHITE); clock.setTypeface(font); clock.setIncludeFontPadding(false);
        clockArea.addView(clock);
        weatherLine = text("", 11, MUTED); clockArea.addView(weatherLine);
        header.addView(clockArea, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout utilities = new LinearLayout(this);
        TextView airplay = utility("AirPlay", this::openAirPlay);
        utilities.addView(airplay, new LinearLayout.LayoutParams(dp(100), dp(42)));
        centerButton = utility("Settings", this::showControlCenter);
        LinearLayout.LayoutParams settings = new LinearLayout.LayoutParams(dp(100), dp(42)); settings.leftMargin = dp(12);
        utilities.addView(centerButton, settings);
        utilityGlide = new GlideFocus(this, utilities, glass(true)); header.addView(utilityGlide);
        View.OnKeyListener topKeys = (v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) { focusDock(); return true; }
            return false;
        };
        airplay.setOnKeyListener(topKeys); centerButton.setOnKeyListener(topKeys);
        airplay.setNextFocusRightId(centerButton.getId()); centerButton.setNextFocusLeftId(airplay.getId());

        scroll = new ScrollView(this) {
            @Override public void requestChildFocus(View child, View focused) {
                int previous = getScrollY(); super.requestChildFocus(child, focused); scrollTo(getScrollX(), previous);
            }
            @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int previous = getScrollY(); super.onLayout(changed, l, t, r, b); scrollTo(getScrollX(), previous);
            }
        };
        scroll.setFillViewport(false); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -1);
        rows = vertical(); rows.setClipChildren(false); rows.setPadding(dp(54), 0, dp(54), dp(24)); scroll.addView(rows);
        homeContent.addView(scroll, sp); homeContent.addView(header, hp);

        musicStrip = new LinearLayout(this); musicStrip.setGravity(Gravity.CENTER_VERTICAL);
        musicStrip.setPadding(dp(8), dp(6), dp(8), dp(6)); musicStrip.setClipChildren(false);
        musicStrip.setBackground(glass(false)); musicStrip.setVisibility(View.GONE);
        musicArt = new ImageView(this); musicArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        musicArt.setBackground(glass(false)); musicArt.setClipToOutline(true);
        musicStrip.addView(musicArt, new LinearLayout.LayoutParams(dp(32), dp(32)));
        musicText = vertical(); musicText.setPadding(dp(10), 0, dp(8), 0);
        musicTitle = text("Spotify", 12, Color.WHITE); musicTitle.setSingleLine(true); musicTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        musicArtist = text("", 10, MUTED); musicArtist.setSingleLine(true); musicArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        musicProgress = new PlaybackProgress(this);
        musicProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        musicProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF)); musicProgress.setMax(1000);
        musicText.addView(musicTitle); gap(musicText, 2); musicText.addView(musicArtist); gap(musicText, 3);
        musicText.addView(musicProgress, new LinearLayout.LayoutParams(-1, dp(2)));
        musicText.setContentDescription("Spotify track");
        musicText.setFocusable(true); musicText.setDefaultFocusHighlightEnabled(false); musicText.setClickable(true);
        musicText.setOnClickListener(v -> launchPackage("com.spotify.tv.android"));
        musicText.setOnFocusChangeListener((v, focused) -> { if (focused) clearBackdrop(); });
        musicStrip.addView(musicText, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView spotifyMark = new ImageView(this); spotifyMark.setImageDrawable(new BrandMarkDrawable("spotify", font));
        LinearLayout.LayoutParams mark = new LinearLayout.LayoutParams(dp(13), dp(13)); mark.rightMargin = dp(4); musicStrip.addView(spotifyMark, mark);
        musicPrevious = transport("‹", "Previous track", () -> { if (music != null) music.getTransportControls().skipToPrevious(); });
        musicToggle = transport("▶", "Play or pause", this::toggleMusic);
        musicNext = transport("›", "Next track", () -> { if (music != null) music.getTransportControls().skipToNext(); });
        for (TextView control : new TextView[]{musicPrevious, musicToggle, musicNext}) musicStrip.addView(control, new LinearLayout.LayoutParams(dp(30), dp(32)));
        View.OnKeyListener musicKeys = (v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (code == KeyEvent.KEYCODE_DPAD_UP) { centerButton.requestFocus(); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (focusedCard != null) focusedCard.requestFocus(); else focusDock(); return true;
                }
            }
            return false;
        };
        musicText.setOnKeyListener(musicKeys); musicPrevious.setOnKeyListener(musicKeys); musicToggle.setOnKeyListener(musicKeys); musicNext.setOnKeyListener(musicKeys);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(355), dp(46));
        mp.leftMargin = dp(54); mp.topMargin = dp(88); homeContent.addView(musicStrip, mp);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((previous, next) -> updateMusicFocus());
    }

    private TextView utility(String label, Runnable action) {
        TextView view = button(label, action); view.setId(View.generateViewId()); view.setBackground(null); view.setTextColor(Color.WHITE); view.setAlpha(.85f);
        view.setShadowLayer(dp(2), 0, dp(1), 0xB0000000);
        view.setOnFocusChangeListener((v, focused) -> {
            view.setAlpha(focused ? 1f : .85f); utilityGlide.focus(view, focused, motion(220));
            if (focused) clearBackdrop();
        });
        return view;
    }

    @Override protected void onResume() {
        super.onResume(); active = true; art.resume();
        arrivalPending = true; arrivalRowsReady = false; arrivalWallpaperReady = false;
        homeContent.animate().cancel(); homeContent.setAlpha(0f);
        scrollTarget = 0; scroll.scrollTo(0, 0);
        main.removeCallbacks(arrivalFallback); main.postDelayed(arrivalFallback, 400);
        spotify.start();
        updateWallpaper();
        if (System.currentTimeMillis() - appsDiscoveredAt > 5000) discoverApps();
        renderRows(focusedKey); refreshContent(); loadWeather();
        if (!observerRegistered) {
            try {
                getContentResolver().registerContentObserver(Uri.parse("content://android.media.tv/watch_next_program"), true, channels);
                getContentResolver().registerContentObserver(Uri.parse("content://android.media.tv/preview_program"), true, channels);
                observerRegistered = true;
            } catch (SecurityException ignored) {}
        }
        if (getIntent().getBooleanExtra("control_center", false)) {
            getIntent().removeExtra("control_center"); main.post(this::showControlCenter);
        }
    }
    @Override protected void onPause() {
        arrivalPending = false; main.removeCallbacks(arrivalFallback);
        homeContent.animate().cancel(); homeContent.setAlpha(1f);
        active = false; main.removeCallbacks(musicTick); main.removeCallbacks(refreshPrograms);
        weatherGeneration++; refreshGeneration++; main.removeCallbacks(refreshWeather);
        art.pause(); wallpaperKey = "";
        backdropGeneration++; backdropLocation = ""; backdrop.animate().withEndAction(null).cancel(); backdrop.setAlpha(0f); backdrop.setImageDrawable(null);
        if (scrollAnimation != null) scrollAnimation.cancel();
        browseOpen = false; focusedKey = null;
        spotify.stop(); music = null; if (observerRegistered) { getContentResolver().unregisterContentObserver(channels); observerRegistered = false; }
        super.onPause();
    }
    @Override protected void onStop() {
        super.onStop();
        // Keep only a small return-frame preview, not the full wallpaper or poster bitmaps.
        if (wallpaperBitmap != null) wallpaperPreview = Bitmap.createScaledBitmap(wallpaperBitmap, 320, 180, true);
        // Home stays in the back stack during a film. Drop its pixel references,
        // not just the LRU entries; ImageViews otherwise keep those pixels alive.
        musicGeneration++; rowsGeneration++;
        releaseImages(root);
        wallpaper.setImageBitmap(wallpaperPreview);
        wallpaper.setAlpha(.9f);
        if (panelFrame != null) releaseImages(panelFrame);
        wallpaperBitmap = null; wallpaperKey = ""; musicIdentity = "";
        apps.clear(); appsDiscoveredAt = 0;
    }

    private void finishHomeArrival(boolean fallback) {
        if (!active || closed || !arrivalPending || (!fallback && (!arrivalRowsReady || !arrivalWallpaperReady))) return;
        arrivalPending = false; main.removeCallbacks(arrivalFallback);
        // The layout callback has already restored focus before this frame is drawn.
        homeContent.animate().alpha(1f).setDuration(motion(200))
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void releaseImages(View view) {
        view.animate().withEndAction(null).cancel();
        if (view instanceof ImageView) ((ImageView) view).setImageDrawable(null);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) releaseImages(group.getChildAt(i));
        }
    }

    @Override protected void onDestroy() {
        closed = true; refreshGeneration++; worker.shutdownNow(); art.close(); main.removeCallbacksAndMessages(null);
        if (panel != null) panel.dismiss(); super.onDestroy();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            focusedKey = null; browseOpen = false;
            if (panel != null) panel.dismiss();
            main.post(() -> { if (scrollAnimation != null) scrollAnimation.cancel(); scrollTarget = 0; scroll.scrollTo(0, 0); renderRows(null); });
        }
        if (intent.getBooleanExtra("control_center", false)) { intent.removeExtra("control_center"); main.post(this::showControlCenter); }
    }
    @Override public void onBackPressed() { focusDock(); animateScroll(0); }
    private void focusDock() { if (!rails.isEmpty() && !rails.get(0).isEmpty()) rails.get(0).get(0).requestFocus(); }

    private void refreshContent() { loadPrograms(); }

    private void loadWeather() {
        if (!active || closed || !WeatherData.configured(this)) return;
        main.removeCallbacks(refreshWeather);
        int request = ++weatherGeneration;
        worker.execute(() -> {
            if (!active || closed) return;
            WeatherData forecast = WeatherData.load(this);
            main.post(() -> {
                if (!active || closed || request != weatherGeneration) return;
                weatherLine.setText(forecast == null ? "" : forecast.summary);
                main.postDelayed(refreshWeather, forecast == null ? 300_000L : 3_600_000L);
            });
        });
    }

    private void loadPrograms() {
        int generation = ++refreshGeneration;
        worker.execute(() -> {
            if (!active || closed) return;
            List<ContinueWatching.Item> result = ContinueWatching.read(this);
            main.post(() -> {
                if (closed || generation != refreshGeneration) return;
                StringBuilder signature = new StringBuilder();
                for (ContinueWatching.Item item : result) signature.append(item.pkg).append(item.title).append(item.intent).append(item.position).append(item.duration).append(item.artwork).append(item.description).append(item.episode).append(item.updated).append(item.publishedWeight);
                String nextSignature = signature.toString();
                if (nextSignature.equals(programSignature)) return;
                programSignature = nextSignature; progress = result;
                if (active && panel == null) renderRows(focusedKey);
            });
        });
    }
    private void discoverApps() {
        apps.clear(); appsDiscoveredAt = System.currentTimeMillis();
        List<String> hidden = Arrays.asList(getResources().getStringArray(R.array.hidden_apps));
        for (ResolveInfo info : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg)) continue;
            String brand = BrandMarkDrawable.brand(pkg);
            Drawable banner = brand == null ? info.loadIcon(getPackageManager()) : new BrandMarkDrawable(brand, strongFont);
            apps.putIfAbsent(pkg, new App(pkg, String.valueOf(info.loadLabel(getPackageManager())),
                    new ComponentName(pkg, info.activityInfo.name), banner));
        }
        apps.put("com.gathin.tvhome.inputs", new App("com.gathin.tvhome.inputs", "Inputs", null, new BrandMarkDrawable("inputs", font)));
    }
    private List<String> dockPackages() {
        String stored = prefs.getString("dock_apps", null);
        List<String> order = stored == null ? Arrays.asList(getResources().getStringArray(R.array.dock_apps)) : Arrays.asList(stored.split(","));
        List<String> result = new ArrayList<>();
        for (String pkg : order) if (apps.containsKey(pkg) && !result.contains(pkg) && result.size() < 5) result.add(pkg);
        if (result.size() < 5) for (String pkg : apps.keySet()) if (!result.contains(pkg) && result.size() < 5) result.add(pkg);
        return result;
    }
    private void renderRows(String restoreKey) {
        int generation = ++rowsGeneration;
        View externalFocus = restoreKey == null || scroll.hasFocus() ? null : getCurrentFocus();
        if (apps.isEmpty()) discoverApps();
        rows.removeAllViews(); rails.clear(); dockViews.clear(); glassTiles.clear(); focusedCard = null;
        int screenHeight = Math.round(getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density);
        int dockTop = Math.round(screenHeight * .60f);
        gap(rows, dockTop);
        List<String> dock = dockPackages();
        LinearLayout dockRail = newRail("", true);
        for (String pkg : dock) {
            App app = apps.get(pkg); View card = appCard(app, appTileWidth(), true);
            addCard(dockRail, card, "app:" + pkg, this::clearBackdrop); dockViews.put(pkg, card);
        }
        gap(rows, Math.max(24, screenHeight - dockTop - 104 + 80));
        browse = vertical(); browse.setMinimumHeight(dp(screenHeight)); rows.addView(browse);
        if (browseOpen) {
            if (!progress.isEmpty()) {
                LinearLayout watching = newRail("Continue watching", false);
                for (ContinueWatching.Item item : progress) {
                    LinearLayout card = visualCard(item.title, watchingWidth(), Math.round(watchingWidth() * .56f), item.artwork, item.progress());
                    card.setOnClickListener(v -> openProgram(item));
                    addCard(watching, card, "watch:" + item.pkg + ":" + item.title, () -> previewBackdrop(item));
                }
            }
            List<App> remaining = new ArrayList<>();
            for (App app : apps.values()) if (!dock.contains(app.pkg)) remaining.add(app);
            remaining.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
            if (!remaining.isEmpty()) {
                LinearLayout others = newRail("Other apps", false);
                for (App app : remaining) addCard(others, appCard(app, appTileWidth(), false), "other:" + app.pkg, this::clearBackdrop);
            }
        }
        updateMusic(music);
        rows.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                rows.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            if (closed || !active || generation != rowsGeneration) return;
            arrivalRowsReady = true;
            finishHomeArrival(false);
            View restore = restoreKey == null ? null : rows.findViewWithTag(restoreKey);
            if (panel != null) return;
            if (externalFocus != null && externalFocus.isAttachedToWindow()) { externalFocus.requestFocus(); return; }
            if (restore != null) restore.requestFocus();
            else if (browseOpen && rails.size() > 1) rails.get(1).get(0).requestFocus();
            else focusDock();
        } });
    }
    private LinearLayout newRail(String title, boolean dock) {
        LinearLayout parent = dock ? rows : browse;
        if (!dock) {
            gap(parent, 16); TextView heading = text(title, 12, MUTED);
            heading.setPadding(dp(6), 0, 0, dp(8)); parent.addView(heading);
        }
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false); horizontal.setClipToPadding(false); horizontal.setClipChildren(false);
        horizontal.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout rail = new LinearLayout(this); rail.setGravity(Gravity.CENTER_VERTICAL); rail.setClipChildren(false); rail.setClipToPadding(false);
        GlideFocus surface = new GlideFocus(this, rail, glass(true));
        horizontal.setFillViewport(true); horizontal.addView(surface); rails.add(new ArrayList<>()); parent.addView(horizontal);
        if (dock) {
            dockSurface = horizontal;
            horizontal.setBackground(wallpaperBitmap == null ? glass(false) : new FrostedPanelDrawable(horizontal, root, wallpaperBitmap, "quiet"));
        }
        return rail;
    }
    private void animateScroll(int target) {
        target = Math.max(0, target);
        if (target == scrollTarget && scrollAnimation != null && scrollAnimation.isRunning()) return;
        if (scrollAnimation != null) scrollAnimation.cancel();
        scrollTarget = target;
        scrollAnimation = android.animation.ObjectAnimator.ofInt(scroll, "scrollY", scroll.getScrollY(), target);
        scrollAnimation.setDuration(motion(320));
        scrollAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)); scrollAnimation.start();
    }
    private void addCard(LinearLayout rail, View card, String key, Runnable focused) {
        if (rail.getChildCount() > 0) {
            View previous = rail.getChildAt(rail.getChildCount() - 1);
            ((LinearLayout.LayoutParams) previous.getLayoutParams()).rightMargin = dp(8);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); rail.addView(card, lp);
        registerCard(card, key, focused);
    }
    private void registerCard(View card, String key, Runnable focusAction) {
        card.setTag(key); card.setFocusable(true); card.setFocusableInTouchMode(true); card.setDefaultFocusHighlightEnabled(false); card.setClickable(true);
        List<View> row = rails.get(rails.size() - 1); row.add(card); int rowIndex = rails.size() - 1;
        GlideFocus glide = (GlideFocus) card.getParent().getParent();
        card.setOnFocusChangeListener((view, focus) -> {
            view.setBackground(null); view.setAlpha(focus ? 1f : .85f); glide.focus(view, focus, motion(220));
            if (view instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) view;
                for (int i = 0; i < layout.getChildCount(); i++) if (layout.getChildAt(i) instanceof TextView)
                    ((TextView) layout.getChildAt(i)).setTypeface(focus ? strongFont : font);
            }
            if (focus) {
                focusedKey = key; focusedCard = view; focusAction.run();
                main.post(() -> {
                    if (!view.hasFocus()) return;
                    animateScroll(rowIndex == 0 ? 0 : browse.getTop() - dp(164));
                    View parent = (View) view.getParent();
                    if (parent.getParent().getParent() instanceof HorizontalScrollView) {
                        HorizontalScrollView rail = (HorizontalScrollView) parent.getParent().getParent();
                        int visible = rail.getWidth() - rail.getPaddingLeft() - rail.getPaddingRight();
                        int target = rail.getScrollX();
                        if (view.getLeft() - dp(6) < target) target = Math.max(0, view.getLeft() - dp(6));
                        else if (view.getRight() + dp(6) > target + visible) target = view.getRight() + dp(6) - visible;
                        rail.smoothScrollTo(target, 0);
                    }
                });
            }
        });
        card.setOnKeyListener((v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int column = row.indexOf(card);
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int next = column + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (next >= 0 && next < row.size()) row.get(next).requestFocus(); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (rowIndex == 0 && code == KeyEvent.KEYCODE_DPAD_DOWN && !browseOpen) {
                    browseOpen = true; renderRows(null); return true;
                }
                int next = rowIndex + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                if (next < 0) { if (musicStrip.getVisibility() == View.VISIBLE) musicText.requestFocus(); else centerButton.requestFocus(); }
                else if (next < rails.size() && !rails.get(next).isEmpty()) rails.get(next).get(Math.min(column, rails.get(next).size()-1)).requestFocus();
                return true;
            }
            return false;
        });
    }
    private int motion(int milliseconds) { return prefs.getBoolean("reduced_motion", false) ? 0 : milliseconds; }
    private void hover(View view, boolean focused, float scale) {
        if (prefs.getBoolean("reduced_motion", false)) {
            view.animate().cancel(); view.setScaleX(1f); view.setScaleY(1f); view.setTranslationY(0);
            view.setTranslationZ(focused ? dp(8) : 0); return;
        }
        boolean app = glassTiles.contains(view);
        view.animate().scaleX(focused ? scale : 1f).scaleY(focused ? scale : 1f)
                .translationY(focused ? -dp(2) : 0).translationZ(focused ? dp(app ? 14 : 8) : 0).setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    private LinearLayout visualCard(String title, int width, int height, String url, int progressValue) {
        LinearLayout card = vertical(); card.setPadding(dp(2), dp(2), dp(2), dp(3));
        card.setElevation(dp(1)); shadow(card);
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackground(glass(false));
        image.setClipToOutline(true); card.addView(image, new LinearLayout.LayoutParams(dp(width), dp(height)));
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(dp(2), dp(2), dp(width + 2), dp(height + 2), dp(14)); outline.setAlpha(.5f);
            }
        });
        int generation = rowsGeneration;
        java.lang.ref.WeakReference<ImageView> target = new java.lang.ref.WeakReference<>(image);
        art.load(url, dp(width), dp(height), bitmap -> {
            ImageView current = target.get();
            if (active && !closed && generation == rowsGeneration && current != null) current.setImageBitmap(bitmap);
        });
        if (progressValue >= 0) {
            ProgressBar progressBar = new PlaybackProgress(this);
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
            progressBar.setProgress(progressValue); card.addView(progressBar, new LinearLayout.LayoutParams(dp(width), dp(2)));
        }
        TextView label = text(title, 12, Color.WHITE); label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(18)); lp.topMargin = dp(5); card.addView(label, lp);
        card.setContentDescription(title); return card;
    }
    private View appCard(App app, int width, boolean monochrome) {
        LinearLayout card = vertical(); card.setGravity(Gravity.CENTER); card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setMinimumWidth(dp(width)); card.setMinimumHeight(dp(88));
        card.setElevation(0);
        card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        String brand = BrandMarkDrawable.brand(app.pkg);
        ImageView mark = new ImageView(this);
        mark.setImageDrawable(monochrome && brand != null ? new BrandMarkDrawable(brand, font, true) : app.art);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(43)));
        TextView label = text(app.label, 12, Color.WHITE); label.setGravity(Gravity.CENTER); label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams caption = new LinearLayout.LayoutParams(dp(width - 20), dp(17)); caption.topMargin = dp(4); card.addView(label, caption);
        glassTiles.add(card);
        card.setContentDescription(app.label);
        ComponentName component = app.component;
        card.setOnClickListener(v -> { if (component == null) showInputs(); else open(new Intent(Intent.ACTION_MAIN).setComponent(component)); });
        return card;
    }
    private int appTileWidth() { return Math.max(92, (Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 156) / 5); }
    private int watchingWidth() { return Math.max(132, (Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 170) / 5); }
    private void shadow(View view) {
        if (android.os.Build.VERSION.SDK_INT >= 28) { view.setOutlineAmbientShadowColor(Color.BLACK); view.setOutlineSpotShadowColor(Color.BLACK); }
    }

    private void previewBackdrop(ContinueWatching.Item item) {
        String id = ContinueWatching.imdb(item);
        String location = id.isEmpty() ? item.artwork : "https://images.metahub.space/background/medium/" + id + "/img";
        if (location == null || location.isEmpty()) { clearBackdrop(); return; }
        if (location.equals(backdropLocation)) return;
        art.cancelBackdrops();
        backdropLocation = location; int request = ++backdropGeneration;
        backdrop.animate().withEndAction(null).cancel();
        backdrop.animate().alpha(0f).setDuration(motion(180)).withEndAction(() -> {
            if (request == backdropGeneration) backdrop.setImageDrawable(null);
        }).start();
        main.postDelayed(() -> {
            if (!active || closed || request != backdropGeneration) return;
            art.loadBackdrop(location, bitmap -> {
                if (!active || closed || request != backdropGeneration) return;
                backdrop.animate().withEndAction(null).cancel();
                backdrop.animate().alpha(0f).setDuration(motion(100)).withEndAction(() -> {
                    if (!active || closed || request != backdropGeneration) return;
                    backdrop.setImageBitmap(bitmap);
                    backdrop.animate().alpha(.78f).setDuration(motion(260)).withEndAction(null).start();
                }).start();
            });
        }, 280);
    }
    private void clearBackdrop() {
        if (backdropLocation.isEmpty()) return;
        art.cancelBackdrops();
        backdropLocation = ""; int request = ++backdropGeneration;
        backdrop.animate().withEndAction(null).cancel();
        backdrop.animate().alpha(0f).setDuration(motion(220)).withEndAction(() -> {
            if (request == backdropGeneration) backdrop.setImageDrawable(null);
        }).start();
    }

    private void updateWallpaper() {
        if (!active || closed) return;
        String key = prefs.getString("wallpaper_selection", "misty");
        if (!Arrays.asList("misty", "aurora", "moon_lake", "dunes", "dusk_peaks", "golden_coast", "clouds", "custom").contains(key)) key = "misty";
        if ("custom".equals(key)) key = prefs.getString("wallpaper_uri", "");
        int dim = 10;
        wallpaper.setAlpha(1f - Math.max(0, Math.min(80, dim)) / 100f);
        if (key.equals(wallpaperKey)) { arrivalWallpaperReady = wallpaperBitmap != null; finishHomeArrival(false); return; }
        wallpaperKey = key;
        String location = key.startsWith("content:") ? key : "asset:wallpapers/" + key + ".jpg";
        String requested = key;
        art.load(location, 1280, 720, bitmap -> {
            if (!active || closed || !requested.equals(wallpaperKey)) return;
            float opacity = 1f - 10 / 100f;
            wallpaper.animate().cancel(); wallpaperNext.animate().cancel();
            if (wallpaperBitmap == null) wallpaper.setImageBitmap(bitmap);
            else {
                wallpaperNext.setAlpha(0f); wallpaperNext.setImageBitmap(bitmap);
                wallpaper.animate().alpha(0f).setDuration(motion(650)).start();
                wallpaperNext.animate().alpha(opacity).setDuration(motion(650)).withEndAction(() -> {
                    wallpaper.setImageBitmap(bitmap); wallpaper.setAlpha(opacity);
                    wallpaperNext.setAlpha(0f); wallpaperNext.setImageDrawable(null);
                }).start();
            }
            wallpaperBitmap = bitmap;
            wallpaperPreview = null;
            if (dockSurface != null) dockSurface.setBackground(new FrostedPanelDrawable(dockSurface, root, bitmap, "quiet"));
            arrivalWallpaperReady = true; finishHomeArrival(false);
        });
    }
    private void chooseWallpaper() {
        LinearLayout content = vertical();
        String[] keys = {"misty", "dunes", "moon_lake"};
        String[] labels = {"Misty coast", "Dunes", "Moon Lake"};
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            addButton(content, labels[i], () -> { prefs.edit().putString("wallpaper_selection", key).apply(); updateWallpaper(); closePanel(); focusDock(); });
        }
        showPanel("Wallpaper", content);
    }

    private void showPanel(String title, LinearLayout content) {
        closePanel();
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout frame = vertical(); panelFrame = frame; frame.setPadding(dp(28), dp(24), dp(28), dp(16)); frame.setBackground(glass(false));
        TextView heading = text(title, 23, Color.WHITE); frame.addView(heading); gap(frame, 20);
        GlideFocus actions = new GlideFocus(this, content, glass(true));
        for (int i = 0; i < content.getChildCount(); i++) {
            View action = content.getChildAt(i);
            if (!action.isFocusable()) continue;
            action.setBackground(null);
            action.setOnFocusChangeListener((v, focus) -> actions.focus(v, focus, motion(220)));
        }
        ScrollView viewport = new ScrollView(this); viewport.addView(actions); frame.addView(viewport);
        dialog.setContentView(frame); Window window = dialog.getWindow();
        if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); }
        panel = dialog;
        dialog.setOnDismissListener(d -> {
            if (panel == dialog) { panel = null; panelFrame = null; main.post(this::focusDock); }
        });
        dialog.show();
        if (window != null) window.setLayout(Math.min(dp(520), getResources().getDisplayMetrics().widthPixels - dp(60)), -2);
        if (content.getChildCount() > 0) content.getChildAt(0).requestFocus();
    }
    private void closePanel() {
        if (panel != null) { Dialog previous = panel; panel = null; previous.dismiss(); }
        panelFrame = null;
    }
    private void addButton(LinearLayout parent, String label, Runnable click) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(9); parent.addView(button(label,click),lp);
    }
    private void showControlCenter() {
        LinearLayout content = vertical();
        addButton(content, "Wallpaper", this::chooseWallpaper);
        addButton(content, "Choose dock apps", this::chooseDock);
        addButton(content, "Spotify control access", () -> open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addButton(content, "TV settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        showPanel("Settings", content);
    }
    private void chooseDock(){
        List<App> choices=new ArrayList<>(apps.values());choices.sort((a,b)->a.label.compareToIgnoreCase(b.label));List<String> selected=dockPackages();
        String[] labels=new String[choices.size()];boolean[] checked=new boolean[choices.size()];
        for(int i=0;i<choices.size();i++){int slot=selected.indexOf(choices.get(i).pkg);labels[i]=choices.get(i).label+(slot>=0?" · slot "+(slot+1):"");checked[i]=slot>=0;}
        new AlertDialog.Builder(this).setTitle("Five dock apps · reselect an app to move it last")
            .setMultiChoiceItems(labels,checked,(dialog,which,isChecked)->{
                String pkg=choices.get(which).pkg;
                if(isChecked&&selected.size()>=5){((AlertDialog)dialog).getListView().setItemChecked(which,false);toast("Remove a favourite first.");}
                else if(isChecked)selected.add(pkg);else selected.remove(pkg);
            }).setPositiveButton("Save",(dialog,which)->{prefs.edit().putString("dock_apps",String.join(",",selected)).apply();renderRows(null);showControlCenter();}).setNegativeButton("Cancel",null).show();
    }
    private void showInputs(){
        LinearLayout content=vertical();TvInputManager manager=(TvInputManager)getSystemService(TV_INPUT_SERVICE);boolean any=false;
        if(manager!=null)try{for(TvInputInfo info:manager.getTvInputList())if(info.isPassthroughInput()){
            any=true;CharSequence custom=info.loadCustomLabel(this);String label=String.valueOf(custom==null?info.loadLabel(this):custom);
            boolean connected=manager.getInputState(info.getId())==TvInputManager.INPUT_STATE_CONNECTED;
            addButton(content,label+(connected?" · connected":" · no signal"),()->open(new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(info.getId()))));
        }}catch(SecurityException ignored){}
        String live=getString(R.string.live_tv_package);if(installed(live)){any=true;addButton(content,"Live TV",()->launchPackage(live));}
        if(!any)content.addView(text("No inputs reported by this TV.",17,MUTED));showPanel("Inputs",content);
    }
    private boolean installed(String pkg){try{return getPackageManager().getApplicationInfo(pkg,0).enabled;}catch(Exception ignored){return false;}}
    private void openAirPlay(){if(installed("io.github.jqssun.airplay"))launchPackage("io.github.jqssun.airplay");else toast("AirPlay receiver is not installed.");}
    static void launchSmartTube(Context context){
        for(String pkg:new String[]{"org.smarttube.beta","org.smarttube.stable","app.smarttube.fdroid","com.liskovsoft.smarttubetv.beta","com.liskovsoft.smarttubetv"}){
            Intent intent=context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(intent!=null){context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
        }Toast.makeText(context,"SmartTube is not installed",Toast.LENGTH_SHORT).show();
    }
    private void launchPackage(String pkg){Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(intent==null)intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent!=null)open(intent);else toast("App is not available.");}
    private void open(Intent intent){try{startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException|SecurityException ignored){toast("This screen is not available on your TV.");}}
    private void openProgram(ContinueWatching.Item item){
        try{
            Intent intent=Intent.parseUri(item.intent,Intent.URI_INTENT_SCHEME);intent.setSelector(null);
            if(intent.getComponent()!=null&&!item.pkg.equals(intent.getComponent().getPackageName()))intent.setComponent(null);
            intent.setPackage(item.pkg);intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);intent.setClipData(null);startActivity(intent);
        }catch(Exception ignored){launchPackage(item.pkg);}
    }
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}

    private TextView transport(String symbol, String label, Runnable action) {
        TextView view = button(symbol, action); view.setTextSize(21); view.setMinHeight(0); view.setPadding(0, 0, 0, 0);
        view.setBackground(null); view.setContentDescription(label);
        view.setOnFocusChangeListener((v, focused) -> {
            view.setBackground(focused ? glass(true) : null);
            if (focused) clearBackdrop();
        });
        return view;
    }
    private void updateMusicFocus() {
        boolean focused = musicStrip.hasFocus();
        if (focused == musicFocused) return;
        musicFocused = focused;
        GradientDrawable background = glass(false);
        background.setStroke(dp(focused ? 2 : 1), focused ? 0xEEFFFFFF : 0x18FFFFFF);
        musicStrip.setBackground(background);
        PlaybackState state = music == null ? null : music.getPlaybackState();
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        musicStrip.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)
                .translationY(focused ? -dp(2) : 0).alpha(focused || playing ? 1f : .65f)
                .setDuration(motion(180)).start();
    }
    private void updateMusic(MediaController controller) {
        music = controller; main.removeCallbacks(musicTick);
        MediaMetadata data = controller == null ? null : controller.getMetadata();
        String title = data == null ? null : data.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.isEmpty()) {
            musicGeneration++; musicIdentity = ""; musicArt.setImageDrawable(null);
            if (musicStrip.hasFocus()) focusDock(); musicStrip.setVisibility(View.GONE); return;
        }
        musicStrip.setVisibility(View.VISIBLE);
        musicTitle.setText(title); musicArtist.setText(data.getString(MediaMetadata.METADATA_KEY_ARTIST));
        String identity = title + "|" + data.getString(MediaMetadata.METADATA_KEY_ALBUM) + "|" + data.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (!identity.equals(musicIdentity)) {
            musicIdentity = identity; musicGeneration++; musicArt.setImageDrawable(null);
            Bitmap bitmap = data.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (bitmap == null) bitmap = data.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (bitmap != null) applyMusicArt(bitmap);
            else {
                int request = musicGeneration;
                String uri = data.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
                if (uri == null) uri = data.getString(MediaMetadata.METADATA_KEY_ART_URI);
                art.load(uri, 96, 96, loaded -> { if (active && request == musicGeneration) applyMusicArt(loaded); });
            }
        }
        PlaybackState state = controller.getPlaybackState();
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        musicToggle.setText(playing ? "Ⅱ" : "▶"); musicToggle.setContentDescription(playing ? "Pause" : "Play");
        long actions = state == null ? 0 : state.getActions();
        musicPrevious.setEnabled((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);
        musicNext.setEnabled((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
        musicPrevious.setAlpha(musicPrevious.isEnabled() ? 1f : .3f); musicNext.setAlpha(musicNext.isEnabled() ? 1f : .3f);
        musicStrip.animate().alpha(musicStrip.hasFocus() || playing ? 1f : .65f).setDuration(motion(250)).start();
        musicTick.run();
    }
    private void applyMusicArt(Bitmap bitmap) { musicArt.setImageBitmap(bitmap); }
    private void toggleMusic(){
        if(music==null){launchPackage("com.spotify.tv.android");return;}
        PlaybackState state=music.getPlaybackState();
        if(state!=null&&state.getState()==PlaybackState.STATE_PLAYING)music.getTransportControls().pause();else music.getTransportControls().play();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && art != null) art.trimMemory();
        if (!active && (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)) {
            wallpaperPreview = null; wallpaper.setImageDrawable(null);
        }
    }

}
