package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small cream glyphs match the launcher's own marks. */
final class SettingsIconDrawable extends Drawable {
  private final String kind;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  SettingsIconDrawable(String kind) {
    this.kind = kind;
  }

  @Override
  public void draw(Canvas canvas) {
    int save = canvas.save();
    float size = Math.min(getBounds().width(), getBounds().height());
    canvas.translate(getBounds().centerX() - size / 2, getBounds().centerY() - size / 2);
    canvas.scale(size / 100, size / 100);
    paint.setColor(0xFFEAEAE2);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(5);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    if (kind.equals("appearance")) {
      canvas.drawRoundRect(12, 15, 88, 85, 10, 10, paint);
      canvas.drawCircle(65, 35, 7, paint);
      Path p = new Path();
      p.moveTo(15, 72);
      p.lineTo(37, 47);
      p.lineTo(55, 65);
      p.lineTo(66, 55);
      p.lineTo(87, 77);
      canvas.drawPath(p, paint);
    } else if (kind.equals("scenes")) {
      canvas.drawRoundRect(10, 18, 90, 82, 12, 12, paint);
      Path p = new Path();
      p.moveTo(42, 35);
      p.lineTo(65, 50);
      p.lineTo(42, 65);
      p.close();
      canvas.drawPath(p, paint);
    } else if (kind.equals("apps")) {
      for (int x : new int[] {15, 57})
        for (int y : new int[] {15, 57}) canvas.drawRoundRect(x, y, x + 28, y + 28, 6, 6, paint);
    } else if (kind.equals("connections")) {
      canvas.drawRoundRect(30, 8, 70, 92, 9, 9, paint);
      canvas.drawLine(44, 80, 56, 80, paint);
      canvas.drawArc(4, 25, 96, 80, 210, 120, false, paint);
    } else if (kind.equals("sound")) {
      Path p = new Path();
      p.moveTo(12, 40);
      p.lineTo(30, 40);
      p.lineTo(50, 23);
      p.lineTo(50, 77);
      p.lineTo(30, 60);
      p.lineTo(12, 60);
      p.close();
      canvas.drawPath(p, paint);
      canvas.drawArc(42, 28, 80, 72, -65, 130, false, paint);
      canvas.drawArc(38, 13, 96, 87, -65, 130, false, paint);
    } else {
      canvas.drawCircle(50, 50, 20, paint);
      for (int i = 0; i < 8; i++) {
        canvas.save();
        canvas.rotate(i * 45, 50, 50);
        canvas.drawLine(50, 10, 50, 23, paint);
        canvas.restore();
      }
    }
    canvas.restoreToCount(save);
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
