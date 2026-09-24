# Review Findings — Deferred

Open findings from `/review` runs that were not addressed in the originating PR, and from design-doc syncs (DESIGN.md vs shipped code). Each finding cites `file:line` and is tagged with its origin and category. Close items by deleting them; do not "soft-close" with strikethroughs. Line numbers were correct when each finding was written; re-check them before relying on them.

Format: `- [ ] [ID] file:line — finding. **Fix:** suggested fix. *(Origin: PR #N, /review YYYY-MM-DD)*`

---

## From PR #12 — Phase 3 Qibla Screen (`/review` 2026-05-08)

### Maintainability

- [ ] **[M1]** `android/app/src/main/java/com/aynama/prayertimes/qibla/QiblaViewModel.kt` — `QiblaUiState.Ready.rawAzimuth`, `pitch`, `roll` are only consumed by `DebugOverlay`, which is gated by `SHOW_DEBUG=false`. Dead state in production builds; widens the data class API and forces `postUpdate`/`emitReady` to thread unused values. **Fix:** Drop the three fields from `Ready` and from the `postUpdate`/`emitReady` signatures. If the debug overlay stays, compute its values inline behind `SHOW_DEBUG`. *(Origin: PR #12, /review 2026-05-08)*

- [ ] **[M7]** `android/app/src/main/java/com/aynama/prayertimes/qibla/QiblaScreen.kt` — File is 614 lines holding screen entry, ReadyContent, BearingReadout, BearingChipsRow, BearingChip, DotSep, QiblaArrow, NorthGlyph, CalibrationBanner, three empty-state composables, DebugOverlay/DebugRow, plus two private helpers. Single-file monolith. **Fix:** Split into siblings under `qibla/`: `QiblaCompass.kt` (rose, arrow, north glyph), `QiblaReadout.kt` (BearingReadout + chips), `QiblaStates.kt` (Loading/NoProfile/NoSensor + CalibrationBanner), `QiblaA11y.kt` (`buildA11yDescription`, `bearingToCardinal`). Keep `QiblaScreen.kt` as the entry + `ReadyContent` orchestrator. *(Origin: PR #12)*

- [ ] **[M8]** `android/app/src/main/java/com/aynama/prayertimes/ui/theme/AynamaTypography.kt:15` — `frauncesFamily` widened from `private` to `internal` so QiblaScreen could call `frauncesFamily(32f)` / `frauncesFamily(96f)` directly. Becomes a module-wide opsz factory; ad-hoc `TextStyle`s will keep duplicating around it (already happening in QiblaScreen). **Fix:** Keep `frauncesFamily` private; expose named `TextStyle` tokens from `AynamaTypography` (e.g. `DisplayLg`, `DisplayMd`, `TitleMd`) that bake in opsz, weight, letter-spacing, and feature settings. Have QiblaScreen consume those tokens. *(Origin: PR #12)*

- [ ] **[M9]** `android/app/src/main/java/com/aynama/prayertimes/home/GradientColors.kt` — `gradientColorsFor` and `isLightPhase` live in package `com.aynama.prayertimes.home` as `internal` helpers, but QiblaScreen now imports them cross-package. The `home` package is acting as a de facto shared phase-styling module. **Fix:** Move `PrayerPhase`, `derivePhase`, `gradientColorsFor`, `isLightPhase` into a neutral package such as `com.aynama.prayertimes.ui.phase` (or `ui.theme.phase`); both home and qibla import from there. *(Origin: PR #12)*

- [ ] **[M10]** `QiblaScreen.kt:580-587` — `PrayerPhase.displayName()` defined privately in QiblaScreen.kt; HomeScreen.kt has its own `Prayer.displayName()` and almost certainly will need the `PrayerPhase` mapping too. **Fix:** Move `PrayerPhase.displayName()` next to `PrayerPhase` itself (or into the new shared phase package from M9). *(Origin: PR #12)*

### Performance

- [ ] **[P1]** `QiblaScreen.kt:451` — `QiblaArrow` Canvas allocates a new `Path()` on every recomposition. ReadyContent recomposes ~16Hz from sensor state; the Path is rebuilt each time. GC pressure. **Fix:** `val path = remember { Path() }` outside Canvas; inside, `path.reset()` then rebuild. Or remember the entire built path keyed on `size`. *(Origin: PR #12)*

- [ ] **[P2]** `QiblaScreen.kt:157` — `formattedDistance = numberFormat.format(state.distanceKm.roundToInt())` recomputes every sensor tick (~16Hz) even though `state.distanceKm` only changes when the active profile changes. `NumberFormat.format` allocates a new String each call. **Fix:** `remember(state.distanceKm) { numberFormat.format(state.distanceKm.roundToInt()) }`. *(Origin: PR #12)*

- [ ] **[P3]** `QiblaScreen.kt:117` — `gradientColorsFor(state.phase)` invoked every recomposition; phase changes only at prayer-phase boundaries. The destructured `Pair` allocates each time. **Fix:** `remember(state.phase) { gradientColorsFor(state.phase) }`. *(Origin: PR #12)*

- [ ] **[P4]** `QiblaScreen.kt:127-133` — `turnHint` String concatenation rebuilt every sensor tick even when the rounded integer hasn't changed. Drives unnecessary `Text` recomposition + String allocation. **Fix:** `val turnDeg = absDelta.roundToInt()`; use `remember(isAligned, turnDeg, sign(delta))` to memoize the string. *(Origin: PR #12)*

- [ ] **[P5]** `QiblaScreen.kt:107` — `ReadyContent` reads the entire `QiblaUiState.Ready` data class directly, so any sensor-driven field change recomposes the whole tree (title strip, gradient, readout, chips, calibration banner). **Fix:** Hoist sensor-only state into a smaller composable (`RoseLayer`) that consumes only `unwrappedAzimuth`/`azimuth`/`qiblaBearing`. Pass static state (phase, distanceKm, qiblaDegrees, accuracy) to sibling composables as stable parameters; Compose's stability inference will skip the static subtrees. *(Origin: PR #12)*

### Testing

- [ ] **[T2]** `QiblaScreen.kt:601` — Private `bearingToCardinal()` has untested boundary cases at exact bin edges (22.5°, 67.5°, 337.5°, etc.) and overflow inputs. Negative azimuth from unwrapped float is normalized but never tested. **Fix:** Make `bearingToCardinal` `internal`/`@VisibleForTesting`; add tests for 0°, 22°, 23°, 67°, 68°, 112°, 157°, 158°, 202°, 247°, 292°, 337°, 359°, −90°, 720°. Also test `buildA11yDescription` 'straight ahead' vs 'turn right/left' boundary at diff=5° and diff=355°. *(Origin: PR #12)*

- [ ] **[T3]** `android/app/src/main/java/com/aynama/prayertimes/home/GradientColors.kt` — Newly extracted pure functions `gradientColorsFor()` and `isLightPhase()` have zero direct tests. Used by both HomeScreen and QiblaScreen now; a regression silently breaks both surfaces. **Fix:** JVM unit test asserting (a) every `PrayerPhase` produces a non-null pair (exhaustive `when`), (b) `isLightPhase` returns true exactly for `{DHUHR, ASR, SUNRISE_TRANSITION}`, (c) specific top/bottom hex values for each phase to lock the design tokens. *(Origin: PR #12)*

- [ ] **[T4]** `android/shared-logic/src/test/java/com/aynama/prayertimes/shared/QiblaCalculatorTest.kt:48` — Pole/antimeridian tests assert only `bearing in [0, 360)` — never the actual expected bearing. Pole singularity (`atan2(0,0)=0`) and antimeridian crossing (`dLng` wraps) are exactly where a sign or modulo bug would produce a wrong-but-in-range value and pass. **Fix:** Strengthen pole/antimeridian tests: assert specific great-circle bearings; add `bearingTo(Double.NaN, 0.0).isNaN()` and `bearingTo(Double.POSITIVE_INFINITY, 0.0).isNaN()` guards. *(Origin: PR #12)*

- [ ] **[T1]** `QiblaViewModel.kt:121-123,226` — `QiblaViewModelTest` now covers most of the original T1, but not two cases. (a) A profile change while profile A's times are still being fetched: the test lets A's fetch finish before switching, so `timesJob?.cancel()` and the `capturedProfileId` guard never run. (b) Declination: every test stubs `GeomagneticField.declination` to 0f. **Fix:** On a `StandardTestDispatcher`, switch profile mid-fetch and assert A's result neither fills the cache nor emits `Ready` for B; assert a non-zero declination shifts the emitted azimuth. *(Origin: PR #12; narrowed at the 2026-09-24 review)*

- [ ] **[T5]** `QiblaViewModel.kt:194` — The `Clock` is now injected, and `QiblaViewModelTest` covers the same-day cache hit and cache clearing on profile change. Still untested: a date rollover (the clock advancing past midnight between two sensor frames) triggering exactly one recomputation. **Fix:** Add that test with a mutable fake `Clock`. *(Origin: PR #12; narrowed at the 2026-09-23 design-doc sync)*

### Adversarial / Cross-cutting

- [ ] **[A1]** `QiblaScreen.kt:217-220` — `liveRegion = LiveRegionMode.Polite` + content-description that recomputes on every quantized 15° bin causes TalkBack to re-announce on every 15° rotation. The description string flips between 'turn left' and 'turn right' at the 0/180° boundary — hovering near alignment causes alternating announcements. Floods accessibility queue. **Fix:** Throttle announcements with a 2-second minimum gap; use `Assertive` only on alignment, `Polite` otherwise; add a ~10° dead-band around 0° to suppress L/R oscillation. *(Origin: PR #12)*

- [ ] **[A2]** `QiblaScreen.kt:152-154` — `buildA11yDescription` keys on `quantizedAzimuth` in `remember(...)` but uses raw `state.azimuth` in the body. Defeats the throttling intent because the string content can drift within the same quantized bin. **Fix:** Use the quantized value in the body too: `buildA11yDescription(quantizedAzimuth * A11Y_ANNOUNCE_THRESHOLD_DEG, state.qiblaBearing)`. *(Origin: PR #12)*

- [ ] **[A3]** `QiblaViewModel.kt:165` — `start()` is not idempotent. Two `ON_RESUME` events without an intervening pause (overlay dismissal, dialog) call `registerListener` twice; subsequent `unregisterListener` removes only one registration on some Android versions, leaking sensor callbacks (battery drain). **Fix:** Track a `@Volatile var registered = false`; guard `start()` with `if (registered) return; registered = true; ...`; reset on `stop()`. *(Origin: PR #12)*

- [ ] **[A5]** `QiblaViewModel.kt:121-126` — `magneticDeclination` computed once at profile load and never refreshed. Drifts over years; if the user travels (GPS profile updates lat/lng), declination only refreshes via the profile observer — silent staleness if the profile doesn't re-emit. **Fix:** Recompute declination on date change or every N hours; trigger from a daily midnight broadcast or on app resume. *(Origin: PR #12)*

- [ ] **[A6]** `android/app/src/main/java/com/aynama/prayertimes/navigation/NavGraph.kt:50-53` — `fontScale = minOf(density.fontScale, 1.3f)` caps accessibility scaling on the bottom nav. Users at >1.3× system font scale lose intended scaling on the nav labels. Trade-off documented in commit `fe046e5`; flagging because it affects accessibility for low-vision users. **Fix:** Either accept the trade-off (icons compensate) and document in CLAUDE.md / accessibility notes, or restructure the nav layout to handle larger scales without truncation (e.g. icon-only mode at >1.5×). *(Origin: PR #12)*

---

## From PR #13 — Phase 4 Prayer Tracker Screen (`/review` 2026-05-09)

### Design / Scope

- [ ] **[D1]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerViewModel.kt:85-87` — History window fixed at 4 weeks (Monday of 3 weeks ago to today). Users cannot view or track prayers older than ~30 days; data persists in database but is invisible. For long-term Qada tracking or compliance audits, users need access to older history. **Fix:** Implement a dedicated `HistoryScreen` with date-range picker or scrollable infinite list of weeks. Allow filtering by status (Qada, missed, prayed on time) and date. Route from Tracker to History via NavGraph. *(Origin: PR #13, deferred)*

### Maintainability

- [ ] **[M1]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerViewModel.kt:87` — A `Clock` is now injected, but `today` and `historyStart` are still captured inside `flatMapLatest`, so they only update when the default profile re-emits. A ViewModel that survives past midnight keeps yesterday as "today": today's rows point at the wrong date and the history window stops sliding. (`TrackerScreen.kt:76` and `HomeScreen.kt:104` also `remember { LocalDate.now() }` for the sheet date.) **Fix:** Drive recomputation from a date-rollover flow, like HomeViewModel's `clockFlow`, so `today` and `historyStart` advance on their own. *(Origin: PR #13; narrowed at the 2026-09-23 design-doc sync)*

- [ ] **[M2]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerViewModel.kt:72` — `prayerTimesCache: MutableMap<Pair<Long, LocalDate>, PrayerTimesResult>` grows unbounded across the ViewModel's lifetime. Bounded in practice by the 4-week window × profile count, but never evicts stale `(profileId, date)` keys after profile changes or window slides. **Fix:** Either evict entries whose date is outside `[historyStart, today]` after each window recompute, or replace with an LRU bounded by `28 × maxProfiles`. *(Origin: PR #13)*

- [ ] **[M4]** `android/shared-logic/src/main/java/com/aynama/prayertimes/shared/data/entity/QazaEntry.kt:11` — `QazaStatus.INTENTION_TO_MAKEUP` exists in the enum and is counted by `observeOutstandingCount` (`status IN ('MISSED', 'INTENTION_TO_MAKEUP')`), but no UI surface allows the user to set it. Dead write-path. Code reading this status (e.g. `MarkPrayerSheet.isMissed`, `prayedCount` filter) treats it identically to `MISSED`. **Fix:** Either (a) add a fourth option to `MarkPrayerSheet` ("I plan to make this up later") that writes `INTENTION_TO_MAKEUP`, or (b) drop the enum value and remove it from the outstanding-count query. Decide based on whether the spec actually requires this distinction. *(Origin: PR #13)*

- [ ] **[M5]** `android/app/src/main/java/com/aynama/prayertimes/home/HomeScreen.kt:152` — `MarkPrayerSheet` invoked with `currentStatus = null` always, even when the user has already marked the prayer earlier today. Pre-selection works in TrackerScreen (line 152-159 looks up status from `state.todayRows`/`state.weeks`) but is silently dropped on Home — no consistent "current selection" affordance. **Fix:** Thread the current status through `ProfileUiState` (or via a per-prayer status map keyed off `qazaCounts` extension), pass to the sheet. *(Origin: PR #13)*

- [ ] **[M7]** `android/app/src/main/java/com/aynama/prayertimes/tracker/MarkPrayerSheet.kt:68,85-87` — `isMissed = currentStatus == MISSED || currentStatus == INTENTION_TO_MAKEUP` and the "I didn't pray this" tap always writes `MISSED`. If a user previously marked `INTENTION_TO_MAKEUP` and re-opens the sheet, the row appears selected as "missed", and a confirming tap silently overwrites `INTENTION_TO_MAKEUP` → `MISSED`. Lossy. **Fix:** Resolve in tandem with M4 — if the distinction matters, render two separate options; if it doesn't, drop `INTENTION_TO_MAKEUP` entirely. *(Origin: PR #13)*

### Testing

- [ ] **[T1]** `android/app/src/test/java/com/aynama/prayertimes/tracker/TrackerViewModelTest.kt` — Two ViewModel-driven tests now exist, covering the current-week aggregate and today-row tappability, with an injected `Clock`. Still untested: week-section grouping across several weeks, `toggleExpansion` and expanded rows, `markPrayer` writing through to the repository, and the `Empty` branch. **Fix:** Extend the existing fake-repository harness to cover those. *(Origin: PR #13; narrowed at the 2026-09-23 design-doc sync)*

- [ ] **[T2]** `android/app/src/main/java/com/aynama/prayertimes/home/HomeViewModel.kt:128` — Newly added `markPrayer(profileId, prayer, date, status)` has zero direct tests. Repository call is one line, but it's the entry point for the Home ribbon's tap-to-mark flow and silently ignores failures (no exception channel). **Fix:** Add a unit test with a fake `QazaRepository` asserting `markPrayer` is called with the expected args; consider exposing a `Result`/error flow if user-facing failure feedback is desired later. *(Origin: PR #13)*

### Adversarial / Cross-cutting

- [ ] **[A1]** `android/app/src/main/java/com/aynama/prayertimes/tracker/TrackerScreen.kt:208-232` — `HistoryColumnHeader` renders the F/D/A/M/I letters with no `contentDescription` or `semantics` block. Screen readers read each letter as "F", "D", etc. with no context. The 5 status squares per `DayRow` also have no per-square semantics — only the row-level "$dateLabel, $prayedCount of 5 prayers" is announced. **Fix:** Add `Modifier.semantics { contentDescription = "Prayer columns: Fajr, Dhuhr, Asr, Maghrib, Isha" }` to the header row; consider making each `PrayerStatusSquare` in `DayRow` a focusable element with `contentDescription = "$prayerName: $statusLabel"` for AT users who want to inspect individual prayers without expanding the day. *(Origin: PR #13)*

---

## From PR #14 — Phase 5 Notifications (`/review` 2026-05-10)

### Localization

- [ ] **[L1]** `android/app/src/main/java/com/aynama/prayertimes/notifications/NotificationHelper.kt:48,55,57` — Channel names, notification titles, and description text are hardcoded English strings ("Prayer Times", "It is time for $prayerName prayer", "Adhan", "Playing…", "Prayer time alerts", "Adhan playback service"). Localization is a later-phase concern; flagging for Phase 6+ localization pass. *(Origin: PR #14, /review 2026-05-10)*

### Design / Visual

- [ ] **[V1]** `android/app/src/main/res/drawable/ic_notification.xml` — Icon path does not render as a clear crescent moon; visual polish needed. Revisit design in a later phase to ensure the notification icon clearly signals "prayer" or "adhan" to users. *(Origin: PR #14, /review 2026-05-10)*

---

## From PR #15 — Phase 6a Settings Screen (`/review` 2026-05-16)

### Maintainability

- [ ] **[M1]** `android/app/src/main/java/com/aynama/prayertimes/settings/SettingsScreen.kt:538-552` — `getGpsLocation` uses `LocationManager.getLastKnownLocation()`, a known-unreliable API. Returns `null` when no app has recently requested location (very common on fresh devices), returns stale data (could be hours/days old), and on Android 12+ with COARSE-only permission returns "fudged" coordinates for `GPS_PROVIDER`. Users on rarely-used devices will frequently get null with no feedback. **Fix:** Use `location/CurrentLocationProvider.kt` (`AndroidCurrentLocationProvider`), which asks the platform for a fresh fix without Play services and keeps the app F-Droid-compatible (see PR #15 M11). *(Origin: PR #15, /review 2026-05-16; fix updated at the 2026-09-24 review)*

- [ ] **[M2]** `SettingsScreen.kt:397-402` — GPS silent failure. Tap "Use current location" → `isSearching = false` fires immediately, then `onGpsRequested()` launches async. If `getGpsLocation()` returns null (no provider enabled, no last-known fix), the lambda quietly does nothing and the user is left staring at the "selected" UI with the previous (or empty) label. No Snackbar, no toast, no error state. Combines with M1 — already-flaky API made invisible by no error path. **Fix:** Convert `onGpsRequested` to suspend or return `Boolean`/`Result`; set `isSearching = false` only on success; on null, surface a Snackbar ("Location unavailable. Try search instead.") and keep the search UI visible. *(Origin: PR #15)*

- [ ] **[M3]** `settings/SettingsViewModel.kt:24-34` — `save()` insert path reads `profiles.value.size` for sortOrder. Two issues: (1) race — if two saves fire concurrently they get the same sortOrder; (2) staleness — `profiles` is a `StateFlow` with `WhileSubscribed(5000)`, so if the Settings screen has been backgrounded the value may lag the DB. Unlikely in this UI (one form at a time) but brittle. **Fix:** Move sortOrder assignment into the repo: `repo.insertWithNextSortOrder(profile)` that runs `INSERT ... VALUES (..., (SELECT COALESCE(MAX(sort_order), -1) + 1 FROM profiles))` atomically, or use a transaction. *(Origin: PR #15)*

- [ ] **[M4]** `SettingsScreen.kt` — 572 lines containing: screen entry, ProfileRow, ProfileFormSheet, LocationSection, CalculationMethodPicker, AsrMadhabSelector, buildCityLabel, reverseGeocode, searchCity, getGpsLocation, formatCoord, two displayName extensions. Single-file monolith, same shape as the QiblaScreen.kt issue (PR #12 M7). **Fix:** Split into siblings under `settings/`: `ProfileForm.kt` (ProfileFormSheet, CalculationMethodPicker, AsrMadhabSelector), `LocationPicker.kt` (LocationSection, buildCityLabel), `LocationServices.kt` (reverseGeocode, searchCity, getGpsLocation — extract behind a `LocationService` interface for testability per T3). Keep `SettingsScreen.kt` as entry + ProfileRow + extensions. *(Origin: PR #15)*

- [ ] **[M5]** `SettingsScreen.kt:350-356` — "Change" button does not pre-populate the query field with the current city name. User loses their starting point and must retype from scratch to make a small correction (e.g., "London" → "London, UK"). **Fix:** Initialize `query` to `label` when transitioning into search mode: `TextButton(onClick = { isSearching = true; query = label; suggestions = emptyList() })`. *(Origin: PR #15)*

- [ ] **[M6]** `SettingsScreen.kt:322-329` — No loading indicator during the 400ms debounce + up to 5s Geocoder roundtrip. User types "Lond" and sees silence until suggestions appear. Looks broken on slow networks. **Fix:** Track an `isLookingUp` flag inside the `LaunchedEffect(query)` (set true before `withContext`, false after); render a thin `LinearProgressIndicator` or "Searching…" Text below the field while it's true. *(Origin: PR #15)*

- [ ] **[M7]** `SettingsScreen.kt:511,533` — `reverseGeocode` and `searchCity` both swallow all exceptions with `catch (_: Exception)`. Network errors, malformed responses, and Geocoder bugs all look identical to "no results". No telemetry, no Log.w. Future bug reports about "search doesn't work" will be impossible to diagnose. **Fix:** Add `Log.w("SettingsLocation", "geocode failed for query=$query", e)` (or equivalent for reverseGeocode). Don't change user-facing behavior — just instrument. *(Origin: PR #15)*

- [ ] **[M8]** `SettingsScreen.kt:201-209` — On open-for-edit, `locationLabel` starts as `""` and the reverse-geocode `LaunchedEffect(Unit)` can take up to 5s. During that window, `LocationSection` (which reads `hasSelection = locationLat != null` = true) renders the "selected" UI with a blank label. Brief flash of an empty city chip. **Fix:** Initialize `locationLabel` synchronously to `"${initial.latitude.formatCoord()}, ${initial.longitude.formatCoord()}"` so something is always visible, then let the reverse-geocode upgrade it to "City, Country". *(Origin: PR #15)*

- [ ] **[M9]** `SettingsScreen.kt:391-409` — No recovery path after the user denies the location permission with "Don't ask again" (Android 11+). Subsequent taps on "Use current location" silently re-launch `permLauncher` which fires the callback with `granted=false` and no system dialog appears. User sees nothing happen, has no idea why. **Fix:** Detect permanent denial by tracking `shouldShowRequestPermissionRationale()` before launching; if it returns false after a denial, swap the button to "Enable location in Settings" that opens `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", packageName, null) }`. *(Origin: PR #15)*

- [ ] **[M10]** `SettingsScreen.kt:107-113,292-300` — Delete actions (both swipe-to-dismiss and the "Delete profile" button in the edit sheet) fire immediately without confirmation. Easy accidental loss of a manually-tuned profile with custom calculation method + madhab. **Fix:** Wrap `vm.delete(profile)` in an `AlertDialog` confirmation: title "Delete '${profile.name}'?", body "This will also cancel scheduled notifications for this profile.", confirm/cancel buttons. *(Origin: PR #15)*

- [ ] **[M11]** `SettingsScreen.kt:750-764` — The manifest now declares `ACCESS_FINE_LOCATION` too, and Qibla requests it. But the profile sheet still requests only `ACCESS_COARSE_LOCATION` (`SettingsScreen.kt:552-560`) while `getGpsLocation` also tries `GPS_PROVIDER`. With coarse-only, that branch returns a fudged fix on Android 12+ and throws a swallowed `SecurityException` on Android 8–11 (`SettingsScreen.kt:759`), so it never beats `NETWORK_PROVIDER`. `location/CurrentLocationProvider.kt` already picks providers by granted permission and asks for a fresh fix. **Fix:** Use `AndroidCurrentLocationProvider` in the profile sheet; this also resolves M1. *(Origin: PR #15; updated at the 2026-09-23 design-doc sync)*

### Testing

- [ ] **[T1]** `settings/SettingsViewModel.kt` — Zero tests. Insert path computes sortOrder + schedules alarm, update path schedules alarm, delete path cancels alarm. All untested. Regressions in sortOrder math, alarm scheduling on edit, or alarm cancellation on delete will land silently. **Fix:** JVM unit test with a fake `ProfileRepository` and a spy `AlarmScheduler` (extract its scheduling API behind an interface or test the static functions via the static-mock pattern); assert (a) insert assigns sortOrder = profiles.size, calls `scheduleForProfile` with the new id; (b) update calls `scheduleForProfile` with the updated profile; (c) delete cancels the deleted profile's alarms, which today it doesn't (PR #34 A2). *(Origin: PR #15)*

- [ ] **[T2]** `SettingsScreen.kt:485-491,554-572` — `buildCityLabel`, `formatCoord`, `CalculationMethodKey.displayName`, `AsrMadhab.displayName` are pure functions with zero tests. Mechanical but easy regressions (a renamed enum value, a swapped fallback chain). **Fix:** JVM unit tests asserting (a) `buildCityLabel` prefers `locality + countryName`, falls back through subAdminArea → adminArea, then `getAddressLine(0)`, then formatted coords; (b) `formatCoord(51.5074)` returns `"51.5074"`; (c) both `displayName()` extensions are total (every enum entry produces a non-empty, non-default string). *(Origin: PR #15)*

- [ ] **[T3]** `SettingsScreen.kt:494-552` — `reverseGeocode`, `searchCity`, `getGpsLocation` are not unit-testable as written because they directly construct `Geocoder(context)` and read static `LocationManager.NETWORK_PROVIDER`. Cannot fake in JVM tests. **Fix:** Extract a `LocationService` interface (`suspend fun reverseGeocode(lat, lng): String?`, `suspend fun searchCity(query): List<CityResult>`, `suspend fun getCurrentLocation(): Triple<Double, Double, String>?`) with a production impl wrapping the platform APIs. Inject via the SettingsViewModel factory; pass a fake in tests. Pairs naturally with M4 (file split). *(Origin: PR #15)*

### Adversarial / Cross-cutting

- [ ] **[A1]** `SettingsScreen.kt:203-209` — Editing a profile while offline causes `reverseGeocode` to return null (5s latch timeout), label falls back to `"${lat.formatCoord()}, ${lng.formatCoord()}"`. The user just shipped commit e2969b6 ("Show the city name, country name, not the lat and long") but on no-network the user *still* sees lat/lng. Defeats the fix. **Fix:** Persist the resolved label as a `locationLabel: String?` column on `Profile`; reverse-geocode only on first selection (when the user picks a city or GPS resolves) and cache the result in the DB. Edits show the persisted label even offline. *(Origin: PR #15)*

- [ ] **[A2]** `SettingsScreen.kt:498-505,521-528` — `CountDownLatch.await(5, SECONDS)` on `Dispatchers.IO` blocks an IO thread for up to 5 seconds even if the surrounding coroutine has been cancelled (e.g., user dismissed the sheet). IO dispatcher has elasticity so won't deadlock, but it's a code smell — blocks instead of suspending. **Fix:** Replace `CountDownLatch` with `suspendCancellableCoroutine`: `suspend fun reverseGeocodeAsync(context, lat, lng): String? = suspendCancellableCoroutine { cont -> geocoder.getFromLocation(...) { list -> cont.resume(list.firstOrNull()?.let(::buildCityLabel)) } }`. Properly cancellable, doesn't pin an IO thread. *(Origin: PR #15)*

- [ ] **[A3]** `settings/SettingsViewModel.kt:24-42` — No error path. If `repo.insert/update/delete` throws (DB locked, constraint violation), or `AlarmScheduler.scheduleForProfile` throws (denied SCHEDULE_EXACT_ALARM on API 31+ when canScheduleExactAlarms() goes false mid-session), the coroutine fails silently inside `viewModelScope.launch` and the sheet dismisses as if the save succeeded. User loses data with no signal. **Fix:** Wrap in `try/catch`, expose a `SharedFlow<UiError>` from the ViewModel; show a Snackbar in `SettingsScreen` when an error event fires. *(Origin: PR #15)*

- [ ] **[A4]** `SettingsScreen.kt:367-386` — Suggestion rows have no accessibility semantics. TalkBack reads each city name but doesn't announce that it's tappable. Users relying on AT will hear "London, United Kingdom" with no role/state hint. **Fix:** Add `Modifier.semantics { contentDescription = "Select $cityLabel"; role = Role.Button }` to each suggestion row; same for the selected-state Row at line 332 (label + Change button — Change button is already a TextButton so it announces correctly, but the row containing the label has no semantics). *(Origin: PR #15)*

- [ ] **[A5]** `SettingsScreen.kt:318-320` — `permLauncher` callback only calls `onGpsRequested()` on grant — it does *not* set `isSearching = false`. So if the user is in the search mode and taps GPS without prior permission, grants the permission, GPS resolves successfully, the location is selected — but the LocationSection still shows the search UI because `isSearching` was never updated. (Inverse of M2: the success path through the launcher doesn't transition out of search mode.) **Fix:** Set `isSearching = false` inside the permLauncher callback's `if (granted)` branch alongside the `onGpsRequested()` call — or fold both into a single helper that handles both states uniformly. *(Origin: PR #15)*

---

## From PR #16 — Phase 6b Notification Settings Screen (`/review` 2026-05-22)

### Maintainability

- [ ] **[M1]** `android/app/src/main/java/com/aynama/prayertimes/notifications/NotificationSettingsScreen.kt` — File is 466 lines containing screen entry + 7 composables (MasterToggleRow, SectionHeader, PrayerToggleRow, AdhanVoiceRow, ImsakRow, VibrationRow, VibrationSheet). Same monolith pattern as PR #12 M7 (QiblaScreen) and PR #15 M4 (SettingsScreen). **Fix:** Split into siblings under `notifications/`: `NotificationRows.kt` (MasterToggleRow, PrayerToggleRow, ImsakRow, VibrationRow, AdhanVoiceRow), `VibrationSheet.kt`, and keep `NotificationSettingsScreen.kt` as the orchestrator. *(Origin: PR #16)*

- [ ] **[M2]** `NotificationSettingsScreen.kt:251-258` — `PrayerToggleRow.masterEnabled: Boolean` parameter is dead. The single call site (line 132) always passes `true`, and the upstream `if (state.masterEnabled)` guard (line 124) already ensures the row only renders when master is on. The `else InkMuted` branch on `nameColor` (line 258) is unreachable. **Fix:** Drop the `masterEnabled` parameter from `PrayerToggleRow`; collapse `nameColor` to `MaterialTheme.colorScheme.onSurface` directly. *(Origin: PR #16)*

- [ ] **[M3]** `AlarmScheduler.kt:55-58` — `scheduleForProfile` hardcodes the SharedPreferences name `"aynama_prefs"` and constructs a fresh `NotificationPreferences` per call, bypassing `AynamaApplication.notificationPreferences`. Two instances coexist for the same backing store. **Fix:** Accept `NotificationPreferences` as a parameter on `scheduleAll`/`scheduleForProfile`, or read it from `context.applicationContext as AynamaApplication`. Eliminate the literal string. *(Origin: PR #16)*

- [ ] **[M4]** `NotificationSettingsViewModel.kt:107-142` — `loadPrayerTimes()` runs only inside `init`. It doesn't refresh on day rollover (the times shown become stale at midnight), profile changes (no `observeAll().collect` subscription, only `.first()`), or screen resume. **Fix:** Inject a `Clock` and recompute on date rollover, as HomeViewModel's `clockFlow` does; subscribe to `repo.observeAll()` via `flatMapLatest` and recompute `prayerRows` on each emission; trigger refresh on `ON_RESUME` in addition to the existing `refreshPermission`/`refreshFromPrefs` calls. *(Origin: PR #16)*

- [ ] **[M5]** `NotificationSettingsViewModel.kt:109,146` — Both `loadPrayerTimes()` and `rescheduleAll()` use `repo.observeAll().first()` instead of subscribing. Adding a profile in Settings while NotificationSettings is on the back stack doesn't update displayed times or rescheduled alarms until the user navigates back. **Fix:** Restructure as a single Flow chain that subscribes once and reacts to profile changes, prefs changes, and date rollover. *(Origin: PR #16)*

- [ ] **[M6]** `NotificationSettingsViewModel.kt:59-61,95-105` — `refreshFromPrefs()` calls `loadPrefs()` which updates `permissionGranted, masterEnabled, imsakEnabled, adhanVoice, vibration` — but does NOT refresh per-prayer `enabled` flags from prefs. Today this is safe because per-prayer enabled is only written via `setPrayerEnabled` (which mirrors state). The moment any other write path appears (quick-settings tile, widget, deeplink), the rows desync. **Fix:** Also re-read each row's `prefs.isPrayerEnabled(index)` in `loadPrefs`, rebuilding `prayerRows.enabled` against the current pref values. *(Origin: PR #16)*

- [ ] **[M7]** `NotificationSettingsScreen.kt:300-330` and `AdhanPickerScreen.kt:93-139` — Two distinct `@Composable AdhanVoiceRow` functions with the same name. One is the entry-row summary on the settings screen, the other is a selectable picker row. Compiler scopes them per-file, but readers grepping for `AdhanVoiceRow` hit both and have to disambiguate. **Fix:** Rename the settings-screen one to `AdhanVoiceEntryRow` (or `AdhanVoiceSummaryRow`). *(Origin: PR #16)*

- [ ] **[M8]** `AynamaApplication.kt:25` and `AlarmScheduler.kt:57` — Magic string `"aynama_prefs"` duplicated. Two writers to the same prefs file by-string-match; drift one and you silently get two prefs files. **Fix:** Extract `const val SHARED_PREFS_NAME = "aynama_prefs"` to a single location (e.g., `AynamaApplication` companion object); both call sites reference the const. *(Origin: PR #16)*

- [ ] **[M9]** `NotificationSettingsViewModel.kt:134` — `name = PRAYER_NAMES[index]!!` force-unwrap. Safe today because `prayerEntries` only contains the 5 prayer indices that exist in `PRAYER_NAMES`. Brittle if the iteration set ever expands to include `PRAYER_INDEX_IMSAK` or future additions. **Fix:** Replace `!!` with `?: return@map null` and filter nulls, or use `PRAYER_NAMES[index] ?: "Prayer"` as a defensive fallback. *(Origin: PR #16)*

### Performance

- [ ] **[P1]** `NotificationSettingsScreen.kt:228-235,282-288,365-372` — `SwitchDefaults.colors(checkedTrackColor = Saffron, ...)` is rebuilt on every recomposition in three places (MasterToggleRow, PrayerToggleRow, ImsakRow). The colors are constant. **Fix:** Hoist to a file-level `@Composable` function returning a `remember { }`-cached `SwitchColors`, or define `val saffronSwitchColors: @Composable () -> SwitchColors = { remember { SwitchDefaults.colors(...) } }` and reuse. *(Origin: PR #16)*

- [ ] **[P2]** `AdhanPickerScreen.kt:76` and `NotificationSettingsScreen.kt:435` — `AdhanVoice.entries` / `VibrationMode.entries` evaluated inside composable bodies on every recomposition. Cheap (enum entries), but conventional pattern is `remember { AdhanVoice.entries }` to make the intent explicit and avoid future regressions if `.entries` ever becomes non-trivial. *(Origin: PR #16)*

### Testing

- [ ] **[T1]** `android/app/src/main/java/com/aynama/prayertimes/notifications/NotificationPreferences.kt` — Zero tests. Pure SharedPreferences key/value mapping with enum parsing, defaults, and an `else "notif_prayer_$index"` fallback for unknown indices. **Fix:** JVM unit test with an in-memory `SharedPreferences` fake (e.g., MockSharedPreferences from Robolectric or hand-rolled `Map<String, Any?>`-backed impl) asserting: (a) all defaults match the spec (masterEnabled=true, vibration=WITH_SOUND, etc.), (b) round-trip set/get for every property, (c) enum parsing returns the default when stored string is malformed/missing, (d) `isPrayerEnabled` returns true by default for all known indices and writes to distinct keys. *(Origin: PR #16)*

- [ ] **[T2]** `android/app/src/main/java/com/aynama/prayertimes/notifications/NotificationSettingsViewModel.kt` — Zero tests. All state mutations (`setMaster`, `setPrayerEnabled`, `setImsakEnabled`, `setVibration`, `setAdhanVoice`), the reschedule-on-change side effect, the prefs-refresh-on-resume flow, and the `loadPrayerTimes` Asr-madhab branch are untested. **Fix:** Extract `AlarmScheduler` behind an interface (`AlarmSchedulerApi`) so it's mockable; inject a fake `NotificationPreferences` + fake `ProfileRepository` + spy `AlarmSchedulerApi`; test (a) each setter writes to prefs and updates state, (b) toggles trigger exactly one reschedule call, (c) Hanafi vs Shafii produces different Asr times in the rows, (d) `refreshPermission` reads `NotificationManagerCompat` and updates state. *(Origin: PR #16)*

- [ ] **[T3]** `NotificationSettingsScreen.kt`, `AdhanPickerScreen.kt` — Zero Compose UI tests. Conditional rendering (master-off hides sections), permission-denied UI swap (Switch → "Enable in Settings →" link), and Ramadan-tinted Imsak row are visual gates that regress silently. **Fix:** `@RunWith(AndroidJUnit4)` Compose UI tests with `createComposeRule()` asserting (a) master off → no "PRAYERS"/"ADHAN"/"OTHER" headers visible, (b) permission denied → "Enable in Settings →" button visible, no Switch, (c) Ramadan=true → ImsakRow background equals ParchmentMuted. *(Origin: PR #16)*

### Adversarial / Cross-cutting

- [ ] **[A4]** `SettingsScreen.kt:101-104` — `NotificationsEntryRow` placed at the TOP of the LazyColumn, above the "Profiles" header. Profiles is the primary content of the Settings screen; promoting Notifications above it changes the screen's information hierarchy. Reconsider — Notifications might fit better below Profiles, or in a dedicated bottom-of-screen "Preferences" group with future entries (Theme, Language, About). *(Origin: PR #16)*

- [ ] **[A5]** `NotificationSettingsScreen.kt:279-289,363-373` — Switches have no a11y content description that includes the prayer name. TalkBack reads "Switch, On" / "Switch, Off" with no context. Users navigating with screen readers can't tell which prayer the focused Switch belongs to without backing up to read the preceding label. **Fix:** Wrap each Switch with `Modifier.semantics { contentDescription = "${row.name} notifications, ${if (row.enabled) "on" else "off"}" }` (or use `Modifier.toggleable` with a `Role.Switch` + onClickLabel for proper semantics). *(Origin: PR #16)*

- [ ] **[A6]** `AdhanPickerScreen.kt:126-138` — Play icon button renders identically to a working button but only shows a Toast "Adhan assets coming soon". Users will tap repeatedly expecting playback. **Fix:** Until assets ship, render the play button with `enabled = false` + 40% alpha tint, OR remove the play button entirely and add a top-of-screen banner: "Preview coming when adhan assets ship." *(Origin: PR #16)*

- [ ] **[A7]** `NotificationSettingsScreen.kt:66` and `AdhanPickerScreen.kt:49` — Each screen creates its own `NotificationSettingsViewModel` via `viewModel(factory = ...)`. They're on separate `NavBackStackEntry`s, so two VM instances coexist while the picker is open. State sync works through SharedPreferences + `ON_RESUME` refresh (cheap), but it's wasteful and the two VMs both call `loadPrayerTimes()` independently. **Fix:** Scope the VM to a shared parent route — define a nested navigation graph for the "settings/notifications" subtree, scope the VM to that graph's `NavBackStackEntry`, both screens share the same instance. *(Origin: PR #16)*

- [ ] **[A9]** `NotificationSettingsViewModel.kt` — Notification-profile selection (`resolveNotificationProfile`) uses `profiles.minByOrNull { it.sortOrder }` and does not prefer GPS profiles. `ProfileRepository.observeDefaultProfile()` (added in post-phase-6 review pass) uses `firstOrNull { it.isGps } ?: minByOrNull { it.sortOrder }`. Notifications intentionally keeps its own picker (so per-notification-profile prefs are preserved), but the default it falls back to diverges from `observeDefaultProfile`. If a user has a GPS profile and expects their notifications to match what Qibla and Tracker show, they won't by default. **Fix:** When `resolveNotificationProfile` finds no stored preference, call `observeDefaultProfile()` rather than `observeAll().map { it.minByOrNull { it.sortOrder } }`. *(Origin: PR #16)*

- [ ] **[A10]** `NotificationSettingsScreen.kt:338,371` — During Ramadan, `ImsakRow` renders with `background = ParchmentMuted`. But the Switch's `uncheckedTrackColor` is ALSO `ParchmentMuted`. When the Imsak toggle is off during Ramadan, the Switch off-state nearly disappears against the row background — only the thumb circle is visible, so users can't tell at a glance whether Imsak is enabled. **Fix:** Either use a different background for the Ramadan tint (e.g., a 50% blend of `ParchmentMuted` with `Parchment`), or give the Imsak Switch a distinct uncheckedTrackColor when on the tinted background. *(Origin: PR #16)*

- [ ] **[A11]** No "Send test notification" affordance anywhere in the Notifications settings. Users must wait for an actual prayer time to verify their settings work. After picking an adhan voice + vibration mode + permission grant, the next signal that anything was correctly configured comes hours later. **Fix:** Add a "Test notification" `TextButton` at the bottom of the screen that fires `NotificationHelper.showPrayerNotification(context, "Test", testNotifId)` + starts `AdhanService` once. Helpful for QA and for users who just changed settings. *(Origin: PR #16)*

---

## From PR #17 — Phase 6c per-prayer detail, fixed-time mode, profile scoping (`/review` 2026-05-23)

### Ask / Design

- [ ] **[A5]** `android/app/src/main/java/com/aynama/prayertimes/notifications/NotificationSettingsScreen.kt` — `ProfilePickerSheet`'s selected-profile name is colored Saffron AND a checkmark is shown. Saffron name + checkmark is double-indicating; DESIGN.md convention for pickers (AdhanPicker, VibrationSheet) uses checkmark only. **Fix:** Drop the Saffron color on the selected name; keep only the Saffron checkmark to indicate selection. *(Origin: PR #17, /review 2026-05-23)*

- [ ] **[A6]** `android/app/src/main/java/com/aynama/prayertimes/home/HomeViewModel.kt` — `prayerTimesCache: mutableMapOf<PrayerCacheKey, PrayerTimesResult>()` is unbounded and never evicts. Previous per-profile cache (Map keyed by `Long`) was naturally bounded by profile count; the new `PrayerCacheKey(profileId, date, lat, lng, method, timezone)` key accumulates one entry per unique date+location combo across the ViewModel's lifetime. **Fix:** Bound the cache — either `LinkedHashMap(16, 0.75f, true)` limited to N entries via `removeEldestEntry`, or evict keys whose date is not today on each cache access. A bound of 10 entries covers all realistic concurrent profiles + a few days of background recalculation. *(Origin: PR #17, /review 2026-05-23)*

### Notes

- [ ] **[N1]** `android/app/src/main/java/com/aynama/prayertimes/notifications/NotificationSettingsScreen.kt` — Developer comments left in the vibration/prayer-index wiring block: `// Vibration sheet — keep local state since we replaced the onClick placeholder above` and `// Actually wire vibration properly:` precede `val selectedIndex = state.selectedPrayerIndex`. Dead commentary. **Fix:** Remove the two comment lines. *(Origin: PR #17, /review 2026-05-23)*

- [ ] **[N2]** `android/app/src/main/java/com/aynama/prayertimes/notifications/PrayerDetailSheet.kt:274` — `val valueColor = if (isActive) InkMuted else InkMuted` — both branches produce the same value. Dead branch. **Fix:** Replace with `val valueColor = InkMuted`, unless the §15 sign-off keeps April's rule (active value `ink`), in which case use `if (isActive) Ink else InkMuted`. *(Origin: PR #17, /review 2026-05-23)*

- [ ] **[N4]** `android/app/src/main/java/com/aynama/prayertimes/notifications/AlarmScheduler.kt` — `cancelForProfile` iterates `0 until REQUEST_CODE_MULTIPLIER` (= 20) to cancel PendingIntents, but the highest slot actually used is 14 (early-reminder base index 10 + prayer index 4). The bound is a hard constant, not derived from `EARLY_REMINDER_BASE_INDEX + MAX_PRAYER_INDEX`. If slot layout changes, the cancel loop and the constant will drift silently. **Fix:** Replace the literal `20` upper bound with `EARLY_REMINDER_BASE_INDEX + PRAYER_NAMES.size` so the cancel range is always exactly the set of possible slots. *(Origin: PR #17, /review 2026-05-23)*

- [ ] **[N5]** `android/app/src/main/java/com/aynama/prayertimes/notifications/AlarmScheduler.kt` — `REQUEST_CODE_MULTIPLIER` changed from 10 → 20 in this PR. Existing `PendingIntent`s registered with the old multiplier (10×profileId + index) are not cancelled by the new `cancelForProfile` (which cancels 20×profileId + rawIndex). Users who had alarms scheduled before this update will have ghost alarms firing in the OS until the device reboots or the OS expires them. **Fix:** On first run after upgrade, cancel the old request codes explicitly: iterate `for (rawIndex in 0 until 10)` with `OLD_MULTIPLIER = 10` and call `PendingIntent.cancel` on `profileId * 10 + rawIndex`. Guard with the same V1 migration flag used by `NotificationPreferences.migrateFromV1`. *(Origin: PR #17, /review 2026-05-23)*

- [ ] **[N6]** `android/app/src/main/java/com/aynama/prayertimes/notifications/PrayerDetailSheet.kt:60-71` — `formatFixedTime` formats in 12h AM/PM unconditionally. If `is24Hour` is true for the device, the fixed-time display string on the main row (outside the picker) still shows 12h format, inconsistent with the now-corrected `TimePickerDialog`. **Fix:** Pass the 24h flag into `formatFixedTime` (or a new `formatFixedTime(minutesOfDay, is24Hour)` overload) and format accordingly: `"%d:%02d".format(h, m)` when 24h. *(Origin: PR #17, /review 2026-05-23)*

---

## From PR #21 — Notion bug list fixes (`/review` 2026-08-06)

### Correctness

- [ ] **[C1]** `android/shared-logic/src/main/java/com/aynama/prayertimes/shared/AdhanWrapper.kt:52` — **Unrecoverable crash loop above the Arctic Circle.** On any day the sun does not cross the horizon (polar day *or* polar night), adhan-java returns `null` for **all six** times — fajr, sunrise, dhuhr, asr, maghrib, isha — and `getPrayerTimes` dereferences them directly. `fajr` is simply the first: `NullPointerException: fajr must not be null`. Pre-existing; untouched by PR #21, surfaced by its new high-latitude widget test (pinned to 65°N to stay green).

  **Measured (adhan 1.2.1, MWL, 2026).** Onset between **65.5°N and 65.75°N** on Jun 21 — south of the true Arctic Circle (66.56°N) because refraction extends the midnight-sun band. Dec 21: ok at 67°N, fails at 69.65°N. Southern hemisphere symmetric. Days affected in 2026: Longyearbyen 78.2°N **242 days**, Utqiaġvik 71.3°N **148**, Tromsø 69.65°N **118** (May 18–Jul 25, Nov 27–Jan 14), Murmansk 68.97°N **104**, Bodø 67.28°N **44**, Rovaniemi 66.5°N **31**. Luleå 65.6°N, Reykjavík 64.15°N and Anchorage 61.2°N are unaffected.

  **All three `HighLatitudeRule` values fail identically** (MIDDLE_OF_THE_NIGHT — the default — SEVENTH_OF_THE_NIGHT, TWILIGHT_ANGLE), so the library's high-latitude machinery is *not* a fix: it reshapes fajr/isha when twilight is not reached, but cannot invent a sunrise that never happens. **All 11 calculation methods fail identically.**

  **Observed on emulator (Tromsø profile, 2026-06-21).** Two distinct severities: (1) with the Arctic profile merely *present*, `HomeViewModel`'s `.catch` renders "Something went wrong / fajr must not be null" — and because the `combine` maps over every profile, one Arctic profile blanks the home screen for **all** profiles. Widgets and alarms bound to other profiles keep working. (2) with the Arctic profile selected as the **notification profile**, `MainActivity.onResume` → `AlarmScheduler.scheduleAll` → `scheduleForProfile` throws on a scope with no `CoroutineExceptionHandler`: `FATAL EXCEPTION`, process dead, and it repeats on every launch because `onResume` reschedules. The user cannot reach Settings to undo it — unrecoverable without clearing app data.

  **Defensive half — DONE** (PR #21, commit following `ecfc707`). `AdhanWrapper` now throws a typed `PrayerTimesUnavailableException` instead of letting a bare NPE escape; `AlarmScheduler.scheduleAll` and `PrayerWidgetScheduler.scheduleForBoundProfiles` log-and-skip per profile; `appScope` has a `CoroutineExceptionHandler`; `QiblaViewModel` and `NotificationSettingsViewModel` no longer throw out of `viewModelScope`; the widget renders an "unavailable" state; and the home pager isolates failure to the affected page via `ProfilePage.Unavailable`. Verified on emulator with a Tromsø profile set as the notification profile on 2026-06-21: zero `FATAL EXCEPTION`, all four tabs usable, other profiles unaffected.

  **Behavioural half — STILL OPEN, needs a decision.** The app currently tells the user there are no times rather than showing any. Pick a convention for days with no sunrise: `aqrab al-bilad` (nearest latitude — compute at 45°), `aqrab al-ayyam` (nearest day with a valid schedule), or fixed proportions of the day. Implement it in `AdhanWrapper` behind a documented helper so `PrayerTimesUnavailableException` becomes unreachable for real locations, then extend `PrayerWidgetTest.widget state stays coherent at high latitude in midsummer` from 65°N to 69.65°N and drop the note in that test explaining why it is pinned. *(Origin: PR #21, /review 2026-08-06)*

### Design

- [ ] **[D1]** `android/app/src/main/java/com/aynama/prayertimes/qibla/QiblaScreen.kt:135` — Qibla surface boxes use `Parchment` (#F2EAD8) as `boxBg` on all light phases, but the light-phase gradient tops are near-identical: DHUHR top is #F2EAD8 (1.00:1, pre-existing) and the SUNRISE_TRANSITION top became #EDE1C5 in this PR (1.08:1). Verified on emulator at 10:00: the panels remain **legible** because the vertical gradient darkens beneath them and the ink text on each box is unaffected — so this is a reduction in surface definition, not a legibility failure. But PR #21 extends the affected window from the Dhuhr block to sunrise→Dhuhr as well. **Fix:** derive `boxBg` per phase, or give light-phase boxes a hairline border/elevation independent of fill, so surfaces stay delineated whenever `gradTop ≈ Parchment`. Covers Dhuhr and sunrise in one change. *(Origin: PR #21, /review 2026-08-06)*

### Maintainability

- [ ] **[M12]** `android/app/src/main/java/com/aynama/prayertimes/widgets/PrayerWidget.kt` — Prayer identity is still a bare `String` shared between `scheduleRows()`, `timelineEvents()`, and the renderer. PR #21 introduced `SUNRISE_NAME` to stop the highlight logic drifting, but the other five names remain duplicated literals across the two builders. **Fix:** introduce a `PrayerId` enum (FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA) carried by both `TimelineEvent` and `WidgetScheduleRow`, with display names resolved at render time; matching becomes compiler-checked and localisable. *(Origin: PR #21, /review 2026-08-06)*

---

## From design-doc sync — Android v1 vs DESIGN.md (2026-09-23)

The Android v1 code was read end to end against DESIGN.md, which was then re-baselined. Where the app deliberately changed the design, the spec was updated. Where the app breaks a DESIGN.md rule, the rule stayed and the breach is listed here. DESIGN.md §21 is the index. When you fix one, first grep the docs for its ID (`grep -rnw 'DS7' --include='*.md' .`) and update every sentence that describes the defect as current, including TODOS.md items and architecture-design.md notes. Then delete the finding here and remove its ID from the §21 row, deleting the row once no IDs remain.

How the evidence was gathered:
- **Contrast:** WCAG 2.x relative luminance.
- **Material 3 fallbacks:** read from the Compose Material 3 token sources (`ColorLightTokens`, `ColorDarkTokens`, `PaletteTokens`, `TypefaceTokens`, component tokens).
- **Fonts:** the bundled TTFs were parsed (cmap, hmtx, GSUB, fvar, OS/2 tables).
- **ICU:** run on the JVM with ICU4J 74.2.
- **Not screenshotted:** nothing could be run on a device or emulator here. Items that depend on rendering say so.

### Colour & contrast

- [ ] **[DS1]** `android/app/src/main/java/com/aynama/prayertimes/ui/theme/AynamaTheme.kt:17-45` — Only 12 Material colour roles are set. Every other role falls back to Material's baseline palette:
  - light: `surfaceContainer` `#F3EDF7`, `surfaceContainerLow` `#F7F2FA`, `surfaceContainerHigh` `#ECE6F0`, `surfaceContainerHighest` `#E6E0E9`, `secondaryContainer` `#E8DEF8`, `secondary` `#625B71`, `tertiaryContainer` `#FFD8E4`, `errorContainer` `#F9DEDC`
  - dark: `#211F26`, `#1D1B20`, `#4A4458`, `#633B48`

  These tint the navigation bar's container, active pill and selected label (`NavGraph.kt:56`), every `ModalBottomSheet`, the calculation-method dropdown, the time-picker dialog (lavender dial, pink AM/PM), and the swipe-to-delete background (`SettingsScreen.kt:143`). That breaks DESIGN.md §10's "no purple/indigo". Separately, `onPrimary` is parchment in light mode, which is 2.99:1 on saffron.

  **Fix:** Set every role in both schemes from the tokens: `surfaceContainer*` as parchment/ink steps, `secondaryContainer` as parchment-muted, `tertiary*` from the saffron family, `error*` from a new destructive pair (DS19). Make `onPrimary` ink in light mode. Add a JVM test that fails if any role still equals its Material baseline value. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS2]** Saffron (`#B87A2E`) text on parchment is **2.99:1** — below AA at every size. It appears here:
  - `MarkPrayerSheet.kt:112` — the selected option
  - `NotificationSettingsScreen.kt:271` — profile picker name
  - `NotificationSettingsScreen.kt:353` — "Enable in Settings →"
  - `NotificationSettingsScreen.kt:582` — vibration sheet
  - `PrayerDetailSheet.kt:219` — "Preview adhan"
  - `PrayerDetailSheet.kt:335` — "OK"
  - `PrayerDetailSheet.kt:387` and `:441` — offset and early-reminder sheets
  - `WidgetConfigureActivity.kt:199,206` — via `colorScheme.primary`, and its "Cancel" `TextButton` (`:170-175`)
  - Material defaults that use `primary` as text: the "Change" `TextButton` (`SettingsScreen.kt:508`) and focused `OutlinedTextField` labels
  - widgets: `widget_next_prayer.xml:23`, `widget_next_prayer_dated.xml:66`, and the 4×2 column highlight at `PrayerWidget.kt:552-553` (the 4×2's Sunrise highlight, `:538-539`, sits on the ink band at 4.85:1 and passes)

  The Qibla screen already does this right: it uses `SaffronInk` (4.92:1) on light phases.

  **Fix:** Use `SaffronInk` for saffron text on light surfaces. Add `aynama_saffron_ink` to `colors.xml` for the widgets. Override text colours on Material components that default to `primary`, but keep saffron for fills. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS3]** `HomeScreen.kt:326-337` and `:237-243`, with `GradientColors.kt:5-12` — Timeline text is 20 sp at weight 500, which is body size, so it needs 4.5:1. Estimated at row positions on a ~700 dp pager, it fails in these places:
  - **Asr phase:** passed rows and the Sunrise row in `ink-muted`, about 2.4–2.8:1. The Qaḍā line (ink at 60%) is about 2.75:1.
  - **Sunrise phase:** about 3.9–4.1:1.
  - **Dhuhr phase:** the Qaḍā line, 4.17:1.
  - **Fajr and Maghrib:** the current row in saffron, about 4.36:1.

  DESIGN.md §3 has the full table.

  **Fix:** Give each phase muted and current colours that pass against the gradient under each row — for example, ink at full opacity with the ✓ for passed rows on Asr, and a lighter accent on Fajr and Maghrib (those gradients are dark, so a darker accent would lower contrast). Sunrise and a passed Imsak use the muted token too (`HomeScreen.kt:397-398`, `:429-431`); if they switch to ink, Sunrise, which has no mark, reads like an upcoming prayer, so give it another cue. Or put a quiet scrim behind the ribbon. Add a JVM contrast test over phase × row position. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS4]** Dark mode: utilitarian screens hard-code light-theme colours.
  - `PrayerDetailSheet.kt:104` sets the sheet header to `Ink`, which is about 1:1 on the dark sheet — invisible.
  - `AdhanPickerScreen.kt:116` (labels) and `:151` (radio stroke) use `Ink` on an ink background — invisible.
  - `InkMuted` is used for secondary text on ink (3.02:1) across the Tracker, Notifications, the profile sheet's time-zone sub-label, and the mark sheet.
  - The Ramadan Imsak tint (`NotificationSettingsScreen.kt:471`) puts `ParchmentMuted` behind parchment text: 1.37:1.

  **Fix:** Use `colorScheme.onSurface` and `onSurfaceVariant` rather than token constants on utilitarian screens. Add a dark-mode Compose or screenshot test. *(Origin: design-doc sync 2026-09-23)*

### Typography

- [ ] **[DS5]** `AynamaTypography.kt:47-96` — Only 8 of Material's 15 type slots are defined. `headlineLarge`, `headlineSmall`, `titleLarge`, `titleMedium`, `titleSmall`, `labelLarge` and `labelSmall` fall back to `FontFamily.SansSerif` — Roboto on stock Android. Where that shows:
  - **`labelLarge`** styles every `Button`, `TextButton`, `OutlinedButton` and `DropdownMenuItem`. So Roboto renders in "Save", "Create profile", "Delete profile", "Use current location", "Change", Shāfiʻī/Ḥanafī, "OK"/"Cancel", the widget-config "Cancel", and the method list.
  - **`labelSmall`** is used directly at `SettingsScreen.kt:205,213` (the Hijri adjustment buttons) and `PrayerDetailSheet.kt:148` ("ALERT TIME").
  - **`titleMedium`** styles the time picker's AM/PM.

  That breaks DESIGN.md §4 and §10.

  **Fix:** Define all 15 slots from the tokens. For example, `labelLarge` as IBM Plex Sans 500 at 15 sp, `labelSmall` as Plex 500 at 11 sp with tracking, `titleMedium` as Plex 500 at 16 sp. Move `mono-num` out of `labelMedium` (DS20). *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS9]** `HomeScreen.kt:210` and the widget `Chronometer`s — The bundled `fraunces.ttf` has **proportional** figures: at the default instance, "1" is 1024/2000 em and "0" is 1461. The file also has no `tnum` feature in GSUB. So `fontFeatureSettings = "tnum"` does nothing, and the centred countdown hero re-centres each time its digits change: every minute, then every second in the final minute. That breaks DESIGN.md §4's "must not drift". IBM Plex Sans digits are already tabular (all 600/1000 em), so the Plex `tnum` settings are redundant but harmless. The Qibla degree readout (`QiblaScreen.kt:377`) also carries a no-op `tnum`, but it shows the fixed Qibla bearing, so it doesn't change on screen.

  **Fix:** Set changing numerals in IBM Plex Sans, or bundle a Fraunces build with tabular figures. Remove the no-op `tnum` settings, or leave a comment saying why they do nothing. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS10]** `res/layout/widget_*.xml` (e.g. `widget_next_prayer.xml:19`, `:44`) — RemoteViews `TextView`s load `@font/fraunces` without variation settings, so they get the file's default instance: wght 900 (Black) at opsz 9. Its `usWeightClass` is 900, so `textStyle="bold"` adds nothing. DESIGN.md §4 uses Fraunces 400/500. This is the expected platform behaviour but unverified on a device.

  **Fix:** Check on a device. If confirmed, bundle static Fraunces instances for widgets — for example 500 at opsz 20 and 32. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS20]** `AynamaTypography.kt:90-95` and `NavGraph.kt:49-55` — `mono-num` sits in Material's `labelMedium` slot, which Material also uses for navigation-bar labels. So nav labels render at 17 sp against Material's 12 sp default, which is what led to capping nav font scale at 1.3× (PR #12 A6). `mono-num` also lacks the tabular setting its name promises; that's harmless for IBM Plex Sans.

  **Fix:** Make `mono-num` a named style outside Material's slots, and give `labelMedium` a nav-appropriate size. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS23]** Inconsistent transliteration:
  - `HomeScreen.kt:239` says "Qaḍā"; `MarkPrayerSheet.kt:77` says "Qada"; entity names say "Qaza".
  - `NotificationSettingsScreen.kt:486` says "Ramadan Imsak"; `HomeScreen.kt:517` says "Ramaḍān Mubārak".

  **Fix:** Pick one form per term (DESIGN.md §4, Copy & transliteration) and apply it through `strings.xml` (PR #14 L1). *(Origin: design-doc sync 2026-09-23)*

### Composition, iconography & motion

- [ ] **[DS6]** `HomeScreen.kt:358-373` (and `:228-235`) — The prayer timeline has no vertical rule and no moving tick; "current" is a static 8 dp dot. That's DESIGN.md §9's second deliberate departure, and TODOS Phase 2 had it checked off.

  **Fix:** Build it: a 1.5 dp rule in the muted token through the mark column, and a tick placed between the current and next rows by the elapsed fraction of that interval. Use the existing 1 s clock, and move the tick at most once a minute. Otherwise, amend §9 through the DESIGN.md §14 process. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS8]** `AndroidManifest.xml:15-20` and `res/values/themes.xml:3` — The app has no `android:icon` or `android:roundIcon`, so the launcher, recents, system settings and the Android 12+ splash show the platform's default icon. The window theme is `android:Theme.Material.NoActionBar`, so the launch window before Compose draws is the platform's dark grey, not `ink`.

  **Fix:** Design an adaptive launcher icon with a monochrome layer (DESIGN.md §6). Give `Theme.Aynama` window and splash colours from the tokens. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS17]** `HomeScreen.kt:544` — The empty-state Kaaba mark is the 🕋 emoji, which renders in the system colour-emoji font as a black cube with a gold band. DESIGN.md §6 asks for a custom abstract Kaaba mark and forbids gold ornament.

  **Fix:** Draw the mark as a vector in token colours. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS18]** `NavGraph.kt:74-79` and `MainActivity.kt:29` — `Scaffold`'s default `contentWindowInsets` (`WindowInsets.systemBarsForVisualComponents`) pads the `NavHost` below the status bar. The time-of-day surface stops there, and the strip shows `colorScheme.background` — parchment in light mode, even above the dark Isha surface. That breaks DESIGN.md §11's "status bar matches current surface". The code is certain; the visual effect hasn't been screenshotted.

  **Fix:** Pass `contentWindowInsets = WindowInsets(0)`. Let Home and Qibla draw behind the bar and pad only their content. Screens without a top app bar (Tracker, Settings) then need their own status-bar padding. Set the status-bar icon appearance per phase from `isLightPhase`. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS19]** `QiblaScreen.kt:513,523`, `HomeScreen.kt:510`, `SettingsScreen.kt:143,455` — Colours outside the token set: the calibration amber (`#7A5800` with `#FFF3CD`), the Ramadan banner's oxblood (`#6B2E2A`, a gradient stop reused), and Material's baseline error red for deleting.

  **Fix:** Add named tokens — for example `caution`, `oxblood`, `destructive` — checked against ink and parchment, or map these to existing tokens. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS21]** `NavGraph.kt:34-39` and `:59` — Nav icons are the filled `Icons.Default` set in both states; DESIGN.md §6 wants stroke icons, filled only for the active tab. Also, `selected = currentRoute == screen.route` leaves no tab selected on `settings/notifications` and `settings/notifications/adhan`.

  **Fix:** Use outlined icons for unselected tabs and filled for the selected one. Match selection against the destination hierarchy. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS22]** `res/drawable/ic_notification.xml` — The notification small icon is a crescent (two circles combined even-odd). That's next to the crescent-and-star motif DESIGN.md §6 and §10 forbid, and hard to read (PR #14 V1).

  **Fix:** Decide the glyph (for example the Qibla arrow or an abstract Kaaba mark) and draw a monochrome version. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS26]** `HomeScreen.kt:142-149` — The Ramadan banner is overlaid 8 dp from the top, so while it shows it covers the header line (profile · method, Hijri date) and the top of the countdown hero.

  **Fix:** Put it in the column above the header so it pushes content down, or dock it above the page dots. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS28]** `HomeScreen.kt:208-224`, and reduced motion is checked nowhere — DESIGN.md §8's 400 ms prayer-transition cross-fade isn't implemented: the hero and timeline text swap instantly at each boundary. There's no app-level reduced-motion handling either (§12). And the text colour flips the moment the phase changes while the surface takes 3 s to fade (`HomeScreen.kt:98-101`), so for a moment ink text sits on a still-dark surface, or parchment on a light one.

  **Fix:** Use `AnimatedContent`/`Crossfade` keyed on the next prayer. Compose already scales its animations by the system animator duration scale, so "Remove animations" snaps the 3 s fade; what's missing is §12's 150 ms reduced-motion cross-fade. Animate the text colour with the surface. *(Origin: design-doc sync 2026-09-23)*

### Behaviour & states

- [ ] **[DS7]** `RamadanDetector.kt:39-40` and `SettingsScreen.kt:439-441` — **The Hijri adjustment lapses on the wrong day.**
  - **+1:** set for Ramadan, it lapses on the user's own 1 Shawwāl, because the adjusted date has left Ramadan. The app then falls back to the calculated calendar, where that day is still 30 Ramaḍān. So on Eid, Home shows the Imsak row, the Ramadan banner and "30 Ramaḍān". If this is the "Alerts for" profile, the scheduler also arms an Imsak alarm for Eid morning.
  - **−1:** set on the calculated 1 Ramadan, it's pinned to Shaʻbān and lapses the next day, so Ramadan's end is never delayed.
  - **+1 saved before the first fast:** the evening a sighting is announced, the adjusted date (today + 1) is still the calculated last day of Shaʻbān, so Save pins the offset to Shaʻbān (`SettingsScreen.kt:439-440`). It lapses at midnight, on the user's own 1 Ramaḍān, where the calculated calendar still says Shaʻbān: the first fast day gets no Ramadan state and no Imsak alarm. Saving +1 anywhere in Shaʻbān does the same. Only a +1 saved on or after the user's 1 Ramaḍān is pinned to Ramaḍān, and that one hits the Eid case above.

  The existing tests check the pieces on inputs production never combines: `offset1_lastCalcDay_returnsFalse` checks Eid with the raw +1 (production has already lapsed it to 0), `effectiveOffset_newPerceivedMonth_resetsToZero` pins the lapse itself, and the −1 test uses a Ramaḍān anchor that a −1 saved on the calculated 1 Ramadan doesn't get. Nothing checks the composed Ramadan state. This is religious correctness, so treat it as high severity.

  **Fix:** Choose the rule, after settling DS11: the offset is relative to whichever calendar the device picked. Keeping the offset until the user changes it handles every case above. Lapsing "once both the calculated and adjusted dates have left the pinned month" does not: pinned to Shaʻbān, +1 then holds on the first fast day but drops on the next (so "1 Ramaḍān" shows twice) and Eid still shows Ramadan, and −1 drops on the calculated 2 Ramaḍān, so Ramadan's end still isn't delayed. Any automatic expiry needs explicit start and end boundaries. Test through the production path, `isRamadanWithOffset(d, effectiveHijriOffset(offset, key, d, zone), zone)`, with `key` computed as `SettingsScreen.kt:439-440` does: +1 saved on 2024-03-09 → Ramadan on 2024-03-10; +1 anchored to Ramaḍān 1445 → not Ramadan on 2024-04-09; −1 saved on 2024-03-11 → Ramadan on 2024-04-10. *(Origin: design-doc sync 2026-09-23; cases added at the 2026-09-24 review)*

- [ ] **[DS11]** `RamadanDetector.kt:58-63` — `IslamicCalendar(TimeZone)` without `setCalculationType` lets ICU pick the variant from the device locale's region, via CLDR calendar preferences. Saudi-region locales get `islamic-umalqura`; every other region gets `islamic-civil`. That was checked with ICU4J 74.2 for US, GB, SA, AE, EG, PK, ID, MY, TR, IR, QA, KW and BD.
  - The two variants gave different dates on 655 of 1,095 days in 2025–2027, and disagreed on whether it was Ramadan on 2 days.
  - So the same profile shows different Hijri dates, and can show different Ramadan windows, on different phones. The ±2 adjustment is relative to whichever variant the device picked.
  - `architecture-design.md` said "Umm al-Qura by default".

  **Fix:** Choose a calculation type explicitly, document it in DESIGN.md §18, and optionally make it a per-profile setting. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS12]** `QiblaViewModel.kt:219,244`, `TrackerViewModel.kt:87,243`, `HomeViewModel.kt:140,146`, `NotificationSettingsViewModel.kt:198` and `AlarmScheduler.kt:170-173` — Several places use the device zone where the profile's applies:
  - Qibla computes prayer times in the device zone and takes its phase from device-local time.
  - Tracker computes its scheduled times and "today" in the device zone.
  - Home (`HomeViewModel.kt:140,146`) and the Notifications screen (`NotificationSettingsViewModel.kt:198`) pass the device's `LocalDate.now()` to the calculation. Home's phase, Hijri date and Ramadan state use the profile's date (`:205-212`), so between the two midnights it shows the wrong day's times.
  - The notification rollover alarm fires at device midnight (`AlarmScheduler.kt:170-173`), but each day's alarms are built for the profile's date (`:58-62`). Prayers between the profile's midnight and the device's are never armed unless the app is opened or a widget's rollover alarm re-runs `scheduleAll` (`PrayerWidgetUpdateReceiver.kt:23`). For a profile a few hours behind the device zone, that can be most of the day's alarms.

  Widgets and the alarm times themselves use `profile.effectiveZoneId()`. With "Use location time zone" on (the default for new profiles) and the device somewhere else, these disagree with Home and with each other.

  **Fix:** Use `effectiveZoneId()` for both the times and "now" in both ViewModels and for Home's date, and arm the rollover at the notification profile's next midnight. Fix DS32 first, or this spreads its wrong zones to Qibla and the Tracker. *(Origin: design-doc sync 2026-09-23; Home and the alarm rollover added at the 2026-09-24 review)*

- [ ] **[DS13]** Time formats disagree. The same time reads "4:14 PM" on Home and "16:14" in Notifications.
  - Always 12-hour ("h:mm a"): Home (`HomeViewModel.kt:114`) and Tracker (`TrackerViewModel.kt:75`).
  - Always 24-hour ("HH:mm"): Notifications, including the detail-sheet header (`NotificationSettingsViewModel.kt:26`).
  - Always 12-hour: the fixed-time row (`PrayerDetailSheet.kt:61-72`, PR #17 N6).
  - Follow the device's 12/24-hour setting: the widgets (`PrayerWidget.kt:280-283`) and the time picker.

  **Fix:** Use one shared formatter that honours `DateFormat.is24HourFormat` and the locale. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS14]** `NotificationHelper.kt:67-103`, `PrayerAlarmReceiver.kt:33-44`, `AdhanService.kt:36-38` — Alerts as delivered:
  - **(a)** Prayer notifications have no content intent, so tapping one does nothing and `setAutoCancel` never fires.
  - **(b)** Imsak alarms use the prayer template ("It is time for Imsak prayer") and start the adhan service.
  - **(c)** The ongoing "Adhan · Playing…" notification has no stop action.
  - **(d)** Every voice plays the system notification sound; there's no bundled adhan audio, and previews show a toast.
  - **(e) Unverified, check on a device:** the app's own vibration (`NotificationHelper.kt:64`) is a background `vibrate()` with no usage attribute, which Android may ignore for a background app, so voice None with vibration Always could give a silent, still alert. And when exact alarms aren't allowed, the fallback alarm's receiver still calls `startForegroundService` (`PrayerAlarmReceiver.kt:38-43`), even for voice None; Android 12+ blocks that from the background unless the app is exempt from battery optimisation.

  **Fix:**
  - (a) Add a content intent that opens Home.
  - (b) Give Imsak its own copy (e.g. "Imsak — Fajr in 10 minutes") and no adhan.
  - (c) Add a "Stop" action.
  - (d) Ship the adhan assets, or hide the voice choice until they exist.

  *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS15]** `MarkPrayerSheet.kt:70-87` and `TrackerScreen.kt:342-348` — Two problems in the mark sheet and history squares:
  - "I prayed this" (on time) is offered for any date. DESIGN.md §16 allows it only within the prayer's window and greys it out for past days.
  - `MISSED`, `INTENTION_TO_MAKEUP` and unmarked all render as the same empty square.

  **Fix:** Decide the rule. If it stays, disable "on time" outside the window, and give "missed" a mark distinct from "unmarked". *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS16]** `NotificationSettingsViewModel.kt:193-213` — If the "Alerts for" profile has no computable times (polar day or night), `loadPrayerTimes` returns at line 213, before it sets `profiles`, `notificationProfile` or `prayerRows` (lines 223-247). The result: PRAYERS is empty, the profile row shows "—", and the Profile Picker opens with no rows, so the user can't switch away from this screen. The code comment says the rows stay "without a clock value"; in fact they don't render at all. Switching "Alerts for" to such a profile from inside the screen is worse: the new profile is stored, then `loadPrayerTimes` returns early, so PRAYERS keeps the previous profile's rows under the new name while every toggle, offset or fixed time the user changes is saved to the new profile (`:171-179`).

  **Fix:** Set `profiles` and `notificationProfile` before computing times. On failure, build the rows with "--:--", except fixed-time rows, which should show their configured time. The scheduler has the same blind spot: `buildAlarmSchedule` needs Adhan times before it builds any alarm (`AlarmScheduler.kt:120`, `:233-243`), so even fixed-time alerts, which don't depend on the sun, are dropped on polar days. Decide that with PR #21 C1. *(Origin: design-doc sync 2026-09-23; fixed-time rows added at the 2026-09-24 review)*

- [ ] **[DS24]** `HomeScreen.kt:581-607` and `HomeViewModel.kt:159` — Home's error state shows "Something went wrong" and the raw exception message. There's no cause-specific copy and no recovery action, which the interaction-states table in `architecture-design.md` requires.

  **Fix:** Map known failures to a plain-language cause with one action, and log the exception rather than displaying it. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS25]** `QiblaScreen.kt:508-527` and `HomeScreen.kt:500` — Two TalkBack gaps:
  - The calibration banner isn't a live region, so TalkBack users aren't told calibration is needed. `architecture-design.md` asks for a live-region announcement.
  - Each page dot is a separate focus stop that announces "Page N", duplicating the pager's own page semantics.

  **Fix:** Set `liveRegion = Polite` on the banner. Give the dot row a single description, such as "Page 2 of 4", with `clearAndSetSemantics`. *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS27]** `TrackerScreen.kt:251-256` and `:270` — `DayRow`'s today branch ("Today · Sep 23" in saffron) never runs, because history ends at yesterday (`TrackerViewModel.kt:167-170`).

  **Fix:** Delete the branch, which also removes a saffron-text use (DS2). *(Origin: design-doc sync 2026-09-23)*

- [ ] **[DS29]** `HomeScreen.kt:346-356` — Home's timeline rows, the main way to mark a prayer, have no minimum height. Each is as tall as its text (the `title` line height, 25 sp), and `Arrangement.SpaceEvenly` (`:230`) spaces the rows without enlarging them, so the tap target is about 25 dp, not the 48 dp DESIGN.md §12 requires.

  **Fix:** Give tappable rows `Modifier.heightIn(min = 48.dp)`, keeping the even spacing. *(Origin: 2026-09-24 review)*

- [ ] **[DS30]** `TrackerViewModel.kt:99,125,210` — On a polar day for the default profile, `cachedPrayerTimes` throws and `.catch` turns it into `Empty`, so the Tracker says "Create a profile to track prayers" to someone who has one. `.catch` also ends the flow, so the Tracker stays empty until its ViewModel is recreated, even after the polar day. DESIGN.md §20 doesn't list this state.

  **Fix:** Catch `PrayerTimesUnavailableException` per day, show the ledger without times, and say why, as Home's per-page state does. *(Origin: 2026-09-24 review)*

- [ ] **[DS31]** `HomeViewModel.kt:247-257,327-337` — The after-midnight-Isha logic only runs when Isha's clock time is earlier than Fajr's (`times.isha < times.fajr`). Near the June solstice at about 50°N and above, adhan's default `MIDDLE_OF_THE_NIGHT` rule puts Fajr and that night's Isha at the same clock time: London, MWL, 2026-06-21 gives Fajr 01:02 and Isha 01:02 the next night. The guard then doesn't fire and `indexOfLast` picks Isha, so from about 1 AM Home shows Isha as current and Fajr, Dhuhr, Asr and Maghrib as passed. Passed rows are tappable, so the user can mark Asr and Maghrib before they happen. The surface also jumps to Isha at Maghrib. The debug seed profiles are in London. Still true on `agent-main`, whose ribbon keeps the same guard.

  **Fix:** Carry dates or instants through the ribbon and phase functions, as the widgets and `agent-main`'s countdown do, instead of comparing `LocalTime`s. Add a `HomeRibbonStateTest` built from real London 2026-06-21 MWL output, not the synthetic 00:25 Isha. *(Origin: 2026-09-24 review)*

- [ ] **[DS32]** `SettingsScreen.kt:681-688` — City search detects a location's time zone by picking, among the country's zones, the one whose raw offset is closest to longitude ÷ 15. That's an hour off for some major cities: Madrid and Barcelona get `Atlantic/Canary`, Lisbon `Atlantic/Azores`, Detroit, Atlanta and Columbus a US Central zone, Calgary and Edmonton `America/Vancouver`, and Surabaya WITA. With "Use location time zone" on by default, Home, the widgets and the Notifications rows then label every prayer an hour early or late against the local clock. The countdown and the alarm instants stay right, but someone reading Madrid's Dhuhr off the timeline would pray an hour before it starts. Same code on `agent-main` (`ProfileFormSheet.kt:525-532`).

  **Fix:** Resolve the zone from the coordinates with a zone-boundary lookup, not offset proximity. Until then, keep "Use location time zone" off by default. Fix this before DS12, whose fix would spread the wrong zones to Qibla and the Tracker. *(Origin: 2026-09-24 review)*

- [ ] **[DS33]** `QiblaScreen.kt:94,103` — Qibla requests `ACCESS_FINE_LOCATION` on its own. Android says to request fine and coarse together: that's what shows the Precise/Approximate choice, a fine-only request logs "ACCESS_FINE_LOCATION must be requested with ACCESS_COARSE_LOCATION" for apps targeting Android 12+, and some Android 12 releases ignore it (<https://developer.android.com/develop/sensors-and-location/location/permissions/runtime>). When it's ignored, Qibla gets live location only if coarse was already granted through the profile sheet; otherwise it points from the default profile's saved city without saying so, so a traveller gets the bearing from home. Still true on `agent-main`.

  **Fix:** Request both with `RequestMultiplePermissions`, and show which location the bearing uses when it falls back to a profile. *(Origin: 2026-09-24 review)*

- [ ] **[DS34]** `HomeScreen.kt:159` vs `TrackerViewModel.kt:84,92` — Home saves a mark under the profile page it was made on; the Tracker shows only the default (first) profile's marks. A prayer marked on any other Home page never appears in the Tracker's Today rows, history, weekly line or outstanding count, and each Home page counts only its own marks. Deleting a profile also deletes its marks (`QazaEntry` cascades), with no confirmation (PR #15 M10).

  **Fix:** Decide the model: marks belong to the person rather than the location profile (store them once, or migrate), or Home always marks against the Tracker's profile. Document it in §5 and §16, and mention the deleted history in M10's delete dialog. *(Origin: 2026-09-24 review)*

- [ ] **[DS35]** `NotificationSettingsViewModel.kt:121-160` — Changing a prayer's offset, alert mode or fixed time updates those fields and re-arms the alarm, but not the row's `time`, which only `loadPrayerTimes` computes (`:231-235`). The Notifications row and the detail sheet's "Today · {time}" keep the old alert time until the screen is recreated: the sheet can read "+10 min" over "Today · 04:21" while the alarm is set for 04:31.

  **Fix:** Keep the calculated prayer time in `PrayerRowData` and derive the displayed alert time in one place, recomputing it in each setter. *(Origin: 2026-09-24 review)*

## From PR #34 — design-doc sync review (`/review` 2026-09-24)

Code defects found while checking the sync's claims. Each contradicts `architecture-design.md`'s notification notes.

### Adversarial / Cross-cutting

- [ ] **[A1]** `AlarmScheduler.kt:154-162` vs `:188-193` — `cancelForProfile` looks up each `PendingIntent` with an `Intent` that has no action, but `submitAlarm` arms them with `ACTION_PRAYER_ALARM`. `PendingIntent` matching compares the action, so the `FLAG_NO_CREATE` lookup finds nothing and nothing is cancelled. Turning the master switch off, turning a prayer or early reminder off, or switching "Alerts for" leaves the day's remaining alarms armed, and `PrayerAlarmReceiver` fires them without re-checking the settings. **Fix:** Build the `Intent` in one function used by both, as `PrayerWidgetScheduler.updateIntent` does, and test at the `AlarmManager` level. *(Origin: PR #34 review. Fixed on `agent-main` by #27; delete this when that lands on `main`.)*

- [ ] **[A2]** `SettingsViewModel.kt:43-50` — Deleting a profile deletes it, then calls `scheduleAll` with the remaining profiles, and `scheduleAll` only cancels alarms for the profiles it's given. The deleted profile's alarms are never cancelled: if it was the "Alerts for" profile, its remaining alarms still fire today, alongside the new default profile's. Still true on `agent-main`. **Fix:** Call `AlarmScheduler.cancelForProfile(context, profile.id)` before deleting, and cover it in PR #15 T1. *(Origin: PR #34 review)*

- [ ] **[A3]** `MainActivity.kt:31-38` — The battery-optimisation exemption is asked for only from the notification-permission result, and that permission is only requested on Android 13+ when it's missing. So Android 8–12 (minSdk is 26) never see the prompt, and neither do Android 13+ users who had already allowed notifications — including most of the aggressive-OEM devices the prompt exists for. Still true on `agent-main`. **Fix:** Call `requestBatteryOptExemptionOnce()` directly on Android 12 and below, and when notifications are already allowed. *(Origin: PR #34 review)*

---

## Workflow

- When you address a finding, **delete its line** rather than checking it off — keeps the file scoped to open work.
- New `/review` runs append a section under `## From PR #N — ...` with the same structure.
- IDs (M1, P1, T1, A1, etc.) are stable per-PR; reference them in commit messages or follow-up PR titles for traceability.
- Design-doc syncs append `## From design-doc sync — <scope> (<date>)`. DS IDs are one sequence across syncs and are never reused (next: DS36); they can be cited bare. Cite per-PR IDs as `PR #N <ID>`, for example PR #21 C1.
