package com.unciv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;

/** Adult-controlled device credential, never an age-screen answer, grants online access. */
public class WigoParentActivity extends Activity {
    private static volatile boolean onlineAllowed;
    private static Runnable lockListener;
    private boolean awaitingCredential;
    public static boolean isOnlineAllowed() { return onlineAllowed; }
    public static void setLockListener(Runnable listener) { lockListener = listener; }
    public static void lock() {
        onlineAllowed = false;
        if (lockListener != null) lockListener.run();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        awaitingCredential = state != null && state.getBoolean("awaitingCredential", false);
        if (awaitingCredential) return; // Android delivers the outstanding credential result after recreation.
        if (onlineAllowed) {
            new AlertDialog.Builder(this).setTitle(R.string.wigo_parent_title)
                .setMessage(R.string.wigo_parent_enabled)
                .setPositiveButton(R.string.wigo_parent_lock, (d, w) -> { lock(); finish(); })
                .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish()).show();
            return;
        }
        KeyguardManager manager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        Intent confirmation = manager == null ? null : manager.createConfirmDeviceCredentialIntent(
                getString(R.string.wigo_parent_title), getString(R.string.wigo_parent_credential));
        if (manager == null || !manager.isDeviceSecure() || confirmation == null) {
            new AlertDialog.Builder(this).setTitle(R.string.wigo_parent_title)
                .setMessage(R.string.wigo_parent_no_lock)
                .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                .setOnCancelListener(d -> finish()).show();
        } else {
            awaitingCredential = true;
            startActivityForResult(confirmation, 7104);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("awaitingCredential", awaitingCredential);
        super.onSaveInstanceState(state);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 7104) return;
        awaitingCredential = false;
        if (result != RESULT_OK) { finish(); return; }
        new AlertDialog.Builder(this).setTitle(R.string.wigo_parent_title)
            .setMessage(R.string.wigo_parent_warning)
            .setPositiveButton(R.string.wigo_parent_enable, (d, w) -> { onlineAllowed = true; finish(); })
            .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
            .setOnCancelListener(d -> finish()).show();
    }
}
