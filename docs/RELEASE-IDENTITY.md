# FirmPulse 1.0.0 release identity

Developer: Haroon
Repository: https://github.com/haroon-ai1/FirmPulse
Application ID: io.github.haroonjadoon.firmscope
Version: 1.0.0
Version code: 101
Minimum Android: 9 (API 28)
Target Android: 16 (API 36)
Build type: release; non-debuggable
Certificate subject: CN=Haroon, O=FirmPulse
Key: RSA 4096-bit; SHA256withRSA certificate; validity 10,000 days
Alias: firmpulse
Verified APK signature scheme: v2

## Public certificate fingerprints

SHA-256: `24d7e202a6b516157c817a5d7a1cf5937e7172f32f54a5048d9721ca18321007`

SHA-1: `1dcfe1754f6dca0585c4d03db6b67da8c7069a4f`

MD5: `d3406d00df0bef3c8eed0dde853c1374`

The SHA-256 fingerprint identifies the retained permanent release certificate. The certificate is also provided as `FirmPulse-release-certificate.der`; it contains no private signing key. SHA-1/MD5 are legacy certificate identifiers, not the recommended APK integrity checksum.

## Published APK checksum

Filename: `FirmPulse-1.0.0.apk`

File SHA-256: `fbfa045891028c436dcab652475dd1aeb130c4cffb4ea62d9dd08c05de0496c7`

This file checksum changes with each build. The signing-certificate fingerprint should remain the same for future app updates. Public fingerprints and checksums are safe to attach to the GitHub release. Keep the separately provided private signing backup and credentials outside GitHub.

Older development APKs use different certificates. Export saved devices, uninstall the old development build, install this release and import the backup. Future releases must keep this key and application ID and increase the version code.

This revised 1.0.0 build uses version code 101 and the same permanent certificate as the preceding code-100 build. It can update that release in place.
