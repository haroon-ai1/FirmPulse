# FirmPulse privacy

FirmPulse stores saved model/CSC pairs, watched devices, the latest results, successful comparison baselines, up to 100 recent checks, up to 400 activity records, local first-observed dates, verified build-name mappings and preferences in private application storage. Samsung's security-support page may be cached for 24 hours. Settings can clear history/baselines/results or matched names separately. Saved devices remain unless removed. Uninstalling or clearing app storage removes the app's private data. Android backup and device-transfer rules exclude this data; users can explicitly export/import bookmark backups.

Searches send the entered model and CSC to `fota-cloud-dn.ospserver.net` and `doc.samsungmobile.com` over HTTPS. An optional support-frequency check requests `security.samsungmobile.com`. Samsung can observe requests and the connecting IP address. Candidate generation, hashing and verification happen locally; matched names are not uploaded to a developer or community backend.

The app has no advertising, analytics, account system or developer server. It does not request IMEI, serial number, contacts, location or phone-state access. It may read Android's public model/manufacturer and build fields to prefill a Samsung model or compare the installed build. CSC is entered or selected by the user.

Declared permissions:

| Permission | Purpose |
|---|---|
| Internet | Samsung metadata and documentation |
| Network state | Check the Wi-Fi-only preference for background jobs |
| Notifications | Optional firmware-list change alerts; runtime permission on Android 13+ |
| Boot completed | Persist Android's scheduled jobs across a restart |

Alerts are off initially. When enabled, only watched devices are checked. The default Wi-Fi-only preference is on. Android controls job timing; intervals are not exact. Initial checks create a baseline without a change notification.

Export/import uses Android's file picker, with no broad storage permission. Sharing grants the chosen receiving application temporary read access to the selected cached file. Long reports can be shared as text files; result cards are PNG images. Copy/share/open-source actions happen only when selected. Exported files and receiving applications are outside the app's private storage and have their own handling policies.
