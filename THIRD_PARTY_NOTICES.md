# Acknowledgements and third-party notices

## CheckFirm

CheckFirm by Bluesion: https://github.com/Bluesion/CheckFirm

Its 11.1.8 source was used to understand the existing firmware metadata protocol. CheckFirm application source, graphics, branding, private service configuration, and patched APKs are not included. The independently written client acknowledges this research reference. The additional public protocol research by ducthoe in https://github.com/Bluesion/CheckFirm/pull/8 documents newer HMAC-SHA256 identifiers and flexible test-list parsing. FirmPulse independently implements those protocol facts and credits the contributor; it does not redistribute the Kotlin patch. CheckFirm's repository identifies its own application license as GPL-3.0.

## Gradle wrapper

The standard Gradle wrapper scripts and JAR are included to make the project buildable. Gradle is licensed under Apache License 2.0. See `third-party/Gradle-LICENSE` and `third-party/Gradle-NOTICE`.

## Build/runtime dependencies

The Android Gradle Plugin and Kotlin are downloaded by Gradle during a build. The app uses the Android framework and Kotlin standard library. Kotlin is licensed under Apache License 2.0. Dependency copyrights and notices remain with their respective owners.

## Test-only dependencies

JUnit 4.13.2 and Robolectric 4.16 are used only for development checks, not packaged in the APK. JUnit uses the Eclipse Public License 1.0 and Robolectric uses the MIT License; their dependencies retain their own notices. Tests download Android runtime artifacts for supported simulated SDKs. The source includes test declarations rather than vendored copies of these dependencies.

## Samsung metadata

Firmware data, release notes and security-support information are requested from Samsung's public services. Samsung names and trademarks belong to their owners. FirmPulse does not imply affiliation or authorization to flash a firmware build. Full Samsung documentation pages are not bundled as app assets.
