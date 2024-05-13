/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Permissions {
    public static final String[] REQUIRED;

    static {
        final var required = new ArrayList<String>();
        required.add(Manifest.permission.CAMERA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        REQUIRED = required.toArray(new String[0]);
    }

    private Permissions() {}

    public static boolean haveRequired(@NonNull Context context) {
        return Arrays.stream(REQUIRED).allMatch(p ->
                context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED);
    }

    public static boolean canRequestRequired(@NonNull Activity activity) {
        return Arrays.stream(REQUIRED).allMatch(activity::shouldShowRequestPermissionRationale);
    }

    public static @NonNull Intent getAppInfoIntent(@NonNull Context context) {
        final var uri = Uri.fromParts("package", context.getPackageName(), null);
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
    }
}
