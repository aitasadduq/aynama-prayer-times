# aynama-prayer-times — Design System

**Status:** v1. Governs all UI decisions across iOS, Android, watchOS, WearOS. Re-baselined in September 2026 against Android v1 as built on `agent-main`.
**Aesthetic:** Contemplative editorial. Warm. Hospitable. Unhurried.
**Stance:** Hybrid — reverent hero, utilitarian tools.

Every UI decision in this repo traces back to this document. If a new surface contradicts something here, update this file first, then the code.

> **How to read this revision.** Android v1 is the first built surface, and this file now describes it. Where the app deliberately moved away from the April 2026 spec, the spec now follows the app. That covers the stepped surface cycle, the Qibla panels, the four widgets, the ±2-day Hijri adjustment, and location time zone being on by default. Where the app breaks a hard rule (contrast, no Roboto, tabular numerals), **the rule stands**, and the breach is listed in §27 with a finding ID (`DS…`) tracked in `REVIEW-FINDINGS.md`. "Android v1" means the code on `agent-main` at `5eeed05`: Android, WearOS and iOS work merges there, and `main` is behind it. §19–§24 are that branch's specs; the sync added §25–§27. Android dp and sp map 1:1 onto the pt values used in the platform-neutral tables.

---

## 1. Visual Thesis

Aynama feels like sitting on a cool stone floor at dusk with a cup of cardamom tea. Not a mosque interior. Not a minimalist tech product. The first three seconds should read as *hospitable* — a host dimming the lights for you — rather than *sacred* — a building commanding reverence.

Reverence comes from restraint, not gold filigree. Warm shadow, paper grain, one deliberate ornament per screen.

The app is a companion, not a shrine.

---

## 2. Design Stance: Hybrid

Two modes. The surface tells you which one you're in.

**Contemplative screens** (reverent hero treatment, time-of-day surface):
- Home — countdown hero + prayer timeline. The countdown lives here; Android v1 has no standalone countdown screen.
- Qibla

**Utilitarian screens** (pragmatic clarity, stable surface):
- Settings (profiles) and the profile sheet
- Notifications and Adhan voice
- Tracker (prayer history)
- Every bottom sheet, including the mark-prayer sheet opened from Home
- The widget configuration screen
- Later: Zakat calculator, Quran reader (text is sacred; the UI serving it is not)

**Glanceable surfaces** (fixed palette, no cycle): home-screen widgets (§25), system notifications (§15, §22), and the watch (§7).

Contemplative surfaces carry the time-of-day cycle. Utilitarian surfaces stay on a stable neutral. Do not bleed one into the other.

---

## 3. Color System

### Core tokens

| Token | Hex | Role |
|---|---|---|
| `ink` | `#1C1A17` | Warm brown-black. Primary text on light. Primary surface on dark. |
| `ink-muted` | `#6B6560` | Secondary text and passed-prayer states **on light surfaces**. Not for text on `ink` (3.02:1). |
| `parchment` | `#F2EAD8` | Unbleached linen. Primary surface on light. Primary text on dark. |
| `parchment-muted` | `#D4C9B1` | Dividers, switch-off tracks, empty-square strokes. Secondary text on `ink` (10.57:1). |
| `saffron` | `#B87A2E` | The accent **as a fill**: filled buttons, switch tracks, the current-prayer dot, on-time squares, the selected page dot. As text, only on `ink`. |
| `saffron-ink` | `#8A5A22` | The accent **as text or a thin stroke on light surfaces** (4.92:1 on parchment). Also the pressed state of saffron fills. |

**Rules:**
- `ink` is NOT `#000`. NOT slate. It is warm and brown-tinted.
- `parchment` is NOT white. NOT cream. It is unbleached linen.
- Saffron is the only accent. No secondary accent colour. Do not add emerald, teal, purple, indigo, or mosque-green anywhere, even as "just a hint."
- Saffron-coloured text on a light surface is `saffron-ink`, never `saffron` (2.99:1 — fails AA at every size). Labels on a `saffron` fill are `ink` (4.85:1), never `parchment` (2.99:1). The Qibla screen already follows this; other surfaces do not yet (DS2).

### Time-of-day surface cycle (contemplative screens only)

Each phase is a vertical two-stop gradient. The phase comes from the active profile's prayer times. The surface **steps** at each prayer boundary and cross-fades over 3 s, and it does the same when you page between profiles in different phases. It does not interpolate continuously through the day. The April spec asked for continuous interpolation, Android v1 shipped steps, and this spec now follows the app.

| Phase | Window | Top | Bottom | Foreground | Notes |
|---|---|---|---|---|---|
| Isha (night) | Isha → Fajr, across midnight | `#0F1419` | `#1C1A17` | parchment | Deep stone. Near-mono. |
| Fajr (predawn) | Fajr → sunrise | `#1C1A17` | `#3A3530` | parchment | Warm ink to predawn warmth. **NOT indigo. NOT cool blue.** |
| Sunrise transition | sunrise → Dhuhr | `#EDE1C5` | `#E8C89A` | ink | Morning light: linen into honey. |
| Dhuhr (midday) | Dhuhr → Asr | `#F2EAD8` | `#EDE1C5` | ink | Flat linen parchment. Bright. |
| Asr (afternoon) | Asr → Maghrib | `#E8C89A` | `#B87A2E` | ink | Honey to saffron. Asr per the profile's madhab. |
| Maghrib (sunset) | Maghrib → Isha | `#6B2E2A` | `#1C1A17` | parchment | Oxblood into ink. |

- When Isha falls after midnight (high latitudes in summer), Maghrib holds until Isha. Near the June solstice at about 50°N and up this breaks: Isha and Fajr land on the same clock time (DS31).
- A profile with no computable times (§26) uses the Isha surface.
- Home and Qibla evaluate the phase in the profile's effective time zone (§17).

Utilitarian screens ignore this cycle. They stay on `parchment` when the system is in light mode and on `ink` in dark mode.

### Contrast & accessibility

All text must meet **WCAG AA**: 4.5:1 for body text, 3:1 for large text. WCAG's large-text sizes, 18 pt or 14 pt bold, are typographic points: about 24 sp, or 18.7 sp bold, counting one sp or iOS point as one CSS pixel. The pt values in this file's type tables are iOS points, not typographic points. Ratios here use the WCAG 2.x relative-luminance formula (W3C, *WCAG 2.1*, definitions of "contrast ratio" and "relative luminance": <https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio>).

The April table misstated three pairs: ink on parchment (listed 13.8:1, actually 14.50:1), ink-muted on parchment (listed 5.2:1, actually 4.80:1) and — the one that matters — **saffron on parchment (listed 4.6:1 "AA body", actually 2.99:1, which fails)**.

| Foreground | Background | Ratio | AA body | AA large |
|---|---|---|---|---|
| ink | parchment | 14.50:1 | ✓ | ✓ |
| parchment | ink | 14.50:1 | ✓ | ✓ |
| parchment-muted | ink | 10.57:1 | ✓ | ✓ |
| ink | parchment-muted | 10.57:1 | ✓ | ✓ |
| saffron-ink | parchment | 4.92:1 | ✓ | ✓ |
| saffron | ink | 4.85:1 | ✓ | ✓ |
| ink | saffron | 4.85:1 | ✓ | ✓ |
| ink-muted | parchment | 4.80:1 | ✓ | ✓ |
| ink-muted | parchment-muted | 3.50:1 | ✗ | ✓ |
| ink-muted | ink | 3.02:1 | ✗ | ✓ |
| saffron | parchment | 2.99:1 | ✗ | ✗ |
| parchment | saffron | 2.99:1 | ✗ | ✗ |
| saffron | parchment-muted | 2.18:1 | ✗ | ✗ |

**On the gradient.** The ribbon text on Home is 20 pt at weight 500, which is body size, so it needs 4.5:1. The ratios below are estimates at each row's position on a roughly 700 dp-tall pager. They move with screen height.

| Phase | Weakest text on Home | Ratio |
|---|---|---|
| Asr | Passed rows and Sunrise (`ink-muted` over honey→saffron); the Qaḍā line (ink at 60%) | **2.4–2.8:1** ✗ |
| Sunrise | Passed Fajr and Sunrise (`ink-muted`); the Qaḍā line | 3.9–4.1:1 ✗ |
| Fajr | Current Fajr row (`saffron`) | 4.36:1 ✗ |
| Maghrib | Current Maghrib row (`saffron`) | 4.36:1 ✗ |
| Dhuhr | Passed rows (`ink-muted`) | 4.63–4.67:1 ✓ (the Qaḍā line, 4.17:1, fails) |
| Isha | Current Isha row (`saffron`) | 4.90:1 ✓ |

Tracked as DS3. Any new colour must be checked against ink, parchment, and every gradient stop it can sit on.

**Not yet audited: non-text contrast** (WCAG 1.4.11: 3:1 for control boundaries and meaningful graphics). Likely failures: `parchment-muted` on parchment (1.37:1) for switch-off tracks, empty tracker squares and outlined-button borders; and the saffron page dot, the saffron Add profile FAB and the Qibla arrow against the Asr gradient, whose bottom stop is saffron.

### Colours outside the token set (in use)

| Colour | Where | Status |
|---|---|---|
| `#3A3530` `#EDE1C5` `#E8C89A` `#6B2E2A` `#0F1419` | Gradient stops | Part of the cycle above; not for other use. |
| `#6B2E2A` at 90% | Ramadan banner on Home | Reuses the Maghrib oxblood stop; parchment text 6.7:1. Promote to a token or drop (DS19). |
| `#7A5800` at 90% with `#FFF3CD` text | Qibla calibration banner | Off-palette caution pair, 4.8–6.5:1 (DS19). |
| Material baseline error `#B3261E`, error container `#F9DEDC` | Delete profile button and swipe-to-delete background | Off-palette (DS19). |

### Android: Material 3 colour roles

Android maps the tokens onto Material 3 colour roles in `ui/theme/AynamaTheme.kt`. Dynamic colour (Material You wallpaper extraction) is never used.

| Role | Light | Dark |
|---|---|---|
| primary / onPrimary | saffron / parchment ✗ (2.99:1) | saffron / ink |
| primaryContainer / onPrimaryContainer | parchment-muted / ink | saffron-ink / parchment |
| secondary, tertiary / their on- roles | saffron-ink / parchment | saffron / ink |
| secondaryContainer, tertiaryContainer / their on- roles | parchment-muted / ink | ink-muted / parchment |
| background, surface / onBackground, onSurface | parchment / ink | ink / parchment |
| surfaceVariant / onSurfaceVariant | parchment-muted / ink-muted | ink-muted / parchment-muted |
| surfaceContainerLowest, surfaceContainerLow, surfaceContainer | parchment | ink |
| surfaceContainerHigh, surfaceContainerHighest | parchment-muted | ink-muted |
| surfaceBright / surfaceDim | parchment / parchment-muted | ink-muted / ink |
| inverseSurface / inverseOnSurface | ink / parchment | parchment / ink |
| inversePrimary / surfaceTint | saffron / saffron | saffron-ink / saffron |
| outline / outlineVariant | ink-muted / parchment-muted | ink-muted / ink-muted |
| scrim | ink | ink |
| error roles | Material's baseline red (DS19) | Material's baseline red (DS19) |

