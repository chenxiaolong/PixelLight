/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Launder the launching of foreground services through an activity. Allows launching while-in-use
 * foreground services from a {@link android.service.quicksettings.TileService}, which is no longer
 * allowed from a quick settings tile's context in Android 15.
 */
public class FgsLauncherActivity extends Activity {
    private static final String EXTRA_SERVICE_INTENT = "service_intent";

    public static @NonNull Intent createIntent(
            @NonNull Context context, @NonNull Intent serviceIntent) {
        final var intent = new Intent(context, FgsLauncherActivity.class);
        intent.putExtra(EXTRA_SERVICE_INTENT, serviceIntent);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final var intent = getIntent().getParcelableExtra(EXTRA_SERVICE_INTENT, Intent.class);
        startForegroundService(intent);

        finish();
    }
}
