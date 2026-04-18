# Legal Posture — aynama-prayer-times

Sub-spec of `architecture-design.md`. Covers license obligations for all bundled code and text.

## Code: Adhan library (Batoul Apps, Apache 2.0)

**Obligation (Apache 2.0 §4):**
- Include full Apache 2.0 license text with distribution.
- Preserve copyright, patent, trademark, and attribution notices.
- Propagate `NOTICE` file contents (if any) into derivative work.
- State changes if the library is modified.

**Implementation:**
- Copy `LICENSE` and `NOTICE` from upstream Adhan repos (Adhan-Kotlin, Adhan-Swift) into `/third_party/adhan-kotlin/` and `/third_party/adhan-swift/`.
- In-app About screen: "Powered by Adhan by Batoul Apps — Apache License 2.0. [View license]."
- Google Play / App Store listings: attribution in the description or linked docs page.
- If we modify Adhan source, mark the modification in the file header and in `CHANGES.md` per Apache §4(b).

**Strategy:** ship unmodified Adhan releases when possible. Prefer raising issues/PRs upstream over forking. If we must fork, make the fork public and named clearly (`aynama-prayer-times/adhan-kotlin-fork`).

## Text: Quran (Arabic Uthmani script)

**Chosen source:** **Tanzil Quran Text** (https://tanzil.net/download/).

**License:** Creative Commons Attribution-NoDerivatives 3.0 Unported (CC BY-ND 3.0) for the Simple Enhanced variant. Requires:
- Attribution: "Quran text courtesy of Tanzil Project."
- No modification of the text itself.
- Link back to tanzil.net in About.

**Alternative considered:** King Fahd Complex — restrictive redistribution terms; avoid for OSS app.

**Implementation:**
- Bundle Tanzil's pre-vetted Uthmani text as SQLite in `/assets/quran/`.
- Attribution in About screen + in the Quran view footer.
- Never edit the bundled text programmatically (respects ND clause).

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

- **Code:** MIT or Apache 2.0 (choose before first public release; lean Apache 2.0 to match Adhan upstream and simplify derivative compliance).
- **Test vectors:** CC0 or MIT — they're factual data, should be permissive to encourage adoption.
- **Documentation:** CC BY 4.0.

## Blocks on first public release

- [ ] Adhan NOTICE / LICENSE files vendored.
- [ ] About screen with Adhan + Tanzil attribution.
- [ ] Google Play listing includes license note.
- [ ] Chosen project license (MIT vs Apache 2.0) decided and `LICENSE` file in repo root.
- [ ] Trademark clearance on "aynama" (moved to TODOS.md).
