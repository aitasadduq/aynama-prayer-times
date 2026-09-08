import Foundation

/// The Hijri date, Ramadan detection, and the user's sighting offset (DESIGN.md §18).
///
/// A port of `RamadanDetector.kt`. Android reads `android.icu.util.IslamicCalendar`; Foundation's
/// `.islamicCivil` is the same tabular calendar ICU calls `islamic-civil`, so the two agree by
/// construction rather than by coincidence. `.islamicUmmAlQura` is deliberately *not* used: it
/// would put iOS a day off Android for the same civil date, and cross-platform agreement on
/// "is it Ramadan" is what the offset in §18 exists to let the user correct.
public enum HijriCalendar {

    /// Ramadan is the ninth month; `Calendar` numbers months from 1.
    public static let ramadanMonth = 9

    public static let monthNames = [
        "Muḥarram", "Ṣafar", "Rabīʻ I", "Rabīʻ II",
        "Jumādā I", "Jumādā II", "Rajab", "Shaʻbān",
        "Ramaḍān", "Shawwāl", "Dhu al-Qaʻdah", "Dhu al-Ḥijjah",
    ]

    /// A positive offset advances the Hijri calendar — the month starts earlier, because the moon
    /// was sighted the night before the calculated date. A negative offset delays it.
    public static func isRamadan(_ date: CalendarDate, offsetDays: Int = 0, in zone: TimeZone) -> Bool {
        components(date.plusDays(offsetDays), in: zone).month == ramadanMonth
    }

    public static func currentHijriYear(in zone: TimeZone = .current) -> Int {
        components(CalendarDate.from(.now, in: zone), in: zone).year ?? 0
    }

    /// Stable identifier for a Hijri month, `year * 12 + month`.
    public static func monthKey(_ date: CalendarDate, in zone: TimeZone) -> Int {
        let c = components(date, in: zone)
        return (c.year ?? 0) * 12 + (c.month ?? 0)
    }

    /// The offset applies only while the adjusted (perceived) Hijri month still matches the month
    /// it was set for; once a new month begins it auto-expires to 0. Evaluated with the stored
    /// offset so the month comparison does not depend on its own result.
    public static func effectiveOffset(_ offset: Int, monthKey storedKey: Int, on date: CalendarDate, in zone: TimeZone) -> Int {
        guard offset != 0, monthKey(date.plusDays(offset), in: zone) == storedKey else { return 0 }
        return offset
    }

    public static func displayString(_ date: CalendarDate, offsetDays: Int = 0, in zone: TimeZone) -> String {
        let c = components(date.plusDays(offsetDays), in: zone)
        guard let day = c.day, let month = c.month, let year = c.year,
              monthNames.indices.contains(month - 1)
        else { return "" }
        return "\(day) \(monthNames[month - 1]) \(year)"
    }

    /// Noon in the target zone unambiguously falls within `date`, regardless of how the Hijri
    /// calendar's own day boundaries land — this is what avoids an off-by-one near the date line.
    private static func components(_ date: CalendarDate, in zone: TimeZone) -> DateComponents {
        let instant = date.atTime(ClockTime(hour: 12, minute: 0), in: zone)
        var hijri = Calendar(identifier: .islamicCivil)
        hijri.timeZone = zone
        return hijri.dateComponents([.year, .month, .day], from: instant)
    }
}
