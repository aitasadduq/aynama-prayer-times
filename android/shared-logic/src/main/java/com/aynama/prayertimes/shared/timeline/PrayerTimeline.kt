package com.aynama.prayertimes.shared.timeline

import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The single source of truth for "what prayer is it, and how long until the next one".
 *
 * Every surface that shows a countdown — the home screen, the four widgets, the live
 * notification, and the watch apps once they exist — resolves it here rather than
 * re-deriving slightly different rules from a [PrayerTimesResult]. The rules are stated
 * once, in [countdownAt], and the formatting once, in [format].
 *
 * Everything is instant-based rather than [LocalTime]-based. Wall-clock comparison breaks
 * at every date boundary the app actually hits: Isha after midnight sorts before Fajr,
 * "the next prayer" after Isha lives on tomorrow's date, and a profile pinned to a far-away
 * timezone can produce a day whose times are not in canonical clock order at all.
 */
enum class TimelineEvent(val isPrayer: Boolean) {
    FAJR(true),

    /**
     * Not a prayer, but a real boundary: it closes the Fajr window, so it is a legitimate
     * countdown target. It never counts *up* — nothing begins at sunrise.
     */
    SUNRISE(false),
    DHUHR(true),
    ASR(true),
    MAGHRIB(true),
    ISHA(true),
}

/**
 * One event of one prayer day, resolved to an absolute instant in the profile's zone.
 *
 * [date] is the calendar date the event actually *occurs* on, which is not always the date
 * it was calculated for — see [entriesFor] on late Isha.
 */
data class TimelineEntry(
    val event: TimelineEvent,
    val date: LocalDate,
    val time: LocalTime,
    val instant: Instant,
)

/**
 * The name to show for this entry — "Jumuah" for a Friday Dhuhr. See [prayerDisplayName].
 *
 * On [TimelineEntry] rather than [TimelineEvent] because a prayer's name depends on the day
 * it falls on. Every surface reads names from here so there is one place to change.
 */
fun TimelineEntry.displayName(): String = prayerDisplayName(event, date)

/**
 * The count-up window: how long a prayer stays "current" after its time before the
 * countdown flips to the next one. Specified in IMPLEMENTATION_PLAN.md Phase 1.
 */
val COUNT_UP_WINDOW: Duration = Duration.ofMinutes(30)

/**
 * Where the clock is relative to the prayer timeline.
 *
 * [Elapsed] counts upward from a prayer that has started; [Remaining] counts down towards
 * one that has not. The two carry the same shape so callers can render them uniformly and
 * only branch on the sign.
 */
sealed interface PrayerCountdown {
    /** The event being counted from ([Elapsed]) or towards ([Remaining]). */
    val entry: TimelineEntry

    /** Always non-negative. Direction is carried by the type, not the sign. */
    val duration: Duration

    data class Elapsed(override val entry: TimelineEntry, override val duration: Duration) : PrayerCountdown

    data class Remaining(override val entry: TimelineEntry, override val duration: Duration) : PrayerCountdown
}

/**
 * Build the ordered event timeline for [days], resolved in [zone].
 *
 * Pass at least yesterday, today and tomorrow: [countdownAt] needs an event on each side of
 * `now` at every moment of the day, including the stretch after Isha and the stretch before
 * Fajr. Entries are sorted by instant, so callers never depend on clock ordering.
 */
fun buildTimeline(
    days: Map<LocalDate, PrayerTimesResult>,
    asrMadhab: AsrMadhab,
    zone: ZoneId,
): List<TimelineEntry> =
    days.entries
        .flatMap { (date, times) -> entriesFor(date, times, asrMadhab, zone) }
        .sortedBy { it.instant }

/**
 * Resolve one calculated day into occurrence-dated entries.
 *
 * [PrayerTimesResult] carries wall-clock times with the date stripped, so a late Isha — 00:25
 * for a summer northern city — comes back looking like it happened before that morning's Fajr.
 * Any event whose clock time sorts before Fajr's belongs to the following calendar day; that
 * is what "the night of the 12th" means. Without this roll-forward the evening of a long
 * summer day would count *backwards* to an Isha 23 hours in the past.
 */
