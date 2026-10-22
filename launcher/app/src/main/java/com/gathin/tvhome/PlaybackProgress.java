package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.ProgressBar;

/** A genuinely thin track without the platform progress drawable's minimum-height insets. */
final class PlaybackProgress extends ProgressBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    PlaybackProgress(Context context) { super(context, null, android.R.attr.progressBarStyleHorizontal); setIndeterminate(false); }
    @Override protected synchronized void onDraw(Canvas canvas) {
        float radius = getHeight() / 2f;
        paint.setColor(0x33FFFFFF); canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
        float fraction = getMax() <= 0 ? 0 : Math.max(0f, Math.min(1f, getProgress() / (float) getMax()));
        paint.setColor(0xFF8EDFE7); canvas.drawRoundRect(0, 0, getWidth() * fraction, getHeight(), radius, radius, paint);
    }
}
