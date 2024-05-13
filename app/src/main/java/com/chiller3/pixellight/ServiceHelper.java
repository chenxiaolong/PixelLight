/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.Collections;

/**
 * A helper class to implement the common logic for all services. This manages the torch session,
 * the service start/stop lifecycle, and notifications. It also handles session ownership for when
 * multiple services are running at the same time (see {@link TorchSession}).
 */
public class ServiceHelper<S extends Service & ServiceHelper.Callbacks>
        implements TorchSession.ServiceOwner, TorchSession.Listener {
    private static final String EXTRA_BRIGHTNESS = "brightness";

    private final String tag;
    private final String actionSetBrightness;
    private final String actionPersist;
    private final S service;
    private final TorchSession session;
    private final Preferences prefs;
    private final Notifications notifications;
    private int curBrightness = -1;
    private int maxBrightness = -1;

    private static @NonNull String getSetBrightnessAction(Class<?> serviceClass) {
        return serviceClass.getCanonicalName() + ".set_brightness";
    }

    private static @NonNull String getPersistAction(Class<?> serviceClass) {
        return serviceClass.getCanonicalName() + ".persist";
    }

    public static @NonNull Intent createSetBrightnessIntent(
            @NonNull Context context, @NonNull Class<?> serviceClass, int brightness) {
        final var intent = new Intent(context, serviceClass);
        intent.setAction(getSetBrightnessAction(serviceClass));
        // This is unused, but necessary to ensure that intents for different brightnesses are
        // treated as unique when used with PendingIntent.
        intent.setData(Uri.fromParts(EXTRA_BRIGHTNESS, Integer.toString(brightness), null));
        intent.putExtra(EXTRA_BRIGHTNESS, brightness);
        return intent;
    }

    public static @NonNull Intent createPersistIntent(
            @NonNull Context context, @NonNull Class<?> serviceClass) {
        final var intent = new Intent(context, serviceClass);
        intent.setAction(getPersistAction(serviceClass));
        return intent;
    }

    public ServiceHelper(@NonNull S service) {
        tag = ServiceHelper.class.getSimpleName() + '[' + service.getClass().getSimpleName() + ']';
        actionSetBrightness = getSetBrightnessAction(service.getClass());
        actionPersist = getPersistAction(service.getClass());
        this.service = service;
        session = TorchSession.getInstance(service);
        prefs = new Preferences(service);
        notifications = new Notifications(service);
    }

    public @NonNull TorchSession getSession() {
        return session;
    }

    public void onCreate() {
        Log.d(tag, "Creating service");

        session.registerServiceOwner(this);
        session.registerTorchListener(this);
    }

    public void onDestroy() {
        Log.d(tag, "Destroying service");

        session.unregisterTorchListener(this);
        session.unregisterServiceOwner(this);
    }

    public int onStartCommand(Intent intent) {
        Log.d(tag, "Received intent: " + intent);

        final var action = intent != null ? intent.getAction() : null;

        if (actionSetBrightness.equals(action)) {
            final var brightness = intent.getIntExtra(EXTRA_BRIGHTNESS, -1);
            setTorchBrightness(brightness);
            // If we're not the primary owner, there's no need to stay alive.
            tryStopService();
        } else if (actionPersist.equals(action)) {
            Log.d(tag, "Keeping service alive");
        } else {
            Log.w(tag, "Invalid intent: " + intent);
            tryStopService();
        }

        return Service.START_NOT_STICKY;
    }

    private void updateForegroundNotification() {
        if (!session.isServiceOwner(this)) {
            return;
        }

        // If we're here, then we're the service owner. Thus, if we don't have the initial state
        // yet, we can still assume that the torch is off.
        final var message = curBrightness > 0
                ? R.string.notification_persistent_torch_on
                : R.string.notification_persistent_torch_off;
        final var actionText = curBrightness > 0
                ? R.string.notification_action_turn_off
                : R.string.notification_action_turn_on;
        final var actionBrightness = curBrightness > 0 ? 0 : -1;
        final var actionIntent = createSetBrightnessIntent(
                service, service.getClass(), actionBrightness);
        final var notification = notifications.createPersistentNotification(
                message, Collections.singletonList(new Pair<>(actionText, actionIntent)));
        final var type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA : 0;
        service.startForeground(Notifications.ID_PERSISTENT, notification, type);
    }

    public void setTorchBrightness(int brightness) {
        if (brightness < 0) {
            brightness = prefs.getBrightness(maxBrightness);
        }
        session.setTorchBrightness(brightness);
    }

    public void tryStopService() {
        final var ownerNeeded = session.isOwnerNeeded();
        final var serviceNeeded = service.isServiceNeeded();
        Log.d(tag, "Attempting to stop service: ownerNeeded=" + ownerNeeded
                + ", serviceNeeded=" + serviceNeeded);

        if (!ownerNeeded && !serviceNeeded) {
            Log.d(tag, "Stopping foreground service");
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE);

            Log.d(tag, "Stopping service");
            service.stopSelf();
        } else {
            Log.d(tag, "Cannot stop service yet");
        }
    }

    @Override
    public void onTorchOwnerNeeded(boolean needService, boolean needForeground) {
        if (needService) {
            Log.d(tag, "Starting service to keep it alive");
            service.startService(createPersistIntent(service, service.getClass()));

            if (needForeground) {
                Log.d(tag, "Moving service to foreground for camera access");
                updateForegroundNotification();
            }
        } else {
            tryStopService();
        }
    }

    @Override
    public void onTorchStateChanged(int curBrightness, int maxBrightness) {
        this.curBrightness = curBrightness;
        this.maxBrightness = maxBrightness;

        if (session.isServiceOwner(this)) {
            updateForegroundNotification();
        }
    }

    @Override
    public void onTorchError(@NonNull TorchError error) {
        if (session.isServiceOwner(this)) {
            notifications.sendErrorNotification(error);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return super.toString() + '[' + service.toString() + ']';
    }

    public interface Callbacks {
        boolean isServiceNeeded();
    }
}
