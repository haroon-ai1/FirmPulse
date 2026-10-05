# Implemented UI previews

These images render FirmPulse's native Android views using Robolectric native graphics at 393 × 852 dp. They are simulated layouts, not photographs or live-phone screenshots.

The DZJ3 test candidate and its hidden identifier are synthetic regression fixtures. Released dates are fixture values taken from Samsung documentation. None of these images proves a live test build. `home-unresolved.png` demonstrates the fix that refuses to repeat a released build as test firmware.

The newer-matched-tools render uses a synthetic HMAC-matched name to exercise the newer local validation path. The APK contains no seeded test results. Older-beta and detected-phone images exercise warnings and clean launch; those values are also test-only fixtures. Tests write these preview images during `:app:testDebugUnitTest`.

1.0.0 includes the floating frosted dock, compact cards, reduced-motion navigation, collapsed/paged hidden entries and released-history fallback. It retains dark/light search dialogs, system-bar padding and long-identifier classification regression renders. The long hex identifier models the reported screenshot; it is never presented as a verified firmware name.
