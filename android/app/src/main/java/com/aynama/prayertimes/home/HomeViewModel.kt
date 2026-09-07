package com.aynama.prayertimes.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.notifications.RamadanDetector
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.PrayerTimesUnavailableException
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import com.aynama.prayertimes.shared.data.repository.QazaRepository
import com.aynama.prayertimes.shared.timeline.PrayerCountdown
import com.aynama.prayertimes.shared.timeline.TimelineEntry
import com.aynama.prayertimes.shared.timeline.buildTimeline
import com.aynama.prayertimes.shared.timeline.countdownAt
import com.aynama.prayertimes.shared.timeline.displayName
import com.aynama.prayertimes.shared.timeline.format
import com.aynama.prayertimes.shared.timeline.prayerDisplayName
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class PrayerPhase { FAJR, SUNRISE_TRANSITION, DHUHR, ASR, MAGHRIB, ISHA }

enum class RibbonState { PASSED, CURRENT, UPCOMING }

sealed interface RibbonRow {
    val displayTime: String

    data class PrayerEntry(
        val prayer: Prayer,
        /** Day-aware — "Jumuah" on a Friday. Resolved here so the UI never re-derives it. */
        val displayName: String,
        override val displayTime: String,
        val ribbonState: RibbonState,
    ) : RibbonRow

    data class SunriseEntry(override val displayTime: String) : RibbonRow

    data class ImsakEntry(override val displayTime: String, val isPast: Boolean) : RibbonRow
}

data class ProfileUiState(
    val profile: Profile,
    val ribbonRows: List<RibbonRow>,
    /** Already signed and padded per DESIGN.md §19 — render it verbatim. */
    val countdownText: String,
    /** True while counting up from a prayer that has started; false while counting down to one. */
    val countdownIsElapsed: Boolean,
    /** The prayer the countdown refers to: the one just started, or the one coming next. */
    val countdownPrayerName: String,
    val countdownPrayerTime: String,
    val currentPhase: PrayerPhase,
    val isRamadan: Boolean,
    val showRamadanBanner: Boolean,
    val outstandingQazaCount: Int,
    val hijriDateText: String,
)

/**
 * One page of the home pager.
 *
 * Failure is per profile, not per screen: a location whose times cannot be calculated becomes an
 * [Unavailable] page and every other profile still renders. Folding the failure into the shared
 * [HomeUiState.Error] would blank the whole pager, including profiles that are perfectly fine.
 */
sealed interface ProfilePage {
    val profile: Profile

    data class Ready(val state: ProfileUiState) : ProfilePage {
        override val profile: Profile get() = state.profile
    }

    data class Unavailable(override val profile: Profile, val reason: String) : ProfilePage
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val cause: String) : HomeUiState
    data class Loaded(val pages: List<ProfilePage>) : HomeUiState
}

