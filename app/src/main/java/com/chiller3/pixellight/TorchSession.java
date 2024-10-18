/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * A type to manage the lifecycle of the torch. The lifecycle begins when the torch is turned on and
 * ends when it turns off.
 */
public class TorchSession {
    private enum State {
        OFF,
        ACTIVATING,
        ON,
    }

    private static final String TAG = TorchSession.class.getSimpleName();

    // Things following the object lifecycle.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HashSet<Listener> listeners = new HashSet<>();
    private final ServiceOwner serviceOwner;
    private final CameraManager cameraManager;
    private final HandlerThread cameraThread = new HandlerThread("CameraThread");
    private final Handler cameraHandler;
    private final Executor cameraExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            if (!cameraHandler.post(command)) {
                throw new RejectedExecutionException("Failed to queue runnable: " + command);
            }
        }
    };
    private final SurfaceTexture surfaceTexture = new SurfaceTexture(0);
    private final Surface surface = new Surface(surfaceTexture);

    // Things following the torch lifecycle.
    private State state = State.OFF;
    private String cameraId;
    private int maxBrightness = -1;
    private int curBrightness = 0;
    private int desiredBrightness = 0;
    private CameraDevice camera;
    private CameraCaptureSession session;

    // Callbacks.
    private final CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mainHandler.post(() -> onCameraOpened(camera));
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mainHandler.post(() -> onCameraClosed(camera));
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mainHandler.post(() -> onCameraError(camera, error));
        }
    };
    private final CameraCaptureSession.StateCallback sessionCallback =
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mainHandler.post(() -> onSessionConfigured(session));
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    mainHandler.post(() -> onSessionConfigureFailed(session));
                }
            };

    public TorchSession(@NonNull Context context, @NonNull ServiceOwner owner) {
        serviceOwner = owner;

        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        cameraManager = context.getSystemService(CameraManager.class);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        cameraThread.quitSafely();

        surface.release();
        surfaceTexture.release();
    }

    @MainThread
    public void registerTorchListener(@NonNull Listener listener) {
        Log.d(TAG, "Registering listener: " + listener);

        if (!listeners.add(listener)) {
            Log.w(TAG, "Listener was already registered: " + listener);
        }

        if (updateCameraDetails()) {
            listener.onTorchStateChanged(curBrightness, maxBrightness);
        }
    }

    @MainThread
    public void unregisterTorchListener(@NonNull Listener listener) {
        Log.d(TAG, "Unregistering listener: " + listener);

        if (!listeners.remove(listener)) {
            Log.w(TAG, "Listener was never registered: " + listener);
        }
    }

    public boolean isOwnerNeeded() {
        return state != State.OFF;
    }

    private void notifyOwnerNeeded() {
        Log.d(TAG, "Notifying primary owner that foreground mode is needed");
        serviceOwner.onTorchOwnerNeeded(true, state != State.OFF);
    }

    private void tryNotifyOwnerNotNeeded() {
        if (isOwnerNeeded()) {
            Log.d(TAG, "Foreground mode is still needed: state=" + state);
        } else {
            Log.d(TAG, "Notifying primary owner that foreground mode is not needed");
            serviceOwner.onTorchOwnerNeeded(false, false);
        }
    }

    @MainThread
    private boolean updateCameraDetails() {
        if (cameraId != null) {
            return true;
        }

        try {
            for (final var cameraId : cameraManager.getCameraIdList()) {
                final var characteristics = cameraManager.getCameraCharacteristics(cameraId);
                final var maxBrightness = characteristics.get(
                        ExperimentalKeys.CHARACTERISTICS_FLASHLIGHT_BRIGHTNESS_LEVEL_MAX);
                if (maxBrightness == null) {
                    continue;
                }

                this.cameraId = cameraId;
                this.maxBrightness = maxBrightness;
                this.curBrightness = 0;

                Log.d(TAG, "Found camera " + cameraId + " with max brightness " + maxBrightness);

                return true;
            }

            Log.e(TAG, "Failed to find suitable camera");
            onError(TorchError.NO_VALID_CAMERA);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to query for suitable cameras", e);
            onError(TorchError.fromException(e));
        }

        return false;
    }

    @MainThread
    public void refreshCameras() {
        if (updateCameraDetails()) {
            for (final var listener : listeners) {
                listener.onTorchStateChanged(curBrightness, maxBrightness);
            }
        }
    }

    @MainThread
    public void setTorchBrightness(int brightness) {
        Log.d(TAG, "User requesting brightness of " + brightness);
        desiredBrightness = Math.min(brightness, maxBrightness);

        if (desiredBrightness == 0) {
            closeCamera();
            return;
        }

        switch (state) {
            case OFF -> openCamera();
            // Session is not ready yet. It'll pick up the new value when it is ready.
            case ACTIVATING -> {}
            // Session is already active. Change the brightness with a new capture request.
            case ON -> performCapture();
        }
    }

    @MainThread
    private void onError(@NonNull TorchError error) {
        Log.w(TAG, "Camera lifecycle exiting due to error: " + error);

        notifyTorchError(error);

        if (state != State.OFF) {
            closeCamera();
        }
    }

    @MainThread
    private void openCamera() {
        assert state == State.OFF;
        state = State.ACTIVATING;

        notifyOwnerNeeded();

        try {
            cameraManager.openCamera(cameraId, cameraCallback, cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to open camera: " + cameraId, e);
            onError(TorchError.fromException(e));
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied when opening camera: " + cameraId, e);
            onError(TorchError.NO_PERMISSION);
        }
    }

    @MainThread
    private void closeCamera() {
        // We don't need to close the session. Closing the camera device is sufficient.
        session = null;

        if (camera != null) {
            camera.close();
            camera = null;
        }

        final var notifyOwner = state != State.OFF;
        state = State.OFF;
        curBrightness = 0;

        notifyTorchState();

        // Only notify if the torch was turned on. The service would not have been running in the
        // foreground otherwise.
        if (notifyOwner) {
            tryNotifyOwnerNotNeeded();
        }
    }

    @MainThread
    private void onCameraOpened(@NonNull CameraDevice camera) {
        Log.d(TAG, "Camera " + camera.getId() + " opened");

        this.camera = camera;

        final var output = Collections.singletonList(new OutputConfiguration(surface));
        final var sessionConfiguration = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, output, cameraExecutor, sessionCallback);

        try {
            camera.createCaptureSession(sessionConfiguration);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create capture session", e);
            onError(TorchError.fromException(e));
        }
    }

    @MainThread
    private void onCameraClosed(@NonNull CameraDevice camera) {
        Log.e(TAG, "Camera " + camera.getId() + " disconnected");

        onError(TorchError.DISCONNECTED);
    }

    @MainThread
    private void onCameraError(@NonNull CameraDevice camera, int error) {
        Log.e(TAG, "Camera " + camera.getId() + " failed with error: " + error);

        final var torchError = switch (error) {
            case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> TorchError.IN_USE;
            case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> TorchError.MAXIMUM_IN_USE;
            case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> TorchError.BLOCKED_BY_POLICY;
            case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> TorchError.DEVICE_ERROR;
            case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> TorchError.SERVICE_ERROR;
            default -> TorchError.UNKNOWN;
        };

        onError(torchError);
    }

    @MainThread
    private void onSessionConfigured(@NonNull CameraCaptureSession session) {
        Log.d(TAG, "Camera session configured: " + session);

        this.session = session;

        performCapture();
    }

    @MainThread
    private void onSessionConfigureFailed(@NonNull CameraCaptureSession session) {
        Log.e(TAG, "Failed to configure session: " + session);

        onError(TorchError.SESSION_ERROR);
    }

    @MainThread
    private void performCapture() {
        try {
            assert state == State.ACTIVATING || state == State.ON;
            state = State.ON;

            if (curBrightness != desiredBrightness) {
                Log.d(TAG, "Performing capture because current brightness (" + curBrightness +
                        ") != desired brightness (" + desiredBrightness + ")");
                curBrightness = desiredBrightness;

                final var captureRequest = session.getDevice()
                        .createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
                captureRequest.addTarget(surface);
                captureRequest.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                captureRequest.set(ExperimentalKeys.REQUEST_FLASHLIGHT_BRIGHTNESS_ENABLED, true);
                captureRequest.set(ExperimentalKeys.REQUEST_FLASHLIGHT_BRIGHTNESS, curBrightness);

                session.capture(captureRequest.build(), null, cameraHandler);

                notifyTorchState();
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to perform capture", e);
            onError(TorchError.fromException(e));
        }
    }

    private void notifyTorchState() {
        if (cameraId != null) {
            for (final var listener : listeners) {
                listener.onTorchStateChanged(curBrightness, maxBrightness);
            }
        }
    }

    private void notifyTorchError(@NonNull TorchError error) {
        for (final var listener : listeners) {
            listener.onTorchError(error);
        }
    }

    public interface Listener {
        @MainThread
        void onTorchStateChanged(int curBrightness, int maxBrightness);

        @MainThread
        void onTorchError(@NonNull TorchError error);
    }

    public interface ServiceOwner {
        @MainThread
        void onTorchOwnerNeeded(boolean needService, boolean needForeground);
    }
}