Every role except `error` has been mapped from the tokens since the Phase 2 validation gate (#27). One contrast gap remains: light `onPrimary` is parchment, 2.99:1 on saffron. The app's filled buttons and FABs override it with ink, so it shows only where a Material component reads the role itself, such as the selected number on the time picker's dial. **Rule:** set it to `ink`, as dark mode does. Tracked as DS1.

### Every Material role must be assigned

On Android, a `ColorScheme` role left unset keeps Material 3's **baseline palette, which is
purple** — and §10 forbids purple and indigo outright. Naming the dozen roles the app reads
directly is not enough.

This is not theoretical. `NavigationBar` draws its background from `surfaceContainer` and its
selected-item pill from `secondaryContainer`; neither was set, so the bottom navigation
rendered lavender on every screen of the app, in both themes, until the Phase 2 gate caught it.

`AynamaTheme` therefore assigns **every** role from the six tokens above:

- **secondary / tertiary** — saffron and its pressed shade. There is no second accent, so these
  are not an opportunity to introduce one.
- **surfaceContainer ladder** — Material's elevation tones flattened onto parchment/ink and
  their muted partners. The palette has no tint scale to climb, and §2 asks utilitarian
  surfaces to stay on one stable ground.
- **error** — Material's baseline red, listed above with the other colours from outside the token set (DS19).

When adding a Material component, check which roles it reads before assuming it inherits.

---

## 4. Typography

Four independent typography tracks. Do not mix them.

### Track 1 — UI Latin (display + body) — shipped

- **Display:** Fraunces (variable serif). Used for prayer names, the countdown hero, the Qibla degree readout, and section and screen headers.
- **Body:** IBM Plex Sans. Used for all running UI text, labels, buttons, times, dates.

Android bundles variable builds as `res/font/fraunces.ttf` and `res/font/ibm_plex_sans.ttf`:
- Fraunces axes: opsz 9–144, wght 100–900, SOFT, WONK. The file's default instance is wght 900 at opsz 9.
- IBM Plex Sans axes: wght 100–700, wdth 75–100.

Compose pins weight and optical size per style through font variation settings. RemoteViews widgets can't do that (DS10). Both fonts are SIL OFL 1.1, with the licence notice embedded in each file (see `legal-posture.md`).

### Track 2 — UI Arabic — not bundled yet

- **IBM Plex Sans Arabic** — matches the Latin stack, for Arabic UI chrome (menu items, labels, buttons in Arabic locale). Optical tuning is genuinely modern, not a grafted-on default.
- **This is NOT a Quranic font.** Do not render Quran text or dua with it.

### Track 3 — Quran Uthmani script — not bundled yet (v4)

- **KFGQPC Uthman Taha Naskh** (King Fahd Complex standard).
- Non-negotiable for Mushaf fidelity. Do not substitute.
- Used wherever the app displays Quranic verses in the Mushaf-standard script.

### Track 4 — Quran Naskh script — not bundled yet (v4)

- **Amiri Quran** (Khaled Hosny, SIL OFL). Tashkeel-perfect, open-source, optional coloured-tajweed variant.
- Alternate display for Quranic text when the user prefers Naskh over Uthmani.

### Numerals

Every time display uses tabular figures. **Non-negotiable — prayer times must not visually drift.** What the bundled files actually provide:

- **IBM Plex Sans:** figures are tabular by default. Every digit is 600/1000 em, so Plex times never drift. The file has no `tnum` feature, so `fontFeatureSettings = "tnum"` in code does nothing (and nothing is needed).
- **Fraunces:** figures are **proportional**. At the default instance, "1" is 1024/2000 em and "0" is 1461. The file has no `tnum` feature either, so Fraunces cannot set a number that changes in place without drifting.

Android v1 sets two changing numbers in Fraunces anyway: the Home countdown hero and the widget countdowns. The hero is left-aligned and changes every second (§19), so whenever a digit changes width, every character after it shifts (DS9). The Qibla degree readout is also Fraunces with a no-op `tnum`, but it shows the fixed Qibla bearing, so it doesn't change on screen.

**Rule until DS9 is resolved:** numbers that change in place are set in IBM Plex Sans. Fraunces numerals are for numbers that don't change on screen. The alternative is to bundle a Fraunces build that has tabular figures.

### Type scale

| Token | Size / line height | Font | Material 3 slot (Android) | Used for |
|---|---|---|---|---|
| `display-xl` | 72 / 1.0 | Fraunces 400, opsz 144 | `displayLarge` | Home countdown hero |
| `display-lg` | 48 / 1.05 | Fraunces 500, opsz 96 | `displayMedium` | Empty-state mark |
| `display-md` | 32 / 1.1 | Fraunces 500, opsz 48 | `displaySmall` | The prayer line under the countdown, sheet titles, "Profiles", "No prayer times today" |
| `title` | 20 / 1.25 | Fraunces 500, opsz 20 | `headlineMedium` | Timeline prayer names, section and week headers, profile names, picker titles |
| `body-lg` | 17 / 1.45 | IBM Plex Sans 400 | `bodyLarge` | Tracker "Today" rows, mark-sheet options, text-field input |
| `body` | 15 / 1.45 | IBM Plex Sans 400 | `bodyMedium` | Default UI, row labels |
| `body-sm` | 13 / 1.4 | IBM Plex Sans 500 | `bodySmall` | Metadata, captions, row values |
| `mono-num` | 17 / 1.0 | IBM Plex Sans 500 | `labelMedium` | Notification-row times, the ✓ mark, "Dismiss" |

- Timeline times use the `title` size and weight **in IBM Plex Sans**: Fraunces name, Plex time, same size.
- `display-md` is Material's `displaySmall`, not `displayMedium`, which is `display-lg`. The former PR #17 N3, withdrawn in this sync, misread this.
- `mono-num` sits in the `labelMedium` slot, and Material also uses that slot for navigation-bar labels, so nav labels render at 17 pt. That's why the nav caps font scale at 1.3× (DS20, PR #12 A6).
- **Unmapped Material slots** fall back to `FontFamily.SansSerif`, which is Roboto on stock Android. Those slots are `headlineLarge`, `headlineSmall`, `titleLarge`, `titleMedium`, `titleSmall`, `labelLarge` and `labelSmall`. Material uses `labelLarge` for every button and menu item. The code uses `labelSmall` directly (Hijri adjustment buttons, "ALERT TIME"). The time picker uses `titleMedium`. All of these render in Roboto today (DS5). **Rule:** define all 15 slots from the tokens.

Watch scales are defined separately in §7.

### Supplementary styles (in use, not yet tokens)

Code builds these ad hoc. PR #12 M8 asks for them to become named styles.

| Style | Spec | Where |
|---|---|---|
| Qibla degree | Fraunces 400, opsz 96, 56 pt, −0.025 em | Qibla readout panel |
| Qibla title | Fraunces 500, opsz 32, 28 pt, −0.01 em | Qibla title panel |
| North glyph | Fraunces 500, opsz 20, 16 pt | "N" on the Qibla ring |
| Eyebrow | IBM Plex Sans 500, 12 pt, +0.08 em, capitals | Qibla phase label ("DHUHR") |
| Caption | IBM Plex Sans 500, 11 pt, +0.1 em | Qibla alignment hint and bearing chip |
| Readout secondary | IBM Plex Sans 400, 14 pt | "4,832 km to the Kaaba" |
| Column initial | IBM Plex Sans 700, 12 pt | Tracker "F D A M I" header |

### Copy & transliteration

- Prayer names are plain: Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha, Imsak, and Jumuah for Friday's Dhuhr (§20).
- Scholarly transliteration (macrons, underdots, ʻayn) is used for Hijri months ("Rabīʻ II", "Dhu al-Ḥijjah"), madhab names ("Shāfiʻī", "Ḥanafī"), "Umm al-Qurā", and the greeting "Ramaḍān Mubārak".
- It isn't consistent yet: Home says "Qaḍā", the mark sheet says "Qada", the code says "Qaza", Notifications says "Ramadan Imsak", and §20's heading says "Jumu'ah" where the app shows "Jumuah" (DS23). Pick one form per word and apply it everywhere.
- Tone: calm and declarative. No exclamation marks, no encouragement copy (§16 copy rules apply app-wide).

### Rules

- No Inter. No Roboto. No Arial. No Helvetica. No system-default sans as a final choice. (Android v1 breaches this through Material fallbacks — DS5.)
- Fraunces is the only serif in use.
- Mixing Fraunces with IBM Plex Sans within the same line is fine (hero number + caption). Do not mix Fraunces with IBM Plex Sans Arabic in the same line — script mismatch is jarring.
- RTL layouts mirror correctly; Arabic text never uses Latin typography.

---

## 5. Composition & Layout

### Navigation shell (Android)

- A bottom navigation bar with four tabs: **Prayers** (Home, renamed from "Home" in May 2026), **Qibla**, **Tracker**, **Settings**.
- Notifications and Adhan voice are pushed routes inside the Settings tab.
- The bar is parchment with a parchment-muted indicator (ink and ink-muted in dark mode), and uses filled icons (DS21). Labels are capped at 1.3× font scale.
- On the Notifications and Adhan voice routes, no tab shows as selected, because selection compares exact routes (DS21).
- Content, including the time-of-day surface, is laid out below the status bar (DS18).
- Phone only, portrait only. There is no tablet or foldable layout yet.

### Home — countdown hero + prayer timeline

**Replaces** the industry-standard circular countdown ring.

```
┌──────────────────────────────────┐
│ London · MWL     11 Rabīʻ II 1448│  ← body-sm, 70% of foreground
│                                  │
│ -02:18:07                        │  ← display-xl, left-aligned, signed (§19)
│ Asr · 4:14 PM                    │  ← display-md: the prayer the countdown refers to
│                                  │
│  ✓  Fajr                5:12 AM  │  ← passed: muted token, ✓
│     Sunrise             6:48 AM  │  ← time reference: muted, no mark
│  ●  Dhuhr              12:55 PM  │  ← current: dot (ink on light phases, saffron on dark)
│     Asr                 4:14 PM  │  ← upcoming: foreground, no mark
│     Maghrib             6:57 PM  │
│     Isha                8:22 PM  │
│  3 outstanding Qaḍā          (+) │  ← body-sm, 60%, only when > 0; the Add profile FAB (§21)
│                ● •               │  ← page dots, one per profile
└──────────────────────────────────┘
```

**Header line**
- The profile name and its method's short name sit on the left ("London · MWL").
- The Hijri date sits on the right, with the profile's §18 adjustment applied.
- Both are `body-sm` at 70% of the foreground, on one line, truncated with an ellipsis.

**Hero**
- The countdown is the largest thing on screen: `display-xl`, left-aligned, following §19. It reads "-02:18:07" while counting down to an event, and "00:15:42", with no sign, for the first 30 minutes after a prayer begins. (`main` has centred it since 2026-05-29; #22 restored the left alignment on `agent-main`.)
- It counts to the next event, **Sunrise included**. Between Fajr and sunrise it counts to Sunrise, when Fajr's time ends; this is v1 feature #3, "time until the prayer becomes Qaḍā". Sunrise never counts up (§19).
- If no event can be found on one side of now, it shows `--:--:--`.
- Below it, in `display-md` and also left-aligned: the prayer the countdown refers to and that prayer's time, "Asr · 4:14 PM". While counting up, that's the prayer that has just begun.

**Timeline rows**

Six rows (seven in Ramadan) are spread evenly over the remaining height. Each row has a 20 dp mark slot, a 12 dp gap, the name in `title` (Fraunces), and the time right-aligned at `title` size in IBM Plex Sans. States:

- **Passed:** the phase's muted token (`ink-muted` on light phases, `parchment-muted` on dark phases) at full opacity, with a ✓. The April spec said 60% opacity, but alpha over a gradient was unreadable.
- **Current:** an 8 dp filled dot. The dot and text are `saffron` on dark phases and `ink` on light phases, because saffron on the light gradients fails contrast (§3).
- **Upcoming:** the surface foreground, no mark.
- **Sunrise:** muted, no mark.
- **Imsak:** Ramadan only, the first row, at Fajr − 10 min. Foreground until it passes, then muted. No mark.

On Friday the Dhuhr row reads Jumuah (§20).

Edge cases:
- After Isha, Isha stays current until midnight. From midnight to Fajr, the new day's rows are all upcoming.
- When Isha falls after midnight, Maghrib stays current until Isha. That holds only while Isha's clock time is earlier than Fajr's. Near the June solstice at about 50°N and up, adhan's default rule puts both at the middle of the night (London, 21 June: 01:02), so Home shows Isha as current, and Dhuhr, Asr and Maghrib as passed and markable, from about 1 AM, while the countdown counts up from Fajr (DS31).

Interaction:
- Tapping a passed or current prayer opens the mark-prayer sheet for today (§16).
- Sunrise, Imsak and upcoming rows don't respond to taps.

**Qaḍā line.** When the profile has missed prayers: "{n} outstanding Qaḍā", `body-sm` at 60%.

**Not built yet — the ribbon of §9.** The design calls for a vertical rule joining the marks, and a tick that travels down it between the current and the next prayer, like a sundial shadow. Android v1's "tick" is the static dot on the current row (DS6).

Never on this list: coloured left borders, cards per row, or row background fills.

**No circular *progress* rings. No concentric arcs. No pie charts. No graduated cardinal dials. Anywhere in the app.** The single Qibla frame ring (below) is the only ring permitted, and it carries no graduations or progress.

### Multi-profile pager (weather-app metaphor)

- There is one page per profile, in creation order (there's no reordering UI yet).
- There's no add page. A saffron **Add profile** FAB at the bottom right opens the profile sheet in place (§21).
- The FAB floats 56 dp above the bottom edge, on the side where the timeline's times are right-aligned, and nothing pads the timeline to clear it. On shorter phones it likely covers part of the Isha row's time. Not checked on a device (DS37).
- Dots sit under the pager, one per profile. The selected dot is 8 dp `saffron`; the others are 5 dp at 30% of the foreground, 6 dp apart.
- The whole screen takes the active page's phase surface. Paging between profiles in different phases cross-fades over 3 s.
- Paging never changes the structure; only the times, labels and surface change.
- Swiping is the only way to switch profiles. The April "tap reveals a card stack" switcher and its "[Profile: Home ▾]" chip were not built.

Which profile each surface uses:

| Surface | Profile |
|---|---|
| Home | Every profile (the pager) |
| Qibla, Tracker | The *default profile*: the GPS-flagged one, otherwise the first. v1 never sets the GPS flag, so in practice the first profile. |
| Notifications | The "Alerts for" profile (§15) |
| Widgets | Each widget's own profile (§25) |
| Live notification | The "Alerts for" profile (§22) |
| Watch | Every profile, mirrored from the phone. The watch opens on the "Alerts for" profile, and its complications and tile show that one (§7). |

### Ramadan on Home

- The Imsak row (above).
- A banner: an oxblood card (`#6B2E2A` at 90%, 12 dp corners) overlaid at the top of the screen, 8 dp down and 16 dp in from each side. It reads "Ramaḍān Mubārak" (`body`, parchment) with a "Dismiss" button (parchment at 70%).
- The banner shows while the active profile is in Ramadan, until it's dismissed. Dismissal is remembered per Hijri year.
- It covers the header line and the top of the hero (DS26).
- Nothing else changes during Ramadan.

### Qibla — typographic compass

**Replaces** the conventional compass-with-needle. A saffron arrow on a single quiet ring, set against the time-of-day gradient. The arrow is the figure; the ring is the frame.

```
┌──────────────────────────────────┐
│ ╭──────────────────────────────╮ │
│ │ Qibla                  DHUHR │ │  ← title panel
│ ╰──────────────────────────────╯ │
│        ╭ Turn right 12° ╮        │  ← hint pill
│                N                 │
│            ╭───────╮             │
│           │   ▲   │              │  ← rose: ring + saffron arrow
│            ╰───────╯             │
│ ╭──────────────────────────────╮ │
│ │             119°             │ │  ← readout panel
│ │    4,832 km to the Kaaba     │ │
│ ╰──────────────────────────────╯ │
│        ╭ • Qibla 119° ╮          │  ← chip panel
│ [ Hold phone flat and move … ]   │  ← only while accuracy < HIGH
└──────────────────────────────────┘
```

**Surface and panels**
- The surface is the time-of-day gradient for the default profile (§3).
- The instrument sits directly on the gradient. All text sits on **panels**: 16 dp-rounded rectangles filled `ink` on dark phases and `parchment` on light phases.
- Panel text is parchment or ink. Muted text is parchment at 65%, or `ink-muted`. The accent is `saffron` on dark panels and `saffron-ink` on light ones.
- On light phases the parchment panels nearly vanish into the top of the gradient (1.00–1.08:1). PR #21 D1 tracks this.

**Title panel.** Full width, with a 16 dp side margin. It holds "Qibla" (Qibla title style) and, in the accent colour, the current phase as an eyebrow ("DHUHR", or "JUMUAH" on a Friday afternoon, §20).

**Rose (280 dp).** It rotates by the negative of the device heading, on a spring (damping 0.8, stiffness 100), so its contents hold still in the world.
- **Ring:** a single circle, 28 dp stroke, 126 dp radius, in the panel colour. It is a frame: no graduations, no ticks, no progress.
- **Arrow:** 140 × 200 dp, saffron fill with a 1.5 dp outline (saffron at 85% on dark phases, saffron-ink at 85% on light). It is fixed inside the rose at the Qibla bearing, so it points at the Kaaba.
- **"N":** the North glyph with a 1 × 8 dp tick above it. It rides the ring at 12 o'clock and counter-rotates to stay upright. North is a whisper; there's no E, S or W.

**Alignment hint.** A panel pill above the rose.
- Text: "Turn right 12°", "Turn left 12°", or "— Aligned —", in Caption style.
- Colour: muted, fading to the accent over 300 ms when aligned.
- Aligned means within 5°, and it stays aligned until the drift passes 7° (hysteresis).
- A 50 ms haptic fires on becoming aligned.

**Readout panel**
- The Qibla bearing from true north, rounded ("119°"), in the Qibla degree style. It is the bearing for this location, not the live heading.
- Under it, "4,832 km to the Kaaba" in Readout-secondary style, muted, with locale digit grouping.

**Chip panel.** A 6 dp accent dot, then "Qibla" and "119°" in Caption style, in the accent colour.

**Calibration banner**
- Shown at the bottom while sensor accuracy is below HIGH.
- Text: "Hold phone flat and move in a figure-8 to calibrate", `body-sm`, `#FFF3CD` on `#7A5800` at 90%, 8 dp corners.

**Location and north**
- The bearing is computed from the device's current position when location permission is granted. The screen asks for fine location alone on first open (`QiblaScreen.kt:103`). Android's Precise/Approximate choice needs fine and coarse in one request, and some Android 12 releases ignore a fine-only request (DS33). Nothing on screen says when the bearing comes from the profile instead.
- Without permission, it uses the default profile's saved coordinates.
- Magnetic declination is applied to get true north. If the device's geomagnetic model has expired, declination falls back to 0°.

**Forbidden:** a cardinal N/E/S/W ring, concentric circles, tick marks, degree graduations, a 3D Kaaba, a needle. It reads as a letterpress print with a single ruled circle, not a cockpit.

### Countdown screen

Not built in Android v1; the Home hero is the countdown surface. The spec is kept for the watch and iOS:
- Time-until in Fraunces `display-xl`, for example `-00:12:35`. Format and direction follow §19; §4 Numerals applies.
- The prayer name below it in Fraunces `display-md`, with the prayer's own clock time.
- The adhan trigger time in IBM Plex `body-lg`, at the bottom.
- Time-of-day surface behind.

### Utilitarian screens (Settings, Notifications, Tracker, sheets)

**Surface and layout**
- A stable surface: `parchment` in system light mode, `ink` in dark. No time-of-day.
- Content is left-aligned with 24 dp side margins. The top is 16 dp (Tracker) or a top app bar (Notifications, Adhan voice). The April spec's 32 dp top margin is not used.
- Rows are at least 56 dp; nested rows (an expanded tracker day) 48 dp; two-line rows (Imsak) 64 dp. A 44 dp dense mode is allowed but not used.

**Text**
- Section headers are Fraunces `title`. Notifications sets its headers in capitals: "PRAYERS", "ADHAN", "OTHER".
- Screen headers such as "Profiles" are `display-md`.
- Row labels are IBM Plex Sans `body`. Row values are `body-sm` in `ink-muted`, before a chevron.

**Dividers**
- Notifications and sheets: `parchment-muted`, 0.5 dp, 24 dp inset.
- Settings: Material's default divider (`outlineVariant`).

**Top app bar.** A back arrow and the title in `body`, on the background colour.

**Selection**
- In list pickers (Vibration, Offset, Early reminder) the current value's text turns saffron.
- The profile picker adds a saffron check.
- Adhan voice and alert-time mode use radio marks: a 1.5 dp stroke circle with a saffron centre.
- Saffron *text* on parchment fails AA, so it moves to `saffron-ink` (DS2).

**Controls**
- Primary button: filled `saffron` with an `ink` label.
- Secondary button: outlined, with an `ink-muted` outline. Android v1 gets Material's default border instead, `outlineVariant` (`parchment-muted`, 1.37:1 on parchment), so the unselected Asr school and Hijri buttons are nearly borderless.
- Destructive button: outlined in Material's error red (DS19).
- Switch: `saffron` track when on, `parchment-muted` track when off, `parchment` thumb.
- Segmented choices (Asr school, Hijri adjustment): a row of equal-width buttons. The selected one is filled saffron with an ink label; the others are outlined.

**Sheets.** Material `ModalBottomSheet` with its default 28 dp top corners and drag handle, on `surfaceContainerLow`: parchment, or ink in dark mode.

---

## 6. Iconography

- Stroke icons, 1.5pt weight. Consistent terminals (flat cut).
- No filled icons in nav. Filled reserved for active-state tabs only.
- **Forbidden:** mosque-dome silhouettes, crescent-and-star cliché, gold ornaments, stock 99-names calligraphy wallpaper.
- Custom glyphs for: Qibla arrow, prayer-name initials (watch), tasbeeh bead, Kaaba abstract mark.
- Prefer platform symbol libraries where neutral (SF Symbols on iOS, Material Symbols on Android) for utilitarian screens. Swap to custom for contemplative screens.

**Android v1:**
- **Nav bar:** filled Material Icons in both states: Home, Explore, DateRange, Settings (DS21).
- **Utilitarian chrome:** Compose Material Icons: Add, Delete, LocationOn, Place, Notifications, KeyboardArrowRight (chevron), ArrowBack, Check, PlayCircleOutline.
- **Custom-drawn:** the Qibla arrow, the "N" glyph and its tick, the ribbon's ✓ and current-prayer dot, the tracker's status squares, the radio marks.
- **Placeholders still in place:**
  - The empty-state Kaaba mark is the 🕋 emoji, which renders in the system colour-emoji font — a black cube with a gold band. That's gold ornament, and it isn't the custom mark this section asks for (DS17).
  - The notification icon is a crescent (DS22, PR #14 V1).
  - There is no launcher icon, so Android shows its generic default (DS8).
- **Still to design:** the Kaaba abstract mark, the launcher icon, the notification icon, the watch prayer initials, the tasbeeh bead.

---

## 7. Watch Scale (watchOS + WearOS)

**Status:** WearOS (v2) is in progress on `agent-main`: the watch app, three complication families and a tile are built, and the phone mirrors its profiles to the watch over the Wear Data Layer. watchOS (v3) hasn't started.

**Designed first, not last.** Watch is the primary surface, not an afterthought.

### Token reduction

At watch scale, only three color tokens exist:
- `ink`
- `parchment`
- `saffron`

No time-of-day cycle on watch. OLED inversion instead: watch renders solid `ink` field with `parchment` text. Saves power, maximizes contrast, glanceable in sunlight.

### Complication spec (40px–60px)

Minimum viable complication:

```
● 04:21 F
```

- Saffron dot (prayer is active or next)
- 5-char tabular countdown or prayer time
- Single-letter prayer initial (F=Fajr, S=Sunrise, D=Dhuhr, A=Asr, M=Maghrib, I=Isha)

Three state variants:
- **Active:** saffron dot + saffron time + saffron letter (current prayer window open)
- **Upcoming:** saffron dot + ink time + ink letter (next prayer, default state)
- **Passed:** hidden; system moves to next prayer

Fraunces ball-terminal numerals carry the identity even at 12pt. IBM Plex Sans tabular numerals used when space is tighter.

> **WearOS as built.** The watch app bundles no fonts: its screens and tile render in the system font (DS36), and complication text is drawn by the watch face. If Fraunces comes to the watch, settle DS9 first: the bundled build has proportional figures and no `tnum` feature (§4).

### Tile / watch-face scale (80px–100px)

Adds:
- Prayer name spelled out (`Fajr` not `F`) in Fraunces `body` size
- Profile name above it, so a two-profile user knows which city they are looking at
- Sub-line: the prayer's own clock time — `at 13:00`, or `began 13:00` once it is under way

**The tile carries no countdown.** A tile is a static layout rebuilt on an interval; it has no
equivalent of the widget's `Chronometer` or the complication's `TimeDifferenceComplicationText`.
A countdown there would be frozen at whatever it was when the tile was last built — right for a
second and quietly wrong afterwards, which is worse than not showing one. The tile answers
*when*; the complication and the app answer *how long*.

### WearOS complication families

WearOS offers a data source a fixed set of families. aynama supports three and declines the
rest — a decision, not an omission.

**Supported**

| Family | Content |
|---|---|
| `SHORT_TEXT` | System-ticked countdown as the text, prayer initial as the title. The 40–60px spec above. |
| `LONG_TEXT` | Same countdown, prayer name spelled out as the title. The tile/watch-face scale above. |
| `MONOCHROMATIC_IMAGE` | The app mark only — a single filled dot. No room for prayer state, and inventing a glyph per prayer would produce five shapes nobody can tell apart at 40px. |

**Declined**

- `RANGED_VALUE`, `GOAL_PROGRESS` — watch faces render these as **circular progress arcs**.
  §10 forbids circular progress rings anywhere in this app and §9 names their absence as one of
  the two deliberate differentiators. The arc would be drawn by the watch face rather than by
  us, but the user would still be looking at a ring, so the rule holds.
- `WEIGHTED_ELEMENTS`, `SMALL_IMAGE`, `PHOTO_IMAGE` — prayer state is a name and a time. There
  is no honest image of it, and a decorative one would be §10's "Arabic calligraphy as
  ornament" wearing a different hat.

The countdown text is a `TimeDifferenceComplicationText`, so the system ticks it between
refreshes. Its format is the platform's — "2h 18m", no sign, no seconds — the same constraint
§19 records for widgets and the live notification. The prayer name beside it carries the
direction.

### Rules

- No gradients on watch. Solid surfaces only.
- No glyphs smaller than 10pt.
- No thin hairlines (< 1pt) — they disappear on OLED.
- Tap targets minimum 44×44pt on watchOS, 48×48dp on WearOS.

---

## 8. Motion

| Motion | Rule | Android v1 |
|---|---|---|
| Surface cycle | Steps at each prayer boundary with a 3 s cross-fade. Paging to a profile in another phase also cross-fades. | ✓ |
| Prayer-time transitions | 400 ms ease-out cross-fade of the hero and timeline. Never a hard cut. | ✗ The text swaps with no fade; only the surface fades (DS28). |
| Countdown tick | Every second, because the countdown shows seconds (§19). No rolling digits. | ✓ |
| Widget and live-notification countdowns | The system ticks them every second in its own format; §19's platform exception says what that allows. | ✓ |
| Qibla rose | Spring physics (damping 0.8, stiffness 100). The raw heading is low-pass filtered, so there's no magnetic-needle jitter. | ✓ |
| Qibla hint colour | 300 ms tween. | ✓ |
| Page transitions | Platform defaults: iOS native push; on Android, Navigation Compose with Material predictive back. No custom transitions. | ✓ Navigation Compose defaults, no custom transitions. Predictive back is not declared in the manifest and is untested. |
| Sheets | Material default slide. | ✓ |
| Reduced motion | See §12. | ✗ No app-level handling (DS28). |

**Forbidden:** bouncing easing, parallax scrolling, particle effects, glow pulses on CTAs, "shimmer" loading states.

---

## 9. Two Deliberate Departures

These are the things no prayer app does. They are the product.

1. **Typographic Qibla** — saffron arrow on a single quiet ring, framed by a Fraunces degree readout. Not a compass-with-needle, not a cardinal-ring legend, not a tick dial. The single ring frames the arrow; it is not graduated, not a progress arc, and carries no cardinal labels except a single whispered "N". §5.
2. **Prayer timeline ribbon** — vertical line with moving sundial tick, not a circular countdown ring. §5.

If a future surface proposal reintroduces a circular countdown ring, a graduated cardinal dial (N/E/S/W ring with tick marks), or a 3D Kaaba render, reject it at design review. These are the two non-negotiable differentiators.

> **Android v1 status.**
> - Departure 1 has shipped, with the addition of the Qibla panels (§5).
> - Departure 2 is only half built. The timeline and its passed, current and upcoming states exist; the vertical line and the moving tick do not (DS6).
>
> Until DS6 is built, or this section is formally amended under §14, the moving tick remains required.

---

## 10. Anti-Slop (forbidden)

Hard rules. A PR that violates any of these gets blocked at review.

- **No circular progress rings anywhere.** Not countdown, not tasbeeh, not habit tracker, not Zakat progress, not onboarding. Use linear ribbons, depleting bars, or typographic countdowns instead. *(Exception: the single quiet frame ring around the Qibla arrow per §5. It carries no progress, no graduations, no cardinal labels — it is a frame, not a meter.)*
- **No mosque-green, gold-on-green, emerald, teal, purple, or indigo** anywhere in the palette, even as "just a hint."
- **No dome silhouettes, minaret icons, or crescent-and-star** decorative motifs.
- **No Arabic calligraphy used decoratively** as background texture, splash art, wallpaper, or ornament. Arabic script in this app is *content*. If it's on screen, it says something specific and is legible.
- **No stock 99 Names wallpapers.**
- **No gradients on utilitarian screens.** No gradients on watch.
- **No purely black or purely white surfaces.** Always warm ink, always linen parchment.
- **No system-default sans-serif as a shipped choice** (no Inter, Roboto, Arial, Helvetica as final body).
- **No bouncing animations, particle effects, glow pulses, or shimmer loaders.**

**Android v1 breaches:**
- Roboto renders through unset type slots (DS5), and the watch app uses the system font throughout (DS36).
- The crescent notification icon is borderline (DS22).

Everything else on this list holds.

---

## 11. Platform Notes

### iOS
- Liquid Glass surfaces allowed on utilitarian screens (Settings modals, share sheets). Do not apply Liquid Glass over contemplative time-of-day surfaces — the effect clashes.
- SF Symbols on utilitarian chrome. Custom SVGs on contemplative screens.
- Dynamic Type supported through `display-xl` down to `body-sm`.

### Android
- **Colour.** Material You colour extraction is **disabled**; the design IS the palette. Every Material role except `error` is mapped from the tokens (§3).
- **Icons.** §6 prefers Material Symbols on utilitarian chrome. Android v1 uses the Compose Material Icons set.
- **Edge-to-edge** by default. Status bar matches current surface. Android v1 enables edge-to-edge, but the scaffold pads content below the status bar, so the time-of-day surface doesn't reach behind it and the status-bar strip shows the theme background instead (DS18).
- **Form factor.** Portrait-only phone layout. minSdk 26, targetSdk 36.
- **Dark mode.** Utilitarian screens follow the system theme and switch to `ink`. Contemplative screens ignore it, because the phase decides. Widgets ignore it (§25).
- **RTL.** `supportsRtl` is on, but no RTL locale ships yet.

### watchOS
- Complication families: `.circularSmall`, `.graphicCircular`, `.graphicRectangular`, `.graphicCorner`. Spec each per §7.
- Always-On state: same render as active, OLED-safe ink field.

### WearOS
- Tile API for glanceable card. Complication slots per Wear complications spec.
- Color theme locked — does not adopt system theme.
- As built on `agent-main`: the three watch tokens (`wear/WearTokens.kt`) on a solid ink field. The watch computes its own prayer times from the profiles the phone publishes.

---

## 12. Accessibility

| Requirement | Android v1 |
|---|---|
| WCAG AA on all text (§3) | Partial. Saffron text on light surfaces (DS2), the Home gradient (DS3), and dark-mode colours (DS4) fail. |
| RTL mirror; Arabic never in Latin typography | Declared, untested. No Arabic locale yet. |
| Dyslexia-friendly option (OpenDyslexic body) | Not built. |
| Reduced motion: surface holds per phase; transitions shorten to a 150 ms cross-fade | Not built (DS28). The surface already holds per phase by design. |
| TalkBack labels on every touch target | Mostly. Gaps: notification switches don't name their prayer (PR #16 A5), the tracker column letters (PR #13 A1), the calibration banner isn't announced (DS25). |
| Minimum tap target 48 × 48 dp | ✗ on Home: the timeline rows are about 25 dp tall (DS29). Utilitarian rows are 56 dp. |
| Text scales with system font size | Mostly. Nav labels are capped at 1.3× (PR #12 A6), and the fixed-height two-line Imsak row is likely to clip at large font scales (§15). |

**TalkBack strings as shipped:**

| Element | Announcement |
|---|---|
| Home countdown | "Asr in 02:18:07"; while counting up, "Dhuhr began 00:15:42 ago" |
| Home page | "Profile page 1 of 3: London" |
| Timeline row | "Fajr 5:12 AM, passed" (or "current", "upcoming"); "Sunrise 6:48 AM"; "Imsak 5:02 AM" |
| Add profile FAB | "Add profile" |
| Page dots | "Page 1", "Page 2", … (DS25) |
| Qibla rose (polite live region, re-announced every 15° of heading) | "Facing northeast. Qibla is to the southeast — turn right" |
| Tracker today row | "Mark Fajr prayer", or "Asr prayer, not due yet" |
| Tracker day row | "Mon, Sep 22, 3 of 5 prayers" |
| Outstanding count | "3 prayers outstanding" |
| Notification row chevron | "Fajr notification settings" |
| Adhan preview button | "Preview Makkah adhan" |
| 4×2 widget time | "Asr, 4:14 PM, current" |

The April target — prayer times announced with full context, as in "Dhuhr at 12:47, in 2 hours 18 minutes" — is not met yet. The countdown is read as its digits, with words for the direction.

Minimum tap target on the other platforms: 44 × 44 pt (iOS), 44 × 44 pt (watchOS), 48 × 48 dp (WearOS).

---

## 13. Tokens — JSON Schema

Design tokens ship as shared JSON (per repo architecture). Four native projects consume the same token file.

```json
{
  "color": {
    "ink": "#1C1A17",
    "ink-muted": "#6B6560",
    "parchment": "#F2EAD8",
    "parchment-muted": "#D4C9B1",
    "saffron": "#B87A2E",
    "saffron-ink": "#8A5A22",
    "surface": {
      "fajr": ["#1C1A17", "#3A3530"],
      "sunrise": ["#EDE1C5", "#E8C89A"],
      "dhuhr": ["#F2EAD8", "#EDE1C5"],
      "asr": ["#E8C89A", "#B87A2E"],
      "maghrib": ["#6B2E2A", "#1C1A17"],
      "isha": ["#0F1419", "#1C1A17"]
    }
  },
  "font": {
    "latin-display": "Fraunces",
    "latin-body": "IBM Plex Sans",
    "arabic-ui": "IBM Plex Sans Arabic",
    "quran-uthmani": "KFGQPC Uthman Taha Naskh",
    "quran-naskh": "Amiri Quran"
  },
  "radius": { "sm": 6, "md": 12, "lg": 20, "pill": 9999 },
  "spacing": { "xs": 4, "sm": 8, "md": 16, "lg": 24, "xl": 32, "xxl": 48 }
}
```

This revision adds the `sunrise` surface, which was already in §3 but missing here.

Path: `shared/design-tokens.json` — **still to be created.** Until it exists, Android keeps its own copies:

| File | Holds |
|---|---|
| `ui/theme/AynamaTheme.kt` | Colour tokens and the Material role mapping |
| `home/GradientColors.kt` | Surface gradients |
| `res/values/colors.xml` | Widget colours (no `saffron-ink`) |
| `ui/theme/AynamaTypography.kt` | Type scale |
| `wear/WearTokens.kt` | The watch's three colours |

Android v1 doesn't follow the scales above:
- **Radii:** 8 dp (widgets, calibration banner), 12 dp (Ramadan banner, Material's card default), 16 dp (Qibla panels, hint pill), 28 dp (sheet top, Material's default). Only 12 is on the scale.
- **Spacing:** 12 dp is used throughout but isn't on the scale.

Resolve both when the shared file is created.

---

## 14. Change Process

This file governs UI decisions. Changes require:

1. A proposal in PR description: what, why, which principle it touches.
2. Verification that existing surfaces still pass (no silent regressions).
3. Update to this file FIRST, then code that follows.

Small tweaks (adjusting a spacing token, adding a new icon) follow this process lightly. Changes to color, typography, composition (§3, §4, §5), or the Two Deliberate Departures (§9) require explicit discussion before merge.

**Re-baselining.** When shipped code has drifted from this file, record each drift in the same PR, either as a spec change (with the reason) or as a §27 gap with a finding. The September 2026 Android sync did this. Its spec changes, to §3, §4, §5, §8, §15–§18 and §25, still need the explicit sign-off that §14 requires. TODOS.md holds the one list to sign off.

---

## 15. Notification Settings Screen — UX Spec

### Overview

Notifications is a utilitarian screen: stable parchment surface, no time-of-day cycle. It controls per-prayer alerts and the adhan audio selection. The screen's job is to make it fast to turn prayers on/off and to pick a voice without any friction.

**Adhan model:** one global voice picker. No per-prayer adhan override — one fewer thing to explain.

**Offset model:** hidden in per-prayer detail, not on the main list. Main list stays scannable.

**Scope:** alarms are scheduled for **one profile at a time**, the "Alerts for" profile. Other profiles get no alarms. The live notification (§22) follows the same profile. Widgets refresh independently for whichever profiles they show (§25).

### Information Architecture

```
Notifications                       ← top app bar: back arrow + "Notifications" (body)
├── [Master toggle row]             Prayer alerts          [toggle]
└── (only when master is on)
    ├── [Profile row]               Alerts for      London  ›
    ├── PRAYERS
    │   ├── Fajr      04:21  [toggle]  [›]
    │   ├── Dhuhr     12:47  [toggle]  [›]
    │   ├── Asr       15:33  [toggle]  [›]
    │   ├── Maghrib   18:11  [toggle]  [›]
    │   └── Isha      19:42  [toggle]  [›]
    ├── ADHAN
    │   └── Adhan voice       [Makkah  ›]
    └── OTHER
        ├── Live countdown     [toggle]
        ├── Ramadan Imsak      [toggle]
        └── Vibration          [With sound  ›]
```

### Profile Row

- Appears immediately below the master toggle, and only when the master toggle is on.
- Picks which profile the alarms are scoped to. The label "Alerts for" is deliberate: it says the whole alert system applies to exactly one profile at a time.
- Row (56 dp): "Alerts for" (`body`, `ink`), then the profile name (`body-sm`, `ink-muted`), then a chevron (`ink-muted`).
- The default is the first profile by creation order. Unlike Qibla and Tracker, it doesn't prefer a GPS-flagged profile (PR #16 A9).

Tapping the row opens the **Profile Picker Sheet**, a `ModalBottomSheet`:
- Title "Profile" in `title`, with 24 dp side padding and 8 dp vertical padding.
- One 56 dp row per profile, name in `body`. The selected profile shows a saffron check on the right, and its name is saffron too; the name should drop the saffron (PR #17 A5, DS2).
- Dividers: `parchment-muted`, 0.5 dp, 24 dp inset.
- Selection is immediate: the sheet animates closed, then applies the choice.

**Profile-scoped settings:** per-prayer toggles, offsets, early reminders, alert modes, and fixed times are all stored independently per profile. Switching profiles loads that profile's saved settings. Global settings (master toggle, adhan voice, vibration, Imsak toggle) are NOT profile-scoped — they apply regardless of which profile is selected.

### Master Toggle Row

- Label: IBM Plex `body`, ink — "Prayer alerts". Row height 56 dp.
- Toggle on the right: saffron track when on, parchment-muted track and border when off, parchment thumb.
- **When OS notification permission is denied:** the toggle is replaced by the saffron text button "Enable in Settings →", which opens the app's system settings page. No toggle is shown in this state. The text should move to `saffron-ink` (DS2).
- **When master is off:** every section below is hidden, including the profile row. Per-prayer settings persist and return unchanged when master is turned back on. This replaces the April wording that kept rows visible in `ink-muted`; the IA above always showed them hidden.

No "all-off" guilt banner. It's the user's phone.

### Prayer Toggle Rows

**Row anatomy** (56 dp):

```
[prayer name]    [alert time]    [toggle]  [›]
```

- **Prayer name:** IBM Plex `body`, `ink`. Always the canonical name: Dhuhr stays "Dhuhr" on Fridays, because the row governs every day (§20).
- **Time:** `mono-num`, `ink-muted`. This is the **alert** time — the offset or fixed time applied — not the calculated prayer time. It's always 24-hour ("04:21"), whatever the device setting (DS13). It's computed when the screen opens and isn't refreshed after an edit, so the row and the detail-sheet header keep the old time until the screen is reopened (DS35).
- **Toggle:** saffron track when on, parchment-muted when off, parchment thumb. Tapping the toggle turns the alert on or off.
- **Chevron:** `ink-muted`, labelled "{Prayer} notification settings".
- Tapping anywhere else on the row opens the per-prayer detail sheet. With permission denied, both the row and the toggle open system settings instead.

**Dividers:** `parchment-muted`, 0.5 dp, 24 dp left inset.

**Section headers:** "PRAYERS", "ADHAN", "OTHER" in Fraunces `title`, in capitals, `ink`, at the 24 dp margin, with 12 dp above and 8 dp below.

### Adhan Section

Single row, height 56 dp:

```
Adhan voice                               Makkah  ›
```

- **Label:** IBM Plex `body`, `ink`
- **Current selection:** IBM Plex `body-sm`, `ink-muted`, right-aligned before chevron

Tapping the row navigates to the Adhan Picker screen (full navigation push, not a sheet — the list is long enough to warrant its own screen).

### Adhan Picker Screen

A top app bar with the title "Adhan voice" in `body`.

**Options** (radio select, single choice):

| Label | Caption |
|---|---|
| Makkah | Al-Masjid Al-Haram |
| Madinah | Al-Masjid An-Nabawi |
| Egyptian | Abdul Basit Abdus Samad |
| Turkish | Diyanet İşleri |
| Al-Aqsa | |
| None | Silent — no audio |

**Row anatomy** (56 dp):
- **Left:** a 20 dp radio mark. The stroke circle is 1.5 dp `ink`; when selected, the centre is a `saffron` disc at half the radius. The stroke must switch to `parchment` in dark mode (DS4).
- **Centre:** the label in IBM Plex `body`, with an optional caption in `body-sm`, `ink-muted`, below.
- **Right:** a play icon (SF Symbols `play.circle` on iOS; `PlayCircleOutline` on Android), `ink-muted`, 24 dp, labelled "Preview {voice} adhan".
  - The design: tapping it plays a 10-second preview, and only one preview plays at a time.
  - **Android v1:** no adhan audio ships yet, so it shows a "Adhan assets coming soon" toast (PR #16 A6).

Selection is immediate. No "Save" or "Apply" button. Nav-back confirms.

### Other Section

**Live countdown row** (64 dp, §22):
- "Live countdown" in `body`, with the caption "Keep the current prayer and its countdown in the shade" in `body-sm`, `ink-muted`, and a switch.
- Off by default. Like every row below the master toggle, it's hidden while master is off, and master off also removes the notification.
- The caption is hard-coded `InkMuted`, 3.02:1 in dark mode (DS4).

**Ramadan Imsak row** (64 dp):
- Label: IBM Plex `body`, `ink` — "Ramadan Imsak"
- Caption: IBM Plex `body-sm`, `ink-muted` — "10 minutes before Fajr, during Ramadan"
- Toggle on right, saffron when enabled
- During Hijri Ramadan the row background shifts to `parchment-muted` (#D4C9B1) — a quiet signal the feature is seasonally active. Not saffron, not a badge. Just a tint.
- Known issues:
  - On the tint, the switch's parchment-muted off-track disappears (PR #16 A10).
  - In dark mode, parchment text on the tint is 1.37:1 (DS4).

**Vibration row** (56 dp):
- "Vibration" in `body`. The current value — "Always", "With sound" or "Never" — sits right-aligned in `body-sm`, `ink-muted`, with a chevron.
- Tapping it opens a bottom sheet titled "Vibration" with three 56 dp rows. The current value's text is saffron; there's no check.
- The default is "With sound", which vibrates unless the voice is None.

### Per-Prayer Detail Sheet

A `ModalBottomSheet` that **opens fully expanded**. The April spec said about 60% height; Android v1 skips the half-height state. On iOS, use `UISheetPresentationController` at the `.medium` detent, expandable to `.large`.

**Header** (not a row):
- The prayer name in Fraunces `display-md`, centred. It is currently hard-coded to `ink`, which is invisible in dark mode (DS4).
- Below it, "Today · {alert time}" in `body-sm`, `ink-muted`, 24-hour.

**Rows:**

| Row | Label | Control |
|---|---|---|
| Alert | "Send alert" | Toggle (saffron on) |
| Alert time — Offset | "Offset" | Radio + current value `›` |
| Alert time — Fixed | "Fixed time" | Radio + current value `›` |
| Early reminder | "Early reminder" | Current value `›` |
| Preview | — | Saffron text button |

**Alert time mode** (two rows, visually grouped). The user picks exactly one of two modes.
- The rows sit back to back under the label "ALERT TIME" (`ink-muted`, set in Material's `labelSmall`, which falls back to Roboto — DS5), with no divider between them.
- Each row starts with a 20 dp radio mark: a 1.5 dp stroke, with a centre dot at 35% of the radius when active. It's `saffron` when active and `ink-muted` when not, and the chevron takes the same colour.
- The active row's label is `ink`; the inactive row's label is `ink-muted`. The value is `ink-muted` in both rows (PR #17 N2).
- The mode switches only when a picker is confirmed. Cancelling leaves it unchanged.

The two modes:
- **Offset (default).** The value reads "On time", "−5 min" or "+10 min". Tapping opens the **Time offset** sheet: a list of −15, −10, −5, On time, +5, +10, +15, with the current value in saffron text. Choosing a value also switches the mode to Offset.
- **Fixed time.** The value reads as a clock time such as "6:00 AM", or "Not set". It's always 12-hour today (PR #17 N6, DS13).
  - Tapping opens a Material 3 `TimePicker` in a dialog, with "OK" in saffron and "Cancel" in `ink-muted`. The picker itself follows the device's 12/24-hour setting.
  - Confirming saves the time and switches to Fixed mode.
  - The fixed time sets when the alarm fires and the time shown in the notification settings row.
  - The Home timeline always shows the calculated time.

**Early reminder.** The value is "Off", "5 min before", "10 min before" or "15 min before" (default Off), picked from a list sheet. When set, a separate notification fires that many minutes before the alert time: "Fajr in 10 minutes". It has no adhan, and vibrates according to the global vibration setting.

**Preview row.** "Preview adhan" in `body`, saffron, 56 dp. It should play a 10-second sample of the chosen voice; Android v1 shows the "coming soon" toast instead.

### Alerts as delivered (Android v1)

**Channels**
- "Prayer Times" (`prayer_times_v2`): high importance, and silent at the channel level. The app vibrates itself per the vibration setting, with a 400–200–400 ms pattern.
- "Live prayer countdown": minimum importance, silent, no badge (§22).
- "Adhan": low importance, for the playback service.

**Prayer alert**
- The title is the prayer name; the text is "It is time for {prayer} prayer". Friday's Dhuhr is named Jumuah (§20).
- The small icon is the crescent placeholder (DS22).
- Tapping the notification does nothing: it has no content intent, so auto-cancel never fires (DS14).

**Adhan playback**
- Unless the voice is None, a foreground service starts with an ongoing "Adhan · Playing…" notification.
- It plays the system default notification sound as a placeholder — no adhan audio is bundled yet — and stops after 30 s.
- The notification has no stop action (DS14).

**Imsak** (during Ramadan, if enabled)
- Uses the prayer template, so it reads "It is time for Imsak prayer".
- Starts the adhan service too (DS14).

**Live notification** (§22, opt-in)
- Ongoing and silent. The title is the prayer, the text "At 1:00 PM" or "Began at 1:00 PM", and the sub-text the profile.
- The notification's own chronometer counts down to the prayer, then up from it.
- Tapping it opens Home on that profile. Its times are always 12-hour (DS13).

**Permissions**
- The notification permission is requested on first launch (Android 13+).
- Right after it's granted, the app asks once to be exempted from battery optimisation. That only happens on a fresh grant on Android 13+: Android 8–12, and users who had already allowed notifications, are never asked (PR #34 A3).

### States

| State | Behavior |
|---|---|
| OS permission denied | The master toggle is replaced by the saffron link "Enable in Settings →". If master is on, the rows show; tapping a row or toggle opens system settings. |
| Master toggle off | Everything below the master row is hidden. Per-prayer settings are preserved and return when master is turned back on. |
| All individual prayers off | No summary state. User sees each prayer's toggle in its off state. Not our job to add a guilt banner. |
| Ramadan (Hijri calendar) | Imsak row gains `parchment-muted` background tint. No other visual change. |
| "Alerts for" profile has no computable times (polar day or night) | The PRAYERS section is empty, the profile row shows "—", and the profile picker opens **empty**, so the user can't switch away from this screen (DS16). Switching to such a profile from inside the screen instead leaves the previous profile's rows showing while edits go to the new one. |

### Accessibility

- **Toggles:** the design is for TalkBack to read full context, as in "Fajr prayer alert, on". Android v1's switches have no label and read "Switch, on" (PR #16 A5).
- **Row disclosure:** the chevron reads "Fajr notification settings". ✓
- **Adhan preview:** "Preview {voice name} adhan, button". ✓
- **Time offset picker:** should announce "Time offset, minus 5 minutes". Android v1 reads the visible text.
- **Tap targets:** every interactive element at least 44 × 44 pt on iOS and 48 × 48 dp on Android. Android v1: 56 dp rows; switches and icon buttons at least 48 dp. ✓
- **Reduced motion:** toggle state changes are instant colour shifts only (no spring or bounce), and sheets fade rather than slide. Not built (DS28).
- **Dynamic Type / large fonts:** rows grow vertically to fit larger text, and times stay tabular with no wrapping. Android v1 rows have fixed heights (56 and 64 dp), so the two-line Imsak row is likely to clip at large font scales.

---

## 16. Prayer History View

**Surface:** utilitarian — stable parchment (light) or ink (dark), no time-of-day cycle. Left-aligned with 24 dp side margins and a 16 dp top.

**Profile:** the default profile (§5). There's no profile switcher on this screen. Home's mark sheet saves under whichever profile page is showing, so marks made on any other page never reach the Tracker (DS34).

**Five prayers tracked per day:** Fajr, Dhuhr, Asr, Maghrib, Isha. Sunrise is a time marker on the home ribbon, not a tracked prayer. Do not include a Sunrise indicator in history rows.

### Marking flow

**Primary (same day):** tap a passed or current prayer on the Home timeline (§5). A bottom sheet opens in place; there's no navigation away from Home. From Home the sheet opens with nothing pre-selected, even if the prayer was already marked (PR #13 M5). The mark is saved under the device's date as it was when Home was first shown, not the profile's (DS12, PR #13 M1).

**Today on this screen:** tap a row in the "Today" section. The rows are the profile's day, but the mark is saved under the device's date, as from Home (DS12).

**Retroactive (past days — qaḍā and corrections):** tap a day row to expand it inline, then tap one of its prayer rows. The same sheet opens.

**The sheet:**
- The prayer name in `display-md`, centred, named for its day (Jumuah on a Friday, §20). Under it, "Today", "Yesterday" or "Mon, Sep 22" in `body-sm`, `ink-muted`, judged against the device's date.
- Three 56 dp options, each a status square followed by its label in `body-lg`. The current status's label is saffron (DS2).
- Choosing an option saves it and closes the sheet.

**Three prayer states:**

| State | Label | Indicator |
|---|---|---|
| Prayed on time | "I prayed this" | Filled `saffron` square |
| Prayed as Qaḍā | "I prayed this later (Qada)" | Filled `ink-muted` square |
| Missed | "I didn't pray this" | Empty square, 1 dp `parchment-muted` stroke |

Two gaps in how the states behave (DS15):
- **"On time" has no time limit.** The design allows "Prayed on time" only within the prayer's window, and greys it out for past days. Android v1 offers all three options for any date.
- **Missed and unmarked look the same.** A missed prayer and a prayer nobody has marked both show an empty square. So does the dormant `INTENTION_TO_MAKEUP` status (PR #13 M4/M7).

Nothing is ever marked automatically. The repository's `autoMarkMissed` isn't wired to anything.

**Where Qaḍā appears in history:** on the day the prayer was *missed*, not the day it was made up. Fajr skipped Monday and made up Tuesday shows on Monday's row as Qaḍā. When it was marked is stored (`updatedAt`) but not shown.

**Copy rules:** Never use "Mark complete" or "Check in." Use: "I prayed this" / "I prayed this later (Qada)" / "I didn't pray this." Calm and direct. No exclamation marks, no encouragement copy.

### Today section

- The header "Today" in Fraunces `title`.
- Five 56 dp rows: a 12 dp status square, the prayer name in `body-lg`, and the scheduled time in `body` (IBM Plex Sans, tabular), `ink-muted`, right-aligned.
- Prayers whose time hasn't come yet are shown at 40% opacity and can't be tapped. TalkBack reads them as "Asr prayer, not due yet".
- Below the rows, "{n} prayers outstanding" in `body-sm`, `ink-muted`, when there are any. It counts every missed prayer ever recorded for the profile, not just this week's.

### History list structure

**Window:** the current week plus the three before it, up to yesterday. Weeks start on Monday, newest first. Today appears only in the Today section, never as a history row. Older data stays in the database but can't be shown yet (PR #13 D1).

**Column header** (rendered once at the top of the history, not per section):

```
                       F   D   A   M   I
```

Single-letter prayer initials (F=Fajr, D=Dhuhr, A=Asr, M=Maghrib, I=Isha), aligned over the squares, in the Column-initial style (IBM Plex Sans 700, 12 pt), `ink-muted`. Same convention as the watch complication initials (§7).

**Week headers:** Fraunces `title` — "This week", "Last week", then date ranges such as "Sep 1–7".

**Week summary:** under "This week" only, one descriptive line in `body-sm`, `ink-muted`:

```
12 of 17 prayers on time this week
```

It counts only prayers marked on time, out of every prayer due so far this week, including today's due prayers. It's not a streak counter and has no reset mechanic.

**Day row** (56 dp):

```
Mon, Sep 22            ▪  ▪  ▫  ▪  ▪   4/5
Sun, Sep 21            ▪  ▪  ▪  ▪  ▪   5/5
```

- **Left:** the date, "Mon, Sep 22", in `body`, `ink`, in a 130 dp column.
- **Centre:** five 15 dp squares, 4 dp apart, in prayer order. The April spec said 8 dp squares with 5 dp gaps.
- **Right:** the count in `body-sm`, `ink-muted`, right-aligned in 28 dp. It counts on-time plus qaḍā ("4/5").

Squares, not circles. That keeps clear of circular-ring territory (§10) and reads as a printed ledger.

`DayRow` still has a branch that turns today's label saffron, but today never reaches the history list, so it never runs (DS27).

**Expanded day row** (tap the row to reveal): one 48 dp row per prayer, indented 16 dp. Each has a 10 dp status square, the name in `body`, and the scheduled time in `body-sm` (IBM Plex Sans, tabular), `ink-muted`. Tapping a row opens the mark sheet for that date.

### Hard rules

- No calendar grid or monthly heat-map. No gradients (§10).
- No streak counter as primary or secondary hero.
- No circular progress indicators (§10).
- No colored left-border on any row — including today (consistent with prayer ribbon list treatment).
- No cards per row — flat list only.
- No gamification copy. No "Great job!", "Keep it up!", or streak-reset warnings.
- Sunrise is not a tracked prayer. Five indicators per row, not six.

---

## 17. Settings & Profile Sheet

### Settings screen

```
┌──────────────────────────────────┐
│ 🔔  Notifications              › │  ← 56 dp entry row
│ ──────────────────────────────── │
│ Profiles                         │  ← display-md
│ London                           │  ← title
│ 51.5074, -0.1278 ·               │  ← body-sm, ink-muted
│ Muslim World League              │
│ ──────────────────────────────── │
│ London (Ḥanafī)                  │
│ …                                │
│                              (+) │  ← FAB: saffron, ink "+"
└──────────────────────────────────┘
```

- **Notifications row.** A bell icon, "Notifications" in `body`, and a chevron. It opens §15. It sits above Profiles; PR #16 A4 questions that order.
- **Profile rows.** The name in `title`. Under it, the coordinates to four decimal places and the method's full name, in `body-sm`, `ink-muted`. The city name isn't stored, so it can't be shown (PR #15 A1).
- **Actions.** Tapping a row opens the edit sheet. Swiping it right-to-left deletes it immediately over Material's error-container red, with no confirmation (PR #15 M10).
- **FAB.** The saffron button with an ink "+" opens the create sheet: the same `ProfileFormSheet` that Home's FAB and empty-state button open (§21).

### Profile sheet (create and edit)

A `ModalBottomSheet` that opens **fully expanded**. The fields scroll, and Save and Delete stay pinned below them. It replaced the architecture plan's original full-screen flow (§21).

```
New profile                                      ← display-md ("Edit profile" when editing)
[ Name                                     ]     ← outlined field; empty, no length limit
📍 London, United Kingdom            Change      ← once a location is chosen
   or [ City or location ]                        ← search: results after 3 chars, 400 ms debounce
      [ ⌖ Use current location ]                  ← one-shot fill from the device position
Use location time zone                  [on]      ← only when a zone was detected
{zone's full display name}                        ← body-sm, ink-muted
[ Calculation method                   ▾ ]        ← dropdown, 10 methods
[ Shāfiʻī ] [ Ḥanafī ]                            ← Asr school
Hijri date adjustment                             ← §18
Shifts the Hijri date and Ramadan for local moon sighting. …
[ −2 ] [ −1 ] [ 0 ] [ +1 ] [ +2 ]
[                 Save                  ]         ← saffron, ink label; disabled until name + location
[            Delete profile             ]         ← edit only; outlined, error red
```

**Name.** The field starts empty, with no "Home" or "Profile 2" pre-fill, and there's no 20-character limit.

**Location**
- **Search** uses the platform `Geocoder`. Results appear as "City, Country" rows under the field. With no results the list just stays empty; there's no "No cities found" message.
- **"Use current location"** asks for approximate location and fills in the device's last known position. It is a one-time fill: it doesn't create a GPS profile that follows the device.
- **"Change"** reopens an empty search (PR #15 M5).

**Calculation method.** A dropdown listing Muslim World League, ISNA, Umm al-Qurā, Egyptian, Karachi, Dubai, Moon Sighting Committee, Kuwait, Qatar and Singapore. The default is Muslim World League. The architecture plan's default was ISNA, and it planned a short description for each method.

Home's header uses short names for the same methods: MWL, ISNA, Umm al-Qurā, Egyptian, Karachi, Dubai, MSC, Kuwait, Qatar, Singapore.

**Asr school.** A two-button segmented row, Shāfiʻī or Ḥanafī. The default is Shāfiʻī. Only that madhab's Asr is shown anywhere in the app.

**Save.** Enabled once the profile has a name and a location. It closes the sheet, and the new profile becomes the last page of the Home pager. Opened from Home, the pager then lands on it; opened from Settings, the user stays on Settings.

**Delete.** Only when editing. It works immediately, with no confirmation.

### Location time zone

**Context.** Each profile stores a location. When a user travels and keeps a "London" profile, they may want to see London prayer times in London time, even while the device is on another time zone.

**Data model**
- `timezone: String` — an IANA ID detected when the location is chosen (for example `"Europe/London"`). Blank when detection fails.
- `useLocationTimezone: Boolean` — **on by default for new profiles** (since 2026-05-23). Profiles created before the option existed stay off until the user changes them.

**Auto-detection rules**
- **"Use current location":** the device's zone, `ZoneId.systemDefault()`.
- **City search:** `android.icu.util.TimeZone.getAvailableIDs(countryCode)`. If the country has one zone, use it. If it has several, pick the one whose `rawOffset` is closest to `longitude / 15 × 3 600 000 ms`. That picks an hour-off zone for some major cities: Madrid and Barcelona get `Atlantic/Canary`, Lisbon `Atlantic/Azores`, Detroit and Atlanta a US Central zone, Calgary `America/Vancouver`, Surabaya WITA (DS32).
- **Editing an older profile with a blank zone:** reverse-geocode its coordinates and detect again.

**Placement.** Below the location, above the calculation method. The row is rendered only when `timezone` isn't blank. There's no ghost row and no "unknown" label; silence is clearer than a disabled toggle.

**The row**
- "Use location time zone" in `body`, `ink`.
- Under it, in `body-sm`, `ink-muted`, the zone's full display name from `ZoneId.getDisplayName(TextStyle.FULL, locale)`. That's a zone name with no offset; the April example "London time (GMT+1)" isn't what the platform returns.
- The switch has a saffron track when on and a parchment-muted track when off.

**Behaviour.** With the toggle on, the profile's zone applies to:
- Home's times, countdown, timeline states, Hijri date and surface phase
- Qibla's surface phase, and the Tracker's times and "today"
- Notification alarms and the live notification
- Widgets

Three things still use the device's date (DS12): the date a mark is saved under, from Home or the Tracker; the date the Notifications screen computes its rows for; and the midnight rollover that re-arms notification alarms. With the toggle off, everything uses the device zone.

---

## 18. Hijri Date Adjustment

### Context

Islamic communities differ on when each month, and above all Ramadan, begins. Some follow a calculated calendar; others wait for a moon sighting. This per-profile setting shifts the app's Hijri calendar by up to two days in either direction.

### Placement

In the profile sheet (§17), below the Asr school and above Save. Each profile has its own value.

### Control

- A label, "Hijri date adjustment", in `body`, followed by a caption in `body-sm`, `ink-muted`: "Shifts the Hijri date and Ramadan for local moon sighting. 0 keeps the calculated date; + starts the month earlier, − later."
- A five-option segmented row: **−2 · −1 · 0 · +1 · +2**. The minus signs are the true minus, U+2212.
- The selected option is filled `saffron` with an **ink** label. The April spec said parchment, but parchment on saffron is 2.99:1. The others are outlined.
- The labels use Material's `labelSmall`, which falls back to Roboto (DS5).

This replaces the April three-option control ("Calculated · +1 day · +2 days"), which could only delay a month.

### Semantics

- The offset is added to the civil date before the Hijri conversion.
  - **+1** shows tomorrow's Hijri date, so a month starts a day earlier. Use it when the moon was sighted before the calculated start.
  - **−1** starts a month a day later.
- It applies, per profile, to:
  - the Hijri date on Home and on widgets
  - Ramadan detection: the Imsak row, the Ramadan banner, and the Imsak alarm when this is the "Alerts for" profile

### The adjustment lapses

The offset is pinned to the Hijri month it was set in, as adjusted. It stops applying — dropping back to 0 — once the adjusted date leaves that month. The profile sheet then shows 0. The idea is that a moon-sighting correction lasts one month.

**Known defect — the lapse lands on the wrong day (DS7):**
- **+1:** on the user's own 1 Shawwāl (Eid), the adjusted date has left Ramadan, so the offset lapses. The calculated calendar still says 30 Ramaḍān, so the app shows the Ramadan state on Eid: the Imsak row, the banner, and an Imsak alarm.
- **−1:** set on the calculated 1 Ramadan, it's pinned to Shaʻbān and lapses the next day. It delays Ramadan's start but not its end.
- **+1 saved before the first fast** (the evening a sighting is announced, or any time in Shaʻbān): it's pinned to Shaʻbān and lapses at midnight on the user's own 1 Ramaḍān, where the calculated calendar still says Shaʻbān. The first fast day gets no Ramadan state and no Imsak alarm.

The existing tests check the pieces on inputs production never combines, not the Ramadan state the screen then shows (DS7).

### Calendar source

- Android uses `android.icu.util.IslamicCalendar` without choosing a calculation type, so ICU chooses one from the device's region: **Umm al-Qura for Saudi-region locales, the tabular civil calendar everywhere else**. That was checked by running ICU4J 74.2 for US, GB, SA, AE, EG, PK, ID, MY, TR, IR, QA, KW and BD.
- The two calendars gave different dates on 655 of 1,095 days in 2025–2027, and disagreed on whether it was Ramadan on 2 of those days.
- So the same profile can show different Hijri dates on two phones, and the ±2 adjustment is relative to whichever calendar the device picked (DS11).
- `architecture-design.md` said "Umm al-Qura by default"; that is not what ships.

### Persistence

Two Room columns on `Profile` (database version 4):
- `hijriOffset`, stored in a column still named `ramadanOffset`, holding −2 to +2.
- `hijriOffsetMonthKey`: the adjusted month (year × 12 + month) the offset was set for. 0 means none.

---

## 19. Unified Prayer Countdown

### Context

The countdown is the reason people open the app. It appears on the home screen, on all four
widgets, in the live notification, in the WearOS app and complications, and — once those
platforms exist — on iOS and watchOS. Before this spec each surface derived its own version, and they disagreed about what
happens in the minutes just after a prayer begins.

One rule now governs all of them, implemented once in
`shared-logic/.../shared/timeline/PrayerTimeline.kt`. UI may differ; the number may not.

### The rule

| When | Reads | Direction |
|---|---|---|
| Before the prayer | `-00:12:35` | counting down towards it |
| At the prayer instant | `00:00:00` | sign drops |
| First 30 minutes after | `00:15:42` | counting up from it |
| After 30 minutes | `-03:42:18` | counting down towards the next |

The count-up window ends early if the next event arrives before 30 minutes are up, so two
events are never "current" at once.

**Sunrise is a target, never a source.** It closes the Fajr window, so the countdown runs
towards it — but nothing begins at sunrise, so the moment it passes the countdown moves
straight on to Dhuhr. Only prayers count up.

### Format

- `HH:MM:SS`, zero-padded, minus sign only while counting down.
- Hours are not wrapped at 24. A gap longer than a day (possible at high latitudes) reads
  `-31:04:12` rather than silently restarting.
- Fraunces `display-xl` on the home hero, tabular numerals (`tnum`). Never centred — §5.
  The bundled Fraunces has no tabular figures, so until DS9 is settled the digits drift
  (§4 Numerals).
- The prayer the number refers to is always named next to it. A bare signed number does not
  say whether Dhuhr is coming or has just started.

### Platform exception: system-ticked surfaces (widgets, live notification)

Both platforms have the same shape of constraint: the only way to show a countdown that ticks
without something of ours running every second is to hand the system a target instant and let
it render the number. In exchange the system owns the format. **The state and the direction
are always ours; the padding is not.**

#### Android

Android widgets render the countdown with `RemoteViews.setChronometer` +
`setChronometerCountDown` (architecture-design.md, Reviewer Concern #4). The system ticks it
natively in the launcher process, which is what makes a live countdown possible at all
without a per-second update job — but the format belongs to the platform's `Chronometer`,
which emits `MM:SS` under an hour and `H:MM:SS` above it, and cannot be zero-padded.

Widgets therefore render `-12:35` where the app renders `-00:12:35`. The sign is ours (the
Chronometer format string carries it); the padding is not. **The state and the direction are
identical** — only the padding differs. Do not "fix" this by replacing the Chronometer with
a periodic update job; that trades a live countdown for a stale one.

The live notification (§22) has the same constraint and less room: its chronometer is the
notification's own `when` field, which takes no format string at all. There the direction is
carried in words — "At 1:00 PM" while counting down, "Began at 1:00 PM" while counting up.

#### iOS

iOS widgets and Live Activities render the countdown with SwiftUI's
`Text(timerInterval:pauseTime:countsDown:)`. WidgetKit ticks it in the system's own render
process, so the widget extension is not woken and no timeline budget is spent on the ticking
itself — the iOS counterpart of the Android `Chronometer`, and the resolution of Reviewer
Concern #4 for this platform.

Two differences from Android follow from the API, and neither is negotiable:

- **No sign at all.** `Text(timerInterval:)` takes no format string, so unlike the Android
  `Chronometer` we cannot even prepend the minus. iOS widgets carry the direction the way the
  Android live notification does — in words beside the number: `At 1:00 PM` while counting
  down, `Began at 1:00 PM` while counting up. The prayer name is always named next to it.
- **One direction per timeline entry.** The view is fixed at render time and cannot flip from
  counting down to counting up partway through. So a WidgetKit entry covers exactly one
  segment of the rule, and the entry boundaries are exactly `nextTransition()` — prayer
  instants and +30-minute closes, nothing else. That is roughly a dozen entries a day, well
  inside WidgetKit's refresh budget, and it is why the shared timeline exposes `transitions()`
  as well as `nextTransition()`: WidgetKit needs the whole schedule up front, where Android is
  woken at one boundary at a time.

Do not replace the timer text with a per-minute timeline of static strings. It would spend the
entire daily budget to render a worse countdown, and the widget would be visibly stale between
refreshes.

### Boundary behavior

Deterministic at: the prayer instant, exactly +30 minutes, midnight, Fajr across the date
boundary, Isha → next day's Fajr, and profile/timezone/location changes mid-countdown. All
comparisons are on absolute instants resolved in `profile.effectiveZoneId()`, never on
wall-clock `LocalTime` — an Isha at 00:25 sorts before Fajr on a clock and after Maghrib in
reality.

Surfaces that cannot tick continuously (widgets, live notification) arm their next refresh
at `nextTransition()`, which returns the +30 minute flip as well as prayer boundaries.

---

## 20. Friday Naming — Jumu'ah

### The rule

On Friday, Dhuhr is displayed as **Jumuah**. Every other prayer, and every other day, is
unchanged.

This is a label, not a prayer. Jumu'ah is calculated, scheduled, tracked and notified as
Dhuhr; `Prayer.DHUHR` remains the stored value and nothing downstream branches on the name.
Implemented once, in `shared-logic/.../shared/timeline/PrayerNaming.kt`.

### Where it applies

Anywhere a **specific day's prayer** is shown:

- Home countdown hero and prayer ribbon
- All four widgets — schedule rows, 4×2 columns, countdown subject, and the 3-letter
  abbreviation (`JUM`, not `DHU`)
- Prayer notifications and early reminders, including their text, and the live notification
- Prayer tracker — today's rows, history rows, and the mark-prayer sheet
- The Qibla screen's phase label
- The WearOS app and complications; watchOS when it's built

### Where it does not

Anywhere a **recurring setting** is configured. A notification settings row governs all seven
days; calling it "Jumuah" because today happens to be Friday would misdescribe what the toggle
does. Those rows keep the canonical name, with today's time beside them as reference — the
same treatment §15 already gives them.

### Abbreviation

Widget abbreviations are derived from the displayed name (first three letters, uppercased)
rather than mapped per prayer, so the abbreviation cannot drift from the label it abbreviates.

---

## 21. Profile Creation — FAB Flow

### Entry point

A saffron FAB sits at the bottom-right of the **Prayers** screen, clear of the dot indicator
and the bottom nav. Icon only (`Add`), `Ink` on `Saffron` — the same treatment the Settings
profile list already uses, so the affordance reads as one thing in two places.

### The flow

1. Tap the FAB → the profile form opens as a bottom sheet **on the Prayers screen**. The user
   does not leave the screen they were reading.
2. Fill in name, location, calculation method, Asr school, Hijri adjustment (§18).
3. **Save** → the profile is persisted, becomes the selected profile, and the pager scrolls
   onto it. The user lands on what they just made.
4. **Dismiss** → nothing is written. No profile is created, and no existing profile changes.

The empty state's "Create profile" CTA opens the same sheet.

### One form, three doors

Prayers FAB, empty-state CTA and Settings > Profiles all open the same `ProfileFormSheet`
composable. A field added to the form cannot appear at one entry point and not another.

### No "+" pager slot

The profile pager has no trailing "+" page. It duplicated the FAB and cost the pager a phantom
page that the dot indicator counted, so a user with two profiles saw three dots.

---

## 22. Live Prayer Countdown Notification

### What it is

An optional ongoing notification carrying the current prayer and a live countdown, following
the same rule as every other surface (§19).

**Off by default.** An ongoing notification the user did not ask for is the kind of thing that
gets an app's notifications muted wholesale.

### Content

| Field | Counting down | Counting up |
|---|---|---|
| Title | prayer name — day-aware (§20) | prayer name |
| Text | `At 1:00 PM` | `Began at 1:00 PM` |
| Sub-text | profile name | profile name |
| Time slot | system chronometer counting **down** to the prayer | counting **up** from it |

The number is the platform's own chronometer (`setWhen` + `setUsesChronometer` +
`setChronometerCountDown`). Nothing of ours runs per second, and the count stays right while
the app is dead. The chronometer takes no format string, so the sign lives in the text — see
§19's platform exception.

Tapping it opens the app on the profile it is about, the same as a widget tap.

### Channel

Its own channel at `IMPORTANCE_MIN`: silent, no vibration, no badge. This notification is
present all day; it belongs in the shade, not on the status bar competing for attention.

### How it stays current

One exact alarm at a time, armed at `nextTransition()` — the moment a prayer starts, or the
moment its 30-minute count-up window closes. The receiver re-arms on every fire, so the chain
survives process death; boot and timezone changes re-enter through `AlarmScheduler.scheduleAll`.

**Not a foreground service.** A service would hold a process alive all day to render a number
the system can tick on its own, and would need a `FOREGROUND_SERVICE_SPECIAL_USE` justification
it does not deserve.

### Setting

Notification settings → OTHER → **Live countdown**, a 64pt two-line toggle row above Ramadan
Imsak. Same shape as the Imsak row: both are opt-in behaviours that need a sentence of
explanation, unlike the plain per-prayer toggles.

The master toggle gates it as well. Master off means "aynama may not put prayer notifications
in my shade", and an ongoing one would contradict that most visibly of all. The user's own
preference is left untouched, so turning master back on restores the notification without them
having to re-find the row — which matters, because the row lives inside the master-gated part
of the screen.

---

## 23. Live Prayer Countdown — iOS

### Why this is not §22 with the nouns changed

§22's mechanism is an ongoing notification that costs nothing because the system ticks it and
it stays in the shade all day. iOS has no such object. The nearest candidates each fail on an
Apple constraint rather than a design one:

| Candidate | Why not |
|---|---|
| An ongoing local notification | iOS has no ongoing/persistent notification. Every delivered notification is dismissible and inert — nothing in it ticks. |
| A repeating silent notification | Would consume the 64-pending budget (§24) to redraw a number, and clutter Notification Center all day. |
| An all-day Live Activity | ActivityKit ends an activity after **8 hours** active plus up to 4 stale, and starting one from the background needs a push token, which needs a server. §22's "present all day, survives process death" is not available. |

So the iOS live countdown is **two surfaces, not one**, and the always-on half is a widget.

### The always-on half: lock-screen and home-screen widgets

`.accessoryRectangular` and `.accessoryInline` widgets on the Lock Screen are the true iOS
analogue of a persistent status notification: always visible, always current, ticked by the
system, no battery cost, no server, and they survive process death and reboot because
WidgetKit owns the timeline. They follow §19 exactly, in the iOS form of the platform
exception above.

This is on by default in the sense that any widget the user places is live — there is no
toggle, because there is nothing running to turn off.

### The opt-in half: a bounded Live Activity

An optional Live Activity covering **the current prayer window only** — never all day.

- Started only from the foreground, when the user asks for it. No push server.
- `staleDate` at `nextTransition()`; the activity ends at the count-up window's close or the
  next prayer, whichever the rule says comes first.
- Content follows §19 and §20: the day-aware prayer name, the timer text, the profile name.
- Tapping it opens the app on the profile it is about, the same as a widget tap.

A single prayer window fits inside ActivityKit's 8-hour budget in every ordinary case. The one
that does not is the Isha → Fajr gap in a high-latitude winter, which can exceed 8 hours; the
activity is allowed to expire there rather than being renewed, and the Lock Screen widget —
which has no such limit — carries the countdown across the night.

**Off by default**, for §22's reason: a persistent surface the user did not ask for is how an
app gets muted wholesale.

### Setting

Notification settings → OTHER → **Live countdown**, the same two-line 64pt row as Android,
with copy that says what it actually is on this platform ("Shows the current prayer on the
Lock Screen until the next one begins"). Gated by the master toggle, like Android's.

---

## 24. iOS Notification Budget

### The constraint

iOS keeps at most **64 pending local notifications per app**. Anything scheduled beyond that
is silently dropped — no error, no callback. Android's exact-alarm chain has no equivalent
limit, so this is a genuinely iOS-shaped problem and the iOS answer wins over Android's
scheduling shape (Phase 3A conflict resolution).

The naive schedule overflows: 5 prayers × 7 days = 35, which is fine, but adding a per-prayer
early reminder doubles it to 70.

### The rule

Schedule a **horizon of whole days**, sized from what the user has actually enabled:

```
perDay  = enabled prayers + enabled early reminders + Imsak (Ramadan only)
horizon = clamp(60 / perDay, 3...7) days
```

- **60, not 64.** Four slots are held back as headroom so a feature that needs a one-off
  notification cannot silently push a prayer out of the queue.
- **Prayers are scheduled before reminders**, day by day. If the budget runs out mid-day the
  thing dropped is an early reminder, never the prayer it reminds about. Overflow degrades in
  the order the user would choose.
- The worst realistic case — all five prayers, all five reminders, plus Imsak — is 11 a day,
  which still yields a 5-day horizon. The best case is capped at 7 days rather than 12: past a
  week the times drift enough that rescheduling is better than a longer queue.

### Refilling

The queue is rebuilt whenever the app becomes active, and from a `BGAppRefreshTask` registered
for roughly daily. Both are opportunistic — iOS guarantees neither — which is why the horizon
floor is 3 days and not 1: the user who does not open the app for a couple of days must not
lose their notifications.

Profile edits, timezone changes and permission changes rebuild it immediately. There is
nothing to restore after a reboot: pending notifications survive it, unlike Android alarms.

---

## 25. Home-Screen Widgets (Android)

Four widgets, each picked separately from the launcher's widget list. They are glanceable surfaces: a fixed palette, no time-of-day cycle, and no dark variant.

### Shared anatomy

- **Body:** `parchment` with 8 dp corners. `ink` bands at the top (dates) and bottom (profile), square on their inner edge.
- **Type:** Fraunces for prayer names and countdowns; IBM Plex Sans for times, dates and profile names.
  - RemoteViews can't set variation axes, so the widgets likely render Fraunces at the file's default instance — Black (900) at optical size 9 — not the 400/500 weights of §4. Verify on a device (DS10).
- **Countdown:** a system Chronometer following §19. It counts down to the next event, Sunrise included, with a leading minus, then counts up without a sign for the first 30 minutes after a prayer. The platform owns the format: H:MM:SS, or MM:SS under an hour.
- **Names** are day-aware: on Friday, Dhuhr reads Jumuah and abbreviates to JUM (§20).
- **Times** follow the device's 12/24-hour setting.
- **Tap:** anywhere opens Home on this widget's profile, switching tabs if needed.
- **Profile:** chosen per widget on the configure screen when the widget is placed, and again through the launcher's reconfigure action. A widget with no choice shows the "Alerts for" profile.

### The four widgets

**Next Prayer** — 1 × 1, resizes from 40 to 250 dp. A centred stack:
- the countdown's prayer (the next one, or the one that has just begun) — Fraunces 18 bold, saffron
- its time — IBM Plex Sans 14, ink
- the countdown — Fraunces 22 bold, ink
- the profile — IBM Plex Sans 11, ink-muted

**Next Prayer & Dates** — 2 × 2.
- Top band (a quarter of the height): the Gregorian date "Wed, Sep 23" (IBM Plex Sans 13, parchment) and the Hijri date (11, parchment-muted).
- Middle: the name (Fraunces 20 bold, saffron), the countdown (Fraunces 30 bold, ink), and the time (IBM Plex Sans 14, ink).
- Bottom band: the profile (IBM Plex Sans 12, parchment-muted).

**Prayer Schedule** — 2 × 2.
- Top band: the dates.
- Middle: six rows, Fajr to Isha including Sunrise. Name in Fraunces 14 on the left, time in IBM Plex Sans 14 on the right, all ink.
- Bottom band: the profile.

**Prayer Times** — 4 × 2.
- Top band: the dates on the left (IBM Plex Sans 16 and 13). On the right, "Sunrise" and its time (13 and 17).
- Middle: five columns, Fajr to Isha. Name in Fraunces 14, ink, over the time in IBM Plex Sans 14, ink-muted.
- Bottom band: the countdown (Fraunces 18 bold, saffron), "until Asr", or "since Asr" while counting up (IBM Plex Sans 13, parchment), and the profile (12, parchment-muted).

**Current-event highlight** (Prayer Times only)
- The current prayer's column turns saffron and bold.
- Between sunrise and Dhuhr, the top band's Sunrise block is highlighted instead, but only once today's sunrise has actually passed.
- TalkBack reads each time with its name and state: "Asr, 4:14 PM, current".

**Contrast.** The saffron prayer names on parchment are 2.99:1. That fails at 18 pt bold, and misses the 3:1 large-text minimum at 20 pt bold (DS2).

### States

| State | Shows |
|---|---|
| No profile yet | "Set up profile", "Open aynama" and "--:--". The countdown behaves as in the polar row below. |
| The profile has no computable times (polar day or night) | "No times here", with "Midnight sun or polar night" where the Hijri date goes. The countdown starts at zero and then runs negative until the next refresh, at most 30 minutes later: the Chronometer doesn't stop at zero, and it shows two minus signs, the widget's and the platform's (`PrayerWidget.kt:333,358`, `:623-626`). |

### Updates

Widgets re-render:
- 2 s after each change of countdown state (every event, and 30 minutes after each prayer), with the next 12 always armed, for every profile a placed widget shows
- when the time or time zone changes
- when the app opens
- every 30 minutes as a backstop

### Configure screen ("Choose a profile")

- A utilitarian surface. The title "Choose a profile" in `title`, then "This widget will show the selected profile's prayer times." in `body`, `ink-muted`.
- One row per profile: a place icon and the name in `body-lg`. The current choice is saffron (DS2).
- Tapping a row saves it and closes the screen. A "Cancel" text button closes without saving.
- With no profiles: "No profiles set up yet. Open aynama to add one."

The widget picker shows one generic placeholder image for all four widgets. Android 12+ launchers render the real layout instead.

---

## 26. Screen States

Loading is never a shimmer (§8).

**Home**

| State | Treatment |
|---|---|
| Loading | Plain `ink` surface. |
| No profiles | `ink` surface, with a Kaaba mark (the emoji placeholder, DS17). "Set up your first prayer profile" in `title`, parchment. "Add a location to see accurate prayer times." in `body`, parchment at 60%. A "Create profile" button, saffron with an ink label, opens the profile sheet in place (§21). |
| Error | `ink` surface, "Something went wrong" and the raw exception message. There's no specific cause and no way to recover (DS24). |
| A page with no times today (polar day or night) | That page only, on the Isha surface. The profile name in `body-sm`. "No prayer times today" in `display-md`. Then, in `body` at 70%: "The sun doesn't fully rise or set at this location today, so there are no times to calculate from. This happens inside the polar circles around midsummer and midwinter. Other profiles are unaffected." |

**Qibla**

| State | Treatment |
|---|---|
| Loading | `ink` surface. |
| No profile | `ink` surface, "Set up a prayer profile to find Qibla direction". |
| No rotation sensor | `parchment` surface, "Compass not available on this device". |
| Low accuracy | The calibration banner (§5). |
| The default profile has no times (polar) | The compass works normally on the Isha surface. |

**Tracker**

| State | Treatment |
|---|---|
| Loading | Blank utilitarian surface. |
| No profiles, or a load error | "Create a profile to track prayers" in `body`, `ink-muted`, centred. A database error lands here too. |
| The default profile has no times today (polar) | The same no-profile message, and it stays until the ViewModel is recreated (DS30). |

**Other surfaces**

| Surface | State | Treatment |
|---|---|---|
| Notifications | Permission denied, master off, or a polar profile | §15. |
| Profile sheet | No location results | Nothing shown. No finding tracks this yet; PR #15 M6 covers the missing loading indicator. |
| Profile sheet | No location fix | Nothing happens (PR #15 M2). |
| Widgets | No profile, or no times | §25. |
| Watch | No profiles, or no times | "No profiles yet", saying to open aynama on the phone (or, after a sync, to add a profile there). "No times today" for a polar day. "Phone not seen recently" under the countdown when the sync is stale. |

The empty-state rule from `architecture-design.md` still applies: a small meaningful visual, a plain-language heading, one primary action, and at most one sentence of context.

---

## 27. Android v1 Conformance

Where the shipped Android app stands against this document's rules. Each gap has a finding in `REVIEW-FINDINGS.md` under "From design-doc sync". When a gap is fixed, follow the closing steps in `REVIEW-FINDINGS.md` in the same PR: update every mention of its ID, delete the finding, and remove the ID from its row here.

| Rule | Status | Finding |
|---|---|---|
| §3 — AA contrast | ✗ Saffron text on light surfaces. The Home timeline on every phase but Isha (§3 table). Dark-mode colours. Light `onPrimary` on saffron. | DS1, DS2, DS3, DS4 |
| §4 / §10 — no Roboto or system sans | ✗ Every button, menu item and time-picker label, plus two direct `labelSmall` uses. The watch app bundles no fonts. | DS5, DS36 |
| §9 — the moving sundial tick | ✗ A static dot on the current row. | DS6 |
| §18 — the Hijri adjustment is correct | ✗ The lapse lands on the wrong day. With +1 the Ramadan state appears on the user's Eid, or is missing on their first fast day; with −1 Ramadan's end is never delayed. | DS7 |
| §6 — app icon | ✗ No launcher icon; the launch window is platform grey. | DS8 |
| §4 — tabular numerals | ✗ The Fraunces countdowns drift. IBM Plex times are fine. | DS9 |
| §4 — Fraunces only at 400/500 | ? Widgets probably render Fraunces Black. | DS10 |
| §18 — the same Hijri date on every device | ✗ It depends on the device's region. | DS11 |
| §17 — one time zone per profile everywhere | ~ Mark dates, the Notifications screen's date and the alarm rollover use the device's date. | DS12 |
| Consistent time format | ✗ Mixed 12-hour and 24-hour. | DS13 |
| §15 — alerts behave well | ✗ No tap action, no stop action, Imsak treated as a prayer, placeholder audio. | DS14 |
| §16 — "on time" only within the window; missed distinct from unmarked | ✗ | DS15 |
| §15 — Notifications survives a polar profile | ✗ | DS16 |
| §6 — custom Kaaba mark, no gold | ✗ Emoji. | DS17 |
| §11 — the surface extends behind the status bar | ✗ | DS18 |
| §3 — no colours outside the tokens | ~ Calibration amber, error red, the oxblood banner. | DS19 |
| §4 — Material slots used as designed | ~ `mono-num` also styles the nav labels. | DS20 |
| §6 — stroke icons; filled only for the active tab | ✗ Also, no tab is selected on the Notifications routes. | DS21 |
| §6 / §10 — no crescent motif | ? Crescent notification icon — needs a decision. | DS22 |
| §4 — consistent transliteration | ~ | DS23 |
| `architecture-design.md` interaction states — errors name a cause and a recovery | ✗ | DS24 |
| §12 — TalkBack coverage | ~ | DS25, PR #16 A5, PR #13 A1 |
| §5 — the Ramadan banner doesn't cover content | ✗ | DS26 |
| §16 — no dead styling paths | ~ | DS27 |
| §8 / §12 — transition fades, reduced motion | ✗ | DS28 |
| §12 — 48 dp touch targets | ✗ Home's timeline rows are about 25 dp tall. | DS29 |
| §26 — the Tracker survives a polar profile | ✗ It shows its no-profile state. | DS30 |
| §5 — Maghrib holds until an after-midnight Isha | ✗ Near the June solstice at about 50°N and up, Isha shows as current from about 1 AM. | DS31 |
| §17 — the detected location time zone is right | ✗ City search picks a neighbouring zone for some major cities. | DS32 |
| §5 — Qibla uses the device's location | ✗ The fine-only request may never show a dialog on Android 12+. | DS33 |
| §16 — the Tracker shows the prayers marked on Home | ✗ Marks on other profile pages never reach it. | DS34 |
| §15 — rows show the current alert time | ✗ They show the time from when the screen opened. | DS35 |
| §21 — the FAB stays clear of the content | ? It likely covers part of the Isha time on shorter phones. | DS37 |

✗ = breach · ~ = partial · ? = unverified or needs a decision

**Meets:**
- no circular progress anywhere
- no calligraphy, domes or 99-Names wallpaper
- no gradients on utilitarian screens
- no shimmer
- no cards or coloured left borders on rows
- no streaks or gamification copy
- dynamic colour off, and every Material colour role except `error` mapped from the tokens
- squares, not circles, in the tracker
- a single quiet Qibla ring
- 48 dp touch targets on the utilitarian screens

---

## Provenance

- Created: 2026-04-18
- Process: `/design-consultation` skill (gstack v1.0.0.0)
- Outside voice: indie-studio perspective (GT Alpina, 29LT Zarid, burnt sienna considered — rejected in favor of free open-source equivalents that preserve the same emotional direction while keeping the project fully open)
- Advisor corrections applied: separated Quran typography from UI Arabic; hybrid surface stance (not app-wide gradient); watch-scale spec written first; ink shifted warm-brown to meet "avoid purple/indigo" mandate
- 2026-05-08 — §5/§9/§10 amended for the Qibla ring (PR #12)
- 2026-05-23 — §15 per-prayer detail and profile scoping; §17 added (PR #17)
- 2026-05-25 — §18 added (PR #18)
- 2026-08-06 — sunrise-phase gradient changed; no North readout on Qibla (PR #21)
- 2026-09-23 — re-baselined against Android v1 (Phases 0–7).
  - Contrast table recomputed; the font files audited for tabular figures and default instances; the Material 3 fallback colours and fonts checked against the Compose sources.
  - §5, §15–§18 rewritten to match the code. Widgets, screen states and conformance added, now §25–§27.
  - Spec changes flagged for §14 sign-off: listed in TODOS.md ("Sign off the spec changes the sync recorded").
- 2026-09-24 — the sync re-checked against `agent-main` (`5eeed05`), where #22–#33 had landed, and placed after that branch's §19–§24. The Home hero follows §19 again, the add-profile page is gone, DS1 and DS12 narrowed, DS36 and DS37 added.
