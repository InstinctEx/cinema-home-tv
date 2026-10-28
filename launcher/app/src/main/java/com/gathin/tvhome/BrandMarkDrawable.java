package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Small vector brand marks keep app surfaces consistent without loading large TV banners. */
final class BrandMarkDrawable extends Drawable {
  private final String brand;
  private final boolean monochrome;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Typeface font;

  static String brand(String pkg) {
    if (pkg.startsWith("com.nuvio.")) return "nuvio";
    if (pkg.equals("com.netflix.ninja")) return "netflix";
    if (pkg.equals("com.android.vending")) return "playstore";
    if (pkg.equals("com.spotify.tv.android")) return "spotify";
    if (pkg.contains("smarttube")) return "smarttube";
    if (pkg.startsWith("input:") || pkg.equals("com.gathin.tvhome.inputs")) return "inputs";
    return null;
  }

  BrandMarkDrawable(String brand, Typeface font) {
    this(brand, font, false);
  }

  BrandMarkDrawable(String brand, Typeface font, boolean monochrome) {
    this.brand = brand;
    this.font = font;
    this.monochrome = monochrome;
  }

  private Path polygon(float... points) {
    Path path = new Path();
    path.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    path.close();
    return path;
  }

  @Override
  public void draw(Canvas canvas) {
    int save = canvas.save();
    float size = Math.min(getBounds().width(), getBounds().height());
    canvas.translate(getBounds().centerX() - size / 2, getBounds().centerY() - size / 2);
    canvas.scale(size / 100f, size / 100f);
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(null);
    if (monochrome) {
      drawMonochrome(canvas);
      canvas.restoreToCount(save);
      return;
    }
    if ("playstore".equals(brand)) {
      paint.setColor(0xFF52C774);
      canvas.drawPath(polygon(16, 12, 59, 50, 16, 88), paint);
      paint.setColor(0xFF44B4F1);
      canvas.drawPath(polygon(16, 12, 80, 42, 59, 50), paint);
      paint.setColor(0xFFF4C44E);
      canvas.drawPath(polygon(59, 50, 80, 42, 94, 50, 80, 58), paint);
      paint.setColor(0xFFE97175);
      canvas.drawPath(polygon(16, 88, 59, 50, 80, 58), paint);
    } else if ("netflix".equals(brand)) {
      paint.setColor(0xFFB20710);
      canvas.drawRect(24, 8, 42, 92, paint);
      canvas.drawRect(59, 8, 77, 92, paint);
      paint.setColor(0xFFE50914);
      canvas.drawPath(polygon(24, 8, 44, 8, 77, 92, 57, 92), paint);
    } else if ("spotify".equals(brand)) {
      paint.setColor(0xFF1ED760);
      canvas.drawCircle(50, 50, 43, paint);
      paint.setColor(0xFF07150D);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeCap(Paint.Cap.ROUND);
      for (int line = 0; line < 3; line++) {
        paint.setStrokeWidth(7 - line);
        Path curve = new Path();
        curve.moveTo(24 + line * 3, 37 + line * 14);
        curve.cubicTo(41, 30 + line * 14, 63, 34 + line * 13, 76 - line * 3, 43 + line * 13);
        canvas.drawPath(curve, paint);
      }
    } else if ("nuvio".equals(brand)) {
      paint.setShader(
          new LinearGradient(
              18,
              14,
              76,
              84,
              new int[] {0xFF50D4E6, 0xFF8972FF, 0xFFB52BE2},
              null,
              Shader.TileMode.CLAMP));
      Path triangle = new Path();
      triangle.moveTo(22, 10);
      triangle.quadTo(12, 4, 12, 17);
      triangle.lineTo(12, 83);
      triangle.quadTo(12, 96, 23, 89);
      triangle.lineTo(84, 57);
      triangle.quadTo(97, 50, 84, 43);
      triangle.close();
      canvas.drawPath(triangle, paint);
      paint.setShader(null);
      paint.setColor(0xFF152032);
      canvas.drawPath(polygon(33, 30, 33, 71, 70, 50), paint);
      paint.setColor(0xFFF5F8FC);
      canvas.drawPath(polygon(42, 40, 42, 61, 60, 50), paint);
    } else if ("smarttube".equals(brand)) {
      paint.setColor(0xFFE82A2D);
      canvas.drawRoundRect(4, 18, 96, 82, 13, 13, paint);
      paint.setColor(0xFFFFFFFF);
      paint.setTypeface(font);
      paint.setTextSize(66);
      paint.setTextAlign(Paint.Align.CENTER);
      canvas.drawText("S", 50, 73, paint);
    } else {
      paint.setColor(0xFFF4F7FA);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(5);
      paint.setStrokeJoin(Paint.Join.ROUND);
      canvas.drawPath(polygon(12, 25, 88, 25, 88, 60, 70, 78, 30, 78, 12, 60), paint);
      canvas.drawLine(29, 45, 71, 45, paint);
    }
    paint.setStyle(Paint.Style.FILL);
    canvas.restoreToCount(save);
  }

