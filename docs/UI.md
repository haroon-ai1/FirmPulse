# FirmPulse interface

The five persistent tabs sit in a floating rounded dock. A short damped selection animation, interruptible directional content transitions, restrained dialog motion and subtle button compression give feedback without long waits. Reduce motion and Android's global animation setting disable decorative motion.

Android 12+ draws a small reusable snapshot of the scroll viewport behind the dock, applies blur and clips it to the capsule. Tint opacity resets each frame, keeping foreground icons and labels sharp. Older Android versions use an opaque tinted surface. Glass effects can be disabled. Bottom content padding and window insets preserve the system gesture area.

Blue test cards and green released cards use soft radial highlights, fine borders and rounded corners. Test builds remain the primary content. Details are opened on demand. Hidden entries are collapsed and paged by thirty, and release feed history is usable without release notes.

First launch contains no fixture results. Only the actual Samsung model is detected when available; CSC stays editable. The default theme follows the phone, and manual choices persist. S-series support and older-build uncertainty are visible near the relevant result. About identifies Haroon and links to his GitHub, portfolio, LinkedIn and project.

Physical-phone animation frame rate and GPU appearance are not established by the simulated renders. Use PHONE_TEST_CHECKLIST.md for that review.

Settings begins with Haroon's centered profile card and direct GitHub, Website and LinkedIn links. The repository support action is last. After the first usable foreground check, an optional one-time star prompt is due in five seconds; it persists its one-shot state and waits for the foreground and any existing dialog. Choosing either action or dismissing the prompt prevents repetition.

Share icons on test, beta and released cards produce a 1080 × 1440 PNG bearing FirmPulse, model/CSC, full build components, supplied release and patch dates, encoded month labels, check time and applicable warnings. The beta action selects that exact beta entry; released sharing includes the identified beta when available. Missing publication dates remain explicit.
