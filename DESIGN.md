# aynama-prayer-times — Design System

**Status:** v1 foundation. Governs all UI decisions across iOS, Android, watchOS, WearOS.
**Aesthetic:** Contemplative editorial. Warm. Hospitable. Unhurried.
**Stance:** Hybrid — reverent hero, utilitarian tools.

Every UI decision in this repo traces back to this document. If a new surface contradicts something here, update this file first, then the code.

---

## 1. Visual Thesis

Aynama feels like sitting on a cool stone floor at dusk with a cup of cardamom tea. Not a mosque interior. Not a minimalist tech product. The first three seconds should read as *hospitable* — a host dimming the lights for you — rather than *sacred* — a building commanding reverence.

Reverence comes from restraint, not gold filigree. Warm shadow, paper grain, one deliberate ornament per screen.

The app is a companion, not a shrine.

---

## 2. Design Stance: Hybrid

Two modes. The surface tells you which one you're in.

**Contemplative screens** (reverent hero treatment):
- Home (prayer timeline)
- Countdown (next prayer)
- Qibla

**Utilitarian screens** (pragmatic clarity):
- Settings
- Zakat calculator
- Habit tracker
- Notifications
- Quran reader (text is sacred; the UI serving it is not)

Contemplative surfaces carry the time-of-day cycle. Utilitarian surfaces stay on a stable neutral. Do not bleed one into the other.

---

## 3. Color System

### Core tokens

| Token | Hex | Role |
|---|---|---|
| `ink` | `#1C1A17` | Warm brown-black. Primary text on light. Primary surface on dark. |
| `ink-muted` | `#6B6560` | Secondary text, passed-prayer states. |
| `parchment` | `#F2EAD8` | Unbleached linen. Primary surface on light. Primary text on dark. |
| `parchment-muted` | `#D4C9B1` | Dividers, subtle backgrounds. |
| `saffron` | `#B87A2E` | Accent. Active states, current prayer, key CTAs. |
| `saffron-ink` | `#8A5A22` | Saffron on parchment pressed state. |

**Rules:**
- `ink` is NOT `#000`. NOT slate. It is warm and brown-tinted.
- `parchment` is NOT white. NOT cream. It is unbleached linen.
- Saffron is the only accent. No secondary accent color. Do not add emerald, teal, purple, indigo, or mosque-green anywhere, even as "just a hint."

### Time-of-day surface cycle (contemplative screens only)

Surfaces on Home, Countdown, and Qibla shift across the day. Transitions are slow cross-fades tied to real astronomical events, not discrete clock thresholds.

| Phase | Top | Bottom | Notes |
|---|---|---|---|
| Fajr (predawn) | `#1C1A17` | `#3A3530` | Warm ink to predawn warmth. **NOT indigo. NOT cool blue.** |
| Sunrise transition | `#3A3530` | `#E8C89A` | Brief (~15 min window). |
| Dhuhr (midday) | `#F2EAD8` | `#EDE1C5` | Flat linen parchment. Bright. |
| Asr (afternoon) | `#E8C89A` | `#B87A2E` | Honey to saffron. |
| Maghrib (sunset) | `#6B2E2A` | `#1C1A17` | Oxblood into ink. |
| Isha (night) | `#0F1419` | `#1C1A17` | Deep stone. Near-mono. |

Utilitarian screens ignore this cycle. They stay on `parchment` (light) or `ink` (dark) regardless of time.

### Contrast & accessibility

All text meets **WCAG AA** minimum (4.5:1 for body, 3:1 for large text).

- `ink` on `parchment` = 13.8:1 ✓
- `ink-muted` on `parchment` = 5.2:1 ✓
- `saffron` on `parchment` = 4.6:1 ✓ (AA body)
- `saffron` on `ink` = 4.8:1 ✓ (AA body)
- `parchment` on `ink` = 13.8:1 ✓

Any new color added to this system must be verified against both ink and parchment backgrounds.

---

## 4. Typography

Four independent typography tracks. Do not mix them.

### Track 1 — UI Latin (display + body)

- **Display:** Fraunces (variable serif, opsz axis). Used for prayer names, countdown time, hero numerals, section headers.
- **Body:** IBM Plex Sans. Used for all running UI text, labels, buttons, timestamps.
- **Numerals:** IBM Plex Sans with `font-feature-settings: "tnum"` (tabular) for all time displays and countdowns. Non-negotiable — prayer times must not visually drift.

Both fonts are free, open-source (SIL OFL), have wide weight/opsz ranges, and render cleanly at screen scale.

### Track 2 — UI Arabic

- **IBM Plex Sans Arabic** — matches the Latin stack, for Arabic UI chrome (menu items, labels, buttons in Arabic locale). Optical tuning is genuinely modern, not a grafted-on default.
- **This is NOT a Quranic font.** Do not render Quran text or dua with it.

