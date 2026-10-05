# Contributing to FirmPulse

Thank you for helping improve FirmPulse. Open an issue or fork the repository, make a focused change and submit a pull request to `haroon-ai1/FirmPulse`. The maintainer reviews and merges contributions.

## Local setup

Use JDK 17 and Android SDK 36 with Build Tools 36.0.0. Open the repository root in Android Studio. Gradle wrapper 8.14 is included.

```bash
bash scripts/test-core.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Explain the behavior you changed and how you checked it. Firmware changes need complete model/CSC examples and full-string/hash evidence, rather than short build suffixes alone. Add regression coverage for classification or matching changes. UI changes should remain usable at large text sizes and with system gestures, disabled animations and both themes.

## Firmware correctness

- Preserve exact full-string verification and released/test separation.
- Keep missing data explicit. Do not infer newest builds from hash order, branch letters alone, first-observed times or unverified guesses.
- Distinguish published release dates from encoded build months.
- Do not add private service credentials, third-party application code/assets, patched APKs or undisclosed data sources.
- Keep network requests bounded and cancellable. No account or app-owned backend is required.

## Useful issue reports

Include app version, Android version, exact model/CSC, check time, steps to reproduce and expected/actual behavior. Attach a redacted JSON report or full build strings when possible. Do not share IMEI, serial numbers, passwords or signing material.

## Signing

Contributor builds use their own debug certificate. The maintainer's permanent release key and passwords are never committed or attached to issues. An unsigned CI or local build is not the published APK.

By contributing, you agree that your independently authored contributions are distributed under the repository's MIT license. Retain applicable third-party notices.
