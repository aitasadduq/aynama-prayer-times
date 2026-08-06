# Bug verification & fix progress

Source: Notion page "Prayer Times App" → TODO List → Bugs
(https://app.notion.com/p/Prayer-Times-App-31f07117767d8024820ac89140200980)

Task: verify each bug exists, fix the ones that do, test on Android emulator.
This file tracks state so another session can resume. Update after each step.

## Bug status

| # | Bug | Status |
|---|-----|--------|
| 1 | Widget countdown doesn't auto-reset on new prayer time, goes negative | ALREADY FIXED in commits aa8fb85 + a57e387 (per-profile alarm chains, 2s guard, extensive PrayerWidgetTest coverage). TODO: run unit tests + emulator dumpsys alarm check |
| 2 | 4x2 widget: highlight Sunrise at sunrise time instead of Fajr | CONFIRMED — PrayerWidget.kt buildPrayerWidgetState: currentPrayerName only considers obligatory prayers, so Fajr stays highlighted sunrise→Dhuhr. Fix: include Sunrise as current event; highlight the sunrise block (top-right of widget_full.xml, need id on label TextView) and un-highlight Fajr columns |
| 3 | Remove "North 0°" from Qibla screen | CONFIRMED — QiblaScreen.kt BearingChipsRow has `BearingChip(label="North", deg=0)`. Fix: remove North chip + DotSep, keep Qibla chip. Update DESIGN.md §5 sentence about "Qibla / North reference pair" first |
| 4 | Background colour at sunrise time not legible | CONFIRMED — GradientColors.kt SUNRISE_TRANSITION = #3A3530→#E8C89A with Ink content color (isLightPhase=true); dark top makes header/countdown unreadable; phase spans sunrise→Dhuhr (hours). Fix: lighter gradient #EDE1C5→#E8C89A (linen into honey). Update DESIGN.md §3 table first |
| 5 | Profile create/edit: save button should always be visible without scrolling | CONFIRMED — SettingsScreen.kt ProfileFormSheet: default ModalBottomSheet opens partially expanded, plain Column, Save at bottom. Fix: sheetState skipPartiallyExpanded=true + scrollable field column with Save/Delete pinned below |

Statuses: NOT STARTED → VERIFYING → CONFIRMED / NOT REPRODUCIBLE → FIXED → TESTED

## Fixes implemented (2026-08-05, all unit tests pass, APK installed on emulator-5554)

- Bug 2 FIXED: PrayerWidget.kt — currentPrayerName now derived from all timeline events
  (Sunrise included), TimelineEvent.obligatory field removed; full() highlights
  widget_sunrise_label/_time in saffron+bold when current event is Sunrise; added id
  widget_sunrise_label to widget_full.xml. Two new tests in PrayerWidgetTest
  (`sunrise is the current event between sunrise and dhuhr`, `fajr is current before sunrise`).
- Bug 3 FIXED: QiblaScreen.kt — removed North chip + DotSep + dim params; BearingChipsRow
  now shows only the Qibla chip. DESIGN.md §5 updated (no North bearing readout).
- Bug 4 FIXED: GradientColors.kt — SUNRISE_TRANSITION now #EDE1C5→#E8C89A (light, ink-legible).
  DESIGN.md §3 table updated.
- Bug 5 FIXED: SettingsScreen.kt ProfileFormSheet — skipPartiallyExpanded=true so sheet opens
  full height; form fields in weight(1f, fill=false)+verticalScroll column; Save/Delete pinned below.
- Bug 1: no code change needed (fixed by earlier commits aa8fb85/a57e387); unit tests pass.

## Emulator verification — ALL DONE (2026-08-05)

- [x] Bug 1: `dumpsys alarm` showed per-profile PRAYER_WIDGET_UPDATE chains armed at every
      remaining boundary +2s guard (2 profiles; Hanafi Asr split; after-midnight Isha on next
      calendar day). Live rollover test: set clock to 15:07:50, watched Dhuhr boundary — all
      widgets flipped to "until Asr" with positive countdowns, nothing went negative.
- [x] Bug 2: placed 4x2 Full widget bound to London; at 10:03 (sunrise 7:30→Dhuhr 15:08 window)
      Sunrise block highlighted saffron+bold, Fajr normal. After Dhuhr rollover, highlight moved
      to Dhuhr column and sunrise reverted. Verified.
- [x] Bug 3: Qibla screen shows only "Qibla 119°" chip; North chip gone. Verified.
- [x] Bug 4: home at 10:00 in sunrise phase — light linen→honey gradient, ink text fully legible.
- [x] Bug 5: Edit profile sheet opens fully expanded, Save + Delete visible without scrolling.

Emulator clock restored to automatic afterwards.

## Final status

ALL 5 BUGS RESOLVED AND VERIFIED. Tests pass (`:app:testDebugUnitTest`,
`:shared-logic:testDebugUnitTest`), lint clean (`lintDebug`), debug build installs and runs.
Changes are UNCOMMITTED on main (user prefers to be asked before committing).
Notion checkboxes NOT updated (not requested).

## Notes

- All 5 bugs assessed 2026-08-05. 2-5 need code fixes; 1 needs verification only.
- Key files: android/app/src/main/java/com/aynama/prayertimes/widgets/PrayerWidget.kt,
  widgets/PrayerWidgetScheduler.kt, qibla/QiblaScreen.kt, home/GradientColors.kt,
  settings/SettingsScreen.kt, res/layout/widget_full.xml
- Unit tests: android/app/src/test/.../widgets/PrayerWidgetTest.kt — note test
  `current prayer name always matches a schedule row` (14:00 → Dhuhr, unaffected) and
  `next and current are derived chronologically...` (expects current=Fajr pre-sunrise, unaffected).
  Bug 2 fix should add a test asserting currentPrayerName=="Sunrise" between sunrise and Dhuhr.
- DESIGN.md governs UI: no rings, palette fixed (ink/parchment/saffron), update DESIGN.md before color/composition changes (§14).

## Environment

- Repo: /Users/anastasadduq/Documents/development/repos/aynama-prayer-times, branch main, clean at start (HEAD dd832b6)
- Android emulator available for testing
- Memory note: for widget rollover verification, assert on `dumpsys alarm`; `uiautomator dump` lies when a Chronometer is ticking
