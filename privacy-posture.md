# Privacy Posture — aynama-prayer-times

Sub-spec of `architecture-design.md`. Covers data collection, retention, and regulatory posture.

## Core principle

**The app has no server in v1–v3.** Everything runs on-device. Location stays on-device. Qaza history stays on-device. Multi-profile data stays on-device. This is load-bearing for the "open, offline, trustworthy" positioning and de-risks nearly every privacy question.

## Data collected (all on-device only)

| Category | Source | Retention | Purpose |
|---|---|---|---|
| Current location (coarse / fine) | GPS or manual entry | Until user changes it | Prayer time calculation + qibla |
| Saved profiles (name + coords + method + madhab) | User input | Until user deletes profile | Multi-profile switching |
| Notification preferences (per-prayer sound / pre-reminder) | User input | Until user edits | Adhan / reminder scheduling |
| Qaza tracking (if enabled) | User input | Until user deletes | Makeup-prayer count |
| App usage state (last screen, theme) | App internal | Until uninstall | UX continuity |

**Never collected:** name, email, phone, contacts, device IDs, advertising IDs, crash dumps with PII, prayer-completion telemetry.

### Android v1 — what is actually stored (2026-09-23)

| Data | Where | Notes |
|---|---|---|
| Profiles: name, coordinates, method, madhab, time zone and its toggle, Hijri adjustment | Room database `aynama.db` | Stores coordinates only; the city name isn't kept. |
| Prayer marks: on time, qaḍā, missed | Room `qaza_entries` | Written only when the user marks a prayer; nothing is auto-recorded. The Tracker is **always on**, not opt-in (see GDPR below). |
| Notification settings (global, and per profile and prayer), the Ramadan banner's dismissed Hijri year, the one-time battery-prompt flag | SharedPreferences `aynama_prefs` | Nothing about the last screen or theme is stored. |
| Each widget's chosen profile | Glance widget state (DataStore) | Cleared by the system when the widget is removed. |
| Live device location | Memory only (Qibla screen) | Used for the bearing and distance; never written to disk. |

## GDPR Art. 9 compliance

Religious belief is special-category data. Since we never send it off-device, Art. 9 processing conditions do not apply beyond the user's own consent to install. *(Android v1 can send data off the device through the platform geocoder and Auto Backup — see "Two routes off the device" below — so revisit this reasoning once those are decided.)* In-app affordances required:

- **Data export:** JSON dump of profiles + Qaza history + settings. One-tap from Settings.
- **Data delete:** Clear all profiles / clear Qaza / factory reset. Each as separate option.
- **Explicit opt-in for Qaza tracking.** Off by default. Users who never enable it never get asked about it.
  - **Android v1 doesn't meet this yet.** The Tracker tab and the mark-prayer sheet on Home are always available, with no opt-in. Entries are only created when the user marks a prayer, and the "outstanding" count is derived from them. Either add the opt-in or revise this requirement (TODOS.md, design decisions).
- **Privacy page in app** with plain-language summary (target: readable at 8th-grade level).

## iOS PrivacyInfo.xcprivacy (required since May 2024)

Required manifest entries:

```xml
<key>NSPrivacyCollectedDataTypes</key>
<array/>  <!-- empty: we collect nothing off-device -->

<key>NSPrivacyTracking</key>
<false/>

<key>NSPrivacyAccessedAPITypes</key>
<array>
  <dict>
    <key>NSPrivacyAccessedAPIType</key>
    <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
    <key>NSPrivacyAccessedAPITypeReasons</key>
    <array><string>CA92.1</string></array>  <!-- app-only -->
  </dict>
  <dict>
    <key>NSPrivacyAccessedAPIType</key>
    <string>NSPrivacyAccessedAPICategoryDiskSpace</string>
    <key>NSPrivacyAccessedAPITypeReasons</key>
    <array><string>85F4.1</string></array>  <!-- write/read user-content -->
  </dict>
</array>
```

**Location permission copy (iOS):** "aynama uses your location to calculate prayer times and qibla direction. Your location stays on this device."

## Android equivalents

- `POST_NOTIFICATIONS` runtime permission (API 33+).
- `USE_EXACT_ALARM` — declared in manifest (no user grant required). Prayer apps qualify under the alarm/clock exemption. Inexact alarms are unacceptable for prayer notification timing. Play Console requires exact alarm category declaration.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — coarse is sufficient for prayer time calculation (<1 arcminute error); prefer coarse.
- Play Store **Data Safety form:** tick "No data collected" + "No data shared." *(Android v1: only once the Auto Backup and geocoder decisions below are made.)* Declare `SCHEDULE_EXACT_ALARM` usage reason in Play Console.