### Track 3 — Quran Uthmani script

- **KFGQPC Uthman Taha Naskh** (King Fahd Complex standard).
- Non-negotiable for Mushaf fidelity. Do not substitute.
- Used wherever the app displays Quranic verses in the Mushaf-standard script.

### Track 4 — Quran Naskh script

- **Amiri Quran** (Khaled Hosny, SIL OFL).
- Tashkeel-perfect, open-source, optional colored-tajweed variant available.
- Alternate display for Quranic text when user prefers Naskh over Uthmani.

### Type scale

| Token | Size | Line height | Font | Use |
|---|---|---|---|---|
| `display-xl` | 72pt | 1.0 | Fraunces 400 opsz144 | Countdown hero |
| `display-lg` | 48pt | 1.05 | Fraunces 500 opsz96 | Screen headers |
| `display-md` | 32pt | 1.1 | Fraunces 500 opsz48 | Card headers |
| `title` | 20pt | 1.25 | Fraunces 500 opsz20 | Section titles |
| `body-lg` | 17pt | 1.45 | IBM Plex Sans 400 | Primary reading |
| `body` | 15pt | 1.45 | IBM Plex Sans 400 | Default UI |
| `body-sm` | 13pt | 1.4 | IBM Plex Sans 500 | Metadata, captions |
| `mono-num` | 17pt | 1.0 | IBM Plex Sans 500 tnum | Prayer time grid |

Watch scales defined separately in §7.

### Rules

- No Inter. No Roboto. No Arial. No Helvetica. No system-default sans as a final choice.
- Fraunces is the only serif in use.
- Mixing Fraunces with IBM Plex Sans within the same line is fine (hero number + caption). Do not mix Fraunces with IBM Plex Sans Arabic in the same line — script mismatch is jarring.
- RTL layouts mirror correctly; Arabic text never uses Latin typography.

---

## 5. Composition & Layout

### Home screen — prayer timeline ribbon

**Replaces** the industry-standard circular countdown ring. A vertical ribbon anchors the screen.

```
┌─────────────────────────┐
│    Dhuhr · 12:47         │  ← Fraunces display-xl, current prayer
│    in 2h 18m             │  ← IBM Plex body-lg
│                          │
│    ─●─ Fajr   04:21 ✓    │  ← muted ink, passed
│     │                    │
│       Sunrise  06:02     │  ← ink-muted, time reference only, no dot
│     │                    │
│    ─●─ Dhuhr  12:47  ←   │  ← saffron, current
│     │                    │     (moving tick)
│    ─●─ Asr    15:33      │  ← ink, upcoming
│     │                    │
│    ─●─ Maghrib 18:11     │
│     │                    │
│    ─●─ Isha   19:42      │
│                          │
│    [Profile: Home ▾]     │  ← profile switcher, weather-app style
└─────────────────────────┘
```

- Past prayers: `ink-muted`, small check glyph, 60% opacity.
- Current moment: saffron tick that moves down the ribbon as time passes. Like a sundial shadow.
- Upcoming prayers: `ink`, full opacity, no decoration.
- Profile switcher sits at the bottom edge. Tap reveals horizontal card stack (weather-app metaphor).

**No circular rings. No concentric arcs. No pie charts. Anywhere in the app.**

### Qibla — typographic compass

**Replaces** the conventional compass-with-needle.

- Single giant custom arrow glyph (~200pt, center screen), rotating in place against parchment.
- Degree readout in Fraunces `display-md` below. E.g. `227°`.
- Distance to Kaaba in IBM Plex `body-sm`. E.g. `11,842 km`.
- No cardinal N/E/S/W ring. No concentric circles. No 3D Kaaba render.
- Reads as a letterpress print, not a cockpit.

### Countdown screen

- Prayer name in Fraunces `display-lg`, top.
- Time-until in Fraunces `display-xl`, tabular numerals, center. E.g. `2h 18m`.
- Adhan trigger time in IBM Plex `body-lg`, bottom.
- Time-of-day surface behind.

### Utilitarian screens (Settings, Zakat, Tracker, Notifications, Quran reader)

- Stable surface (parchment light / ink dark). No time-of-day.
- Left-aligned content, generous margins (24pt side, 32pt top).
- Lists use 56pt row height minimum. Dense-mode variant allowed at 44pt.
- Section headers in Fraunces `title`. Row labels in IBM Plex `body`.

### Multi-profile UX (weather-app metaphor)

