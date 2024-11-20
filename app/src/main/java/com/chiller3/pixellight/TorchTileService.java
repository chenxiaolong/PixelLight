/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.NonNull;

/** Quick settings tile for toggling the torch status. The last selected brightness is used. */
public class TorchTileService extends TileService implements ServiceConnection, TorchSession.Listener {
    private static final String TAG = TorchTileService.class.getSimpleName();

    private TorchService.TorchBinder torchBinder;
    private int curBrightness = -1;

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(TAG, "Tile is listening");

        final var intent = new Intent(this, TorchService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);

        refreshTileState();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.d(TAG, "Tile is no longer listening");

        onBinderGone();
        unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        torchBinder = (TorchService.TorchBinder) service;
        torchBinder.registerTorchListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        onBinderGone();
    }

    private void onBinderGone() {
        if (torchBinder != null) {
            torchBinder.unregisterTorchListener(this);
        }

        torchBinder = null;
    }

    @Override
    public void onClick() {
        super.onClick();

        // With Android 15, it is no longer process to start a foreground service that relies on
        // while-in-use permissions, regardless if that's another service or the TileService itself.
        // We have no choice but to provide a worse experience and perform the operation through an
        // activity.

        final int newBrightness;

        if (curBrightness == -1) {
            Log.w(TAG, "onClick was reachable before camera session was ready");
            return;
        } else if (curBrightness == 0) {
            Log.d(TAG, "Toggling torch on");
            newBrightness = -1;
        } else {
            Log.d(TAG, "Toggling torch off");
            newBrightness = 0;
        }

        if (torchBinder.isInForeground()) {
            // With Android 15, we can't start a camera foreground service from a tile service
            // anymore, but we can connect to a previously started instance just fine.
            torchBinder.setTorchBrightness(newBrightness);
        } else {
            final var serviceIntent = TorchService.createSetBrightnessIntent(this, newBrightness);
            final var intent = FgsLauncherActivity.createIntent(this, serviceIntent);

            startActivityAndCollapse(PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
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

    @Override
    public void onTorchError(@NonNull TorchError error) {
        if (error == TorchError.NO_PERMISSION) {
            final var intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivityAndCollapse(PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        }
    }
}
