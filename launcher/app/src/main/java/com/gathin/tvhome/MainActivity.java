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
import android.widget.ImageButton;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A quiet home: app dock, published viewing progress, and a Spotify pill. */
public class MainActivity extends Activity implements PhoneServer.Host {
  private static final int ACCENT = 0xFF8EDFE7, MUTED = 0xFFB6C3D3;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ThreadPoolExecutor worker =
      new ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
  private SharedPreferences prefs;
  private LauncherLayout layout;
  private MediaLibrary media;
  private PhoneServer phone;
  private SceneView scene;
  private android.media.tv.TvView inputScene;
  private TextClock homeClock, ambientClock;
  private LinearLayout homeHeader;
  private boolean screensaverVisible, inputWasHandled;
  private long unlockedUntil, pinRetryAt;
  private int pinFailures;
  private final Runnable idleMotion = this::startWallpaperMotion;
  private final Runnable idleSaver = this::startScreensaver;
  private final Runnable rotateWallpaper =
      new Runnable() {
        @Override
        public void run() {
          if (!active) return;
          String source = prefs.getString("wallpaper_selection", "misty");
          if (Arrays.asList("automatic", "rotation", "photo_daily", "daily").contains(source))
            updateWallpaper();
          main.postDelayed(this, 60_000);
        }
      };
  private final android.content.BroadcastReceiver wakeReceiver =
      new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (hasWindowFocus()) openBootTarget();
        }
      };

  private ArtworkLoader art;
  private SpotifySessions spotify;
  private MediaController music;
  private FrameLayout root, homeContent;
  private boolean arrivalPending,
      arrivalRowsReady,
      arrivalWallpaperReady,
      arrivalTimedOut,
      arrivalQueued;
  private int arrivalEpoch;
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
  private TextView weatherLine, musicTitle, musicArtist, centerButton;
  private ImageButton musicPrevious, musicToggle, musicNext;
  private ProgressBar musicProgress;
  private int rowsGeneration, musicGeneration;
  private long appsDiscoveredAt;
  private String musicIdentity = "";
  private Bitmap musicBitmap;
  private Boolean musicPlaying;
  private String weatherLocation = "";
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
  private final ContentObserver channels =
      new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
          main.removeCallbacks(refreshPrograms);
          main.postDelayed(refreshPrograms, 900);
        }
      };
  private final Runnable refreshPrograms = this::loadPrograms;
  private final Runnable musicTick =
      new Runnable() {
        @Override
        public void run() {
          if (!active || music == null || musicStrip.getVisibility() != View.VISIBLE) return;
          PlaybackState state = music.getPlaybackState();
          MediaMetadata data = music.getMetadata();
          if (state == null || data == null) return;
          long duration = data.getLong(MediaMetadata.METADATA_KEY_DURATION);
          long position =
              HomePolicy.playbackPosition(
                  state.getPosition(),
                  state.getLastPositionUpdateTime(),
                  state.getPlaybackSpeed(),
                  state.getState() == PlaybackState.STATE_PLAYING,
                  android.os.SystemClock.elapsedRealtime(),
                  duration);
          int value = duration <= 0 ? 0 : (int) Math.min(1000, position * 1000d / duration);
          if (musicProgress.getProgress() != value) musicProgress.setProgress(value);
          if (state.getState() == PlaybackState.STATE_PLAYING) main.postDelayed(this, 1000);
        }
      };

  private static final class App {
    final String pkg, label;
    final ComponentName component;
    final Drawable art;

    App(String pkg, String label, ComponentName component, Drawable art) {
      this.pkg = pkg;
      this.label = label;
      this.component = component;
      this.art = art;
    }
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    worker.allowCoreThreadTimeOut(true);
    prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
    layout = new LauncherLayout(this);
    media = new MediaLibrary(this);
    Typeface baseFont = getResources().getFont(R.font.outfit);
    font = weightedFont(baseFont, 450);
    strongFont = weightedFont(baseFont, 600);
    art = new ArtworkLoader(this);
    spotify = new SpotifySessions(this, this::updateMusic);
    buildHome();
    registerReceiver(wakeReceiver, new android.content.IntentFilter(Intent.ACTION_SCREEN_ON));
    FrameStats.attach(getWindow());
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private Typeface weightedFont(Typeface base, int weight) {
    // Typeface.create(weight) leaves this bundled variable font's axis at its thin default.
    TextView sample = new TextView(this);
    sample.setTypeface(base);
    sample.setFontVariationSettings("'wght' " + weight);
    return sample.getTypeface();
  }

  private TextView text(String value, int size, int color) {
    TextView view = new TextView(this);
    view.setText(value);
    view.setTextSize(size);
    view.setTextColor(color);
    view.setTypeface(font);
    view.setIncludeFontPadding(false);
    return view;
  }

  private GradientDrawable glass(boolean focused) {
    GradientDrawable drawable =
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            focused ? new int[] {0x48FFFFFF, 0x22FFFFFF} : new int[] {0xE01B252C, 0xE0151F26});
    drawable.setCornerRadius(dp(focused ? 14 : 22));
    drawable.setStroke(dp(1), focused ? 0x44FFFFFF : 0x18FFFFFF);
    return drawable;
  }

  private TextView button(String value, Runnable action) {
    TextView view = text(value, 14, Color.WHITE);
    view.setGravity(Gravity.CENTER);
    view.setMinHeight(dp(46));
    view.setPadding(dp(16), dp(12), dp(16), dp(12));
    view.setFocusable(true);
    view.setFocusableInTouchMode(true);
    view.setDefaultFocusHighlightEnabled(false);
    view.setClickable(true);
    view.setBackground(glass(false));
    view.setOnFocusChangeListener(
        (v, focus) -> {
          view.setBackground(glass(focus));
          hover(view, focus, 1.025f);
        });
    view.setOnClickListener(v -> action.run());
    return view;
  }

  private LinearLayout vertical() {
    LinearLayout view = new LinearLayout(this);
    view.setOrientation(LinearLayout.VERTICAL);
    return view;
  }

  private void gap(LinearLayout parent, int height) {
    parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
  }

  private void updateSceneGlass(Bitmap bitmap) {
    if (closed
        || !active
        || screensaverVisible
        || dockSurface == null
        || !prefs.getBoolean("home_plate", true)
        || liteEffects()) return;
    Drawable current = dockSurface.getBackground();
    if (current instanceof FrostedPanelDrawable)
      ((FrostedPanelDrawable) current)
          .updateFrame(bitmap, !prefs.getBoolean("reduced_motion", false));
    else dockSurface.setBackground(new FrostedPanelDrawable(dockSurface, root, bitmap, "quiet"));
  }

  private void buildHome() {
    root = new FrameLayout(this);
    root.setBackgroundColor(0xFF0B141B);
    setContentView(root);
    wallpaper = new ImageView(this);
    wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
    root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
    wallpaperNext = new ImageView(this);
    wallpaperNext.setScaleType(ImageView.ScaleType.CENTER_CROP);
    wallpaperNext.setAlpha(0f);
    root.addView(wallpaperNext, new FrameLayout.LayoutParams(-1, -1));
    backdrop = new ImageView(this);
    backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
    backdrop.setAlpha(0f);
    scene = new SceneView(this, prefs, media, this::updateSceneGlass);
    root.addView(scene, new FrameLayout.LayoutParams(-1, -1));
    root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
    View shade = new View(this);
    shade.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0x4810181C, 0x0010181C, 0x6610181C}));
    root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    homeContent = new FrameLayout(this);
    root.addView(homeContent, new FrameLayout.LayoutParams(-1, -1));

    LinearLayout header = new LinearLayout(this);
    homeHeader = header;
    header.setGravity(Gravity.CENTER_VERTICAL);
    FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, dp(60));
    hp.setMargins(dp(54), dp(28), dp(54), 0);
    LinearLayout clockArea = vertical();
    TextClock clock = new TextClock(this);
    homeClock = clock;
    clock.setFormat24Hour("HH:mm");
    clock.setFormat12Hour("h:mm");
    clock.setTextSize(28);
    clock.setTextColor(Color.WHITE);
    clock.setTypeface(font);
    clock.setIncludeFontPadding(false);
    clockArea.addView(clock);
    weatherLine = text("", 13, 0xFFEAEAE2);
    weatherLine.setFontVariationSettings("'wght' 500");
    weatherLine.setIncludeFontPadding(false);
    weatherLine.setSingleLine(true);
    weatherLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
    weatherLine.setPadding(0, dp(3), 0, 0);
    weatherLine.setShadowLayer(dp(3), 0, dp(1), 0xEE000000);
    weatherLine.setVisibility(View.GONE);
    clockArea.addView(weatherLine);
    header.addView(clockArea, new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout utilities = new LinearLayout(this);
    TextView airplay = utility("AirPlay", this::openAirPlay);
    utilities.addView(airplay, new LinearLayout.LayoutParams(dp(100), dp(42)));
    centerButton = utility("Settings", this::showControlCenter);
    LinearLayout.LayoutParams settings = new LinearLayout.LayoutParams(dp(100), dp(42));
    settings.leftMargin = dp(12);
    utilities.addView(centerButton, settings);
    utilityGlide = new GlideFocus(this, utilities, glass(true));
    header.addView(utilityGlide);
    View.OnKeyListener topKeys =
        (v, code, event) -> {
          if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusDock();
            return true;
          }
          return false;
        };
    airplay.setOnKeyListener(topKeys);
    centerButton.setOnKeyListener(topKeys);
    airplay.setNextFocusRightId(centerButton.getId());
    centerButton.setNextFocusLeftId(airplay.getId());

    scroll =
        new ScrollView(this) {
          @Override
          public void requestChildFocus(View child, View focused) {
            int previous = getScrollY();
            super.requestChildFocus(child, focused);
            scrollTo(getScrollX(), previous);
          }

          @Override
          protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int previous = getScrollY();
            super.onLayout(changed, l, t, r, b);
            scrollTo(getScrollX(), previous);
          }
        };
    scroll.setFillViewport(false);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -1);
    rows = vertical();
    rows.setClipChildren(false);
    rows.setPadding(dp(54), 0, dp(54), dp(24));
    scroll.addView(rows);
    homeContent.addView(scroll, sp);
    homeContent.addView(header, hp);

    musicStrip = new LinearLayout(this);
    musicStrip.setGravity(Gravity.CENTER_VERTICAL);
    musicStrip.setPadding(dp(8), dp(6), dp(8), dp(6));
    musicStrip.setClipChildren(false);
    musicStrip.setBackground(glass(false));
    musicStrip.setVisibility(View.GONE);
    musicArt = new ImageView(this);
    musicArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
    musicArt.setBackground(glass(false));
    musicArt.setClipToOutline(true);
    musicStrip.addView(musicArt, new LinearLayout.LayoutParams(dp(32), dp(32)));
    musicText = vertical();
    musicText.setPadding(dp(10), 0, dp(8), 0);
    musicTitle = text("Spotify", 12, Color.WHITE);
    musicTitle.setSingleLine(true);
    musicTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
    musicArtist = text("", 10, MUTED);
    musicArtist.setSingleLine(true);
    musicArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
    musicProgress = new PlaybackProgress(this);
    musicProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
    musicProgress.setProgressBackgroundTintList(
        android.content.res.ColorStateList.valueOf(0x33FFFFFF));
    musicProgress.setMax(1000);
    musicText.addView(musicTitle);
    gap(musicText, 2);
    musicText.addView(musicArtist);
    gap(musicText, 3);
    musicText.addView(musicProgress, new LinearLayout.LayoutParams(-1, dp(2)));
    musicText.setContentDescription("Spotify track");
    musicText.setFocusable(true);
    musicText.setDefaultFocusHighlightEnabled(false);
    musicText.setClickable(true);
    musicText.setOnClickListener(v -> launchPackage("com.spotify.tv.android"));
    musicText.setOnFocusChangeListener(
        (v, focused) -> {
          if (focused) clearBackdrop();
        });
    musicStrip.addView(musicText, new LinearLayout.LayoutParams(0, -2, 1));
    ImageView spotifyMark = new ImageView(this);
    spotifyMark.setImageDrawable(new BrandMarkDrawable("spotify", font));
    LinearLayout.LayoutParams mark = new LinearLayout.LayoutParams(dp(13), dp(13));
    mark.rightMargin = dp(4);
    musicStrip.addView(spotifyMark, mark);
    musicPrevious =
        transport(
            "previous",
            "Previous track",
            () -> {
              if (music != null) music.getTransportControls().skipToPrevious();
            });
    musicToggle = transport("play", "Play or pause", this::toggleMusic);
    musicNext =
        transport(
            "next",
            "Next track",
            () -> {
              if (music != null) music.getTransportControls().skipToNext();
            });
    for (ImageButton control : new ImageButton[] {musicPrevious, musicToggle, musicNext})
      musicStrip.addView(control, new LinearLayout.LayoutParams(dp(30), dp(32)));
    View.OnKeyListener musicKeys =
        (v, code, event) -> {
          if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
              centerButton.requestFocus();
              return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
              if (focusedCard != null) focusedCard.requestFocus();
              else focusDock();
              return true;
            }
          }
          return false;
        };
    musicText.setOnKeyListener(musicKeys);
    musicPrevious.setOnKeyListener(musicKeys);
    musicToggle.setOnKeyListener(musicKeys);
    musicNext.setOnKeyListener(musicKeys);
    FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(355), dp(46));
    mp.leftMargin = dp(54);
    mp.topMargin = dp(88);
    homeContent.addView(musicStrip, mp);
    root.getViewTreeObserver()
        .addOnGlobalFocusChangeListener((previous, next) -> updateMusicFocus());
    ambientClock = new TextClock(this);
    ambientClock.setFormat24Hour("HH:mm");
    ambientClock.setFormat12Hour("h:mm");
    ambientClock.setTextSize(54);
    ambientClock.setTypeface(font);
    ambientClock.setTextColor(0xDDEAEAE2);
    ambientClock.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
    ambientClock.setVisibility(View.GONE);
    FrameLayout.LayoutParams ac = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
    ac.setMargins(dp(54), dp(48), dp(54), 0);
    root.addView(ambientClock, ac);
  }

  private TextView utility(String label, Runnable action) {
    TextView view = button(label, action);
    view.setId(View.generateViewId());
    view.setBackground(null);
    view.setTextColor(Color.WHITE);
    view.setAlpha(.85f);
    view.setShadowLayer(dp(2), 0, dp(1), 0xB0000000);
    view.setOnFocusChangeListener(
        (v, focused) -> {
          view.setAlpha(focused ? 1f : .85f);
          utilityGlide.focus(view, focused, motion(220));
          if (focused) clearBackdrop();
        });
    return view;
  }

  @Override
  protected void onResume() {
    super.onResume();
    active = true;
    art.resume();
    arrivalPending = true;
    arrivalRowsReady = false;
    arrivalWallpaperReady = false;
    arrivalTimedOut = false;
    arrivalQueued = false;
    arrivalEpoch++;
    homeContent.animate().cancel();
    homeContent.setAlpha(.01f);
    scrollTarget = 0;
    scroll.scrollTo(0, 0);
    main.removeCallbacks(arrivalFallback);
    main.postDelayed(arrivalFallback, 400);
    spotify.start();
    updateWallpaper();
    if (System.currentTimeMillis() - appsDiscoveredAt > 5000) discoverApps();
    renderRows(focusedKey);
    refreshContent();
    loadWeather();
    if (!observerRegistered) {
      try {
        getContentResolver()
            .registerContentObserver(
                Uri.parse("content://android.media.tv/watch_next_program"), true, channels);
        getContentResolver()
            .registerContentObserver(
                Uri.parse("content://android.media.tv/preview_program"), true, channels);
        observerRegistered = true;
      } catch (SecurityException ignored) {
      }
    }
    backdropLocation = "";
    backdrop.setAlpha(0f);
    backdrop.setImageDrawable(null);
    phone = PhoneSession.current();
    if (phone != null) phone.bind(this);
    applyAppearance();
    if (continuousWallpaperMotion()) startWallpaperMotion();
    scheduleIdle();
    main.removeCallbacks(rotateWallpaper);
    main.postDelayed(rotateWallpaper, 60_000);
    if (getIntent().getBooleanExtra("cinema_boot", false)) {
      getIntent().removeExtra("cinema_boot");
      openBootTarget();
    }
    int bootCount = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    if (bootCount >= 0 && bootCount != prefs.getInt("last_boot", -2)) {
      prefs.edit().putInt("last_boot", bootCount).apply();
      openBootTarget();
    }
    if (getIntent().getBooleanExtra("control_center", false)) {
      getIntent().removeExtra("control_center");
      main.post(this::showControlCenter);
    }
  }

  private void suspendHomeWork() {
    if (!active) return;
    active = false;
    main.removeCallbacks(rotateWallpaper);
    main.removeCallbacks(idleMotion);
    main.removeCallbacks(idleSaver);
    main.removeCallbacks(musicTick);
    main.removeCallbacks(refreshPrograms);
    main.removeCallbacks(refreshWeather);
    weatherGeneration++;
    refreshGeneration++;
    art.pause();
    scene.pauseForHandoff();
    spotify.stop();
  }

  @Override
  protected void onPause() {
    suspendHomeWork();
    stopInputScene();
    screensaverVisible = false;
    main.removeCallbacks(driftClock);
    homeContent.setAlpha(1);
    ambientClock.setVisibility(View.GONE);
    scroll.setVisibility(View.VISIBLE);
    homeHeader.setVisibility(View.VISIBLE);
    unlockedUntil = 0;
    arrivalPending = false;
    arrivalQueued = false;
    arrivalEpoch++;
    main.removeCallbacks(arrivalFallback);
    homeContent.animate().cancel();
    homeContent.setAlpha(1f);
    wallpaper.animate().cancel();
    wallpaper.setAlpha(.9f);
    wallpaperKey = "";
    backdropGeneration++;
    backdropLocation = "";
    backdrop.animate().withEndAction(null).cancel();
    if (scrollAnimation != null) scrollAnimation.cancel();
    browseOpen = false;
    focusedKey = null;
    music = null;
    if (observerRegistered) {
      getContentResolver().unregisterContentObserver(channels);
      observerRegistered = false;
    }
    super.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
    // Home stays in the back stack during a film. Drop its pixel references,
    // not just the LRU entries; ImageViews otherwise keep those pixels alive.
    musicGeneration++;
    rowsGeneration++;
    releaseImages(root);
    wallpaper.setImageBitmap(wallpaperPreview);
    wallpaper.setAlpha(.9f);
    if (panelFrame != null) releaseImages(panelFrame);
    wallpaperBitmap = null;
    wallpaperKey = "";
    musicIdentity = "";
    musicBitmap = null;
    apps.clear();
    appsDiscoveredAt = 0;
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      finishHomeArrival(false);
      if (continuousWallpaperMotion()) startWallpaperMotion();
    }
  }

  private void finishHomeArrival(boolean fallback) {
    if (fallback) arrivalTimedOut = true;
    // Resume/layout can happen behind the outgoing app. Do not spend the fade there.
    if (!active
        || closed
        || !arrivalPending
        || !hasWindowFocus()
        || (!arrivalTimedOut && (!arrivalRowsReady || !arrivalWallpaperReady))) return;
    if (arrivalQueued) return;
    arrivalQueued = true;
    int epoch = arrivalEpoch;
    Runnable presented =
        () ->
            main.postDelayed(
                () -> {
                  if (epoch != arrivalEpoch || !active || closed || !arrivalPending) return;
                  arrivalQueued = false;
                  if (hasWindowFocus()) animateHomeArrival();
                },
                prefs.getBoolean("reduced_motion", false) ? 0 : 350);
    // Alpha zero skips child rendering. Present a near-transparent warm-up frame
    // so texture uploads cannot consume the entrance animation's whole duration.
    if (android.os.Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated()) {
      root.getViewTreeObserver().registerFrameCommitCallback(presented);
      root.invalidate();
    } else root.postOnAnimation(() -> root.postOnAnimation(presented));
  }

  private void animateHomeArrival() {
    arrivalPending = false;
    main.removeCallbacks(arrivalFallback);
    // The layout callback has already restored focus before this frame is drawn.
    homeContent
        .animate()
        .alpha(1f)
        .setDuration(motion(800))
        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
        .start();
    wallpaper
        .animate()
        .alpha(1f - Math.max(0, Math.min(80, prefs.getInt("wallpaper_dim", 10))) / 100f)
        .setDuration(motion(800))
        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
        .start();
  }

  private void releaseImages(View view) {
    if (view == scene) {
      scene.trimForBackground();
      return;
    }
    if (view instanceof ImageButton
        || (view instanceof ImageView
            && ((ImageView) view).getDrawable() instanceof BrandMarkDrawable)) return;
    view.animate().withEndAction(null).cancel();
    if (view instanceof ImageView) ((ImageView) view).setImageDrawable(null);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) releaseImages(group.getChildAt(i));
    }
  }

  @Override
  protected void onDestroy() {
    unregisterReceiver(wakeReceiver);
    if (scene != null) scene.close();
    closed = true;
    refreshGeneration++;
    worker.shutdownNow();
    art.close();
    main.removeCallbacksAndMessages(null);
    if (panel != null) panel.dismiss();
    super.onDestroy();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (intent.hasCategory(Intent.CATEGORY_HOME)) {
      focusedKey = null;
      browseOpen = false;
      if (panel != null) panel.dismiss();
      main.post(
          () -> {
            if (scrollAnimation != null) scrollAnimation.cancel();
            scrollTarget = 0;
            scroll.scrollTo(0, 0);
            clearBackdrop();
            renderRows(null);
            resumePanelVideo();
          });
    }
    if (intent.getBooleanExtra("control_center", false)) {
      intent.removeExtra("control_center");
      main.post(this::showControlCenter);
    }
  }

  @Override
  public void onBackPressed() {
    if (screensaverVisible) {
      stopScreensaver();
      return;
    }
    if (scroll.getScrollY() == 0 && prefs.getBoolean("back_screensaver", false)) {
      startScreensaver();
      return;
    }
    focusDock();
    animateScroll(0);
  }

  private void focusDock() {
    if (!rails.isEmpty() && !rails.get(0).isEmpty()) rails.get(0).get(0).requestFocus();
  }

  private void refreshContent() {
    loadPrograms();
  }

  private void showWeather(String summary) {
    if (android.text.TextUtils.equals(weatherLine.getText(), summary)) return;
    android.text.SpannableString text = new android.text.SpannableString(summary);
    int condition = summary.lastIndexOf("  ·  ");
    if (condition >= 0)
      text.setSpan(
          new android.text.style.ForegroundColorSpan(0xFFD4D8DA),
          condition,
          text.length(),
          android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    weatherLine.setText(text);
    weatherLine.setVisibility(summary.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void loadWeather() {
    main.removeCallbacks(refreshWeather);
    int request = ++weatherGeneration;
    if (!active || closed) return;
    if (!WeatherData.configured(this)) {
      weatherLocation = "";
      showWeather("");
      return;
    }
    String location = WeatherData.cacheFile(this).getName();
    if (!location.equals(weatherLocation) || weatherLine.getText().length() == 0) {
      weatherLocation = location;
      showWeather(WeatherData.name(this) + " · Updating weather…");
    }
    worker.execute(
        () -> {
          if (!active || closed || request != weatherGeneration) return;
          WeatherData forecast = WeatherData.load(this);
          main.post(
              () -> {
                if (!active || closed || request != weatherGeneration) return;
                showWeather(
                    forecast == null
                        ? WeatherData.name(this) + " · Weather unavailable"
                        : forecast.summary);
                main.postDelayed(refreshWeather, forecast == null ? 300_000L : 3_600_000L);
              });
        });
  }

  private void loadPrograms() {
    int generation = ++refreshGeneration;
    worker.execute(
        () -> {
          if (!active || closed) return;
          List<ContinueWatching.Item> result = ContinueWatching.read(this);
          main.post(
              () -> {
                if (closed || generation != refreshGeneration) return;
                StringBuilder signature = new StringBuilder();
                for (ContinueWatching.Item item : result)
                  signature
                      .append(item.pkg)
                      .append(item.title)
                      .append(item.intent)
                      .append(item.position)
                      .append(item.duration)
                      .append(item.artwork)
                      .append(item.description)
                      .append(item.episode)
                      .append(item.updated)
                      .append(item.publishedWeight);
                String nextSignature = signature.toString();
                if (nextSignature.equals(programSignature)) return;
                programSignature = nextSignature;
                progress = result;
                if (active && panel == null && browseOpen) renderRows(focusedKey);
              });
        });
  }

  private void discoverApps() {
    apps.clear();
    appsDiscoveredAt = System.currentTimeMillis();
    List<String> hidden = Arrays.asList(getResources().getStringArray(R.array.hidden_apps));
    List<ResolveInfo> discovered =
        new ArrayList<>(
            getPackageManager()
                .queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                    0));
    discovered.addAll(
        getPackageManager()
            .queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
    for (ResolveInfo info : discovered) {
      if (info.activityInfo == null) continue;
      String pkg = info.activityInfo.packageName;
      if (pkg.equals(getPackageName()) || hidden.contains(pkg)) continue;
      String brand = BrandMarkDrawable.brand(pkg);
      Drawable banner =
          brand == null
              ? info.loadIcon(getPackageManager())
              : new BrandMarkDrawable(brand, strongFont);
      apps.putIfAbsent(
          pkg,
          new App(
              pkg,
              layout.name(pkg, String.valueOf(info.loadLabel(getPackageManager()))),
              new ComponentName(pkg, info.activityInfo.name),
              banner));
    }
    TvInputManager inputManager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
    if (inputManager != null)
      try {
        for (TvInputInfo info : inputManager.getTvInputList())
          if (info.isPassthroughInput()) {
            String key = "input:" + info.getId();
            String label =
                layout.name(
                    key,
                    String.valueOf(
                        info.loadCustomLabel(this) == null
                            ? info.loadLabel(this)
                            : info.loadCustomLabel(this)));
            apps.put(key, new App(key, label, null, new BrandMarkDrawable("inputs", font, true)));
          }
      } catch (SecurityException ignored) {
      }
    apps.put(
        "com.gathin.tvhome.inputs",
        new App("com.gathin.tvhome.inputs", "Inputs", null, new BrandMarkDrawable("inputs", font)));
  }

  private List<String> dockPackages() {
    List<String> saved = layout.list("dock");
    String stored = saved.isEmpty() ? prefs.getString("dock_apps", null) : String.join(",", saved);
    List<String> order =
        stored == null
            ? Arrays.asList(getResources().getStringArray(R.array.dock_apps))
            : Arrays.asList(stored.split(","));
    List<String> result = new ArrayList<>();
    for (String pkg : order)
      if (apps.containsKey(pkg)
          && !layout.list("hidden").contains(pkg)
          && !result.contains(pkg)
          && result.size() < 5) result.add(pkg);
    if (stored == null && result.size() < 5)
      for (String pkg : apps.keySet())
        if (!pkg.startsWith("input:")
            && !layout.list("hidden").contains(pkg)
            && !result.contains(pkg)
            && result.size() < 5) result.add(pkg);
    return result;
  }

  private void renderRows(String restoreKey) {
    int generation = ++rowsGeneration;
    View externalFocus =
        panel != null && panel.getWindow() != null
            ? panel.getWindow().getDecorView().findFocus()
            : restoreKey == null || scroll.hasFocus() ? null : getCurrentFocus();
    if (apps.isEmpty()) discoverApps();
    rows.removeAllViews();
    rails.clear();
    dockViews.clear();
    glassTiles.clear();
    focusedCard = null;
    int screenHeight =
        Math.round(
            getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density);
    int dockTop = Math.round(screenHeight * .60f);
    gap(rows, dockTop);
    List<String> dock = dockPackages();
    LinearLayout dockRail = newRail("", true);
    if (dock.isEmpty()) {
      TextView choose = button("Choose favorite apps", this::chooseDock);
      addCard(dockRail, choose, "choose-dock", this::clearBackdrop);
    }
    for (String pkg : dock) {
      App app = apps.get(pkg);
      View card = appCard(app, appTileWidth(), true);
      addCard(dockRail, card, "app:" + pkg, this::clearBackdrop);
      dockViews.put(pkg, card);
    }
    gap(
        rows,
        prefs.getBoolean("two_rows", false)
            ? 16
            : Math.max(
                24,
                screenHeight - dockTop - 104 + (prefs.getBoolean("peek_row", false) ? -24 : 80)));
    browse = vertical();
    browse.setMinimumHeight(dp(screenHeight));
    rows.addView(browse);
    if (browseOpen || prefs.getBoolean("two_rows", false)) {
      if (!progress.isEmpty()) {
        LinearLayout watching = newRail("Continue watching", false, true);
        for (ContinueWatching.Item item : progress) {
          LinearLayout card =
              visualCard(
                  item.title,
                  watchingWidth(),
                  Math.round(watchingWidth() * .56f),
                  item.artwork,
                  item.progress());
          card.setOnClickListener(v -> openProgram(item));
          addCard(
              watching, card, "watch:" + item.pkg + ":" + item.title, () -> previewBackdrop(item));
        }
      }
      List<App> remaining = new ArrayList<>();
      org.json.JSONObject folders = layout.folders();
      java.util.Set<String> grouped = new java.util.HashSet<>();
      java.util.Iterator<String> folderNames = folders.keys();
      while (folderNames.hasNext()) {
        String name = folderNames.next();
        org.json.JSONArray members = folders.optJSONArray(name);
        LinearLayout folderRail = null;
        if (members != null)
          for (int i = 0; i < members.length(); i++) {
            String key = members.optString(i);
            App member = apps.get(key);
            if (member == null || dock.contains(key) || layout.list("hidden").contains(key))
              continue;
            if (folderRail == null) folderRail = newRail(name, false);
            addCard(
                folderRail,
                appCard(member, appTileWidth(), false),
                "other:" + key,
                this::clearBackdrop);
            grouped.add(key);
          }
      }
      for (String key : layout.ordered(apps.keySet()))
        if (!dock.contains(key) && !grouped.contains(key) && !key.startsWith("input:"))
          remaining.add(apps.get(key));
      if (!remaining.isEmpty()) {
        LinearLayout others = newRail("Other apps", false);
        for (App app : remaining)
          addCard(
              others, appCard(app, appTileWidth(), false), "other:" + app.pkg, this::clearBackdrop);
      }
    }
    updateMusic(music);
    rows.getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                rows.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (closed || !active || generation != rowsGeneration) return;
                arrivalRowsReady = true;
                finishHomeArrival(false);
                View restore = restoreKey == null ? null : rows.findViewWithTag(restoreKey);
                if (panel != null) return;
                if (externalFocus != null && externalFocus.isAttachedToWindow()) {
                  externalFocus.requestFocus();
                  return;
                }
                if (restore != null) restore.requestFocus();
                else if (browseOpen && rails.size() > 1) rails.get(1).get(0).requestFocus();
                else focusDock();
              }
            });
  }

  private LinearLayout newRail(String title, boolean dock) {
    return newRail(title, dock, false);
  }

  private LinearLayout newRail(String title, boolean dock, boolean posters) {
    LinearLayout parent = dock ? rows : browse;
    if (!dock) {
      gap(parent, 16);
      TextView heading = text(title, 12, MUTED);
      heading.setPadding(dp(6), 0, 0, dp(8));
      parent.addView(heading);
    }
    HorizontalScrollView horizontal = new HorizontalScrollView(this);
    horizontal.setHorizontalScrollBarEnabled(false);
    horizontal.setClipToPadding(false);
    horizontal.setClipChildren(false);
    horizontal.setPadding(dp(8), dp(8), dp(8), dp(8));
    LinearLayout rail = new LinearLayout(this);
    rail.setGravity(Gravity.CENTER_VERTICAL);
    rail.setClipChildren(false);
    rail.setClipToPadding(false);
    GradientDrawable outline = new GradientDrawable();
    outline.setCornerRadius(dp(10));
    outline.setStroke(dp(prefs.getBoolean("high_contrast", false) ? 3 : 2), 0xFFEAEAE2);
    GlideFocus surface =
        new GlideFocus(
            this,
            rail,
            posters ? outline : glass(true),
            posters ? (prefs.getBoolean("reduced_motion", false) ? 1f : 1.045f) : 0f);
    horizontal.setFillViewport(true);
    horizontal.addView(surface);
    rails.add(new ArrayList<>());
    parent.addView(horizontal);
    if (dock) {
      dockSurface = horizontal;
      horizontal.setBackground(
          !prefs.getBoolean("home_plate", true)
              ? null
              : wallpaperBitmap == null || liteEffects()
                  ? glass(false)
                  : new FrostedPanelDrawable(horizontal, root, wallpaperBitmap, "quiet"));
    }
    return rail;
  }

  private void animateScroll(int target) {
    target = Math.max(0, target);
    if (target == scrollTarget && scrollAnimation != null && scrollAnimation.isRunning()) return;
    if (scrollAnimation != null) scrollAnimation.cancel();
    scrollTarget = target;
    scrollAnimation =
        android.animation.ObjectAnimator.ofInt(scroll, "scrollY", scroll.getScrollY(), target);
    scrollAnimation.setDuration(motion(320));
    scrollAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
    scrollAnimation.start();
  }

  private void addCard(LinearLayout rail, View card, String key, Runnable focused) {
    if (rail.getChildCount() > 0) {
      View previous = rail.getChildAt(rail.getChildCount() - 1);
      ((LinearLayout.LayoutParams) previous.getLayoutParams()).rightMargin =
          dp(key.startsWith("watch:") ? 12 : 8);
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    rail.addView(card, lp);
    registerCard(card, key, focused);
  }

  private void registerCard(View card, String key, Runnable focusAction) {
    card.setTag(key);
    card.setFocusable(true);
    card.setFocusableInTouchMode(true);
    card.setDefaultFocusHighlightEnabled(false);
    card.setClickable(true);
    List<View> row = rails.get(rails.size() - 1);
    row.add(card);
    int rowIndex = rails.size() - 1;
    GlideFocus glide = (GlideFocus) card.getParent().getParent();
    card.setOnFocusChangeListener(
        (view, focus) -> {
          view.setBackground(null);
          boolean poster = key.startsWith("watch:");
          view.setAlpha(focus ? 1f : poster ? .94f : .85f);
          if (poster) {
            float scale = focus && !prefs.getBoolean("reduced_motion", false) ? 1.045f : 1f;
            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(motion(220))
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .start();
          }
          glide.focus(view, focus, motion(220));
          if (view instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) view;
            for (int i = 0; i < layout.getChildCount(); i++)
              if (layout.getChildAt(i) instanceof TextView) {
                ((TextView) layout.getChildAt(i)).setTypeface(focus ? strongFont : font);
                if (key.startsWith("app:") || key.startsWith("other:"))
                  layout
                      .getChildAt(i)
                      .setVisibility(
                          focus || prefs.getBoolean("show_names", true)
                              ? View.VISIBLE
                              : View.INVISIBLE);
              }
          }
          if (focus) {
            if (!poster && prefs.getBoolean("high_contrast", false))
              view.setBackground(glass(true));
            focusedKey = key;
            focusedCard = view;
            focusAction.run();
            if (prefs.getString("wallpaper_selection", "misty").equals("follow_app")
                && (key.startsWith("app:") || key.startsWith("other:"))) {
              int color =
                  key.contains("spotify")
                      ? 0xFF173B32
                      : key.contains("nuvio")
                          ? 0xFF283046
                          : key.contains("smarttube") ? 0xFF44282B : 0xFF273B43;
              backdrop.setImageDrawable(
                  new GradientDrawable(
                      GradientDrawable.Orientation.TOP_BOTTOM, new int[] {color, 0xFF10171C}));
              backdrop.setAlpha(.95f);
            }
            main.post(
                () -> {
                  if (!view.hasFocus()) return;
                  animateScroll(rowIndex == 0 ? 0 : browse.getTop() - dp(164));
                  View parent = (View) view.getParent();
                  if (parent.getParent().getParent() instanceof HorizontalScrollView) {
                    HorizontalScrollView rail =
                        (HorizontalScrollView) parent.getParent().getParent();
                    int visible = rail.getWidth() - rail.getPaddingLeft() - rail.getPaddingRight();
                    int target = rail.getScrollX();
                    if (view.getLeft() - dp(6) < target)
                      target = Math.max(0, view.getLeft() - dp(6));
                    else if (view.getRight() + dp(6) > target + visible)
                      target = view.getRight() + dp(6) - visible;
                    rail.smoothScrollTo(target, 0);
                  }
                });
          }
        });
    card.setOnKeyListener(
        (v, code, event) -> {
          if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
          int column = row.indexOf(card);
          if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int next = column + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            if (next >= 0 && next < row.size()) row.get(next).requestFocus();
            return true;
          }
          if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (rowIndex == 0 && code == KeyEvent.KEYCODE_DPAD_DOWN && !browseOpen) {
              browseOpen = true;
              renderRows(null);
              return true;
            }
            int next = rowIndex + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
            if (next < 0) {
              if (musicStrip.getVisibility() == View.VISIBLE) musicText.requestFocus();
              else centerButton.requestFocus();
            } else if (next < rails.size() && !rails.get(next).isEmpty())
              rails.get(next).get(Math.min(column, rails.get(next).size() - 1)).requestFocus();
            return true;
          }
          return false;
        });
  }

  private int motion(int milliseconds) {
    return prefs.getBoolean("reduced_motion", false) ? 0 : milliseconds;
  }

  private void hover(View view, boolean focused, float scale) {
    if (prefs.getBoolean("reduced_motion", false)) {
      view.animate().cancel();
      view.setScaleX(1f);
      view.setScaleY(1f);
      view.setTranslationY(0);
      view.setTranslationZ(focused ? dp(8) : 0);
      return;
    }
    boolean app = glassTiles.contains(view);
    view.animate()
        .scaleX(focused ? scale : 1f)
        .scaleY(focused ? scale : 1f)
        .translationY(focused ? -dp(2) : 0)
        .translationZ(focused ? dp(app ? 14 : 8) : 0)
        .setDuration(180)
        .setInterpolator(new android.view.animation.DecelerateInterpolator())
        .start();
  }

  private LinearLayout visualCard(
      String title, int width, int height, String url, int progressValue) {
    LinearLayout card = vertical();
    card.setPadding(dp(2), dp(2), dp(2), dp(3));
    card.setElevation(dp(1));
    shadow(card);
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    FrameLayout picture = new FrameLayout(this);
    GradientDrawable shape = glass(false);
    shape.setCornerRadius(dp(8));
    picture.setBackground(shape);
    picture.setClipToOutline(true);
    picture.addView(image, new FrameLayout.LayoutParams(-1, -1));
    card.addView(picture, new LinearLayout.LayoutParams(dp(width), dp(height)));
    card.setOutlineProvider(
        new android.view.ViewOutlineProvider() {
          @Override
          public void getOutline(View view, android.graphics.Outline outline) {
            outline.setRoundRect(dp(2), dp(2), dp(width + 2), dp(height + 2), dp(8));
            outline.setAlpha(.5f);
          }
        });
    int generation = rowsGeneration;
    java.lang.ref.WeakReference<ImageView> target = new java.lang.ref.WeakReference<>(image);
    art.load(
        url,
        dp(width),
        dp(height),
        bitmap -> {
          ImageView current = target.get();
          if (active && !closed && generation == rowsGeneration && current != null)
            current.setImageBitmap(bitmap);
        });
    if (progressValue >= 0) {
      ProgressBar progressBar = new PlaybackProgress(this);
      progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
      progressBar.setProgressBackgroundTintList(
          android.content.res.ColorStateList.valueOf(0x33FFFFFF));
      progressBar.setProgress(progressValue);
      FrameLayout.LayoutParams progress = new FrameLayout.LayoutParams(-1, dp(2), Gravity.BOTTOM);
      progress.setMargins(dp(8), 0, dp(8), dp(6));
      picture.addView(progressBar, progress);
    }
    TextView label = text(title, 12, Color.WHITE);
    label.setSingleLine(true);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(18));
    lp.topMargin = dp(8);
    card.addView(label, lp);
    card.setContentDescription(title);
    return card;
  }

  private View appCard(App app, int width, boolean monochrome) {
    LinearLayout card = vertical();
    card.setGravity(Gravity.CENTER);
    card.setPadding(dp(10), dp(8), dp(10), dp(8));
    card.setMinimumWidth(dp(width));
    card.setMinimumHeight(dp(88));
    card.setElevation(0);
    card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
    String brand = BrandMarkDrawable.brand(app.pkg);
    ImageView mark = new ImageView(this);
    mark.setImageDrawable(
        monochrome && brand != null ? new BrandMarkDrawable(brand, font, true) : app.art);
    String device = prefs.getString("device:" + app.pkg, "inputs");
    if (app.pkg.startsWith("input:"))
      mark.setImageDrawable(new BrandMarkDrawable(device, font, true));
    String customIcon = prefs.getString("icon:" + app.pkg, "");
    if (FeaturePolicy.mediaId(customIcon) && media.file(customIcon).isFile())
      art.load(
          "private:" + customIcon,
          100,
          80,
          bitmap -> {
            if (!closed) mark.setImageBitmap(bitmap);
          });
    mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
    card.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(43)));
    TextView label = text(app.label, 12, Color.WHITE);
    label.setGravity(Gravity.CENTER);
    label.setSingleLine(true);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams caption = new LinearLayout.LayoutParams(dp(width - 20), dp(17));
    caption.topMargin = dp(4);
    card.addView(label, caption);
    label.setVisibility(prefs.getBoolean("show_names", true) ? View.VISIBLE : View.GONE);
    glassTiles.add(card);
    card.setContentDescription(app.label);
    ComponentName component = app.component;
    card.setOnClickListener(
        v -> {
          if (app.pkg.startsWith("input:")) openInput(app.pkg.substring(6));
          else if (component == null) showInputs();
          else open(new Intent(Intent.ACTION_MAIN).setComponent(component));
        });
    card.setOnLongClickListener(
        v -> {
          showAppActions(app);
          return true;
        });
    return card;
  }

  private int appTileWidth() {
    return Math.max(
        92,
        (Math.round(
                    getResources().getDisplayMetrics().widthPixels
                        / getResources().getDisplayMetrics().density)
                - 156)
            / 5);
  }

  private int watchingWidth() {
    return Math.max(
        132,
        (Math.round(
                    getResources().getDisplayMetrics().widthPixels
                        / getResources().getDisplayMetrics().density)
                - 178)
            / 4);
  }

  private void shadow(View view) {
    if (android.os.Build.VERSION.SDK_INT >= 28) {
      view.setOutlineAmbientShadowColor(Color.BLACK);
      view.setOutlineSpotShadowColor(Color.BLACK);
    }
  }

  private void previewBackdrop(ContinueWatching.Item item) {
    if (!prefs.getBoolean("cinematic_artwork", true)) {
      clearBackdrop();
      return;
    }
    String id = ContinueWatching.imdb(item);
    String location =
        id.isEmpty()
            ? item.artwork
            : "https://images.metahub.space/background/medium/" + id + "/img";
    if (location == null || location.isEmpty()) {
      clearBackdrop();
      return;
    }
    if (location.equals(backdropLocation)) return;
    art.cancelBackdrops();
    backdropLocation = location;
    int request = ++backdropGeneration;
    main.postDelayed(
        () -> {
          if (!active || closed || request != backdropGeneration) return;
          art.loadBackdrop(
              location,
              bitmap -> {
                if (!active || closed || request != backdropGeneration || bitmap == null) return;
                Drawable previous = backdrop.getDrawable();
                if (previous instanceof android.graphics.drawable.TransitionDrawable)
                  previous =
                      ((android.graphics.drawable.TransitionDrawable) previous).getDrawable(1);
                backdrop.animate().withEndAction(null).cancel();
                Drawable next =
                    new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
                if (previous == null) {
                  backdrop.setImageDrawable(next);
                  backdrop
                      .animate()
                      .alpha(1f)
                      .setDuration(motion(260))
                      .withEndAction(
                          () -> {
                            if (active
                                && !backdropLocation.isEmpty()
                                && continuousWallpaperMotion()) scene.hold();
                          })
                      .start();
                } else {
                  android.graphics.drawable.TransitionDrawable blend =
                      new android.graphics.drawable.TransitionDrawable(
                          new Drawable[] {previous, next});
                  blend.setCrossFadeEnabled(false);
                  backdrop.setImageDrawable(blend);
                  backdrop.setAlpha(1f);
                  blend.startTransition(motion(260));
                  if (continuousWallpaperMotion()) scene.hold();
                }
              });
        },
        280);
  }

  private void clearBackdrop() {
    if (backdropLocation.isEmpty()) return;
    art.cancelBackdrops();
    backdropLocation = "";
    int request = ++backdropGeneration;
    resumePanelVideo();
    backdrop.animate().withEndAction(null).cancel();
    backdrop
        .animate()
        .alpha(0f)
        .setDuration(motion(220))
        .withEndAction(
            () -> {
              if (request == backdropGeneration) backdrop.setImageDrawable(null);
            })
        .start();
  }

  private void updateWallpaper() {
    if (!active || closed) return;
    String key = prefs.getString("wallpaper_selection", "misty");
    Bitmap frame = scene.returnFrame();
    if ((key.equals("aerial") || key.equals("video")) && frame != null) {
      wallpaperKey = "scene-return";
      wallpaperBitmap = wallpaperPreview = frame;
      wallpaper.animate().cancel();
      wallpaper.setImageBitmap(frame);
      wallpaper.setAlpha(1f);
      wallpaperNext.animate().cancel();
      wallpaperNext.setImageDrawable(null);
      wallpaperNext.setAlpha(0f);
      arrivalWallpaperReady = true;
      if (dockSurface != null && prefs.getBoolean("home_plate", true) && !liteEffects())
        dockSurface.setBackground(new FrostedPanelDrawable(dockSurface, root, frame, "quiet"));
      finishHomeArrival(false);
      return;
    }
    if (key.equals("daily")) loadDailyWallpaper();
    if (key.equals("automatic") || key.equals("rotation"))
      key =
          HomePolicy.wallpaper(
              key,
              java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
              (int)
                  (System.currentTimeMillis()
                      / (prefs.getInt("wallpaper_interval", 60) * 60_000L)));
    if (Arrays.asList("aerial", "video", "photos", "photo_daily", "daily", "follow_app")
        .contains(key)) key = "misty";
    String uploaded = prefs.getString("personal_wallpaper", "");
    if (prefs.getString("wallpaper_selection", "misty").equals("photo_daily")) {
      List<java.io.File> photos = media.files(false);
      if (!photos.isEmpty())
        uploaded =
            photos
                .get(Math.floorMod((int) (System.currentTimeMillis() / 86400000L), photos.size()))
                .getName();
    }
    if (Arrays.asList("photos", "photo_daily")
            .contains(prefs.getString("wallpaper_selection", "misty"))
        && FeaturePolicy.mediaId(uploaded)
        && media.file(uploaded).isFile()) key = "private:" + uploaded;
    if (!key.startsWith("private:")
        && !Arrays.asList(
                "misty",
                "aurora",
                "moon_lake",
                "dunes",
                "dusk_peaks",
                "golden_coast",
                "clouds",
                "custom")
            .contains(key)) key = "misty";
    if ("custom".equals(key)) key = prefs.getString("wallpaper_uri", "");
    int dim = prefs.getInt("wallpaper_dim", 10);
    wallpaper.setAlpha(arrivalPending ? .45f : 1f - Math.max(0, Math.min(80, dim)) / 100f);
    if (key.equals(wallpaperKey)) {
      arrivalWallpaperReady = wallpaperBitmap != null;
      finishHomeArrival(false);
      return;
    }
    wallpaperKey = key;
    String location =
        key.startsWith("content:") || key.startsWith("private:")
            ? key
            : "asset:wallpapers/" + key + ".jpg";
    String requested = key;
    art.load(
        location,
        1280,
        720,
        bitmap -> {
          if (!active || closed || !requested.equals(wallpaperKey)) return;
          float opacity = 1f - prefs.getInt("wallpaper_dim", 10) / 100f;
          wallpaper.animate().cancel();
          wallpaperNext.animate().cancel();
          if (wallpaperBitmap == null || arrivalPending) {
            wallpaper.setImageBitmap(bitmap);
            wallpaperNext.setAlpha(0f);
            wallpaperNext.setImageDrawable(null);
            // A slow image may arrive after the readiness fallback began the entrance.
            if (!arrivalPending)
              wallpaper.animate().alpha(opacity).setDuration(motion(250)).start();
          } else {
            wallpaperNext.setAlpha(0f);
            wallpaperNext.setImageBitmap(bitmap);
            wallpaper.animate().alpha(0f).setDuration(motion(650)).start();
            wallpaperNext
                .animate()
                .alpha(opacity)
                .setDuration(motion(650))
                .withEndAction(
                    () -> {
                      wallpaper.setImageBitmap(bitmap);
                      wallpaper.setAlpha(opacity);
                      wallpaperNext.setAlpha(0f);
                      wallpaperNext.setImageDrawable(null);
                    })
                .start();
          }
          wallpaperBitmap = bitmap;
          Looper.myQueue()
              .addIdleHandler(
                  () -> {
                    if (active && wallpaperBitmap == bitmap)
                      wallpaperPreview = Bitmap.createScaledBitmap(bitmap, 320, 180, true);
                    return false;
                  });
          if (dockSurface != null)
            dockSurface.setBackground(
                !prefs.getBoolean("home_plate", true)
                    ? null
                    : liteEffects()
                        ? glass(false)
                        : new FrostedPanelDrawable(dockSurface, root, bitmap, "quiet"));
          arrivalWallpaperReady = true;
          finishHomeArrival(false);
        });
  }

  private void chooseWallpaper() {
    LinearLayout content = vertical();
    String[] keys = {
      "misty",
      "dunes",
      "moon_lake",
      "aurora",
      "golden_coast",
      "clouds",
      "automatic",
      "rotation",
      "photos",
      "photo_daily",
      "aerial",
      "video",
      "daily",
      "follow_app"
    };
    String[] labels = {
      "Misty coast",
      "Dunes",
      "Moon Lake",
      "Aurora",
      "Golden coast",
      "Clouds",
      "Time of day",
      "Scene rotation",
      "My photos",
      "My photos · daily",
      "Aerial films",
      "My videos",
      "Daily photograph",
      "Follow focused app"
    };
    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];
      addButton(
          content,
          labels[i],
          () -> {
            prefs.edit().putString("wallpaper_selection", key).apply();
            updateWallpaper();
            closePanel();
            focusDock();
            scheduleIdle();
            if (key.equals("aerial") || key.equals("video") || key.equals("photos")) {
              startWallpaperMotion();
              if (key.equals("aerial")) toast("Loading aerial film…");
            }
          });
    }
    showPanel("Wallpaper", content);
  }

  private void showPanel(String title, LinearLayout content) {
    scene.hold();
    stopInputScene();
    closePanel();
    Dialog dialog = new Dialog(this);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout frame = vertical();
    panelFrame = frame;
    frame.setPadding(dp(28), dp(24), dp(28), dp(16));
    frame.setBackground(glass(false));
    TextView heading = text(title, 23, Color.WHITE);
    frame.addView(heading);
    gap(frame, 20);
    GlideFocus actions = new GlideFocus(this, content, glass(true));
    for (int i = 0; i < content.getChildCount(); i++) {
      View action = content.getChildAt(i);
      if (!action.isFocusable()) continue;
      action.setBackground(null);
      if (action instanceof TextView)
        ((TextView) action).setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
      action.setOnFocusChangeListener((v, focus) -> actions.focus(v, focus, motion(220)));
    }
    ScrollView viewport = new ScrollView(this);
    viewport.addView(actions);
    frame.addView(viewport);
    dialog.setContentView(frame);
    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawableResource(android.R.color.transparent);
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }
    panel = dialog;
    dialog.setOnDismissListener(
        d -> {
          if (panel == dialog) {
            panel = null;
            panelFrame = null;
            main.post(
                () -> {
                  if (panel == null && active) {
                    focusDock();
                    resumePanelVideo();
                  }
                });
          }
        });
    dialog.show();
    if (window != null)
      window.setLayout(
          Math.min(dp(520), getResources().getDisplayMetrics().widthPixels - dp(60)), -2);
    List<View> choices = content.getFocusables(View.FOCUS_FORWARD);
    for (View choice : choices) {
      if (choices.size() > 1
          && choice instanceof TextView
          && ((TextView) choice).getText().toString().startsWith("‹")) continue;
      choice.requestFocus();
      break;
    }
  }

  private void closePanel() {
    if (panel != null) {
      Dialog previous = panel;
      panel = null;
      previous.dismiss();
    }
    panelFrame = null;
    main.post(this::resumePanelVideo);
  }

  private void resumePanelVideo() {
    if (!active || closed || panel != null || !backdropLocation.isEmpty()) return;
    if (continuousWallpaperMotion()) startWallpaperMotion();
    scheduleIdle();
  }

  private void addButton(LinearLayout parent, String label, Runnable click) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(9);
    parent.addView(button(label, click), lp);
  }

  private void addSettingSwitch(LinearLayout parent, String label, String key, boolean fallback) {
    android.widget.Switch toggle = new android.widget.Switch(this);
    toggle.setText(label);
    toggle.setTextColor(Color.WHITE);
    toggle.setTextSize(14);
    toggle.setTypeface(font);
    toggle.setShowText(false);
    toggle.setSplitTrack(false);
    toggle.setSwitchMinWidth(dp(52));
    toggle.setSwitchPadding(dp(24));
    toggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    toggle.setPadding(dp(16), dp(12), dp(16), dp(12));
    toggle.setMinHeight(dp(56));
    toggle.setFocusable(true);
    toggle.setFocusableInTouchMode(true);
    toggle.setDefaultFocusHighlightEnabled(false);
    android.graphics.drawable.GradientDrawable track =
        new android.graphics.drawable.GradientDrawable();
    track.setCornerRadius(dp(16));
    track.setSize(dp(52), dp(30));
    track.setColor(0xFF465159);
    track.setTintList(
        new android.content.res.ColorStateList(
            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
            new int[] {0xFF34C759, 0xFF465159}));
    android.graphics.drawable.GradientDrawable thumb =
        new android.graphics.drawable.GradientDrawable();
    thumb.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    thumb.setColor(0xFFF7F8F5);
    thumb.setSize(dp(26), dp(26));
    toggle.setTrackDrawable(track);
    toggle.setThumbDrawable(new android.graphics.drawable.InsetDrawable(thumb, dp(2)));
    toggle.setChecked(prefs.getBoolean(key, fallback));
    toggle.jumpDrawablesToCurrentState();
    toggle.setOnCheckedChangeListener(
        (button, checked) -> {
          prefs.edit().putBoolean(key, checked).apply();
          if (key.equals("scene_shuffle") || key.equals("scene_favorites_only"))
            scene.preferencesChanged();
          else refreshFeatures();
          if (prefs.getBoolean("reduced_motion", false)) toggle.jumpDrawablesToCurrentState();
          toggle.requestFocus();
        });
    toggle.setOnKeyListener(
        (v, code, event) -> {
          if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_UP) toggle.toggle();
            return true;
          }
          if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT)
            return false;
          if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
            toggle.setChecked(code == KeyEvent.KEYCODE_DPAD_RIGHT);
          return true;
        });
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.bottomMargin = dp(3);
    parent.addView(toggle, params);
  }

  private void showControlCenter() {
    LinearLayout content = vertical();
    String[][] sections = {
      {"appearance", "Appearance", "Wallpaper, layout & clock"},
      {"scenes", "Scenes", "Browse, favorites & playback"},
      {"apps", "Apps & inputs", "Your dock, devices & hidden apps"},
      {"connections", "Connections", "Phone remote, Wi-Fi & music"},
      {"sound", "Sound & picture", "Volume, mute & brightness"},
      {"system", "System", "Privacy, backups & updates"}
    };
    int width =
        Math.min(
            680,
            Math.round(
                    getResources().getDisplayMetrics().widthPixels
                        / getResources().getDisplayMetrics().density)
                - 60);
    int cardWidth = (width - 68) / 2;
    for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
      LinearLayout row = new LinearLayout(this);
      row.setClipChildren(false);
      row.setMinimumWidth(cardWidth * 2 + 12);
      GlideFocus glide = new GlideFocus(this, row, glass(true));
      for (int column = 0; column < 2; column++) {
        String[] section = sections[rowIndex * 2 + column];
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(18), dp(10), dp(18));
        card.setFocusable(true);
        card.setClickable(true);
        card.setDefaultFocusHighlightEnabled(false);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new SettingsIconDrawable(section[0]));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(34), dp(34));
        ip.rightMargin = dp(14);
        card.addView(icon, ip);
        LinearLayout labels = vertical();
        TextView title = text(section[1], 18, Color.WHITE);
        title.setTypeface(strongFont);
        labels.addView(title);
        gap(labels, 5);
        TextView description = text(section[2], 11, MUTED);
        description.setMaxLines(2);
        labels.addView(description);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        card.setContentDescription(section[1] + ". " + section[2]);
        card.setOnClickListener(v -> showSettingsCategory(section[0]));
        card.setOnFocusChangeListener((v, focused) -> glide.focus(v, focused, motion(220)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(cardWidth), dp(96));
        if (column == 0) cp.rightMargin = dp(12);
        row.addView(card, cp);
      }
      content.addView(glide);
      gap(content, 10);
    }
    showPanel("Settings", content);
    panelBack(
        () -> {
          closePanel();
          focusDock();
        });
    if (panel != null && panel.getWindow() != null) panel.getWindow().setLayout(dp(width), -2);
    List<View> focusable = content.getFocusables(View.FOCUS_FORWARD);
    if (!focusable.isEmpty()) focusable.get(0).requestFocus();
  }

  private void showSettingsCategory(String category) {
    if (category.equals("sound")) {
      showVolumePanel();
      return;
    }
    LinearLayout content = vertical();
    String title;
    switch (category) {
      case "appearance":
        title = "Appearance";
        addButton(content, "Wallpaper", this::chooseWallpaper);
        addButton(content, "App layout", () -> showAppearanceGroup("layout"));
        addButton(content, "Clock & widgets", () -> showAppearanceGroup("clock"));
        addButton(content, "Motion & focus", () -> showAppearanceGroup("motion"));
        addButton(content, "Weather · choose on your phone", this::showPhonePairing);
        break;
      case "scenes":
        title = "Scenes";
        addButton(content, "Browse aerial films", () -> showSceneBrowser("all", 0));
        addButton(content, "Favorites", () -> showSceneBrowser("favorites", 0));
        addButton(content, "Screensaver & quality", this::showScreensaverSettings);
        addSettingSwitch(content, "Shuffle", "scene_shuffle", true);
        addSettingSwitch(content, "Play favorites only", "scene_favorites_only", false);
        break;
      case "apps":
        title = "Apps & inputs";
        addButton(content, "Choose dock apps", this::chooseDock);
        addButton(content, "Inputs & connected devices", this::showInputs);
        addButton(content, "Hidden apps", () -> withUnlock(this::showHiddenApps));
        addButton(content, "Arrange from your phone", this::showPhonePairing);
        break;
      case "connections":
        title = "Connections";
        addButton(content, "Connect phone · remote & editor", this::showPhonePairing);
        addButton(
            content,
            "Network · " + networkStatus(),
            () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        addButton(
            content,
            "Music control access",
            () -> open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        break;
      default:
        title = "System";
        addButton(content, "Privacy & PIN", this::showPrivacyPanel);
        addButton(content, "Backups & diagnostics", this::showMaintenance);
        addButton(content, "Launcher updates", this::showUpdates);
        addButton(content, "Android TV settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        break;
    }
    showSettingsSection(title, content);
  }

  private void showSettingsSection(String title, LinearLayout content) {
    View back = button("‹ Settings", this::showControlCenter);
    content.addView(back, 0, new LinearLayout.LayoutParams(-1, -2));
    showPanel(title, content);
    panelBack(this::showControlCenter);
  }

  private void panelBack(Runnable back) {
    if (panel != null)
      panel.setOnKeyListener(
          (dialog, key, event) -> {
            if (key != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) back.run();
            return true;
          });
  }

  private void showSceneBrowser(String category, int page) {
    try {
      org.json.JSONArray catalogue = SceneLibrary.catalogue(this);
      List<org.json.JSONObject> matches = new ArrayList<>();
      for (int i = 0; i < catalogue.length(); i++) {
        org.json.JSONObject film = catalogue.getJSONObject(i);
        String id = film.getString("id");
        if (category.equals("all")
            || category.equals(film.getString("category"))
            || category.equals("favorites")
                && prefs
                    .getStringSet("scene_favorites", java.util.Collections.emptySet())
                    .contains(id)) matches.add(film);
      }
      LinearLayout content = vertical();
      addButton(
          content,
          "Categories · " + category,
          () -> {
            LinearLayout filters = vertical();
            for (String filter :
                new String[] {"all", "favorites", "landscapes", "cities", "space", "underwater"})
              addButton(filters, filter, () -> showSceneBrowser(filter, 0));
            showSettingsSection("Scene categories", filters);
          });
      if (!category.equals("all") && !category.equals("favorites"))
        addButton(
            content,
            (prefs
                        .getStringSet("scene_excluded_categories", java.util.Collections.emptySet())
                        .contains(category)
                    ? "Include"
                    : "Exclude")
                + " this category",
            () -> {
              try {
                SceneLibrary.change(
                    this,
                    prefs,
                    "category",
                    category,
                    !prefs
                        .getStringSet("scene_excluded_categories", java.util.Collections.emptySet())
                        .contains(category));
                scene.preferencesChanged();
                showSceneBrowser(category, page);
              } catch (Exception e) {
                toast(e.getMessage());
              }
            });
      if (matches.isEmpty())
        content.addView(text("No favorites yet. Open a film and choose Favorite.", 15, MUTED));
      int start = Math.min(page * 9, Math.max(0, matches.size() - 1)),
          end = Math.min(start + 9, matches.size());
      for (int offset = start; offset < end; offset += 3) {
        LinearLayout row = new LinearLayout(this);
        GlideFocus glide = new GlideFocus(this, row, glass(true));
        for (int n = offset; n < Math.min(offset + 3, end); n++) {
          org.json.JSONObject film = matches.get(n);
          LinearLayout card = vertical();
          card.setPadding(dp(5), dp(5), dp(5), dp(9));
          card.setFocusable(true);
          card.setClickable(true);
          card.setDefaultFocusHighlightEnabled(false);
          ImageView image = new ImageView(this);
          image.setScaleType(ImageView.ScaleType.CENTER_CROP);
          image.setBackground(glass(false));
          card.addView(image, new LinearLayout.LayoutParams(dp(180), dp(102)));
          String id = film.getString("id");
          TextView name =
              text(
                  (prefs
                              .getStringSet("scene_favorites", java.util.Collections.emptySet())
                              .contains(id)
                          ? "★ "
                          : "")
                      + film.getString("label"),
                  12,
                  Color.WHITE);
          name.setMaxLines(2);
          name.setPadding(0, dp(8), 0, 0);
          card.addView(name, new LinearLayout.LayoutParams(dp(180), -2));
          card.setContentDescription(film.getString("label"));
          art.load(
              film.optString("thumbnail"),
              240,
              140,
              bitmap -> {
                if (!closed) image.setImageBitmap(bitmap);
              });
          card.setOnClickListener(v -> showSceneDetails(film, category, page));
          card.setOnFocusChangeListener((v, f) -> glide.focus(v, f, motion(220)));
          LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(190), dp(151));
          cp.rightMargin = dp(8);
          row.addView(card, cp);
        }
        content.addView(glide);
        gap(content, 8);
      }
      if (page > 0) addButton(content, "Previous page", () -> showSceneBrowser(category, page - 1));
      if (end < matches.size())
        addButton(
            content,
            "Next page · "
                + (end + 1)
                + "–"
                + Math.min(end + 9, matches.size())
                + " of "
                + matches.size(),
            () -> showSceneBrowser(category, page + 1));
      showSettingsSection("Aerial films", content);
      if (panel != null && panel.getWindow() != null)
        panel
            .getWindow()
            .setLayout(
                Math.min(dp(680), getResources().getDisplayMetrics().widthPixels - dp(60)), -2);
    } catch (Exception e) {
      toast("Could not load the scene library");
    }
  }

  private void showSceneDetails(org.json.JSONObject film, String category, int page) {
    String id = film.optString("id");
    LinearLayout content = vertical();
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    content.addView(image, new LinearLayout.LayoutParams(-1, dp(180)));
    art.load(
        film.optString("thumbnail"),
        640,
        360,
        bitmap -> {
          if (!closed) image.setImageBitmap(bitmap);
        });
    addButton(
        content,
        "Use this scene on Home",
        () -> {
          try {
            edit(new org.json.JSONObject().put("op", "sceneSelect").put("id", id));
          } catch (Exception e) {
            toast(e.getMessage());
          }
        });
    for (String kind : new String[] {"favorite", "exclude"}) {
      boolean selected =
          prefs
              .getStringSet(
                  kind.equals("favorite") ? "scene_favorites" : "scene_excluded",
                  java.util.Collections.emptySet())
              .contains(id);
      String label =
          kind.equals("favorite")
              ? (selected ? "★ Remove favorite" : "☆ Add favorite")
              : (selected ? "Include in playback" : "Exclude from playback");
      addButton(
          content,
          label,
          () -> {
            try {
              SceneLibrary.change(this, prefs, kind, id, !selected);
              scene.preferencesChanged();
              showSceneDetails(film, category, page);
            } catch (Exception e) {
              toast(e.getMessage());
            }
          });
    }
    addButton(content, "‹ Back to scenes", () -> showSceneBrowser(category, page));
    showPanel(film.optString("label"), content);
  }

  private void chooseDock() {
    List<App> choices = new ArrayList<>(apps.values());
    choices.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
    List<String> selected = dockPackages();
    String[] labels = new String[choices.size()];
    boolean[] checked = new boolean[choices.size()];
    for (int i = 0; i < choices.size(); i++) {
      int slot = selected.indexOf(choices.get(i).pkg);
      labels[i] = choices.get(i).label + (slot >= 0 ? " · slot " + (slot + 1) : "");
      checked[i] = slot >= 0;
    }
    new AlertDialog.Builder(this)
        .setTitle("Five dock apps · reselect an app to move it last")
        .setMultiChoiceItems(
            labels,
            checked,
            (dialog, which, isChecked) -> {
              String pkg = choices.get(which).pkg;
              if (isChecked && selected.size() >= 5) {
                ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                toast("Remove a favourite first.");
              } else if (isChecked) selected.add(pkg);
              else selected.remove(pkg);
            })
        .setPositiveButton(
            "Save",
            (dialog, which) -> {
              prefs.edit().putString("dock_apps", String.join(",", selected)).apply();
              layout.setList("dock", selected);
              renderRows(null);
              showControlCenter();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showInputs() {
    LinearLayout content = vertical();
    TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
    boolean any = false;
    if (manager != null)
      try {
        for (TvInputInfo info : manager.getTvInputList())
          if (info.isPassthroughInput()) {
            any = true;
            CharSequence custom = info.loadCustomLabel(this);
            String label = String.valueOf(custom == null ? info.loadLabel(this) : custom);
            boolean connected =
                manager.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
            addButton(
                content,
                label + (connected ? " · connected" : " · no signal"),
                () ->
                    open(
                        new Intent(
                            Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(info.getId()))));
          }
      } catch (SecurityException ignored) {
      }
    String live = getString(R.string.live_tv_package);
    if (installed(live)) {
      any = true;
      addButton(content, "Live TV", () -> launchPackage(live));
    }
    if (!any) content.addView(text("No inputs reported by this TV.", 17, MUTED));
    showPanel("Inputs", content);
  }

  private boolean installed(String pkg) {
    try {
      return getPackageManager().getApplicationInfo(pkg, 0).enabled;
    } catch (Exception ignored) {
      return false;
    }
  }

  private void openAirPlay() {
    if (installed("io.github.jqssun.airplay")) launchPackage("io.github.jqssun.airplay");
    else toast("AirPlay receiver is not installed.");
  }

  static Intent homeIntent(Context context) {
    return new Intent(context, MainActivity.class)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  static void launchSmartTube(Context context) {
    for (String pkg :
        new String[] {
          "org.smarttube.beta",
          "org.smarttube.stable",
          "app.smarttube.fdroid",
          "com.liskovsoft.smarttubetv.beta",
          "com.liskovsoft.smarttubetv"
        }) {
      Intent intent = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
      if (intent != null) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return;
      }
    }
    Toast.makeText(context, "SmartTube is not installed", Toast.LENGTH_SHORT).show();
  }

  private void launchPackage(String pkg) {
    Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
    if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(pkg);
    if (intent != null) open(intent);
    else toast("App is not available.");
  }

  private void open(Intent intent) {
    try {
      startFromHome(intent);
    } catch (ActivityNotFoundException | SecurityException ignored) {
      toast("This screen is not available on your TV.");
    }
  }

  private void startFromHome(Intent intent) {
    View anchor =
        panel != null && panel.getWindow() != null
            ? panel.getWindow().getDecorView().findFocus()
            : focusedCard;
    if (anchor == null) anchor = getCurrentFocus();
    Bundle options = null;
    if (!prefs.getBoolean("reduced_motion", false)
        && anchor != null
        && anchor.getWidth() > 0
        && anchor.getHeight() > 0)
      options =
          android.app.ActivityOptions.makeScaleUpAnimation(
                  anchor, 0, 0, anchor.getWidth(), anchor.getHeight())
              .toBundle();
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options);
    suspendHomeWork();
  }

  private void openProgram(ContinueWatching.Item item) {
    try {
      Intent intent = Intent.parseUri(item.intent, Intent.URI_INTENT_SCHEME);
      intent.setSelector(null);
      if (intent.getComponent() != null && !item.pkg.equals(intent.getComponent().getPackageName()))
        intent.setComponent(null);
      intent.setPackage(item.pkg);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setClipData(null);
      startFromHome(intent);
    } catch (Exception ignored) {
      launchPackage(item.pkg);
    }
  }

  private void toast(String value) {
    Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
  }

  private ImageButton transport(String symbol, String label, Runnable action) {
    ImageButton view = new ImageButton(this);
    view.setImageDrawable(new TransportDrawable(symbol));
    view.setScaleType(ImageView.ScaleType.FIT_CENTER);
    view.setMinimumWidth(0);
    view.setMinimumHeight(0);
    view.setPadding(dp(5), dp(6), dp(5), dp(6));
    view.setFocusable(true);
    view.setDefaultFocusHighlightEnabled(false);
    view.setOnClickListener(v -> action.run());
    view.setBackground(null);
    view.setContentDescription(label);
    view.setOnFocusChangeListener(
        (v, focused) -> {
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
    musicStrip
        .animate()
        .scaleX(focused ? 1.035f : 1f)
        .scaleY(focused ? 1.035f : 1f)
        .translationY(focused ? -dp(2) : 0)
        .alpha(focused || playing ? 1f : .85f)
        .setDuration(motion(180))
        .start();
  }

  private void updateMusic(MediaController controller) {
    music = controller;
    main.removeCallbacks(musicTick);
    MediaMetadata data = controller == null ? null : controller.getMetadata();
    String title = data == null ? null : data.getString(MediaMetadata.METADATA_KEY_TITLE);
    if (!prefs.getBoolean("music_strip", true) || title == null || title.isEmpty()) {
      if (!musicIdentity.isEmpty() || musicArt.getDrawable() != null) {
        musicGeneration++;
        musicIdentity = "";
        musicBitmap = null;
        musicArt.setImageDrawable(null);
      }
      if (musicStrip.hasFocus()) focusDock();
      musicStrip.setVisibility(View.GONE);
      return;
    }
    if (musicStrip.getVisibility() != View.VISIBLE) musicStrip.setVisibility(View.VISIBLE);
    if (!android.text.TextUtils.equals(musicTitle.getText(), title)) musicTitle.setText(title);
    String artist = data.getString(MediaMetadata.METADATA_KEY_ARTIST);
    if (!android.text.TextUtils.equals(musicArtist.getText(), artist == null ? "" : artist))
      musicArtist.setText(artist);
    Bitmap bitmap = data.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
    if (bitmap == null) bitmap = data.getBitmap(MediaMetadata.METADATA_KEY_ART);
    String identity =
        title
            + "|"
            + data.getString(MediaMetadata.METADATA_KEY_ALBUM)
            + "|"
            + data.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            + "|"
            + data.getString(MediaMetadata.METADATA_KEY_ART_URI);
    boolean sameArtwork =
        bitmap == musicBitmap
            || (bitmap != null && musicBitmap != null && bitmap.sameAs(musicBitmap));
    if (!identity.equals(musicIdentity) || !sameArtwork) {
      musicBitmap = bitmap;
      musicIdentity = identity;
      musicGeneration++;
      musicArt.setImageDrawable(null);
      if (bitmap != null) applyMusicArt(bitmap);
      else {
        int request = musicGeneration;
        String uri = data.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (uri == null) uri = data.getString(MediaMetadata.METADATA_KEY_ART_URI);
        art.load(
            uri,
            96,
            96,
            loaded -> {
              if (active && request == musicGeneration) applyMusicArt(loaded);
            });
      }
    }
    PlaybackState state = controller.getPlaybackState();
    boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
    boolean playbackChanged = musicPlaying == null || musicPlaying != playing;
    if (playbackChanged || musicToggle.getDrawable() == null) {
      musicToggle.setImageDrawable(new TransportDrawable(playing ? "pause" : "play"));
      musicToggle.setContentDescription(playing ? "Pause" : "Play");
    }
    musicPlaying = playing;
    long actions = state == null ? 0 : state.getActions();
    musicPrevious.setEnabled((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);
    musicNext.setEnabled((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
    musicPrevious.setAlpha(musicPrevious.isEnabled() ? 1f : .65f);
    musicNext.setAlpha(musicNext.isEnabled() ? 1f : .65f);
    if (playbackChanged)
      musicStrip
          .animate()
          .alpha(musicStrip.hasFocus() || playing ? 1f : .85f)
          .setDuration(motion(250))
          .start();
    musicTick.run();
  }

  private void applyMusicArt(Bitmap bitmap) {
    if (!(musicArt.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)
        || ((android.graphics.drawable.BitmapDrawable) musicArt.getDrawable()).getBitmap()
            != bitmap) musicArt.setImageBitmap(bitmap);
  }

  private void toggleMusic() {
    if (music == null) {
      launchPackage("com.spotify.tv.android");
      return;
    }
    PlaybackState state = music.getPlaybackState();
    if (state != null && state.getState() == PlaybackState.STATE_PLAYING)
      music.getTransportControls().pause();
    else music.getTransportControls().play();
  }

  @Override
  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && art != null)
      art.trimMemory();
    if (!active
        && (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)) {
      wallpaperPreview = null;
      wallpaper.setImageDrawable(null);
    }
  }

  private boolean liteEffects() {
    String mode = prefs.getString("effects", "full");
    android.app.ActivityManager manager =
        (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
    android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
    if (manager != null) manager.getMemoryInfo(info);
    return mode.equals("lite")
        || (mode.equals("auto")
            && FeaturePolicy.lowEnd(
                manager != null && manager.isLowRamDevice(),
                info.totalMem,
                Runtime.getRuntime().availableProcessors()));
  }

  private void applyAppearance() {
    boolean large = prefs.getBoolean("large_clock", false);
    homeClock.setTextSize(large ? 54 : 28);
    android.view.ViewGroup.LayoutParams headerParams = homeHeader.getLayoutParams();
    headerParams.height = dp(large ? 92 : 64);
    homeHeader.setLayoutParams(headerParams);
    FrameLayout.LayoutParams musicParams = (FrameLayout.LayoutParams) musicStrip.getLayoutParams();
    musicParams.topMargin = dp(large ? 128 : 92);
    musicStrip.setLayoutParams(musicParams);
    ((LinearLayout.LayoutParams) homeHeader.getChildAt(0).getLayoutParams()).weight = 1;
    ((LinearLayout) homeHeader.getChildAt(0))
        .setGravity(prefs.getBoolean("clock_right", false) ? Gravity.RIGHT : Gravity.LEFT);
    updateMusic(music);
  }

  private boolean continuousWallpaperMotion() {
    String source = prefs.getString("wallpaper_selection", "misty");
    return (source.equals("aerial") || source.equals("video"))
        && !prefs.getString("wallpaper_motion", "idle").equals("off")
        && !prefs.getBoolean("reduced_motion", false);
  }

  private void scheduleIdle() {
    main.removeCallbacks(idleMotion);
    main.removeCallbacks(idleSaver);
    if (!active || screensaverVisible) return;
    int minutes = prefs.getInt("screensaver_minutes", 5);
    if (minutes > 0 && !prefs.getString("screensaver_mode", "system").equals("system"))
      main.postDelayed(idleSaver, minutes * 60_000L);
    String motion = prefs.getString("wallpaper_motion", "idle");
    if (!motion.equals("off") && !prefs.getBoolean("reduced_motion", false))
      main.postDelayed(
          idleMotion, motion.equals("always") || continuousWallpaperMotion() ? 500 : 10_000);
  }

  private void startWallpaperMotion() {
    if (!active
        || prefs.getBoolean("reduced_motion", false)
        || prefs.getString("wallpaper_motion", "idle").equals("off")
        || panel != null
        || (browseOpen && !continuousWallpaperMotion())
        || screensaverVisible) return;
    String source = prefs.getString("wallpaper_selection", "misty");
    if (continuousWallpaperMotion()
        && !backdropLocation.isEmpty()
        && backdrop.getDrawable() != null) return;
    if (source.equals("aerial") || source.equals("video") || source.equals("photos"))
      scene.start(source, false);
    else if (source.equals("daily")) loadDailyWallpaper();
    else if (source.equals("follow_app")) {
      /* Focus color is applied by the tile's focus callback. */
    } else if (source.startsWith("input:")) startInputScene(source.substring(6));
  }

  private void startScreensaver() {
    if (!active || panel != null || screensaverVisible) return;
    String mode = prefs.getString("screensaver_mode", "system");
    if (mode.equals("system")) {
      try {
        open(new Intent(Settings.ACTION_DREAM_SETTINGS));
      } catch (Exception ignored) {
      }
      return;
    }
    screensaverVisible = true;
    main.removeCallbacks(idleMotion);
    main.removeCallbacks(idleSaver);
    clearBackdrop();
    stopInputScene();
    homeContent
        .animate()
        .alpha(0)
        .setDuration(motion(1000))
        .withEndAction(
            () -> {
              if (screensaverVisible)
                for (View view : new View[] {scroll, homeHeader, musicStrip})
                  view.setVisibility(View.INVISIBLE);
            })
        .start();
    ambientClock.setVisibility(
        prefs.getBoolean("screensaver_clock", true) || mode.equals("clock")
            ? View.VISIBLE
            : View.GONE);
    if (mode.equals("clock")) {
      scene.start("photos", true);
      ambientClock.setTextSize(86);
    } else {
      scene.start(mode, true);
      ambientClock.setTextSize(36);
    }
    moveAmbientClock();
  }

  private final Runnable driftClock = this::moveAmbientClock;

  private void moveAmbientClock() {
    if (!screensaverVisible) return;
    ambientClock.setTranslationX(-dp((float) (Math.random() * 160)));
    ambientClock.setTranslationY(dp((float) (Math.random() * 180)));
    main.removeCallbacks(driftClock);
    main.postDelayed(driftClock, 60_000);
  }

  private void stopScreensaver() {
    screensaverVisible = false;
    homeContent.animate().withEndAction(null).cancel();
    main.removeCallbacks(driftClock);
    if (continuousWallpaperMotion())
      scene.start(prefs.getString("wallpaper_selection", "misty"), false);
    else scene.pause(true);
    ambientClock.setVisibility(View.GONE);
    scroll.setVisibility(View.VISIBLE);
    homeHeader.setVisibility(View.VISIBLE);
    musicStrip.setVisibility(music == null ? View.GONE : View.VISIBLE);
    homeContent.setAlpha(0);
    homeContent.animate().alpha(1).setDuration(motion(300)).start();
    focusDock();
    scheduleIdle();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (screensaverVisible) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
            || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)
          scene.next(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
        else stopScreensaver();
      }
      return true;
    }
    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
      if ((!prefs.getString("wallpaper_motion", "idle").equals("always")
              && !continuousWallpaperMotion())
          || panel != null) scene.pause(true);
      if (prefs.getBoolean("navigation_sounds", false))
        ((android.media.AudioManager) getSystemService(AUDIO_SERVICE))
            .playSoundEffect(
                event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                    ? android.media.AudioManager.FX_KEY_CLICK
                    : android.media.AudioManager.FX_FOCUS_NAVIGATION_DOWN);
      scheduleIdle();
      if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
        App app = selectedApp();
        if (app != null) showAppActions(app);
        else showControlCenter();
        return true;
      }
      if (event.getKeyCode() == KeyEvent.KEYCODE_TV_INPUT) {
        showInputs();
        return true;
      }
    }
    if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) return true;
    return super.dispatchKeyEvent(event);
  }

  @Override
  public boolean dispatchTouchEvent(android.view.MotionEvent event) {
    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
      if (screensaverVisible) {
        stopScreensaver();
        inputWasHandled = true;
      } else {
        if (!continuousWallpaperMotion() || panel != null) scene.pause(true);
        scheduleIdle();
      }
    }
    if (inputWasHandled) {
      if (event.getAction() == android.view.MotionEvent.ACTION_UP
          || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) inputWasHandled = false;
      return true;
    }
    return super.dispatchTouchEvent(event);
  }

  private App selectedApp() {
    if (focusedKey == null) return null;
    for (String prefix : new String[] {"app:", "other:"})
      if (focusedKey.startsWith(prefix)) return apps.get(focusedKey.substring(prefix.length()));
    return null;
  }

  private void showAppActions(App app) {
    LinearLayout content = vertical();
    addButton(content, "Move left", () -> moveApp(app.pkg, -1));
    addButton(content, "Move right", () -> moveApp(app.pkg, 1));
    addButton(
        content,
        dockPackages().contains(app.pkg) ? "Remove from dock" : "Pin to dock",
        () -> {
          List<String> dock = new ArrayList<>(dockPackages());
          if (dock.contains(app.pkg)) dock.remove(app.pkg);
          else {
            if (dock.size() >= 5) dock.remove(dock.size() - 1);
            dock.add(app.pkg);
          }
          layout.setList("dock", dock);
          prefs.edit().putString("dock_apps", String.join(",", dock)).apply();
          refreshFeatures();
          closePanel();
        });
    addButton(
        content,
        "Rename",
        () ->
            askText(
                "Name",
                app.label,
                name -> {
                  layout.rename(app.pkg, name);
                  refreshFeatures();
                  closePanel();
                }));
    addButton(
        content,
        "Move to folder",
        () ->
            askText(
                "Folder name (empty removes folder)",
                "",
                name -> {
                  layout.folder(app.pkg, name);
                  refreshFeatures();
                  closePanel();
                }));
    addButton(
        content,
        "Hide",
        () -> {
          layout.hide(app.pkg, true);
          refreshFeatures();
          closePanel();
        });
    addButton(
        content,
        "Open when TV starts",
        () -> {
          prefs.edit().putString("boot_target", app.pkg).apply();
          toast("Start with " + app.label);
          closePanel();
        });
    if (app.pkg.startsWith("input:"))
      addButton(
          content,
          "Device icon",
          () ->
              chooseValue(
                  "Connected device",
                  "device:" + app.pkg,
                  new String[] {
                    "inputs", "appletv", "playstation", "switch", "xbox", "computer", "speaker"
                  }));
    if (app.pkg.startsWith("input:"))
      addButton(
          content,
          "Use as live wallpaper",
          () -> {
            prefs.edit().putString("wallpaper_selection", app.pkg).apply();
            closePanel();
            startInputScene(app.pkg.substring(6));
          });
    else if (!app.pkg.equals("com.gathin.tvhome.inputs"))
      addButton(
          content,
          "App information / uninstall",
          () ->
              open(
                  new Intent(
                      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                      Uri.parse("package:" + app.pkg))));
    addButton(
        content,
        "Reset custom icon",
        () -> {
          prefs.edit().remove("icon:" + app.pkg).apply();
          refreshFeatures();
          closePanel();
        });
    showPanel(app.label, content);
  }

  private void moveApp(String key, int delta) {
    List<String> dock = new ArrayList<>(dockPackages());
    if (dock.contains(key)) {
      layout.setList("dock", FeaturePolicy.move(dock, key, delta));
    } else {
      List<String> order = layout.ordered(apps.keySet());
      layout.setList("order", FeaturePolicy.move(order, key, delta));
    }
    refreshFeatures();
  }

  private void refreshFeatures() {
    if (!active) {
      appsDiscoveredAt = 0;
      return;
    }
    if (continuousWallpaperMotion()) scene.hold();
    else scene.pause(false);
    stopInputScene();
    appsDiscoveredAt = 0;
    discoverApps();
    renderRows(focusedKey);
    updateWallpaper();
    applyAppearance();
    loadWeather();
    resumePanelVideo();
    scheduleIdle();
  }

  private void askText(String title, String current, java.util.function.Consumer<String> action) {
    android.widget.EditText input = new android.widget.EditText(this);
    input.setText(current);
    input.setSingleLine();
    input.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(40)});
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        action.accept(input.getText().toString());
                        dialog.dismiss();
                      } catch (IllegalArgumentException e) {
                        input.setError(e.getMessage());
                      }
                    }));
    dialog.show();
  }

  private void showAppearanceGroup(String group) {
    LinearLayout content = vertical();
    String title;
    String[][] toggles;
    if (group.equals("layout")) {
      title = "App layout";
      toggles =
          new String[][] {
            {"Two app rows", "two_rows", "false"},
            {"Peek at the next row", "peek_row", "false"},
            {"App names", "show_names", "true"},
            {"Glass dock", "home_plate", "true"}
          };
    } else if (group.equals("clock")) {
      title = "Clock & widgets";
      toggles =
          new String[][] {
            {"Large clock", "large_clock", "false"},
            {"Clock on the right", "clock_right", "false"},
            {"Spotify pill", "music_strip", "true"},
            {"Weather", "weather_enabled", "false"}
          };
    } else {
      title = "Motion & focus";
      toggles =
          new String[][] {
            {"High contrast focus", "high_contrast", "false"},
            {"Reduced motion", "reduced_motion", "false"},
            {"Navigation sounds", "navigation_sounds", "false"}
          };
    }
    for (String[] item : toggles)
      addSettingSwitch(content, item[0], item[1], Boolean.parseBoolean(item[2]));
    if (group.equals("motion")) {
      addButton(
          content,
          "Wallpaper motion · " + prefs.getString("wallpaper_motion", "idle"),
          () ->
              chooseValue(
                  "Wallpaper motion", "wallpaper_motion", new String[] {"off", "idle", "always"}));
      addButton(
          content,
          "Effects · " + prefs.getString("effects", "full"),
          () -> chooseValue("Effects", "effects", new String[] {"full", "auto", "lite"}));
    }
    content.addView(button("‹ Appearance", () -> showSettingsCategory("appearance")), 0);
    showPanel(title, content);
    panelBack(() -> showSettingsCategory("appearance"));
  }

  private void chooseValue(String title, String key, String[] choices) {
    LinearLayout content = vertical();
    for (String choice : choices)
      addButton(
          content,
          choice,
          () -> {
            prefs.edit().putString(key, choice).apply();
            refreshFeatures();
            closePanel();
          });
    showPanel(title, content);
  }

  private void showScreensaverSettings() {
    LinearLayout content = vertical();
    addButton(content, "‹ Scenes", () -> showSettingsCategory("scenes"));
    addButton(
        content,
        "Preview screensaver",
        () -> {
          closePanel();
          startScreensaver();
        });
    addButton(
        content,
        "Mode · " + prefs.getString("screensaver_mode", "system"),
        () ->
            chooseValue(
                "Screensaver",
                "screensaver_mode",
                new String[] {"clock", "photos", "aerial", "video", "system"}));
    for (int minutes : new int[] {0, 2, 5, 10, 20})
      addButton(
          content,
          minutes == 0 ? "Never start automatically" : "Start after " + minutes + " minutes",
          () -> {
            prefs.edit().putInt("screensaver_minutes", minutes).apply();
            scheduleIdle();
            toast(minutes == 0 ? "Automatic screensaver off" : "Screensaver timer saved");
          });
    addSettingSwitch(content, "Clock overlay", "screensaver_clock", true);
    addSettingSwitch(content, "Back starts screensaver", "back_screensaver", false);
    addButton(
        content,
        "Video quality · " + prefs.getString("aerial_quality", "1080p"),
        () ->
            chooseValue(
                "Supported quality with fallback",
                "aerial_quality",
                new String[] {"auto", "1080p", "4k", "dolby_vision"}));
    showPanel("Screensaver", content);
    panelBack(() -> showSettingsCategory("scenes"));
  }

  private void showVolumePanel() {
    LinearLayout content = vertical();
    android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
    addButton(
        content,
        "Volume −",
        () ->
            audio.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_LOWER,
                android.media.AudioManager.FLAG_SHOW_UI));
    addButton(
        content,
        "Volume +",
        () ->
            audio.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_RAISE,
                android.media.AudioManager.FLAG_SHOW_UI));
    addButton(
        content,
        "Mute / unmute",
        () ->
            audio.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_TOGGLE_MUTE,
                android.media.AudioManager.FLAG_SHOW_UI));
    addButton(content, "Brightness −", () -> adjustBrightness(-12));
    addButton(content, "Brightness +", () -> adjustBrightness(12));
    addButton(
        content, "Picture settings", () -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
    showPanel("Sound and picture", content);
  }

  private void adjustBrightness(int delta) {
    if (!Settings.System.canWrite(this)) {
      open(
          new Intent(
              Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
      return;
    }
    int value =
        Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
    if (!Settings.System.putInt(
        getContentResolver(),
        Settings.System.SCREEN_BRIGHTNESS,
        Math.max(1, Math.min(255, value + delta))))
      toast("Use the TV picture settings for this panel");
  }

  private String networkStatus() {
    android.net.ConnectivityManager manager =
        (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    android.net.NetworkCapabilities caps =
        manager == null ? null : manager.getNetworkCapabilities(manager.getActiveNetwork());
    if (caps == null) return "Offline";
    return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        ? "Ethernet"
        : caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : "Connected";
  }

  private void showHiddenApps() {
    LinearLayout content = vertical();
    for (String key : layout.list("hidden")) {
      App app = apps.get(key);
      addButton(
          content,
          "Show " + (app == null ? key : app.label),
          () -> {
            layout.hide(key, false);
            refreshFeatures();
            showHiddenApps();
          });
    }
    if (content.getChildCount() == 0) content.addView(text("No hidden apps", 16, MUTED));
    showPanel("Hidden apps", content);
  }

  private boolean locked() {
    return !prefs.getString("parental_pin", "").isEmpty()
        && android.os.SystemClock.elapsedRealtime() > unlockedUntil;
  }

  private void verifyPin(String pin) {
    long now = android.os.SystemClock.elapsedRealtime();
    if (now < pinRetryAt) throw new IllegalArgumentException("Wait 30 seconds before trying again");
    if (!PinLock.matches(prefs.getString("parental_pin", ""), pin)) {
      if (++pinFailures >= 5) {
        pinRetryAt = now + 30000;
        pinFailures = 0;
      }
      throw new IllegalArgumentException("Incorrect PIN");
    }
    pinFailures = 0;
  }

  private void withUnlock(Runnable action) {
    if (!locked()) {
      action.run();
      return;
    }
    askPin(
        false,
        pin -> {
          verifyPin(pin);
          unlockedUntil = android.os.SystemClock.elapsedRealtime() + 600_000;
          action.run();
        });
  }

  private void askPin(boolean create, java.util.function.Consumer<String> action) {
    LinearLayout box = vertical();
    android.widget.EditText entry = new android.widget.EditText(this);
    entry.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    entry.setHint("4–12 digits or directions");
    entry.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(12)});
    box.addView(entry);
    LinearLayout directions = new LinearLayout(this);
    for (String[] direction : new String[][] {{"↑", "U"}, {"↓", "D"}, {"←", "L"}, {"→", "R"}})
      directions.addView(button(direction[0], () -> entry.append(direction[1])));
    box.addView(directions);
    String[] confirmed = {null};
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(create ? "Create PIN" : "Unlock hidden apps")
            .setView(box)
            .setPositiveButton(create ? "Set PIN" : "Unlock", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        if (!PinLock.valid(entry.getText().toString()))
                          throw new IllegalArgumentException("Use 4–12 digits or directions");
                        if (create && confirmed[0] == null) {
                          confirmed[0] = entry.getText().toString();
                          entry.setText("");
                          entry.setHint("Repeat your PIN");
                          return;
                        }
                        if (create && !confirmed[0].equals(entry.getText().toString())) {
                          confirmed[0] = null;
                          entry.setText("");
                          throw new IllegalArgumentException("PINs did not match; enter a new PIN");
                        }
                        action.accept(entry.getText().toString());
                        dialog.dismiss();
                      } catch (IllegalArgumentException e) {
                        entry.setError(e.getMessage());
                      }
                    }));
    dialog.show();
  }

  private void showPrivacyPanel() {
    LinearLayout content = vertical();
    addButton(
        content,
        "Set / replace hidden-app PIN",
        () ->
            withUnlock(
                () ->
                    askPin(
                        true,
                        pin -> {
                          try {
                            prefs.edit().putString("parental_pin", PinLock.create(pin)).apply();
                            unlockedUntil = android.os.SystemClock.elapsedRealtime() + 600_000;
                            toast("PIN saved");
                          } catch (Exception e) {
                            throw new IllegalArgumentException("Could not save PIN");
                          }
                        })));
    addButton(
        content,
        "Remove PIN",
        () ->
            withUnlock(
                () -> {
                  prefs.edit().remove("parental_pin").apply();
                  toast("PIN removed");
                }));
    addButton(
        content,
        "Lock now",
        () -> {
          unlockedUntil = 0;
          closePanel();
        });
    addButton(
        content,
        "Disconnect phone",
        () -> {
          PhoneSession.end(this);
          phone = null;
          toast("Phone disconnected");
        });
    showPanel("Privacy and PIN", content);
  }

  private void openInput(String id) {
    open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)));
  }

  private void openBootTarget() {
    String key = prefs.getString("boot_target", "");
    long now = android.os.SystemClock.elapsedRealtime();
    if (key.isEmpty() || now - prefs.getLong("last_boot_open", -30_000) < 15_000) return;
    prefs.edit().putLong("last_boot_open", now).apply();
    if (key.startsWith("input:")) openInput(key.substring(6));
    else launchPackage(key);
  }

  private void startInputScene(String id) {
    if (inputScene != null) return;
    inputScene = new android.media.tv.TvView(this);
    inputScene.setFocusable(false);
    inputScene.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    root.addView(inputScene, 2, new FrameLayout.LayoutParams(-1, -1));
    inputScene.setCallback(
        new android.media.tv.TvView.TvInputCallback() {
          @Override
          public void onVideoUnavailable(String inputId, int reason) {
            toast("Input preview unavailable · using wallpaper");
            stopInputScene();
          }

          @Override
          public void onConnectionFailed(String inputId) {
            stopInputScene();
          }
        });
    android.media.tv.TvView view = inputScene;
    main.postDelayed(
        () -> {
          if (inputScene == view && active)
            try {
              view.tune(id, TvContract.buildChannelUriForPassthroughInput(id));
            } catch (Exception e) {
              stopInputScene();
            }
        },
        800);
  }

  private void stopInputScene() {
    if (inputScene != null) {
      inputScene.reset();
      root.removeView(inputScene);
      inputScene = null;
    }
  }

  private void showPhonePairing() {
    try {
      if (phone != null) phone.close();
      phone = new PhoneServer(this, media, this);
      PhoneSession.keep(this, phone);
      String url = phone.address();
      if (url == null) {
        phone.close();
        phone = null;
        toast("Connect the TV and phone to the same local network");
        return;
      }
      LinearLayout content = vertical();
      com.google.zxing.common.BitMatrix matrix =
          new com.google.zxing.MultiFormatWriter()
              .encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 360, 360);
      Bitmap image = Bitmap.createBitmap(360, 360, Bitmap.Config.RGB_565);
      for (int y = 0; y < 360; y++)
        for (int x = 0; x < 360; x++)
          image.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
      ImageView code = new ImageView(this);
      code.setImageBitmap(image);
      code.setContentDescription("Scan to pair your phone with Cinema Home");
      content.addView(code, new LinearLayout.LayoutParams(-1, dp(220)));
      content.addView(text("Same Wi-Fi · pairing expires in 15 minutes", 14, MUTED));
      TextView address = text(url, 12, MUTED);
      address.setTextIsSelectable(true);
      content.addView(address);
      addButton(
          content,
          "Disconnect",
          () -> {
            PhoneSession.end(this);
            phone = null;
            closePanel();
          });
      showPanel("Connect your phone", content);
    } catch (Exception e) {
      toast("Could not open phone pairing");
    }
  }

  @Override
  public boolean active() {
    return active && !closed;
  }

  @Override
  public void navigate(String key) {
    if (key.equals("home")) {
      if (screensaverVisible) stopScreensaver();
      closePanel();
      browseOpen = false;
      renderRows(null);
      animateScroll(0);
      return;
    }
    if (key.equals("settings")) {
      showControlCenter();
      return;
    }
    if (panel != null && panel.getWindow() != null) {
      View focused = panel.getWindow().getDecorView().findFocus();
      if (focused != null) {
        if (key.equals("ok")) {
          focused.performClick();
          return;
        }
        if (focused instanceof android.widget.Switch
            && (key.equals("left") || key.equals("right"))) {
          ((android.widget.Switch) focused).setChecked(key.equals("right"));
          return;
        }
        int direction =
            key.equals("up")
                ? View.FOCUS_UP
                : key.equals("down")
                    ? View.FOCUS_DOWN
                    : key.equals("left") ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
        if (key.equals("up") || key.equals("down") || key.equals("left") || key.equals("right")) {
          View next = focused.focusSearch(direction);
          if (next != null) next.requestFocus();
          return;
        }
      }
    }
    int code;
    switch (key) {
      case "up":
        code = KeyEvent.KEYCODE_DPAD_UP;
        break;
      case "down":
        code = KeyEvent.KEYCODE_DPAD_DOWN;
        break;
      case "left":
        code = KeyEvent.KEYCODE_DPAD_LEFT;
        break;
      case "right":
        code = KeyEvent.KEYCODE_DPAD_RIGHT;
        break;
      case "ok":
        code = KeyEvent.KEYCODE_DPAD_CENTER;
        break;
      case "back":
        code = KeyEvent.KEYCODE_BACK;
        break;
      case "menu":
        code = KeyEvent.KEYCODE_MENU;
        break;
      default:
        throw new IllegalArgumentException("Unknown navigation button");
    }
    for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
      KeyEvent event = new KeyEvent(action, code);
      if (panel != null && panel.getWindow() != null) panel.dispatchKeyEvent(event);
      else dispatchKeyEvent(event);
    }
  }

  @Override
  public org.json.JSONObject state() throws Exception {
    org.json.JSONObject result = new org.json.JSONObject(), settings = new org.json.JSONObject();
    for (java.util.Map.Entry<String, Object> entry : HomeSettings.defaults().entrySet()) {
      Object value = prefs.getAll().get(entry.getKey());
      if (value == null && entry.getKey().equals("weather_enabled"))
        value = WeatherData.configured(this);
      settings.put(entry.getKey(), value == null ? entry.getValue() : value);
    }
    org.json.JSONArray list = new org.json.JSONArray();
    List<String> order = layout.ordered(apps.keySet());
    for (String key : apps.keySet()) if (!order.contains(key)) order.add(key);
    for (String key : order) {
      boolean hidden = layout.list("hidden").contains(key);
      if (hidden && locked()) continue;
      App app = apps.get(key);
      if (app == null) continue;
      list.put(
          new org.json.JSONObject()
              .put("id", key)
              .put("name", app.label)
              .put("hidden", hidden)
              .put("input", key.startsWith("input:"))
              .put("device", prefs.getString("device:" + key, "inputs"))
              .put("icon", prefs.getString("icon:" + key, "")));
    }
    org.json.JSONObject visibleFolders = layout.folders();
    if (locked()) {
      java.util.Iterator<String> names = visibleFolders.keys();
      while (names.hasNext()) {
        String name = names.next();
        org.json.JSONArray members = visibleFolders.getJSONArray(name),
            visible = new org.json.JSONArray();
        for (int i = 0; i < members.length(); i++)
          if (!layout.list("hidden").contains(members.getString(i)))
            visible.put(members.getString(i));
        visibleFolders.put(name, visible);
      }
    }
    return result
        .put("name", "Cinema Home")
        .put("version", "4.0")
        .put("settings", settings)
        .put("scenes", SceneLibrary.preferences(prefs))
        .put("remote", RemoteControls.capabilities(this, active))
        .put("apps", list)
        .put("dock", new org.json.JSONArray(dockPackages()))
        .put("folders", visibleFolders)
        .put("media", media.state())
        .put("storage", media.used())
        .put("locked", locked())
        .put("pinSet", !prefs.getString("parental_pin", "").isEmpty())
        .put(
            "weather",
            new org.json.JSONObject()
                .put("name", prefs.getString("weather_name", ""))
                .put("latitude", prefs.getString("weather_lat", ""))
                .put("longitude", prefs.getString("weather_lon", "")))
        .put("diagnostics", FrameStats.summary())
        .put("bootTarget", prefs.getString("boot_target", ""));
  }

  @Override
  public void edit(org.json.JSONObject command) throws Exception {
    if (!active) throw new IllegalArgumentException("Return Home to edit launcher settings");
    String op = command.getString("op"), key = command.optString("id");
    if (op.equals("scenePreference")) {
      SceneLibrary.change(
          this, prefs, command.getString("kind"), key, command.getBoolean("enabled"));
      scene.preferencesChanged();
      return;
    }
    if (op.equals("sceneSelect")) {
      if (!SceneLibrary.eligible(SceneLibrary.catalogue(this), prefs).contains(key))
        throw new IllegalArgumentException("Include this scene in playback first");
      prefs
          .edit()
          .putString("wallpaper_selection", "aerial")
          .putString("selected_scene", key)
          .apply();
      refreshFeatures();
      closePanel();
      scene.selectFilm(key);
      scene.start("aerial", false);
      return;
    }
    if (op.equals("settings")) {
      org.json.JSONObject values = command.getJSONObject("settings");
      java.util.Map<String, Object> raw = new java.util.LinkedHashMap<>();
      java.util.Iterator<String> keys = values.keys();
      while (keys.hasNext()) {
        String name = keys.next();
        if (!HomeSettings.defaults().containsKey(name))
          throw new IllegalArgumentException("Unknown setting");
        raw.put(name, values.get(name));
      }
      java.util.Map<String, Object> valid = HomeSettings.validate(raw);
      SharedPreferences.Editor editor = prefs.edit();
      for (java.util.Map.Entry<String, Object> value : valid.entrySet()) {
        if (value.getValue() instanceof Boolean)
          editor.putBoolean(value.getKey(), (Boolean) value.getValue());
        else if (value.getValue() instanceof Integer)
          editor.putInt(value.getKey(), (Integer) value.getValue());
        else editor.putString(value.getKey(), (String) value.getValue());
      }
      editor.apply();
    } else if (op.equals("weather")) {
      String name = command.getString("name"),
          lat = command.getString("latitude"),
          lon = command.getString("longitude");
      if (!WeatherLocation.valid(name, lat, lon))
        throw new IllegalArgumentException("Choose a valid city or coordinates");
      prefs
          .edit()
          .putString("weather_name", FeaturePolicy.label(name))
          .putString("weather_lat", lat)
          .putString("weather_lon", lon)
          .putBoolean("weather_enabled", true)
          .apply();
    } else if (op.equals("unlock")) {
      verifyPin(command.getString("pin"));
      unlockedUntil = android.os.SystemClock.elapsedRealtime() + 600_000;
    } else if (op.equals("release")) {
      String url = command.getString("url");
      if (!url.isEmpty() && (!url.startsWith("https://") || url.length() > 2048))
        throw new IllegalArgumentException("Use an HTTPS release manifest URL");
      prefs.edit().putString("release_manifest", url).apply();
    } else if (op.equals("deleteMedia")) {
      String id = command.getString("media");
      media.delete(id);
      for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet())
        if (id.equals(entry.getValue())) prefs.edit().remove(entry.getKey()).apply();
    } else if (op.equals("wallpaperPhoto")) {
      String id = command.getString("media");
      if (!id.endsWith(".jpg") || !media.file(id).isFile())
        throw new IllegalArgumentException("Choose an uploaded photo");
      prefs
          .edit()
          .putString("personal_wallpaper", id)
          .putString("wallpaper_selection", "photos")
          .apply();
    } else if (op.equals("previewSaver")) {
      closePanel();
      startScreensaver();
    } else if (op.equals("dock")) {
      org.json.JSONArray items = command.getJSONArray("items");
      if (items.length() > 5) throw new IllegalArgumentException("Choose up to five dock apps");
      List<String> values = new ArrayList<>();
      for (int i = 0; i < items.length(); i++) {
        String id = items.getString(i);
        if (!apps.containsKey(id) || layout.list("hidden").contains(id))
          throw new IllegalArgumentException("App unavailable");
        if (!values.contains(id)) values.add(id);
      }
      layout.setList("dock", values);
      prefs.edit().putString("dock_apps", String.join(",", values)).apply();
    } else if (op.equals("boot") && key.isEmpty()) prefs.edit().remove("boot_target").apply();
    else {
      if (!apps.containsKey(key) || locked() && layout.list("hidden").contains(key))
        throw new IllegalArgumentException("App unavailable or locked");
      switch (op) {
        case "device":
          String kind = command.getString("device");
          if (!key.startsWith("input:")
              || !Arrays.asList(
                      "inputs", "appletv", "playstation", "switch", "xbox", "computer", "speaker")
                  .contains(kind)) throw new IllegalArgumentException("Unknown device icon");
          prefs.edit().putString("device:" + key, kind).apply();
          break;
        case "rename":
          layout.rename(key, command.getString("name"));
          break;
        case "move":
          moveApp(key, command.getInt("delta"));
          break;
        case "hide":
          layout.hide(key, command.getBoolean("hidden"));
          break;
        case "folder":
          layout.folder(key, command.optString("name"));
          break;
        case "boot":
          prefs.edit().putString("boot_target", key).apply();
          break;
        case "icon":
          String id = command.optString("media");
          if (!id.isEmpty() && (!id.endsWith(".jpg") || !media.file(id).isFile()))
            throw new IllegalArgumentException("Choose an uploaded image");
          if (id.isEmpty()) prefs.edit().remove("icon:" + key).apply();
          else prefs.edit().putString("icon:" + key, id).apply();
          break;
        default:
          throw new IllegalArgumentException("Unknown action");
      }
    }
    refreshFeatures();
  }

  private void showMaintenance() {
    LinearLayout content = vertical();
    content.addView(
        text(
            "Cinema Home 4.0 · "
                + networkStatus()
                + "\n"
                + FrameStats.summary()
                + "\nEffects: "
                + (liteEffects() ? "Lite" : "Full"),
            14,
            MUTED));
    addButton(
        content,
        "Save settings restore point",
        () -> {
          try {
            SettingsBackup.save(this, prefs);
            toast("Restore point saved");
          } catch (Exception e) {
            toast("Could not save settings");
          }
        });
    addButton(
        content,
        "Restore settings",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Restore saved appearance?")
                .setMessage(
                    "Your current visual settings will be replaced. Apps, media and PIN stay in"
                        + " place.")
                .setPositiveButton(
                    "Restore",
                    (d, w) -> {
                      try {
                        String json =
                            new String(
                                java.nio.file.Files.readAllBytes(
                                    SettingsBackup.snapshot(this).toPath()),
                                java.nio.charset.StandardCharsets.UTF_8);
                        SettingsBackup.apply(prefs, SettingsBackup.parse(json));
                        layout.setList(
                            "dock", Arrays.asList(prefs.getString("dock_apps", "").split(",")));
                        refreshFeatures();
                        toast("Settings restored");
                      } catch (Exception e) {
                        toast("No valid restore point");
                      }
                    })
                .setNegativeButton("Cancel", null)
                .show());
    addButton(content, "Check for updates", this::showUpdates);
    showPanel("Backup and diagnostics", content);
  }

  private void showUpdates() {
    LinearLayout content = vertical();
    content.addView(
        text(
            "Updates use your own signed Cinema Home releases.\n"
                + "Configure a release manifest in the phone editor. Android confirms every"
                + " installation.",
            15,
            MUTED));
    addButton(
        content,
        "Check configured release",
        () -> LauncherUpdates.check(this, prefs, worker, result -> toast(result)));
    showPanel("Updates", content);
  }

  private void loadDailyWallpaper() {
    worker.execute(
        () -> {
          try {
            java.io.File file = DailyWallpaper.load(this);
            main.post(
                () -> {
                  if (active && prefs.getString("wallpaper_selection", "").equals("daily"))
                    art.load(
                        "private-daily:",
                        1280,
                        720,
                        bitmap -> {
                          wallpaper.setImageBitmap(bitmap);
                          wallpaperBitmap = bitmap;
                        });
                });
          } catch (Exception ignored) {
          }
        });
  }
}
