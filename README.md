# aynama

Open-source Muslim prayer times & spiritual companion app for Android, iOS, WearOS, and watchOS.

## What makes it different

**Multiple prayer profiles** — like the Weather app. Switch between Home, Office, Travel, and custom calculation methods. No other prayer app offers this.

**Deep watch integration** — complications, taraweeh counter, tasbeeh. Watch is a first-class surface, not an afterthought.

**Platform-native** — Material You on Android, Liquid Glass on iOS. No cross-platform UI framework.

## Platforms

| Platform | Status |
|---|---|
| Android (phone + widgets) | v1 |
| WearOS | v2 |
| iOS (phone + widgets) | v3 |
| watchOS | v3 |

## Architecture

Independent native projects validated by shared JSON test vectors. Prayer time math is handled by [Adhan](https://github.com/batoulapps/adhan-kotlin) (Batoul Apps, Apache 2.0) on both platforms. CI runs test vectors on every commit.

```
aynama/
├── shared-test-vectors/   ← JSON contract between platforms
├── android/               ← Kotlin + Jetpack Compose
├── ios/                   ← Swift + SwiftUI
└── scripts/               ← test vector generator
```

See [architecture-design.md](architecture-design.md) for the full spec.

## Design

The design system is editorial and warm — Fraunces + IBM Plex, parchment and ink, saffron accent. Two deliberate departures from the prayer app genre: a vertical timeline ribbon instead of circular countdown rings, and a typographic arrow instead of a compass-with-needle.

Read [DESIGN.md](DESIGN.md) before any UI work. Hard rules are non-negotiable.

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

## License

To be decided before first public release (Apache 2.0 preferred — matches Adhan upstream). See [legal-posture.md](legal-posture.md) for third-party attribution obligations.
