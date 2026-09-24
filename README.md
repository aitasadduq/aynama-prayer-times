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
| iOS (phone + widgets) | v3 — started: the shared prayer-time logic is ported to Swift (`ios/SharedLogic`); no app yet |
| watchOS | v3 — not started |

## Architecture

Independent native projects, validated by shared JSON test vectors. Prayer time math is handled by [Adhan](https://github.com/batoulapps/adhan-kotlin) (Batoul Apps) on both platforms: `com.batoulapps.adhan:adhan:1.2.1` on Android and Adhan-Swift 1.5.0 on iOS. The vectors in `test-vectors/prayer-times/` are generated from Adhan-Kotlin by `scripts/adhan-parity/generate.py`; CI checks them against the schema and runs the Swift tests against them. The Android tests still check Adhan against hard-coded Makkah values.

```
aynama/
├── test-vectors/          ← JSON contract between platforms: schema + generated vectors
├── android/
│   ├── app/               ← Kotlin + Jetpack Compose phone app, incl. Glance widgets
│   ├── shared-logic/      ← Adhan wrapper, countdown timeline, Qibla maths, Room database
│   └── wear/              ← WearOS app, complications and tile
├── ios/SharedLogic/       ← Swift package: Adhan-Swift wrapper and the shared-logic ports
└── scripts/               ← test-vector generator and validator
```

See [architecture-design.md](architecture-design.md) for the full spec.

## Design

The design system is editorial and warm — Fraunces + IBM Plex, parchment and ink, saffron accent. Two deliberate departures from the prayer app genre: a vertical prayer timeline instead of circular countdown rings, and a typographic arrow instead of a compass-with-needle.

Read [DESIGN.md](DESIGN.md) before any UI work. Hard rules are non-negotiable. DESIGN.md §27 lists where the Android app doesn't meet them yet.

## Contributing

All code changes require corresponding tests in the same PR. Before opening a pull request, run the unit tests from `android/` with `./gradlew test` and, with an emulator or device attached, the instrumented tests (Room, Hijri offset, alarm delivery, widget binding). `:app` and `:wear` share an application ID, so run `./gradlew :app:connectedAndroidTest :shared-logic:connectedAndroidTest` on a phone and `./gradlew :wear:connectedAndroidTest` on a Wear OS emulator, picking each with `ANDROID_SERIAL`. For the Swift package, run `swift test` in `ios/SharedLogic`.

## License

Apache License 2.0 — see [LICENSE](LICENSE). Third-party attribution obligations (Adhan, bundled fonts, future Quran text) are in [legal-posture.md](legal-posture.md).
