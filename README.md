# aynama

Open-source Muslim prayer times & spiritual companion app for Android, iOS, WearOS, and watchOS.

## What makes it different

**Multiple prayer profiles** — like the Weather app. Switch between Home, Office, Travel, and custom calculation methods. No other prayer app offers this.

**Deep watch integration** — complications, taraweeh counter, tasbeeh. Watch is a first-class surface, not an afterthought.

**Platform-native** — Jetpack Compose with Material 3 components on Android, SwiftUI with Liquid Glass on iOS. No cross-platform UI framework. The Android app uses its own fixed palette rather than wallpaper-derived Material You colours.

## Platforms

| Platform | Status |
|---|---|
| Android (phone + widgets) | v1 — in development. Built so far: prayer times with multiple profiles, Qibla, prayer tracker, notifications, four home-screen widgets |
| WearOS | v2 — not started |
| iOS (phone + widgets) | v3 — not started |
| watchOS | v3 — not started |

## Architecture

Independent native projects, validated by shared JSON test vectors. Prayer time math is handled by [Adhan](https://github.com/batoulapps/adhan-kotlin) (Batoul Apps) on both platforms: `com.batoulapps.adhan:adhan:1.2.1` on Android. The vectors aren't generated or run in CI yet; today the Android tests check Adhan against hard-coded Makkah values.

```
aynama/
├── test-vectors/          ← JSON contract between platforms (schema only so far)
├── android/
│   ├── app/               ← Kotlin + Jetpack Compose phone app, incl. Glance widgets
│   └── shared-logic/      ← Adhan wrapper, Qibla maths, Room database
├── ios/                   ← Swift + SwiftUI (planned)
└── scripts/               ← test vector generator (planned)
```

See [architecture-design.md](architecture-design.md) for the full spec.

## Design

The design system is editorial and warm — Fraunces + IBM Plex, parchment and ink, saffron accent. Two deliberate departures from the prayer app genre: a vertical prayer timeline instead of circular countdown rings, and a typographic arrow instead of a compass-with-needle.

Read [DESIGN.md](DESIGN.md) before any UI work. Hard rules are non-negotiable. DESIGN.md §21 lists where the Android app doesn't meet them yet.

## Contributing

All code changes require corresponding tests in the same PR. Before opening a pull request, run the unit tests from `android/` with `./gradlew test`. The shared test-vector suite isn't set up yet.

## License

Apache License 2.0 — see [LICENSE](LICENSE). Third-party attribution obligations (Adhan, bundled fonts, future Quran text) are in [legal-posture.md](legal-posture.md).
