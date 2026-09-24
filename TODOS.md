# TODOS — aynama-prayer-times

Tracked items from plan reviews. Must-decide-before-code items are in `.gstack/projects/aitasadduq-aynama-prayer-times/ceo-plans/`.

> **Sync note (2026-09-23, re-checked 2026-09-24).** The Android v1 checklist below was re-checked against the code on `agent-main` (`5eeed05`). Items that were ticked but aren't in the app are now unticked, with a short note saying what actually shipped. Design gaps found in the same pass are tracked as DS findings in `REVIEW-FINDINGS.md`; DESIGN.md §27 is the index.

## Design TODOs (from /plan-design-review, 2026-04-18)

- [x] ~~**Run /design-consultation before v1 UI work.**~~ → **COMPLETE** (DESIGN.md created 2026-04-18, covers color palette, typeface, iconography, app icon, brand tokens, all 6 sections)

- [x] ~~**Notification settings screen UX spec.**~~ → **COMPLETE** (DESIGN.md §15: master toggle, per-prayer rows, adhan picker with 6 voices, Imsak toggle, vibration modes, per-prayer offset/early-reminder sheets)

- [x] ~~**Prayer tracker history view spec.**~~ → **COMPLETE** (DESIGN.md §16: calendar-free flat list, per-day row expansion, 5 prayer indicators (no Sunrise), marked states: on-time / Qada / missed, week sections, soft aggregate header)

- [x] ~~**Design-doc sync against Android v1.**~~ → **COMPLETE 2026-09-24.** DESIGN.md was re-baselined against `agent-main`: §3 contrast was recomputed, and §25 (widgets), §26 (states) and §27 (conformance) were added after that branch's §19–§24. `architecture-design.md`, README and the posture docs were updated.

- [ ] **Sign off the spec changes the sync recorded** (DESIGN.md §14 requires explicit discussion for §3, §4, §5 and §9). This is the one list; DESIGN.md points here.
  - §3, §8: the surface stepping per phase (not continuous)
  - §5: passed rows in the muted token at full opacity. This still fails AA on the Asr and Sunrise gradients (DS3).
  - §5: the current row in ink on light phases (saffron on dark ones still fails AA on the Fajr and Maghrib gradients, DS3)
  - §5: the Qibla panels
  - §5: no profile-switcher chip or card stack; swiping is the only way to switch
  - §5: the Ramadan banner no longer mentions Imsak or links to notification settings
  - §5: utilitarian screens use a 16 dp top margin (April: 32 dp)
  - §3: `saffron-ink` as the accent for text and thin strokes on light surfaces, and `ink` labels on saffron fills
  - §4: numbers that change in place are set in IBM Plex Sans. This contradicts §4's own `display-xl` row and §19's format rule, which keep the hero in Fraunces, so decide it with DS9.
  - §18: the −2…+2 Hijri adjustment
  - §17: location time zone on by default. Hold this until DS32 is fixed: city search picks the wrong zone for Madrid, Lisbon, Detroit and others, so the default labels their times an hour off.
  - §17: Muslim World League as the default method (the plan said ISNA). The default changes users' prayer times.
  - §15: master-off hiding every Notifications section
  - §15: prayer names stay `ink` when their alert is off (April: `ink-muted`)
  - §15: the alert-time value is `ink-muted` in both rows (April: `ink` when active). The code's dead branch (PR #17 N2) produced this; confirm it's wanted before N2's fix cements it.
  - §15: the per-prayer detail sheet opening fully expanded
  - §25: four widgets instead of three sizes

## Design decisions raised by the 2026-09-23 sync

