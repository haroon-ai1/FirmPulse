# Releasing FirmPulse

Repository: https://github.com/haroon-ai1/FirmPulse

## Keep these details

- Application ID: `io.github.haroonjadoon.firmscope`
- First public version: `1.0.0`
- Current version code: `101` (display version remains `1.0.0`)
- Permanent release key alias: `firmpulse`
- Public fingerprints and APK checksum: `docs/RELEASE-IDENTITY.md`
- Private keystore and passwords: the separately supplied **PRIVATE signing backup**, kept outside this repository.

Keep the private backup in secure storage and a second private backup location. Never upload it to GitHub, releases, issues or pull requests. Public certificate fingerprints and the APK checksum are safe to publish. The permanent key is required for future updates; replacing it breaks the update path for existing installations.

## Put the source on GitHub

1. Extract the repository ZIP. Place the contents of its `FirmPulse` folder at the root of your GitHub repository, with `README.md`, `app/` and `gradlew` directly at the root.
2. Commit the source and documentation. Do not commit local SDK files, build caches, APKs or signing material.
3. Create a GitHub Release tagged `v1.0.0`, using `docs/RELEASE-NOTES-1.0.0.md` as the description.
4. Attach `FirmPulse-1.0.0.apk` and the public release-details text file supplied with it.
5. Keep S-series and hidden-name limitations visible in the release description. Add real phone screenshots when available; test renders use sample data.

The delivered APK is signed and non-debuggable. Automated checks and simulated rendering do not establish physical-phone animation frame rate or current beta recovery on every model. Perform `docs/PHONE_TEST_CHECKLIST.md` and record the actual tested pairs.

## Build the next signed version

Increase both `versionCode` and `versionName` in `app/build.gradle.kts`, and update `FirmwareCore.CLIENT_VERSION`. Keep the same application ID and permanent signing key.

Set these environment variables privately in the build environment:

| Variable | Value |
|---|---|
| `FIRMPULSE_KEYSTORE` | Absolute path to the retained `FirmPulse-release.p12` |
| `FIRMPULSE_KEYSTORE_PASSWORD` | Store password from the private backup |
| `FIRMPULSE_KEY_ALIAS` | `firmpulse` |
| `FIRMPULSE_KEY_PASSWORD` | Key password from the private backup |

Then run:

```bash
bash scripts/test-core.sh
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
```

Signed APK: `app/build/outputs/apk/release/app-release.apk`.

Alternatively use Android Studio's Generate Signed App Bundle / APK wizard and select the same retained key. Do not create a new key for each version. Verify the APK with Android SDK `apksigner verify --verbose --print-certs`, compare its certificate SHA-256 with the retained public identity, and calculate its file SHA-256 before attaching it to a release.

Earlier development APKs cannot be updated by this permanent-key release. Users of those builds should export saved devices before uninstalling and reimport them after installing 1.0.0. Future releases signed with this key can use the normal Android update path.

Android signing reference: https://developer.android.com/studio/publish/app-signing
