package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Transport symbols share a fixed center, independent of text font metrics. */
final class TransportDrawable extends Drawable {
  private final String symbol;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  TransportDrawable(String symbol) {
    this.symbol = symbol;
    paint.setColor(0xFFEAEAE2);
    paint.setStrokeWidth(2);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
  }

  @Override
  public void draw(Canvas canvas) {
    int save = canvas.save();
    canvas.translate(getBounds().left, getBounds().top);
    canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
    paint.setStyle(Paint.Style.FILL);
    Path path = new Path();
    if (symbol.equals("pause")) {
      canvas.drawRect(7, 6, 10, 18, paint);
      canvas.drawRect(14, 6, 17, 18, paint);
    } else if (symbol.equals("play")) {
      path.moveTo(9, 6);
      path.lineTo(18, 12);
      path.lineTo(9, 18);
      path.close();
      canvas.drawPath(path, paint);
    } else {
      paint.setStyle(Paint.Style.STROKE);
      boolean previous = symbol.equals("previous");
      path.moveTo(previous ? 15 : 9, 5);
      path.lineTo(previous ? 9 : 15, 12);
      path.lineTo(previous ? 15 : 9, 19);
      canvas.drawPath(path, paint);
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
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public int getIntrinsicWidth() {
    return 24;
  }

  @Override
  public int getIntrinsicHeight() {
    return 24;
  }
}
