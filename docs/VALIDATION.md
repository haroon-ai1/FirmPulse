# FirmPulse 1.0.0 validation

Checks completed 2026-10-05. The app is packaged as a permanent-key signed release. Firmware matching remains bounded and does not guarantee the latest hidden S-series name.

| Check | Result |
|---|---|
| Standalone firmware suite | 118 checks passed |
| Debug Android runtime simulation | 38 tests, zero failures/errors/skips |
| Release Android runtime simulation | Same 38 tests, zero failures/errors/skips |
| Debug and release lint | Zero errors and 21 advisory warnings per variant |
| Signed release build | Successful; APK Signature Scheme v2 verified |
| APK identity | Version 1.0.0/code 101; Android 9 minimum; Haroon/FirmPulse RSA 4096-bit release certificate |
| Release manifest | Non-debuggable; no fixture results or signing secrets as app assets |
| Latest hidden S-series recovery | Not established; limitation shown in the interface |
| Physical-phone animation/battery/background checks | Pending separate device review |

Tests cover exact hash verification, released/test separation, unsupported/missing values, known-date priority, older encoded month and comparable revisions, beta month handling, unrelated families and unknown dates. Published dates take priority when both exist; failed released data cannot create an age claim.

Android tests cover clean first launch, detected own Samsung model without guessed CSC, system light/dark default, persisted manual theme choices, S-series and older-beta notices, owner links, star link, absence of preview copy in About, model endings, storage and backup validation, jobs, sharing, dark/light dialog readability, Android 9, large fonts, gesture insets, reduced motion, rapid navigation, hidden paging and released-history fallback.

Native fixture renders were reviewed for rounded glass clipping, tint contrast, blue/green highlights, notice readability, clean launch and accessible navigation. They are simulated Android layouts, not live phone screenshots or proof of a beta build.

The delivered public source excludes build/cache output, local SDK paths, patched third-party APKs, private service configuration, signing keys and passwords. Public certificate identity is in RELEASE-IDENTITY.md. The private permanent-key backup is delivered separately. Earlier development certificates cannot update to this new certificate in place.

The previous public protocol research and captured-feed checks are described in PROTOCOL.md. Those dated tests do not establish today's latest hidden firmware. This release adds clearer classification warnings rather than claiming that S-series recovery is fixed.

The revised build additionally checks profile ordering and centering, all top/bottom links, the five-second one-shot star prompt, foreground/modal deferral, restart persistence, failed-check behavior, and all three Home share buttons. Shared PNG dimensions, content URI, MIME type, ClipData and read grants are checked. The actual branded beta/released export and updated Settings render were visually reviewed. The same permanent signing key is retained.
