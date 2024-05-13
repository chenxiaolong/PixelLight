/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Quick settings tile for toggling the torch status. The last selected brightness is used.
 * <p>
 * The service starts when the system binds to it, but may stop long after the system unbinds if it
 * is the primary owner of the {@link TorchSession}. The complex service lifecycle and code
 * duplication of {@link TorchService} is due to Android 14's new restrictions on when foreground
 * services are allowed to access while-in-use permissions. See {@link TorchSession} and
 * {@link #isServiceNeeded()} for more details.
 */
public class TorchTileService extends TileService implements TorchSession.Listener, ServiceHelper.Callbacks {
    private static final String TAG = TorchTileService.class.getSimpleName();

    private ServiceHelper<TorchTileService> helper;
    private boolean isBound = false;
    private boolean isListening = false;
    private int curBrightness = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        helper = new ServiceHelper<>(this);
        helper.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        helper.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return helper.onStartCommand(intent);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(TAG, "Tile is listening");

        isListening = true;
        helper.getSession().registerTorchListener(this);
        refreshTileState();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.d(TAG, "Tile is no longer listening");

        isListening = false;
        helper.getSession().unregisterTorchListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        final var binder = super.onBind(intent);
        Log.d(TAG, "Binding service: " + intent);

        isBound = true;
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        final var allowRebind = super.onUnbind(intent);
        Log.d(TAG, "Unbinding service: " + intent);

        isBound = false;
        helper.tryStopService();
        return allowRebind;
    }

    @Override
    public void onClick() {
        super.onClick();

        if (curBrightness == -1) {
            Log.w(TAG, "onClick was reachable before camera session was ready");
        } else if (curBrightness == 0) {
            Log.d(TAG, "Toggling torch on");
            helper.setTorchBrightness(-1);
        } else {
            Log.d(TAG, "Toggling torch off");
            helper.setTorchBrightness(0);
        }

        // The tile state will be changed when onTorchStateChanged() is called.
    }

    private void refreshTileState() {
        final var tile = getQsTile();
        if (tile == null) {
            Log.w(TAG, "Tile was null during refreshTileState");
            return;
        }

        if (curBrightness < 0) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else if (curBrightness == 0) {
            tile.setState(Tile.STATE_INACTIVE);
        } else {
            tile.setState(Tile.STATE_ACTIVE);
        }

        tile.updateTile();
    }

    @Override
    public void onTorchStateChanged(int curBrightness, int maxBrightness) {
        Log.d(TAG, "New torch state: current=" + curBrightness + ", max=" + maxBrightness);
        this.curBrightness = curBrightness;
        refreshTileState();
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Override
    public void onTorchError(@NonNull TorchError error) {
        if (error == TorchError.NO_PERMISSION) {
            final var intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(
                        this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(intent);
            }
        }
    }

    /**
     * Stop the service only if no clients are bound, the tile is not listening, and the torch
     * session does not require us to stay alive. This is necessary because
     * {@link #startForeground(int, Notification, int)} can only be called once after the system
     * binds to the service. After moving out of the foreground with {@link #stopForeground(int)},
     * attempting to move back into the foreground before the system binds to this service again
     * will result in a {@link SecurityException} due to Android 14's restrictions on when a
     * foreground service is allowed to use while-in-use permissions.
     * <p>
     * These restrictions are enforced in ActiveServices.shouldAllowFgsWhileInUsePermissionLocked()
     * in the AOSP code base.
     */
    @Override
    public boolean isServiceNeeded() {
        Log.d(TAG, "Is service needed: isBound=" + isBound + ", isListening=" + isListening);
        return isBound || isListening;
    }
}
