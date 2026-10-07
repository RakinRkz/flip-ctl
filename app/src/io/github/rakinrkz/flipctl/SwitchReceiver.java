package io.github.rakinrkz.flipctl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the notification's mode buttons. */
public class SwitchReceiver extends BroadcastReceiver {
    static final String EXTRA_MODE = "mode";

    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = intent.getStringExtra(EXTRA_MODE);
        if (mode == null) return;
        Context app = context.getApplicationContext();
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                // A process started just for this broadcast gets Shizuku's binder a moment later.
                if (FlipShell.awaitReady(5000)) FlipShell.switchTo(app, mode);
            } finally {
                pending.finish();
            }
        }).start();
    }
}
