import Foundation
import Testing

@testable import SharedLogic

/// A case-for-case port of `HomeRibbonStateTest.kt`.
///
/// The ribbon's passed/current/upcoming states and the time-of-day phase are wall-clock logic,
/// not instant logic — a phase is a property of the rendered day. The cases that matter are the
/// ones where clock order and timeline order come apart: a post-midnight Isha sorts before Fajr
/// on a clock and after Maghrib in reality, and both derivations have to agree with the
/// instant-based countdown across that boundary.
@Suite("Prayer day state")
struct PrayerDayStateTests {

    /// A Monday: these cases are about ribbon state, not day-dependent naming.
    let monday = CalendarDate(year: 2026, month: 5, day: 11)
    let friday = CalendarDate(year: 2026, month: 5, day: 15)

    /// The tests assert state and naming, never localisation, so the formatter is the identity.
    let format: (ClockTime) -> String = { String(format: "%02d:%02d", $0.hour, $0.minute) }

    let sample = PrayerTimesResult(
        fajr: ClockTime(hour: 4, minute: 30),
        sunrise: ClockTime(hour: 6, minute: 10),
        dhuhr: ClockTime(hour: 12, minute: 15),
        asrShafii: ClockTime(hour: 15, minute: 45),
        asrHanafi: ClockTime(hour: 16, minute: 30),
        maghrib: ClockTime(hour: 19, minute: 50),
        isha: ClockTime(hour: 21, minute: 20)
    )

    /// London in summer: Isha at 00:25 belongs to the following calendar day.
    let postMidnightIsha = PrayerTimesResult(
        fajr: ClockTime(hour: 5, minute: 25),
        sunrise: ClockTime(hour: 7, minute: 44),
        dhuhr: ClockTime(hour: 13, minute: 0),
        asrShafii: ClockTime(hour: 16, minute: 45),
        asrHanafi: ClockTime(hour: 18, minute: 0),
        maghrib: ClockTime(hour: 22, minute: 14),
        isha: ClockTime(hour: 0, minute: 25)
    )

    func rows(
        _ times: PrayerTimesResult,
        _ madhab: AsrMadhab = .shafii,
        at hour: Int,
        _ minute: Int,
        on date: CalendarDate? = nil,
        ramadan: Bool = false
    ) -> [RibbonRow] {
        deriveRibbonRows(
            times,
            asrMadhab: madhab,
            now: ClockTime(hour: hour, minute: minute),
            date: date ?? monday,
            isRamadan: ramadan,
            formatter: format
        )
    }

    func state(_ rows: [RibbonRow], of prayer: Prayer) -> RibbonState? {
        for case let .prayer(candidate, _, _, state) in rows where candidate == prayer { return state }
        return nil
    }

    // MARK: - Ribbon state

    @Test("before Fajr every prayer is upcoming")
    func beforeFajr() {
        let rows = rows(sample, at: 3, 0)
        for prayer in Prayer.allCases {
            #expect(state(rows, of: prayer) == .upcoming, "\(prayer)")
        }
    }

    @Test("after Fajr and before Dhuhr, Fajr is current and the rest upcoming")
    func afterFajr() {
        let rows = rows(sample, at: 8, 0)
        #expect(state(rows, of: .fajr) == .current)
        for prayer in [Prayer.dhuhr, .asr, .maghrib, .isha] {
            #expect(state(rows, of: prayer) == .upcoming, "\(prayer)")
        }
    }

    @Test("after Dhuhr and before Asr, Fajr has passed and Dhuhr is current")
    func afterDhuhr() {
        let rows = rows(sample, at: 14, 0)
        #expect(state(rows, of: .fajr) == .passed)
        #expect(state(rows, of: .dhuhr) == .current)
        #expect(state(rows, of: .asr) == .upcoming)
    }

    @Test("after Isha, everything before it has passed and Isha is current")
    func afterIsha() {
        let rows = rows(sample, at: 22, 0)
        for prayer in [Prayer.fajr, .dhuhr, .asr, .maghrib] {
            #expect(state(rows, of: prayer) == .passed, "\(prayer)")
        }
        #expect(state(rows, of: .isha) == .current)
    }

    @Test("the Hanafi madhab uses the Hanafi Asr time")
    func hanafiAsr() {
        // Hanafi Asr is 16:30, Shafi'i 15:45. At 16:00 the Shafi'i Asr is current and the
        // Hanafi one has not arrived.
        #expect(state(rows(sample, .shafii, at: 16, 0), of: .asr) == .current)
        #expect(state(rows(sample, .hanafi, at: 16, 0), of: .asr) == .upcoming)
    }

