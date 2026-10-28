package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import java.util.Random;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow. Every minute
 * the clock drifts to a new spot and the glow shifts colour, so nothing burns in. No network, no
 * images: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

  private static final long MOVE_MS = 60_000L;

  /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
  private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Random random = new Random();
  private View content;
  private GradientDrawable glow;
  private int colorIndex;
  private int glowColor;
  private boolean dreaming;
  private SceneView scene;
  private String sceneMode;
  private android.content.SharedPreferences preferences;
  private ValueAnimator glowAnimation;

  private final Runnable tick =
      new Runnable() {
        @Override
        public void run() {
          if (!dreaming) return;
          moveClock();
          shiftGlow();
          handler.postDelayed(this, MOVE_MS);
        }
      };

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    setInteractive(false); // any key wakes the TV back to where it was
    setFullscreen(true);
    setScreenBright(false);
    setContentView(R.layout.dream_clock);

    content = findViewById(R.id.dreamContent);
    View ambient = findViewById(R.id.dreamAmbient);
    glow = new GradientDrawable();
    glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
    ambient.setBackground(glow);
    ambient.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> glow.setGradientRadius((r - l) * 0.55f));
    colorIndex = random.nextInt(PALETTE.length);
    setGlow(PALETTE[colorIndex]);
    preferences = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
    sceneMode = preferences.getString("screensaver_mode", "system");
    if (java.util.Arrays.asList("aerial", "photos", "video").contains(sceneMode)) {
      scene = new SceneView(this, preferences, new MediaLibrary(this), bitmap -> {});
      ((android.widget.FrameLayout) content.getParent())
          .addView(scene, 0, new android.widget.FrameLayout.LayoutParams(-1, -1));
      setInteractive(true);
      if (!preferences.getBoolean("screensaver_clock", true)) content.setVisibility(View.GONE);
    }
  }

  @Override
  public void onDreamingStarted() {
    super.onDreamingStarted();
    dreaming = true;
    if (scene != null) scene.start(sceneMode, true);
    handler.removeCallbacks(tick);
    content.setAlpha(0f);
    content.post(
        () -> {
          if (!dreaming) return;
          placeClock();
          content.animate().alpha(1f).setDuration(1200).start();
        });
    handler.postDelayed(tick, MOVE_MS);
  }

  @Override
  public void onDreamingStopped() {
    stopAnimations();
    super.onDreamingStopped();
  }

  @Override
  public void onDetachedFromWindow() {
    stopAnimations();
    if (scene != null) {
      scene.close();
      scene = null;
    }
    super.onDetachedFromWindow();
  }

  @Override
  public boolean dispatchKeyEvent(android.view.KeyEvent event) {
    if (scene == null) return super.dispatchKeyEvent(event);
    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
      if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_LEFT
          || event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        scene.next(event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
      else wakeUp();
    }
    return true;
  }

  private void stopAnimations() {
    if (scene != null) scene.pause(false);
    dreaming = false;
    handler.removeCallbacks(tick);
    if (content != null) content.animate().withEndAction(null).cancel();
    if (glowAnimation != null) {
      glowAnimation.cancel();
      glowAnimation = null;
    }
  }

  private void moveClock() {
    content
        .animate()
        .alpha(0f)
        .setDuration(900)
        .withEndAction(
            () -> {
              if (!dreaming) return;
              placeClock();
              content.animate().alpha(1f).setDuration(1200).start();
            })
        .start();
  }

  /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
  private void placeClock() {
    View parent = (View) content.getParent();
    int maxX = Math.max(0, parent.getWidth() - content.getWidth());
    int maxY = Math.max(0, parent.getHeight() - content.getHeight());
    float x = maxX * (0.1f + 0.8f * random.nextFloat());
    float y = maxY * (0.1f + 0.8f * random.nextFloat());
    content.setX(x);
    content.setY(y);
    if (parent.getWidth() > 0 && parent.getHeight() > 0) {
      glow.setGradientCenter(
          (x + content.getWidth() / 2f) / parent.getWidth(),
          (y + content.getHeight() / 2f) / parent.getHeight());
    }
  }

  private void shiftGlow() {
    int from = glowColor;
    colorIndex = (colorIndex + 1) % PALETTE.length;
    if (glowAnimation != null) glowAnimation.cancel();
    ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
    glowAnimation = anim;
    anim.setDuration(8000);
    anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
    anim.start();
  }

  private void setGlow(int color) {
    glowColor = color;
    int c = color & 0x00FFFFFF;
    glow.setColors(new int[] {0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
  }
}
