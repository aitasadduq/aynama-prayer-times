package com.aynama.prayertimes.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.PrayerTimesUnavailableException
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import com.aynama.prayertimes.shared.timeline.PrayerCountdown
import com.aynama.prayertimes.shared.timeline.TimelineEntry
import com.aynama.prayertimes.shared.timeline.TimelineEvent
import com.aynama.prayertimes.shared.timeline.buildTimeline
import com.aynama.prayertimes.shared.timeline.countdownAt
import com.aynama.prayertimes.shared.timeline.currentEntry
import com.aynama.prayertimes.shared.timeline.displayName
import com.aynama.prayertimes.shared.timeline.format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row of the watch's prayer list. */
data class WearPrayerRow(
    val name: String,
    val time: String,
    val isCurrent: Boolean,
    val isPast: Boolean,
)

data class WearProfilePage(
    val profileId: Long,
    val profileName: String,
    /** Signed and padded per DESIGN.md §19 — render verbatim. */
    val countdownText: String,
    val countdownIsElapsed: Boolean,
    val countdownPrayerName: String,
    val countdownPrayerTime: String,
    val rows: List<WearPrayerRow>,
)

sealed interface WearHomeState {
    data object Loading : WearHomeState

    /** No profiles mirrored yet. Distinct from "the phone has gone quiet" — see [staleness]. */
    data class WaitingForPhone(val everSynced: Boolean) : WearHomeState

    data class NoTimes(val profileName: String) : WearHomeState

    data class Ready(
        val pages: List<WearProfilePage>,
        val initialPage: Int,
        val staleness: Staleness,
    ) : WearHomeState

    /**
     * How long since the phone last published.
     *
     * The times themselves keep being right while the phone is away — the watch calculates
     * them — so this is not an error. It only matters once it is old enough that the *profiles*
     * might have changed underneath, which is why the threshold is in days and not minutes.
     */
    enum class Staleness { FRESH, STALE }
}

class WearHomeViewModel(
    private val profileRepository: ProfileRepository,
    private val syncState: WearSyncState,
) : ViewModel() {

    private val adhan = AdhanWrapper()
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    private val _uiState = MutableStateFlow<WearHomeState>(WearHomeState.Loading)
    val uiState: StateFlow<WearHomeState> = _uiState.asStateFlow()

    private val clock: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(1_000L)
        }
    }

    init {
        combine(profileRepository.observeAll(), clock) { profiles, now ->
            if (profiles.isEmpty()) {
                return@combine WearHomeState.WaitingForPhone(everSynced = syncState.lastSyncedAt > 0L)
            }
            val pages = profiles.mapNotNull { buildPage(it, now) }
            if (pages.isEmpty()) {
                return@combine WearHomeState.NoTimes(profiles.first().name)
            }
            WearHomeState.Ready(
                pages = pages,
                initialPage = pages.indexOfFirst { it.profileId == syncState.activeProfileId }
                    .coerceAtLeast(0),
                staleness = stalenessOf(syncState.lastSyncedAt, now),
            )
        }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    private fun buildPage(profile: Profile, now: Instant): WearProfilePage? {
        val zone = profile.effectiveZoneId()
        val today = now.atZone(zone).toLocalDate()
        val timeline = buildTimeline(daysFor(profile, today), profile.asrMadhab, zone)
        val countdown = countdownAt(timeline, now) ?: return null
        val current = currentEntry(timeline, now)
        return WearProfilePage(
            profileId = profile.id,
            profileName = profile.name,
            countdownText = countdown.format(),
            countdownIsElapsed = countdown is PrayerCountdown.Elapsed,
            countdownPrayerName = countdown.entry.displayName(),
            countdownPrayerTime = countdown.entry.time.format(timeFormatter),
            rows = timeline
                .filter { it.date == today }
                .map { entry -> row(entry, current, now) },
        )
    }

    private fun row(entry: TimelineEntry, current: TimelineEntry?, now: Instant) = WearPrayerRow(
        name = entry.displayName(),
        time = entry.time.format(timeFormatter),
        // Sunrise is a time reference, never "the current prayer" — the same rule the phone's
        // ribbon and the widgets follow.
        isCurrent = entry.event != TimelineEvent.SUNRISE && entry.instant == current?.instant,
        isPast = !entry.instant.isAfter(now),
    )

    private fun daysFor(profile: Profile, today: LocalDate): Map<LocalDate, PrayerTimesResult> =
        (-1L..1L).mapNotNull { offset ->
            val date = today.plusDays(offset)
            try {
                date to adhan.getPrayerTimes(
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

    companion object {
        /**
         * How long the phone may stay quiet before the watch says so.
         *
         * Days, not minutes: prayer times do not depend on the phone, so a disconnected watch
         * is still correct. What goes stale is the profile *set* — a location edited on the
         * phone this morning. Warning after an hour would cry wolf on every commute.
         */
        internal val STALE_AFTER: Duration = Duration.ofDays(2)

        internal fun stalenessOf(lastSyncedAt: Long, now: Instant): WearHomeState.Staleness = when {
            lastSyncedAt <= 0L -> WearHomeState.Staleness.STALE
            Duration.between(Instant.ofEpochMilli(lastSyncedAt), now) > STALE_AFTER ->
                WearHomeState.Staleness.STALE
            else -> WearHomeState.Staleness.FRESH
        }

        fun factory(app: WearApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WearHomeViewModel(app.profileRepository, app.syncState) as T
            }
    }
}