**Android v1 manifest (2026-09-23):**

*Permissions declared:*
- `POST_NOTIFICATIONS` — requested on first launch on Android 13+.
- `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` — the app falls back to inexact alarms when exact alarms aren't allowed.
- `RECEIVE_BOOT_COMPLETED`, `VIBRATE`.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` — for adhan playback.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — requested once, right after notifications are allowed; so only on Android 13+ after a fresh grant (PR #34 A3).
- `ACCESS_COARSE_LOCATION` **and** `ACCESS_FINE_LOCATION`. The profile sheet's "Use current location" asks for coarse. The Qibla screen asks for **fine** alone on first open. Android offers the approximate choice only when both are requested together, and some Android 12 releases ignore a fine-only request (DS33). So "prefer coarse" holds for profiles, but not for Qibla.

*Not declared:* `INTERNET`, so the app can't open network connections itself. Confirm with the merged manifest (`:app:processReleaseMainManifest`) that no dependency adds it back.

*Two routes off the device, both through system services:*
1. **Location search and reverse geocoding** use the platform `Geocoder`, which delegates to a backend service outside the Android framework (Android reference: <https://developer.android.com/reference/android/location/Geocoder>). On most devices that backend is a network service, so the typed city query and the chosen coordinates can leave the device. Disclose this in the privacy page and the Data Safety review. Without a backend (for example, some de-Googled devices), search returns nothing.
2. **Android Auto Backup** is on: `android:allowBackup="true"`, with no backup or data-extraction rules. Auto Backup uploads app data — including databases and shared preferences — to the user's Google Drive backup (Android guide: <https://developer.android.com/identity/data/autobackup>). The same data also moves to a new phone in a device-to-device transfer, and on some manufacturers' devices `allowBackup="false"` stops the Google Drive backup but not that transfer (Android 12 behaviour changes: <https://developer.android.com/about/versions/12/behavior-changes-12>). Excluding `aynama.db` from both takes `android:dataExtractionRules` on Android 12+ and `android:fullBackupContent` below it. A restore would also bring back the one-time battery-prompt flag in `aynama_prefs` (`MainActivity.kt:59`), so a restored phone is never asked for the battery-optimisation exemption. The cloud backup would carry Qaza history and profiles off the device, which contradicts "Qaza history stays on-device" above. Decide before release: turn backup off, exclude the database, or document it as a user-controlled backup.

## Analytics posture

**v1: zero analytics. Zero crash reporting.**

Rationale: every analytics SDK sends at least a device ID + IP. Every crash reporter captures stack traces that may include user data. For a religious app, zero is the only defensible posture at launch.

**Later (v4+, if ever):** if we add analytics, it must be:
- Self-hosted (no Google / Firebase / Mixpanel / Amplitude).
- Anonymous (no device ID, no IP retention).
- Opt-in (off by default, off for installs <14 days, explicit privacy-page explanation).
- Separable (user can export/delete their own contribution if any unique ID exists).

Plausible stack later: Umami or PostHog self-hosted, event counts only, no user-scoped events.

## Network posture

- **v1:** zero network calls after install. Zero.
  - **Android v1:** the app itself makes no network calls; its manifest doesn't declare `INTERNET`. The platform geocoder and Auto Backup can still send data off the device on its behalf (see Android equivalents above).
- **v3 (Quran):** Quran text bundled in-app; no download. Audio recitation is still unresolved — if we stream, treat as v4 decision with its own privacy review.
- **v4 (maybe):** optional cloud sync of profiles + Qaza across devices. Requires: explicit user account, end-to-end encryption, self-hostable server, separate privacy review.

## Blocks on first public release

- [ ] iOS `PrivacyInfo.xcprivacy` included in app bundle.
- [ ] Android Data Safety form filled on Play Console.
- [ ] In-app Privacy page reachable from Settings.
- [ ] Data export + delete affordances implemented.
- [ ] Location permission copy reviewed for clarity + tone.
- [ ] Zero third-party analytics SDKs in v1 build (verified via dependency audit).
- [ ] Decide Android Auto Backup: turn off `allowBackup` (on some devices that doesn't stop device-to-device transfer), or add `dataExtractionRules` and `fullBackupContent` that exclude `aynama.db`, or disclose it.
- [ ] Disclose geocoder use for location search in the privacy page and the Data Safety form.
- [ ] Qaza tracking opt-in, as specified above, or revise that requirement.
