/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

public class Preferences {
    private static final String PREF_BRIGHTNESS = "brightness";

    private final SharedPreferences prefs;

    public Preferences(@NonNull Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public int getBrightness(int defaultValue) {
        return prefs.getInt(PREF_BRIGHTNESS, defaultValue);
    }

    public void setBrightness(int value) {
        prefs.edit().putInt(PREF_BRIGHTNESS, value).apply();
    }
}