private fun entriesFor(
    date: LocalDate,
    times: PrayerTimesResult,
    asrMadhab: AsrMadhab,
    zone: ZoneId,
): List<TimelineEntry> {
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    return listOf(
        TimelineEvent.FAJR to times.fajr,
        TimelineEvent.SUNRISE to times.sunrise,
        TimelineEvent.DHUHR to times.dhuhr,
        TimelineEvent.ASR to asr,
        TimelineEvent.MAGHRIB to times.maghrib,
        TimelineEvent.ISHA to times.isha,
    ).map { (event, time) ->
        val occursOn = if (time < times.fajr) date.plusDays(1) else date
        TimelineEntry(event, occursOn, time, occursOn.atTime(time).atZone(zone).toInstant())
    }
}

/**
 * Resolve the countdown state at [now].
 *
 * The rules, in full:
 *
 *  - Before a prayer, count down towards it. Rendered with a minus sign: `-00:12:35`.
 *  - At the prayer instant the sign drops and the clock counts up from `00:00:00`.
 *  - It keeps counting up for [window] (30 minutes), or until the next event arrives if
 *    that comes sooner — so a short Fajr-to-sunrise gap cannot leave two events "current".
 *  - After that it counts down towards the next event again.
 *
 * Only prayers count up. Sunrise is a boundary, not an act, so the moment it passes the
 * countdown moves straight on to Dhuhr.
 *
 * Returns null when [timeline] has no event on the needed side of [now] — the caller's
 * timeline is too short, not a state the UI should invent a value for.
 */
fun countdownAt(
    timeline: List<TimelineEntry>,
    now: Instant,
    window: Duration = COUNT_UP_WINDOW,
): PrayerCountdown? {
    val current = currentEntry(timeline, now)
    if (current != null && current.event.isPrayer) {
        val elapsed = Duration.between(current.instant, now)
        if (elapsed < window) return PrayerCountdown.Elapsed(current, elapsed)
    }
    val next = timeline.firstOrNull { it.instant.isAfter(now) } ?: return null
    return PrayerCountdown.Remaining(next, Duration.between(now, next.instant))
}

/**
 * The event that is currently under way at [now]: the most recently started one.
 *
 * Two events can share an instant. Above roughly 48° latitude adhan's high-latitude fallback
 * collapses Fajr and Isha onto the same moment, and then "the most recent event" is genuinely
 * ambiguous. Ties resolve to the earliest event in the day's canonical order — at 04:38 on a
 * June morning in London the answer users expect is Fajr, not the previous night's Isha.
 */
fun currentEntry(timeline: List<TimelineEntry>, now: Instant): TimelineEntry? {
    val latest = timeline.lastOrNull { !it.instant.isAfter(now) } ?: return null
    return timeline.first { it.instant == latest.instant }
}

/**
 * The instant at which [countdownAt] would return a different state than it does at [now].
 *
 * Surfaces that cannot tick continuously — widgets, the live notification — arm an update
 * here instead of guessing at prayer boundaries, which would miss the +30 minute flip from
 * counting up to counting down.
 */
fun nextTransition(
    timeline: List<TimelineEntry>,
    now: Instant,
    window: Duration = COUNT_UP_WINDOW,
): Instant? {
    val next = timeline.firstOrNull { it.instant.isAfter(now) }
    return when (val state = countdownAt(timeline, now, window)) {
        is PrayerCountdown.Elapsed -> {
            val windowEnd = state.entry.instant.plus(window)
            if (next != null && next.instant.isBefore(windowEnd)) next.instant else windowEnd
        }
        is PrayerCountdown.Remaining -> state.entry.instant
        null -> null
    }
}

/**
 * `-HH:MM:SS` while counting down, `HH:MM:SS` while counting up.
 *
 * Hours are not wrapped at 24 — a gap longer than a day (possible at high latitudes) reads
 * as `-31:04:12` rather than silently restarting.
 */
fun PrayerCountdown.format(): String {
    val sign = if (this is PrayerCountdown.Remaining) "-" else ""
    return sign + formatDuration(duration)
}

internal fun formatDuration(duration: Duration): String {
    val total = duration.seconds.coerceAtLeast(0L)
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}
