package io.github.rakinrkz.flipctl;

import android.os.Bundle;

/**
 * The same panel, in its own task, opened on the cover screen by cover and dual mode. It shows
 * over the lock screen so "Main screen" is reachable without unlocking on the small screen.
 */
public class CoverActivity extends MainActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
    }

    @Override
    boolean finishAfter(String mode) {
        // Cover and dual relaunch this very activity, so closing it would leave the cover without it.
        return mode.equals("main");
    }
}