- [ ] **Moving sundial tick (DS6).** Build the vertical rule and the moving tick, or formally amend DESIGN.md §9. It's one of the Two Deliberate Departures.
- [ ] **Hijri adjustment lapse rule (DS7).** With +1, the app shows Ramadan, with an Imsak alarm, on the user's Eid, and a +1 saved the evening before the first fast misses the first day entirely. Decide what should happen when the adjusted month ends.
- [ ] **Hijri calendar variant (DS11).** ICU picks Umm al-Qura or civil from the device's region. Pin one explicitly.
- [ ] **Polar-day convention** (REVIEW-FINDINGS PR #21 C1). Nearest latitude, nearest day, or fixed proportions.
- [ ] **"Prayed on time" for past days (DS15).** Enforce the DESIGN.md §16 window, or drop the rule.
- [ ] **Countdown numerals (DS9).** Move the hero to IBM Plex Sans (tabular), or bundle a Fraunces build with tabular figures.
- [ ] **Notification and launcher icons (DS8, DS22).** The crescent placeholder sits next to a forbidden motif.
- [ ] **Qaza tracking opt-in.** `privacy-posture.md` requires the tracker to be opt-in and off by default. Android v1's Tracker tab is always on (though it only records what the user marks).

## Deferred decisions (not blocking v1 code start)

- [ ] **Monetization / sustainability model.** Pick one: donations, pay-what-you-want, freemium, paid-upfront, fully-free. Decide before v2.
- [ ] **iOS CI runner strategy.** GitHub macOS vs MacStadium vs self-hosted Mac mini vs no-iOS-until-v3. Decide before v3 (iOS phase start).
- [ ] **Quran data source.** Tanzil (chosen in legal-posture.md) vs alternative. Validate against licensing + attribution before v4 (Quran feature).
- [ ] **Gold/silver price API for zakat.** Free tier API vs scraped static value vs user-input. Decide before v5.
- [ ] **Trademark clearance on "aynama."** USPTO + EUIPO search before Play Store + F-Droid submission.
- [x] ~~**Project license.** MIT vs Apache 2.0. Decide before first public repo push. Lean Apache 2.0 to match Adhan upstream.~~ → **DECIDED:** Apache License 2.0, in `LICENSE` (commit `77ef0c6`, 2026-04-24).
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
- [ ] T8 — `FOREGROUND_SERVICE_SPECIAL_USE` manifest entry + Play Console justification. *(The manifest half is done: the permission and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE = prayerTimeAdhan` are declared. The Play Console justification text is still to write.)*

## Engineering TODOs (from /plan-eng-review, 2026-04-21)

- [x] ~~**Vector generator self-tests.**~~ → **COMPLETE** (`scripts/test_generator.py`, 9 tests pass). Golden values corrected to Adhan 1.2.1 actual output (arch-design.md had stale PrayTimes.py values): fajr=05:10, sunrise=06:24, dhuhr=12:29, asr_shafii=15:53, asr_hanafi=16:50, maghrib=18:32, isha=19:42. ~~`architecture-design.md` golden values table needs updating separately.~~ *(Updated 2026-09-23. Note: `scripts/test_generator.py` isn't on `main`; PR #8 merged it into the `t3-test-vector-schema` branch only.)*

- [ ] **Run all test vectors in CI.** ~~Load all 12 cities from `test-vectors/schema.json`~~ — `schema.json` is a JSON Schema and never contained any cities; the twelve now exist, defined in `scripts/adhan-parity/cases.json` and generated into `test-vectors/prayer-times/*.json` (Phase 3A). **iOS side is done** — `VectorParityTests` loops all twelve under `swift test`. **Android side remains:** replace the hardcoded Makkah-only tests in `AdhanWrapperTest.kt` with a file-driven loop over the same directory, and wire it into `android.yml`. Required v1 gate (before launch) to catch Adhan upstream regressions.

- [ ] **`scripts/generate_vectors.py` is on an unmerged branch.** The PrayTimes.py cross-validation generator (`vector-generator-self-tests`, db1268d) was never merged to `main` or `agent-main`, and its `CITY_CONFIGS` holds one city, not twelve. `scripts/adhan-parity/` (Phase 3A) solves a different problem — cross-*port* agreement, Swift vs Kotlin — and does not replace the two-source correctness check architecture-design.md specifies. Merge that branch and extend its city list to the twelve now defined in `scripts/adhan-parity/cases.json`, so the vectors are validated for correctness and not only for port parity. Note `scripts/reference-versions.json` now exists on both branches and will conflict trivially: keep the superset (Phase 3A added the `adhan-swift` keys).

- [x] ~~**Adhan-Swift version pin + parity check.**~~ → **COMPLETE** (Phase 3A). Adhan-Swift pinned to `1.5.0` (exact, not a range) in `ios/SharedLogic/Package.swift`, mirrored in `scripts/reference-versions.json` with the tag's commit SHA. The twelve parity cities did not exist anywhere in the repo, so they are now defined in `scripts/adhan-parity/cases.json` and their vectors generated from Adhan-Kotlin 1.2.1 into `test-vectors/prayer-times/*.json` — all ten calculation methods covered. `VectorParityTests` asserts Adhan-Swift matches every vector within ±1 min and runs under plain `swift test`. **11 of 12 agreed on the first run; London did not — see the high-latitude item below.**

- [ ] **adhan-test-vectors companion repo ownership protocol.** Before v1 launch: document in companion repo README: (1) how to trigger vector regeneration on Adhan upstream release (GitHub Actions manual dispatch); (2) who reviews PrayTimes.py vs Adhan disagreements; (3) process for syncing updated vectors back to main repo.

- [x] ~~**iOS notification limit analysis.**~~ → **RESOLVED** (Phase 3A, DESIGN.md §24). A fixed day cap is the wrong shape — it is sized for the worst case and short-changes the common one, where the user has enabled far fewer than eleven notifications a day. The horizon is computed from what is actually enabled instead: `perDay = enabled prayers + enabled early reminders + Imsak`, `horizon = clamp(60 / perDay, 3...7)` days. Four of the 64 slots are held back as headroom, prayers are scheduled before their reminders so overflow drops the reminder and never the prayer, and the queue is refilled on foreground and from a `BGAppRefreshTask`. Worst realistic case (5 prayers + 5 reminders + Imsak = 11/day) still yields a 5-day horizon; the common case is capped at 7.

## Known issues

- [ ] **Wall-clock round-trip loses an hour in a DST fall-back, on both platforms.**
  `AdhanWrapper` throws away the absolute instants Adhan returns and stores wall-clock times
  (`ClockTime` / `LocalTime`); `PrayerTimeline.entriesFor` then rebuilds an instant from them.
  In the repeated hour of a fall-back that round-trip is lossy — measured on Europe/London
  2026-10-25, an instant of `01:30:00Z` reads as 01:30 local and reconstructs to `00:30:00Z`,
  one hour early. The spring-forward gap is fine (it resolves forward, as `java.time` does);
  it is the autumn overlap that has no representation.

  **Not reachable with the five prayers today.** Fall-back happens in autumn, when Fajr and
  Isha are nowhere near the repeated hour, and the midsummer high-latitude collapse that does
  put them at 01:02 never lands on a transition date. It becomes reachable the moment anything
  round-trips a time that can fall there. The fix is to carry the instant through
  `PrayerTimesResult` instead of recomputing it, which is a change to both ports — recorded
  here rather than made inside an iOS PR.

- [ ] **Android follow-ups from the iOS review (PR #29).** Two divergences found by porting,
  both fixed on iOS and still open on Android:
  (a) `QiblaCalculator.distanceKm` passes `sqrt(1.0 - a)` unclamped, so haversine rounding can
  return `NaN` at the antipode of the Kaaba. One-line `coerceAtMost(1.0)`.
  (b) `Profile.effectiveZoneId()` calls bare `ZoneId.of(timezone)`, which throws
  `ZoneRulesException` for an identifier the tz database has dropped. Swift falls back to the
  device zone instead. One of the two behaviours should win; silently computing in the wrong
  zone and taking the app down are both bad, so the answer is probably "fall back and say so".

- [ ] **DECISION NEEDED — the two Adhan ports disagree above 48° latitude, and London is one of
  them.** Adhan-Kotlin 1.2.1 (what Android ships) applies `MIDDLE_OF_THE_NIGHT` at every
  latitude: its `nightPortions()` never sees the coordinates. Adhan-Swift 1.5.0 leaves the rule
  unset and falls back to `HighLatitudeRule.recommended(for:)`, which is `SEVENTH_OF_THE_NIGHT`
  above 48°. Measured on the twelve parity cities: eleven agree to the minute, **London on the
  June solstice is 157 minutes apart on Fajr and 158 on Isha.**

  iOS now pins `.middleOfTheNight` to match Android, because Phase 3A makes the tested Android
  behaviour the specification, and all twelve cities pass. But the pin preserves a consequence
  worth deciding on rather than inheriting: under middle-of-the-night, **London's Fajr and Isha
  collapse onto the same instant (01:02) for weeks around midsummer** — the degenerate case the
  timeline already handles, occurring in a city with one of the largest Muslim populations in
  Europe, not in the Arctic. Adhan upstream changed its own recommendation for this reason.

  This is one decision for both platforms, not something iOS settles alone, and it is a fiqh
  question as much as a UX one. Options: (a) keep middle-of-the-night everywhere, as today;
  (b) move both platforms to seventh-of-the-night above 48°, which means changing Android and
  regenerating the vectors; (c) make it a per-profile setting, which no other calculation
  input currently is. Pinned by `HighLatitudeParityTests` either way, so the decision cannot
  be made accidentally. Needs a product call before iOS ships to users above 48°.
  *(The countdown handles the collapse; Home's ribbon and surface don't — DS31 in `REVIEW-FINDINGS.md`.)*


- [ ] **GPS profile assumes the device timezone matches the fix.** "Use current location" sets
  `timezone = ZoneId.systemDefault()`, which is right at home and wrong for a traveller whose
  phone has not updated its zone: the profile then computes correct prayer instants and renders
  them in the wrong wall clock. Derive the zone from the fix's coordinates instead, as the
  city-search path already does via `detectTimezoneForLocation`. (found while testing the
  add-profile FAB flow)

## Supply-chain / security

- [x] ~~**Verify Adhan license (MIT vs Apache 2.0).**~~ → **VERIFIED 2026-09-24: MIT.** `batoulapps/adhan-java` (the repo is now `batoulapps/adhan-kotlin`) and `batoulapps/adhan-swift` both ship the MIT License, © 2016 Batoul Apps; `legal-posture.md` updated. (flagged /plan-eng-review 2026-04-19)

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
| Light / dark themes | both legible; nav bar palette bug found and fixed *(the 2026-09-24 re-check still finds dark-mode text hard-coded to light-theme colours: DS4)* |
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
- [x] `AynamaTheme` — 6-token color system, no dynamic color *(every Material role except `error` is mapped since the Phase 2 gate; light `onPrimary` still fails contrast — DS1)*
- [x] `AynamaTypography` — Fraunces + IBM Plex Sans, 8 scale slots *(the other 7 Material slots fall back to Roboto — DS5)*
- [x] `NavGraph` — 4-tab bottom nav (Home, Qibla, Tracker, Settings), placeholder screens
- [x] `AdhanWrapper` — Adhan 1.2.1, both Shafi'i and Hanafi Asr, `java.time.LocalTime`
- [x] `AdhanWrapperTest` — 15 tests (Makkah goldens, validation, polar unavailability, determinism)
- [x] Font files bundled (`fraunces.ttf`, `ibm_plex_sans.ttf`)

---

### Phase 1 — Room Data Layer ✅ DONE (PR #10)
Prerequisite for Home, Tracker, Settings, Notifications. Implement Room before building any screen.

**Gradle setup**
- [x] Add Room (`2.8.4`) + KSP to `shared-logic/build.gradle.kts`
- [x] Set `ksp { arg("room.schemaLocation", ...) }` and commit `schemas/` directory

**Entities**
- [x] `Profile(id, name, latitude, longitude, calculationMethod, asrMadhab, isGps, sortOrder)` — at most one row has `isGps=true` *(later added `timezone`, `useLocationTimezone`, `hijriOffset` (column `ramadanOffset`) and `hijriOffsetMonthKey`; the database is now version 4)*
- [x] `QazaEntry(id, prayer, date, status: enum{missed, made_up, intention_to_makeup}, profileId, updatedAt)` — per schema in Reviewer Concern #3

**DAOs + Database**
- [x] `ProfileDao` — CRUD + `Flow<List<Profile>>`; GPS constraint enforced (set GPS on new profile → clear on old)
- [x] `QazaEntryDao` — insert/update, `Flow<List<QazaEntry>>` by date range, outstanding-count query
- [x] `AynamaDatabase` — `@Database`, `exportSchema = true`, TypeConverters for enums

**Repository layer**
- [x] `ProfileRepository` — wraps `ProfileDao`, exposes `Flow<List<Profile>>` and `observeDefaultProfile()`
- [ ] GPS profile auto-refresh via `getLastKnownLocation()` (no background location permission) — **not wired.** `setGpsProfile()` exists but nothing calls it, no UI sets `isGps`, and nothing refreshes a profile's coordinates. Qibla uses the live device location instead.
- [x] `QazaRepository` — wraps `QazaEntryDao`
- [ ] Auto-mark-as-missed when the next prayer window opens — **not wired.** `autoMarkMissed()` exists but nothing calls it; the app only records what the user marks.

**Tests**
- [x] `ProfileRepositoryTest` — in-memory Room DB; create/update/delete/read; GPS constraint; Qaza cascade on profile delete
- [x] `QazaTrackerTest` — TypeConverter for status enum; mark-as-prayed write; `autoMarkMissed()` inserts MISSED only when no entry exists (nothing calls it on a schedule; see Repository layer); outstanding-count query
      (both existed but had never run: `shared-logic` named AndroidJUnitRunner without
      depending on it, so the instrumentation crashed on start. Fixed in the Phase 2 gate;
      16 tests now execute.)

---

### Phase 2 — Home Screen ✅ DONE (PR #11) — details corrected 2026-09-23
Depends on: Phase 1.

**ViewModel**
- [x] `HomeViewModel` — observes all profiles via `Flow`; calls `AdhanWrapper.getPrayerTimes()` once per day per profile (cached by profile id + date); exposes `HomeUiState`; 1-second tick drives countdown + ribbon state; `AynamaApplication` manual DI with debug seed profiles

**Prayer timeline ribbon (DESIGN.md §5)**
- [x] Countdown hero: Fraunces `display-xl` (72sp), left-aligned, signed per DESIGN.md §19 — *`main` has centred it since 2026-05-29 (`b028aa3`); #22 restored the left alignment*
- [ ] Countdown hero digits don't drift — **not met:** the bundled Fraunces has proportional figures and no `tnum` feature, so `fontFeatureSettings = "tnum"` does nothing (DS9)
- [x] Prayer line: Fraunces `display-md`, "Asr · 4:14 PM" — *below the countdown, not above it, naming the prayer the countdown refers to (the one just begun while counting up)*
- [x] Header line: IBM Plex `body-sm` — *shows "Profile · method" plus the Hijri date, not "Home · London"*
- [x] Prayer rows — 3 visual states:
  - Passed: `ink-muted` (light phases) / `parchment-muted` (dark) at **full** opacity (not 60%), ✓ glyph
  - Current: 8dp dot; saffron on dark phases, ink on light phases
  - Upcoming: `ink`, full opacity, no decoration
- [ ] Current: saffron tick that **moves down** the ribbon as time passes — **not built.** The "tick" is a static dot, and there's no vertical line (DS6).
- [x] Sunrise row: `ink-muted`, no dot, time-reference only
- [x] Tap on prayer row → opens mark-prayer bottom sheet (passed and current prayers only)
- [x] Time-of-day surface: slow cross-fade gradient per prayer phase (`animateColorAsState(tween(3000))`, 6 phases)

**Profile switcher**
- [x] Horizontal swipe between profiles (`HorizontalPager`, weather-app pager)
- [x] Dot indicator at bottom of Home content (not nav bar)
- [x] ~~Swipe past last dot → reveals "+" slot~~ → **SUPERSEDED**: replaced by the Prayers-screen FAB, which opens the profile sheet in place
- [x] Profile switcher never disrupts ribbon structure — only times and label change

**Empty + error states**
- [x] Empty state (no profiles): "Set up your first prayer profile", "Create profile" CTA — *the Kaaba mark is the 🕋 emoji placeholder (DS17)*
- [ ] Error state names cause + recovery action — **not met:** a generic "Something went wrong" plus the exception message (DS24)
- [x] Per-profile "No prayer times today" page for polar days (PR #21)
- [ ] Location stale: last-known times + "Tap to refresh" badge (deferred to Phase 6 GPS work)

**Ramadan**
- [x] Imsak row auto-appears above Fajr during Hijri Ramadan month (`android.icu.util.IslamicCalendar`)
- [x] First Ramadan open: dismissible banner, shown once per Hijri year (dismissed year in SharedPreferences) — *the copy is just "Ramaḍān Mubārak", with no Imsak mention*

**Accessibility**
- [x] TalkBack countdown — *reads "Asr in 02:14:00", or "Dhuhr began 00:15:42 ago", not the full sentence*
- [x] Prayer row announces: "Fajr 5:12 AM, passed"
- [ ] Profile switcher: accessibility action "Switch to next profile" — **not built.** Pages announce "Profile page 1 of 3: London"; navigation relies on the pager's default scroll actions.

---

### Phase 3 — Qibla Screen ✅ DONE (branch android-phase3-qibla)
Depends on: Phase 1 (ProfileRepository for active profile + prayer times for gradient phase).

**ViewModel + sensor**
- [x] `QiblaViewModel` — registers `SensorManager` listener in `onResume`, unregisters in `onPause`
- [x] `SENSOR_DELAY_UI` (~16 Hz) sampling rate; `LP_ALPHA = 0.15` tuned for ~6-sample (~300 ms) settling. UI rate chosen over GAME for power; convergence acceptable during normal turning.
- [x] Direct rotation matrix (no `remapCoordinateSystem`) for tilt-stable flat-phone bearing (T7)
- [x] Bearing from device coordinates to Kaaba (21.4225°N, 39.8262°E) — *uses the live device location when permission is granted (asked for on first open), otherwise the default profile. The request asks for fine alone, which some Android 12 releases ignore (DS33).*
- [x] Accuracy state: `HIGH` / `MEDIUM` / `LOW` / `UNRELIABLE`

**UI (DESIGN.md §5)**
- [x] Custom saffron arrow (140×200dp) inside a rotating 280dp rose — one quiet ring and an upright "N" — on the time-of-day gradient, with text on ink/parchment panels
- [x] Physics-based rotation (damping 0.8, stiffness 100) — no jitter
- [x] Degree readout below arrow — *Fraunces at 56sp, not `display-md`*
- [x] Distance to Kaaba: IBM Plex, 14sp
- [x] No cardinal N/E/S/W ring; no concentric circles; no 3D Kaaba render
- [x] Calibration warning banner (amber, persistent) when accuracy < HIGH: "Hold phone flat and move in a figure-8 to calibrate"
- [x] Rotation-vector sensor unavailable: "Compass not available on this device"

**Accessibility**
- [x] Announce bearing as direction: "Facing northeast, Qibla is to the southeast — turn right"
- [x] Announcement throttled to every 15° to avoid flooding
- [x] Short haptic pulse when within ±5° of Qibla bearing (exits at 7°)

**Tests**
- [x] `QiblaCalculatorTest` — bearing from known coordinates to Kaaba matches expected ±1°
- [x] `QiblaSensorStateTest`, `SensorAccuracyTest`, `QiblaViewModelTest` (added in later passes)

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
- [ ] "Prayed on time" only within the prayer window; greyed out for past days — **not enforced** (DS15)

**Today view**
- [x] 5 prayer rows (Fajr, Dhuhr, Asr, Maghrib, Isha — no Sunrise); prayers not yet due are dimmed and can't be tapped
- [x] Status indicators per prayer
- [x] Outstanding Qaza count shown as secondary context

**History list (DESIGN.md §16)**
- [x] Column header once at top: `F  D  A  M  I` in IBM Plex, ink-muted
- [x] Weekly section headers: Fraunces `title` — "This week" / "Last week" / "Apr 7–13"
- [x] Day row: date label + 5 squares + count ("4/5") — *15dp squares, 4dp apart; today isn't a history row, so the saffron today label never shows (DS27)*
- [x] Squares, not circles (avoids §10 anti-patterns)
- [x] Row height: 56pt minimum
- [x] Tap day row → expands inline with individual prayer rows + scheduled times
- [x] Soft aggregate line: "12 of 17 prayers on time this week" — IBM Plex `body-sm`, ink-muted, under "This week"
- [x] No calendar grid, no heat-map, no streak hero, no gamification copy

**Accessibility**
- [x] Each prayer row: "Mark Fajr prayer" (a tappable row, not a checkbox)
- [x] Outstanding count announced: "3 prayers outstanding"

---

### Phase 5 — Notifications & Adhan ✅ DONE (PR #14) — details corrected 2026-09-23
Depends on: Phase 1 (profiles + Qaza repo), Phase 2 (prayer time calculation).

**Manifest**
- [x] `USE_EXACT_ALARM` permission declared (T8)
- [x] `FOREGROUND_SERVICE_SPECIAL_USE` declared (T8)
- [ ] Play Console justification written for `FOREGROUND_SERVICE_SPECIAL_USE` (T8) — not in the repo
- [x] `BOOT_COMPLETED` receiver declared
- [x] `ACTION_TIMEZONE_CHANGED` receiver declared (also handles `TIME_SET`)
- [x] Foreground service for adhan audio declared

**AlarmScheduler**
- [x] `scheduleAll(profiles)` — exact alarms via `AlarmManager.setExactAndAllowWhileIdle` (inexact `setAndAllowWhileIdle` when exact alarms aren't allowed), for the "Alerts for" profile only (plus early reminders)
- [x] Imsak alarm = Fajr −10 min, scheduled only during Hijri Ramadan
- [x] Idempotent: calling `scheduleAll()` twice produces no duplicate alarms
- [x] Reschedule on app open/resume (covers gaps from background kill)
- [x] Daily midnight reschedule (advance to next day's times) — *at the device's midnight, so a notification profile in another zone misses the alarms between the two midnights unless a widget or an app open re-arms them (DS12)*

**BroadcastReceivers**
- [x] `BootReceiver` — `BOOT_COMPLETED` → `scheduleAll()` for all active profiles
- [x] `TimezoneReceiver` — `ACTION_TIMEZONE_CHANGED` → recalculate times + `scheduleAll()`

**Audio playback**
- [x] Foreground service handles adhan audio (prevents system kill mid-adhan)
- [ ] Adhan audio assets bundled: Makkah, Madinah, Egyptian, Turkish, Al-Aqsa, Silent — **not bundled.** `AdhanService` plays the system notification sound for any voice except None and stops after 30 s (DS14).

**OEM battery optimization**
- [x] `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent immediately after notification permission granted — *Android 13+ only; Android 8–12 are never asked (PR #34 A3)*
- [x] One-time prompt; do not re-prompt

**Tests**
- [x] `AlarmSchedulerTest` — pure `buildAlarmSchedule()`: 5 alarms (6 in Ramadan); unique request codes, which is what makes re-arming idempotent; Imsak = Fajr −10 min; master, per-prayer and Imsak toggles; offsets, fixed times, early reminders; `resolveNotificationProfile`. *(`scheduleAll()` itself and the midnight reschedule aren't unit-tested.)*
- [x] `RamadanDetectorTest` (JVM, month mapping) + `RamadanDetectionTest` (instrumented: known dates, Hijri offset and its lapse)
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

#### Phase 6a — Profile Management ✅ DONE (PR #15) — details corrected 2026-09-23
- [x] Profile list with add/edit/delete — *deleting has no confirmation (PR #15 M10)*
- [x] Profile creation — **as a fully expanded bottom sheet, not a full-screen flow**:
  1. Profile name field
  2. Location: city search (platform `Geocoder` — network-backed on most devices, not offline) OR "Use current location"
  3. Calculation method picker — *10 methods, no descriptions, default Muslim World League*
  4. Asr madhab picker (Hanafi / Shafi'i)
  5. Save → closes the sheet; from Home the pager lands on the new profile, from Settings it stays on Settings
- [ ] Profile name max 20 chars, pre-filled "Home" / "Profile 2" — **not built**
- [x] ~~Save navigates back to Home with the new profile active~~ → **superseded by the Prayers-screen FAB (#25):** saving from Home lands the pager on the new profile
- [ ] GPS profile auto-names with detected city name if user hasn't edited it — **not built.** "Use current location" fills the location only.

#### Phase 6b — Notification Settings Screen ✅ DONE
- [x] Master toggle row (56pt): saffron track on / parchment-muted track off
- [x] When OS permission denied: replace toggle with "Enable in Settings →" saffron link
- [x] When master off: every section below is hidden *(DESIGN.md §15 updated to match; the old spec kept rows visible in ink-muted)*
- [x] Per-prayer toggle rows (56pt): prayer name + time + toggle + chevron
- [x] Section headers: Fraunces `title` (20pt)
- [x] Adhan picker row → navigates to Adhan Picker screen
  - [x] 6 options with a play-preview button
  - [ ] Preview playback (10 s each, one at a time) — **not built:** it shows a "coming soon" toast until the adhan assets ship
  - [x] Radio selection, immediate (no Save button)
- [x] Ramadan Imsak row (64pt) with `parchment-muted` tint during active Ramadan
- [x] Vibration row → 3-option action sheet (Always / With sound / Never), default: With sound

#### Phase 6c — Per-Prayer Detail Sheet ✅ DONE
- [x] `ModalBottomSheet` on Android — *opens fully expanded, not ~60%*
- [x] Header: prayer name Fraunces `display-md`, centered; "Today · {time}" below
- [x] Time offset picker: −15/−10/−5/0/+5/+10/+15 min, default 0 — *a list sheet, not a wheel*
- [x] Fixed-time alert mode (Material 3 time picker), with an Offset/Fixed radio pair
- [x] Early reminder picker: Off / 5 / 10 / 15 min before, default Off
- [x] Notification profile scoping ("Alerts for"); per-prayer settings stored per profile
- [x] Per-profile "Use location time zone" (DESIGN.md §17) — on by default for new profiles
- [ ] Preview row plays a 10 s sample — **not built** (toast placeholder)

#### Phase 6d — Hijri Settings ✅ DONE
- [x] Hijri offset: −2 to +2 days with per-month auto-reset (moon-sighting accommodation) — *the auto-reset puts Ramadan on the user's Eid with +1 (DS7)*

---

### Phase 7 — Home Screen Widgets (Android Glance)
Depends on: Phase 1 (profiles), Phase 2 (prayer time calc).

**Setup**
- [x] Add `glance-appwidget` dependency to `app/build.gradle.kts`
- [x] `GlanceAppWidget` base class + `GlanceAppWidgetReceiver`
- [x] Widget metadata XML (sizes, preview, description)

**Four widget categories (DESIGN.md §25 / architecture-design.md)**
Each is separately pickable in the widget drawer and only stretches on resize (`SizeMode.Single`).
- [x] Next Prayer, 1×1 — prayer name + time + countdown + profile
- [x] Next Prayer & Dates, 2×2 — dates band + next prayer cluster
- [x] Prayer Schedule, 2×2 — dates band + six prayer times + profile
- [x] Prayer Times, 4×2 — dates + sunrise, five prayers, countdown, current prayer highlighted
- [x] All categories: tap → opens app Home on that widget's own profile
- [x] Per-widget profile selection via `WidgetConfigureActivity` (stored in each instance's Glance state)
- [ ] Real widget-picker preview images — all four share one generic placeholder drawing
- [ ] Widget text weights per DESIGN.md — RemoteViews likely renders Fraunces at its default Black instance (DS10; verify on a device)

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
- [x] ~~`vectors.yml` GitHub Actions workflow~~ → **DONE, as the `vectors` job in `ios.yml`** rather than its own file (it gates the macOS job, so it has to be in the same workflow). `scripts/validate-vectors.py` validates every `test-vectors/prayer-times/*.json` against `schema.json`. `schema.json` had claimed this was enforced since it was written; until now nothing enforced it.

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
- [x] ~~Verify Adhan license in `batoulapps/adhan-java` GitHub; update `legal-posture.md`~~ → MIT (2026-09-24)
- [ ] `./gradlew --write-verification-metadata sha256`; commit `gradle/verification-metadata.xml`
- [ ] `./gradlew :app:dependencies` — confirm zero third-party analytics SDKs

**Documentation / schema**
- [x] ~~Update `architecture-design.md` golden values table (still has PrayTimes.py values; correct to Adhan 1.2.1)~~ → done 2026-09-23
- [ ] T5: Add `tags: List<String>` or `context: ProfileContext` to `Profile` entity schema
- [ ] T8: Write Play Console `FOREGROUND_SERVICE_SPECIAL_USE` justification text

**Design conformance (from the 2026-09-23 sync; details in `REVIEW-FINDINGS.md`)**
- [ ] Launcher icon and a token-coloured launch window (DS8)
- [ ] Adhan audio assets, a notification tap action and a stop action; Imsak copy (DS14)
- [ ] Contrast: saffron text on light surfaces, the Home gradient, dark mode (DS2–DS4)
- [ ] No Material fallbacks: every type slot defined, and light `onPrimary` set to ink (DS1, DS5)
- [ ] Watch typography: IBM Plex Sans and Fraunces on the watch (DS36)
- [ ] Home's Add profile FAB clears the timeline (DS37; check on a device first)
- [ ] Hijri adjustment lapse fixed (DS7)

**Privacy / legal (see the posture docs)**
- [ ] Decide `android:allowBackup` / data-extraction rules (Qaza history is religious data)
- [ ] Disclose that location search and reverse geocoding go through the platform geocoder
- [ ] Disclose the watch sync: profiles go to a paired watch through Google Play services
- [ ] Decide the F-Droid build: `play-services-wearable` is proprietary, and F-Droid forbids Google Play Services (`legal-posture.md`)
- [ ] About screen: Adhan, Fraunces and IBM Plex Sans (SIL OFL 1.1) attributions

**Play Store prep**
- [ ] Declare `USE_EXACT_ALARM` alarm/clock category in Play Console
- [ ] Verify `com.aynama.prayertimes` has no Play Store conflict
- [ ] Trademark clearance on "aynama" (USPTO + EUIPO) before public submission
