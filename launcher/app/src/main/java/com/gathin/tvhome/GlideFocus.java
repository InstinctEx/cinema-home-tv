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
    private View target;
    private boolean placed;

    GlideFocus(Context context, LinearLayout content, Drawable background) {
        super(context);
        this.content = content;
        setClipChildren(false); setClipToPadding(false);
        selection = new View(context); selection.setBackground(background); selection.setAlpha(0f);
        selection.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(selection, new LayoutParams(0, 0));
        addView(content, new LayoutParams(content.getOrientation() == LinearLayout.VERTICAL ? -1 : -2, -2));
    }

    void focus(View view, boolean focused, int duration) {
        if (focused) { target = view; move(duration); }
        else if (target == view) {
            target = null;
            selection.animate().cancel(); selection.animate().alpha(0f).setDuration(duration).start();
        }
    }

    private void move(int duration) {
        if (target == null || target.getWidth() == 0 || target.getHeight() == 0) return;
        selection.animate().cancel();
        LayoutParams size = (LayoutParams) selection.getLayoutParams();
        if (size.width != target.getWidth() || size.height != target.getHeight()) {
            size.width = target.getWidth(); size.height = target.getHeight(); selection.setLayoutParams(size);
        }
        float x = content.getLeft() + target.getLeft(), y = content.getTop() + target.getTop();
        if (!placed || duration == 0) {
            selection.setTranslationX(x); selection.setTranslationY(y); selection.setAlpha(1f); placed = true;
        } else selection.animate().translationX(x).translationY(y).alpha(1f).setDuration(duration)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (target != null && (!placed || changed)) move(0);
    }
}
