/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.hardware.camera2.CameraAccessException;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public enum TorchError {
    NO_PERMISSION,
    BLOCKED_BY_POLICY,
    DISCONNECTED,
    DEVICE_ERROR,
    SERVICE_ERROR,
    SESSION_ERROR,
    IN_USE,
    MAXIMUM_IN_USE,
    NO_VALID_CAMERA,
    UNKNOWN;

    public @StringRes int toUiString() {
        return switch (this) {
            case NO_PERMISSION -> R.string.notification_error_no_permission;
            case BLOCKED_BY_POLICY -> R.string.notification_error_blocked_by_policy;
            case DISCONNECTED -> R.string.notification_error_disconnected;
            case DEVICE_ERROR -> R.string.notification_error_device_error;
            case SERVICE_ERROR -> R.string.notification_error_service_error;
            case SESSION_ERROR -> R.string.notification_error_session_error;
            case IN_USE -> R.string.notification_error_in_use;
            case MAXIMUM_IN_USE -> R.string.notification_error_maximum_in_use;
            case NO_VALID_CAMERA -> R.string.notification_error_no_valid_camera;
            case UNKNOWN -> R.string.notification_error_unknown;
        };
    }

    public static TorchError fromException(@NonNull CameraAccessException exception) {
        return switch (exception.getReason()) {
            case CameraAccessException.CAMERA_DISABLED -> BLOCKED_BY_POLICY;
            case CameraAccessException.CAMERA_DISCONNECTED -> DISCONNECTED;
            case CameraAccessException.CAMERA_ERROR -> DEVICE_ERROR;
            case CameraAccessException.CAMERA_IN_USE -> IN_USE;
            case CameraAccessException.MAX_CAMERAS_IN_USE -> MAXIMUM_IN_USE;
            default -> UNKNOWN;
        };
    }
}
