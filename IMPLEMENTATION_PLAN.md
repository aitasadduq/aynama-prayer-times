## Implementation Loop

Work through the implementation using the dependency rules below.

### Platform Dependencies

* Android is the first and primary reference implementation.
* iOS depends on Android.
* WearOS depends on Android.
* watchOS depends on iOS.
* iOS does **not** depend on WearOS.
* WearOS does **not** depend on iOS.
* watchOS does **not** depend on WearOS.

The dependency graph is:

`Android → iOS → watchOS`

and independently:

`Android → WearOS`

Once Android has passed its validation gate, iOS and WearOS may proceed independently.

For every platform or phase, use this loop:

1. Review the existing architecture, TODO files, and current implementation.
2. Identify the exact changes required for the current phase.
3. Implement the changes.
4. Build and run the application.
5. Test the affected functionality end-to-end.
6. Fix all regressions, crashes, UI issues, synchronization issues, and edge cases discovered during testing.
7. Re-run the relevant tests.
8. Repeat steps 3–7 until the implementation is stable.
9. Update any TODOs/documentation that are now obsolete or completed.
10. Only then mark that phase as complete.

Do not leave known issues behind for another platform unless they are explicitly platform-specific.

---

# Phase 1 — Android Mobile App

Complete and fully test the Android mobile app first.

Android is the behavioral reference implementation for the other platforms unless a platform-specific requirement explicitly overrides it.

## Profile Navigation

* When a user taps a widget, open the prayer profile associated with that specific widget.
* Do not simply open the default/current profile.
* Verify this works correctly when multiple profiles and multiple widgets exist.

## Add Profile Flow

Replace the existing dedicated "Add Profile" page with a FAB-based flow.

* Add a Floating Action Button to the home/prayers screen for creating a new profile.
* Remove the standalone Add Profile page if it is no longer required.
* Tapping the FAB should open the new-profile bottom sheet on the Prayers page.
* After the user successfully saves the new profile:

  * persist the profile;
  * make it the selected/current profile;
  * navigate/show the user directly on the newly created profile.
* Make sure cancelling the sheet does not create or change profiles.

## Friday Prayer Naming

On Friday:

* Display `Jumuah` instead of `Dhuhr`.
* The underlying prayer should still behave as the Dhuhr prayer internally where appropriate.
* Ensure the Friday naming is reflected consistently wherever the prayer name is displayed, including:

  * main app;
  * widgets;
  * notifications;
  * smartwatch apps;
  * complications.

Do not duplicate prayer-time calculation logic just to support the renamed label.

## Unified Prayer Countdown Behavior

Create one clearly defined countdown behavior and use it everywhere possible.

This behavior must eventually apply consistently to:

* Android app;
* Android widgets;
* live notifications;
* WearOS;
* WearOS complications;
* iOS;
* iOS widgets / Live Activities where applicable;
* watchOS;
* watchOS complications.

### Before prayer time

Show the time remaining until the prayer with a minus sign.

Example:

`-00:12:35`

### When prayer time is reached

At exactly the prayer time:

* remove the minus sign;
* start counting upward from zero.

Example:

`00:00:00`
`00:00:01`
`00:15:42`

### For the first 30 minutes after prayer time

Continue counting upward from the prayer time.

### After 30 minutes

Stop counting upward for the previous/current prayer and switch to counting down toward the next prayer.

Example:

`-03:42:18`

Make sure boundary behavior is deterministic around:

* exactly prayer time;
* exactly +30 minutes;
* midnight;
* Fajr across date boundaries;
* Isha → next day's Fajr;
* Friday Dhuhr/Jumuah;
* profile/timezone/location changes while a countdown is active.

Prefer having shared domain rules/specification for this behavior rather than independently re-implementing slightly different countdown rules in every UI surface.

## Live Prayer Notification

Give users an optional persistent/live notification showing the current prayer state and countdown.

The user must be able to enable or disable this feature.

The notification should follow the same countdown rules defined above and update appropriately as the prayer state changes.

Handle platform lifecycle/background restrictions correctly rather than relying on the application remaining active.

---

# Phase 2 — Android Validation Gate

