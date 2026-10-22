package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Optional key shortcuts and Android's screen-lock action; does not read window contents. */
public class TvAccessibilityService extends AccessibilityService {
    private static TvAccessibilityService connected;
    @Override protected void onServiceConnected() { connected = this; }
    @Override public void onDestroy() { if (connected == this) connected = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    static boolean available() { return connected != null; }
    static boolean canStandby() {
        if (connected == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction action : connected.getSystemActions()) {
                if (action.getId() == GLOBAL_ACTION_LOCK_SCREEN) return true;
            }
            return false;
        }
        return android.os.Build.VERSION.SDK_INT >= 28;
    }
    static boolean standby() {
        return connected != null && connected.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        int smarttube = prefs.getInt("smarttube_key", -1);
        int smarttubeScan = prefs.getInt("smarttube_scan", -1);
        boolean extras = prefs.getBoolean("remote_extra_shortcuts", false);
        boolean media = extras && event.getKeyCode() == KeyEvent.KEYCODE_PROG_BLUE;
        boolean youtube = smarttube >= 0 && event.getKeyCode() == smarttube
                || smarttubeScan >= 0 && event.getScanCode() == smarttubeScan;
        boolean settings = extras && event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS;
        if (!media && !youtube && !settings) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (youtube) MainActivity.launchSmartTube(this);
            else if (media) MediaApps.open(this, null, null);
            else startActivity(new Intent(this, MainActivity.class)
                    .putExtra("control_center", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }
}
