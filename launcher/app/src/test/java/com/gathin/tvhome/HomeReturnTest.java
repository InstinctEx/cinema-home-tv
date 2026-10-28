package com.gathin.tvhome;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class HomeReturnTest {
  static Object field(Object target, String name) throws Exception {
    java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(target);
  }

  static void set(Object target, String name, Object value) throws Exception {
    java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }

  static Object call(Object target, String name, Class<?> type, Object arg) throws Exception {
    java.lang.reflect.Method m = target.getClass().getDeclaredMethod(name, type);
    m.setAccessible(true);
    return m.invoke(target, arg);
  }

  static void configure() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("tvhome_prefs", 0)
        .edit()
        .clear()
        .putString("wallpaper_selection", "aerial")
        .putString("wallpaper_motion", "idle")
        .putBoolean("weather_enabled", false)
        .putInt("screensaver_minutes", 0)
        .commit();
  }

  @Test
  public void stoppedHomeRetainsTinyAerialFrameAndResumesWithoutInput() throws Exception {
    configure();
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity a = c.get();
      SceneView scene = (SceneView) field(a, "scene");
      Bitmap frame = Bitmap.createBitmap(256, 144, Bitmap.Config.ARGB_8888);
      set(scene, "lastVideoFrame", frame);
      scene.start("aerial", false);
      android.widget.LinearLayout strip = (android.widget.LinearLayout) field(a, "musicStrip");
      ImageView brand = null;
      for (int i = 0; i < strip.getChildCount(); i++)
        if (strip.getChildAt(i) instanceof ImageView
            && ((ImageView) strip.getChildAt(i)).getDrawable() instanceof BrandMarkDrawable)
          brand = (ImageView) strip.getChildAt(i);
      assertNotNull(brand);
      c.pause().stop();
      assertTrue(
          "The static Spotify brand mark must survive trimming too",
          brand.getDrawable() instanceof BrandMarkDrawable);
      assertNotNull(
          "Stopping Home must retain the aerial return frame",
          ((ImageView) field(scene, "photo")).getDrawable());
      assertNotNull(
          "Static transport symbols must survive image trimming",
          ((ImageView) field(a, "musicPrevious")).getDrawable());
      assertNotNull(((ImageView) field(a, "musicNext")).getDrawable());
      c.start().resume();
      assertEquals(View.VISIBLE, scene.getVisibility());
      assertEquals(1f, ((ImageView) field(scene, "photo")).getAlpha(), 0f);
      assertEquals(
          "Resume aerial on Home arrival without another key", true, field(scene, "playing"));
    }
  }

  @Test
  @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  public void savedAerialBecomesTheReturnWallpaperAndSurvivesActivityReplacement()
      throws Exception {
    configure();
    android.content.Context context = RuntimeEnvironment.getApplication();
    java.io.File saved = new java.io.File(context.getFilesDir(), "scene-return.bin");
    saved.delete();
    org.json.JSONArray films;
    try (java.io.InputStream in = context.getAssets().open("aerials.json")) {
      films =
          new org.json.JSONObject(
                  new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
              .getJSONArray("videos");
    }
    String resumeUri = films.getJSONObject(2).getString("url");
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity a = c.get();
      SceneView scene = (SceneView) field(a, "scene");
      Bitmap frame = Bitmap.createBitmap(256, 144, Bitmap.Config.ARGB_8888);
      frame.eraseColor(android.graphics.Color.RED);
      set(scene, "lastVideoFrame", frame);
      set(scene, "lastUri", resumeUri);
      set(scene, "source", "aerial");
      c.pause().stop();
      ((java.util.concurrent.ExecutorService) field(scene, "io"))
          .submit(() -> {})
          .get(2, java.util.concurrent.TimeUnit.SECONDS);
      assertTrue("A saved frame must survive the launcher process", saved.isFile());
      c.start().resume();
      ImageView wallpaper = (ImageView) field(a, "wallpaper");
      assertSame(
          "The return frame must also cover the root wallpaper layer",
          frame,
          ((android.graphics.drawable.BitmapDrawable) wallpaper.getDrawable()).getBitmap());
    }
    try (ActivityController<MainActivity> fresh =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity a = fresh.get();
      Bitmap restored =
          ((android.graphics.drawable.BitmapDrawable)
                  ((ImageView) field(a, "wallpaper")).getDrawable())
              .getBitmap();
      assertTrue(
          "A fresh Home must show the saved video image before any network reply",
          android.graphics.Color.red(restored.getPixel(100, 60)) > 220);
      assertEquals(android.view.View.VISIBLE, ((SceneView) field(a, "scene")).getVisibility());
      SceneView restoredScene = (SceneView) field(a, "scene");
      for (int i = 0;
          i < 100 && ((org.json.JSONArray) field(restoredScene, "aerials")).length() == 0;
          i++) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Thread.sleep(5);
      }
      assertEquals(
          "Restart must resume the film associated with the saved frame",
          films.getJSONObject(2).getString("id"),
          ((SceneQueue) field(restoredScene, "queue")).current());
    }
  }

  @Test
  public void nextVideoHoldsTheLastFrameBeforeItsNewSurfaceIsReady() throws Exception {
    configure();
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      SceneView scene = (SceneView) field(c.get(), "scene");
      Bitmap frame = Bitmap.createBitmap(256, 144, Bitmap.Config.ARGB_8888);
      set(scene, "lastVideoFrame", frame);
      scene.start("aerial", false);
      ((ImageView) field(scene, "photo")).setAlpha(0);
      scene.next(1);
      ImageView photo = (ImageView) field(scene, "photo");
      assertNotNull(
          "The outgoing film must cover the next film's preparation", photo.getDrawable());
      assertSame(
          frame, ((android.graphics.drawable.BitmapDrawable) photo.getDrawable()).getBitmap());
      assertEquals(1f, photo.getAlpha(), 0f);
    }
  }

  @org.robolectric.annotation.Implements(android.view.TextureView.class)
  public static class EarlyTextureShadow extends org.robolectric.shadows.ShadowTextureView {
    @org.robolectric.annotation.Implementation
    protected Bitmap getBitmap(int width, int height) {
      return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }
  }

  @Test
  @Config(shadows = EarlyTextureShadow.class)
  public void anUnrenderedIncomingVideoMustNotReplaceTheOutgoingFrame() throws Exception {
    configure();
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      SceneView scene = (SceneView) field(c.get(), "scene");
      scene.start("aerial", false);
      Bitmap outgoing = Bitmap.createBitmap(256, 144, Bitmap.Config.ARGB_8888);
      set(scene, "lastVideoFrame", outgoing);
      set(scene, "ready", true);
      set(scene, "rendered", false);
      set(scene, "firstFramePending", true);
      scene.onSurfaceTextureUpdated(null);
      assertSame(
          "A prepared decoder may still expose an empty incoming texture",
          outgoing,
          field(scene, "lastVideoFrame"));
    }
  }

  @Test
  public void repeatedSpotifyStateDoesNotRewriteMetadataOrReplaceControlArtwork() throws Exception {
    configure();
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity a = c.get();
      MediaSession session = new MediaSession(a, "refresh-check");
      try {
        Bitmap cover = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        org.robolectric.Shadows.shadowOf(session.getController())
            .setMetadata(
                new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Evening")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist")
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover)
                    .build());
        org.robolectric.Shadows.shadowOf(session.getController())
            .setPlaybackState(
                new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 100, 1f)
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                    .build());
        call(
            a, "updateMusic", android.media.session.MediaController.class, session.getController());
        TextView title = (TextView) field(a, "musicTitle");
        int[] writes = {0};
        title.addTextChangedListener(
            new android.text.TextWatcher() {
              public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

              public void onTextChanged(CharSequence s, int start, int before, int count) {
                writes[0]++;
              }

              public void afterTextChanged(android.text.Editable s) {}
            });
        ImageView toggle = (ImageView) field(a, "musicToggle");
        Object icon = toggle.getDrawable();
        Object art = ((ImageView) field(a, "musicArt")).getDrawable();
        call(
            a, "updateMusic", android.media.session.MediaController.class, session.getController());
        assertEquals("Unchanged text must not trigger a text/layout update", 0, writes[0]);
        assertSame(icon, toggle.getDrawable());
        assertSame(art, ((ImageView) field(a, "musicArt")).getDrawable());
        org.robolectric.Shadows.shadowOf(session.getController())
            .setMetadata(
                new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Evening")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist")
                    .putBitmap(
                        MediaMetadata.METADATA_KEY_ALBUM_ART,
                        cover.copy(Bitmap.Config.ARGB_8888, false))
                    .build());
        call(
            a, "updateMusic", android.media.session.MediaController.class, session.getController());
        assertSame(
            "Identical artwork received in a fresh metadata parcel must be reused",
            art,
            ((ImageView) field(a, "musicArt")).getDrawable());

        assertTrue(
            "Unavailable skip arrows must remain readable",
            ((View) field(a, "musicPrevious")).getAlpha() >= .6f);
        org.robolectric.Shadows.shadowOf(session.getController())
            .setMetadata(
                new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Evening")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "New artist")
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover)
                    .build());
        call(
            a, "updateMusic", android.media.session.MediaController.class, session.getController());
        assertEquals("New artist", ((TextView) field(a, "musicArtist")).getText().toString());
      } finally {
        session.release();
      }
    }
  }
}
