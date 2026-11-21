import CoreGraphics

/// Keeps the complete prayer list above the profile controls on every phone viewport.
/// Text still starts from Dynamic Type sizes; the whole Home composition then shrinks
/// together when those sizes cannot fit the available page height or width.
///
/// A wide window (the iPhone Duo's inner screen) gets a two-column spread instead: the hero
/// grows to fill its column and the prayer rows share the full page height.
struct HomePageMetrics {
    let isWide: Bool
    let scale: CGFloat
    let horizontalPadding: CGFloat
    let topPadding: CGFloat
    let bottomPadding: CGFloat
    let headerGap: CGFloat
    let heroGap: CGFloat
    let qazaGap: CGFloat
    let heroLineGap: CGFloat
    let columnGap: CGFloat
    let ribbonWidth: CGFloat
    let headerStacked: Bool
    let headerFont: CGFloat
    let heroFont: CGFloat
    let subtitleFont: CGFloat
    let rowFont: CGFloat
    let markFont: CGFloat
    let rowHeight: CGFloat
    let qazaFont: CGFloat
    let occupiedHeight: CGFloat

    init(size: CGSize, rows: Int, hasQaza: Bool, wide: Bool = false,
         headerSize: CGFloat, heroSize: CGFloat, subtitleSize: CGFloat,
         rowSize: CGFloat, qazaSize: CGFloat) {
        let count = max(rows, 1)
        let header = min(headerSize, 26)
        let hero = min(heroSize, 144)
        let subtitle = min(subtitleSize, 64)
        let row = min(rowSize, 40)
        let qaza = min(qazaSize, 26)
        isWide = wide

        if wide {
            let padding: CGFloat = 40
            let gap: CGFloat = 56
            let column = max(0, (size.width - 2 * padding - gap) / 2)
            let top: CGFloat = 24
            let bottom: CGFloat = 12
            let headerLine = header * 1.15
            let headerBox = headerLine * 1.5
            let body = max(0, size.height - top - bottom - headerBox - 24)

            // The countdown reads as one line about 3.8 em wide; let it fill its column.
            let heroTarget = min(144, max(hero, column / 3.8))
            let qazaLine = qaza * 1.15
            let leftNeed = heroTarget * 1.2 + heroTarget * 0.44 * 1.25 + (hasQaza ? qazaLine * 1.6 + 16 : 0) + 12
            let heroFit = min(1, body / max(leftNeed, 1))
            let heroSizeFinal = heroTarget * heroFit

            let rowTarget = min(row * 1.3, 32)
            let rowSlot = body / CGFloat(count)
            let rowFit = min(1, rowSlot / max(rowTarget * 2, 1))

            scale = heroSizeFinal / max(hero, 1)
            horizontalPadding = padding
            topPadding = top
            bottomPadding = bottom
            headerGap = 24
            heroGap = 16
            qazaGap = 0
            heroLineGap = 8 * heroFit
            columnGap = gap
            ribbonWidth = min(column, 440)
            headerStacked = false
            headerFont = headerLine
            heroFont = heroSizeFinal
            subtitleFont = min(64, max(subtitle, heroSizeFinal * 0.44))
            rowFont = rowTarget * rowFit
            markFont = min(20, qazaSize * 1.3) * rowFit
            qazaFont = qazaLine
            rowHeight = rowSlot
            occupiedHeight = top + headerBox + 24 + CGFloat(count) * rowSlot + bottom
            return
        }

        let padding = min(24, size.width * 0.06)
        let stacked = size.width < 350

        // Reserve text line boxes, not just nominal point sizes. Two header lines are
        // budgeted on narrow phones; the 7th Ramadan row is included in `count`.
        let headerBox = header * (stacked ? 3 : 1.5)
        let heroBox = hero * 1.2 + 6
        let subtitleBox = subtitle * 1.25
        let qazaBox = hasQaza ? qaza * 1.6 : 0
        let gaps: CGFloat = 16 + 16 + 18 + (hasQaza ? 12 : 0) + 12
        let rowBox = max(44, row * 2)
        let desired = headerBox + heroBox + subtitleBox + qazaBox + gaps + CGFloat(count) * rowBox
        let heightFit = size.height / max(desired, 1)
        let widthFit = (size.width - 2 * padding) / 340
        let fit = min(1, max(0.1, heightFit), max(0.1, widthFit))

        scale = fit
        horizontalPadding = padding
        topPadding = 16 * fit
        bottomPadding = 12 * fit
        headerGap = 16 * fit
        heroGap = 18 * fit
        qazaGap = hasQaza ? 12 * fit : 0
        heroLineGap = 6 * fit
        columnGap = 0
        ribbonWidth = .infinity
        headerStacked = stacked
        headerFont = header * fit
        heroFont = hero * fit
        subtitleFont = subtitle * fit
        rowFont = row * fit
        markFont = min(17, qazaSize) * fit
        qazaFont = qaza * fit

        let fixed = (headerBox + heroBox + subtitleBox + qazaBox + gaps) * fit
        rowHeight = min(112, max(0, (size.height - fixed) / CGFloat(count)))
        occupiedHeight = fixed + CGFloat(count) * rowHeight
    }
}
