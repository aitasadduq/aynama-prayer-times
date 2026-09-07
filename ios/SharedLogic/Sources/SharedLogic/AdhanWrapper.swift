import Adhan
import Foundation

/// The calculation methods this app offers, named independently of either Adhan port.
///
/// Deliberately the same ten as Android's `CalculationMethodKey`. Adhan-Swift 1.5.0 also ships
/// `tehran` and `turkey`, which Adhan-Kotlin 1.2.1 does not; offering them on iOS alone would
/// give the two platforms different answers for the same profile, and the cross-platform
/// consistency test exists to stop exactly that. They can be added when both ports have them.
public enum CalculationMethodKey: String, Codable, CaseIterable, Sendable {
    case mwl
    case isna
    case ummAlQura
    case egyptian
    case karachi
    case dubai
    case moonSightingCommittee
    case kuwait
    case qatar
    case singapore
}

/// One day's prayer times as they read on the wall clock of the profile's zone.
///
/// Both Asr times are carried rather than one resolved by madhab, because the widgets and the
/// notification settings show a profile's own school while the tracker sheet can show either,
/// and recalculating to switch would mean holding a `CalculationMethodKey` everywhere.
public struct PrayerTimesResult: Hashable, Sendable {
    public let fajr: ClockTime
    public let sunrise: ClockTime
    public let dhuhr: ClockTime
    public let asrShafii: ClockTime
    public let asrHanafi: ClockTime
    public let maghrib: ClockTime
    public let isha: ClockTime

    public init(
        fajr: ClockTime,
        sunrise: ClockTime,
        dhuhr: ClockTime,
        asrShafii: ClockTime,
        asrHanafi: ClockTime,
        maghrib: ClockTime,
        isha: ClockTime
    ) {
        self.fajr = fajr
        self.sunrise = sunrise
        self.dhuhr = dhuhr
        self.asrShafii = asrShafii
        self.asrHanafi = asrHanafi
        self.maghrib = maghrib
        self.isha = isha
    }
}

public enum PrayerTimesError: Error, Equatable, CustomStringConvertible {

    /// No prayer times exist for this location and date.
    ///
    /// Inside the polar circles the sun can stay continuously above or below the horizon, leaving
    /// sunrise and sunset undefined; Adhan's `PrayerTimes.init` is failable for exactly this and
    /// returns nil, rather than returning some times and not others. The high-latitude rules do
    /// not help — they reshape Fajr and Isha when twilight is not reached, but cannot invent a
    /// sunrise that never happens. Choosing what to display instead (nearest latitude, nearest
    /// day, fixed day proportions) is a convention this app has not decided, so callers must
    /// handle absence rather than assume times.
    ///
    /// Measured with Adhan and MWL, this begins between 65.5°N and 65.75°N around the June
    /// solstice and near 69°N around the December solstice, mirrored in the south.
    case unavailable(latitude: Double, date: CalendarDate)

    case invalidCoordinates(latitude: Double, longitude: Double)

    public var description: String {
        switch self {
        case let .unavailable(latitude, date):
            "No prayer times at latitude \(latitude) on \(date): "
                + "the sun does not both rise and set at this location on this date"
        case let .invalidCoordinates(latitude, longitude):
            "Coordinates out of range: latitude \(latitude) must be in [-90, 90], "
                + "longitude \(longitude) must be in [-180, 180]"
        }
    }
}

/// The app's only door to Adhan-Swift.
///
/// Everything above this line speaks ``PrayerTimesResult`` and ``CalculationMethodKey``; nothing
/// else imports Adhan. That is what makes the pinned-version-plus-test-vectors contract in
/// architecture-design.md enforceable — there is one place where an upstream change can land,
/// and `VectorParityTests` guards it.
public struct AdhanWrapper: Sendable {

