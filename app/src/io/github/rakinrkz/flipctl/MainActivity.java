package io.github.rakinrkz.flipctl;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import rikka.shizuku.Shizuku;

/** Three buttons that pick which screens are on. */
public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 1;
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    private final Map<String, Button> buttons = new LinkedHashMap<>();
    // onResume and the sticky binder listener both refresh on launch; one shell round-trip is enough.
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private TextView status;
    private Runnable statusAction;

    private final Shizuku.OnBinderReceivedListener onBinderReceived = () -> runOnUiThread(this::refresh);
    private final Shizuku.OnBinderDeadListener onBinderDead = () -> runOnUiThread(this::refresh);
    private final Shizuku.OnRequestPermissionResultListener onPermissionResult =
            (requestCode, grantResult) -> runOnUiThread(this::refresh);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(16));

        TextView title = new TextView(this);
        title.setText("Screens");
        title.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        addButton(root, "main", "Main screen");
        addButton(root, "cover", "Cover screen");
        addButton(root, "dual", "Both screens");

        status = new TextView(this);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, dp(12), 0, 0);
        status.setOnClickListener(v -> {
            if (statusAction != null) statusAction.run();
        });
        root.addView(status);

        setContentView(root);

        Shizuku.addBinderReceivedListenerSticky(onBinderReceived);
        Shizuku.addBinderDeadListener(onBinderDead);
        Shizuku.addRequestPermissionResultListener(onPermissionResult);

        // The ongoing "Main screen" notification is the way back from the cover screen.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST);
        }
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinderReceived);
        Shizuku.removeBinderDeadListener(onBinderDead);
        Shizuku.removeRequestPermissionResultListener(onPermissionResult);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Whether to close this panel once mode has been applied. */
    boolean finishAfter(String mode) {
        return true;
    }

    private void addButton(LinearLayout root, String mode, String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> choose(mode));
        root.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        buttons.put(mode, button);
    }

    private void refresh() {
        if (!Shizuku.pingBinder()) {
            setStatus("Shizuku isn't running. Tap here, then press Start in Shizuku.", this::openShizuku);
            return;
        }
        if (!FlipShell.ready()) {
            setStatus("Tap here to allow Screens in Shizuku.",
                    () -> Shizuku.requestPermission(PERMISSION_REQUEST));
            return;
        }
        setStatus("", null);
        if (!refreshing.compareAndSet(false, true)) return;
        new Thread(() -> {
            // Mode plus known traps (untested model, USB default that stops Shizuku on lock).
            String[] info = FlipShell.modeAndWarnings(this);
            String mode = info[0];
            // The mode may have changed outside the app (PC, `try` revert): keep the floating
            // cover button and its notification in step with it.
            if ("main".equals(mode) || "cover".equals(mode) || "dual".equals(mode)) {
                ModeNotification.update(this, mode);
            }
            runOnUiThread(() -> {
                refreshing.set(false);
                markCurrent(mode);
                setStatus(info[1].replace("warn: ", "⚠ "), null);
            });
        }).start();
    }

    private void choose(String mode) {
        if (!FlipShell.ready()) {
            refresh();
            return;
        }
        setButtonsEnabled(false);
        setStatus("Switching…", null);
        new Thread(() -> {
            FlipShell.Result result = FlipShell.switchTo(this, mode);
            runOnUiThread(() -> {
                if (result.ok() && finishAfter(mode)) {
                    finish();
                    return;
                }
                setButtonsEnabled(true);
                if (result.ok()) refresh();
                else setStatus(result.output, null);
            });
        }).start();
    }

    private void markCurrent(String current) {
        for (Map.Entry<String, Button> entry : buttons.entrySet()) {
            String label = entry.getValue().getText().toString().replace("  ✓", "");
            entry.getValue().setText(entry.getKey().equals(current) ? label + "  ✓" : label);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Button button : buttons.values()) button.setEnabled(enabled);
    }

    private void setStatus(String text, Runnable action) {
        status.setText(text);
        statusAction = action;
    }

    private void openShizuku() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
        if (intent != null) startActivity(intent);
        else setStatus("Install Shizuku first (github.com/RikkaApps/Shizuku).", null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