  private void drawMonochrome(Canvas canvas) {
    paint.setShader(null);
    paint.setColor(0xFFEAEAE2);
    paint.setStyle(Paint.Style.FILL);
    Path logo = new Path();
    if (java.util.Arrays.asList("appletv", "playstation", "switch", "xbox", "computer", "speaker")
        .contains(brand)) {
      drawDevice(canvas);
      return;
    }
    if ("nuvio".equals(brand)) {
      logo.moveTo(22, 10);
      logo.quadTo(12, 4, 12, 17);
      logo.lineTo(12, 83);
      logo.quadTo(12, 96, 23, 89);
      logo.lineTo(84, 57);
      logo.quadTo(97, 50, 84, 43);
      logo.close();
      logo.addPath(polygon(35, 30, 35, 71, 70, 50));
      logo.setFillType(Path.FillType.EVEN_ODD);
    } else if ("smarttube".equals(brand)) {
      logo.addRoundRect(4, 18, 96, 82, 17, 17, Path.Direction.CW);
      logo.addPath(polygon(41, 34, 41, 67, 68, 50));
      logo.setFillType(Path.FillType.EVEN_ODD);
    } else if ("spotify".equals(brand)) {
      logo.addCircle(50, 50, 43, Path.Direction.CW);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeCap(Paint.Cap.ROUND);
      for (int line = 0; line < 3; line++) {
        paint.setStrokeWidth(7 - line);
        Path curve = new Path(), cutout = new Path();
        curve.moveTo(24 + line * 3, 37 + line * 14);
        curve.cubicTo(41, 30 + line * 14, 63, 34 + line * 13, 76 - line * 3, 43 + line * 13);
        paint.getFillPath(curve, cutout);
        logo.op(cutout, Path.Op.DIFFERENCE);
      }
      paint.setStyle(Paint.Style.FILL);
    } else if ("netflix".equals(brand)) {
      logo.addRect(24, 8, 42, 92, Path.Direction.CW);
      logo.addRect(59, 8, 77, 92, Path.Direction.CW);
      logo.addPath(polygon(24, 8, 44, 8, 77, 92, 57, 92));
    } else if ("playstore".equals(brand)) {
      logo = polygon(18, 10, 92, 50, 18, 90);
      Path seams = new Path(), cutout = new Path();
      seams.moveTo(18, 10);
      seams.lineTo(64, 50);
      seams.lineTo(18, 90);
      seams.moveTo(18, 50);
      seams.lineTo(92, 50);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(2);
      paint.getFillPath(seams, cutout);
      logo.op(cutout, Path.Op.DIFFERENCE);
      paint.setStyle(Paint.Style.FILL);
    } else {
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(4);
      paint.setStrokeJoin(Paint.Join.ROUND);
      canvas.drawPath(polygon(12, 25, 88, 25, 88, 60, 70, 78, 30, 78, 12, 60), paint);
      canvas.drawLine(29, 45, 71, 45, paint);
      paint.setStyle(Paint.Style.FILL);
      return;
    }
    canvas.drawPath(logo, paint);
  }

  private void drawDevice(Canvas canvas) {
    paint.setColor(0xFFEAEAE2);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(5);
    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setStrokeCap(Paint.Cap.ROUND);
    if ("computer".equals(brand)) {
      canvas.drawRoundRect(10, 15, 90, 70, 5, 5, paint);
      canvas.drawLine(50, 70, 50, 85, paint);
      canvas.drawLine(30, 85, 70, 85, paint);
    } else if ("speaker".equals(brand)) {
      canvas.drawRoundRect(25, 8, 75, 92, 8, 8, paint);
      canvas.drawCircle(50, 65, 15, paint);
      canvas.drawCircle(50, 28, 6, paint);
    } else if ("switch".equals(brand)) {
      canvas.drawRoundRect(10, 15, 90, 85, 18, 18, paint);
      canvas.drawLine(38, 15, 38, 85, paint);
      canvas.drawCircle(24, 35, 5, paint);
      canvas.drawCircle(68, 62, 5, paint);
    } else if ("xbox".equals(brand)) {
      canvas.drawCircle(50, 50, 40, paint);
      canvas.drawLine(26, 22, 76, 78, paint);
      canvas.drawLine(74, 22, 24, 78, paint);
    } else {
      canvas.drawRoundRect(10, 15, 90, 85, 15, 15, paint);
      paint.setStyle(Paint.Style.FILL);
      paint.setTypeface(font);
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTextSize("appletv".equals(brand) ? 42 : 35);
      canvas.drawText("appletv".equals(brand) ? "tv" : "PS", 50, 64, paint);
    }
    paint.setStyle(Paint.Style.FILL);
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
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
