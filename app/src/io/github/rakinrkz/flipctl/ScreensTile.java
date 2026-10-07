package io.github.rakinrkz.flipctl;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile that opens the Screens panel and shows the current mode. */
public class ScreensTile extends TileService {
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        new Thread(() -> {
            // A tile process started just now gets Shizuku's binder a moment later.
            FlipShell.awaitReady(3000);
            String mode = FlipShell.mode(this);
            main.post(() -> show(mode));
        }).start();
    }

    @Override
    public void onClick() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void show(String mode) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(mode == null || mode.equals("main") ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setSubtitle(mode == null ? "Shizuku off"
                : mode.equals("dual") ? "Both"
                : mode.equals("cover") ? "Cover"
                : mode.equals("main") ? "Main" : mode);
        tile.updateTile();
    }
}
