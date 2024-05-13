/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Arrays;
import java.util.List;

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

    public Notification createPersistentNotification(@StringRes int titleResId,
                                                     List<Pair<Integer, Intent>> actions) {
        final var notificationIntent = new Intent(context, MainActivity.class);
        final var pendingIntent = PendingIntent.getActivity(
                context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        final var builder = new Notification.Builder(context, CHANNEL_ID_PERSISTENT);
        builder.setContentTitle(context.getText(titleResId));
        builder.setSmallIcon(R.drawable.ic_notifications);
        builder.setContentIntent(pendingIntent);
        builder.setOngoing(true);
        builder.setOnlyAlertOnce(true);

        for (final var pair : actions) {
            final var actionPendingIntent = PendingIntent.getService(context, 0, pair.second,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                            | PendingIntent.FLAG_ONE_SHOT);
            builder.addAction(new Notification.Action.Builder(
                    null, context.getString(pair.first), actionPendingIntent).build());
        }

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
