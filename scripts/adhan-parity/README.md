# Adhan cross-port parity

The pre-v3 gate from `TODOS.md`: **the Swift and Kotlin Adhan ports must agree, to the minute,
on every city the app is tested against.** Android is the behavioural reference (Phase 3A), so
the vectors are what Adhan-Kotlin actually produces and iOS is held to them.

## What is here

| File | |
|---|---|
| `cases.json` | The twelve parity cities, plus a ten-point probe across the 48° high-latitude threshold. Both sets were defined here — they did not exist anywhere in the repo before. |
| `AdhanKotlinRunner.java` | A thin mirror of `AdhanWrapper.kt` against the pinned Adhan-Kotlin JAR. Prints one case as JSON. |
| `generate.py` | Runs the cities through it and writes `test-vectors/prayer-times/<method>.json`, plus `high-latitude-probe.json`. |
| `high-latitude-probe.json` | Adhan-Kotlin's answers across 47°–65°N, checked in so the divergence below can be re-read without a JVM. |

The Adhan JAR is downloaded from Maven Central on first run and gitignored; the version is
pinned in `../reference-versions.json`.

## Running it

```sh
python3 scripts/adhan-parity/generate.py      # regenerate vectors (needs java + javac)
cd ios/SharedLogic && swift test              # assert Adhan-Swift matches them
```

The assertion lives in `ios/SharedLogic/Tests/SharedLogicTests/VectorParityTests.swift` and runs
under plain `swift test` — no simulator, no scheme — so it is cheap enough to be a CI gate on
every commit.

## The one place the ports disagree

Adhan-Kotlin 1.2.1 applies `MIDDLE_OF_THE_NIGHT` everywhere; its `nightPortions()` has no
coordinates parameter. Adhan-Swift 1.5.0 leaves `highLatitudeRule` nil and falls back to
`HighLatitudeRule.recommended(for:)`, which returns `.seventhOfTheNight` above 48°.

Eleven of the twelve cities agree to the minute. London on the June solstice is **157 minutes
apart on Fajr and 158 on Isha** — not rounding, a different answer.

`AdhanWrapper.highLatitudeRule` pins `.middleOfTheNight` so iOS matches Android, and
`HighLatitudeParityTests` proves the pin is load-bearing rather than decorative. The product
question the pin defers — middle-of-the-night collapses London's Fajr and Isha onto one instant
for weeks around midsummer — is recorded under "Known issues" in `TODOS.md`.
