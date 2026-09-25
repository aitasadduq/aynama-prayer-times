import CoreText
import SwiftUI
import UIKit

/// DESIGN.md §4's type scale, resolved against the two variable fonts the app ships.
///
/// Both faces are variable, and both default to something the design does not want: Fraunces
/// ships `wght 900, opsz 9` (Black, caption-optical) and IBM Plex Sans ships `wdth 100, wght 400`.
/// `Font.custom(_:size:)` instantiates a family's *default* instance, so asking for "Fraunces"
/// by name would put 9pt Black on the 72pt countdown hero. The axes have to be set explicitly,
/// through a `CTFontDescriptor` variation dictionary — the iOS counterpart of the
/// `FontVariation.Setting` calls in `AynamaTypography.kt`, and the same axis values.
///
/// Sizes scale with Dynamic Type through `display-xl` down to `body-sm` (§11), so every style
/// goes through `UIFontMetrics` rather than being a fixed point size.
enum AynamaFont {

    // MARK: - Display (Fraunces)

    /// 72pt / 1.0 — the countdown hero, and nothing else.
    static let displayXL = fraunces(size: 72, weight: 400, opticalSize: 144, textStyle: .largeTitle)

    /// 48pt / 1.05 — screen headers.
    static let displayLG = fraunces(size: 48, weight: 500, opticalSize: 96, textStyle: .largeTitle)

    /// 32pt / 1.1 — card headers, and the prayer name under the countdown.
    static let displayMD = fraunces(size: 32, weight: 500, opticalSize: 48, textStyle: .title1)

    /// 20pt / 1.25 — section titles.
    static let title = fraunces(size: 20, weight: 500, opticalSize: 20, textStyle: .title3)

    // MARK: - Body (IBM Plex Sans)

    /// 17pt / 1.45 — primary reading.
    static let bodyLG = plex(size: 17, weight: 400, textStyle: .body)

    /// 15pt / 1.45 — default UI.
    static let body = plex(size: 15, weight: 400, textStyle: .subheadline)

    /// 13pt / 1.4 — metadata, captions.
    static let bodySM = plex(size: 13, weight: 500, textStyle: .footnote)

    /// 17pt / 1.0, tabular — the prayer time grid.
    ///
    /// Tabular figures are non-negotiable in §4: prayer times sit in a column and must not
    /// shuffle sideways as the digits change.
    static let monoNum = plex(size: 17, weight: 500, textStyle: .body, tabular: true)

    // MARK: - Construction

    /// Fraunces' four axes. `SOFT` and `WONK` stay at the family defaults — the design uses
    /// Fraunces for its ball terminals, not for its wonk.
    private static func fraunces(
        size: CGFloat,
        weight: CGFloat,
        opticalSize: CGFloat,
        textStyle: UIFont.TextStyle
    ) -> Font {
        variableFont(
            named: "Fraunces",
            size: size,
            textStyle: textStyle,
            axes: [axisTag("wght"): weight, axisTag("opsz"): opticalSize]
        )
    }

    private static func plex(
        size: CGFloat,
        weight: CGFloat,
        textStyle: UIFont.TextStyle,
        tabular: Bool = false
    ) -> Font {
        variableFont(
            named: "IBM Plex Sans",
            size: size,
            textStyle: textStyle,
            axes: [axisTag("wght"): weight],
            tabular: tabular
        )
    }

    private static func variableFont(
        named family: String,
        size: CGFloat,
        textStyle: UIFont.TextStyle,
        axes: [Int: CGFloat],
        tabular: Bool = false
    ) -> Font {
        var attributes: [UIFontDescriptor.AttributeName: Any] = [
            .family: family,
            kCTFontVariationAttribute as UIFontDescriptor.AttributeName: axes,
        ]
        if tabular {
            // Tabular figures are a feature selector, not an axis: type 6 (kNumberSpacingType),
            // selector 0 (kMonospacedNumbersSelector).
            attributes[.featureSettings] = [
                [
                    UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                    UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector,
                ]
            ]
        }
        let descriptor = UIFontDescriptor(fontAttributes: attributes)
        let base = UIFont(descriptor: descriptor, size: size)
        // Dynamic Type, capped so `display-xl` at an accessibility size does not push the prayer
        // name off the screen entirely. §12 asks for Dynamic Type, not for unbounded growth.
        let scaled = UIFontMetrics(forTextStyle: textStyle).scaledFont(for: base, maximumPointSize: size * 1.6)
        return Font(scaled)
    }

    /// A four-character axis tag as the integer CoreText wants ('wght' → 0x77676874).
    private static func axisTag(_ tag: String) -> Int {
        tag.utf8.reduce(0) { ($0 << 8) | Int($1) }
    }
}

extension Text {
    /// `-00:12:35` and friends. Tabular numerals plus a fixed width per digit so the hero does
    /// not jitter every second (§4, §8 — the countdown ticks every second, always).
    func countdownNumerals() -> some View {
        self.monospacedDigit()
    }
}
