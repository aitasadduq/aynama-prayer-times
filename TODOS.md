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
- [ ] **`SCHEDULE_EXACT_ALARM` Play Console justification string.** Write before Play Store submission (required for API 31+ apps using exact alarms).
- [ ] **Android package name.** `com.aynama.prayertimes` — verify no conflict on Play Store before submission.

## Reviewer Concerns from design doc

- [x] ~~Reviewer Concern #1: Forbidden prayer time calculation~~ → moved to must-decide-before-code (CEO plan)
- [ ] **Reviewer Concern #2: Zakat nisab / Hawl logic.** v4-v5 decision.
- [x] ~~Reviewer Concern #3: Qaza tracking UX~~ → moved to must-decide-before-code (CEO plan)
- [ ] **Reviewer Concern #4: Widget countdown strategy.** Platform-specific. Decide during widget implementation per platform.
- [ ] **Reviewer Concern #5: WearOS complication refresh model.** Decide before v2.

## Temporal Interrogation follow-ups (non-blocking, low effort)

- [ ] T1 — Write minimal README before v1 code starts.
- [ ] T2 — Design doc language: "Adhan via Gradle dep on Maven Central, source-vendor only if patched."
- [ ] T3 — Write `test-vectors/schema.json` before first vector commit.
- [ ] T5 — Add `tags: List<String>` or `context: ProfileContext` field to Profile schema.
- [ ] T6 — Scheduler rebuilds alarms at local-midnight + on `ACTION_TIMEZONE_CHANGED` broadcast.
- [ ] T7 — Qibla uses `SensorManager.remapCoordinateSystem` for tilt-stable bearing.
- [ ] T8 — `FOREGROUND_SERVICE_SPECIAL_USE` manifest entry + Play Console justification.

## Supply-chain / security

- [ ] `gradle --write-verification-metadata sha256` after first build; commit `gradle/verification-metadata.xml`.
- [ ] Dependency audit: zero third-party analytics SDKs in v1 (verify with `./gradlew :app:dependencies`).
