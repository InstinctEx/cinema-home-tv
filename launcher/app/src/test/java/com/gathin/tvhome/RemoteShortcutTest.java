package com.gathin.tvhome;

import static org.junit.Assert.*;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.view.KeyEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class RemoteShortcutTest {
  @Test
  public void capturedPrimeScanLaunchesNuvioOnceAndLeavesOtherKeysAlone() {
    TvAccessibilityService service =
        Robolectric.buildService(TvAccessibilityService.class).create().get();
    service
        .getSharedPreferences("tvhome_prefs", 0)
        .edit()
        .clear()
        .putInt("nuvio_scan", 504)
        .commit();
    ResolveInfo info = new ResolveInfo();
    info.activityInfo = new ActivityInfo();
    info.activityInfo.packageName = "com.nuvio.tv";
    info.activityInfo.name = "com.nuvio.tv.MainActivity";
    info.activityInfo.applicationInfo = new ApplicationInfo();
    info.activityInfo.applicationInfo.packageName = "com.nuvio.tv";
    Shadows.shadowOf(service.getPackageManager())
        .addResolveInfoForIntent(
            new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setPackage("com.nuvio.tv"),
            info);
    assertTrue(service.onKeyEvent(new KeyEvent(1, 1, KeyEvent.ACTION_DOWN, 0, 0, 0, 2, 504)));
    Intent launch = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
    assertNotNull(launch);
    assertEquals("com.nuvio.tv", launch.getComponent().getPackageName());
    assertTrue(service.onKeyEvent(new KeyEvent(1, 2, KeyEvent.ACTION_DOWN, 0, 1, 0, 2, 504)));
    assertTrue(service.onKeyEvent(new KeyEvent(1, 3, KeyEvent.ACTION_UP, 0, 0, 0, 2, 504)));
    assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
    assertFalse(service.onKeyEvent(new KeyEvent(1, 4, KeyEvent.ACTION_DOWN, 0, 0, 0, 2, 505)));
  }
}
