/*
 * SPDX-FileCopyrightText: 2024-2025 Andrew Gunnerson
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
 * This is an exported activity for toggling the torch state or changing the brightness. This also
 * allows the quick settings tile to launch the while-in-use foreground service, which is no longer
 * possible from the tile's context in Android 15.
 */
public class ToggleActivity extends Activity {
    /** This is exported. Do not rename. */
    private static final String EXTRA_BRIGHTNESS = "brightness";

    public static @NonNull Intent createIntent(@NonNull Context context, int brightness) {
        final var intent = new Intent(context, ToggleActivity.class);
        intent.putExtra(EXTRA_BRIGHTNESS, brightness);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final var brightness = getIntent().getIntExtra(EXTRA_BRIGHTNESS, TorchSession.BRIGHTNESS_TOGGLE);
        final var serviceIntent = TorchService.createSetBrightnessIntent(this, brightness);
        startForegroundService(serviceIntent);

        finish();
    }
}
