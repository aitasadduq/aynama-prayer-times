# Review Findings — Deferred

Open findings from `/review` runs that were not addressed in the originating PR. Each finding cites `file:line` and is tagged with the originating PR + reviewer category. Close items by deleting them; do not "soft-close" with strikethroughs.

Format: `- [ ] [ID] file:line — finding. **Fix:** suggested fix. *(Origin: PR #N, /review YYYY-MM-DD)*`

---

## From PR #12 — Phase 3 Qibla Screen (`/review` 2026-05-08)

### Maintainability

- [ ] **[M1]** `android/app/src/main/java/com/aynama/prayertimes/qibla/QiblaViewModel.kt` — `QiblaUiState.Ready.rawAzimuth`, `pitch`, `roll` are only consumed by `DebugOverlay`, which is gated by `SHOW_DEBUG=false`. Dead state in production builds; widens the data class API and forces `postUpdate`/`emitReady` to thread unused values. **Fix:** Drop the three fields from `Ready` and from the `postUpdate`/`emitReady` signatures. If the debug overlay stays, compute its values inline behind `SHOW_DEBUG`. *(Origin: PR #12, /review 2026-05-08)*

- [ ] **[M2]** `QiblaViewModel.kt:261` — `qiblaDegrees = qiblaBearing.toInt()` truncates toward zero rather than rounds. Bearing 119.9° displays as 119°; 0.5° displays as 0°. Off-by-one in the headline degree readout. **Fix:** Replace with `qiblaBearing.roundToInt() % 360` to round and wrap 360→0. *(Origin: PR #12)*

- [ ] **[M3]** `QiblaViewModel.kt:47-48` — `qiblaBearing: Float` and `qiblaDegrees: Int` both stored on `Ready` with `qiblaDegrees = qiblaBearing.toInt()`. Two sources of truth. **Fix:** Remove `qiblaDegrees` from state; derive at the call site (`state.qiblaBearing.roundToInt()`). Combine with M2. *(Origin: PR #12)*

- [ ] **[M4]** `QiblaViewModel.kt:101-102` — Comment "AXIS_X/AXIS_Z remap improves portrait-vertical stability but causes singularity when flat" describes a `remapCoordinateSystem` call that no longer exists (removed in commit `3ecbf69`). Reads stale. **Fix:** Rewrite to explicitly state the absence: "Intentionally NOT calling `remapCoordinateSystem(AXIS_X, AXIS_Z)` — it would improve vertical-portrait use but introduces a singularity in the flat-phone posture this compass targets." *(Origin: PR #12)*

- [ ] **[M7]** `android/app/src/main/java/com/aynama/prayertimes/qibla/QiblaScreen.kt` — File is 614 lines holding screen entry, ReadyContent, BearingReadout, BearingChipsRow, BearingChip, DotSep, QiblaArrow, NorthGlyph, CalibrationBanner, three empty-state composables, DebugOverlay/DebugRow, plus two private helpers. Single-file monolith. **Fix:** Split into siblings under `qibla/`: `QiblaCompass.kt` (rose, arrow, north glyph), `QiblaReadout.kt` (BearingReadout + chips), `QiblaStates.kt` (Loading/NoProfile/NoSensor + CalibrationBanner), `QiblaA11y.kt` (`buildA11yDescription`, `bearingToCardinal`). Keep `QiblaScreen.kt` as the entry + `ReadyContent` orchestrator. *(Origin: PR #12)*

- [ ] **[M8]** `android/app/src/main/java/com/aynama/prayertimes/ui/theme/AynamaTypography.kt:15` — `frauncesFamily` widened from `private` to `internal` so QiblaScreen could call `frauncesFamily(32f)` / `frauncesFamily(96f)` directly. Becomes a module-wide opsz factory; ad-hoc `TextStyle`s will keep duplicating around it (already happening in QiblaScreen). **Fix:** Keep `frauncesFamily` private; expose named `TextStyle` tokens from `AynamaTypography` (e.g. `DisplayLg`, `DisplayMd`, `TitleMd`) that bake in opsz, weight, letter-spacing, and feature settings. Have QiblaScreen consume those tokens. *(Origin: PR #12)*

- [ ] **[M9]** `android/app/src/main/java/com/aynama/prayertimes/home/GradientColors.kt` — `gradientColorsFor` and `isLightPhase` live in package `com.aynama.prayertimes.home` as `internal` helpers, but QiblaScreen now imports them cross-package. The `home` package is acting as a de facto shared phase-styling module. **Fix:** Move `PrayerPhase`, `derivePhase`, `gradientColorsFor`, `isLightPhase` into a neutral package such as `com.aynama.prayertimes.ui.phase` (or `ui.theme.phase`); both home and qibla import from there. *(Origin: PR #12)*

- [ ] **[M10]** `QiblaScreen.kt:580-587` — `PrayerPhase.displayName()` defined privately in QiblaScreen.kt; HomeScreen.kt has its own `Prayer.displayName()` and almost certainly will need the `PrayerPhase` mapping too. **Fix:** Move `PrayerPhase.displayName()` next to `PrayerPhase` itself (or into the new shared phase package from M9). *(Origin: PR #12)*

- [ ] **[M11]** `QiblaViewModel.kt:85` — `lastSmoothed = -1f` sentinel for "never set" (azimuths are always ≥ 0). Brittle: easy to break if sensor ever yields negative or someone rounds. **Fix:** Replace with `Float?` (`lastSmoothed: Float? = null`); use `?:` / `!= null` checks. Removes the implicit invariant. *(Origin: PR #12)*

### Performance

- [ ] **[P1]** `QiblaScreen.kt:451` — `QiblaArrow` Canvas allocates a new `Path()` on every recomposition. ReadyContent recomposes ~16Hz from sensor state; the Path is rebuilt each time. GC pressure. **Fix:** `val path = remember { Path() }` outside Canvas; inside, `path.reset()` then rebuild. Or remember the entire built path keyed on `size`. *(Origin: PR #12)*

- [ ] **[P2]** `QiblaScreen.kt:157` — `formattedDistance = numberFormat.format(state.distanceKm.roundToInt())` recomputes every sensor tick (~16Hz) even though `state.distanceKm` only changes when the active profile changes. `NumberFormat.format` allocates a new String each call. **Fix:** `remember(state.distanceKm) { numberFormat.format(state.distanceKm.roundToInt()) }`. *(Origin: PR #12)*

- [ ] **[P3]** `QiblaScreen.kt:117` — `gradientColorsFor(state.phase)` invoked every recomposition; phase changes only at prayer-phase boundaries. The destructured `Pair` allocates each time. **Fix:** `remember(state.phase) { gradientColorsFor(state.phase) }`. *(Origin: PR #12)*

- [ ] **[P4]** `QiblaScreen.kt:127-133` — `turnHint` String concatenation rebuilt every sensor tick even when the rounded integer hasn't changed. Drives unnecessary `Text` recomposition + String allocation. **Fix:** `val turnDeg = absDelta.roundToInt()`; use `remember(isAligned, turnDeg, sign(delta))` to memoize the string. *(Origin: PR #12)*

- [ ] **[P5]** `QiblaScreen.kt:107` — `ReadyContent` reads the entire `QiblaUiState.Ready` data class directly, so any sensor-driven field change recomposes the whole tree (title strip, gradient, readout, chips, calibration banner). **Fix:** Hoist sensor-only state into a smaller composable (`RoseLayer`) that consumes only `unwrappedAzimuth`/`azimuth`/`qiblaBearing`. Pass static state (phase, distanceKm, qiblaDegrees, accuracy) to sibling composables as stable parameters; Compose's stability inference will skip the static subtrees. *(Origin: PR #12)*

### Testing

- [ ] **[T1]** `android/app/src/main/java/com/aynama/prayertimes/qibla/QiblaViewModel.kt` — Zero unit tests despite non-trivial logic: low-pass sin/cos filter convergence, azimuth unwrap delta math, profile-change cache invalidation (the C1 fix), magnetic-declination application, accuracy state mapping, null-sensor early-return path, single-flight `timesJob` dedup. **Fix:** Inject `SensorManager` (already done) + `Clock` / `() -> LocalDate`; add a JVM unit test with a fake `SensorManager` that drives `postUpdate` and asserts: (1) lowpass converges toward steady-state, (2) wrap from 359°→1° produces +2° unwrap not −358°, (3) profile change cancels in-flight `timesJob` and clears `cachedTimes`, (4) `sensor==null` transitions to `NoSensor`, (5) accuracy ints map to enum correctly, (6) midnight rollover does not flood coroutines. *(Origin: PR #12)*

- [ ] **[T2]** `QiblaScreen.kt:601` — Private `bearingToCardinal()` has untested boundary cases at exact bin edges (22.5°, 67.5°, 337.5°, etc.) and overflow inputs. Negative azimuth from unwrapped float is normalized but never tested. **Fix:** Make `bearingToCardinal` `internal`/`@VisibleForTesting`; add tests for 0°, 22°, 23°, 67°, 68°, 112°, 157°, 158°, 202°, 247°, 292°, 337°, 359°, −90°, 720°. Also test `buildA11yDescription` 'straight ahead' vs 'turn right/left' boundary at diff=5° and diff=355°. *(Origin: PR #12)*

- [ ] **[T3]** `android/app/src/main/java/com/aynama/prayertimes/home/GradientColors.kt` — Newly extracted pure functions `gradientColorsFor()` and `isLightPhase()` have zero direct tests. Used by both HomeScreen and QiblaScreen now; a regression silently breaks both surfaces. **Fix:** JVM unit test asserting (a) every `PrayerPhase` produces a non-null pair (exhaustive `when`), (b) `isLightPhase` returns true exactly for `{DHUHR, ASR, SUNRISE_TRANSITION}`, (c) specific top/bottom hex values for each phase to lock the design tokens. *(Origin: PR #12)*

- [ ] **[T4]** `android/shared-logic/src/test/java/com/aynama/prayertimes/shared/QiblaCalculatorTest.kt:48` — Pole/antimeridian tests assert only `bearing in [0, 360)` — never the actual expected bearing. Pole singularity (`atan2(0,0)=0`) and antimeridian crossing (`dLng` wraps) are exactly where a sign or modulo bug would produce a wrong-but-in-range value and pass. **Fix:** Strengthen pole/antimeridian tests: assert specific great-circle bearings; add `bearingTo(Double.NaN, 0.0).isNaN()` and `bearingTo(Double.POSITIVE_INFINITY, 0.0).isNaN()` guards. *(Origin: PR #12)*

- [ ] **[T5]** `QiblaViewModel.kt:211` — `cachedTimes` keyed by `LocalDate.now()` — hidden system-clock dependency. No test pins the clock, so a midnight-crossing during `postUpdate` silently recomputes; no test asserts cache invalidates at day rollover or that two calls within the same day reuse the cache. **Fix:** Inject a `Clock` or `() -> LocalDate` into `QiblaViewModel`; add tests for (1) two `postUpdate` calls with same fixed date hit cache once (verify `adhan.getPrayerTimes` called once via spy/fake), (2) date rollover triggers recomputation, (3) profile change nulls `cachedTimes`. *(Origin: PR #12)*

### Adversarial / Cross-cutting

- [ ] **[A1]** `QiblaScreen.kt:217-220` — `liveRegion = LiveRegionMode.Polite` + content-description that recomputes on every quantized 15° bin causes TalkBack to re-announce on every 15° rotation. The description string flips between 'turn left' and 'turn right' at the 0/180° boundary — hovering near alignment causes alternating announcements. Floods accessibility queue. **Fix:** Throttle announcements with a 2-second minimum gap; use `Assertive` only on alignment, `Polite` otherwise; add a ~10° dead-band around 0° to suppress L/R oscillation. *(Origin: PR #12)*

- [ ] **[A2]** `QiblaScreen.kt:152-154` — `buildA11yDescription` keys on `quantizedAzimuth` in `remember(...)` but uses raw `state.azimuth` in the body. Defeats the throttling intent because the string content can drift within the same quantized bin. **Fix:** Use the quantized value in the body too: `buildA11yDescription(quantizedAzimuth * A11Y_ANNOUNCE_THRESHOLD_DEG, state.qiblaBearing)`. *(Origin: PR #12)*

- [ ] **[A3]** `QiblaViewModel.kt:165` — `start()` is not idempotent. Two `ON_RESUME` events without an intervening pause (overlay dismissal, dialog) call `registerListener` twice; subsequent `unregisterListener` removes only one registration on some Android versions, leaking sensor callbacks (battery drain). **Fix:** Track a `@Volatile var registered = false`; guard `start()` with `if (registered) return; registered = true; ...`; reset on `stop()`. *(Origin: PR #12)*

- [ ] **[A4]** `QiblaViewModel.kt:113` — Active-profile selection (`profiles.firstOrNull { it.isGps } ?: profiles.minByOrNull { it.sortOrder }`) likely diverges from HomeScreen's selection logic. Qibla and Home can show different profiles' phase/gradient simultaneously. **Fix:** Centralize active-profile selection in `ProfileRepository` (e.g. `observeActiveProfile()`); reuse across Home, Qibla, and any future screens. *(Origin: PR #12)*

- [ ] **[A5]** `QiblaViewModel.kt:121-126` — `magneticDeclination` computed once at profile load and never refreshed. Drifts over years; if the user travels (GPS profile updates lat/lng), declination only refreshes via the profile observer — silent staleness if the profile doesn't re-emit. **Fix:** Recompute declination on date change or every N hours; trigger from a daily midnight broadcast or on app resume. *(Origin: PR #12)*

- [ ] **[A6]** `android/app/src/main/java/com/aynama/prayertimes/navigation/NavGraph.kt:50-53` — `fontScale = minOf(density.fontScale, 1.3f)` caps accessibility scaling on the bottom nav. Users at >1.3× system font scale lose intended scaling on the nav labels. Trade-off documented in commit `fe046e5`; flagging because it affects accessibility for low-vision users. **Fix:** Either accept the trade-off (icons compensate) and document in CLAUDE.md / accessibility notes, or restructure the nav layout to handle larger scales without truncation (e.g. icon-only mode at >1.5×). *(Origin: PR #12)*

---

## From PR #13 — Phase 4 Prayer Tracker Screen (`/review` 2026-05-09)

### Design / Scope

- [ ] **[D1]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerViewModel.kt:85-87` — History window fixed at 4 weeks (Monday of 3 weeks ago to today). Users cannot view or track prayers older than ~30 days; data persists in database but is invisible. For long-term Qada tracking or compliance audits, users need access to older history. **Fix:** Implement a dedicated `HistoryScreen` with date-range picker or scrollable infinite list of weeks. Allow filtering by status (Qada, missed, prayed on time) and date. Route from Tracker to History via NavGraph. *(Origin: PR #13, deferred)*

### Maintainability

- [ ] **[M1]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerViewModel.kt:84` — `val today = LocalDate.now()` captured inside `flatMapLatest`'s lambda — only re-evaluated when profile list re-emits. ViewModel surviving past midnight will keep stale `today`/`historyStart` values, causing incorrect "today" routing and the history window to stop sliding forward. Same hidden-clock dependency as Qibla T5. **Fix:** Inject `Clock` or `() -> LocalDate` into `TrackerViewModel`; trigger recomputation on date rollover (e.g. via a `clockFlow` similar to HomeViewModel) so `today`/`historyStart` advance without waiting for profile re-emit. *(Origin: PR #13)*

- [ ] **[M2]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerViewModel.kt:72` — `prayerTimesCache: MutableMap<Pair<Long, LocalDate>, PrayerTimesResult>` grows unbounded across the ViewModel's lifetime. Bounded in practice by the 4-week window × profile count, but never evicts stale `(profileId, date)` keys after profile changes or window slides. **Fix:** Either evict entries whose date is outside `[historyStart, today]` after each window recompute, or replace with an LRU bounded by `28 × maxProfiles`. *(Origin: PR #13)*

- [ ] **[M3]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerViewModel.kt:82` — `profiles.firstOrNull()` selects the first profile, diverging from HomeViewModel's selection logic and from QiblaViewModel's `firstOrNull { it.isGps } ?: minByOrNull { it.sortOrder }`. Three screens, three different active-profile selections; user can be looking at one profile on Home and a different profile on Tracker. Mirrors Qibla A4. **Fix:** Centralize active-profile selection in `ProfileRepository.observeActiveProfile()`; reuse across Home, Qibla, Tracker. *(Origin: PR #13)*

- [ ] **[M4]** `android/shared-logic/src/main/java/com/aynama/prayertimes/shared/data/entity/QazaEntry.kt:11` — `QazaStatus.INTENTION_TO_MAKEUP` exists in the enum and is counted by `observeOutstandingCount` (`status IN ('MISSED', 'INTENTION_TO_MAKEUP')`), but no UI surface allows the user to set it. Dead write-path. Code reading this status (e.g. `MarkPrayerSheet.isMissed`, `prayedCount` filter) treats it identically to `MISSED`. **Fix:** Either (a) add a fourth option to `MarkPrayerSheet` ("I plan to make this up later") that writes `INTENTION_TO_MAKEUP`, or (b) drop the enum value and remove it from the outstanding-count query. Decide based on whether the spec actually requires this distinction. *(Origin: PR #13)*

- [ ] **[M5]** `android/app/src/main/java/com/aynama/prayertimes/home/HomeScreen.kt:152` — `MarkPrayerSheet` invoked with `currentStatus = null` always, even when the user has already marked the prayer earlier today. Pre-selection works in TrackerScreen (line 152-159 looks up status from `state.todayRows`/`state.weeks`) but is silently dropped on Home — no consistent "current selection" affordance. **Fix:** Thread the current status through `ProfileUiState` (or via a per-prayer status map keyed off `qazaCounts` extension), pass to the sheet. *(Origin: PR #13)*

- [ ] **[M6]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerViewModel.kt:212-218` — `buildAggregate` ("X of Y prayers on time this week") sums over past days only (`allHistoryDays` ends at yesterday). Today's `PRAYED_ON_TIME` entries are excluded from the "this week" aggregate. User who just prayed Fajr this morning sees the count unchanged. **Fix:** Either include today's row in the aggregate by passing `state.todayRows` into `buildAggregate`, or rename the label to "...so far this week (excluding today)" so behavior matches text. *(Origin: PR #13)*

- [ ] **[M7]** `android/app/src/main/java/com/aynama/prayertimes/tracker/MarkPrayerSheet.kt:68,85-87` — `isMissed = currentStatus == MISSED || currentStatus == INTENTION_TO_MAKEUP` and the "I didn't pray this" tap always writes `MISSED`. If a user previously marked `INTENTION_TO_MAKEUP` and re-opens the sheet, the row appears selected as "missed", and a confirming tap silently overwrites `INTENTION_TO_MAKEUP` → `MISSED`. Lossy. **Fix:** Resolve in tandem with M4 — if the distinction matters, render two separate options; if it doesn't, drop `INTENTION_TO_MAKEUP` entirely. *(Origin: PR #13)*

### Testing

- [ ] **[T1]** `android/app/src/test/java/com/aynama/prayertimes/tracker/TrackerViewModelTest.kt` — Tests cover only pure helpers (`weekLabel`, in-line prayedCount predicate). No tests for `buildUiState`, `buildWeekSections`, `buildDayState`, `buildAggregate`, `markPrayer`, `toggleExpansion`, the flow chain, or the `Empty` branch. Regressions in week-section grouping, today-row construction, or expansion state will land silently. **Fix:** Add JVM tests with a fake `ProfileRepository`/`QazaRepository` that drive the ViewModel through Loaded/Empty transitions; assert week sections, aggregate text, expanded rows, and that `markPrayer` writes through to repository. Inject `Clock` (per M1) and a deterministic `AdhanWrapper` so prayer-time strings can be asserted. *(Origin: PR #13)*

- [ ] **[T2]** `android/app/src/main/java/com/aynama/prayertimes/home/HomeViewModel.kt:128` — Newly added `markPrayer(profileId, prayer, date, status)` has zero direct tests. Repository call is one line, but it's the entry point for the Home ribbon's tap-to-mark flow and silently ignores failures (no exception channel). **Fix:** Add a unit test with a fake `QazaRepository` asserting `markPrayer` is called with the expected args; consider exposing a `Result`/error flow if user-facing failure feedback is desired later. *(Origin: PR #13)*

### Adversarial / Cross-cutting

- [ ] **[A1]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerScreen.kt:208-232` — `HistoryColumnHeader` renders the F/D/A/M/I letters with no `contentDescription` or `semantics` block. Screen readers read each letter as "F", "D", etc. with no context. The 5 status squares per `DayRow` also have no per-square semantics — only the row-level "$dateLabel, $prayedCount of 5 prayers" is announced. **Fix:** Add `Modifier.semantics { contentDescription = "Prayer columns: Fajr, Dhuhr, Asr, Maghrib, Isha" }` to the header row; consider making each `PrayerStatusSquare` in `DayRow` a focusable element with `contentDescription = "$prayerName: $statusLabel"` for AT users who want to inspect individual prayers without expanding the day. *(Origin: PR #13)*

- [ ] **[A2]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerScreen.kt:90` — Today's prayer rows tap-target opens the mark sheet for ANY prayer regardless of whether its time has passed. A user can mark Isha as "I prayed this" at 9 AM. Home ribbon already gates this via `tappable = PASSED || CURRENT`; Tracker does not. **Fix:** Disable taps on `TodayPrayerRow` whose `scheduledTime` is in the future, or surface a confirmation when marking a not-yet-due prayer. *(Origin: PR #13)*

---

## From PR #14 — Phase 5 Notifications (`/review` 2026-05-10)

### Localization

- [ ] **[L1]** `android/app/src/main/java/com/aynama/prayertimes/notifications/NotificationHelper.kt:48,55,57` — Channel names, notification titles, and description text are hardcoded English strings ("Prayer Times", "It is time for $prayerName prayer", "Adhan", "Playing…", "Prayer time alerts", "Adhan playback service"). Localization is a later-phase concern; flagging for Phase 6+ localization pass. *(Origin: PR #14, /review 2026-05-10)*

### Design / Visual

- [ ] **[V1]** `android/app/src/main/res/drawable/ic_notification.xml` — Icon path does not render as a clear crescent moon; visual polish needed. Revisit design in a later phase to ensure the notification icon clearly signals "prayer" or "adhan" to users. *(Origin: PR #14, /review 2026-05-10)*

---

## From PR #15 — Phase 6a Settings Screen (`/review` 2026-05-16)

### Maintainability

- [ ] **[M1]** `android/app/src/main/java/com/aynama/prayertimes/settings/SettingsScreen.kt:538-552` — `getGpsLocation` uses `LocationManager.getLastKnownLocation()`, a known-unreliable API. Returns `null` when no app has recently requested location (very common on fresh devices), returns stale data (could be hours/days old), and on Android 12+ with COARSE-only permission returns "fudged" coordinates for `GPS_PROVIDER`. Users on rarely-used devices will frequently get null with no feedback. **Fix:** Add `com.google.android.gms:play-services-location`, replace with `FusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)` (returns a fresh fix, not last-known). *(Origin: PR #15, /review 2026-05-16)*

- [ ] **[M2]** `SettingsScreen.kt:397-402` — GPS silent failure. Tap "Use current location" → `isSearching = false` fires immediately, then `onGpsRequested()` launches async. If `getGpsLocation()` returns null (no provider enabled, no last-known fix), the lambda quietly does nothing and the user is left staring at the "selected" UI with the previous (or empty) label. No Snackbar, no toast, no error state. Combines with M1 — already-flaky API made invisible by no error path. **Fix:** Convert `onGpsRequested` to suspend or return `Boolean`/`Result`; set `isSearching = false` only on success; on null, surface a Snackbar ("Location unavailable. Try search instead.") and keep the search UI visible. *(Origin: PR #15)*

- [ ] **[M3]** `settings/SettingsViewModel.kt:24-34` — `save()` insert path reads `profiles.value.size` for sortOrder. Two issues: (1) race — if two saves fire concurrently they get the same sortOrder; (2) staleness — `profiles` is a `StateFlow` with `WhileSubscribed(5000)`, so if the Settings screen has been backgrounded the value may lag the DB. Unlikely in this UI (one form at a time) but brittle. **Fix:** Move sortOrder assignment into the repo: `repo.insertWithNextSortOrder(profile)` that runs `INSERT ... VALUES (..., (SELECT COALESCE(MAX(sort_order), -1) + 1 FROM profiles))` atomically, or use a transaction. *(Origin: PR #15)*

- [ ] **[M4]** `SettingsScreen.kt` — 572 lines containing: screen entry, ProfileRow, ProfileFormSheet, LocationSection, CalculationMethodPicker, AsrMadhabSelector, buildCityLabel, reverseGeocode, searchCity, getGpsLocation, formatCoord, two displayName extensions. Single-file monolith, same shape as the QiblaScreen.kt issue (PR #12 M7). **Fix:** Split into siblings under `settings/`: `ProfileForm.kt` (ProfileFormSheet, CalculationMethodPicker, AsrMadhabSelector), `LocationPicker.kt` (LocationSection, buildCityLabel), `LocationServices.kt` (reverseGeocode, searchCity, getGpsLocation — extract behind a `LocationService` interface for testability per T3). Keep `SettingsScreen.kt` as entry + ProfileRow + extensions. *(Origin: PR #15)*

- [ ] **[M5]** `SettingsScreen.kt:350-356` — "Change" button does not pre-populate the query field with the current city name. User loses their starting point and must retype from scratch to make a small correction (e.g., "London" → "London, UK"). **Fix:** Initialize `query` to `label` when transitioning into search mode: `TextButton(onClick = { isSearching = true; query = label; suggestions = emptyList() })`. *(Origin: PR #15)*

- [ ] **[M6]** `SettingsScreen.kt:322-329` — No loading indicator during the 400ms debounce + up to 5s Geocoder roundtrip. User types "Lond" and sees silence until suggestions appear. Looks broken on slow networks. **Fix:** Track an `isLookingUp` flag inside the `LaunchedEffect(query)` (set true before `withContext`, false after); render a thin `LinearProgressIndicator` or "Searching…" Text below the field while it's true. *(Origin: PR #15)*

- [ ] **[M7]** `SettingsScreen.kt:511,533` — `reverseGeocode` and `searchCity` both swallow all exceptions with `catch (_: Exception)`. Network errors, malformed responses, and Geocoder bugs all look identical to "no results". No telemetry, no Log.w. Future bug reports about "search doesn't work" will be impossible to diagnose. **Fix:** Add `Log.w("SettingsLocation", "geocode failed for query=$query", e)` (or equivalent for reverseGeocode). Don't change user-facing behavior — just instrument. *(Origin: PR #15)*

- [ ] **[M8]** `SettingsScreen.kt:201-209` — On open-for-edit, `locationLabel` starts as `""` and the reverse-geocode `LaunchedEffect(Unit)` can take up to 5s. During that window, `LocationSection` (which reads `hasSelection = locationLat != null` = true) renders the "selected" UI with a blank label. Brief flash of an empty city chip. **Fix:** Initialize `locationLabel` synchronously to `"${initial.latitude.formatCoord()}, ${initial.longitude.formatCoord()}"` so something is always visible, then let the reverse-geocode upgrade it to "City, Country". *(Origin: PR #15)*

- [ ] **[M9]** `SettingsScreen.kt:391-409` — No recovery path after the user denies the location permission with "Don't ask again" (Android 11+). Subsequent taps on "Use current location" silently re-launch `permLauncher` which fires the callback with `granted=false` and no system dialog appears. User sees nothing happen, has no idea why. **Fix:** Detect permanent denial by tracking `shouldShowRequestPermissionRationale()` before launching; if it returns false after a denial, swap the button to "Enable location in Settings" that opens `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", packageName, null) }`. *(Origin: PR #15)*

- [ ] **[M10]** `SettingsScreen.kt:107-113,292-300` — Delete actions (both swipe-to-dismiss and the "Delete profile" button in the edit sheet) fire immediately without confirmation. Easy accidental loss of a manually-tuned profile with custom calculation method + madhab. **Fix:** Wrap `vm.delete(profile)` in an `AlertDialog` confirmation: title "Delete '${profile.name}'?", body "This will also cancel scheduled notifications for this profile.", confirm/cancel buttons. *(Origin: PR #15)*

- [ ] **[M11]** `AndroidManifest.xml:13` — Declared `ACCESS_COARSE_LOCATION` only, but `getGpsLocation` tries `LocationManager.GPS_PROVIDER` in its fallback list. On Android 12+, coarse permission with GPS_PROVIDER returns "fudged" coordinates (~150m accuracy). Fine for prayer-time accuracy (`adhan-java` clamps lat/lng to 4 decimals anyway) but means the GPS_PROVIDER branch never actually gives better results than NETWORK_PROVIDER. Dead code path. **Fix:** Drop GPS_PROVIDER from the providers list (NETWORK_PROVIDER is sufficient for coarse) — OR — request `ACCESS_FINE_LOCATION` if precise GPS coords are wanted. *(Origin: PR #15)*

- [ ] **[M12]** Active-profile selection is *still* divergent across screens — HomeViewModel uses one rule, QiblaViewModel uses `firstOrNull { it.isGps } ?: minByOrNull { it.sortOrder }`, TrackerViewModel uses `profiles.firstOrNull()`. Settings doesn't pick an active profile (it edits all), but the underlying `sortOrder` it now writes via `profiles.value.size` is what other screens read. Three screens, three reads, one writer. Mirrors PR #12 A4 and PR #13 M3 — flagging again because Phase 6 is the natural moment to fix it. **Fix:** Centralize in `ProfileRepository.observeActiveProfile()` per the earlier findings. *(Origin: PR #15)*

### Testing

- [ ] **[T1]** `settings/SettingsViewModel.kt` — Zero tests. Insert path computes sortOrder + schedules alarm, update path schedules alarm, delete path cancels alarm. All untested. Regressions in sortOrder math, alarm scheduling on edit, or alarm cancellation on delete will land silently. **Fix:** JVM unit test with a fake `ProfileRepository` and a spy `AlarmScheduler` (extract its scheduling API behind an interface or test the static functions via the static-mock pattern); assert (a) insert assigns sortOrder = profiles.size, calls `scheduleForProfile` with the new id; (b) update calls `scheduleForProfile` with the updated profile; (c) delete calls `cancelForProfile(profile.id)`. *(Origin: PR #15)*

- [ ] **[T2]** `SettingsScreen.kt:485-491,554-572` — `buildCityLabel`, `formatCoord`, `CalculationMethodKey.displayName`, `AsrMadhab.displayName` are pure functions with zero tests. Mechanical but easy regressions (a renamed enum value, a swapped fallback chain). **Fix:** JVM unit tests asserting (a) `buildCityLabel` prefers `locality + countryName`, falls back through subAdminArea → adminArea, then `getAddressLine(0)`, then formatted coords; (b) `formatCoord(51.5074)` returns `"51.5074"`; (c) both `displayName()` extensions are total (every enum entry produces a non-empty, non-default string). *(Origin: PR #15)*

- [ ] **[T3]** `SettingsScreen.kt:494-552` — `reverseGeocode`, `searchCity`, `getGpsLocation` are not unit-testable as written because they directly construct `Geocoder(context)` and read static `LocationManager.NETWORK_PROVIDER`. Cannot fake in JVM tests. **Fix:** Extract a `LocationService` interface (`suspend fun reverseGeocode(lat, lng): String?`, `suspend fun searchCity(query): List<CityResult>`, `suspend fun getCurrentLocation(): Triple<Double, Double, String>?`) with a production impl wrapping the platform APIs. Inject via the SettingsViewModel factory; pass a fake in tests. Pairs naturally with M4 (file split). *(Origin: PR #15)*

### Adversarial / Cross-cutting

- [ ] **[A1]** `SettingsScreen.kt:203-209` — Editing a profile while offline causes `reverseGeocode` to return null (5s latch timeout), label falls back to `"${lat.formatCoord()}, ${lng.formatCoord()}"`. The user just shipped commit e2969b6 ("Show the city name, country name, not the lat and long") but on no-network the user *still* sees lat/lng. Defeats the fix. **Fix:** Persist the resolved label as a `locationLabel: String?` column on `Profile`; reverse-geocode only on first selection (when the user picks a city or GPS resolves) and cache the result in the DB. Edits show the persisted label even offline. *(Origin: PR #15)*

- [ ] **[A2]** `SettingsScreen.kt:498-505,521-528` — `CountDownLatch.await(5, SECONDS)` on `Dispatchers.IO` blocks an IO thread for up to 5 seconds even if the surrounding coroutine has been cancelled (e.g., user dismissed the sheet). IO dispatcher has elasticity so won't deadlock, but it's a code smell — blocks instead of suspending. **Fix:** Replace `CountDownLatch` with `suspendCancellableCoroutine`: `suspend fun reverseGeocodeAsync(context, lat, lng): String? = suspendCancellableCoroutine { cont -> geocoder.getFromLocation(...) { list -> cont.resume(list.firstOrNull()?.let(::buildCityLabel)) } }`. Properly cancellable, doesn't pin an IO thread. *(Origin: PR #15)*

- [ ] **[A3]** `settings/SettingsViewModel.kt:24-42` — No error path. If `repo.insert/update/delete` throws (DB locked, constraint violation), or `AlarmScheduler.scheduleForProfile` throws (denied SCHEDULE_EXACT_ALARM on API 31+ when canScheduleExactAlarms() goes false mid-session), the coroutine fails silently inside `viewModelScope.launch` and the sheet dismisses as if the save succeeded. User loses data with no signal. **Fix:** Wrap in `try/catch`, expose a `SharedFlow<UiError>` from the ViewModel; show a Snackbar in `SettingsScreen` when an error event fires. *(Origin: PR #15)*

- [ ] **[A4]** `SettingsScreen.kt:367-386` — Suggestion rows have no accessibility semantics. TalkBack reads each city name but doesn't announce that it's tappable. Users relying on AT will hear "London, United Kingdom" with no role/state hint. **Fix:** Add `Modifier.semantics { contentDescription = "Select $cityLabel"; role = Role.Button }` to each suggestion row; same for the selected-state Row at line 332 (label + Change button — Change button is already a TextButton so it announces correctly, but the row containing the label has no semantics). *(Origin: PR #15)*

- [ ] **[A5]** `SettingsScreen.kt:318-320` — `permLauncher` callback only calls `onGpsRequested()` on grant — it does *not* set `isSearching = false`. So if the user is in the search mode and taps GPS without prior permission, grants the permission, GPS resolves successfully, the location is selected — but the LocationSection still shows the search UI because `isSearching` was never updated. (Inverse of M2: the success path through the launcher doesn't transition out of search mode.) **Fix:** Set `isSearching = false` inside the permLauncher callback's `if (granted)` branch alongside the `onGpsRequested()` call — or fold both into a single helper that handles both states uniformly. *(Origin: PR #15)*

---

## Workflow

- When you address a finding, **delete its line** rather than checking it off — keeps the file scoped to open work.
- New `/review` runs append a section under `## From PR #N — ...` with the same structure.
- IDs (M1, P1, T1, A1, etc.) are stable per-PR; reference them in commit messages or follow-up PR titles for traceability.
