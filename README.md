# PixelLight

<img src="app/images/icon.svg" alt="app icon" width="72" />

PixelLight is a bare-bones flashlight app for Google Pixel devices that can access higher brightness levels than what is typically allowed by the standard Android 13+ torch APIs.

It allows access to the same brightness levels as Google Magnifier, except without the excessive 200% CPU usage due to constant camera processing. However, due to how the Pixel private API works, avoiding camera processing entirely is not possible. The CPU usage while the flashlight is on will generally hover around 25%.

## Features

* Supports entire brightness range
* Quick settings tile
* [Lock screen shortcut](#lock-screen-shortcut)
* Tiny APK with no dependencies

## Limitations

* Only supports Android 15+. For Android 12-14, use the old PixelLight 1.0 release, which has a better user experience due to fewer Android restrictions.
* On Android 15+, the quick settings panel will close when tapping the tile to turn on the flashlight. However, if the "Keep service alive" option is enabled, then this only happens the first time the tile is toggled after a reboot. This keeps the foreground service running indefinitely, but does not impact battery life because the service is completely idle and not executing any code. The mandatory notification can be disabled from Android's settings if desired.

## Permissions

The `CAMERA` permission is required because Pixel's private API for high brightness modes is only accessible when using the camera as a camera, not when using the camera as a flashlight with the official Android 13+ APIs. Internally, PixelLight is taking a picture every time the flashlight is turned on or the brightness is changed. These exist only in memory and are never saved to disk.

The `FOREGROUND_SERVICE` and `POST_NOTIFICATIONS` permissions are required to allow the flashlight to remain on while the app is in the background. They are also required for the quick settings tile to work.

PixelLight does not and will never have the `INTERNET` permission.

## External control

An external app can change the flashlight state by launching PixelLight's `ToggleActivity` via an intent. By default, this will toggle the flashlight state between on and off.

This activity accepts an optional integer parameter named `brightness`:

* `== -4`: Decrease the brightness by an unspecified incremental amount (clamped to minimum brightness). This has no effect when turned off.
* `== -3`: Increase the brightness by an unspecified incremental amount (clamped to maximum brightness). This has no effect when turned off.
* `== -2`: Toggle between on (at the user's saved brightness) and off. This is the default behavior when the parameter is not specified.
* `== -1`: Turned on at the user's saved brightness.
* `== 0`: Turn off.
* `> 0`: Set to specific brightness (clamped to maximum brightness). This changes the saved brightness.
* If the value is anything else, the intent is ignored.

## Lock screen shortcut

Android currently has no builtin way to set custom lock screen shortcuts. To use PixelLight with a lock screen shortcut, it's necessary to either set it as the default note taking app or override the QR code scanner shortcut. The note taking app approach is preferred since it doesn't result in janky animations or black screen issues.

### Set as default note taking app

Android currently doesn't enable the note taking app role by default. It must first be enabled via the `Force enable Notes role` setting in Android's developer options (underneath the `Apps` heading).

Then, reboot and set PixelLight as the default note taking app in Android's Settings -> Apps -> Default apps -> Notes app.

The `Note-taking` lock screen shortcut will now launch PixelLight.

### Override QR code scanner

**NOTE**: Android 16 [no longer grants `adb shell` the necessary permissions for this](https://android.googlesource.com/platform/frameworks/base/+/0eee1eddc401d365c76b4c21c6770b7735ecdefb%5E%21/), although it still works with root access. Unfortunately, if this config option was previously overridden on an unrooted device running Android <16, there is no way to undo that without a factory reset.

Run from within a root shell:

```bash
device_config override systemui default_qr_code_scanner com.chiller3.pixellight/.ToggleActivity
```

After rebooting, the QR code scanner lock screen shortcut and quick settings tile will toggle the flashlight instead of launching the system QR code scanner app.

To change this setting back to the default, run:

```bash
device_config clear_override systemui default_qr_code_scanner
```

and reboot.

The current setting can be found with:

```bash
device_config get systemui default_qr_code_scanner
```

`null` means the QR code scanner is not overridden.

## Persistent notification

Android 14 and newer [no longer allow](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications) regular apps to prevent persistent notifications from being dismissed. To work around this, PixelLight will automatically show the persistent notification again whenever it is dismissed.

To prevent the notification from being dismissible in the first place (eliminating the UI jank from reshowing the notification), use adb to grant the `SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS` appops permission:

```bash
adb shell appops set com.chiller3.pixellight SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS allow
# To undo the change, change "allow" to "default".
```

Also, if the persistent notification is not desired, it can be disabled from Android's settings by turning off the two "Background services" notification channels for PixelLight. Syncthing will continue to run as normal even if the notification is not visible as long as the overall notification permission is still granted.


## Verifying digital signatures

First, use `apksigner` to print the digests of the APK signing certificate:

```
apksigner verify --print-certs PixelLight-<version>-release.apk
```

Then, check that the SHA-256 digest of the APK signing certificate is:

```
03a9ed333be772cf612af84fc4bf2cc95428ff5a10c057d3b60d86b0f8fec2c3
```

Alternatively, if `apksigner` is not installed, the APK files can be verified against the external `.apk.sig` SSH signatures using [the steps here](https://github.com/chenxiaolong/chenxiaolong/blob/master/VERIFY_SSH_SIGNATURES.md).

## Building from source

PixelLight can be built like most other Android apps using Android Studio or the gradle command line.

To build the APK:

```bash
./gradlew assembleDebug
```

The APK will be signed with the default autogenerated debug key.

To create a release build with a specific signing key, set the following environment variables:

```bash
export RELEASE_KEYSTORE=/path/to/keystore.jks
export RELEASE_KEY_ALIAS=alias_name

read -r -s RELEASE_KEYSTORE_PASSPHRASE
read -r -s RELEASE_KEY_PASSPHRASE
export RELEASE_KEYSTORE_PASSPHRASE
export RELEASE_KEY_PASSPHRASE
```

and then build the release APK:

```bash
./gradlew assembleRelease
```

## Contributing

Bug fix and translation pull requests are welcome and much appreciated!

However, aside from that, PixelLight will only ever support Google Pixel devices and is intentionally featureless. I am unlikely to implement any new features.

## License

PixelLight is licensed under GPL-3.0-only. Please see [`LICENSE`](./LICENSE) for the full license text.
