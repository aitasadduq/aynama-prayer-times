# TODOS — aynama-prayer-times

Tracked items from the CEO plan review on 2026-04-17. Must-decide-before-code items are in `ceo-plans/2026-04-17-muslim-prayer-app.md`, not here.

## Deferred decisions (not blocking v1 code start, but must resolve before first public release)

- [ ] **Monetization / sustainability model.** Pick one: donations, pay-what-you-want, freemium, paid-upfront, fully-free. Decide before v2.
- [ ] **iOS CI runner strategy.** GitHub macOS vs MacStadium vs self-hosted Mac mini vs no-iOS-until-v3. Decide before v3 (iOS phase start).
- [ ] **Quran data source.** Tanzil (chosen in legal-posture.md) vs alternative. Validate against licensing + attribution before v4 (Quran feature).
- [ ] **Gold/silver price API for zakat.** Free tier API vs scraped static value vs user-input. Decide before v5.
- [ ] **Trademark clearance on "aynama."** USPTO + EUIPO search before Play Store + F-Droid submission.
- [ ] **Project license.** MIT vs Apache 2.0. Decide before first public repo push. Lean Apache 2.0 to match Adhan upstream.
- [ ] **`SCHEDULE_EXACT_ALARM` Play Console justification string.** Write before Play Store submission (required for API 31+ apps using exact alarms).
- [ ] **Android package name.** `com.aynama.prayertimes` — verify no conflict on Play Store before submission.

## Reviewer Concerns from design doc (4 of 5 deferred; #1 and #3 are now must-decide-before-code)

- [x] ~~Reviewer Concern #1: Forbidden prayer time calculation~~ → moved to must-decide-before-code
- [ ] **Reviewer Concern #2: Zakat nisab / Hawl logic.** v4-v5 decision. Which scholar's interpretation of Hawl cycle tracking?
- [x] ~~Reviewer Concern #3: Qaza tracking UX~~ → moved to must-decide-before-code
- [ ] **Reviewer Concern #4: Widget countdown strategy.** Platform-specific. Decide during widget implementation per platform.
- [ ] **Reviewer Concern #5: WearOS complication refresh model.** Decide before v2.

## Temporal Interrogation follow-ups (non-blocking, low effort)

- [ ] T1 — Write minimal README before v1 code starts.
- [ ] T2 — Design doc language: "Adhan via Gradle dep on Maven Central, source-vendor only if patched."
- [ ] T3 — Write `test-vectors/schema.json` before first vector commit.
- [ ] T5 — Add `tags: List<String>` or `context: ProfileContext` field to Profile schema (future hajj/umrah awareness).
- [ ] T6 — Scheduler rebuilds alarms at local-midnight + on `ACTION_TIMEZONE_CHANGED` broadcast.
- [ ] T7 — Qibla uses `SensorManager.remapCoordinateSystem` for tilt-stable bearing.
- [ ] T8 — `FOREGROUND_SERVICE_SPECIAL_USE` manifest entry + Play Console justification.

## Outside Voice items noted but not adopted

- [ ] (Optional / user declined) Email 3 community imams for fiqh sign-off on forbidden times, Asr madhab, Imsak offset, Qaza methodology, zakat nisab. Flagged as a trust-position risk by outside voice; user chose to rely on Adhan methodology instead.

## Supply-chain / security

- [ ] `gradle --write-verification-metadata sha256` after first build; commit `gradle/verification-metadata.xml`.
- [ ] Dependency audit: zero third-party analytics SDKs in v1 (verify with `./gradlew :app:dependencies`).