class HomeViewModel(
    private val profileRepository: ProfileRepository,
    private val qazaRepository: QazaRepository,
    private val prefs: SharedPreferences,
) : ViewModel() {

    private val adhan = AdhanWrapper()

    private data class PrayerCacheKey(
        val profileId: Long,
        val date: LocalDate,
        val latitude: Double,
        val longitude: Double,
        val method: CalculationMethodKey,
        val timezone: String,
    )
    // Null value = adhan has no times for that day (polar day/night). Cached like any other
    // answer so a profile inside the polar circle does not re-run the calculation every tick.
    private val prayerTimesCache = mutableMapOf<PrayerCacheKey, PrayerTimesResult?>()
    private val hijriCache = mutableMapOf<Triple<LocalDate, Int, String>, String>()
    private val ramadanCache = mutableMapOf<Triple<LocalDate, Int, String>, Boolean>()
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Instants, not wall times: the countdown crosses date boundaries and profiles can be
    // pinned to a zone the device is not in. Each profile resolves this to its own local
    // date and clock time.
    private val clockFlow: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(1_000L)
        }
    }

    @Suppress("OPT_IN_USAGE")
    private fun qazaCountsFlow(profilesFlow: Flow<List<Profile>>): Flow<Map<Long, Int>> =
        profilesFlow.flatMapLatest { profiles ->
            if (profiles.isEmpty()) return@flatMapLatest flowOf(emptyMap())
            combine(profiles.map { p ->
                qazaRepository.observeOutstandingCount(p.id).map { count -> p.id to count }
            }) { pairs -> pairs.toMap() }
        }

    init {
        val profilesFlow = profileRepository.observeAll()

        combine(profilesFlow, clockFlow, qazaCountsFlow(profilesFlow)) { profiles, now, qazaCounts ->
            if (profiles.isEmpty()) return@combine HomeUiState.Empty
            val hijriYear = RamadanDetector.currentHijriYear()
            val dismissedYear = prefs.getInt(KEY_RAMADAN_BANNER_YEAR, -1)
            HomeUiState.Loaded(
                profiles.map { profile ->
                    val today = now.atZone(profile.effectiveZoneId()).toLocalDate()
                    val times = cachedPrayerTimes(profile, today)
                    if (times == null) {
                        ProfilePage.Unavailable(profile, UNAVAILABLE_POLAR_REASON)
                    } else {
                        ProfilePage.Ready(
                            buildProfileUiState(
                                profile, times, now, today,
                                qazaCounts[profile.id] ?: 0, hijriYear, dismissedYear,
                            )
                        )
                    }
                }
            )
        }
            .catch { e -> _uiState.value = HomeUiState.Error(e.message ?: "Unknown error") }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun dismissRamadanBanner() {
        prefs.edit().putInt(KEY_RAMADAN_BANNER_YEAR, RamadanDetector.currentHijriYear()).apply()
    }

    fun markPrayer(profileId: Long, prayer: Prayer, date: LocalDate, status: QazaStatus) {
        viewModelScope.launch { qazaRepository.markPrayer(profileId, prayer, date, status) }
    }

    /** Today's times for [profile], or null when the location has none (polar day/night). */
    private fun cachedPrayerTimes(profile: Profile, date: LocalDate): PrayerTimesResult? {
        val zone = profile.effectiveZoneId()
        val key = PrayerCacheKey(profile.id, date, profile.latitude, profile.longitude, profile.calculationMethod, zone.id)
        if (prayerTimesCache.containsKey(key)) return prayerTimesCache[key]
        val computed = try {
            adhan.getPrayerTimes(
                latitude = profile.latitude,
                longitude = profile.longitude,
                date = date,
                timezone = zone,
                method = profile.calculationMethod,
            )
        } catch (e: PrayerTimesUnavailableException) {
            null
        }
        prayerTimesCache[key] = computed
        // The countdown needs yesterday and tomorrow as well as today, so the cache now grows
        // three entries a day instead of one. This process can live for weeks; keep only the
        // days a timeline can still reach.
        prayerTimesCache.keys.removeAll { ChronoUnit.DAYS.between(it.date, date).let { d -> d > 2 || d < -2 } }
        return computed
    }

    /**
     * The countdown timeline for [profile] around [today].
     *
     * Yesterday and tomorrow are both required: before Fajr the current event is last night's
     * Isha, and after Isha the next one is tomorrow's Fajr. Days with no computable times are
     * dropped rather than failing the whole timeline — near the polar circles a single day can
     * be undefined while the days around it are fine.
     */
    private fun timelineFor(profile: Profile, today: LocalDate): List<TimelineEntry> =
        buildTimeline(
            days = (-1L..1L).mapNotNull { offset ->
                val date = today.plusDays(offset)
                cachedPrayerTimes(profile, date)?.let { date to it }
            }.toMap(),
            asrMadhab = profile.asrMadhab,
            zone = profile.effectiveZoneId(),
        )

    private fun cachedHijri(date: LocalDate, offset: Int, zone: ZoneId): String =
        hijriCache.getOrPut(Triple(date, offset, zone.id)) {
            RamadanDetector.hijriDateWithOffset(date, offset, zone)
        }

    private fun cachedIsRamadan(date: LocalDate, offset: Int, zone: ZoneId): Boolean =
        ramadanCache.getOrPut(Triple(date, offset, zone.id)) {
            RamadanDetector.isRamadanWithOffset(date, offset, zone)
        }

    private fun buildProfileUiState(
        profile: Profile,
        times: PrayerTimesResult,
        now: Instant,
        today: LocalDate,
        qazaCount: Int,
        hijriYear: Int,
        dismissedYear: Int,
    ): ProfileUiState {
        val zone = profile.effectiveZoneId()
        val localNow = now.atZone(zone).toLocalTime()
        val offset = RamadanDetector.effectiveHijriOffset(
            profile.hijriOffset, profile.hijriOffsetMonthKey, today, zone,
        )
        val ramadan = cachedIsRamadan(today, offset, zone)
        val countdown = countdownAt(timelineFor(profile, today), now)
        return ProfileUiState(
            profile = profile,
            ribbonRows = deriveRibbonRows(times, profile.asrMadhab, localNow, today, ramadan, timeFormatter),
            countdownText = countdown?.format() ?: NO_COUNTDOWN,
            countdownIsElapsed = countdown is PrayerCountdown.Elapsed,
            countdownPrayerName = countdown?.entry?.displayName() ?: "",
            countdownPrayerTime = countdown?.entry?.time?.format(timeFormatter) ?: "",
            currentPhase = derivePhase(times, profile.asrMadhab, localNow),
            isRamadan = ramadan,
            showRamadanBanner = ramadan && dismissedYear != hijriYear,
            outstandingQazaCount = qazaCount,
            hijriDateText = cachedHijri(today, offset, zone),
        )
    }

    companion object {
        private const val KEY_RAMADAN_BANNER_YEAR = "ramadan_banner_dismissed_year"

        // Only reachable when today has times but neither neighbouring day does, so the
        // timeline has no event on one side of now. Em dashes rather than "00:00:00", which
        // would read as a prayer that just started.
        internal const val NO_COUNTDOWN = "--:--:--"

        internal const val UNAVAILABLE_POLAR_REASON =
            "The sun doesn't fully rise or set at this location today, so there are no times " +
                "to calculate from. This happens inside the polar circles around midsummer and " +
                "midwinter. Other profiles are unaffected."

        fun factory(app: AynamaApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(app.profileRepository, app.qazaRepository, app.prefs) as T
            }
    }
}

