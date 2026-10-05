# Protocol and classification

FirmPulse independently implements these public Samsung endpoints:

```text
https://fota-cloud-dn.ospserver.net/firmware/{CSC}/{MODEL}/version.xml
https://fota-cloud-dn.ospserver.net/firmware/{CSC}/{MODEL}/version.test.xml
https://doc.samsungmobile.com/{MODEL}/{CSC}/doc.html
https://security.samsungmobile.com/workScope.smsb
```

Model and CSC inputs are normalized and validated before becoming URL segments. `S926U1` can become `SM-S926U1`; a trailing `/DS` is removed. Variant selection changes only recognized model-number shapes. Manual full model entry remains available. Suggestions are convenience lists, not a compatibility database.

The XML parser reads `versioninfo/firmware/version/latest`, its optional `o` Android-version attribute and `upgrade/value` entries. It retains raw latest and feed order, handles absent optional fields, bounds input size and rejects unsafe XML constructs. Each request has its own result status.

The optional Samsung release-document wrapper is resolved to its English document when available, otherwise the default supported document, restricted to HTTPS on the same host. Parsing supplies the device name, released build history, Android version, release dates, security patches and notes. English and Korean metadata labels are parsed; original notes are not machine-translated. Documentation never overwrites the feed's latest value. Its AP build history supplements the known-released set for test classification. Security cadence is found by exact documented device-name matching, and is separate from a promised support end date.

## Test selection in 0.5.0

Known released builds are excluded by exact AP equality against released latest/history and documented release history, or exact matching against released hashes. Different CSC/CP strings do not turn the same released AP into a test build. An older distinct build can be a rollback test candidate, so relative age alone no longer discards it. Such candidates remain unconfirmed; explicit known-released AP equality still excludes released duplicates. Original entries are retained for inspection/export.

A valid eligible visible or locally matched test `latest` may be labelled latest. With no usable latest field, qualifying distinct entries are labelled test candidates or beta-style candidates. Candidate ranking uses recognized bootloader, branch, encoded year/month and revision; it does not establish Samsung's unpublished latest build. An unresolved headline remains unresolved instead of borrowing a released build. Beta-style code patterns do not prove public beta enrollment.

MD5 and HMAC-SHA256 are not reversible encryption. The client generates candidate full AP/CSC/CP strings and compares their identifiers exactly. The older 32-character format uses MD5. The public CheckFirm patch at https://github.com/Bluesion/CheckFirm/pull/8 documents support for 64-character HMAC-SHA256 using the local protocol constant `fcjimts25@%` and ASCII input. This is an identifier calculation constant, not server authentication or an account credential. Ordinary SHA-256 is not equivalent. Length chooses which supported method to try; only an exact match verifies a name.

Automatic search is limited to 250,000 candidate strings and deeper search to 1,500,000; a mixed feed shares one candidate budget even if both hashes are calculated. Mac instances are reused within a search and reset by doFinal, avoiding per-candidate key setup. Full known released/history names and cached candidates are compared against both identifier sets first. Verified targets are excluded from generated discovery. A usable latest hash restricts candidate discovery to that target; direct known-name comparison still identifies other released overlaps.

Candidate generation uses up to sixteen valid seeds, covering aligned combinations first, then independently dated/revised components and retained component values. Coverage remains finite. Unknown hashes, unsupported prefixes and names outside this search space remain hidden. Empty latest fields, absent Android metadata and missing value count/size attributes do not invalidate the feed. Manual complete firmware strings are trimmed, uppercased and checked against both formats. All saved names are revalidated before display/classification/export.

Comparisons use sets of entries and the explicit latest/Android fields; entry-order changes alone do not produce alerts. Only successful responses replace per-feed baselines. Observation dates are local, not global discovery dates. Background notifications use Android JobScheduler and never run at a guaranteed exact hour.

## Research reference and boundary

CheckFirm 11.1.8 at commit `9937e84` (https://github.com/Bluesion/CheckFirm) was inspected to understand endpoint formats, XML fields, search controls and recovered-name verification. Inspected files included FWFetcher, Tools, MainViewHolder, SearchDialog and SherlockViewModel. CheckFirm's optional community service supplies additional recovered strings/discovery metadata. FirmPulse does not access it or ship CheckFirm application source, assets, patched APKs, Firebase configuration or private keys.

The user's earlier SM-S926U/XAA screenshot showed released `S926USQS6DZI1/S926UOYN6DZI1/S926USQS6DZI1`, Android 16 and 273 test entries with empty latest. Their actual hidden entry strings were not supplied. Other screenshots are observations for those model/CSC pairs, not development-environment tests.

Fresh research retrieved successful HTTP 200 XML from the existing endpoint for SM-S926N/KOO (242 entries), SM-S928B/INS (293), and SM-F766U/TMB (159). Each response had an empty latest field, bare value elements and one 64-character identifier mixed with older entries. The long identifier was the same across these models and is not assigned a build name. The normal app request header succeeded in a controlled check while a browser-style header received HTTP 403; this does not establish a universal header/network rule.

CheckFirm contribution #8 (ducthoe, April 9, 2026) proposes flexible test XML parsing and HMAC-SHA256 matching while retaining the existing hostname/path. It remains unmerged as of this investigation. This is primary implementation evidence, not an official Samsung protocol specification or proof of a universal January 2026 rollout date. The older flexible parser versus newer fixed data model offers a plausible NULL explanation; no claim is made that every NULL has the same cause.

## Uploaded APK inspection

The supplied base APK identifies CheckFirm 11.1.8 / version code 56. DEX inspection found `/version.test.xml` in method `Lel1;.e`, recovered-name/discovery keys in `Lel1;.k`, and MD5 in `Ltt6;.l`. The exact matching release source at commit `9937e84` explains these paths: test latest/values are fetched directly; missing names can be filled from Firestore `clue` or `firmware_decrypted`; default search can reuse released latest when no visible clue exists. Its Sherlock script verifies full AP/CSC/CP combinations by MD5 while component positions can vary independently. FirmPulse independently implements the bounded local matching workflow. It does not reuse the patched APK or its service credentials/configuration.

The user's CheckFirm screenshot shows DZDR for SM-S926N/KOO. Samsung's retrieved Korean release history includes AP `S926NKSUEDZDR`. This establishes a released reference with that short code, not a confirmed new unreleased test build or proof that every community clue is false. The retrieved N/KOO test list includes the exact MD5 for S926NKSUEDZDR/S926NOKREDZDR/S926NKSUEDZDR. Presence in that list does not override the released-history reference. A full current CheckFirm string is still required for exact same-device name parity.
