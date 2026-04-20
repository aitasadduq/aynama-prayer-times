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

## GDPR Art. 9 compliance

Religious belief is special-category data. Since we never send it off-device, Art. 9 processing conditions do not apply beyond the user's own consent to install. In-app affordances required:

- **Data export:** JSON dump of profiles + Qaza history + settings. One-tap from Settings.
- **Data delete:** Clear all profiles / clear Qaza / factory reset. Each as separate option.
- **Explicit opt-in for Qaza tracking.** Off by default. Users who never enable it never get asked about it.
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
- Play Store **Data Safety form:** tick "No data collected" + "No data shared." Declare `SCHEDULE_EXACT_ALARM` usage reason in Play Console.

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
- **v3 (Quran):** Quran text bundled in-app; no download. Audio recitation is still unresolved — if we stream, treat as v4 decision with its own privacy review.
- **v4 (maybe):** optional cloud sync of profiles + Qaza across devices. Requires: explicit user account, end-to-end encryption, self-hostable server, separate privacy review.

## Blocks on first public release

- [ ] iOS `PrivacyInfo.xcprivacy` included in app bundle.
- [ ] Android Data Safety form filled on Play Console.
- [ ] In-app Privacy page reachable from Settings.
- [ ] Data export + delete affordances implemented.
- [ ] Location permission copy reviewed for clarity + tone.
- [ ] Zero third-party analytics SDKs in v1 build (verified via dependency audit).
