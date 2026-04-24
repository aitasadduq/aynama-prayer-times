# TODOS — aynama-prayer-times

Tracked items from plan reviews. Must-decide-before-code items are in `.gstack/projects/aitasadduq-aynama-prayer-times/ceo-plans/`.

## Design TODOs (from /plan-design-review, 2026-04-18)

- [ ] **Run /design-consultation before v1 UI work.** Define color palette, typeface, iconography, and app icon direction for aynama. Without brand tokens, every implementer makes color/font choices inline — produces an inconsistent app. One session produces a DESIGN.md that governs all future UI decisions. No prerequisites.

- [ ] **Notification settings screen UX spec.** Per-prayer toggles, adhan audio picker (silent / vibrate / short alert / full adhan), vibration option, advance notice duration (e.g. "5 min before"). The plan mentions configurable adhan per prayer but the picker UI and per-prayer toggle layout are undefined. Prevents ad-hoc notification UX. ~30 min design work. No prerequisites.

- [ ] **Prayer tracker history view spec.** Define whether history is a calendar heat-map, a flat list, or a streak view. How does the user filter by missed/made-up/pending? How do they mark a historical Qaza as made up? The history view drives Room query design — a calendar heat-map needs different indexes than a flat list. Resolve before building the tracker screen. Depends on: Room schema (Reviewer Concern #3, already spec'd in CEO plan).

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

- [x] ~~**Vector generator self-tests.**~~ → **SCRIPTS IN PLACE** (`scripts/generate_vectors.py`, `scripts/test_generator.py`, `scripts/AdhanRunner.java`). Tests 2+3 pass on any Python machine. Test 1 (golden values) requires Java — auto-downloads JAR and passes in CI. First vector commit unblocked once CI Java environment is confirmed.

- [ ] **Adhan-Swift version pin + parity check.** Before v3 iOS work: pin Adhan-Swift to a specific release in `scripts/reference-versions.json`; verify all 12 test-vector cities agree between Adhan-Swift and Adhan-Kotlin within ±1 min; add parity check to ios.yml CI. Required pre-v3 gate.

- [ ] **adhan-test-vectors companion repo ownership protocol.** Before v1 launch: document in companion repo README: (1) how to trigger vector regeneration on Adhan upstream release (GitHub Actions manual dispatch); (2) who reviews PrayTimes.py vs Adhan disagreements; (3) process for syncing updated vectors back to main repo.

- [ ] **iOS notification limit analysis.** Before v3 iOS notification settings work: OS limit = 64 pending. 5 prayers × 7 days = 35 (fine). + advance-notice reminders = 70 (overflow). Options: (a) cap at 6 days; (b) background-app-refresh regeneration at 5-day mark; (c) alternating schedule. Resolve before speccing advance-notice for iOS.

## Supply-chain / security

- [ ] **Verify Adhan license (MIT vs Apache 2.0).** Check `LICENSE` file in batoulapps/adhan-java + batoulapps/adhan-swift on GitHub. Update `legal-posture.md` compliance framework accordingly — MIT = copyright notice only; Apache 2.0 = NOTICE file + state-changes. Maven POM for adhan2-jvm says MIT; adhan:1.2.1 POM tag is empty. Blocks first public release. (flagged /plan-eng-review 2026-04-19)

- [ ] `gradle --write-verification-metadata sha256` after first build; commit `gradle/verification-metadata.xml`.
- [ ] Dependency audit: zero third-party analytics SDKs in v1 (verify with `./gradlew :app:dependencies`).
