import Foundation

/// What a prayer is called on a given day.
///
/// The only day-dependent name is Dhuhr's: on Friday the congregation prays Jumu'ah in its place,
/// and every surface that shows a specific day's prayer should say so — the app, the widgets, the
/// notifications, the Live Activity, the watch app and its complications (DESIGN.md §20).
///
/// This is a label, not a prayer. Nothing downstream branches on it: Jumu'ah is scheduled,
/// calculated, tracked and notified as Dhuhr, and ``Prayer/dhuhr`` stays the stored value.
/// Duplicating the calculation to carry a different name would be the wrong trade every time.
///
/// Recurring *settings* keep the canonical name. A row that configures the Dhuhr alert governs all
/// seven days, so calling it "Jumuah" because today happens to be Friday would misdescribe what
/// the toggle does. Day-specific displays are day-aware; slot configuration is not.
public let jumuah = "Jumuah"

/// The name for `event` as it falls on `date`.
public func prayerDisplayName(_ event: TimelineEvent, on date: CalendarDate) -> String {
    event == .dhuhr && date.isFriday ? jumuah : event.canonicalName
}

/// The name for `prayer` as it falls on `date`.
public func prayerDisplayName(_ prayer: Prayer, on date: CalendarDate) -> String {
    prayer == .dhuhr && date.isFriday ? jumuah : prayer.canonicalName
}

extension TimelineEvent {
    /// The day-independent name, for surfaces that configure a prayer across every day rather
    /// than displaying one occurrence of it.
    public var canonicalName: String {
        switch self {
        case .fajr: "Fajr"
        case .sunrise: "Sunrise"
        case .dhuhr: "Dhuhr"
        case .asr: "Asr"
        case .maghrib: "Maghrib"
        case .isha: "Isha"
        }
    }
}

extension Prayer {
    /// - SeeAlso: ``TimelineEvent/canonicalName``
    public var canonicalName: String {
        switch self {
        case .fajr: "Fajr"
        case .dhuhr: "Dhuhr"
        case .asr: "Asr"
        case .maghrib: "Maghrib"
        case .isha: "Isha"
        }
    }
}

/// The three-letter form the widgets use, derived from the displayed name rather than mapped per
/// prayer — so `JUM` on a Friday, and so the abbreviation cannot drift from the label it
/// abbreviates (DESIGN.md §20).
public func prayerAbbreviation(_ event: TimelineEvent, on date: CalendarDate) -> String {
    String(prayerDisplayName(event, on: date).prefix(3)).uppercased()
}

/// - SeeAlso: ``prayerAbbreviation(_:on:)``
public func prayerAbbreviation(_ prayer: Prayer, on date: CalendarDate) -> String {
    String(prayerDisplayName(prayer, on: date).prefix(3)).uppercased()
}
