package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** One native decoder shared by wallpaper and screensaver; the still scene survives pauses. */
final class SceneView extends FrameLayout implements TextureView.SurfaceTextureListener {
  interface Listener {
    void frame(Bitmap image);
  }

  private final SharedPreferences prefs;
  private final MediaLibrary library;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final ImageView photo, previousPhoto;
  private final TextureView film;
  private final TextView caption;
  private final Listener listener;
  private MediaPlayer player;
  private Bitmap lastVideoFrame;
  private String frameSavedUri = "";
  private volatile boolean frameWritePending;
  private boolean firstFramePending, seekPending, rendered;
  private Surface surface;
  private JSONArray aerials = new JSONArray();
  private final SceneQueue queue = new SceneQueue(new Random());
  private String requestedScene = "";
  private boolean emptyPlaylist;
  private String source = "", quality = "1080p", lastUri = "";
  private int savedPosition;
  private boolean playing, saver, ready, closed;
  private int index, failures, epoch, videoWidth, videoHeight;
  private final Runnable nextPhoto = () -> next(1);
  private final Runnable captions =
      new Runnable() {
        public void run() {
          if (!playing || closed) return;
          if (ready && player != null) {
            try {
              savedPosition = player.getCurrentPosition();
            } catch (IllegalStateException ignored) {
            }
          }
          updateCaption();
          if (ready && film.isAvailable()) sampleVideoFrame();
          handler.postDelayed(this, 1000);
        }
      };

