package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;
import java.util.Arrays;

/** Bounded local frame timings. No media names, network addresses or telemetry. */
final class FrameStats {
  private static final long[] frames = new long[600];
  private static int count;
  private static long start;

  static void attach(Window window) {
    start = SystemClock.uptimeMillis();
    window.addOnFrameMetricsAvailableListener(
        (w, metrics, dropped) -> {
          if (SystemClock.uptimeMillis() - start < 5000
              || metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return;
          synchronized (frames) {
            frames[count++ % frames.length] = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
          }
        },
        new Handler(Looper.getMainLooper()));
  }

  static String summary() {
    synchronized (frames) {
      int n = Math.min(count, frames.length);
      if (n == 0) return "Frame timings available after navigating Home";
      long[] copy = Arrays.copyOf(frames, n);
      Arrays.sort(copy);
      return String.format(
          java.util.Locale.ROOT,
          "%d frames · median %.1f ms · p90 %.1f ms · p99 %.1f ms",
          n,
          copy[n / 2] / 1e6,
          copy[(n - 1) * 90 / 100] / 1e6,
          copy[(n - 1) * 99 / 100] / 1e6);
    }
  }
}
