# Legal Posture — aynama-prayer-times

Sub-spec of `architecture-design.md`. Covers license obligations for all bundled code and text.

## Code: Adhan library (Batoul Apps, MIT)

> Verified 2026-09-24: `batoulapps/adhan-java` (the repo is now `batoulapps/adhan-kotlin`) and `batoulapps/adhan-swift` both ship the MIT License, © 2016 Batoul Apps. The adhan 1.2.1 POM has no licence tag, so the upstream `LICENSE` governs.

**Obligation (MIT):**
- Include the copyright notice and the MIT permission notice in all copies or substantial portions of the library.

**Implementation:**
- Copy `LICENSE` from the upstream Adhan repos (Adhan-Kotlin, Adhan-Swift) into `/third_party/adhan-kotlin/` and `/third_party/adhan-swift/`.
- In-app About screen: "Powered by Adhan by Batoul Apps — MIT License. [View license]."
- Google Play / App Store listings: attribution in the description or linked docs page.
- If we modify Adhan source, keep the MIT notice in the modified files.

**Strategy:** ship unmodified Adhan releases when possible. Prefer raising issues/PRs upstream over forking. If we must fork, make the fork public and named clearly (`aynama-prayer-times/adhan-kotlin-fork`).

## Code: Google Play services (proprietary)

On `agent-main` the phone app depends on `com.google.android.gms:play-services-wearable` 18.2.0 for the watch sync (`android/app/build.gradle.kts`), which pulls in `play-services-base`, `-basement` and `-tasks`. Their POMs give the licence as the "Android Software Development Kit License" (<https://developer.android.com/studio/terms.html>), not an open-source licence.

- **Play Store build:** list Google Play services on the licences screen.
- **F-Droid:** the inclusion policy forbids Google Play Services in any app (<https://f-droid.org/docs/Inclusion_Policy/>). The planned F-Droid listing (`architecture-design.md`, next steps 9) needs a build without the watch sync, or a free transport for it.

## Fonts (bundled in Android v1)

| Font | File | Copyright (from the font's `name` table) | Licence |
|---|---|---|---|
| Fraunces (variable) | `android/app/src/main/res/font/fraunces.ttf` | "Copyright 2020 The Fraunces Project Authors (github.com/undercasetype/Fraunces)" | SIL Open Font License 1.1 |
| IBM Plex Sans (variable) | `android/app/src/main/res/font/ibm_plex_sans.ttf` | "Copyright 2019 IBM Corp. All rights reserved." | SIL Open Font License 1.1 |

The copyright and licence notices (name IDs 0, 13, 14) were read directly from the bundled files on 2026-09-23.

**What OFL 1.1 requires** (licence text at the URL embedded in both fonts: <https://scripts.sil.org/OFL>):
- The fonts may be bundled and redistributed with the app, but not sold on their own.
- Each copy must carry the copyright notice and the licence. Machine-readable metadata inside the font counts only "as long as those fields can be easily viewed by the user". A TTF buried in an APK arguably isn't easily viewable. In any case, both fonts' name ID 13 holds only a one-line pointer to the licence, not its text, so the metadata can't satisfy this on its own: ship the OFL text.
- Modified versions may not use any Reserved Font Name. Subsetting or instancing — for example, making static widget weights (DS10 in `REVIEW-FINDINGS.md`) — creates a modified version. IBM Plex's licence reserves the name "Plex", so a modified Plex must be renamed; Fraunces's licence reserves no name.

**Implementation:**
- Add both fonts, with the full OFL 1.1 text, to the About / licences screen.
- Keep the bundled TTFs unmodified. Static instances derived for widgets (DS10) are modified versions: fine for Fraunces, which reserves no name, as long as they stay under OFL 1.1.

Tracks 2–4 (IBM Plex Sans Arabic, KFGQPC Uthman Taha Naskh, Amiri Quran) aren't bundled yet. Check their licences when they are; KFGQPC's terms in particular are still unverified.

## Text: Quran (Arabic script)

**Chosen source:** **Tanzil Quran Text** (https://tanzil.net/download/).

**Variants bundled:**
- **Uthmani** ("Simple Enhanced") — standard script used in Arab Gulf, Middle East, and global print editions.
- **Naskh** — preferred script for South Asian populations (Pakistan, India, Bangladesh) and the broader diaspora. Tanzil distributes a vetted Naskh variant at the same URL.

**License:** Both variants are released under Creative Commons Attribution-NoDerivatives 3.0 Unported (CC BY-ND 3.0). Applies equally to Uthmani and Naskh. Requires:
- Attribution: "Quran text courtesy of Tanzil Project."
- No modification of the text itself.
- Link back to tanzil.net in About.

**Alternative considered:** King Fahd Complex — restrictive redistribution terms; avoid for OSS app.

**Implementation:**
- Bundle both variants as separate SQLite files: `/assets/quran/tanzil-uthmani.db` and `/assets/quran/tanzil-naskh.db`.
- User selects preferred script in Settings (persisted in SharedPreferences/UserDefaults). Default: Uthmani.
- Attribution in About screen + in the Quran view footer: "Quran text courtesy of Tanzil Project."
- Never edit either bundled file programmatically (respects ND clause for both).

## Text: Translations

**Chosen translations (v3+):**
- **English:** Sahih International — public domain (no attribution required, but included as courtesy).
- **Urdu:** Maulana Fateh Muhammad Jalandhari — public domain.
- **Bahasa Indonesia:** Kemenag (Ministry of Religious Affairs) — public use permitted with attribution.

**Excluded from v1–v3 (licensing friction):**
- Pickthall (modernized editions are copyrighted).
- Yusuf Ali (various copyright claims on modern printings).
- Abdel Haleem / Oxford (copyrighted, licensing required).

**Implementation:**
- Each translation shipped as a separate SQLite file under `/assets/translations/{lang}/{translator}.db`.
- Attribution per translation in the translation-picker UI.

## Trademark + name

- "aynama" is the working project name. Check USPTO + EUIPO before first public release for conflicts. (Not done this review — add to TODOS.md.)
- App icons and name may not imply endorsement by any Islamic authority.

## License of this project

- **Code:** Apache License 2.0 — decided; `LICENSE` in the repo root (adopted 2026-04-24, commit `77ef0c6`).
- **Test vectors:** CC0 or MIT — they're factual data, should be permissive to encourage adoption.
- **Documentation:** CC BY 4.0.

## Blocks on first public release

- [ ] Adhan `LICENSE` files vendored (MIT).
- [ ] About screen with Adhan + Tanzil attribution — plus Fraunces and IBM Plex Sans (OFL 1.1). Android v1 has no About screen yet.
- [ ] Google Play listing includes license note.
- [x] Chosen project license decided (Apache 2.0) and `LICENSE` file in repo root.
- [ ] Trademark clearance on "aynama" (moved to TODOS.md).
- [ ] Decide how the F-Droid build handles the Play services dependency (see above).
