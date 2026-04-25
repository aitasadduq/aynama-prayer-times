# TODOS — aynama-prayer-times

Tracked items from plan reviews. Must-decide-before-code items are in `.gstack/projects/aitasadduq-aynama-prayer-times/ceo-plans/`.

## Design TODOs (from /plan-design-review, 2026-04-18)

- [x] ~~**Run /design-consultation before v1 UI work.**~~ → **COMPLETE** (DESIGN.md created 2026-04-18, covers color palette, typeface, iconography, app icon, brand tokens, all 6 sections)

- [x] ~~**Notification settings screen UX spec.**~~ → **COMPLETE** (DESIGN.md §15: master toggle, per-prayer rows, adhan picker with 6 voices, Imsak toggle, vibration modes, per-prayer offset/early-reminder sheets)

- [x] ~~**Prayer tracker history view spec.**~~ → **COMPLETE** (DESIGN.md §16: calendar-free flat list, per-day row expansion, 5 prayer indicators (no Sunrise), marked states: on-time / Qada / missed, week sections, soft aggregate header)

## Deferred decisions (not blocking v1 code start)

- [ ] **Monetization / sustainability model.** Pick one: donations, pay-what-you-want, freemium, paid-upfront, fully-free. Decide before v2.
- [ ] **iOS CI runner strategy.** GitHub macOS vs MacStadium vs self-hosted Mac mini vs no-iOS-until-v3. Decide before v3 (iOS phase start).
- [ ] **Quran data source.** Tanzil (chosen in legal-posture.md) vs alternative. Validate against licensing + attribution before v4 (Quran feature).
- [ ] **Gold/silver price API for zakat.** Free tier API vs scraped static value vs user-input. Decide before v5.
- [ ] **Trademark clearance on "aynama."** USPTO + EUIPO search before Play Store + F-Droid submission.
- [ ] **Project license.** MIT vs Apache 2.0. Decide before first public repo push. Lean Apache 2.0 to match Adhan upstream.
- [ ] **`USE_EXACT_ALARM` Play Console declaration.** Declare exact-alarm category (alarm/clock) in Play Console before submission. (Not a runtime permission — no user grant needed, but Play Store requires category justification.)
- [ ] **Android package name.** `com.aynama.prayertimes` — verify no conflict on Play Store before submission.

## Reviewer Concerns from design doc

- [x] ~~Reviewer Concern #1: Forbidden prayer time calculation~~ → **RESOLVED** (Adhan default sunrise marker; v1 has no forbidden-times warning; Fajr row shows as "past" after sunrise)
- [ ] **Reviewer Concern #2: Zakat nisab / Hawl logic.** v4-v5 decision.
- [x] ~~Reviewer Concern #3: Qaza tracking UX~~ → **RESOLVED** (schema: `QazaEntry(id, prayer, date, status: enum{missed, made_up, intention_to_makeup}, profile_id, updated_at)`)
- [x] ~~Reviewer Concern #4: Widget countdown strategy~~ → **RESOLVED** (Android: `RemoteViews.setChronometerCountDown()`, updates on prayer change; iOS v3: resolve before iOS notification settings)
- [ ] **Reviewer Concern #5: WearOS complication refresh model.** Decide before v2.

## Temporal Interrogation follow-ups (non-blocking, low effort)

