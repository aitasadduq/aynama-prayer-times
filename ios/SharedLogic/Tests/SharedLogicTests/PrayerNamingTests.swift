import Foundation
import Testing

@testable import SharedLogic

/// A case-for-case port of `PrayerNamingTest.kt`, plus the widget abbreviation DESIGN.md §20 asks
/// for (`JUM`, not `DHU`) which Android derives inside its widget code.
@Suite("Friday naming")
struct PrayerNamingTests {

    let friday = CalendarDate(year: 2026, month: 5, day: 15)
    var thursday: CalendarDate { friday.minusDays(1) }
    var saturday: CalendarDate { friday.plusDays(1) }

    @Test("the fixture dates are the days they claim to be")
    func fixtureDates() {
        #expect(friday.dayOfWeek == 5)
        #expect(thursday.dayOfWeek == 4)
        #expect(saturday.dayOfWeek == 6)
        #expect(friday.isFriday)
        #expect(!thursday.isFriday)
        #expect(!saturday.isFriday)
    }

    @Test("Friday's Dhuhr is Jumuah")
    func fridayDhuhrIsJumuah() {
        #expect(prayerDisplayName(TimelineEvent.dhuhr, on: friday) == jumuah)
        #expect(prayerDisplayName(Prayer.dhuhr, on: friday) == jumuah)
    }

    @Test("every other day keeps Dhuhr")
    func everyOtherDayKeepsDhuhr() {
        #expect(prayerDisplayName(TimelineEvent.dhuhr, on: thursday) == "Dhuhr")
        #expect(prayerDisplayName(TimelineEvent.dhuhr, on: saturday) == "Dhuhr")
        #expect(prayerDisplayName(Prayer.dhuhr, on: thursday) == "Dhuhr")
        #expect(prayerDisplayName(Prayer.dhuhr, on: saturday) == "Dhuhr")
    }

    @Test("no other prayer changes on Friday")
    func noOtherPrayerChanges() {
        let unchanged: [(TimelineEvent, String)] = [
            (.fajr, "Fajr"), (.sunrise, "Sunrise"), (.asr, "Asr"),
            (.maghrib, "Maghrib"), (.isha, "Isha"),
        ]
        for (event, name) in unchanged {
            #expect(prayerDisplayName(event, on: friday) == name)
        }
        for prayer in Prayer.allCases where prayer != .dhuhr {
            #expect(prayerDisplayName(prayer, on: friday) == prayer.canonicalName)
        }
    }

    @Test("canonical names stay day-independent")
    func canonicalNamesStayDayIndependent() {
        // Recurring settings rows use these: a row that governs all seven days must not be
        // renamed because today happens to be Friday.
        #expect(TimelineEvent.dhuhr.canonicalName == "Dhuhr")
        #expect(Prayer.dhuhr.canonicalName == "Dhuhr")
    }

    // MARK: - Through the timeline

    let zone = TimeZone(identifier: "Europe/London")!

    let day = PrayerTimesResult(
        fajr: ClockTime(hour: 3, minute: 20),
        sunrise: ClockTime(hour: 5, minute: 12),
        dhuhr: ClockTime(hour: 12, minute: 58),
        asrShafii: ClockTime(hour: 17, minute: 5),
        asrHanafi: ClockTime(hour: 18, minute: 20),
        maghrib: ClockTime(hour: 20, minute: 40),
        isha: ClockTime(hour: 21, minute: 45)
    )

    @Test("timeline entries name themselves by their own day")
    func entriesNameThemselvesByTheirOwnDay() {
        let timeline = buildTimeline(
            days: [thursday: day, friday: day, saturday: day],
            asrMadhab: .shafii,
            timeZone: zone
        )
        let dhuhrNames = Dictionary(
            uniqueKeysWithValues: timeline.filter { $0.event == .dhuhr }.map { ($0.date, $0.displayName) }
        )
        #expect(dhuhrNames == [thursday: "Dhuhr", friday: jumuah, saturday: "Dhuhr"])
    }

    @Test("the countdown names Friday's Dhuhr as Jumuah")
    func countdownNamesJumuah() {
        let timeline = buildTimeline(days: [friday: day], asrMadhab: .shafii, timeZone: zone)
        let beforeDhuhr = friday.atTime(ClockTime(hour: 12, minute: 0), in: zone)

        #expect(countdownAt(timeline, now: beforeDhuhr)?.entry.displayName == jumuah)
    }

    @Test("a late Isha is named for the day it lands on")
    func lateIshaNamedForOccurrenceDay() {
        // Thursday's Isha at 00:25 occurs on Friday. It is Isha either way — only Dhuhr moves —
        // but this pins that names follow the occurrence date, not the calculation date.
        let lateIsha = PrayerTimesResult(
            fajr: day.fajr,
            sunrise: day.sunrise,
            dhuhr: day.dhuhr,
            asrShafii: day.asrShafii,
            asrHanafi: day.asrHanafi,
            maghrib: ClockTime(hour: 22, minute: 50),
            isha: ClockTime(hour: 0, minute: 25)
        )
        let entries = buildTimeline(days: [thursday: lateIsha], asrMadhab: .shafii, timeZone: zone)
            .filter { $0.event == .isha }

        #expect(entries.count == 1)
        #expect(entries.first?.date == friday)
        #expect(entries.first?.displayName == "Isha")
    }

    // MARK: - Widget abbreviation (DESIGN.md §20)

    @Test("the widget abbreviation is derived from the displayed name")
    func abbreviationFollowsTheLabel() {
        #expect(prayerAbbreviation(TimelineEvent.dhuhr, on: friday) == "JUM")
        #expect(prayerAbbreviation(TimelineEvent.dhuhr, on: thursday) == "DHU")
        #expect(prayerAbbreviation(Prayer.dhuhr, on: friday) == "JUM")
        #expect(prayerAbbreviation(TimelineEvent.maghrib, on: friday) == "MAG")
    }
}