    /// Pinned, not left to Adhan-Swift's default — because the two ports' defaults disagree.
    ///
    /// Adhan-Kotlin 1.2.1, which the Android app ships, defaults `highLatitudeRule` to
    /// `MIDDLE_OF_THE_NIGHT` for every location; its `nightPortions()` does not see the
    /// coordinates. Adhan-Swift 1.5.0 leaves the rule nil and falls back to
    /// `HighLatitudeRule.recommended(for:)`, which returns `.seventhOfTheNight` above 48°.
    ///
    /// Left unpinned, London on the June solstice comes out **157 minutes apart** between the two
    /// ports — not a rounding difference, a different answer. Phase 3A makes the tested Android
    /// behaviour the product specification, so iOS matches Android rather than quietly shipping
    /// Adhan upstream's newer recommendation on one platform only.
    ///
    /// This is deliberately *not* a per-profile setting. Whether the product should move both
    /// platforms to seventh-of-the-night above 48° is a real fiqh and UX question — under
    /// middle-of-the-night, London's Fajr and Isha collapse onto the same instant for weeks
    /// around midsummer — but it is one decision for both platforms, not something iOS decides on
    /// its own. See `HighLatitudeParityTests` and TODOS.md.
    /// Computed rather than stored: `HighLatitudeRule` is not `Sendable` in Adhan-Swift 1.5.0,
    /// and a static constant of a non-Sendable type is a shared-mutable-state error under Swift 6.
    /// There is nothing to cache — the enum case is a constant either way.
    static var highLatitudeRule: HighLatitudeRule { .middleOfTheNight }

    public init() {}

    public func prayerTimes(
        latitude: Double,
        longitude: Double,
        date: CalendarDate,
        timeZone: TimeZone,
        method: CalculationMethodKey
    ) throws -> PrayerTimesResult {
        guard (-90.0...90.0).contains(latitude), (-180.0...180.0).contains(longitude) else {
            throw PrayerTimesError.invalidCoordinates(latitude: latitude, longitude: longitude)
        }

        let coordinates = Coordinates(latitude: latitude, longitude: longitude)
        let components = DateComponents(year: date.year, month: date.month, day: date.day)

        var shafiiParams = method.adhanMethod.params
        shafiiParams.madhab = .shafi
        shafiiParams.highLatitudeRule = Self.highLatitudeRule
        var hanafiParams = method.adhanMethod.params
        hanafiParams.madhab = .hanafi
        hanafiParams.highLatitudeRule = Self.highLatitudeRule

        guard
            let shafii = PrayerTimes(
                coordinates: coordinates, date: components, calculationParameters: shafiiParams
            ),
            let hanafi = PrayerTimes(
                coordinates: coordinates, date: components, calculationParameters: hanafiParams
            )
        else {
            throw PrayerTimesError.unavailable(latitude: latitude, date: date)
        }

        return PrayerTimesResult(
            fajr: ClockTime.from(shafii.fajr, in: timeZone),
            sunrise: ClockTime.from(shafii.sunrise, in: timeZone),
            dhuhr: ClockTime.from(shafii.dhuhr, in: timeZone),
            asrShafii: ClockTime.from(shafii.asr, in: timeZone),
            asrHanafi: ClockTime.from(hanafi.asr, in: timeZone),
            maghrib: ClockTime.from(shafii.maghrib, in: timeZone),
            isha: ClockTime.from(shafii.isha, in: timeZone)
        )
    }

    /// Yesterday, today and tomorrow for `profile`, with undefined days dropped.
    ///
    /// The window ``PrayerTimeline`` needs: it must find an event on each side of `now` at every
    /// moment, including the stretch after Isha and the stretch before Fajr. Days are dropped
    /// rather than failing the whole window because near the polar circles a single day can be
    /// undefined while the days around it are fine.
    public func timelineDays(
        for profile: Profile,
        around today: CalendarDate
    ) -> [CalendarDate: PrayerTimesResult] {
        var days: [CalendarDate: PrayerTimesResult] = [:]
        for offset in -1...1 {
            let date = today.plusDays(offset)
            if let times = try? prayerTimes(
                latitude: profile.latitude,
                longitude: profile.longitude,
                date: date,
                timeZone: profile.effectiveTimeZone,
                method: profile.calculationMethod
            ) {
                days[date] = times
            }
        }
        return days
    }
}

private extension CalculationMethodKey {
    var adhanMethod: CalculationMethod {
        switch self {
        case .mwl: .muslimWorldLeague
        case .isna: .northAmerica
        case .ummAlQura: .ummAlQura
        case .egyptian: .egyptian
        case .karachi: .karachi
        case .dubai: .dubai
        case .moonSightingCommittee: .moonsightingCommittee
        case .kuwait: .kuwait
        case .qatar: .qatar
        case .singapore: .singapore
        }
    }
}
