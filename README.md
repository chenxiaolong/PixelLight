# PixelLight

<img src="app/images/icon.svg" alt="app icon" width="72" />

![latest release badge](https://img.shields.io/github/v/release/chenxiaolong/PixelLight?sort=semver)
![license badge](https://img.shields.io/github/license/chenxiaolong/PixelLight)

PixelLight is a bare-bones flashlight app for Google Pixel devices that can access higher brightness levels than what is typically allowed by the standard Android 13+ torch APIs.

It allows access to the same brightness levels as Google Magnifier, except without the excessive 200% CPU usage due to constant camera processing. However, due to how the Pixel private API works, avoiding camera processing entirely is not possible. The CPU usage while the flashlight is on will generally hover around 25%.

## Features

* Supports entire brightness range
* Quick settings tile
* [Lock screen shortcut](#lock-screen-shortcut)
* Tiny APK with no dependencies

## Limitations

* Only supports Android 15+. For Android 12-14, use the old PixelLight 1.0 release, which has a better user experience due to fewer Android restrictions.
* On Android 15, the quick settings panel will close when tapping the tile to turn on the flashlight. Additionally, on the lock screen, the background will turn black until the status bar is tapped. These issues are not fixable due to Android 15's new restrictions on starting foreground services.

  However, if the "Keep service alive" option is enabled, then these issues only happens the first time the tile is toggled after a reboot. This keeps the foreground service running indefinitely, but does not impact battery life because the service is completely idle and not executing any code. The mandatory notification can be disabled from Android's settings if desired.

## Permissions

The `CAMERA` permission is required because Pixel's private API for high brightness modes is only accessible when using the camera as a camera, not when using the camera as a flashlight with the official Android 13+ APIs. Internally, PixelLight is taking a picture every time the flashlight is turned on or the brightness is changed. These exist only in memory and are never saved to disk.

The `FOREGROUND_SERVICE` and `POST_NOTIFICATIONS` permissions are required to allow the flashlight to remain on while the app is in the background. They are also required for the quick settings tile to work.

PixelLight does not and will never have the `INTERNET` permission.

## External control

An external app can change the flashlight state by launching PixelLight's `ToggleActivity` via an intent. By default, this will toggle the flashlight state between on and off.

This activity accepts an optional integer parameter named `brightness`:

* If the value is -2, the flashlight is toggled between on (at the user's saved brightness) and off. This is the default behavior when the parameter is not specified.
* If the value is -1, the flashlight is turned on at the user's saved brightness.
* If the value is 0, the flashlight is turned off.
* If the value is positive, the flashlight is turned on at the specified brightness. If the value is out of range, it is automatically clamped to the maximum brightness. This does not change the user's brightness preference.
* If the value is anything else, the intent is ignored.

## Lock screen shortcut

Android currently has no builtin way to set custom lock screen shortcuts. To use PixelLight with a lock screen shortcut, it's necessary to either set it as the default note taking app or override the QR code scanner shortcut. The note taking app approach is preferred since it doesn't result in janky animations or black screen issues.

### Set as default note taking app

Android currently doesn't enable the note taking app role by default. It must first be enabled via the `Force enable Notes role` setting in Android's developer options (underneath the `Apps` heading).

Then, reboot and set PixelLight as the default note taking app in Android's Settings -> Apps -> Default apps -> Notes app.

The `Note-taking` lock screen shortcut will now launch PixelLight.

### Override QR code scanner

Run:

```bash
adb shell device_config override systemui default_qr_code_scanner com.chiller3.pixellight/.ToggleActivity
```

After rebooting, the QR code scanner lock screen shortcut and quick settings tile will toggle the flashlight instead of launching the system QR code scanner app.

To change this setting back to the default, run:

```bash
adb shell device_config clear_override systemui default_qr_code_scanner
```

and reboot.

The current setting can be found with:

```bash
adb shell device_config get systemui default_qr_code_scanner
```

`null` means the QR code scanner is not overridden.

## Verifying digital signatures

First, use `apksigner` to print the digests of the APK signing certificate:

```
apksigner verify --print-certs PixelLight-<version>-release.apk
```

Then, check that the SHA-256 digest of the APK signing certificate is:

```
03a9ed333be772cf612af84fc4bf2cc95428ff5a10c057d3b60d86b0f8fec2c3
```

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

PixelLight is licensed under GPLv3. Please see [`LICENSE`](./LICENSE) for the full license text.