  SceneView(Context context, SharedPreferences prefs, MediaLibrary library, Listener listener) {
    super(context);
    this.prefs = prefs;
    this.library = library;
    this.listener = listener;
    source = prefs.getString("scene_source", "");
    index = prefs.getInt("scene_index", 0);
    savedPosition = prefs.getInt("scene_position", 0);
    lastUri = prefs.getString("scene_uri", "");
    previousPhoto = new ImageView(context);
    previousPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
    addView(previousPhoto, new LayoutParams(-1, -1));
    photo = new ImageView(context);
    photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
    addView(photo, new LayoutParams(-1, -1));
    film = new TextureView(context);
    film.setSurfaceTextureListener(this);
    film.setOpaque(false);
    addView(film, new LayoutParams(-1, -1));
    caption = new TextView(context);
    caption.setTextColor(0xDDEAEAE2);
    caption.setTextSize(16);
    caption.setTypeface(getResources().getFont(R.font.outfit));
    caption.setShadowLayer(3, 0, 1, 0xFF000000);
    LayoutParams cp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
    cp.setMargins(54, 0, 54, 32);
    addView(caption, cp);
    setVisibility(GONE);
    String wallpaper = prefs.getString("wallpaper_selection", "misty");
    if (wallpaper.equals("aerial") || wallpaper.equals("video")) {
      SceneFrame saved = SceneFrame.read(context);
      if (saved != null && saved.source.equals(wallpaper)) {
        lastVideoFrame = saved.image;
        source = saved.source;
        lastUri = saved.uri;
        index = saved.index;
        savedPosition = saved.position;
        showLastVideoFrame();
        setVisibility(VISIBLE);
      }
    }
    io.execute(
        () -> {
          try (InputStream input = context.getAssets().open("aerials.json")) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = input.read(chunk)) != -1) buffer.write(chunk, 0, n);
            JSONArray list = new JSONObject(buffer.toString("UTF-8")).getJSONArray("videos");
            handler.post(
                () -> {
                  aerials = list;
                  if (source.equals("aerial") && lastVideoFrame != null && !lastUri.isEmpty()) {
                    ensurePlaylist();
                    for (int i = 0; i < aerials.length(); i++) {
                      JSONObject film = aerials.optJSONObject(i);
                      if (film != null
                          && (lastUri.equals(film.optString("url"))
                              || lastUri.equals(film.optString("uhd"))
                              || lastUri.equals(film.optString("dv")))) {
                        queue.select(film.optString("id"));
                        break;
                      }
                    }
                  }
                  if (playing && source.equals("aerial")) openVideo();
                });
          } catch (Exception ignored) {
          }
        });
  }

  void start(String mode, boolean screensaver) {
    if (closed) return;
    emptyPlaylist = false;
    boolean same = mode.equals(source);
    saver = screensaver;
    caption.setVisibility(saver ? VISIBLE : GONE);
    if (playing && same) {
      if ((mode.equals("aerial") || mode.equals("video")) && player == null) openVideo();
      updateCaption();
      return;
    }
    if (!same) {
      showLastVideoFrame();
      releasePlayer();
      source = mode;
      index = 0;
      savedPosition = 0;
      failures = 0;
      epoch++;
    }
    playing = true;
    setVisibility(VISIBLE);
    if (mode.equals("aerial") || mode.equals("video")) {
      if (player != null && ready) {
        try {
          if (!seekPending) player.start();
          if (rendered) {
            film.setAlpha(1);
            photo.setAlpha(0);
          }
        } catch (IllegalStateException e) {
          releasePlayer();
        }
      }
      if (player == null) openVideo();
    } else showPhoto();
    handler.removeCallbacks(captions);
    handler.post(captions);
  }

  void hold() {
    if (!playing || closed) return;
    playing = false;
    handler.removeCallbacks(nextPhoto);
    handler.removeCallbacks(captions);
    photo.animate().cancel();
    film.animate().cancel();
    if (player != null && ready && !seekPending) {
      try {
        player.pause();
      } catch (IllegalStateException ignored) {
        releasePlayer();
      }
    }
  }

  void pause(boolean keepFrame) {
    pause(keepFrame, false);
  }

  void pauseForHandoff() {
    pause(true, true);
  }

  private void pause(boolean keepFrame, boolean backgroundRelease) {
    if (keepFrame) saveFrame(true);
    boolean keepTexture = keepFrame && ready && film.isAvailable();
    playing = false;
    saver = false;
    handler.removeCallbacks(nextPhoto);
    handler.removeCallbacks(captions);
    photo.animate().cancel();
    film.animate().cancel();
    photo.setScaleX(1);
    photo.setScaleY(1);
    caption.setVisibility(GONE);
    if (keepFrame) showLastVideoFrame();
    if (player != null && ready) {
      if (!backgroundRelease) {
        try {
          savedPosition = player.getCurrentPosition();
        } catch (IllegalStateException ignored) {
        }
      }
      prefs
          .edit()
          .putString("scene_source", source)
          .putInt("scene_index", index)
          .putInt("scene_position", savedPosition)
          .putString("scene_uri", lastUri)
          .apply();
    }
    releasePlayer(backgroundRelease);
    film.setAlpha(keepTexture ? 1 : 0);
    if (!keepFrame) setVisibility(GONE);
  }

  private void showLastVideoFrame() {
    if (lastVideoFrame == null) return;
    photo.animate().cancel();
    photo.setImageBitmap(lastVideoFrame);
    photo.setAlpha(1);
  }

  private void sampleVideoFrame() {
    if (!playing || !ready || !rendered) return;
    Bitmap image = film.getBitmap(256, 144);
    if (image == null) return;
    lastVideoFrame = image;
    firstFramePending = false;
    saveFrame(false);
    if (!saver) listener.frame(image);
  }

  Bitmap returnFrame() {
    return lastVideoFrame;
  }

  private void saveFrame(boolean leaving) {
    if (lastVideoFrame == null || closed || !(source.equals("aerial") || source.equals("video")))
      return;
    if (!leaving && (frameWritePending || lastUri.equals(frameSavedUri))) return;
    frameSavedUri = lastUri;
    frameWritePending = true;
    SceneFrame saved = new SceneFrame(lastVideoFrame, source, lastUri, index, savedPosition);
    io.execute(
        () -> {
          try {
            saved.write(getContext());
          } finally {
            frameWritePending = false;
          }
        });
  }

  void next(int delta) {
    index = Math.max(0, index + delta);
    savedPosition = 0;
    failures = 0;
    if (source.equals("aerial")) {
      ensurePlaylist();
      queue.step(delta);
    }
    if (source.equals("video") || source.equals("aerial")) {
      showLastVideoFrame();
      releasePlayer();
      openVideo();
    } else showPhoto();
  }

  private void openVideo() {
    if (!playing || closed || player != null || !film.isAvailable()) return;
    String uri;
    try {
      if (source.equals("video")) {
        List<File> videos = library.files(true);
        if (videos.isEmpty()) {
          fallback();
          return;
        }
        uri = videos.get(index % videos.size()).toURI().toString();
        quality = "1080p";
      } else {
        if (aerials.length() == 0) return;
        ensurePlaylist();
        String selected = queue.current();
        if (selected == null) selected = queue.step(1);
        JSONObject entry = SceneLibrary.find(aerials, selected);
        if (entry == null) {
          fallback();
          emptyPlaylist = true;
          caption.setText("No enabled scenes · showing photos");
          return;
        }
        for (int i = 0; i < aerials.length(); i++)
          if (aerials.getJSONObject(i).getString("id").equals(selected)) {
            index = i;
            break;
          }
        quality = chooseQuality();
        uri =
            entry.getString(
                quality.equals("4k") ? "uhd" : quality.equals("dolby_vision") ? "dv" : "url");
      }
      if (!uri.equals(lastUri)) savedPosition = 0;
      lastUri = uri;
      final MediaPlayer current = new MediaPlayer();
      player = current;
      ready = false;
      rendered = false;
      seekPending = false;
      firstFramePending = true;
      current.setVolume(0, 0);
      surface = new Surface(film.getSurfaceTexture());
      current.setSurface(surface);
      current.setOnVideoSizeChangedListener(
          (p, w, h) -> {
            if (player != current) return;
            videoWidth = w;
            videoHeight = h;
            crop();
          });
      current.setOnPreparedListener(
          p -> {
            if (player != current) return;
            ready = true;
            if (savedPosition > 0) {
              seekPending = true;
              p.setOnSeekCompleteListener(
                  seek -> {
                    if (player != current) return;
                    seekPending = false;
                    if (playing) seek.start();
                  });
              p.seekTo(Math.min(savedPosition, Math.max(0, p.getDuration() - 1000)));
            } else if (playing) p.start();
            updateCaption();
          });
      current.setOnInfoListener(
          (p, what, extra) -> {
            if (player == current && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
              rendered = true;
              if (playing) {
                film.animate().alpha(1).setDuration(600).start();
                photo.animate().alpha(0).setDuration(600).start();
              } else {
                film.setAlpha(1);
                photo.setAlpha(0);
              }
            }
            return false;
          });
      current.setOnCompletionListener(
          p -> {
            if (playing && player == current) {
              sampleVideoFrame();
              next(1);
            }
          });
      current.setOnErrorListener(
          (p, what, extra) -> {
            if (player != current) return true;
            releasePlayer();
            if (++failures < 3) {
              if (failures > 1 || quality.equals("1080p")) {
                if (source.equals("aerial")) queue.step(1);
                else index++;
              }
              savedPosition = 0;
              handler.postDelayed(this::openVideo, 500);
            } else fallback();
            return true;
          });
      film.animate().cancel();
      showLastVideoFrame();
      film.setAlpha(0);
      current.setDataSource(getContext(), android.net.Uri.parse(uri));
      current.prepareAsync();
    } catch (Exception e) {
      releasePlayer();
      fallback();
    }
  }

  private void ensurePlaylist() {
    queue.configure(SceneLibrary.eligible(aerials, prefs), prefs.getBoolean("scene_shuffle", true));
    String selected = prefs.getString("selected_scene", "");
    if (!selected.equals(requestedScene)) {
      requestedScene = selected;
      if (!selected.isEmpty()) queue.select(selected);
    }
  }

  void preferencesChanged() {
    if (aerials.length() == 0) return;
    String before = queue.current();
    ensurePlaylist();
    if (playing && emptyPlaylist && !SceneLibrary.eligible(aerials, prefs).isEmpty()) {
      emptyPlaylist = false;
      handler.removeCallbacks(nextPhoto);
      source = "aerial";
      savedPosition = 0;
      openVideo();
      return;
    }
    if (playing && source.equals("aerial") && !java.util.Objects.equals(before, queue.current())) {
      savedPosition = 0;
      releasePlayer();
      openVideo();
    }
  }

  void selectFilm(String id) {
    requestedScene = "";
    prefs.edit().putString("selected_scene", id).apply();
    savedPosition = 0;
    ensurePlaylist();
    if (playing && source.equals("aerial")) {
      releasePlayer();
      openVideo();
    }
  }

  private String chooseQuality() {
    String selected = prefs.getString("aerial_quality", "1080p");
    if (!saver && selected.equals("dolby_vision")) selected = "4k";
    if (failures > 0 || selected.equals("1080p")) return "1080p";
    boolean hevc = false, dolby = false;
    try {
      for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())
        if (!codec.isEncoder())
          for (String mime : codec.getSupportedTypes()) {
            if (mime.equalsIgnoreCase("video/hevc")
                && codec
                    .getCapabilitiesForType(mime)
                    .getVideoCapabilities()
                    .areSizeAndRateSupported(3840, 2160, 30)) hevc = true;
            if (mime.equalsIgnoreCase("video/dolby-vision"))
              for (MediaCodecInfo.CodecProfileLevel profile :
                  codec.getCapabilitiesForType(mime).profileLevels)
                if (profile.profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn
                    && profile.level >= MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd30)
                  dolby = true;
          }
      Display display = getDisplay();
      boolean hdr = false;
      if (display != null)
        for (int type : display.getHdrCapabilities().getSupportedHdrTypes())
          if (type == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) hdr = true;
      if (saver
          && hevc
          && dolby
          && hdr
          && (selected.equals("auto") || selected.equals("dolby_vision"))) return "dolby_vision";
    } catch (Exception ignored) {
    }
    return hevc ? "4k" : "1080p";
  }

  private void fallback() {
    releasePlayer();
    source = "photos";
    showPhoto();
    caption.setText("Video unavailable · showing photos");
  }

  private void showPhoto() {
    if (!playing || closed) return;
    handler.removeCallbacks(nextPhoto);
    int request = ++epoch;
    int selected = index;
    io.execute(
        () -> {
          Bitmap bitmap = null;
          try {
            List<File> files = library.files(false);
            String path =
                files.isEmpty() ? null : files.get(Math.floorMod(selected, files.size())).getPath();
            if (path != null) {
              BitmapFactory.Options options = new BitmapFactory.Options();
              options.inSampleSize = 2;
              options.inPreferredConfig = Bitmap.Config.RGB_565;
              bitmap = BitmapFactory.decodeFile(path, options);
            } else
              try (InputStream stream =
                  getContext()
                      .getAssets()
                      .open(
                          "wallpapers/"
                              + new String[] {
                                    "misty", "dunes", "moon_lake", "golden_coast", "clouds"
                                  }
                                  [Math.floorMod(selected, 5)]
                              + ".jpg")) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                bitmap = BitmapFactory.decodeStream(stream, null, options);
              }
          } catch (Exception ignored) {
          }
          Bitmap image = bitmap;
          handler.post(
              () -> {
                if (request != epoch || closed || !playing) return;
                if (image != null) {
                  photo.animate().cancel();
                  previousPhoto.setImageDrawable(photo.getDrawable());
                  previousPhoto.setAlpha(1);
                  photo.setImageBitmap(image);
                  photo.setAlpha(0);
                  android.animation.ObjectAnimator fade =
                      android.animation.ObjectAnimator.ofFloat(photo, "alpha", 0, 1);
                  fade.setDuration(2000);
                  fade.start();
                  previousPhoto.animate().alpha(0).setDuration(2000).start();
                  listener.frame(image);
                  if (!prefs.getBoolean("reduced_motion", false)) {
                    photo.setScaleX(1.02f);
                    photo.setScaleY(1.02f);
                    photo
                        .animate()
                        .scaleX(1.10f)
                        .scaleY(1.10f)
                        .setDuration(20000)
                        .setInterpolator(new android.view.animation.LinearInterpolator())
                        .start();
                  }
                }
                caption.setText("");
                handler.postDelayed(nextPhoto, 18000);
              });
        });
  }

  private void updateCaption() {
    if (!saver || !source.equals("aerial") || aerials.length() == 0) return;
    try {
      JSONObject entry = aerials.getJSONObject(index % aerials.length());
      String text = entry.getString("label");
      JSONArray captions = entry.optJSONArray("captions");
      int seconds = ready && player != null ? player.getCurrentPosition() / 1000 : 0;
      if (captions != null)
        for (int i = 0; i < captions.length(); i++) {
          JSONArray c = captions.getJSONArray(i);
          if (c.getInt(0) <= seconds) text = c.optString(2, text);
        }
      caption.setText(text);
    } catch (Exception ignored) {
    }
  }

  private void crop() {
    if (videoWidth <= 0 || videoHeight <= 0 || getWidth() == 0 || getHeight() == 0) return;
    float scale = Math.max(getWidth() / (float) videoWidth, getHeight() / (float) videoHeight);
    Matrix matrix = new Matrix();
    matrix.setScale(
        videoWidth * scale / getWidth(),
        videoHeight * scale / getHeight(),
        getWidth() / 2f,
        getHeight() / 2f);
    film.setTransform(matrix);
  }

  private void releasePlayer() {
    releasePlayer(false);
  }

  private void releasePlayer(boolean background) {
    ready = false;
    rendered = false;
    seekPending = false;
    MediaPlayer previous = player;
    Surface previousSurface = surface;
    player = null;
    surface = null;
    if (previous == null && previousSurface == null) return;
    if (previous != null) {
      previous.setOnErrorListener(null);
      previous.setOnPreparedListener(null);
      previous.setOnInfoListener(null);
      previous.setOnCompletionListener(null);
      previous.setOnVideoSizeChangedListener(null);
    }
    Runnable dispose =
        () -> {
          try {
            if (previous != null) previous.release();
          } finally {
            if (previousSurface != null) previousSurface.release();
          }
        };
    if (background) io.execute(dispose);
    else dispose.run();
  }

  void trimForBackground() {
    previousPhoto.setImageDrawable(null);
    photo.animate().cancel();
    if (lastVideoFrame != null && (source.equals("aerial") || source.equals("video")))
      showLastVideoFrame();
    else photo.setImageDrawable(null);
  }

  void close() {
    closed = true;
    epoch++;
    pause(false);
    io.shutdown();
    handler.removeCallbacksAndMessages(null);
    photo.setImageDrawable(null);
    previousPhoto.setImageDrawable(null);
    lastVideoFrame = null;
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
    film.setAlpha(0);
    showLastVideoFrame();
    if (playing) openVideo();
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
    releasePlayer();
    return true;
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
    crop();
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    if (playing && ready && firstFramePending) sampleVideoFrame();
  }
}
