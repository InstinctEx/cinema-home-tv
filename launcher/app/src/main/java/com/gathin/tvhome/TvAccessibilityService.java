package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Optional key shortcuts and Android's screen-lock action; does not read window contents. */
public class TvAccessibilityService extends AccessibilityService {
  private static TvAccessibilityService connected;

  @Override
  protected void onServiceConnected() {
    connected = this;
  }

  @Override
  public void onDestroy() {
    if (connected == this) connected = null;
    super.onDestroy();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {}

  @Override
  public void onInterrupt() {}

  static boolean globalDpadAvailable() {
    return connected != null && android.os.Build.VERSION.SDK_INT >= 33;
  }

  static int remoteActionId(String key) {
    if (key.equals("home")) return GLOBAL_ACTION_HOME;
    if (key.equals("back")) return GLOBAL_ACTION_BACK;
    if (android.os.Build.VERSION.SDK_INT < 33) return 0;
    switch (key) {
      case "up":
        return GLOBAL_ACTION_DPAD_UP;
      case "down":
        return GLOBAL_ACTION_DPAD_DOWN;
      case "left":
        return GLOBAL_ACTION_DPAD_LEFT;
      case "right":
        return GLOBAL_ACTION_DPAD_RIGHT;
      case "ok":
        return GLOBAL_ACTION_DPAD_CENTER;
      default:
        return 0;
    }
  }

  static boolean remoteAction(String key) {
    int action = remoteActionId(key);
    return connected != null && action != 0 && connected.performGlobalAction(action);
  }

  static boolean available() {
    return connected != null;
  }

  static boolean canStandby() {
    if (connected == null) return false;
    if (android.os.Build.VERSION.SDK_INT >= 30) {
      for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction action :
          connected.getSystemActions()) {
        if (action.getId() == GLOBAL_ACTION_LOCK_SCREEN) return true;
      }
      return false;
    }
    return android.os.Build.VERSION.SDK_INT >= 28;
  }

  static boolean standby() {
    return connected != null && connected.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
  }

  @Override
  protected boolean onKeyEvent(KeyEvent event) {
    SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
    int smarttube = prefs.getInt("smarttube_key", -1);
    int smarttubeScan = prefs.getInt("smarttube_scan", -1);
    boolean extras = prefs.getBoolean("remote_extra_shortcuts", false);
    boolean media = extras && event.getKeyCode() == KeyEvent.KEYCODE_PROG_BLUE;
    boolean youtube =
        smarttube >= 0 && event.getKeyCode() == smarttube
            || smarttubeScan >= 0 && event.getScanCode() == smarttubeScan;
    int nuvioScan = prefs.getInt("nuvio_scan", -1);
    boolean nuvio = nuvioScan >= 0 && event.getScanCode() == nuvioScan;
    boolean settings = extras && event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS;
    if (!media && !youtube && !settings && !nuvio) return false;
    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
      if (nuvio) {
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage("com.nuvio.tv");
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage("com.nuvio.tv");
        if (launch != null) {
          try {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
          } catch (android.content.ActivityNotFoundException | SecurityException ignored) {
          }
        } else
          android.widget.Toast.makeText(
                  this, "Nuvio is not installed", android.widget.Toast.LENGTH_SHORT)
              .show();
      } else if (youtube) MainActivity.launchSmartTube(this);
      else if (media) MediaApps.open(this, null, null);
      else
        startActivity(
            MainActivity.homeIntent(this)
                .putExtra("control_center", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    return true;
  }
}
