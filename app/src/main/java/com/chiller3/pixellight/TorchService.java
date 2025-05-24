/*
 * SPDX-FileCopyrightText: 2024-2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;

/** Service for managing the torch state that can be bound. */
public class TorchService extends Service implements TorchSession.ServiceOwner, TorchSession.Listener {
    private static final String TAG = TorchService.class.getSimpleName();

    private static final String ACTION_SET_BRIGHTNESS =
            TorchService.class.getCanonicalName() + ".set_brightness";
    private static final String ACTION_PERSIST =
            TorchService.class.getCanonicalName() + ".persist";

    private static final String EXTRA_BRIGHTNESS = "brightness";

    private TorchSession session;
    private Preferences prefs;
    private Notifications notifications;
    private int curBrightness = -1;
    private boolean initialUpdate = true;
    private boolean foreground = false;

    public static @NonNull Intent createSetBrightnessIntent(
            @NonNull Context context, int brightness) {
        final var intent = new Intent(context, TorchService.class);
        intent.setAction(ACTION_SET_BRIGHTNESS);
        // This is unused, but necessary to ensure that intents for different brightnesses are
        // treated as unique when used with PendingIntent.
        intent.setData(Uri.fromParts(EXTRA_BRIGHTNESS, Integer.toString(brightness), null));
        intent.putExtra(EXTRA_BRIGHTNESS, brightness);
        return intent;
    }

    private static @NonNull Intent createPersistIntent(@NonNull Context context) {
        final var intent = new Intent(context, TorchService.class);
        intent.setAction(ACTION_PERSIST);
        return intent;
    }

    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
        return new TorchBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Creating service");

        session = new TorchSession(this, this);
        prefs = new Preferences(this);
        notifications = new Notifications(this);

        session.registerTorchListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying service");

        session.unregisterTorchListener(this);

        if (session.isOwnerNeeded()) {
            throw new IllegalStateException("Service destroyed while session still requires it");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Received intent: " + intent);

        final var action = intent != null ? intent.getAction() : null;

        if (ACTION_SET_BRIGHTNESS.equals(action)) {
            final var brightness = intent.getIntExtra(EXTRA_BRIGHTNESS, TorchSession.BRIGHTNESS_TOGGLE);
            session.setTorchBrightness(brightness);
        } else if (ACTION_PERSIST.equals(action)) {
            Log.d(TAG, "Keeping service alive");
        } else {
            Log.w(TAG, "Invalid intent: " + intent);
            tryStopService();
        }

        return START_NOT_STICKY;
    }

    private void updateForegroundNotification() {
        // If we're here, then we're the service owner. Thus, if we don't have the initial state
        // yet, we can still assume that the torch is off.
        final var message = curBrightness > 0
                ? R.string.notification_persistent_torch_on
                : R.string.notification_persistent_torch_off;
        final var actionText = curBrightness > 0
                ? R.string.notification_action_turn_off
                : R.string.notification_action_turn_on;
        final var actionBrightness = curBrightness > 0 ? 0 : TorchSession.BRIGHTNESS_PERSISTED;
        final var actionIntent = createSetBrightnessIntent(this, actionBrightness);
        final var notification = notifications.createPersistentNotification(
                message, Collections.singletonList(new Pair<>(actionText, actionIntent)));
        final var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        startForeground(Notifications.ID_PERSISTENT, notification, type);

        foreground = true;
    }

    public void tryStopService() {
        final var ownerNeeded = session.isOwnerNeeded();
        Log.d(TAG, "Attempting to stop service: ownerNeeded=" + ownerNeeded);

        if (prefs.getKeepServiceAlive()) {
            Log.d(TAG, "Keeping service alive indefinitely");
        } else if (!ownerNeeded) {
            Log.d(TAG, "Stopping foreground service");
            stopForeground(Service.STOP_FOREGROUND_REMOVE);

            foreground = false;

            Log.d(TAG, "Stopping service");
            stopSelf();
        } else {
            Log.d(TAG, "Cannot stop service yet");
        }
    }

    @Override
    public void onTorchOwnerNeeded(boolean needService, boolean needForeground) {
        if (needService) {
            Log.d(TAG, "Starting service to keep it alive");
            startService(createPersistIntent(this));

            if (needForeground) {
                Log.d(TAG, "Moving service to foreground for camera access");
                updateForegroundNotification();
            }
        } else {
            tryStopService();
        }
    }

    @Override
    public void onTorchStateChanged(int curBrightness, int maxBrightness) {
        this.curBrightness = curBrightness;

        if (initialUpdate) {
            initialUpdate = false;
        } else {
            updateForegroundNotification();
        }
    }

    @Override
    public void onTorchError(@NonNull TorchError error) {
        notifications.sendErrorNotification(error);
    }

    public class TorchBinder extends Binder {
        @MainThread
        public void registerTorchListener(@NonNull TorchSession.Listener listener) {
            session.registerTorchListener(listener);
        }

        @MainThread
        public void unregisterTorchListener(@NonNull TorchSession.Listener listener) {
            session.unregisterTorchListener(listener);
        }

        @MainThread
        public void setTorchBrightness(int brightness) {
            session.setTorchBrightness(brightness);
        }

        @MainThread
        public void refreshCameras() {
            session.refreshCameras();
        }

        @MainThread
        public boolean isInForeground() {
            return foreground;
        }

        @MainThread
        public void tryStopService() {
            TorchService.this.tryStopService();
        }
    }
}
