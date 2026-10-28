package com.gathin.tvhome;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class FeatureIntegrationTest {
  @Test
  public void layoutPreservesMissingAppsAndFoldersAcrossRecreation() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("cinema_layout", 0).edit().clear().commit();
    LauncherLayout layout = new LauncherLayout(context);
    layout.ordered(new LinkedHashSet<>(List.of("a", "b", "c")));
    layout.setList("order", List.of("c", "a", "b"));
    assertEquals(List.of("c", "b"), layout.ordered(new LinkedHashSet<>(List.of("b", "c"))));
    layout.folder("a", "Cinema");
    layout.rename("a", "Nuvio");
    LauncherLayout restored = new LauncherLayout(context);
    assertEquals(
        List.of("c", "a", "b"), restored.ordered(new LinkedHashSet<>(List.of("a", "b", "c"))));
    assertEquals("Nuvio", restored.name("a", "other"));
    assertEquals("a", restored.folders().getJSONArray("Cinema").getString(0));
    restored.folder("a", "");
    assertEquals(0, restored.folders().length());
  }

  @Test
  public void pinUsesRandomSaltAndRejectsIncorrectInput() throws Exception {
    String first = PinLock.create("UDLR"), second = PinLock.create("UDLR");
    assertNotEquals(first, second);
    assertTrue(PinLock.matches(first, "UDLR"));
    assertFalse(PinLock.matches(first, "1234"));
    assertFalse(PinLock.matches("broken", "UDLR"));
  }

  @Test
  public void settingsBackupDoesNotExportPinPairingOrLocation() throws Exception {
    SharedPreferences prefs =
        RuntimeEnvironment.getApplication().getSharedPreferences("test_backup", 0);
    prefs
        .edit()
        .clear()
        .putString("parental_pin", "secret")
        .putString("weather_lat", "38.0")
        .putString("release_manifest", "https://private.example")
        .putBoolean("large_clock", true)
        .commit();
    String exported = SettingsBackup.export(prefs);
    assertFalse(exported.contains("secret"));
    assertFalse(exported.contains("38.0"));
    assertFalse(exported.contains("private.example"));
    assertEquals(true, SettingsBackup.parse(exported).get("large_clock"));
  }

  @Test
  public void activityBuildsAndSupportsEditorSettings() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      assertNotNull(activity.findViewById(android.R.id.content));
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put(
                  "settings",
                  new JSONObject()
                      .put("wallpaper_motion", "always")
                      .put("screensaver_mode", "photos")));
      assertEquals(
          "always", activity.state().getJSONObject("settings").getString("wallpaper_motion"));
    }
  }

  @Test
  public void selectedCityStaysVisibleBelowLargeClockAndWeatherCanBeDisabled() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs = context.getSharedPreferences("tvhome_prefs", 0);
    prefs
        .edit()
        .clear()
        .putString("weather_name", "Lamia")
        .putString("weather_lat", "38.9")
        .putString("weather_lon", "22.43")
        .putBoolean("weather_enabled", true)
        .commit();
    java.nio.file.Files.write(
        WeatherData.cacheFile(context).toPath(),
        "{\"current\":{\"temperature_2m\":21,\"weather_code\":0},\"daily\":{\"sunrise\":[\"2026-10-27T07:00\"],\"sunset\":[\"2026-10-27T18:00\"],\"temperature_2m_max\":[23],\"temperature_2m_min\":[15]}}"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put("settings", new JSONObject().put("large_clock", true)));
      java.lang.reflect.Field field = MainActivity.class.getDeclaredField("weatherLine");
      field.setAccessible(true);
      android.widget.TextView weather = (android.widget.TextView) field.get(activity);
      for (int i = 0; i < 100 && !weather.getText().toString().contains("21°"); i++) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Thread.sleep(10);
      }
      assertTrue(weather.getText().toString().contains("Lamia  ·  21°"));
      String previousForecast = weather.getText().toString();
      java.lang.reflect.Method refresh = MainActivity.class.getDeclaredMethod("loadWeather");
      refresh.setAccessible(true);
      refresh.invoke(activity);
      assertEquals(
          "Keep a valid forecast visible while refreshing",
          previousForecast,
          weather.getText().toString());

      android.view.View header = (android.view.View) weather.getParent().getParent();
      header.measure(
          android.view.View.MeasureSpec.makeMeasureSpec(1000, 1073741824),
          android.view.View.MeasureSpec.makeMeasureSpec(
              header.getLayoutParams().height, 1073741824));
      header.layout(0, 0, 1000, header.getMeasuredHeight());
      android.graphics.Rect bounds = new android.graphics.Rect();
      weather.getDrawingRect(bounds);
      ((android.view.ViewGroup) header).offsetDescendantRectToMyCoords(weather, bounds);
      assertTrue(
          "Weather must fit inside the large clock header", bounds.bottom <= header.getHeight());
      assertTrue(weather.getHeight() > 0);
      assertNull(
          "Weather stays directly on the wallpaper, without a visible strip",
          weather.getBackground());
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put("settings", new JSONObject().put("weather_enabled", false)));
      assertEquals("", weather.getText().toString());
    }
  }

  @Test
  public void nativePairingRejectsMissingTokenAndForeignOrigin() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    try (PhoneServer server =
        new PhoneServer(
            context,
            new MediaLibrary(context),
            new PhoneServer.Host() {
              public JSONObject state() throws Exception {
                return new JSONObject().put("ok", true);
              }

              public void edit(JSONObject command) {
                throw new AssertionError("Foreign request reached the editor");
              }
            })) {
      java.lang.reflect.Field field = PhoneServer.class.getDeclaredField("server");
      field.setAccessible(true);
      int port = ((java.net.ServerSocket) field.get(server)).getLocalPort();
      java.net.HttpURLConnection missing =
          (java.net.HttpURLConnection)
              new java.net.URL("http://127.0.0.1:" + port + "/api/state").openConnection();
      missing.setReadTimeout(2000);
      assertEquals(404, missing.getResponseCode());
      missing.disconnect();
      java.net.HttpURLConnection font =
          (java.net.HttpURLConnection)
              new java.net.URL("http://127.0.0.1:" + port + "/" + server.token + "/outfit.ttf")
                  .openConnection();
      font.setReadTimeout(2000);
      assertEquals(200, font.getResponseCode());
      assertEquals("font/ttf", font.getContentType());
      try (java.io.InputStream stream = font.getInputStream()) {
        assertTrue(stream.read() != -1);
      }
      font.disconnect();
      try (java.net.Socket foreign = new java.net.Socket("127.0.0.1", port)) {
        foreign.setSoTimeout(2000);
        String request =
            "POST /"
                + server.token
                + "/api/edit HTTP/1.1\r\nHost: 127.0.0.1:"
                + port
                + "\r\nOrigin: https://foreign.example\r\nContent-Length: 2\r\n\r\n{}";
        foreign
            .getOutputStream()
            .write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        String response =
            new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                        foreign.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII))
                .readLine();
        assertTrue(response.startsWith("HTTP/1.1 403"));
      }
    }
  }

  @Test
  public void nativePairedSettingsAreAppliedOnlyOnMainThread() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    java.util.concurrent.atomic.AtomicBoolean applied =
        new java.util.concurrent.atomic.AtomicBoolean();
    PhoneServer.Host owner =
        new PhoneServer.Host() {
          public JSONObject state() throws Exception {
            return new JSONObject().put("ok", true);
          }

          public void edit(JSONObject command) {
            assertEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper());
            applied.set(true);
          }
        };
    try (PhoneServer server = new PhoneServer(context, new MediaLibrary(context), owner)) {
      java.lang.reflect.Field field = PhoneServer.class.getDeclaredField("server");
      field.setAccessible(true);
      int port = ((java.net.ServerSocket) field.get(server)).getLocalPort();
      java.util.concurrent.ExecutorService io =
          java.util.concurrent.Executors.newSingleThreadExecutor();
      try {
        java.util.concurrent.Future<Integer> response =
            io.submit(
                () -> {
                  java.net.HttpURLConnection connection =
                      (java.net.HttpURLConnection)
                          new java.net.URL(
                                  "http://127.0.0.1:" + port + "/" + server.token + "/api/edit")
                              .openConnection();
                  connection.setRequestMethod("POST");
                  connection.setDoOutput(true);
                  connection.setReadTimeout(3000);
                  connection
                      .getOutputStream()
                      .write("{\"op\":\"test\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                  int status = connection.getResponseCode();
                  connection.disconnect();
                  return status;
                });
        for (int i = 0; i < 150 && !response.isDone(); i++) {
          org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
          Thread.sleep(10);
        }
        assertEquals(200, (int) response.get(3, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(applied.get());
        java.lang.ref.Reference.reachabilityFence(owner);
      } finally {
        io.shutdownNow();
      }
    }
  }

  @Test
  public void sceneFavoritesAndExclusionsUseTheSameNativeEligibility() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs = context.getSharedPreferences("scenes_test", 0);
    prefs.edit().clear().commit();
    org.json.JSONArray films = SceneLibrary.catalogue(context);
    assertNull(SceneLibrary.find(films, null));
    String id = films.getJSONObject(0).getString("id"),
        category = films.getJSONObject(0).getString("category");
    SceneLibrary.change(context, prefs, "favorite", id, true);
    prefs.edit().putBoolean("scene_favorites_only", true).commit();
    assertEquals(List.of(id), SceneLibrary.eligible(films, prefs));
    SceneLibrary.change(context, prefs, "category", category, true);
    assertTrue(SceneLibrary.eligible(films, prefs).isEmpty());
    SceneLibrary.change(context, prefs, "category", category, false);
    assertEquals(List.of(id), SceneLibrary.eligible(films, prefs));
    assertEquals(114, films.length());
    assertTrue(
        films.getJSONObject(0).getString("thumbnail").startsWith("https://sylvan.apple.com/"));
  }

  @Test
  public void remoteNavigationAndVolumeDoNotNeedKeyInjection() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    java.util.concurrent.atomic.AtomicReference<String> pressed =
        new java.util.concurrent.atomic.AtomicReference<>();
    PhoneServer.Host host =
        new PhoneServer.Host() {
          public JSONObject state() {
            return new JSONObject();
          }

          public void edit(JSONObject command) {}

          public boolean active() {
            return true;
          }

          public void navigate(String key) {
            pressed.set(key);
          }
        };
    RemoteControls.execute(context, host, "right");
    assertEquals("right", pressed.get());
    android.media.AudioManager audio =
        (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 3, 0);
    RemoteControls.execute(context, null, "volume_up");
    assertEquals(4, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
    try {
      RemoteControls.execute(context, host, "shell");
      fail("Unknown command accepted");
    } catch (IllegalArgumentException expected) {
    }
    assertEquals("right", pressed.get());
  }

  @Test
  public void settingsHubHasSixSectionsInsteadOfAnActionDump() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.navigate("settings");
      android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertNotNull(dialog);
      java.util.List<String> labels = new java.util.ArrayList<>();
      collectText(dialog.getWindow().getDecorView(), labels);
      for (String title :
          List.of(
              "Appearance", "Scenes", "Apps & inputs", "Connections", "Sound & picture", "System"))
        assertTrue(title, labels.contains(title));
      assertFalse(labels.contains("Spotify control access"));
    }
  }

  @Test
  public void settingsSwitchSlidesInPlaceAndSupportsRemoteKeysAndBack() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.navigate("settings");
      android.view.View appearance =
          findText(
              org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView(),
              "Appearance");
      while (!appearance.isClickable()) appearance = (android.view.View) appearance.getParent();
      appearance.performClick();
      findText(
              org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView(),
              "Clock & widgets")
          .performClick();
      android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
      android.widget.Switch toggle =
          (android.widget.Switch) findText(dialog.getWindow().getDecorView(), "Large clock");
      boolean before = toggle.isChecked();
      toggle.requestFocus();
      assertSame(toggle, dialog.getWindow().getDecorView().findFocus());
      activity.navigate("ok");
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
          .idleFor(java.time.Duration.ofMillis(250));
      assertEquals(!before, toggle.isChecked());
      assertEquals(!before, activity.state().getJSONObject("settings").getBoolean("large_clock"));
      assertSame(
          "Changing a switch must not recreate its panel",
          dialog,
          org.robolectric.shadows.ShadowDialog.getLatestDialog());
      assertTrue(toggle.hasFocus());
      activity.navigate("left");
      assertFalse(toggle.isChecked());
      activity.navigate("right");
      assertTrue(toggle.isChecked());
      toggle.dispatchKeyEvent(
          new android.view.KeyEvent(
              android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_CENTER));
      assertFalse("The physical remote must use the same switch", toggle.isChecked());
      activity.navigate("back");
      assertNotNull(
          findText(
              org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView(),
              "Appearance"));
    }
  }

  @Test
  public void aerialCertificateIsAuthenticAndScopedToAppleOnly() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    java.security.cert.X509Certificate certificate;
    try (java.io.InputStream input = context.getResources().openRawResource(R.raw.apple_root_ca)) {
      certificate =
          (java.security.cert.X509Certificate)
              java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(input);
    }
    certificate.verify(certificate.getPublicKey());
    certificate.checkValidity();
    byte[] digest =
        java.security.MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
    StringBuilder hex = new StringBuilder();
    for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
    assertEquals(
        "b0b1730ecbc7ff4505142c49f1295e6eda6bcaed7e2c68c5be91b5a11001f024", hex.toString());
    try (android.content.res.XmlResourceParser xml =
        context.getResources().getXml(R.xml.network_security_config)) {
      int domains = 0, anchors = 0;
      boolean scoped = false;
      for (int event = xml.next();
          event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
          event = xml.next()) {
        if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
          if (xml.getName().equals("domain-config")) {
            scoped = true;
            assertFalse(xml.getAttributeBooleanValue(null, "cleartextTrafficPermitted", true));
          }
          if (xml.getName().equals("domain")) {
            assertTrue(scoped);
            assertFalse(xml.getAttributeBooleanValue(null, "includeSubdomains", true));
            assertEquals("sylvan.apple.com", xml.nextText());
            domains++;
          }
          if (xml.getName().equals("certificates")
              && xml.getAttributeResourceValue(null, "src", 0) == R.raw.apple_root_ca) {
            assertTrue(scoped);
            anchors++;
          }
        } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG
            && xml.getName().equals("domain-config")) scoped = false;
      }
      assertEquals(1, domains);
      assertEquals(1, anchors);
    }
  }

  @Test
  public void choosingAerialStartsPlaybackWithoutWaitingForIdle() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      java.lang.reflect.Method choose = MainActivity.class.getDeclaredMethod("chooseWallpaper");
      choose.setAccessible(true);
      choose.invoke(activity);
      findText(
              org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView(),
              "Aerial films")
          .performClick();
      java.lang.reflect.Field sceneField = MainActivity.class.getDeclaredField("scene");
      sceneField.setAccessible(true);
      SceneView scene = (SceneView) sceneField.get(activity);
      java.lang.reflect.Field playing = SceneView.class.getDeclaredField("playing");
      playing.setAccessible(true);
      assertEquals(
          "aerial", activity.state().getJSONObject("settings").getString("wallpaper_selection"));
      assertTrue(playing.getBoolean(scene));
      assertEquals(android.view.View.VISIBLE, scene.getVisibility());
    }
  }

  @Test
  @Config(sdk = 33)
  public void pairedDpadUsesGlobalActionsOutsideHomeWithoutReadingWindows() throws Exception {
    TvAccessibilityService service =
        Robolectric.buildService(TvAccessibilityService.class).create().get();
    service.onServiceConnected();
    try {
      assertTrue(TvAccessibilityService.globalDpadAvailable());
      for (String key : List.of("up", "down", "left", "right", "ok", "home", "back"))
        RemoteControls.execute(RuntimeEnvironment.getApplication(), null, key);
      assertEquals(
          List.of(16, 17, 18, 19, 20, 2, 1),
          org.robolectric.Shadows.shadowOf(service).getGlobalActionsPerformed());
      assertEquals(0, TvAccessibilityService.remoteActionId("menu"));
      assertEquals(0, TvAccessibilityService.remoteActionId("run-shell"));
    } finally {
      service.onDestroy();
    }
    assertFalse(TvAccessibilityService.globalDpadAvailable());
  }

  @Test
  public void olderAndroidOnlyExposesSupportedHomeAndBackActions() {
    assertEquals(0, TvAccessibilityService.remoteActionId("up"));
    assertEquals(0, TvAccessibilityService.remoteActionId("ok"));
    assertEquals(2, TvAccessibilityService.remoteActionId("home"));
    assertEquals(1, TvAccessibilityService.remoteActionId("back"));
  }

  @Test
  public void aerialKeepsPlayingAcrossDockFocusAndPausesForSettings() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put(
                  "settings",
                  new JSONObject()
                      .put("wallpaper_selection", "aerial")
                      .put("wallpaper_motion", "idle")
                      .put("reduced_motion", false)));
      java.lang.reflect.Field sceneField = MainActivity.class.getDeclaredField("scene");
      sceneField.setAccessible(true);
      SceneView scene = (SceneView) sceneField.get(activity);
      java.lang.reflect.Field playing = SceneView.class.getDeclaredField("playing");
      playing.setAccessible(true);
      scene.start("aerial", false);
      activity.dispatchKeyEvent(
          new android.view.KeyEvent(
              android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
      assertTrue("Dock movement must not stop the aerial player", playing.getBoolean(scene));
      activity.navigate("settings");
      assertFalse("Settings should release the decoder", playing.getBoolean(scene));
      activity.navigate("home");
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put("settings", new JSONObject().put("reduced_motion", true)));
      scene.start("aerial", false);
      activity.dispatchKeyEvent(
          new android.view.KeyEvent(
              android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
      assertFalse("Reduced motion must remain respected", playing.getBoolean(scene));
    }
  }

  @Test
  public void dockGlassCrossfadesWithoutAccumulatingLayers() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put(
                  "settings",
                  new JSONObject()
                      .put("home_plate", true)
                      .put("effects", "full")
                      .put("reduced_motion", false)));
      java.lang.reflect.Method update =
          MainActivity.class.getDeclaredMethod("updateSceneGlass", android.graphics.Bitmap.class);
      update.setAccessible(true);
      java.lang.reflect.Field field = MainActivity.class.getDeclaredField("dockSurface");
      field.setAccessible(true);
      android.view.View dock = (android.view.View) field.get(activity);
      android.graphics.Bitmap sample =
          android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
      sample.eraseColor(android.graphics.Color.RED);
      update.invoke(activity, sample);
      android.graphics.drawable.Drawable background = dock.getBackground();
      assertTrue(background instanceof FrostedPanelDrawable);
      for (int i = 0; i < 5; i++) update.invoke(activity, sample);
      assertSame(
          "Video samples must not replace the view background", background, dock.getBackground());
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put("settings", new JSONObject().put("reduced_motion", true)));
      dock = (android.view.View) field.get(activity);
      update.invoke(activity, sample);
      assertTrue(dock.getBackground() instanceof FrostedPanelDrawable);
    }
  }

  @Test
  @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  public void glassOpacityIncludesItsSheenAndRimDuringCrossfade() {
    android.view.View panel = new android.view.View(RuntimeEnvironment.getApplication());
    panel.layout(0, 0, 100, 100);
    android.graphics.Bitmap source =
        android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
    source.eraseColor(android.graphics.Color.RED);
    FrostedPanelDrawable glass = new FrostedPanelDrawable(panel, panel, source, "quiet");
    glass.setBounds(0, 0, 100, 100);
    android.graphics.Bitmap output =
        android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888);
    glass.setAlpha(0);
    glass.draw(new android.graphics.Canvas(output));
    assertEquals(
        "An invisible glass layer must not leave a bright rim",
        0,
        android.graphics.Color.alpha(output.getPixel(50, 1)));
    assertEquals(0, android.graphics.Color.alpha(output.getPixel(50, 50)));
    glass.setAlpha(128);
    glass.draw(new android.graphics.Canvas(output));
    int alpha = android.graphics.Color.alpha(output.getPixel(50, 50));
    assertTrue(alpha >= 120 && alpha <= 145);
  }

  @Test
  @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  public void videoGlassMaintainsOpacityAndBlendsColorThroughoutEachUpdate() {
    android.view.View panel = new android.view.View(RuntimeEnvironment.getApplication());
    panel.layout(0, 0, 100, 100);
    android.graphics.Bitmap red =
        android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
    red.eraseColor(android.graphics.Color.RED);
    android.graphics.Bitmap blue =
        android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
    blue.eraseColor(android.graphics.Color.BLUE);
    FrostedPanelDrawable glass = new FrostedPanelDrawable(panel, panel, red, "quiet");
    glass.setBounds(0, 0, 100, 100);
    android.graphics.Bitmap output =
        android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas = new android.graphics.Canvas(output);
    glass.draw(canvas);
    int before = output.getPixel(50, 50);
    glass.updateFrame(blue, true);
    int middle = 0;
    for (int i = 0; i <= 4; i++) {
      output.eraseColor(android.graphics.Color.TRANSPARENT);
      glass.draw(canvas);
      int pixel = output.getPixel(50, 50);
      assertEquals(
          "A video frame must not become visible through a fading dock",
          255,
          android.graphics.Color.alpha(pixel));
      if (i == 2) middle = pixel;
      if (i < 4)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(250));
    }
    int after = output.getPixel(50, 50);
    assertTrue(android.graphics.Color.red(before) > android.graphics.Color.red(after));
    assertTrue(android.graphics.Color.blue(before) < android.graphics.Color.blue(after));
    assertEquals(
        (android.graphics.Color.red(before) + android.graphics.Color.red(after)) / 2,
        android.graphics.Color.red(middle),
        2);
    assertEquals(
        (android.graphics.Color.blue(before) + android.graphics.Color.blue(after)) / 2,
        android.graphics.Color.blue(middle),
        2);
  }

  @Test
  @Config(shadows = SilentPlayerShadow.class)
  public void returningFromAerialSaverKeepsTheSameRendererRunning() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put(
                  "settings",
                  new JSONObject()
                      .put("wallpaper_selection", "aerial")
                      .put("screensaver_mode", "aerial")
                      .put("reduced_motion", false)
                      .put("wallpaper_motion", "idle")));
      SceneView scene = sceneOf(activity);
      scene.start("aerial", false);
      android.media.MediaPlayer player = new android.media.MediaPlayer();
      java.lang.reflect.Field field = SceneView.class.getDeclaredField("player");
      field.setAccessible(true);
      field.set(scene, player);
      java.lang.reflect.Method start = MainActivity.class.getDeclaredMethod("startScreensaver");
      start.setAccessible(true);
      start.invoke(activity);
      java.lang.reflect.Method stop = MainActivity.class.getDeclaredMethod("stopScreensaver");
      stop.setAccessible(true);
      stop.invoke(activity);
      assertSame(
          "Returning from the same aerial saver must not reconnect the movie",
          player,
          field.get(scene));
      assertTrue(scenePlaying(scene));
    }
  }

  @Test
  public void browsingContinueWatchingKeepsVideoUntilCoverIsReady() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put(
                  "settings",
                  new JSONObject()
                      .put("wallpaper_selection", "aerial")
                      .put("reduced_motion", false)
                      .put("wallpaper_motion", "idle")));
      SceneView scene = sceneOf(activity);
      scene.start("aerial", false);
      java.lang.reflect.Field browse = MainActivity.class.getDeclaredField("browseOpen");
      browse.setAccessible(true);
      browse.setBoolean(activity, true);
      activity.dispatchKeyEvent(
          new android.view.KeyEvent(
              android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
      assertTrue(
          "Changing Continue Watching focus must keep the aerial running", scenePlaying(scene));
      ContinueWatching.Item item = new ContinueWatching.Item();
      item.intent = "";
      item.artwork = "https://example.com/poster.jpg";
      java.lang.reflect.Method preview =
          MainActivity.class.getDeclaredMethod("previewBackdrop", ContinueWatching.Item.class);
      preview.setAccessible(true);
      preview.invoke(activity, item);
      java.lang.reflect.Field backdrop = MainActivity.class.getDeclaredField("backdropLocation");
      backdrop.setAccessible(true);
      assertEquals("https://example.com/poster.jpg", backdrop.get(activity));
    }
  }

  @Test
  @Config(shadows = SilentPlayerShadow.class)
  public void leavingHomeKeepsTheSceneVisibleAndReleasesOffTheUiThread() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      SceneView scene = sceneOf(activity);
      scene.start("aerial", false);
      java.util.concurrent.CountDownLatch released = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.atomic.AtomicBoolean onMain =
          new java.util.concurrent.atomic.AtomicBoolean();
      android.media.MediaPlayer player =
          new android.media.MediaPlayer() {
            @Override
            public void release() {
              onMain.set(android.os.Looper.myLooper() == android.os.Looper.getMainLooper());
              super.release();
              released.countDown();
            }
          };
      java.lang.reflect.Field field = SceneView.class.getDeclaredField("player");
      field.setAccessible(true);
      field.set(scene, player);
      android.graphics.Bitmap last =
          android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
      java.lang.reflect.Field cached = SceneView.class.getDeclaredField("lastVideoFrame");
      cached.setAccessible(true);
      cached.set(scene, last);
      controller.pause();
      assertTrue(released.await(2, java.util.concurrent.TimeUnit.SECONDS));
      assertFalse("Decoder teardown must not block the next app's first window", onMain.get());
      assertEquals(
          "The handoff must not uncover the static wallpaper",
          android.view.View.VISIBLE,
          scene.getVisibility());
      java.lang.reflect.Field photoField = SceneView.class.getDeclaredField("photo");
      photoField.setAccessible(true);
      android.widget.ImageView photo = (android.widget.ImageView) photoField.get(scene);
      assertSame(
          last, ((android.graphics.drawable.BitmapDrawable) photo.getDrawable()).getBitmap());
      controller.resume();
      assertSame(
          last, ((android.graphics.drawable.BitmapDrawable) photo.getDrawable()).getBitmap());
    }
  }

  @org.robolectric.annotation.Implements(android.media.MediaPlayer.class)
  public static class SilentPlayerShadow extends org.robolectric.shadows.ShadowMediaPlayer {
    @org.robolectric.annotation.Implementation
    protected void __constructor__() {}

    @org.robolectric.annotation.Implementation
    protected void release() {}
  }

  @Test
  public void appAndContinueWatchingLaunchImmediatelyWithNativeAnimation() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put("settings", new JSONObject().put("reduced_motion", false)));
      android.view.View anchor = activity.findViewById(android.R.id.content);
      anchor.layout(0, 0, 320, 180);
      java.lang.reflect.Field focused = MainActivity.class.getDeclaredField("focusedCard");
      focused.setAccessible(true);
      focused.set(activity, anchor);
      java.lang.reflect.Method open =
          MainActivity.class.getDeclaredMethod("open", android.content.Intent.class);
      open.setAccessible(true);
      open.invoke(activity, new android.content.Intent(android.provider.Settings.ACTION_SETTINGS));
      org.robolectric.shadows.ShadowActivity.IntentForResult launch =
          org.robolectric.Shadows.shadowOf(activity).getNextStartedActivityForResult();
      assertNotNull("Launching must not wait for a handler or exit animation", launch);
      assertNotNull("The app window should scale up from the selected tile", launch.options);
      ContinueWatching.Item item = new ContinueWatching.Item();
      item.pkg = "com.nuvio.tv";
      item.intent = "intent://test#Intent;scheme=nuvio;package=com.nuvio.tv;end";
      java.lang.reflect.Method program =
          MainActivity.class.getDeclaredMethod("openProgram", ContinueWatching.Item.class);
      program.setAccessible(true);
      program.invoke(activity, item);
      launch = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivityForResult();
      assertNotNull(launch);
      assertNotNull(launch.options);
      assertEquals(item.pkg, launch.intent.getPackage());
      java.lang.reflect.Field active = MainActivity.class.getDeclaredField("active");
      active.setAccessible(true);
      assertFalse(
          "Successful handoff must suspend Home work immediately", active.getBoolean(activity));
      controller.pause().resume();
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put("settings", new JSONObject().put("reduced_motion", true)));
      open.invoke(activity, new android.content.Intent(android.provider.Settings.ACTION_SETTINGS));
      launch = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivityForResult();
      assertNotNull(launch);
      assertNull(launch.options);
    }
  }

  @Test
  public void switchingSceneSourceKeepsTheVisiblePictureUntilVideoRenders() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      SceneView scene = sceneOf(controller.get());
      java.lang.reflect.Field photoField = SceneView.class.getDeclaredField("photo");
      photoField.setAccessible(true);
      android.widget.ImageView photo = (android.widget.ImageView) photoField.get(scene);
      android.graphics.Bitmap last =
          android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
      last.eraseColor(android.graphics.Color.BLUE);
      photo.setImageBitmap(last);
      scene.start("aerial", false);
      assertSame(
          "Changing source must not clear the visible background",
          last,
          ((android.graphics.drawable.BitmapDrawable) photo.getDrawable()).getBitmap());
    }
  }

  @Test
  public void ownHomeLaunchesUseHomeCategoryAndKeepShortcutExtras() {
    Context context = RuntimeEnvironment.getApplication();
    android.content.Intent home = MainActivity.homeIntent(context).putExtra("control_center", true);
    assertEquals(android.content.Intent.ACTION_MAIN, home.getAction());
    assertTrue(home.hasCategory(android.content.Intent.CATEGORY_HOME));
    assertEquals(MainActivity.class.getName(), home.getComponent().getClassName());
    assertTrue(home.getBooleanExtra("control_center", false));
    assertTrue((home.getFlags() & android.content.Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
  }

  @Test
  @Config(shadows = SilentPlayerShadow.class)
  public void settingsRetainsDecoderAndBackResumesItImmediately() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put(
                  "settings",
                  new JSONObject()
                      .put("wallpaper_selection", "aerial")
                      .put("reduced_motion", false)
                      .put("wallpaper_motion", "idle")));
      SceneView scene = sceneOf(activity);
      scene.start("aerial", false);
      java.util.concurrent.atomic.AtomicInteger
          released = new java.util.concurrent.atomic.AtomicInteger(),
          started = new java.util.concurrent.atomic.AtomicInteger();
      android.media.MediaPlayer player =
          new android.media.MediaPlayer() {
            @Override
            public void pause() {}

            @Override
            public void start() {
              started.incrementAndGet();
            }

            @Override
            public int getCurrentPosition() {
              return 1000;
            }

            @Override
            public void release() {
              released.incrementAndGet();
            }
          };
      java.lang.reflect.Field field = SceneView.class.getDeclaredField("player");
      field.setAccessible(true);
      field.set(scene, player);
      java.lang.reflect.Field ready = SceneView.class.getDeclaredField("ready");
      ready.setAccessible(true);
      ready.setBoolean(scene, true);
      activity.navigate("settings");
      assertSame("Settings must retain its decoder", player, field.get(scene));
      assertEquals(0, released.get());
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put("settings", new JSONObject().put("large_clock", true)));
      assertSame("Changing widgets must not tear the video down", player, field.get(scene));
      activity.navigate("back");
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertSame(player, field.get(scene));
      assertTrue(scenePlaying(scene));
      assertEquals(1, started.get());
    }
  }

  @Test
  public void movieBackdropsHoldTheirPreviousCoverUntilTheNextOneIsReady() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
      MainActivity activity = controller.get();
      activity.edit(
          new JSONObject()
              .put("op", "settings")
              .put(
                  "settings",
                  new JSONObject()
                      .put("wallpaper_selection", "aerial")
                      .put("reduced_motion", false)
                      .put("cinematic_artwork", true)));
      SceneView scene = sceneOf(activity);
      scene.start("aerial", false);
      java.lang.reflect.Field artField = MainActivity.class.getDeclaredField("art");
      artField.setAccessible(true);
      java.lang.reflect.Field cacheField = ArtworkLoader.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      @SuppressWarnings("unchecked")
      android.util.LruCache<String, android.graphics.Bitmap> cache =
          (android.util.LruCache<String, android.graphics.Bitmap>)
              cacheField.get(artField.get(activity));
      android.graphics.Bitmap red =
          android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
      red.eraseColor(android.graphics.Color.RED);
      android.graphics.Bitmap blue =
          android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
      blue.eraseColor(android.graphics.Color.BLUE);
      cache.put("https://example.com/reacher.jpg:1280:720", red);
      cache.put("https://example.com/boys.jpg:1280:720", blue);
      java.lang.reflect.Method preview =
          MainActivity.class.getDeclaredMethod("previewBackdrop", ContinueWatching.Item.class);
      preview.setAccessible(true);
      ContinueWatching.Item item = new ContinueWatching.Item();
      item.intent = "";
      item.artwork = "https://example.com/reacher.jpg";
      preview.invoke(activity, item);
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
          .idleFor(java.time.Duration.ofMillis(600));
      java.lang.reflect.Field backdropField = MainActivity.class.getDeclaredField("backdrop");
      backdropField.setAccessible(true);
      android.widget.ImageView backdrop = (android.widget.ImageView) backdropField.get(activity);
      assertNotNull("Movie covers should appear over an aerial", backdrop.getDrawable());
      android.graphics.drawable.Drawable previous = backdrop.getDrawable();
      assertEquals(1f, backdrop.getAlpha(), .01f);
      assertFalse("The hidden video should be held in place", scenePlaying(scene));
      item.artwork = "https://example.com/boys.jpg";
      preview.invoke(activity, item);
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
          .idleFor(java.time.Duration.ofMillis(200));
      assertSame("Reacher stays visible while The Boys loads", previous, backdrop.getDrawable());
      assertEquals(1f, backdrop.getAlpha(), .01f);
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
          .idleFor(java.time.Duration.ofMillis(400));
      assertTrue(backdrop.getDrawable() instanceof android.graphics.drawable.TransitionDrawable);
      android.graphics.drawable.TransitionDrawable blend =
          (android.graphics.drawable.TransitionDrawable) backdrop.getDrawable();
      assertFalse(blend.isCrossFadeEnabled());
      assertSame(previous, blend.getDrawable(0));
      assertEquals(1f, backdrop.getAlpha(), .01f);
      java.lang.reflect.Field rootField = MainActivity.class.getDeclaredField("root");
      rootField.setAccessible(true);
      android.view.ViewGroup root = (android.view.ViewGroup) rootField.get(activity);
      assertTrue(root.indexOfChild(backdrop) > root.indexOfChild(scene));
      java.lang.reflect.Method clear = MainActivity.class.getDeclaredMethod("clearBackdrop");
      clear.setAccessible(true);
      clear.invoke(activity);
      assertTrue("Dock focus resumes the held movie immediately", scenePlaying(scene));
    }
  }

  private static SceneView sceneOf(MainActivity activity) throws Exception {
    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("scene");
    field.setAccessible(true);
    return (SceneView) field.get(activity);
  }

  private static boolean scenePlaying(SceneView scene) throws Exception {
    java.lang.reflect.Field field = SceneView.class.getDeclaredField("playing");
    field.setAccessible(true);
    return field.getBoolean(scene);
  }

  private static android.widget.TextView findText(android.view.View view, String label) {
    if (view instanceof android.widget.TextView
        && ((android.widget.TextView) view).getText().toString().equals(label))
      return (android.widget.TextView) view;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.widget.TextView found = findText(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static void collectText(android.view.View view, java.util.List<String> values) {
    if (view instanceof android.widget.TextView)
      values.add(((android.widget.TextView) view).getText().toString());
    if (view instanceof android.view.ViewGroup)
      for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++)
        collectText(((android.view.ViewGroup) view).getChildAt(i), values);
  }
}
