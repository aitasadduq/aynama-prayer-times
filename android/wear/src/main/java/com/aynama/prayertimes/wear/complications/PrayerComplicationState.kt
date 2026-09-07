package com.aynama.prayertimes.wear.complications

import android.content.Context
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.PrayerTimesUnavailableException
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.shared.timeline.PrayerCountdown
import com.aynama.prayertimes.shared.timeline.buildTimeline
import com.aynama.prayertimes.shared.timeline.countdownAt
import com.aynama.prayertimes.shared.timeline.displayName
import com.aynama.prayertimes.shared.timeline.nextTransition
import com.aynama.prayertimes.wear.WearApplication
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything a complication needs, resolved once.
 *
 * The three supported families show different amounts of the same fact; deriving that fact in
 * each of them separately is how a watch face ends up showing two different next prayers at
 * the same moment.
 */
data class PrayerComplicationState(
    /** Day-aware — "Jumuah" on a Friday. */
    val prayerName: String,
    /** DESIGN.md §7: single letter, for the tightest formats. */
    val initial: String,
    /** The prayer's own clock time, `HH:mm`. */
    val clockTime: String,
    /** The instant the system chronometer counts to or from. */
    val reference: Instant,
    /** True while counting up from a prayer that has started. */
    val isElapsed: Boolean,
    val profileName: String,
)

/*
 * DESIGN.md §7 describes three complication states — active, upcoming, passed. Only two are
 * ours to express: [PrayerComplicationState.isElapsed] separates a prayer under way from one
 * still ahead. "Passed" never arises, because a prayer that is over stops being the subject
 * and the complication moves on to the next one rather than lingering greyed out. The colour
 * that distinguishes the two belongs to the watch face, which owns its own palette.
 */

object PrayerComplicationData {

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    /** The state for the watch's active profile, or null when there is nothing to show. */
    suspend fun current(context: Context, now: Instant = Instant.now()): PrayerComplicationState? {
        val profile = activeProfile(context) ?: return null
        val timeline = timelineFor(profile, now)
        val countdown = countdownAt(timeline, now) ?: return null
        return PrayerComplicationState(
            prayerName = countdown.entry.displayName(),
            initial = countdown.entry.displayName().take(1).uppercase(Locale.ROOT),
            clockTime = countdown.entry.time.format(timeFormatter),
            reference = countdown.entry.instant,
            isElapsed = countdown is PrayerCountdown.Elapsed,
            profileName = profile.name,
        )
    }

    /** When this complication's content stops being true, so the update can be armed there. */
    suspend fun nextChangeAt(context: Context, now: Instant = Instant.now()): Instant? {
        val profile = activeProfile(context) ?: return null
        return nextTransition(timelineFor(profile, now), now)
    }

    private suspend fun activeProfile(context: Context): Profile? {
        val app = context.applicationContext as WearApplication
        val profiles = app.profileRepository.observeAll().first()
        if (profiles.isEmpty()) return null
        return profiles.firstOrNull { it.id == app.syncState.activeProfileId }
            ?: profiles.minByOrNull { it.sortOrder }
    }

    private fun timelineFor(profile: Profile, now: Instant) = buildTimeline(
        days = days(profile, now.atZone(profile.effectiveZoneId()).toLocalDate()),
        asrMadhab = profile.asrMadhab,
        zone = profile.effectiveZoneId(),
    )

    private fun days(profile: Profile, today: LocalDate): Map<LocalDate, PrayerTimesResult> =
        (-1L..1L).mapNotNull { offset ->
            val date = today.plusDays(offset)
            try {
                date to AdhanWrapper().getPrayerTimes(
                    latitude = profile.latitude,
                    longitude = profile.longitude,
                    date = date,
                    timezone = profile.effectiveZoneId(),
                    method = profile.calculationMethod,
                )
            } catch (e: PrayerTimesUnavailableException) {
                null
            }
        }.toMap()
}
