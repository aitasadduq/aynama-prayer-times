import Foundation
import SharedLogic

extension Profile {
    /// Saving the visible adjustment renews it for this month, including an expired same value.
    mutating func setHijriAdjustment(_ offset: Int, now: Date) {
        hijriOffset = offset
        hijriOffsetMonthKey = offset == 0 ? 0 : HijriCalendar.monthKey(
            CalendarDate.from(now, in: effectiveTimeZone).plusDays(offset), in: effectiveTimeZone)
    }
}