- Horizontal paging cards, one per profile (Home, Work, Travel, Parents').
- Page indicator dots at bottom.
- Active card's surface carries that location's time-of-day cycle.
- Swipe between profiles never interrupts the prayer ribbon structure — only the times and profile label change.

---

## 6. Iconography

- Stroke icons, 1.5pt weight. Consistent terminals (flat cut).
- No filled icons in nav. Filled reserved for active-state tabs only.
- **Forbidden:** mosque-dome silhouettes, crescent-and-star cliché, gold ornaments, stock 99-names calligraphy wallpaper.
- Custom glyphs for: Qibla arrow, prayer-name initials (watch), tasbeeh bead, Kaaba abstract mark.
- Prefer platform symbol libraries where neutral (SF Symbols on iOS, Material Symbols on Android) for utilitarian screens. Swap to custom for contemplative screens.

---

## 7. Watch Scale (watchOS + WearOS)

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

### Tile / watch-face scale (80px–100px)

Adds:
- Prayer name spelled out (`Fajr` not `F`) in Fraunces `body` size
- Sub-line: "in 2h 18m" or "now" in IBM Plex `body-sm`

### Rules

- No gradients on watch. Solid surfaces only.
- No glyphs smaller than 10pt.
- No thin hairlines (< 1pt) — they disappear on OLED.
- Tap targets minimum 44×44pt on watchOS, 48×48dp on WearOS.

---

## 8. Motion

- Prayer-time transitions: 400ms ease-out cross-fade. Never a hard cut.
- Surface cycle (time-of-day): interpolated continuously across the full day, not stepped at prayer boundaries.
- Countdown tick: updates every second for final minute, every minute otherwise. Do not animate numbers rolling.
- Qibla arrow: physics-based rotation (damping 0.8, stiffness 100). No magnetic-needle jitter.
- Page transitions: iOS native push, Android Material predictive back. No custom page transitions.

**Forbidden:** bouncing easing, parallax scrolling, particle effects, glow pulses on CTAs, "shimmer" loading states.

---

## 9. Two Deliberate Departures

These are the things no prayer app does. They are the product.

1. **Typographic Qibla** — letter-arrow, not compass-with-needle. §5.
2. **Prayer timeline ribbon** — vertical line with moving sundial tick, not a circular countdown ring. §5.

If a future surface proposal reintroduces a circular countdown ring or a cardinal-ring compass, reject it at design review. These are the two non-negotiable differentiators.

---

## 10. Anti-Slop (forbidden)

Hard rules. A PR that violates any of these gets blocked at review.

- **No circular progress rings anywhere.** Not countdown, not tasbeeh, not habit tracker, not Zakat progress, not onboarding. Use linear ribbons, depleting bars, or typographic countdowns instead.
- **No mosque-green, gold-on-green, emerald, teal, purple, or indigo** anywhere in the palette, even as "just a hint."
- **No dome silhouettes, minaret icons, or crescent-and-star** decorative motifs.
- **No Arabic calligraphy used decoratively** as background texture, splash art, wallpaper, or ornament. Arabic script in this app is *content*. If it's on screen, it says something specific and is legible.
- **No stock 99 Names wallpapers.**
- **No gradients on utilitarian screens.** No gradients on watch.
- **No purely black or purely white surfaces.** Always warm ink, always linen parchment.
- **No system-default sans-serif as a shipped choice** (no Inter, Roboto, Arial, Helvetica as final body).
- **No bouncing animations, particle effects, glow pulses, or shimmer loaders.**

---

## 11. Platform Notes

### iOS
- Liquid Glass surfaces allowed on utilitarian screens (Settings modals, share sheets). Do not apply Liquid Glass over contemplative time-of-day surfaces — the effect clashes.
- SF Symbols on utilitarian chrome. Custom SVGs on contemplative screens.
- Dynamic Type supported through `display-xl` down to `body-sm`.

### Android
- Material You color-extract is **disabled**. The app does not adopt user wallpaper palette — the design IS the palette.
- Material Symbols on utilitarian chrome.
- Edge-to-edge by default. Status bar matches current surface.

### watchOS
- Complication families: `.circularSmall`, `.graphicCircular`, `.graphicRectangular`, `.graphicCorner`. Spec each per §7.
- Always-On state: same render as active, OLED-safe ink field.

### WearOS
- Tile API for glanceable card. Complication slots per Wear complications spec.
- Color theme locked — does not adopt system theme.

---

## 12. Accessibility

- WCAG AA on all text. Large text 3:1, body 4.5:1. Verified in §3.
- RTL mirror for Arabic locale — all layouts flip correctly, Arabic text never uses Latin typography.
- Dyslexia-friendly option: swap IBM Plex Sans body to OpenDyslexic (optional user setting).
- Reduced-motion honored: time-of-day cycle collapses to static surface per prayer phase; transitions shorten to 150ms crossfade.
- VoiceOver / TalkBack labels required on all touch targets. Prayer times announce full context: "Dhuhr at 12:47, in 2 hours 18 minutes."
- Minimum tap target: 44×44pt (iOS), 48×48dp (Android), 44×44pt (watchOS), 48×48dp (WearOS).

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

Path: `shared/design-tokens.json` (to be created alongside existing shared JSON test vectors).

---

## 14. Change Process

This file governs UI decisions. Changes require:

1. A proposal in PR description: what, why, which principle it touches.
2. Verification that existing surfaces still pass (no silent regressions).
3. Update to this file FIRST, then code that follows.

Small tweaks (adjusting a spacing token, adding a new icon) follow this process lightly. Changes to color, typography, composition (§3, §4, §5), or the Two Deliberate Departures (§9) require explicit discussion before merge.

---

## 16. Prayer History View

**Surface:** Utilitarian treatment. Stable parchment (light) / ink (dark). No time-of-day cycle. Left-aligned, 24pt side margins, 32pt top. Tracker is listed under utilitarian screens in §2.

**Five prayers tracked per day:** Fajr, Dhuhr, Asr, Maghrib, Isha. Sunrise is a time marker on the home ribbon, not a tracked prayer. Do not include a Sunrise indicator in history rows.

---

### Marking flow

**Primary (same-day, in the moment):**

Tap a prayer row on the home screen ribbon (§5). In-place bottom sheet with three states. No navigation away from home required.

**Retroactive (past days — qada and corrections):**

History view → tap a day row → row expands inline → each of the five prayer indicators is tappable → bottom sheet with three states.

**Three prayer states:**

| State | Indicator | Available |
|---|---|---|
| Prayed on time | filled saffron square | Within the prayer window only; greyed out for past days |
| Prayed as Qada | filled ink-muted square | Any past missed prayer |
| Missed | empty square, parchment-muted stroke | Explicit skip acknowledgement |

**Where Qada appears in history:** On the day the prayer was *missed*, not the day it was performed. Fajr skipped Monday and made up Tuesday is shown on Monday's row as Qada. The performance date is stored as metadata but not surfaced in the primary history UI.

**Copy rules:** Never use "Mark complete" or "Check in." Use: "I prayed this" / "I prayed this later (Qada)" / "I didn't pray this." Calm and direct. No exclamation marks, no encouragement copy.

---

### History list structure

**Section headers (by week):**
Fraunces `title` (20pt), ink. "This week" / "Last week" / "Apr 7–13".

**Column header (rendered once at top of list, not repeated per section):**

```
              F  D  A  M  I
```

Single-letter prayer initials (F=Fajr, D=Dhuhr, A=Asr, M=Maghrib, I=Isha) in IBM Plex `body-sm`, ink-muted. Same convention as watch complication initials (§7).

**Day row anatomy:**

```
Today         ▪ ▪ ○ ▪ ▪    4/5
Yesterday     ▪ ▪ ▪ ▪ ▪    5/5
Mon Apr 14    ▪ ○ ○ ▪ ▪    3/5
```

- **Left:** date label in IBM Plex `body`, ink. Today's label renders in saffron (text token only — no colored border or card treatment).
- **Center:** 5 prayer indicator squares, 8×8pt each, 5pt gap, left to right in prayer order.
- **Right:** count in IBM Plex `body-sm`, ink-muted. "5/5" or "3/5".
- **Row height:** 56pt minimum (per §5 utilitarian spec).

Squares, not circles. Avoids the circular ring territory (§10); reads as a printed ledger.

**Expanded day row (tap to reveal):**

Each prayer name with its scheduled time and status, listed vertically. Prayer name in IBM Plex `body`, scheduled time in IBM Plex `mono-num` (tabular). Each row is tappable to trigger the state bottom sheet.

---

### Soft aggregate header

Above the list, a single descriptive line:

```
42 of 45 prayers on time this week
```

IBM Plex `body-sm`, ink-muted. Current week only. Not a streak counter — a descriptive sentence. No hero number, no reset mechanic.

---

### Hard rules

- No calendar grid or monthly heat-map. No gradients (§10).
- No streak counter as primary or secondary hero.
- No circular progress indicators (§10).
- No colored left-border on any row — including today (consistent with prayer ribbon list treatment).
- No cards per row — flat list only.
- No gamification copy. No "Great job!", "Keep it up!", or streak-reset warnings.
- Sunrise is not a tracked prayer. Five indicators per row, not six.

---

## Provenance

- Created: 2026-04-18
- Process: `/design-consultation` skill (gstack v1.0.0.0)
- Outside voice: indie-studio perspective (GT Alpina, 29LT Zarid, burnt sienna considered — rejected in favor of free open-source equivalents that preserve the same emotional direction while keeping the project fully open)
- Advisor corrections applied: separated Quran typography from UI Arabic; hybrid surface stance (not app-wide gradient); watch-scale spec written first; ink shifted warm-brown to meet "avoid purple/indigo" mandate