Before starting any dependent platform implementation, perform complete end-to-end testing of the Android app.

At minimum test:

* creating profiles;
* editing profiles;
* deleting profiles;
* switching profiles;
* multiple locations/profiles;
* app restart/persistence;
* widget creation;
* multiple widgets associated with different profiles;
* widget → correct profile navigation;
* Friday Jumuah naming;
* all countdown state transitions;
* notification enable/disable;
* background behavior;
* device reboot where relevant;
* timezone/date changes;
* light/dark themes if supported;
* relevant permission flows.

Fix all discovered issues before marking Android complete.

Once Android passes this gate:

* iOS may begin.
* WearOS may begin.

These two tracks are independent of each other.

---

# Phase 3A — iOS

Begin iOS as soon as Android has passed its validation gate.

The iOS implementation does not need to wait for WearOS.

Implement the complete Android mobile functionality natively on iOS.

The tested Android application should be treated as the behavioral reference implementation.

This includes equivalent functionality for:

* prayer profiles;
* add-profile flow;
* navigation;
* countdown behavior;
* Friday Jumuah naming;
* notifications;
* widgets;
* settings;
* profile-specific widget navigation;
* all other functionality already implemented and validated on Android.

Use native iOS APIs and UX patterns rather than mechanically copying Android UI implementation details.

## Conflict Resolution

When there is a conflict between:

1. the tested Android implementation; and
2. older architecture/TODO documentation;

use the tested Android behavior as the product specification.

Exception:

If the architecture/TODO specifically describes an iOS-specific capability, restriction, UX pattern, API requirement, widget behavior, Live Activity behavior, or Apple platform constraint, preserve the iOS-specific behavior.

If the correct interpretation remains ambiguous and would materially affect product behavior, ask me rather than guessing.

---

# Phase 4A — iOS Validation Gate

Fully test the iOS app before beginning watchOS.

Test the same functional scenarios that were tested on Android, adapted appropriately for iOS.

Also specifically test:

* application lifecycle/backgrounding;
* notification permissions;
* widgets;
* Live Activities if used for the prayer counter;
* timezone/date changes;
* widget → correct profile navigation;
* state restoration;
* app termination/relaunch;
* relevant Apple background execution constraints.

Resolve all known issues before marking iOS complete.

Once iOS passes this gate:

* watchOS may begin.

watchOS does not need to wait for WearOS.

---

# Phase 5A — watchOS

Begin watchOS as soon as iOS has passed its validation gate.

The watchOS dependency is:

`Android → iOS → watchOS`

WearOS completion is not a prerequisite.

Develop the native watchOS application using:

* the completed iOS app as the mobile companion reference;
* the established cross-platform prayer behavior from Android;
* watchOS-specific architecture and TODO requirements.

The watchOS app should provide feature parity with the intended smartwatch experience wherever the corresponding Apple platform capability exists.

If WearOS has already been completed, its tested behavior may be used as an additional smartwatch UX reference, but watchOS must not be blocked waiting for WearOS.

Implement:

* prayer profiles;
* current prayer;
* next prayer;
* unified countdown/count-up behavior;
* Friday Jumuah naming;
* phone/watch synchronization;
* location/profile changes;
* disconnected/reconnected states;
* watch-specific features documented in the architecture or TODOs.

## watchOS Complications

Support all appropriate third-party watchOS complication families/types.

Map prayer information intelligently to the available complication space.

Where watchOS provides complication formats that do not have a direct equivalent elsewhere, use the watchOS architecture/TODO specification where available.

If the appropriate representation is ambiguous, ask me before making a product-level decision.

---

# Phase 6A — iOS + watchOS Integration Test Gate

Use iPhone and Apple Watch simulators to test the complete paired experience.

Test at minimum:

* pairing;
* initial synchronization;
* profile synchronization;
* active-profile changes;
* prayer-time updates;
* countdown transitions;
* Jumuah behavior;
* connectivity loss/recovery;
* watch app lifecycle;
* iOS app lifecycle;
* complications;
* widget consistency;
* notifications / Live Activities;
* stale data;
* device restart scenarios where simulators permit them.

