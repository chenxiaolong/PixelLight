/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Arrays;

public class Notifications {
    public static final String CHANNEL_ID_PERSISTENT = "persistent";
    public static final String CHANNEL_ID_ERROR = "error";
    public static final int ID_PERSISTENT = 1;
    public static final int ID_ERROR = 2;

    private final Context context;
    private final NotificationManager notificationManager;

    public Notifications(@NonNull Context context) {
        this.context = context;
        notificationManager = context.getSystemService(NotificationManager.class);
    }

    private NotificationChannel createPersistentChannel() {
        final var channel = new NotificationChannel(CHANNEL_ID_PERSISTENT,
                context.getString(R.string.notification_channel_persistent_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.notification_channel_persistent_desc));
        channel.setShowBadge(false);
        return channel;
    }

    private NotificationChannel createErrorChannel() {
        final var channel = new NotificationChannel(CHANNEL_ID_ERROR,
                context.getString(R.string.notification_channel_error_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notification_channel_error_desc));
        return channel;
    }

    public void updateChannels() {
        notificationManager.createNotificationChannels(Arrays.asList(
                createPersistentChannel(),
                createErrorChannel()
        ));
    }

    private @NonNull Notification.Action createBrightnessAction(
            @StringRes int textResId, int brightness) {
        final var intent = TorchService.createSetBrightnessIntent(context, brightness);
        final var pendingIntent = PendingIntent.getService(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_ONE_SHOT);

        return new Notification.Action.Builder(null, context.getString(textResId), pendingIntent)
                .build();
    }

    public Notification createPersistentNotification(int curBrightness, int maxBrightness) {
        final var notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        final var pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        final var titleResId = curBrightness > 0
                ? R.string.notification_persistent_torch_on
                : R.string.notification_persistent_torch_off;

        final var builder = new Notification.Builder(context, CHANNEL_ID_PERSISTENT);
        builder.setContentTitle(context.getText(titleResId));
        builder.setSmallIcon(R.drawable.ic_notifications);
        builder.setContentIntent(pendingIntent);
        builder.setOngoing(true);
        builder.setOnlyAlertOnce(true);

        if (curBrightness > 0) {
            builder.setContentText(curBrightness + " / " + maxBrightness);
            builder.setProgress(maxBrightness, curBrightness, false);
        }

        final var toggleResId = curBrightness > 0
                ? R.string.notification_action_turn_off
                : R.string.notification_action_turn_on;
        final var toggleBrightness = curBrightness > 0 ? 0 : TorchSession.BRIGHTNESS_PERSISTED;
        builder.addAction(createBrightnessAction(toggleResId, toggleBrightness));

        if (curBrightness > 0) {
            if (curBrightness > 1) {
                builder.addAction(createBrightnessAction(R.string.notification_action_decrease,
                        TorchSession.BRIGHTNESS_DECREASE));
            }
            if (curBrightness < maxBrightness) {
                builder.addAction(createBrightnessAction(R.string.notification_action_increase,
                        TorchSession.BRIGHTNESS_INCREASE));
            }
        }

        final var onDismissIntent = TorchService.createPersistIntent(context);
        final var onDismissPendingIntent = PendingIntent.getService(context, 0,
                onDismissIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setDeleteIntent(onDismissPendingIntent);

        // Inhibit 10-second delay when showing persistent notification.
        builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);

        return builder.build();
    }

    public void sendErrorNotification(@NonNull TorchError error) {
        final var builder = new Notification.Builder(context, CHANNEL_ID_ERROR);
        builder.setContentTitle(context.getString(error.toUiString()));
        builder.setSmallIcon(R.drawable.ic_notifications);

        notificationManager.notify(ID_ERROR, builder.build());
    }
}
