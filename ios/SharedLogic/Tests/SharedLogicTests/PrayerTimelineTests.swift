import Foundation
import Testing

@testable import SharedLogic

/// A case-for-case port of `PrayerTimelineTest.kt`.
///
/// The Kotlin suite is the specification (Phase 3A: the tested Android behaviour is the product
/// spec), so the fixtures, the instants and the expected strings are identical. Where a case is
/// named differently it is only to read as Swift; the assertion is the same one.
@Suite("Prayer timeline")
struct PrayerTimelineTests {

    let zone = TimeZone(identifier: "Europe/London")!
    let today = CalendarDate(year: 2026, month: 5, day: 12)

    /// A plainly-ordered day. Isha 21:45.
    let day = PrayerTimesResult(
        fajr: ClockTime(hour: 3, minute: 20),
        sunrise: ClockTime(hour: 5, minute: 12),
        dhuhr: ClockTime(hour: 12, minute: 58),
        asrShafii: ClockTime(hour: 17, minute: 5),
        asrHanafi: ClockTime(hour: 18, minute: 20),
        maghrib: ClockTime(hour: 20, minute: 40),
        isha: ClockTime(hour: 21, minute: 45)
    )

    func timeline(
        madhab: AsrMadhab = .shafii,
        days: [CalendarDate: PrayerTimesResult]? = nil
    ) -> [TimelineEntry] {
        let resolved = days ?? [
            today.minusDays(1): day,
            today: day,
            today.plusDays(1): day,
        ]
        return buildTimeline(days: resolved, asrMadhab: madhab, timeZone: zone)
    }

    func at(_ hour: Int, _ minute: Int, _ second: Int = 0, on date: CalendarDate? = nil) -> Date {
        (date ?? today).atTime(ClockTime(hour: hour, minute: minute, second: second), in: zone)
    }

    func countdownText(_ hour: Int, _ minute: Int, _ second: Int = 0) -> String {
        countdownAt(timeline(), now: at(hour, minute, second))!.formatted()
    }

    // MARK: - Direction and sign

    @Test("before a prayer it counts down with a minus sign")
    func beforePrayerCountsDown() {
        // 12:45:25 → Dhuhr 12:58:00 is 12m35s away.
        #expect(countdownText(12, 45, 25) == "-00:12:35")
    }

    @Test("at the prayer instant the sign drops and the clock reads zero")
    func atPrayerInstant() {
        #expect(countdownText(12, 58, 0) == "00:00:00")
    }

    @Test("one second after the prayer it counts up")
    func oneSecondAfter() {
        #expect(countdownText(12, 58, 1) == "00:00:01")
    }

    @Test("within the first thirty minutes it keeps counting up")
    func withinFirstThirtyMinutes() {
        #expect(countdownText(13, 13, 42) == "00:15:42")
    }

    @Test("hours and minutes are zero padded")
    func zeroPadding() {
        // Under an hour: the hours field is still present.
        #expect(countdownText(16, 57, 42) == "-00:07:18")
        // Over an hour: Asr 17:05 + 30m = 17:35, then Maghrib 20:40 is 3h05m out.
        #expect(countdownText(17, 35, 0) == "-03:05:00")
    }

    // MARK: - The 30-minute boundary

    @Test("at exactly thirty minutes it flips to counting down towards the next")
    func atExactlyThirtyMinutes() {
        // Dhuhr 12:58 + 30m = 13:28:00 exactly. Asr is 17:05, so 3h37m away.
        let state = countdownAt(timeline(), now: at(13, 28, 0))
        #expect(state?.isElapsed == false)
        #expect(state?.entry.event == .asr)
        #expect(state?.formatted() == "-03:37:00")
    }

    @Test("one second before thirty minutes it is still counting up")
    func oneSecondBeforeThirtyMinutes() {
        let state = countdownAt(timeline(), now: at(13, 27, 59))
        #expect(state?.isElapsed == true)
        #expect(state?.formatted() == "00:29:59")
    }

