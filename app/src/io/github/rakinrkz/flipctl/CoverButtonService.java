package io.github.rakinrkz.flipctl;

import android.app.ActivityOptions;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * While cover or dual mode is on: a small floating button on the cover screen that opens the
 * Screens panel there. Unlike the panel or a notification, closing things on the cover can't
 * remove it. Runs in the foreground so Samsung doesn't freeze the app and drop the button.
 */
public class CoverButtonService extends Service {
    static final String EXTRA_MODE = "mode";
    static final int COVER_DISPLAY = 1;

    private WindowManager windowManager;
    private View button;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String mode = intent != null && intent.getStringExtra(EXTRA_MODE) != null
                ? intent.getStringExtra(EXTRA_MODE) : "cover";
        startForeground(ModeNotification.ID, ModeNotification.build(this, mode),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (button == null) showButton();
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        if (button != null) windowManager.removeView(button);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showButton() {
        Display cover = getSystemService(DisplayManager.class).getDisplay(COVER_DISPLAY);
        if (cover == null || !Settings.canDrawOverlays(this)) return;
        Context context = createDisplayContext(cover)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        windowManager = context.getSystemService(WindowManager.class);

        int size = dp(context, 52);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xCC2563EB);
        ImageView view = new ImageView(context);
        view.setImageResource(R.drawable.ic_screens);
        view.setBackground(circle);
        view.setPadding(size / 4, size / 4, size / 4, size / 4);
        view.setContentDescription("Screens");

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(context, 8);
        params.y = dp(context, 120);
        view.setOnTouchListener(new DragOrTap(params));

        windowManager.addView(view, params);
        button = view;
    }

    private void openPanel() {
        Intent intent = new Intent(this, CoverActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(COVER_DISPLAY);
        startActivity(intent, options.toBundle());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Drag vertically to move the button out of the way; a tap opens the panel. */
    private final class DragOrTap implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private float downY;
        private int startY;
        private boolean dragging;

        DragOrTap(WindowManager.LayoutParams params) {
            this.params = params;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = event.getRawY();
                    startY = params.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dy = event.getRawY() - downY;
                    if (Math.abs(dy) > dp(view.getContext(), 8)) dragging = true;
                    if (dragging) {
                        // Keep it on screen: on the cover it is the way back to the main screen.
                        int maxY = windowManager.getCurrentWindowMetrics().getBounds().height()
                                - view.getHeight();
                        params.y = Math.max(0, Math.min(maxY, startY + Math.round(dy)));
                        windowManager.updateViewLayout(view, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) openPanel();
                    return true;
                default:
                    return false;
            }
        }
    }
}
