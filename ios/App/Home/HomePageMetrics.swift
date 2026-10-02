import CoreGraphics

/// Keeps the complete prayer list above the profile controls on every phone viewport.
/// Text still starts from Dynamic Type sizes; the whole Home composition then shrinks
/// together when those sizes cannot fit the available page height or width.
struct HomePageMetrics {
    let scale: CGFloat
    let horizontalPadding: CGFloat
    let topPadding: CGFloat
    let bottomPadding: CGFloat
    let headerGap: CGFloat
    let heroGap: CGFloat
    let qazaGap: CGFloat
    let heroLineGap: CGFloat
    let headerStacked: Bool
    let headerFont: CGFloat
    let heroFont: CGFloat
    let subtitleFont: CGFloat
    let rowFont: CGFloat
    let markFont: CGFloat
    let rowHeight: CGFloat
    let qazaFont: CGFloat
    let occupiedHeight: CGFloat

    init(size: CGSize, rows: Int, hasQaza: Bool,
         headerSize: CGFloat, heroSize: CGFloat, subtitleSize: CGFloat,
         rowSize: CGFloat, qazaSize: CGFloat) {
        let count = max(rows, 1)
        let padding = min(24, size.width * 0.06)
        let stacked = size.width < 350
        let header = min(headerSize, 26)
        let hero = min(heroSize, 144)
        let subtitle = min(subtitleSize, 64)
        let row = min(rowSize, 40)
        let qaza = min(qazaSize, 26)

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
