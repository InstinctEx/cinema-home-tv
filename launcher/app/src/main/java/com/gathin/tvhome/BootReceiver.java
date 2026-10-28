package com.gathin.tvhome;

import android.content.*;

/** Boot fallback only when Android resolves Cinema Home as the selected HOME application. */
public final class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
    android.content.pm.ResolveInfo home =
        context
            .getPackageManager()
            .resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
    if (home == null
        || home.activityInfo == null
        || !context.getPackageName().equals(home.activityInfo.packageName)) return;
    try {
      context.startActivity(
          MainActivity.homeIntent(context)
              .putExtra("cinema_boot", true)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (RuntimeException ignored) {
    }
  }
}
