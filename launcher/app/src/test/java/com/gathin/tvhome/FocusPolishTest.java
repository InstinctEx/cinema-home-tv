package com.gathin.tvhome;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class FocusPolishTest {
  @Test
  public void posterOutlineTracksScaledArtworkAndDockKeepsWholeTileBounds() {
    Context context = RuntimeEnvironment.getApplication();
    for (float scale : new float[] {0f, 1.045f}) {
      LinearLayout row = new LinearLayout(context);
      LinearLayout card = new LinearLayout(context);
      card.setOrientation(LinearLayout.VERTICAL);
      View image = new View(context);
      card.addView(image, new LinearLayout.LayoutParams(200, 112));
      card.addView(new View(context), new LinearLayout.LayoutParams(200, 26));
      row.addView(card);
      GlideFocus glide = new GlideFocus(context, row, new ColorDrawable(Color.WHITE), scale);
      int spec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY);
      glide.measure(spec, spec);
      glide.layout(0, 0, 500, 500);
      glide.focus(card, true, 0);
      View ring = glide.getChildAt(0);
      if (scale == 0) {
        assertEquals(card.getWidth(), ring.getLayoutParams().width);
        assertEquals(card.getHeight(), ring.getLayoutParams().height);
      } else {
        float pad = 2 * context.getResources().getDisplayMetrics().density;
        assertEquals(
            Math.round(image.getHeight() * scale + 2 * pad), ring.getLayoutParams().height);
        assertEquals(card.getHeight() * (1 - scale) / 2 - pad, ring.getTranslationY(), .01);
        assertTrue(ring.getTranslationZ() > 0);
      }
    }
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void previousAndNextHaveMirroredCenteredBounds() {
    Bitmap[] frames = new Bitmap[2];
    int n = 0;
    for (String symbol : new String[] {"previous", "next"}) {
      Bitmap bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
      TransportDrawable drawable = new TransportDrawable(symbol);
      drawable.setBounds(0, 0, 48, 48);
      drawable.draw(new Canvas(bitmap));
      frames[n++] = bitmap;
      int left = 48, right = -1, top = 48, bottom = -1;
      for (int y = 0; y < 48; y++)
        for (int x = 0; x < 48; x++)
          if (Color.alpha(bitmap.getPixel(x, y)) > 128) {
            left = Math.min(left, x);
            right = Math.max(right, x);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
          }
      assertEquals(47, left + right);
      assertEquals(47, top + bottom);
    }
    for (int y = 0; y < 48; y++)
      for (int x = 0; x < 48; x++)
        assertEquals(
            Color.alpha(frames[0].getPixel(x, y)), Color.alpha(frames[1].getPixel(47 - x, y)), 1);
  }
}