    // MARK: - Ramadan

    @Test("Ramadan adds an Imsak row before Fajr")
    func ramadanImsak() {
        let rows = rows(sample, at: 4, 0, ramadan: true)
        guard case let .imsak(_, isPast) = rows[0] else {
            Issue.record("first row is not Imsak: \(rows[0])")
            return
        }
        // Imsak is Fajr (04:30) minus 10 minutes = 04:20; now is 04:00.
        #expect(!isPast)
    }

    @Test("the Imsak row is marked past once its time has gone")
    func imsakPast() {
        let rows = rows(sample, at: 4, 25, ramadan: true)
        guard case let .imsak(_, isPast) = rows[0] else {
            Issue.record("first row is not Imsak: \(rows[0])")
            return
        }
        #expect(isPast)
    }

    @Test("the row order is Imsak, Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha in Ramadan")
    func rowOrder() {
        let rows = rows(sample, at: 10, 0, ramadan: true)
        #expect(rows.count == 7)
        #expect(rows.map(\.id) == [
            "imsak", "prayer-fajr", "sunrise", "prayer-dhuhr",
            "prayer-asr", "prayer-maghrib", "prayer-isha",
        ])
    }

    @Test("the sunrise row is always present and is never a prayer")
    func sunriseRow() {
        let rows = rows(sample, at: 10, 0)
        let sunriseRows = rows.filter { if case .sunrise = $0 { true } else { false } }
        #expect(sunriseRows.count == 1)
        // It has no state to be "current" in — that is what makes it a reference, not an act.
        #expect(rows.count == 6)
    }

    // MARK: - Post-midnight Isha

    @Test("in the evening a post-midnight Isha is upcoming and Maghrib is current")
    func postMidnightEvening() {
        let rows = rows(postMidnightIsha, at: 22, 15)
        #expect(state(rows, of: .maghrib) == .current)
        #expect(state(rows, of: .isha) == .upcoming)
    }

    @Test("after a post-midnight Isha passes it is current and the daytime prayers have passed")
    func postMidnightAfter() {
        let rows = rows(postMidnightIsha, at: 1, 0)
        for prayer in [Prayer.fajr, .dhuhr, .asr, .maghrib] {
            #expect(state(rows, of: prayer) == .passed, "\(prayer)")
        }
        #expect(state(rows, of: .isha) == .current)
    }

    @Test("between midnight and a post-midnight Isha, Maghrib is still current")
    func postMidnightBetween() {
        let rows = rows(postMidnightIsha, at: 0, 5)
        #expect(state(rows, of: .maghrib) == .current)
        #expect(state(rows, of: .isha) == .upcoming)
    }

    // MARK: - Phase

    @Test("the phase maps each prayer window")
    func phaseWindows() {
        func phase(_ hour: Int, _ minute: Int) -> PrayerPhase {
            derivePhase(sample, asrMadhab: .shafii, now: ClockTime(hour: hour, minute: minute))
        }
        #expect(phase(3, 0) == .isha)
        #expect(phase(5, 0) == .fajr)
        #expect(phase(9, 0) == .sunriseTransition)
        #expect(phase(13, 0) == .dhuhr)
        #expect(phase(17, 0) == .asr)
        #expect(phase(20, 30) == .maghrib)
        #expect(phase(22, 0) == .isha)
    }

    @Test("a post-midnight Isha keeps the phase on Maghrib until it passes")
    func postMidnightPhase() {
        func phase(_ hour: Int, _ minute: Int) -> PrayerPhase {
            derivePhase(postMidnightIsha, asrMadhab: .shafii, now: ClockTime(hour: hour, minute: minute))
        }
        #expect(phase(22, 15) == .maghrib)
        #expect(phase(0, 5) == .maghrib)
        #expect(phase(1, 0) == .isha)
    }

    // MARK: - Friday naming (DESIGN.md §20)

    @Test("the ribbon names Friday's Dhuhr as Jumuah")
    func ribbonNamesJumuah() {
        let rows = rows(sample, at: 10, 0, on: friday)
        var names: [String] = []
        var prayers: [Prayer] = []
        for case let .prayer(prayer, displayName, _, _) in rows {
            names.append(displayName)
            prayers.append(prayer)
        }
        #expect(names == ["Fajr", "Jumuah", "Asr", "Maghrib", "Isha"])
        // The label changes; the stored prayer does not.
        #expect(prayers[1] == .dhuhr)
    }

    @Test("the ribbon keeps Dhuhr on every other day")
    func ribbonKeepsDhuhr() {
        let rows = rows(sample, at: 10, 0)
        for case let .prayer(prayer, displayName, _, _) in rows where prayer == .dhuhr {
            #expect(displayName == "Dhuhr")
        }
    }

    @Test("the phase label follows the same Friday rule")
    func phaseLabelFollowsFriday() {
        // The Qibla screen names its time-of-day band after the prayer that opened it.
        #expect(phaseDisplayName(.dhuhr, on: friday) == "Jumuah")
        #expect(phaseDisplayName(.dhuhr, on: monday) == "Dhuhr")
        #expect(phaseDisplayName(.sunriseTransition, on: friday) == "Sunrise")
        #expect(phaseDisplayName(.asr, on: friday) == "Asr")
    }
}