    @Test("the count-up ends early when the next event arrives first")
    func countUpEndsEarly() {
        // Fajr 03:20 → Sunrise 05:12 is far apart here, so build a compressed day where the next
        // event lands 10 minutes after Fajr. The count-up must not outlive it.
        let tight = PrayerTimesResult(
            fajr: ClockTime(hour: 5, minute: 2),
            sunrise: ClockTime(hour: 5, minute: 12),
            dhuhr: day.dhuhr,
            asrShafii: day.asrShafii,
            asrHanafi: day.asrHanafi,
            maghrib: day.maghrib,
            isha: day.isha
        )
        let tl = buildTimeline(days: [today: tight], asrMadhab: .shafii, timeZone: zone)

        let duringFajr = countdownAt(tl, now: at(5, 8))
        #expect(duringFajr?.isElapsed == true)
        #expect(duringFajr?.entry.event == .fajr)

        // Sunrise has now passed, still inside Fajr's 30-minute window — but Fajr is over.
        let afterSunrise = countdownAt(tl, now: at(5, 20))
        #expect(afterSunrise?.isElapsed == false)
        #expect(afterSunrise?.entry.event == .dhuhr)
    }

    // MARK: - Sunrise is a target, never a source

    @Test("sunrise is counted down to")
    func sunriseIsATarget() {
        let state = countdownAt(timeline(), now: at(5, 0))
        #expect(state?.isElapsed == false)
        #expect(state?.entry.event == .sunrise)
        #expect(state?.formatted() == "-00:12:00")
    }

    @Test("sunrise never counts up")
    func sunriseNeverCountsUp() {
        // One minute after sunrise: nothing began, so the clock is already counting to Dhuhr.
        let state = countdownAt(timeline(), now: at(5, 13))
        #expect(state?.isElapsed == false)
        #expect(state?.entry.event == .dhuhr)
    }

    // MARK: - Date boundaries

    @Test("after Isha it counts down to tomorrow's Fajr")
    func afterIsha() {
        let state = countdownAt(timeline(), now: at(23, 30))
        #expect(state?.isElapsed == false)
        #expect(state?.entry.event == .fajr)
        #expect(state?.entry.date == today.plusDays(1))
        // 23:30 → 03:20 next day = 3h50m.
        #expect(state?.formatted() == "-03:50:00")
    }

    @Test("just after midnight it still counts down to today's Fajr")
    func justAfterMidnight() {
        let state = countdownAt(timeline(), now: at(0, 5))
        #expect(state?.isElapsed == false)
        #expect(state?.entry.event == .fajr)
        #expect(state?.entry.date == today)
        #expect(state?.formatted() == "-03:15:00")
    }

    // MARK: - Late Isha (rolls onto the following calendar day)

    var lateIsha: PrayerTimesResult {
        PrayerTimesResult(
            fajr: day.fajr,
            sunrise: day.sunrise,
            dhuhr: day.dhuhr,
            asrShafii: day.asrShafii,
            asrHanafi: day.asrHanafi,
            maghrib: ClockTime(hour: 22, minute: 50),
            isha: ClockTime(hour: 0, minute: 25)
        )
    }

    func lateIshaTimeline() -> [TimelineEntry] {
        buildTimeline(
            days: [
                today.minusDays(1): lateIsha,
                today: lateIsha,
                today.plusDays(1): lateIsha,
            ],
            asrMadhab: .shafii,
            timeZone: zone
        )
    }

    @Test("a late Isha is dated to the day it actually occurs on")
    func lateIshaIsRolledForward() {
        let ishaOfToday = buildTimeline(days: [today: lateIsha], asrMadhab: .shafii, timeZone: zone)
            .filter { $0.event == .isha }
        #expect(ishaOfToday.count == 1)
        #expect(ishaOfToday.first?.date == today.plusDays(1))
        #expect(ishaOfToday.first?.instant == at(0, 25, on: today.plusDays(1)))
    }

    @Test("in the small hours a late Isha counts up, it does not count back to Fajr")
    func lateIshaSmallHours() {
        let state = countdownAt(lateIshaTimeline(), now: at(0, 35))
        #expect(state?.isElapsed == true)
        #expect(state?.entry.event == .isha)
        #expect(state?.formatted() == "00:10:00")
    }

