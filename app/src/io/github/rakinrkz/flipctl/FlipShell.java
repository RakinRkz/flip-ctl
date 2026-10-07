package io.github.rakinrkz.flipctl;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/** Runs the bundled flipctl.sh as the shell user through Shizuku. */
final class FlipShell {
    private static final String SCRIPT = "/data/local/tmp/flipctl.sh";

    static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        boolean ok() {
            return exitCode == 0;
        }
    }

    private FlipShell() {}

    static boolean ready() {
        return Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    /** Waits up to timeoutMs for Shizuku's binder, which reaches a fresh process asynchronously. */
    static boolean awaitReady(long timeoutMs) {
        CountDownLatch received = new CountDownLatch(1);
        Shizuku.OnBinderReceivedListener listener = received::countDown;
        Shizuku.addBinderReceivedListenerSticky(listener, new Handler(Looper.getMainLooper()));
        try {
            received.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            Shizuku.removeBinderReceivedListener(listener);
        }
        return ready();
    }

    /**
     * Switches to main, cover or dual. Cover and dual also open the panel on the cover screen
     * (dual reopens it after every unlock), so there is always a way back from there.
     */
    static Result switchTo(Context context, String mode) {
        String panel = context.getPackageName() + "/" + CoverActivity.class.getName();
        String[] args = mode.equals("dual") ? new String[] {"dual", "--keep", panel}
                : mode.equals("cover") ? new String[] {"cover", panel}
                : new String[] {mode};
        // The floating cover button needs "display over other apps"; the shell user can grant it.
        if (!mode.equals("main") && !Settings.canDrawOverlays(context)) {
            exec("appops", "set", context.getPackageName(), "SYSTEM_ALERT_WINDOW", "allow");
        }
        // In cover mode that button is the way back to the main screen, so never go without it.
        if (mode.equals("cover") && !Settings.canDrawOverlays(context)) {
            return new Result(1, "Not switching: Screens couldn't get \"Display over other apps\", "
                    + "which the cover screen button needs. Allow it in Settings > Apps > Screens.");
        }
        Result result = run(context, args);
        if (result.ok()) ModeNotification.update(context, mode);
        return result;
    }

    /**
     * Installs the bundled script (so the phone always runs the copy that ships with this app)
     * and runs it with args. Each call writes its own temp file ($$ is the shell's pid) and
     * renames it into place, so concurrent calls and a running --keep loop never see a partial
     * script.
     */
    static Result run(Context context, String... args) {
        String[] command = new String[args.length + 4];
        command[0] = "sh";
        command[1] = "-c";
        command[2] = "cat > " + SCRIPT + ".$$ && mv -f " + SCRIPT + ".$$ " + SCRIPT
                + " && exec sh " + SCRIPT + " \"$@\"";
        command[3] = "flipctl";
        System.arraycopy(args, 0, command, 4, args.length);
        try {
            Process process = newProcess(command);
            try (InputStream script = context.getAssets().open("flipctl.sh");
                    OutputStream stdin = process.getOutputStream()) {
                copy(script, stdin);
            }
            String output = read(process.getInputStream()) + read(process.getErrorStream());
            return new Result(process.waitFor(), output.trim());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return new Result(-1, "Shizuku call failed: " + cause);
        }
    }

    /** Runs one command as the shell user and waits for it; failures are ignored. */
    private static void exec(String... command) {
        try {
            Process process = newProcess(command);
            process.getOutputStream().close();
            read(process.getInputStream());
            process.waitFor();
        } catch (Exception ignored) {
        }
    }

    /** The current mode (main, cover, dual or other), or null if it can't be read. */
    static String mode(Context context) {
        if (!ready()) return null;
        Result result = run(context, "mode");
        return result.ok() ? result.output : null;
    }

    /**
     * The current mode plus doctor's warnings in one shell call: element 0 is the mode (null if
     * unreadable), element 1 the warnings, one per line ("" when there are none).
     */
    static String[] modeAndWarnings(Context context) {
        if (!ready()) return new String[] {null, ""};
        Result result = run(context, "mode", "--doctor");
        if (!result.ok()) return new String[] {null, result.output};
        String[] lines = result.output.split("\n", 2);
        return new String[] {lines[0].trim(), lines.length > 1 ? lines[1].trim() : ""};
    }

    // Shizuku 13 made newProcess private in favour of user services, but it is still the
    // simplest way to run one shell command, and other Shizuku clients call it the same way.
    private static Process newProcess(String[] command) throws Exception {
        Method method = Shizuku.class.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
        method.setAccessible(true);
        return (Process) method.invoke(null, command, null, null);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out);
        return out.toString("UTF-8");
    }
}
