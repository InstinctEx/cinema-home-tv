package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** One moving focus surface per row, rather than a separate highlight on every item. */
final class GlideFocus extends FrameLayout {
  private final View selection;
  private final LinearLayout content;
  private final float posterScale;
  private View target;
  private boolean placed;

  GlideFocus(Context context, LinearLayout content, Drawable background) {
    this(context, content, background, 0f);
  }

  GlideFocus(Context context, LinearLayout content, Drawable background, float posterScale) {
    super(context);
    this.posterScale = posterScale;
    this.content = content;
    setClipChildren(false);
    setClipToPadding(false);
    selection = new View(context);
    selection.setBackground(background);
    selection.setAlpha(0f);
    selection.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    if (posterScale > 0) selection.setTranslationZ(8 * getResources().getDisplayMetrics().density);
    addView(selection, new LayoutParams(0, 0));
    addView(
        content, new LayoutParams(content.getOrientation() == LinearLayout.VERTICAL ? -1 : -2, -2));
  }

  void focus(View view, boolean focused, int duration) {
    if (focused) {
      target = view;
      move(duration);
    } else if (target == view) {
      target = null;
      selection.animate().cancel();
      selection.animate().alpha(0f).setDuration(duration).start();
    }
  }

  private void move(int duration) {
    if (target == null || target.getWidth() == 0 || target.getHeight() == 0) return;
    selection.animate().cancel();
    View artwork =
        posterScale > 0 && target instanceof android.view.ViewGroup
            ? ((android.view.ViewGroup) target).getChildAt(0)
            : target;
    float scale = posterScale > 0 ? posterScale : 1f;
    float pad = posterScale > 0 ? 2 * getResources().getDisplayMetrics().density : 0f;
    int width = Math.round(artwork.getWidth() * scale + pad * 2);
    int height = Math.round(artwork.getHeight() * scale + pad * 2);
    LayoutParams size = (LayoutParams) selection.getLayoutParams();
    if (size.width != width || size.height != height) {
      size.width = width;
      size.height = height;
      selection.setLayoutParams(size);
    }
    float x = content.getLeft() + target.getLeft(), y = content.getTop() + target.getTop();
    if (posterScale > 0) {
      x += target.getWidth() * (1 - scale) / 2 + artwork.getLeft() * scale - pad;
      y += target.getHeight() * (1 - scale) / 2 + artwork.getTop() * scale - pad;
    }
    if (!placed || duration == 0) {
      selection.setTranslationX(x);
      selection.setTranslationY(y);
      selection.setAlpha(1f);
      placed = true;
    } else
      selection
          .animate()
          .translationX(x)
          .translationY(y)
          .alpha(1f)
          .setDuration(duration)
          .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
          .start();
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (target != null && (!placed || changed)) move(0);
  }
}
