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
- [x] ~~Reviewer Concern #5: WearOS complication refresh model~~ → **RESOLVED**: WearOS has no
  watchOS-style timeline, so the data source is asked for one value at a time and must say when
  to ask again. One exact alarm at the countdown's `nextTransition()` — a prayer starting, or
  its 30-minute count-up window closing — re-armed each time the source is queried, plus
  `UPDATE_PERIOD_SECONDS=0` to disable the system's periodic poll. Between those points the
  text is a `TimeDifferenceComplicationText` that the system ticks itself, so nothing of ours
  runs. Tracks the content exactly and wakes the watch far less than a 15-minute poll would.

## Temporal Interrogation follow-ups (non-blocking, low effort)

- [x] T1 — Write minimal README before v1 code starts.
- [x] ~~T2 — Design doc language~~ → **RESOLVED** (`com.batoulapps.adhan:adhan:1.2.1`, source-vendor only if patched)
- [x] ~~T3~~ → **RESOLVED** (`test-vectors/schema.json` written; prayer-times only, Draft-07, validates method/tolerance/reference/cases structure)
- [ ] T5 — Add `tags: List<String>` or `context: ProfileContext` field to Profile schema.
- [x] ~~T6~~ → **RESOLVED** (BOOT_COMPLETED + ACTION_TIMEZONE_CHANGED BroadcastReceivers spec'd in architecture-design.md)
- [x] ~~T7~~ → **RESOLVED** (Removed `remapCoordinateSystem`; direct rotation matrix + sin/cos low-pass filter gives stable flat-phone bearing)
- [ ] T8 — `FOREGROUND_SERVICE_SPECIAL_USE` manifest entry + Play Console justification.

## Engineering TODOs (from /plan-eng-review, 2026-04-21)

- [x] ~~**Vector generator self-tests.**~~ → **COMPLETE** (`scripts/test_generator.py`, 9 tests pass). Golden values corrected to Adhan 1.2.1 actual output (arch-design.md had stale PrayTimes.py values): fajr=05:10, sunrise=06:24, dhuhr=12:29, asr_shafii=15:53, asr_hanafi=16:50, maghrib=18:32, isha=19:42. `architecture-design.md` golden values table needs updating separately.

- [ ] **Run all test vectors in CI.** Load all 12 cities from `test-vectors/schema.json` in `android.yml` CI; parse + loop all methods (MWL, ISNA, UMM_AL_QURA, etc.). Assert each prayer time within ±1 min of test vector. Replace hardcoded Makkah-only tests in `AdhanWrapperTest.kt`. Required v1 gate (before launch) to catch Adhan upstream regressions.

- [ ] **Adhan-Swift version pin + parity check.** Before v3 iOS work: pin Adhan-Swift to a specific release in `scripts/reference-versions.json`; verify all 12 test-vector cities agree between Adhan-Swift and Adhan-Kotlin within ±1 min; add parity check to ios.yml CI. Required pre-v3 gate.

- [ ] **adhan-test-vectors companion repo ownership protocol.** Before v1 launch: document in companion repo README: (1) how to trigger vector regeneration on Adhan upstream release (GitHub Actions manual dispatch); (2) who reviews PrayTimes.py vs Adhan disagreements; (3) process for syncing updated vectors back to main repo.

- [ ] **iOS notification limit analysis.** Before v3 iOS notification settings work: OS limit = 64 pending. 5 prayers × 7 days = 35 (fine). + advance-notice reminders = 70 (overflow). Options: (a) cap at 6 days; (b) background-app-refresh regeneration at 5-day mark; (c) alternating schedule. Resolve before speccing advance-notice for iOS.

## Known issues

- [ ] **GPS profile assumes the device timezone matches the fix.** "Use current location" sets
  `timezone = ZoneId.systemDefault()`, which is right at home and wrong for a traveller whose
  phone has not updated its zone: the profile then computes correct prayer instants and renders
  them in the wrong wall clock. Derive the zone from the fix's coordinates instead, as the
  city-search path already does via `detectTimezoneForLocation`. (found while testing the
  add-profile FAB flow)

## Supply-chain / security

- [ ] **Verify Adhan license (MIT vs Apache 2.0).** Check `LICENSE` file in batoulapps/adhan-java + batoulapps/adhan-swift on GitHub. Update `legal-posture.md` compliance framework accordingly — MIT = copyright notice only; Apache 2.0 = NOTICE file + state-changes. Maven POM for adhan2-jvm says MIT; adhan:1.2.1 POM tag is empty. Blocks first public release. (flagged /plan-eng-review 2026-04-19)

- [ ] `gradle --write-verification-metadata sha256` after first build; commit `gradle/verification-metadata.xml`.
- [ ] Dependency audit: zero third-party analytics SDKs in v1 (verify with `./gradlew :app:dependencies`).

---

## Phase 2 — Android Validation Gate ✅ PASSED

Run on a rooted `google_apis` API 36 emulator, 2026-09-07. Clock control via `adb root` +
`date`, so the countdown boundaries were walked at real instants rather than simulated.

### Verified

| Area | Result |
|---|---|
| Countdown: 5 s before / at / +5 s / +29 m 55 s / +30 m / past, around Dhuhr | sign drops at the instant, flips to the next prayer at exactly +30 m |
| Countdown: Isha instant, Isha +30 m, 23:59:55, 00:00:05, before/after Fajr | continuous across midnight; Isha +30 m → tomorrow's Fajr |
| Live notification across the same boundary | `At 12:59 PM` / counting down → `Began at 12:59 PM` / counting up → `Asr`, counting down |
| Live notification with the app process killed | alarm fired, notification flipped to counting up, chain re-armed at the prayer + 30 m + guard |
| Notification enable / disable / master gate | notification and its alarm appear and disappear together |
| Device reboot | 3 prayer alarms + midnight reschedule re-armed without opening the app |
| Device timezone change (Riyadh → New York) | pinned profile's displayed times and armed alarm instants byte-identical |
| Profile create / edit / delete / switch | all correct; edit survived a force-stop |
| Widgets: two placed on two profiles | each keeps its own profile; reconfiguring one leaves the other alone; both get rollover chains |
| Widget → profile navigation | cold, warm, from another tab, and for a deleted profile |
| Friday Jumu'ah | tracker history for Fri Sep 4 reads "Jumuah — 1:01 PM"; today (Monday) reads "Dhuhr" |
| Light / dark themes | both legible; nav bar palette bug found and fixed |
| Notification permission denied | master row becomes "Enable in Settings →" per DESIGN §15 |

### Issues found and fixed

1. **Turning notifications off left every alarm armed.** `cancelForProfile` built its lookup
   intent without an action while `submitAlarm` armed one with `ACTION_PRAYER_ALARM`;
   PendingIntent lookup matches on `Intent.filterEquals`, so nothing was ever cancelled.
   Master off, per-prayer off, profile deleted, profile switched — all left alarms firing.
2. **The bottom navigation was Material's default lavender**, in both themes, on every screen.
   Unset `ColorScheme` roles keep Material's purple baseline; DESIGN §10 forbids purple.
3. **`shared-logic`'s 16 Room instrumented tests had never run** — the module named
   `AndroidJUnitRunner` without depending on `androidx.test:runner`.
4. **The tracker computed prayer times in the device timezone** (fixed in the Jumu'ah PR).
5. **The Qibla phase band did the same** (fixed in the Jumu'ah PR).

### Known issues carried forward

See "Known issues" above — the GPS profile's timezone assumption is real but pre-existing and
does not block the dependent platforms.

---

## Phase 4B — Android + WearOS Integration Gate ✅ PASSED (with one documented limitation)

Run 2026-09-07 on a phone emulator (rooted `google_apis` API 36) and a Wear OS 5 emulator
(384×384 round) side by side.

### The limitation, stated up front

**A real inter-device Data Layer link could not be established here.** Pairing two emulators
needs the Wear OS companion app on the phone; the `google_apis` image has no working Play Store
(`com.android.vending` is a v1.8 stub), the companion app is absent, and no APK for it ships
with the SDK. Installing it would need a Play Store sign-in.

What that leaves unverified is the Play Services hop between two nodes — three lines of
`putDataItem` / `onDataChanged`. Everything on both sides of it is covered by
`WearSyncRoundTripTest`, which publishes exactly what the phone publishes (same contract, same
codec) onto the real Data Layer and reads it back through the watch's own pull path.

### Verified

| Area | Result |
|---|---|
| Profile sync, all fields | `WearSyncRoundTripTest` — ids, madhab, method, timezone, Hijri offset all survive |
| Profile created on the phone | appears on the watch |
| Profile deleted on the phone | disappears from the watch |
| Profile renamed | follows the same row, id preserved |
| Active profile changed | the watch follows |
| All profiles deleted | the watch clears and says so |
| Multiple profiles | both mirrored, both pageable |
| Stale / unreadable payload | the watch keeps its last good state |
| Tracker history across a sync | preserved for profiles that survive |
| Watch app restart | state and countdown intact |
| Watch reboot | app returns with the same profile and a live countdown; complication receiver runs, no crash |
| Phone app restart | publishes, renders normally |
| Phone with no watch paired | publish fails, is logged not thrown, phone unaffected |
| Countdown agreement, live on both devices | phone `-01:54:35` to Asr 16:34, watch `-01:55:00` to the same, ~25 s apart |
| Friday Jumu'ah | asserted on every surface, both modules |

### Cross-surface consistency

The gate's headline requirement — phone, widgets, notifications, watch app and complications
reporting one state — is held by two suites that walk **a whole day**, every minute plus the
exact instants either side of every transition (~1,450 moments each):

- `CrossSurfaceConsistencyTest` (app) — widget state and live-notification content vs the shared
  rule, and against each other.
- `WearSurfaceConsistencyTest` (wear) — complication state and watch screen vs the shared rule,
  and against each other. The tile renders what the complication source returns, so asserting
  that source covers both.

Each surface is asserted against `countdownAt` rather than against its siblings: two surfaces
that drifted the same way would still pass a sibling comparison.

### Notes for whoever runs this next

- `:wear` and `:app` share an `applicationId`, so installing one replaces the other and
  `connectedAndroidTest` will try to run wear tests on the phone. Set `ANDROID_SERIAL`.
- `connectedAndroidTest` uninstalls the app afterwards; reinstall before doing manual checks.

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

### Phase 1 — Room Data Layer ✅ DONE (PR #10)
Prerequisite for Home, Tracker, Settings, Notifications. Implement Room before building any screen.

**Gradle setup**
- [x] Add Room (`2.8.4`) + KSP to `shared-logic/build.gradle.kts`
- [x] Set `ksp { arg("room.schemaLocation", ...) }` and commit `schemas/` directory

**Entities**
- [x] `Profile(id, name, latitude, longitude, calculationMethod, asrMadhab, isGps, sortOrder)` — at most one row has `isGps=true`
- [x] `QazaEntry(id, prayer, date, status: enum{missed, made_up, intention_to_makeup}, profileId, updatedAt)` — per schema in Reviewer Concern #3

**DAOs + Database**
- [x] `ProfileDao` — CRUD + `Flow<List<Profile>>`; GPS constraint enforced (set GPS on new profile → clear on old)
- [x] `QazaEntryDao` — insert/update, `Flow<List<QazaEntry>>` by date range, outstanding-count query
- [x] `AynamaDatabase` — `@Database`, `exportSchema = true`, TypeConverters for enums

**Repository layer**
- [x] `ProfileRepository` — wraps `ProfileDao`, exposes `Flow<List<Profile>>`; GPS profile auto-refresh via `getLastKnownLocation()` (no background location permission)
- [x] `QazaRepository` — wraps `QazaEntryDao`, auto-mark-as-missed when next prayer window opens

**Tests**
- [x] `ProfileRepositoryTest` — in-memory Room DB; create/update/delete/read; GPS constraint; Qaza cascade on profile delete
- [x] `QazaTrackerTest` — TypeConverter for status enum; mark-as-prayed write; auto-mark-as-missed after next prayer starts; outstanding-count query
      (both existed but had never run: `shared-logic` named AndroidJUnitRunner without
      depending on it, so the instrumentation crashed on start. Fixed in the Phase 2 gate;
      16 tests now execute.)

---

### Phase 2 — Home Screen ✅ DONE (PR #11)
Depends on: Phase 1.

**ViewModel**
- [x] `HomeViewModel` — observes all profiles via `Flow`; calls `AdhanWrapper.getPrayerTimes()` once per day per profile (cached by profile id + date); exposes `HomeUiState`; 1-second tick drives countdown + ribbon state; `AynamaApplication` manual DI with debug seed profiles

**Prayer timeline ribbon (DESIGN.md §5)**
- [x] Countdown hero: Fraunces `display-xl` (72sp), tabular numerals (`fontFeatureSettings = "tnum"`), left-aligned
- [x] Next prayer name above countdown: Fraunces `display-md`
- [x] Active profile label top of screen: IBM Plex `body-sm` — "Home · London"
- [x] Prayer rows — 3 visual states:
  - Passed: `ink-muted`, 60% opacity, small check glyph
  - Current: saffron tick that moves down ribbon as time passes
  - Upcoming: `ink`, full opacity, no decoration
- [x] Sunrise row: `ink-muted`, no dot, time-reference only
- [x] Tap on prayer row → opens mark-prayer bottom sheet
- [x] Time-of-day surface: slow cross-fade gradient per prayer phase (`animateColorAsState(tween(3000))`, 6 phases)

**Profile switcher**
- [x] Horizontal swipe between profiles (`HorizontalPager`, weather-app pager)
- [x] Dot indicator at bottom of Home content (not nav bar)
- [x] ~~Swipe past last dot → reveals "+" slot~~ → **SUPERSEDED**: replaced by the Prayers-screen FAB, which opens the profile sheet in place
- [x] Profile switcher never disrupts ribbon structure — only times and label change

**Empty + error states**
- [x] Empty state (no profiles): Kaaba mark, "Set up your first prayer profile", "Create profile" CTA
- [x] Error state: names cause + recovery action
- [ ] Location stale: last-known times + "Tap to refresh" badge (deferred to Phase 6 GPS work)

**Ramadan**
- [x] Imsak row auto-appears above Fajr during Hijri Ramadan month (`android.icu.util.IslamicCalendar`)
- [x] First Ramadan open: dismissible banner "Ramadan Mubarak — Imsak enabled (Fajr −10 min)"; shown once per Hijri year (dismissed state stored as Hijri year int in SharedPreferences)

**Accessibility**
- [x] TalkBack: "Next prayer: Asr in 2 hours 14 minutes"
- [x] Prayer row announces: "Fajr, 5:12 AM, passed"
- [x] Profile switcher: accessibility action "Switch to next profile"

---

### Phase 3 — Qibla Screen ✅ DONE (branch android-phase3-qibla)
Depends on: Phase 1 (ProfileRepository for active profile + prayer times for gradient phase).

**ViewModel + sensor**
- [x] `QiblaViewModel` — registers `SensorManager` listener in `onResume`, unregisters in `onPause`
- [x] `SENSOR_DELAY_UI` (~16 Hz) sampling rate; `LP_ALPHA = 0.15` tuned for ~6-sample (~300 ms) settling. UI rate chosen over GAME for power; convergence acceptable during normal turning.
- [x] Direct rotation matrix (no `remapCoordinateSystem`) for tilt-stable flat-phone bearing (T7)
- [x] Bearing from device coordinates to Kaaba (21.4225°N, 39.8262°E)
- [x] Accuracy state: `HIGH` / `MEDIUM` / `LOW` / `UNRELIABLE`

**UI (DESIGN.md §5)**
- [x] Giant custom arrow glyph (~200sp), rotates in place against parchment surface
- [x] Physics-based rotation (damping 0.8, stiffness 100) — no jitter
- [x] Degree readout below arrow: Fraunces `display-md`
- [x] Distance to Kaaba: IBM Plex `body-sm`
- [x] No cardinal N/E/S/W ring; no concentric circles; no 3D Kaaba render
- [x] Calibration warning banner (amber, persistent) when accuracy < HIGH: "Hold phone flat and move in a figure-8 to calibrate"
- [x] Magnetometer unavailable: "Compass not available on this device"

**Accessibility**
- [x] Announce bearing as direction: "Facing northeast, Qibla is to the southeast — turn right"
- [x] Announcement throttled to every 15° to avoid flooding
- [x] Short haptic pulse when within ±5° of Qibla bearing

**Tests**
- [x] `QiblaCalculatorTest` — bearing from known coordinates to Kaaba matches expected ±1°

---

### Phase 4 — Prayer Tracker Screen ✅ DONE
Depends on: Phase 1 (Room).

**Mark-prayer flow**
- [x] `MarkPrayerSheet` — bottom sheet with 3 states:
  - "I prayed this" → `prayed_on_time` (filled saffron square)
  - "I prayed this later (Qada)" → `made_up` (filled ink-muted square)
  - "I didn't pray this" → `missed` (empty stroke square)
- [x] Entry point: tap prayer row on Home ribbon (same-day)
- [x] Entry point: expand day row in history → tap prayer indicator (retroactive)
- [x] Copy rules: never "Mark complete" / "Check in" — use exact strings above

**Today view**
- [x] 5 prayer rows (Fajr, Dhuhr, Asr, Maghrib, Isha — no Sunrise)
- [x] Status indicators per prayer
- [x] Outstanding Qaza count shown as secondary context

**History list (DESIGN.md §16)**
- [x] Column header once at top: `F  D  A  M  I` in IBM Plex `body-sm`, ink-muted
- [x] Weekly section headers: Fraunces `title` — "This week" / "Last week" / "Apr 7–13"
- [x] Day row: date label (today = saffron text) + 5 × 8pt squares + count ("4/5")
- [x] Squares, not circles (avoids §10 anti-patterns)
- [x] Row height: 56pt minimum
- [x] Tap day row → expands inline with individual prayer rows + scheduled times
- [x] Soft aggregate header: "42 of 45 prayers on time this week" — IBM Plex `body-sm`, ink-muted
- [x] No calendar grid, no heat-map, no streak hero, no gamification copy

**Accessibility**
- [x] Each prayer row checkbox: "Mark Fajr as prayed"
- [x] Outstanding count announced: "3 prayers outstanding"

---

### Phase 5 — Notifications & Adhan ✅ DONE (PR #14)
Depends on: Phase 1 (profiles + Qaza repo), Phase 2 (prayer time calculation).

**Manifest**
- [x] `USE_EXACT_ALARM` permission declared (T8)
- [x] `FOREGROUND_SERVICE_SPECIAL_USE` declared + Play Console justification written (T8)
- [x] `BOOT_COMPLETED` receiver declared
- [x] `ACTION_TIMEZONE_CHANGED` receiver declared
- [x] Foreground service for adhan audio declared

**AlarmScheduler**
- [x] `scheduleAll(profile)` — schedules 5 exact alarms via `AlarmManager.setExactAndAllowWhileIdle`
- [x] Imsak alarm = Fajr −10 min, scheduled only during Hijri Ramadan
- [x] Idempotent: calling `scheduleAll()` twice produces no duplicate alarms
- [x] Reschedule on app open/resume (covers gaps from background kill)
- [x] Daily midnight reschedule (advance to next day's times)

**BroadcastReceivers**
- [x] `BootReceiver` — `BOOT_COMPLETED` → `scheduleAll()` for all active profiles
- [x] `TimezoneReceiver` — `ACTION_TIMEZONE_CHANGED` → recalculate times + `scheduleAll()`

**Audio playback**
- [x] Foreground service handles adhan audio (prevents system kill mid-adhan)
- [x] Adhan audio assets bundled: Makkah, Madinah, Egyptian, Turkish, Al-Aqsa, Silent

**OEM battery optimization**
- [x] `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent immediately after notification permission granted
- [x] One-time prompt; do not re-prompt

**Tests**
- [x] `AlarmSchedulerTest` — `scheduleAll()` sets 5 alarms; idempotent; Imsak = Fajr −10 min; daily midnight reschedule
- [x] `RamadanDetectorTest` — `IslamicCalendar.RAMADAN` detection for known dates; non-Ramadan returns false; Imsak enabled/disabled correctly
- [x] E2E (emulator): an armed alarm fires and reaches the shade — `AlarmDeliveryTest`
- [x] E2E (emulator): alarms are restored after a reschedule — `AlarmDeliveryTest`
      (was ticked here while no such file existed; written during the Phase 2 gate, and it
      found the cancel bug below)

---

### Phase 5b — Live Prayer Notification ✅ DONE
Depends on: Phase 5 (channels + alarm plumbing), unified countdown (DESIGN.md §19).

- [x] `NotificationPreferences.liveNotificationEnabled` — opt-in, default off
- [x] Own channel at `IMPORTANCE_MIN`: silent, no vibration, no badge
- [x] Countdown via `setWhen` + `setUsesChronometer` + `setChronometerCountDown` — no per-second job
- [x] Direction carried in words (`At …` / `Began at …`), the chronometer takes no format string
- [x] Day-aware prayer name (Jumu'ah on Friday)
- [x] Tap → opens the app on the notification profile
- [x] `LiveNotificationScheduler` — one exact alarm at `nextTransition()`, re-armed on each fire
- [x] Re-armed on app start/resume, boot, timezone change and prayer rollover via `AlarmScheduler.scheduleAll`
- [x] Settings toggle: Notifications → OTHER → "Live countdown"
- [x] `LivePrayerNotificationTest` — direction, subject, chronometer base, Jumu'ah, polar-night absence

---

### Phase 3B — WearOS (in progress)

- [x] `wear` module consuming `shared-logic` — same Adhan wrapper, countdown rule and naming
- [x] Phone → watch profile sync over the Data Layer (`ProfileCodec`, `WearSyncContract`)
- [x] Watch home: unified countdown, prayer list, profile paging, Jumu'ah, disconnected states
- [x] Complications: SHORT_TEXT, LONG_TEXT, MONOCHROMATIC_IMAGE (DESIGN.md §7)
- [x] Complication refresh model (Reviewer Concern #5)
- [x] Tiles — prayer name and its clock time; deliberately no countdown (a tile has no live-ticking text, and a frozen countdown is worse than none)
- [x] Phase 4B — Android + WearOS integration gate (see below)

---

### Phase 6 — Settings Screen
Depends on: Phase 1 (profiles), Phase 5 (notifications config).

#### Phase 6a — Profile Management ✅ DONE (PR #15)
- [x] Profile list with add/edit/delete
- [x] Profile creation full-screen flow:
  1. Profile name field (max 20 chars, pre-filled "Home" / "Profile 2")
  2. Location: city search (offline geocoder) OR "Use current location" GPS button
  3. Calculation method picker (11 methods, brief description each, default: ISNA)
  4. Asr madhab picker (Hanafi / Shafi'i)
  5. Save → navigates back to Home with new profile active
- [x] GPS profile auto-names with detected city name if user hasn't edited it

#### Phase 6b — Notification Settings Screen ✅ DONE
- [x] Master toggle row (56pt): saffron track on / parchment-muted track off
- [x] When OS permission denied: replace toggle with "Enable in Settings →" saffron link
- [x] When master off: prayer rows visible, names/times at ink-muted, toggles stay full opacity
- [x] Per-prayer toggle rows (56pt): prayer name + tabular time + toggle + chevron
- [x] Section headers: Fraunces `title` (20pt)
- [x] Adhan picker row → navigates to Adhan Picker screen
  - [x] 6 options with play-preview button (10 s each, one preview at a time)
  - [x] Radio selection, immediate (no Save button)
- [x] Ramadan Imsak row (64pt) with `parchment-muted` tint during active Ramadan
- [x] Vibration row → 3-option action sheet (Always / With sound / Never), default: With sound

#### Phase 6c — Per-Prayer Detail Sheet ✅ DONE
- [x] Bottom sheet (~60% height): `ModalBottomSheet` on Android
- [x] Header: prayer name Fraunces `display-md`, centered; "Today · {time}" below
- [x] Time offset picker: −15/−10/−5/0/+5/+10/+15 min, wheel/number picker, default 0
- [x] Early reminder picker: Off / 5 / 10 / 15 min before, default Off
- [x] Preview row: "Preview adhan" in saffron, plays 10 s sample

#### Phase 6d — Hijri Settings ✅ DONE
- [x] Hijri offset: −2 to +2 days with per-month auto-reset (moon-sighting accommodation)

---

### Phase 7 — Home Screen Widgets (Android Glance)
Depends on: Phase 1 (profiles), Phase 2 (prayer time calc).

**Setup**
- [x] Add `glance-appwidget` dependency to `app/build.gradle.kts`
- [x] `GlanceAppWidget` base class + `GlanceAppWidgetReceiver`
- [x] Widget metadata XML (sizes, preview, description)

**Four widget categories (DESIGN.md / architecture-design.md)**
Each is separately pickable in the widget drawer and only stretches on resize (`SizeMode.Single`).
- [x] Next Prayer, 1×1 — prayer name + time + countdown + profile
- [x] Next Prayer & Dates, 2×2 — dates band + next prayer cluster
- [x] Prayer Schedule, 2×2 — dates band + six prayer times + profile
- [x] Prayer Times, 4×2 — dates + sunrise, five prayers, countdown, current prayer highlighted
- [x] All categories: tap → opens app Home on that widget's own profile
- [x] Per-widget profile selection via `WidgetConfigureActivity` (stored in each instance's Glance state)

**Update strategy**
- [x] Live countdown via `RemoteViews.setChronometerCountDown()` (system-native tick, no WorkManager)
- [x] Widget update triggered on each prayer boundary, Sunrise included (~6×/day)
- [x] Widget reschedules update alarm alongside `AlarmScheduler` (Phase 5)
- [x] Rollover alarms fire 2s past the prayer instant, plus a tomorrow-Fajr slot so one is always pending
- [x] `updatePeriodMillis` 30min backstop + `TIME_SET` refresh (Chronometer is elapsedRealtime-anchored)

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
