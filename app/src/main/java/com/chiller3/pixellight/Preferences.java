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
    private static final String PREF_KEEP_SERVICE_ALIVE = "keep_service_alive";

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

    public boolean getKeepServiceAlive() {
        return prefs.getBoolean(PREF_KEEP_SERVICE_ALIVE, false);
    }

    public void setKeepServiceAlive(boolean keep) {
        prefs.edit().putBoolean(PREF_KEEP_SERVICE_ALIVE, keep).apply();
    }
}
