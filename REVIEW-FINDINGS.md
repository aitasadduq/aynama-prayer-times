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

## Workflow

- When you address a finding, **delete its line** rather than checking it off — keeps the file scoped to open work.
- New `/review` runs append a section under `## From PR #N — ...` with the same structure.
- IDs (M1, P1, T1, A1, etc.) are stable per-PR; reference them in commit messages or follow-up PR titles for traceability.
