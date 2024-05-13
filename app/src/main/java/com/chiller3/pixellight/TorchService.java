/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Service for managing the torch state that can be bound.
 * <p>
 * The service starts when the activity binds to it, but may stop long after the activity unbinds if
 * it is the primary owner of the {@link TorchSession}. {@link TorchTileService} cannot use this
 * service due to Android 14's new restrictions on when foreground services are allowed to access
 * while-in-use permissions. See {@link TorchSession} and {@link TorchTileService#isServiceNeeded()}
 * for more details.
 */
public class TorchService extends Service implements ServiceHelper.Callbacks {
    private ServiceHelper<TorchService> helper;

    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
        return new TorchBinder();
    }

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
    public boolean isServiceNeeded() {
        return false;
    }

    public class TorchBinder extends Binder {
        @MainThread
        public void registerTorchListener(@NonNull TorchSession.Listener listener) {
            helper.getSession().registerTorchListener(listener);
        }

        @MainThread
        public void unregisterTorchListener(@NonNull TorchSession.Listener listener) {
            helper.getSession().unregisterTorchListener(listener);
        }

        @MainThread
        public void setTorchBrightness(int brightness) {
            helper.setTorchBrightness(brightness);
        }

        @MainThread
        public void refreshCameras() {
            helper.getSession().refreshCameras();
        }
    }
}
