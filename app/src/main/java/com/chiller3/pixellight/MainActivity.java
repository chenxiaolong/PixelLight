/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.pixellight;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chiller3.pixellight.databinding.MainActivityBinding;

import java.util.Arrays;

public class MainActivity extends Activity implements ServiceConnection, TorchSession.Listener,
        SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {
    private static final int REQUEST_PERMISSIONS = 1;

    private MainActivityBinding binding;
    private Preferences prefs;
    private TorchService.TorchBinder torchBinder;
    private boolean initialUpdate = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = MainActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = new Preferences(this);

        binding.brightness.setEnabled(false);
        binding.brightness.setOnSeekBarChangeListener(this);

        binding.toggle.setEnabled(false);
        binding.toggle.setOnCheckedChangeListener(this);

        binding.requestPermissions.setOnClickListener(v ->
            requestPermissions(Permissions.REQUIRED, REQUEST_PERMISSIONS));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_options, menu);

        menu.findItem(R.id.keep_service_alive).setChecked(prefs.getKeepServiceAlive());

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.keep_service_alive) {
            item.setChecked(!item.isChecked());
            prefs.setKeepServiceAlive(item.isChecked());

            if (!item.isChecked() && torchBinder != null) {
                // Try to shut down the service so that the user doesn't have to manually turn the
                // torch on and off for the change to take effect.
                torchBinder.tryStopService();
            }

            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (Arrays.stream(grantResults).allMatch(r -> r == PackageManager.PERMISSION_GRANTED)) {
                refreshUiGroups();
                refreshCameras();
            } else if (!Permissions.canRequestRequired(this)) {
                startActivity(Permissions.getAppInfoIntent(this));
            }
        }
    }

    private void refreshUiGroups() {
        final var haveRequired = Permissions.haveRequired(this);
        binding.torchGroup.setVisibility(haveRequired ? View.VISIBLE : View.GONE);
        binding.permissionGroup.setVisibility(haveRequired ? View.GONE : View.VISIBLE);
    }

    private void refreshCameras() {
        if (torchBinder != null) {
            initialUpdate = true;
            torchBinder.refreshCameras();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        refreshUiGroups();

        final var intent = new Intent(this, TorchService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        onBinderGone();
        unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        torchBinder = (TorchService.TorchBinder) service;
        torchBinder.registerTorchListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        onBinderGone();
    }

    private void onBinderGone() {
        if (torchBinder != null) {
            torchBinder.unregisterTorchListener(this);
        }

        torchBinder = null;
    }

    @Override
    public void onTorchStateChanged(int curBrightness, int maxBrightness) {
        if (initialUpdate) {
            binding.brightness.setMin(1);
            binding.brightness.setMax(maxBrightness);
            binding.brightness.setProgress(prefs.getBrightness(maxBrightness));
            initialUpdate = false;
        }

        if (curBrightness == 0) {
            binding.brightness.setEnabled(false);
        } else {
            binding.brightness.setProgress(curBrightness);
            binding.brightness.setEnabled(true);
        }

        binding.toggle.setEnabled(true);
        binding.toggle.setChecked(curBrightness != 0);

        binding.label.setText(curBrightness + " / " + maxBrightness);
    }

    @Override
    public void onTorchError(@NonNull TorchError error) {
        // Retry if async ordering didn't allow onRequestPermissionsResult() to do the refresh.
        if (error == TorchError.NO_PERMISSION && Permissions.haveRequired(this)) {
            refreshCameras();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == binding.brightness && fromUser) {
            torchBinder.setTorchBrightness(progress);
            prefs.setBrightness(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
        // On configuration change, this may be invoked before the service is bound.
        if (buttonView == binding.toggle && torchBinder != null) {
            if (isChecked) {
                torchBinder.setTorchBrightness(binding.brightness.getProgress());
            } else {
                torchBinder.setTorchBrightness(0);
            }
        }
    }
}