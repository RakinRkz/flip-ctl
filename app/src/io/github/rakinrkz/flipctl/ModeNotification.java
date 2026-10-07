package io.github.rakinrkz.flipctl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Ongoing notification while the main screen isn't the only one on. It belongs to
 * CoverButtonService (the floating button on the cover screen), which keeps it posted.
 */
final class ModeNotification {
    static final int ID = 1;
    private static final String CHANNEL = "mode";

    private ModeNotification() {}

    /** Starts or stops the cover button service, which owns this notification. */
    static void update(Context context, String mode) {
        Intent service = new Intent(context, CoverButtonService.class);
        if (mode.equals("main")) {
            context.stopService(service);
            context.getSystemService(NotificationManager.class).cancel(ID);
            return;
        }
        try {
            context.startForegroundService(service.putExtra(CoverButtonService.EXTRA_MODE, mode));
        } catch (RuntimeException e) {
            // Background start not allowed (e.g. from a notification tap): keep the notification only.
            context.getSystemService(NotificationManager.class).notify(ID, build(context, mode));
        }
    }

    static Notification build(Context context, String mode) {
        // Default importance so it is listed on the cover screen, but without sound or vibration.
        NotificationChannel channel =
                new NotificationChannel(CHANNEL, "Screen mode", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        channel.enableVibration(false);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);

        boolean cover = mode.equals("cover");
        return new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_screens)
                .setContentTitle(cover ? "Cover screen only" : "Both screens on")
                .setContentText("Tap the blue Screens button on the cover screen to switch")
                .setContentIntent(switchIntent(context, "main"))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(action(context, "main", "Main screen"))
                .addAction(cover ? action(context, "dual", "Both screens") : action(context, "cover", "Cover screen"))
                .build();
    }

    private static Notification.Action action(Context context, String mode, String label) {
        return new Notification.Action.Builder(null, label, switchIntent(context, mode)).build();
    }

    private static PendingIntent switchIntent(Context context, String mode) {
        Intent intent = new Intent(context, SwitchReceiver.class).putExtra(SwitchReceiver.EXTRA_MODE, mode);
        return PendingIntent.getBroadcast(
                context, mode.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
