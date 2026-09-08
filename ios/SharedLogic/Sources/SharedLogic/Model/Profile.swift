import Foundation

/// Which school's shadow ratio fixes Asr. Mirrors the Android `AsrMadhab`.
public enum AsrMadhab: String, Codable, CaseIterable, Sendable {
    case shafii
    case hanafi
}

/// The five obligatory prayers. Sunrise is deliberately absent — it is a boundary, not a prayer,
/// and the tracker has no row for it. ``TimelineEvent`` is the type that includes it.
public enum Prayer: String, Codable, CaseIterable, Sendable {
    case fajr
    case dhuhr
    case asr
    case maghrib
    case isha
}

/// Qaza tracker state for one prayer on one day. Mirrors the Android `QazaStatus`.
public enum QazaStatus: String, Codable, CaseIterable, Sendable {
    case prayedOnTime
    case missed
    case madeUp
    case intentionToMakeUp
}

/// A saved location and its calculation settings — the "weather app location" of DESIGN.md §5.
///
/// A plain value type. Persistence is the app target's business; the domain only ever reads
/// these fields, so widgets, the watch app and the tests can build one without a store.
public struct Profile: Identifiable, Hashable, Codable, Sendable {

    public var id: Int64
    public var name: String
    public var latitude: Double
    public var longitude: Double
    public var calculationMethod: CalculationMethodKey
    public var asrMadhab: AsrMadhab
    public var isGps: Bool
    public var sortOrder: Int
    public var timezone: String
    public var useLocationTimezone: Bool
    public var hijriOffset: Int
    /// Adjusted-calendar Hijri month (year * 12 + month) the offset was set for; the offset
    /// auto-expires once the perceived month changes. 0 = none.
    public var hijriOffsetMonthKey: Int

    public init(
        id: Int64 = 0,
        name: String,
        latitude: Double,
        longitude: Double,
        calculationMethod: CalculationMethodKey,
        asrMadhab: AsrMadhab,
        isGps: Bool = false,
        sortOrder: Int = 0,
        timezone: String = "",
        useLocationTimezone: Bool = false,
        hijriOffset: Int = 0,
        hijriOffsetMonthKey: Int = 0
    ) {
        self.id = id
        self.name = name
        self.latitude = latitude
        self.longitude = longitude
        self.calculationMethod = calculationMethod
        self.asrMadhab = asrMadhab
        self.isGps = isGps
        self.sortOrder = sortOrder
        self.timezone = timezone
        self.useLocationTimezone = useLocationTimezone
        self.hijriOffset = hijriOffset
        self.hijriOffsetMonthKey = hijriOffsetMonthKey
    }

    /// The zone this profile's prayer day is resolved in.
    ///
    /// Falls back to the device zone when the profile does not pin one, matching Android's
    /// `effectiveZoneId()`. A pinned zone that no longer exists in the tz database falls back
    /// too rather than trapping — a stale identifier must not take the pager down.
    public var effectiveTimeZone: TimeZone {
        guard useLocationTimezone, !timezone.isEmpty, let zone = TimeZone(identifier: timezone) else {
            return TimeZone.current
        }
        return zone
    }
}
