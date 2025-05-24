<!--
    When adding new changelog entries, use [Issue #0] to link to issues and
    [PR #0] to link to pull requests. Then run:

        ./gradlew changelogUpdateLinks

    to update the actual links at the bottom of the file.
-->

### Unreleased

* Avoid needing to unlock the device when turning the flashlight on while locked ([Issue #12], [PR #13])
* Add support for toggling flashlight from other apps ([Issue #12], [PR #13])
    * See the [documentation](./README.md#external-control) for details on which intents are accepted.
    * This also allows [toggling the flashlight](./README.md#lock-screen-shortcut) via a lock screen shortcut.

### Version 2.1

* Enable predictive back gestures ([PR #6])
* Fix incorrect scale factor in icon and rebase off latest material flashlight icon ([PR #7], [PR #8])
* Add new option to keep the foreground service alive ([Issue #9], [PR #10])
  * This is a partial bypass for Android 15's restrictions. When the option is enabled, the quick settings tile works on the lock screen again (without unlocking the device first) and the quick settings panel no longer auto-closes. However, this only works after the tile has been toggled once after each reboot.
* Update build script dependencies ([PR #11])

### Version 2.0

* Add support for (only) Android 15 ([Issue #3], [PR #4])
    * Due to new Android restrictions, the user experience for the quick settings tile is worse now. On the lock screen, the tile now requires unlocking the device. Tapping the tile will also always close the quick settings panel.
    * Support for Android 12-14 has been removed to simplify the code. Android 14, in particular, required some nasty workarounds to make the quick settings tile work well. For older Android versions, continue using PixelLight 1.0.
* Update checksum for `tensorflow-lite-metadata-0.1.0-rc2.pom` dependency ([PR #1])
* Target API 35 ([PR #2])
* Update build script dependencies ([PR #5])

### Version 1.0

* Initial release

<!-- Do not manually edit the lines below. Use `./gradlew changelogUpdateLinks` to regenerate. -->
[Issue #3]: https://github.com/chenxiaolong/PixelLight/issues/3
[Issue #9]: https://github.com/chenxiaolong/PixelLight/issues/9
[Issue #12]: https://github.com/chenxiaolong/PixelLight/issues/12
[PR #1]: https://github.com/chenxiaolong/PixelLight/pull/1
[PR #2]: https://github.com/chenxiaolong/PixelLight/pull/2
[PR #4]: https://github.com/chenxiaolong/PixelLight/pull/4
[PR #5]: https://github.com/chenxiaolong/PixelLight/pull/5
[PR #6]: https://github.com/chenxiaolong/PixelLight/pull/6
[PR #7]: https://github.com/chenxiaolong/PixelLight/pull/7
[PR #8]: https://github.com/chenxiaolong/PixelLight/pull/8
[PR #10]: https://github.com/chenxiaolong/PixelLight/pull/10
[PR #11]: https://github.com/chenxiaolong/PixelLight/pull/11
[PR #13]: https://github.com/chenxiaolong/PixelLight/pull/13
