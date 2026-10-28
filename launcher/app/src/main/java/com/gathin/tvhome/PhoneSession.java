package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.os.*;

/** Keeps an explicitly paired session alive across apps, with a visible, revocable notification. */
public final class PhoneSession extends Service {
  private static PhoneServer session;
  private final Handler handler = new Handler(Looper.getMainLooper());

  static PhoneServer current() {
    return session != null && !session.isClosed() ? session : null;
  }

  static void keep(Context context, PhoneServer server) {
    if (session != null && session != server) session.close();
    session = server;
    context.startForegroundService(new Intent(context, PhoneSession.class));
  }

  static void end(Context context) {
    if (session != null) session.close();
    session = null;
    context.stopService(new Intent(context, PhoneSession.class));
  }

  private final Runnable expiry =
      new Runnable() {
        public void run() {
          if (current() == null) {
            stopSelf();
            return;
          }
          handler.postDelayed(this, 3000);
        }
      };

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "disconnect".equals(intent.getAction())) {
      end(this);
      return START_NOT_STICKY;
    }
    NotificationManager notifications = getSystemService(NotificationManager.class);
    notifications.createNotificationChannel(
        new NotificationChannel(
            "cinema-phone", "Paired phone", NotificationManager.IMPORTANCE_LOW));
    PendingIntent disconnect =
        PendingIntent.getService(
            this,
            0,
            new Intent(this, PhoneSession.class).setAction("disconnect"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification notification =
        new Notification.Builder(this, "cinema-phone")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Cinema Home · phone connected")
            .setContentText("Local controls · pairing expires after 15 minutes")
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Disconnect", disconnect).build())
            .build();
    startForeground(24, notification);
    handler.removeCallbacks(expiry);
    handler.post(expiry);
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (session != null) session.close();
    session = null;
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