// Pure functions — internal for testability

internal fun derivePhase(times: PrayerTimesResult, asrMadhab: AsrMadhab, now: LocalTime): PrayerPhase {
    if (times.isha < times.fajr && now < times.isha) return PrayerPhase.MAGHRIB
    if (times.isha < times.fajr && now >= times.maghrib) return PrayerPhase.MAGHRIB
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    return when {
        now < times.fajr -> PrayerPhase.ISHA
        now < times.sunrise -> PrayerPhase.FAJR
        now < times.dhuhr -> PrayerPhase.SUNRISE_TRANSITION
        now < asr -> PrayerPhase.DHUHR
        now < times.maghrib -> PrayerPhase.ASR
        now < times.isha -> PrayerPhase.MAGHRIB
        else -> PrayerPhase.ISHA
    }
}

internal fun deriveRibbonRows(
    times: PrayerTimesResult,
    asrMadhab: AsrMadhab,
    now: LocalTime,
    date: LocalDate,
    isRamadan: Boolean,
    formatter: DateTimeFormatter,
): List<RibbonRow> {
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    val prayerTimeline = listOf(times.fajr, times.dhuhr, asr, times.maghrib, times.isha)
    // Prayers that cross midnight (e.g. Isha at 00:25) have a LocalTime earlier than Fajr.
    // Including them in a naive `<= now` check would incorrectly mark them as passed during
    // the evening. Only count a prayer as passed if it is within the same prayer-day (>= fajr).
    val currentIndex = if (times.isha < times.fajr && now < times.fajr) {
        if (now >= times.isha) 4 else 3
    } else {
        prayerTimeline.indexOfLast { prayerTime ->
            prayerTime <= now && prayerTime >= times.fajr
        }
    }

    fun stateAt(idx: Int): RibbonState = when {
        idx < currentIndex -> RibbonState.PASSED
        idx == currentIndex -> RibbonState.CURRENT
        else -> RibbonState.UPCOMING
    }

    return buildList {
        if (isRamadan) {
            val imsak = times.fajr.minusMinutes(10)
            add(RibbonRow.ImsakEntry(imsak.format(formatter), isPast = imsak <= now))
        }
        fun prayerRow(prayer: Prayer, time: LocalTime, index: Int) = RibbonRow.PrayerEntry(
            prayer = prayer,
            displayName = prayerDisplayName(prayer, date),
            displayTime = time.format(formatter),
            ribbonState = stateAt(index),
        )
        add(prayerRow(Prayer.FAJR, times.fajr, 0))
        add(RibbonRow.SunriseEntry(times.sunrise.format(formatter)))
        add(prayerRow(Prayer.DHUHR, times.dhuhr, 1))
        add(prayerRow(Prayer.ASR, asr, 2))
        add(prayerRow(Prayer.MAGHRIB, times.maghrib, 3))
        add(prayerRow(Prayer.ISHA, times.isha, 4))
    }
}
