/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

/**
 * Private Pixel-specific camera2 keys. These keys are normally present in the
 * /vendor/framework/com.google.android.camera.experimental[suffix].jar library. It's possible to
 * reference the libraries in the manifest and use reflection to access the fields. However, this
 * puts a maintenance burden on this project because the name of the library changes with every new
 * Pixel device generation. On the other hand, the actual keys have not changed since their initial
 * introduction.
 */
public final class ExperimentalKeys {
    private static final String NS_2020 = "com.google.pixel.experimental2020";

    public static final CaptureRequest.Key<Integer> REQUEST_FLASHLIGHT_BRIGHTNESS =
            new CaptureRequest.Key<>(NS_2020 + ".flashlightBrightness", Integer.TYPE);
    public static final CaptureRequest.Key<Boolean> REQUEST_FLASHLIGHT_BRIGHTNESS_ENABLED =
            new CaptureRequest.Key<>(NS_2020 + ".flashlightBrightnessEnabled", Boolean.TYPE);
    public static final CameraCharacteristics.Key<Integer> CHARACTERISTICS_FLASHLIGHT_BRIGHTNESS_LEVEL_MAX =
            new CameraCharacteristics.Key<>(NS_2020 + ".flashlightBrightnessLevelMax", Integer.TYPE);
}
