package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A cached, softly blurred wallpaper texture with a restrained glass edge. */
final class FrostedPanelDrawable extends Drawable {
  private final View panel, surface;
  private static java.lang.ref.WeakReference<Bitmap> previousSource =
      new java.lang.ref.WeakReference<>(null);
  private static java.lang.ref.WeakReference<Bitmap> previousTexture =
      new java.lang.ref.WeakReference<>(null);
  private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG),
      rim = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint picture = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private Bitmap tiny;
  private BitmapShader shader;
  private final Paint previousPicture = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private long blendStarted;
  private int opacity = 255;
  private final Matrix matrix = new Matrix();
  private final int[] origin = new int[2], location = new int[2];
  private final RectF box = new RectF();
  private final float radius;
  private final boolean pinnedToHome;

  FrostedPanelDrawable(View panel, View surface, Bitmap background, String style) {
    this.panel = panel;
    this.surface = surface;
    pinnedToHome = "quiet".equals(style);
    radius = 24 * panel.getResources().getDisplayMetrics().density;
    if (pinnedToHome) tiny = quietTexture(background);
    else {
      tiny = Bitmap.createBitmap(64, 36, Bitmap.Config.RGB_565);
      Canvas softened = new Canvas(tiny);
      softened.drawBitmap(background, null, new Rect(0, 0, 64, 36), picture);
      softened.drawColor(
          "solid".equals(style) ? 0xF2131D29 : "soft".equals(style) ? 0xC0151F2B : 0xE0131D29);
    }
    rim.setStyle(Paint.Style.STROKE);
    rim.setStrokeWidth(panel.getResources().getDisplayMetrics().density);
    shader = new BitmapShader(tiny, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    picture.setShader(shader);
  }

  void updateFrame(Bitmap frame, boolean animate) {
    previousPicture.setShader(animate ? shader : null);
    tiny = quietTexture(frame);
    shader = new BitmapShader(tiny, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    picture.setShader(shader);
    blendStarted = android.os.SystemClock.uptimeMillis();
    invalidateSelf();
  }

  private static Bitmap quietTexture(Bitmap background) {
    Bitmap cached = previousTexture.get();
    if (previousSource.get() == background && cached != null) return cached;
    int width = 256, height = 144;
    Bitmap texture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Paint sample = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    Canvas canvas = new Canvas(texture);
    canvas.drawColor(0xFF0C171C);
    canvas.drawBitmap(background, null, new Rect(0, 0, width, height), sample);
    int[] pixels = new int[width * height], scratch = new int[pixels.length];
    texture.getPixels(pixels, 0, width, 0, 0, width, height);
    for (int pass = 0; pass < 3; pass++) {
      blur(pixels, scratch, width, height, true);
      blur(scratch, pixels, width, height, false);
    }
    texture.setPixels(pixels, 0, width, 0, 0, width, height);
    canvas.drawColor(0x9A0C171C);
    canvas.drawColor(0x07FFFFFF);
    previousSource = new java.lang.ref.WeakReference<>(background);
    previousTexture = new java.lang.ref.WeakReference<>(texture);
    return texture;
  }

  private static void blur(int[] input, int[] output, int width, int height, boolean horizontal) {
    int radius = 6, count = radius * 2 + 1;
    int lines = horizontal ? height : width,
        length = horizontal ? width : height,
        step = horizontal ? 1 : width;
    for (int line = 0; line < lines; line++) {
      int base = horizontal ? line * width : line, red = 0, green = 0, blue = 0;
      for (int k = -radius; k <= radius; k++) {
        int pixel = input[base + Math.max(0, Math.min(length - 1, k)) * step];
        red += (pixel >> 16) & 255;
        green += (pixel >> 8) & 255;
        blue += pixel & 255;
      }
      for (int i = 0; i < length; i++) {
        output[base + i * step] =
            0xFF000000 | (red / count << 16) | (green / count << 8) | blue / count;
        int leaving = input[base + Math.max(0, i - radius) * step];
        int entering = input[base + Math.min(length - 1, i + radius + 1) * step];
        red += ((entering >> 16) & 255) - ((leaving >> 16) & 255);
        green += ((entering >> 8) & 255) - ((leaving >> 8) & 255);
        blue += (entering & 255) - (leaving & 255);
      }
    }
  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    if (!pinnedToHome) return;
    sheen.setShader(
        new LinearGradient(
            0,
            bounds.top,
            0,
            Math.max(bounds.top + 1, bounds.bottom),
            0x0BFFFFFF,
            0x01FFFFFF,
            Shader.TileMode.CLAMP));
    rim.setShader(
        new LinearGradient(
            0,
            bounds.top,
            0,
            Math.max(bounds.top + 1, bounds.bottom),
            0x2CFFFFFF,
            0x0CFFFFFF,
            Shader.TileMode.CLAMP));
  }

  @Override
  public void draw(Canvas canvas) {
    if (opacity == 0) return;
    panel.getLocationInWindow(location);
    surface.getLocationInWindow(origin);
    if (pinnedToHome) {
      View child = panel;
      while (child.getParent() instanceof View && child != surface) {
        View parent = (View) child.getParent();
        location[0] += parent.getScrollX();
        location[1] += parent.getScrollY();
        child = parent;
      }
    }
    matrix.setScale(
        surface.getWidth() / (float) tiny.getWidth(),
        surface.getHeight() / (float) tiny.getHeight());
    matrix.postTranslate(origin[0] - location[0], origin[1] - location[1]);
    shader.setLocalMatrix(matrix);
    box.set(getBounds());
    int layer = opacity < 255 ? canvas.saveLayerAlpha(box, opacity) : -1;
    float blend = Math.min(1f, (android.os.SystemClock.uptimeMillis() - blendStarted) / 1000f);
    if (previousPicture.getShader() != null && blend < 1f) {
      previousPicture.getShader().setLocalMatrix(matrix);
      // Keep the old texture opaque beneath the new one; fading both exposes the video and flashes.
      canvas.drawRoundRect(box, radius, radius, previousPicture);
      picture.setAlpha(Math.round(255 * blend));
      invalidateSelf();
    } else previousPicture.setShader(null);
    canvas.drawRoundRect(box, radius, radius, picture);
    picture.setAlpha(255);
    if (pinnedToHome) {
      canvas.drawRoundRect(box, radius, radius, sheen);
      float inset = rim.getStrokeWidth() / 2;
      box.inset(inset, inset);
      canvas.drawRoundRect(box, radius - inset, radius - inset, rim);
    }
    if (layer != -1) canvas.restoreToCount(layer);
  }

  @Override
  public void setAlpha(int alpha) {
    if (opacity != alpha) {
      opacity = alpha;
      invalidateSelf();
    }
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    picture.setColorFilter(filter);
    previousPicture.setColorFilter(filter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public void getOutline(android.graphics.Outline outline) {
    outline.setRoundRect(getBounds(), radius);
    outline.setAlpha(.65f);
  }
}