    @Test("the evening counts down to a late Isha, not to tomorrow's Fajr")
    func lateIshaEvening() {
        // 23:30 on `today`: past Maghrib's 22:50 + 30m window, Isha lands at 00:25 tomorrow.
        let state = countdownAt(lateIshaTimeline(), now: at(23, 30))
        #expect(state?.isElapsed == false)
        #expect(state?.entry.event == .isha)
        #expect(state?.entry.date == today.plusDays(1))
        #expect(state?.formatted() == "-00:55:00")
    }

    @Test("after a late Isha's count-up window the next target is Fajr")
    func lateIshaAfterWindow() {
        // Isha 00:25 + 30m = 00:55, then Fajr 03:20 the same morning.
        let state = countdownAt(lateIshaTimeline(), now: at(1, 0))
        #expect(state?.isElapsed == false)
        #expect(state?.entry.event == .fajr)
        #expect(state?.formatted() == "-02:20:00")
    }

    @Test("before Fajr the current event is yesterday's Isha")
    func beforeFajr() {
        // Requires yesterday in the timeline; without it there is no event before now at 01:00.
        let state = countdownAt(timeline(), now: at(1, 0))
        #expect(state?.isElapsed == false)
        #expect(state?.entry.date == today)
        #expect(state?.entry.event == .fajr)
    }

    @Test("across a DST spring-forward the countdown measures real elapsed time")
    func dstSpringForward() {
        // Europe/London jumps 01:00 → 02:00 on 2026-03-29, so 00:30 to Fajr at 03:20 is 2h50m
        // on the wall clock and 1h50m of real time.
        //
        // The literal is the assertion. Comparing against `duration(from: before, to:
        // fajr.instant)` would only restate how `.remaining` is built and would hold whatever
        // `atTime` did with the zone, including nothing at all.
        let dstDay = CalendarDate(year: 2026, month: 3, day: 29)
        let tl = buildTimeline(days: [dstDay: day], asrMadhab: .shafii, timeZone: zone)
        let before = dstDay.atTime(ClockTime(hour: 0, minute: 30), in: zone)
        let state = countdownAt(tl, now: before)

        #expect(state?.entry.event == .fajr)
        #expect(state?.formatted() == "-01:50:00")
    }

    // MARK: - Madhab

    @Test("the Hanafi Asr shifts the timeline")
    func hanafiAsr() {
        let state = countdownAt(timeline(madhab: .hanafi), now: at(17, 30))
        #expect(state?.isElapsed == false)
        #expect(state?.entry.event == .asr)
        #expect(state?.entry.time == ClockTime(hour: 18, minute: 20))
    }

    // MARK: - Transitions

    @Test("while counting down the next transition is the next event")
    func nextTransitionCountingDown() {
        #expect(nextTransition(timeline(), now: at(12, 45)) == at(12, 58))
    }

    @Test("while counting up the next transition is thirty minutes after the prayer")
    func nextTransitionCountingUp() {
        #expect(nextTransition(timeline(), now: at(13, 0)) == at(13, 28))
    }

    @Test("while counting up the next transition is the next event when it comes sooner")
    func nextTransitionCountingUpShortGap() {
        let tight = PrayerTimesResult(
            fajr: ClockTime(hour: 5, minute: 2),
            sunrise: ClockTime(hour: 5, minute: 12),
            dhuhr: day.dhuhr,
            asrShafii: day.asrShafii,
            asrHanafi: day.asrHanafi,
            maghrib: day.maghrib,
            isha: day.isha
        )
        let tl = buildTimeline(days: [today: tight], asrMadhab: .shafii, timeZone: zone)
        #expect(nextTransition(tl, now: at(5, 5)) == at(5, 12))
    }

    // MARK: - Empty / short timelines

    @Test("an empty timeline has no countdown")
    func emptyTimeline() {
        #expect(countdownAt([], now: at(12, 0)) == nil)
        #expect(nextTransition([], now: at(12, 0)) == nil)
    }

    @Test("past the end of the timeline there is no countdown")
    func pastTheEnd() {
        let tl = buildTimeline(days: [today: day], asrMadhab: .shafii, timeZone: zone)
        #expect(countdownAt(tl, now: at(23, 59)) == nil)
    }

    // MARK: - Degenerate high-latitude days

