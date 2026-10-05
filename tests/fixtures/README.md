# Captured Samsung metadata fixtures

These XML responses were retrieved on 2026-10-04 UTC (2026-10-05 in Pakistan) from:

`https://fota-cloud-dn.ospserver.net/firmware/{CSC}/{MODEL}/version.test.xml`

Test pairs: SM-S926N/KOO (242 entries), SM-S928B/INS (293), and SM-F766U/TMB (159).
The released samples use the same paths with `version.xml` for SM-S926N/KOO and SM-F766U/TMB.

These are dated responses, not embedded live app results. Test lists have an empty latest
field, no count/size attributes on value entries, and both 32- and 64-character entries.
The same long entry appears across these model pairs. No recovered name is assumed for it.
Tests verify parsing and released-history overlap without labelling that entry latest.

Fixtures are used by the standalone tests only and are not packaged in the Android app.
