# aynama-prayer-times

Open-source Muslim Prayer & Spiritual Companion app. Android + iOS + watchOS + WearOS. Architecture approach is "fully independent native projects + shared JSON test vectors" (see `architecture-design.md`).

## Design system

**Read `DESIGN.md` before any UI work.** It governs color, typography, composition, watch scale, and the Two Deliberate Departures (typographic Qibla, prayer timeline ribbon). If a proposed surface contradicts DESIGN.md, update DESIGN.md first, then the code.

Hard rules (non-negotiable):
- No circular progress rings. Anywhere.
- No mosque-green, emerald, teal, purple, or indigo in the palette.
- No Arabic calligraphy used decoratively.
- No system-default sans (Inter, Roboto, Arial) as final choice — use Fraunces + IBM Plex.
- Watch scale uses only 3 tokens: ink, parchment, saffron.

## Skill routing

When the user's request matches an available skill, ALWAYS invoke it using the Skill
tool as your FIRST action. Do NOT answer directly, do NOT use other tools first.
The skill has specialized workflows that produce better results than ad-hoc answers.

Key routing rules:
- Product ideas, "is this worth building", brainstorming → invoke office-hours
- Bugs, errors, "why is this broken", 500 errors → invoke investigate
- Ship, deploy, push, create PR → invoke ship
- QA, test the site, find bugs → invoke qa
- Code review, check my diff → invoke review
- Update docs after shipping → invoke document-release
- Weekly retro → invoke retro
- Design system, brand → invoke design-consultation
- Visual audit, design polish → invoke design-review
- Architecture review → invoke plan-eng-review
- Save progress, checkpoint, resume → invoke checkpoint
- Code quality, health check → invoke health