    @Test("a collapsed Fajr and Isha resolve to Fajr")
    func collapsedFajrAndIsha() {
        // Above ~48° Adhan's high-latitude fallback can put Fajr and Isha on the same instant.
        // "The most recent event" is then ambiguous; the day's canonical order breaks the tie.
        let collapsed = PrayerTimesResult(
            fajr: ClockTime(hour: 2, minute: 45),
            sunrise: day.sunrise,
            dhuhr: day.dhuhr,
            asrShafii: day.asrShafii,
            asrHanafi: day.asrHanafi,
            maghrib: day.maghrib,
            isha: ClockTime(hour: 2, minute: 45)
        )
        let tl = buildTimeline(
            days: [today.minusDays(1): collapsed, today: collapsed],
            asrMadhab: .shafii,
            timeZone: zone
        )
        #expect(currentEntry(tl, now: at(4, 38))?.event == .fajr)

        let state = countdownAt(tl, now: at(2, 45))
        #expect(state?.isElapsed == true)
        #expect(state?.entry.event == .fajr)
    }

    // MARK: - Ordering

    @Test("every event has a display name")
    func everyEventHasADisplayName() {
        let names = buildTimeline(days: [today: day], asrMadhab: .shafii, timeZone: zone)
            .map(\.displayName)
        #expect(names == ["Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"])
    }

    @Test("the timeline is sorted by instant across days")
    func sortedAcrossDays() {
        let tl = timeline()
        #expect(tl.count == 18)
        #expect(zip(tl, tl.dropFirst()).allSatisfy { $0.instant <= $1.instant })
    }

    // MARK: - iOS additions

    @Test("transitions walks the same rule forward for a WidgetKit timeline")
    func transitionsWalkForward() {
        // WidgetKit takes a whole schedule up front, so the widget cannot be woken at each
        // transition the way an Android alarm chain is. Walking the rule must produce exactly the
        // instants nextTransition would have produced one at a time.
        let tl = timeline()
        let stops = transitions(tl, from: at(12, 45), limit: 4)
        #expect(stops == [at(12, 58), at(13, 28), at(17, 5), at(17, 35)])
    }

    @Test("transitions stops at the end of the timeline rather than inventing entries")
    func transitionsStopAtTheEnd() {
        let tl = buildTimeline(days: [today: day], asrMadhab: .shafii, timeZone: zone)
        // 21:00 is inside Maghrib's count-up window, so the first stop is its 21:10 close, then
        // Isha at 21:45 and Isha's own 22:15 close. After that the day is spent: with no event
        // beyond Isha the rule has nothing further to say, and the walk must stop rather than
        // extrapolate a prayer it has not been given.
        let stops = transitions(tl, from: at(21, 0), limit: 64)
        #expect(stops == [at(21, 10), at(21, 45), at(22, 15)])
    }
}

/// `CalendarDate` is the key of the timeline's `days`, so its equality has to be the same
/// question its ordering asks.
@Suite("Calendar date identity")
struct CalendarDateIdentityTests {

    @Test("an out-of-range component resolves to the day it names")
    func outOfRangeComponentsAreTheSameDay() {
        // The vector schema's date pattern permits 2026-02-30, and epochDay has always resolved
        // it to 2026-03-02. Synthesized equality compared the raw triple instead, so the two were
        // unequal while neither sorted before the other.
        let named = CalendarDate(year: 2026, month: 2, day: 30)
        let resolved = CalendarDate(year: 2026, month: 3, day: 2)

        #expect(named == resolved)
        #expect(!(named < resolved) && !(resolved < named))
        #expect(named.hashValue == resolved.hashValue)
    }

    @Test("one real day is one key")
    func oneDayIsOneKey() {
        var days: [CalendarDate: String] = [:]
        days[CalendarDate(year: 2026, month: 2, day: 30)] = "first"
        days[CalendarDate(year: 2026, month: 3, day: 2)] = "second"
        #expect(days.count == 1)
    }

    @Test("ordinary dates are still distinct")
    func ordinaryDatesAreDistinct() {
        let a = CalendarDate(year: 2026, month: 5, day: 12)
        #expect(a != a.plusDays(1))
        #expect(a < a.plusDays(1))
        #expect(a == CalendarDate(year: 2026, month: 5, day: 12))
    }
}
