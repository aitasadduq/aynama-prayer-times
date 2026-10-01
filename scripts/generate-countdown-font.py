#!/usr/bin/env python3
"""Build the identical tabular Fraunces countdown face for Android and iOS.

Requires fonttools (tested with 4.61.1). The source remains the bundled SIL OFL
Fraunces variable font; this static 400/144 instance contains only countdown
characters. Its family is renamed because the SIL OFL reserves "Fraunces".
"""

import argparse
from io import BytesIO
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ios/App/Resources/Fonts/Fraunces.ttf"
ANDROID_SOURCE = ROOT / "android/app/src/main/res/font/fraunces.ttf"
TARGETS = (
    ROOT / "ios/App/Resources/Fonts/AynamaCountdown.ttf",
    ROOT / "android/app/src/main/res/font/aynama_countdown.ttf",
)
CHARACTERS = "0123456789:- hms"
FAMILY = "Aynama Countdown"
POSTSCRIPT = "AynamaCountdown-Regular"


def build() -> bytes:
    if SOURCE.read_bytes() != ANDROID_SOURCE.read_bytes():
        raise ValueError("Android and iOS source Fraunces fonts differ")
    source = TTFont(SOURCE)
    font = instantiateVariableFont(
        source, {"wght": 400, "opsz": 144, "SOFT": 0, "WONK": 1}, inplace=False
    )

    # The static face needs neither variation tables nor layout substitutions.
    # Removing GPOS also rules out pair kerning that could disturb tabular spacing.
    for table in ("GDEF", "GPOS", "GSUB", "STAT", "DSIG"):
        if table in font:
            del font[table]

    cmap = font.getBestCmap()
    digits = [cmap[ord(character)] for character in "0123456789"]
    advance = max(font["hmtx"][glyph][0] for glyph in digits)
    for glyph in digits:
        old_advance, left_bearing = font["hmtx"][glyph]
        font["hmtx"][glyph] = (advance, left_bearing + (advance - old_advance) // 2)

    options = subset.Options()
    # TrueType hinting can snap *equal* advances to different whole pixels on
    # Android, depending on the digit outline (observed on API 36 at 72 px).
    # The 72 pt display face is large enough that outline scaling is preferable.
    options.hinting = False
    options.name_IDs = ["*"]  # Retain the embedded OFL copyright and licence notice.
    options.name_languages = ["*"]
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes={ord(character) for character in CHARACTERS})
    subsetter.subset(font)
    # Keep grayscale antialiasing but explicitly turn off grid fitting at every
    # size (gasp bit 0), even on renderers that consider automatic hinting.
    font["gasp"].gaspRange = {65535: 0xA}

    name = font["name"]
    names = {
        1: FAMILY,
        2: "Regular",
        3: "Aynama;Countdown;1.0",
        4: FAMILY,
        6: POSTSCRIPT,
        16: FAMILY,
        17: "Regular",
    }
    for name_id, value in names.items():
        name.setName(value, name_id, 3, 1, 0x409)
        name.setName(value, name_id, 1, 0, 0)
    font["OS/2"].usWeightClass = 400
    font.recalcTimestamp = False
    output = BytesIO()
    font.save(output)
    return output.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify committed fonts")
    args = parser.parse_args()
    data = build()
    for target in TARGETS:
        if args.check:
            if target.read_bytes() != data:
                raise SystemExit(f"Outdated countdown font: {target}")
        else:
            target.write_bytes(data)
        print(f"{target.relative_to(ROOT)}: {len(data)} bytes")


if __name__ == "__main__":
    main()