/// `HijriCalendar` has no Android test file to port — `RamadanDetector` is exercised through
/// `RamadanDetectionTest` on a device. These pin the parts the offset semantics in DESIGN.md §18
/// actually turn on, which is where an off-by-one would be invisible until Ramadan.
@Suite("Hijri calendar")
struct HijriCalendarTests {

    let london = TimeZone(identifier: "Europe/London")!

    @Test("a positive offset advances the calendar and a negative one delays it")
    func offsetDirection() {
        // Offsetting by a day must move the reported day by one, in the stated direction.
        let date = CalendarDate(year: 2026, month: 3, day: 1)
        let base = HijriCalendar.displayString(date, in: london)
        let advanced = HijriCalendar.displayString(date, offsetDays: 1, in: london)
        let delayed = HijriCalendar.displayString(date, offsetDays: -1, in: london)
        #expect(base != advanced)
        #expect(base != delayed)
        #expect(advanced != delayed)
    }

    @Test("the offset expires once the perceived month changes")
    func offsetExpires() {
        let date = CalendarDate(year: 2026, month: 3, day: 1)
        let key = HijriCalendar.monthKey(date.plusDays(1), in: london)

        // Same month it was set for: the offset stands.
        #expect(HijriCalendar.effectiveOffset(1, monthKey: key, on: date, in: london) == 1)

        // A key from a different month: the offset has expired and must not linger.
        #expect(HijriCalendar.effectiveOffset(1, monthKey: key + 1, on: date, in: london) == 0)

        // A zero offset is never "in effect", whatever the key says.
        #expect(HijriCalendar.effectiveOffset(0, monthKey: key, on: date, in: london) == 0)
    }

    @Test("the month key is monotonic across a Hijri year boundary")
    func monthKeyMonotonic() {
        // year * 12 + month has to keep increasing when the year rolls over, or an offset set in
        // Dhu al-Hijjah would survive into Muharram.
        var previous = Int.min
        for offset in stride(from: 0, through: 420, by: 15) {
            let key = HijriCalendar.monthKey(
                CalendarDate(year: 2026, month: 1, day: 1).plusDays(offset), in: london
            )
            #expect(key >= previous)
            previous = key
        }
    }

    @Test("Ramadan is the ninth month and is detected there and nowhere else")
    func ramadanDetection() {
        // Walk a whole Hijri year a week at a time; exactly the stretch whose month key says
        // Ramadan must report true.
        let start = CalendarDate(year: 2026, month: 1, day: 1)
        var ramadanDays = 0
        var otherDays = 0
        for offset in stride(from: 0, through: 380, by: 7) {
            let date = start.plusDays(offset)
            let isNinth = HijriCalendar.monthKey(date, in: london) % 12 == HijriCalendar.ramadanMonth
            #expect(HijriCalendar.isRamadan(date, in: london) == isNinth, "\(date)")
            if isNinth { ramadanDays += 1 } else { otherDays += 1 }
        }
        // Without these the loop above passes just as happily if nothing is ever Ramadan.
        #expect(ramadanDays > 0, "a Hijri year with no Ramadan in it")
        #expect(otherDays > 0)
    }

    @Test("Ramadan 1447 falls where the Hijri calendar says it does")
    func ramadan1447() {
        // 1 Ramadan 1447 AH is 2026-02-18 on the tabular (islamic-civil) calendar — the same
        // calendar `android.icu.util.IslamicCalendar` defaults to, which is why the two
        // platforms agree on whether the Imsak row is showing without anyone syncing them.
        #expect(HijriCalendar.isRamadan(CalendarDate(year: 2026, month: 2, day: 18), in: london))
        #expect(!HijriCalendar.isRamadan(CalendarDate(year: 2026, month: 2, day: 17), in: london))
        #expect(HijriCalendar.displayString(CalendarDate(year: 2026, month: 2, day: 18), in: london)
            == "1 Ramaḍān 1447")
    }
}
