<!--
    When adding new changelog entries, use [Issue #0] to link to issues and
    [PR #0] to link to pull requests. Then run:

        ./gradlew changelogUpdateLinks

    to update the actual links at the bottom of the file.
-->

### Unreleased

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
[PR #1]: https://github.com/chenxiaolong/PixelLight/pull/1
[PR #2]: https://github.com/chenxiaolong/PixelLight/pull/2
[PR #4]: https://github.com/chenxiaolong/PixelLight/pull/4
[PR #5]: https://github.com/chenxiaolong/PixelLight/pull/5
