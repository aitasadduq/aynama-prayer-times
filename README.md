# aynama

Open-source Muslim prayer times & spiritual companion app for Android, iOS, WearOS, and watchOS.

## What makes it different

**Multiple prayer profiles** — like the Weather app. Switch between Home, Office, Travel, and custom calculation methods. No other prayer app offers this.

**Deep watch integration** — complications, taraweeh counter, tasbeeh. Watch is a first-class surface, not an afterthought.

**Platform-native** — Jetpack Compose with Material 3 components on Android, SwiftUI with Liquid Glass on iOS. No cross-platform UI framework. The Android app uses its own fixed palette rather than wallpaper-derived Material You colours.

## Platforms

| Platform | Status |
|---|---|
| Android (phone + widgets) | v1 — in development. Built so far: prayer times with multiple profiles, Qibla, prayer tracker, notifications, four home-screen widgets, an optional live countdown notification |
| WearOS | v2 — in progress: watch app, complications and tile, with profiles synced from the phone |
| iOS (phone + widgets) | v3 — in progress: Swift domain logic, the profile pager and prayer ribbon, profiles, Qibla, tracker, settings and prayer alerts; widgets and Live Activity not started; simulator tests run in CI |
| watchOS | v3 — not started |

## Architecture

Independent native projects, validated by shared JSON test vectors. Prayer time math is handled by [Adhan](https://github.com/batoulapps/adhan-kotlin) (Batoul Apps) on both platforms: `com.batoulapps.adhan:adhan:1.2.1` on Android and Adhan-Swift 1.5.0 on iOS. The vectors in `test-vectors/prayer-times/` are generated from Adhan-Kotlin by `scripts/adhan-parity/generate.py`; CI checks them against the schema and runs the Swift tests against them. The Android tests still check Adhan against hard-coded Makkah values.

Android CI runs phone, shared-logic and watch unit tests and lint, builds debug and R8 release APKs, and runs phone/Room instrumentation on API 31 and 36. City-search time zones use a bundled offline boundary lookup; existing city profiles are repaired once on upgrade.

```
aynama/
├── test-vectors/          ← JSON contract between platforms: schema + generated vectors
├── android/
│   ├── app/               ← Kotlin + Jetpack Compose phone app, incl. Glance widgets
│   ├── shared-logic/      ← Adhan wrapper, countdown timeline, Qibla maths, Room database
│   └── wear/              ← WearOS app, complications and tile
├── ios/                  ← SwiftUI app, SharedLogic package and simulator tests
└── scripts/               ← test-vector generator and validator
```

See [architecture-design.md](architecture-design.md) for the full spec.

## Design

The design system is editorial and warm — Fraunces + IBM Plex, parchment and ink, saffron accent. Two deliberate departures from the prayer app genre: a vertical prayer timeline instead of circular countdown rings, and a typographic arrow instead of a compass-with-needle.

Read [DESIGN.md](DESIGN.md) before any UI work. Hard rules are non-negotiable. DESIGN.md §27 lists where the Android app doesn't meet them yet.

## Contributing

All code changes require corresponding regression tests in the same PR. **Every change must
pass both Android and iOS CI**, even when it touches only one platform or documentation.

The [mobile CI workflow](.github/workflows/ios.yml) validates the shared vectors, runs the Swift
domain suite, builds and tests the iOS app on a simulator (SwiftData persistence and profile
UI flows), and runs Android builds, lint, unit tests, and phone and Wear OS instrumentation.
Test reports and the iOS `.xcresult` bundle are retained as workflow artifacts for 14 days.

Wait for the **Android and iOS** check on the final PR revision before merging. Repository
administrators must make this check required in branch protection for `main` and `agent-main`
after this workflow lands; the workflow itself cannot configure repository protection.

For local checks, run `./gradlew testDebugUnitTest` in `android/` and `swift test` in
`ios/SharedLogic`. Run phone/Room and watch instrumentation on separate emulators because
`:app` and `:wear` share an application ID. Generate the iOS project with `cd ios && xcodegen
generate`, then run `xcodebuild test -project Aynama.xcodeproj -scheme Aynama -destination
"platform=iOS Simulator,name=<installed iPhone>"`.

## License

Apache License 2.0 — see [LICENSE](LICENSE). Third-party attribution obligations (Adhan, bundled fonts, future Quran text) are in [legal-posture.md](legal-posture.md).