- [x] T1 — Write minimal README before v1 code starts.
- [x] ~~T2 — Design doc language~~ → **RESOLVED** (`com.batoulapps.adhan:adhan:1.2.1`, source-vendor only if patched)
- [x] ~~T3~~ → **RESOLVED** (`test-vectors/schema.json` written; prayer-times only, Draft-07, validates method/tolerance/reference/cases structure)
- [ ] T5 — Add `tags: List<String>` or `context: ProfileContext` field to Profile schema.
- [x] ~~T6~~ → **RESOLVED** (BOOT_COMPLETED + ACTION_TIMEZONE_CHANGED BroadcastReceivers spec'd in architecture-design.md)
- [ ] T7 — Qibla uses `SensorManager.remapCoordinateSystem` for tilt-stable bearing.
- [ ] T8 — `FOREGROUND_SERVICE_SPECIAL_USE` manifest entry + Play Console justification.

## Engineering TODOs (from /plan-eng-review, 2026-04-21)

- [x] ~~**Vector generator self-tests.**~~ → **COMPLETE** (`scripts/test_generator.py`, 9 tests pass). Golden values corrected to Adhan 1.2.1 actual output (arch-design.md had stale PrayTimes.py values): fajr=05:10, sunrise=06:24, dhuhr=12:29, asr_shafii=15:53, asr_hanafi=16:50, maghrib=18:32, isha=19:42. `architecture-design.md` golden values table needs updating separately.

- [ ] **Run all test vectors in CI.** Load all 12 cities from `test-vectors/schema.json` in `android.yml` CI; parse + loop all methods (MWL, ISNA, UMM_AL_QURA, etc.). Assert each prayer time within ±1 min of test vector. Replace hardcoded Makkah-only tests in `AdhanWrapperTest.kt`. Required v1 gate (before launch) to catch Adhan upstream regressions.

- [ ] **Adhan-Swift version pin + parity check.** Before v3 iOS work: pin Adhan-Swift to a specific release in `scripts/reference-versions.json`; verify all 12 test-vector cities agree between Adhan-Swift and Adhan-Kotlin within ±1 min; add parity check to ios.yml CI. Required pre-v3 gate.

- [ ] **adhan-test-vectors companion repo ownership protocol.** Before v1 launch: document in companion repo README: (1) how to trigger vector regeneration on Adhan upstream release (GitHub Actions manual dispatch); (2) who reviews PrayTimes.py vs Adhan disagreements; (3) process for syncing updated vectors back to main repo.

- [ ] **iOS notification limit analysis.** Before v3 iOS notification settings work: OS limit = 64 pending. 5 prayers × 7 days = 35 (fine). + advance-notice reminders = 70 (overflow). Options: (a) cap at 6 days; (b) background-app-refresh regeneration at 5-day mark; (c) alternating schedule. Resolve before speccing advance-notice for iOS.

## Supply-chain / security

- [ ] **Verify Adhan license (MIT vs Apache 2.0).** Check `LICENSE` file in batoulapps/adhan-java + batoulapps/adhan-swift on GitHub. Update `legal-posture.md` compliance framework accordingly — MIT = copyright notice only; Apache 2.0 = NOTICE file + state-changes. Maven POM for adhan2-jvm says MIT; adhan:1.2.1 POM tag is empty. Blocks first public release. (flagged /plan-eng-review 2026-04-19)

- [ ] `gradle --write-verification-metadata sha256` after first build; commit `gradle/verification-metadata.xml`.
- [ ] Dependency audit: zero third-party analytics SDKs in v1 (verify with `./gradlew :app:dependencies`).

---

## Android v1 Implementation Checklist

Phases run in dependency order. Each phase should be a separate PR. Scaffold (Phase 0) is done — PR #9 merged.

### Phase 0 — Scaffold ✅ DONE (PR #9)
- [x] Gradle build files (AGP 9.1.1, Kotlin 2.3.20, Compose BOM 2026.04.01)
- [x] `AynamaTheme` — 6-token color system, no dynamic color
- [x] `AynamaTypography` — Fraunces + IBM Plex Sans, 8 scale slots
- [x] `NavGraph` — 4-tab bottom nav (Home, Qibla, Tracker, Settings), placeholder screens
- [x] `AdhanWrapper` — Adhan 1.2.1, both Shafi'i and Hanafi Asr, `java.time.LocalTime`
- [x] `AdhanWrapperTest` — 9 tests passing (Makkah golden values, validation)
- [x] Font files bundled (`fraunces.ttf`, `ibm_plex_sans.ttf`)

---

### Phase 1 — Room Data Layer
Prerequisite for Home, Tracker, Settings, Notifications. Implement Room before building any screen.

**Gradle setup**
- [ ] Add Room (`2.8.4`) + KSP to `shared-logic/build.gradle.kts`
- [ ] Set `ksp { arg("room.schemaLocation", ...) }` and commit `schemas/` directory

**Entities**
- [ ] `Profile(id, name, latitude, longitude, calculationMethod, asrMadhab, isGps, sortOrder)` — at most one row has `isGps=true`
- [ ] `QazaEntry(id, prayer, date, status: enum{missed, made_up, intention_to_makeup}, profileId, updatedAt)` — per schema in Reviewer Concern #3

**DAOs + Database**
- [ ] `ProfileDao` — CRUD + `Flow<List<Profile>>`; GPS constraint enforced (set GPS on new profile → clear on old)
- [ ] `QazaEntryDao` — insert/update, `Flow<List<QazaEntry>>` by date range, outstanding-count query
- [ ] `AynamaDatabase` — `@Database`, `exportSchema = true`, TypeConverters for enums

**Repository layer**
- [ ] `ProfileRepository` — wraps `ProfileDao`, exposes `Flow<List<Profile>>`; GPS profile auto-refresh via `getLastKnownLocation()` (no background location permission)
- [ ] `QazaRepository` — wraps `QazaEntryDao`, auto-mark-as-missed when next prayer window opens

**Tests**
- [ ] `ProfileRepositoryTest` — in-memory Room DB; create/update/delete/read; GPS constraint; Qaza cascade on profile delete
- [ ] `QazaTrackerTest` — TypeConverter for status enum; mark-as-prayed write; auto-mark-as-missed after next prayer starts; outstanding-count query

---

### Phase 2 — Home Screen
Depends on: Phase 1.

**ViewModel**
- [ ] `HomeViewModel` — observes active profile via `Flow`; calls `AdhanWrapper.getPrayerTimes()` once per day per profile; exposes `PrayerTimesUiState`; invalidates cache on day rollover, profile change, timezone change

**Prayer timeline ribbon (DESIGN.md §5)**
- [ ] Countdown hero: Fraunces `display-xl` (72sp), tabular numerals, left-aligned
- [ ] Next prayer name above countdown: Fraunces `display-md`
- [ ] Active profile label top of screen: IBM Plex `body-sm` — "Home · London"
- [ ] Prayer rows — 3 visual states:
  - Passed: `ink-muted`, 60% opacity, small check glyph
  - Current: saffron tick that moves down ribbon as time passes
  - Upcoming: `ink`, full opacity, no decoration
- [ ] Sunrise row: `ink-muted`, no dot, time-reference only
- [ ] Tap on prayer row → opens mark-prayer bottom sheet (implemented in Phase 4)
- [ ] Time-of-day surface: slow cross-fade gradient per prayer phase (Fajr/Dhuhr/Asr/Maghrib/Isha — see DESIGN.md §3 surface table)

**Profile switcher**
- [ ] Horizontal swipe between profiles (weather-app pager)
- [ ] Dot indicator at bottom of Home content (not nav bar)
- [ ] Swipe past last dot → reveals "+" slot → navigates to profile creation
- [ ] Profile switcher never disrupts ribbon structure — only times and label change

**Empty + error states**
- [ ] Empty state (no profiles): Kaaba mark, "Set up your first prayer profile", "Create profile" CTA
- [ ] Error state: names cause + recovery action
- [ ] Location stale: last-known times + "Tap to refresh" badge

**Ramadan**
- [ ] Imsak row auto-appears above Fajr during Hijri Ramadan month (`android.icu.util.IslamicCalendar`)
- [ ] First Ramadan open: dismissible banner "Ramadan Mubarak — Imsak enabled (Fajr −10 min)"; shown once per Hijri year

**Accessibility**
- [ ] TalkBack: "Next prayer: Asr in 2 hours 14 minutes"
- [ ] Prayer row announces: "Fajr, 5:12 AM, passed"
- [ ] Profile switcher: accessibility action "Switch to next profile"

---

### Phase 3 — Qibla Screen
Depends on: Phase 0 only (no Room needed).

**ViewModel + sensor**
- [ ] `QiblaViewModel` — registers `SensorManager` listener in `onResume`, unregisters in `onPause`
- [ ] `SENSOR_DELAY_UI` (20 Hz) sampling rate
- [ ] `SensorManager.remapCoordinateSystem` for tilt-stable bearing (T7)
- [ ] Bearing from device coordinates to Kaaba (21.4225°N, 39.8262°E)
- [ ] Accuracy state: `HIGH` / `MEDIUM` / `LOW` / `UNRELIABLE`

**UI (DESIGN.md §5)**
- [ ] Giant custom arrow glyph (~200sp), rotates in place against parchment surface
- [ ] Physics-based rotation (damping 0.8, stiffness 100) — no jitter
- [ ] Degree readout below arrow: Fraunces `display-md`
- [ ] Distance to Kaaba: IBM Plex `body-sm`
- [ ] No cardinal N/E/S/W ring; no concentric circles; no 3D Kaaba render
- [ ] Calibration warning banner (amber, persistent) when accuracy < HIGH: "Hold phone flat and move in a figure-8 to calibrate"
- [ ] Magnetometer unavailable: "Compass not available on this device"

**Accessibility**
- [ ] Announce bearing as direction: "Facing northeast, Qibla is to the southeast — turn right"
- [ ] Announcement throttled to every 15° to avoid flooding
- [ ] Short haptic pulse when within ±5° of Qibla bearing

**Tests**
- [ ] `QiblaCalculatorTest` — bearing from known coordinates to Kaaba matches expected ±1°

---

### Phase 4 — Prayer Tracker Screen
Depends on: Phase 1 (Room).

**Mark-prayer flow**
- [ ] `MarkPrayerSheet` — bottom sheet with 3 states:
  - "I prayed this" → `prayed_on_time` (filled saffron square)
  - "I prayed this later (Qada)" → `made_up` (filled ink-muted square)
  - "I didn't pray this" → `missed` (empty stroke square)
- [ ] Entry point: tap prayer row on Home ribbon (same-day)
- [ ] Entry point: expand day row in history → tap prayer indicator (retroactive)
- [ ] Copy rules: never "Mark complete" / "Check in" — use exact strings above

**Today view**
- [ ] 5 prayer rows (Fajr, Dhuhr, Asr, Maghrib, Isha — no Sunrise)
- [ ] Status indicators per prayer
- [ ] Outstanding Qaza count shown as secondary context

**History list (DESIGN.md §16)**
- [ ] Column header once at top: `F  D  A  M  I` in IBM Plex `body-sm`, ink-muted
- [ ] Weekly section headers: Fraunces `title` — "This week" / "Last week" / "Apr 7–13"
- [ ] Day row: date label (today = saffron text) + 5 × 8pt squares + count ("4/5")
- [ ] Squares, not circles (avoids §10 anti-patterns)
- [ ] Row height: 56pt minimum
- [ ] Tap day row → expands inline with individual prayer rows + scheduled times
- [ ] Soft aggregate header: "42 of 45 prayers on time this week" — IBM Plex `body-sm`, ink-muted
- [ ] No calendar grid, no heat-map, no streak hero, no gamification copy

**Accessibility**
- [ ] Each prayer row checkbox: "Mark Fajr as prayed"
- [ ] Outstanding count announced: "3 prayers outstanding"

---

### Phase 5 — Notifications & Adhan
Depends on: Phase 1 (profiles + Qaza repo), Phase 2 (prayer time calculation).

**Manifest**
- [ ] `USE_EXACT_ALARM` permission declared (T8)
- [ ] `FOREGROUND_SERVICE_SPECIAL_USE` declared + Play Console justification written (T8)
- [ ] `BOOT_COMPLETED` receiver declared
- [ ] `ACTION_TIMEZONE_CHANGED` receiver declared
- [ ] Foreground service for adhan audio declared

**AlarmScheduler**
- [ ] `scheduleAll(profile)` — schedules 5 exact alarms via `AlarmManager.setExactAndAllowWhileIdle`
- [ ] Imsak alarm = Fajr −10 min, scheduled only during Hijri Ramadan
- [ ] Idempotent: calling `scheduleAll()` twice produces no duplicate alarms
- [ ] Reschedule on app open/resume (covers gaps from background kill)
- [ ] Daily midnight reschedule (advance to next day's times)

**BroadcastReceivers**
- [ ] `BootReceiver` — `BOOT_COMPLETED` → `scheduleAll()` for all active profiles
- [ ] `TimezoneReceiver` — `ACTION_TIMEZONE_CHANGED` → recalculate times + `scheduleAll()`

**Audio playback**
- [ ] Foreground service handles adhan audio (prevents system kill mid-adhan)
- [ ] Adhan audio assets bundled: Makkah, Madinah, Egyptian, Turkish, Al-Aqsa, Silent

**OEM battery optimization**
- [ ] `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent immediately after notification permission granted
- [ ] One-time prompt; do not re-prompt

**Tests**
- [ ] `AlarmSchedulerTest` — `scheduleAll()` sets 5 alarms; idempotent; Imsak = Fajr −10 min; daily midnight reschedule
- [ ] `RamadanDetectorTest` — `IslamicCalendar.RAMADAN` detection for known dates; non-Ramadan returns false; Imsak enabled/disabled correctly
- [ ] E2E (emulator): `AlarmFiresWhileClosedTest` — schedule alarm 30 s ahead; close app; verify notification in shade
- [ ] E2E (emulator): `AlarmRestoredAfterRebootTest` — schedule; `am broadcast -a android.intent.action.BOOT_COMPLETED`; verify rescheduled

---

### Phase 6 — Settings Screen
Depends on: Phase 1 (profiles), Phase 5 (notifications config).

**Profile management**
- [ ] Profile list with add/edit/delete
- [ ] Profile creation full-screen flow:
  1. Profile name field (max 20 chars, pre-filled "Home" / "Profile 2")
  2. Location: city search (offline geocoder) OR "Use current location" GPS button
  3. Calculation method picker (11 methods, brief description each, default: ISNA)
  4. Asr madhab picker (Hanafi / Shafi'i)
  5. Save → navigates back to Home with new profile active
- [ ] GPS profile auto-names with detected city name if user hasn't edited it

**Notification settings screen (DESIGN.md §15)**
- [ ] Master toggle row (56pt): saffron track on / parchment-muted track off
- [ ] When OS permission denied: replace toggle with "Enable in Settings →" saffron link
- [ ] When master off: prayer rows visible, names/times at ink-muted, toggles stay full opacity
- [ ] Per-prayer toggle rows (56pt): prayer name + tabular time + toggle + chevron
- [ ] Section headers: Fraunces `title` (20pt)
- [ ] Adhan picker row → navigates to Adhan Picker screen
  - [ ] 6 options with play-preview button (10 s each, one preview at a time)
  - [ ] Radio selection, immediate (no Save button)
- [ ] Ramadan Imsak row (64pt) with `parchment-muted` tint during active Ramadan
- [ ] Vibration row → 3-option action sheet (Always / With sound / Never), default: With sound

**Per-prayer detail sheet (DESIGN.md §15)**
- [ ] Bottom sheet (~60% height): `ModalBottomSheet` on Android
- [ ] Header: prayer name Fraunces `display-md`, centered; "Today · {time}" below
- [ ] Time offset picker: −15/−10/−5/0/+5/+10/+15 min, wheel/number picker, default 0
- [ ] Early reminder picker: Off / 5 / 10 / 15 min before, default Off
- [ ] Preview row: "Preview adhan" in saffron, plays 10 s sample

**Hijri settings**
- [ ] Ramadan start toggle: "Calculated | +1 day | +2 days" (moon-sighting accommodation)

---

### Phase 7 — Home Screen Widgets (Android Glance)
Depends on: Phase 1 (profiles), Phase 2 (prayer time calc).

**Setup**
- [ ] Add `glance-appwidget` dependency to `app/build.gradle.kts`
- [ ] `GlanceAppWidget` base class + `GlanceAppWidgetReceiver`
- [ ] Widget metadata XML (sizes, preview, description)

**Three widget sizes (DESIGN.md / architecture-design.md)**
- [ ] 1×1 — prayer abbreviation + time ("ASR 15:49"); max 3-char abbreviation
- [ ] 2×2 — next prayer name + countdown ("2h 14m") + profile name; countdown dominates
- [ ] 4×2 — countdown top + full 6-time schedule list below
- [ ] All sizes: tap → opens app Home screen

**Update strategy**
- [ ] Live countdown via `RemoteViews.setChronometerCountDown()` (system-native tick, no WorkManager)
- [ ] Widget update triggered only on prayer change (~5×/day)
- [ ] Widget reschedules update alarm alongside `AlarmScheduler` (Phase 5)

---

### Phase 8 — CI & Test Infrastructure
Depends on: all phases (run after each PR, gate on `main` merge).

**Test vectors**
- [ ] Generate full vector set: run `scripts/generate_test_vectors.py` for all 12 cities × all methods; commit output to `test-vectors/`
- [ ] Expand `AdhanWrapperTest` to load from `test-vectors/schema.json` — replace hardcoded Makkah test with file-driven loop over all 12 cities and methods
- [ ] `vectors.yml` GitHub Actions workflow — validate `test-vectors/*.json` against `test-vectors/schema.json` on every vector file change

**android.yml**
- [ ] Unit tests (`:shared-logic:test`, `:app:test`) on every commit
- [ ] Lint (`:app:lintDebug`) on every commit
- [ ] E2E tests (`android-emulator-runner@v2`, `ubuntu-latest`) gated behind `[e2e]` label or PRs targeting `main`
- [ ] Trigger on `android/**` and `test-vectors/**` path changes

**adhan-test-vectors companion repo**
- [ ] README documents: (1) how to trigger vector regeneration on Adhan upstream release (GitHub Actions manual dispatch); (2) who reviews PrayTimes.py vs Adhan disagreements; (3) process for syncing vectors back to main repo

---

### Phase 9 — Pre-launch Gates
Run before Play Store submission.

**Supply chain**
- [ ] Verify Adhan license in `batoulapps/adhan-java` GitHub; update `legal-posture.md`
- [ ] `./gradlew --write-verification-metadata sha256`; commit `gradle/verification-metadata.xml`
- [ ] `./gradlew :app:dependencies` — confirm zero third-party analytics SDKs

**Documentation / schema**
- [ ] Update `architecture-design.md` golden values table (still has PrayTimes.py values; correct to Adhan 1.2.1)
- [ ] T5: Add `tags: List<String>` or `context: ProfileContext` to `Profile` entity schema
- [ ] T8: Write Play Console `FOREGROUND_SERVICE_SPECIAL_USE` justification text

**Play Store prep**
- [ ] Declare `USE_EXACT_ALARM` alarm/clock category in Play Console
- [ ] Verify `com.aynama.prayertimes` has no Play Store conflict
- [ ] Trademark clearance on "aynama" (USPTO + EUIPO) before public submission