The iPhone app, widgets, Live Activities/notifications, watch app, and complications must all report a consistent prayer state.

Repeat implementation → testing → fixing until this platform pair is stable.

---

# Phase 3B — WearOS

WearOS may begin independently as soon as Android has passed its validation gate.

It does not need to wait for iOS.

Develop the WearOS companion app according to:

* the completed Android implementation;
* the architecture design;
* the existing TODO files.

Where the Android implementation and older architecture/TODO documentation disagree, treat the tested Android behavior as the current product specification unless the documentation explicitly describes WearOS-specific behavior.

## WearOS Requirements

Implement the features defined by the WearOS architecture and TODOs, including the corresponding functionality from the mobile application where appropriate.

The watch should correctly work with:

* prayer profiles;
* current prayer;
* next prayer;
* countdown/up timer behavior;
* Friday Jumuah naming;
* phone/watch synchronization;
* location/profile changes;
* disconnected/reconnected states.

## WearOS Complications

Support all WearOS complication types that are appropriate and available for third-party apps.

Do not silently omit a complication type because its implementation is unclear.

If there is uncertainty about:

* whether a complication type should be supported;
* how prayer information should map to it;
* what data should be displayed;
* differences between complication APIs or watch-face formats;

ask me before making a product decision.

Design each supported complication according to the amount and type of information its format can reasonably display rather than forcing identical content into every complication.

---

# Phase 4B — Android + WearOS Integration Test Gate

Test Android and WearOS together using phone and watch emulators.

Test at minimum:

* initial pairing/setup;
* profile synchronization;
* active profile changes;
* creation of new profiles;
* profile deletion;
* prayer-time synchronization;
* countdown synchronization;
* Friday Jumuah naming;
* watch app launched without phone app currently open;
* phone app launched without watch app open;
* temporary connectivity loss;
* reconnection;
* watch app restart;
* phone app restart;
* emulator/device restart;
* complication updates;
* stale complication data;
* multiple profiles;
* notification/countdown consistency.

Verify that phone, widgets, notifications, watch app, and complications agree on the same prayer state.

Repeat implementation → testing → fixing until the Android/WearOS pair is stable.

---

# Parallel Execution Rule

After Android is complete, there are two independent implementation branches:

## Apple branch

`Android → iOS → watchOS`

## Google branch

`Android → WearOS`

Progress on one branch must not unnecessarily block the other.

Examples:

* If Android is complete and WearOS is still being developed, begin iOS.
* If Android is complete and iOS is still being developed, begin WearOS.
* If iOS is complete but WearOS is not, begin watchOS.
* Do not wait for all four platforms before testing each valid phone/watch pair.

Each branch should independently continue through its implementation and validation loop.

---

# Cross-Platform Consistency Test

After all four applications are implemented:

* Android
* WearOS
* iOS
* watchOS

perform a final cross-platform review.

For the same profile, location, date, timezone, and configuration, verify that all platforms agree on:

* calculated prayer times;
* current prayer;
* next prayer;
* Friday Jumuah naming;
* countdown direction;
* countdown value;
* +30-minute transition behavior;
* selected profile;
* displayed configuration.

Platform-specific UI may differ, but domain behavior should not.

Where operating-system timing or background restrictions prevent exact second-for-second rendering, the underlying prayer state and transition rules must still be consistent.

---

# Final Completion Loop

Before declaring the implementation complete:

1. Run all automated tests.
2. Run all unit tests.
3. Run all integration tests.
4. Run UI tests where available.
5. Perform emulator/simulator-based end-to-end testing.
6. Test Android ↔ WearOS pairing.
7. Test iOS ↔ watchOS pairing.
8. Review logs for unexpected errors or warnings.
9. Check for crashes and lifecycle issues.
10. Check background behavior.
11. Check widgets and complications for stale data.
12. Check all prayer-time boundary conditions.
13. Fix every discovered issue.
14. Repeat the complete relevant test suite after each fix.

Continue this loop until there are no known functional regressions or unresolved implementation issues.

Do not consider a feature complete merely because it builds successfully. It is complete only after its end-to-end behavior has been verified.