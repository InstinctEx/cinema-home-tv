package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

/** A tiny wallpaper snapshot gives soft glass on Android 11 without continuous GPU blur. */
final class FrostedBackground {
    static Drawable create(Context context, Drawable wallpaper, String style) {
        if (wallpaper == null || "solid".equals(style)) return new ColorDrawable(0xF2080A0E);
        Bitmap tiny = Bitmap.createBitmap(64, 36, Bitmap.Config.RGB_565);
        android.graphics.Rect previous = new android.graphics.Rect(wallpaper.getBounds());
        wallpaper.setBounds(0, 0, 64, 36);
        wallpaper.draw(new Canvas(tiny));
        wallpaper.setBounds(previous);
        BitmapDrawable soft = new BitmapDrawable(context.getResources(), tiny);
        soft.setFilterBitmap(true);
        return new LayerDrawable(new Drawable[]{soft,
                new ColorDrawable("soft".equals(style) ? 0x99080A0E : 0xC0080A0E)});
    }

    private FrostedBackground() {}
}
