package com.aynama.prayertimes.home

import android.content.SharedPreferences
import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import com.aynama.prayertimes.shared.data.repository.QazaRepository
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
        override val displayTime: String,
        val ribbonState: RibbonState,
    ) : RibbonRow

    data class SunriseEntry(override val displayTime: String) : RibbonRow

    data class ImsakEntry(override val displayTime: String, val isPast: Boolean) : RibbonRow
}

data class ProfileUiState(
    val profile: Profile,
    val ribbonRows: List<RibbonRow>,
    val countdownText: String,
    val nextPrayerName: String,
    val nextPrayerTime: String,
    val currentPhase: PrayerPhase,
    val isRamadan: Boolean,
    val showRamadanBanner: Boolean,
    val outstandingQazaCount: Int,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val cause: String) : HomeUiState
    data class Loaded(val profiles: List<ProfileUiState>) : HomeUiState
}

class HomeViewModel(
    private val profileRepository: ProfileRepository,
    private val qazaRepository: QazaRepository,
    private val prefs: SharedPreferences,
) : ViewModel() {

    private val adhan = AdhanWrapper()
    private val prayerTimesCache = mutableMapOf<Long, Pair<LocalDate, PrayerTimesResult>>()
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val clockFlow: Flow<LocalTime> = flow {
        while (true) {
            emit(LocalTime.now())
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
            val today = LocalDate.now()
            val hijriYear = currentHijriYear()
            val dismissedYear = prefs.getInt(KEY_RAMADAN_BANNER_YEAR, -1)
            HomeUiState.Loaded(
                profiles.map { profile ->
                    val times = cachedPrayerTimes(profile, today)
                    buildProfileUiState(profile, times, now, today, qazaCounts[profile.id] ?: 0, hijriYear, dismissedYear)
                }
            )
        }
            .catch { e -> _uiState.value = HomeUiState.Error(e.message ?: "Unknown error") }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun dismissRamadanBanner() {
        prefs.edit().putInt(KEY_RAMADAN_BANNER_YEAR, currentHijriYear()).apply()
    }

    fun markPrayer(profileId: Long, prayer: Prayer, date: LocalDate, status: QazaStatus) {
        viewModelScope.launch { qazaRepository.markPrayer(profileId, prayer, date, status) }
    }

    private fun cachedPrayerTimes(profile: Profile, today: LocalDate): PrayerTimesResult {
        val cached = prayerTimesCache[profile.id]
        if (cached != null && cached.first == today) return cached.second
        val result = adhan.getPrayerTimes(
            latitude = profile.latitude,
            longitude = profile.longitude,
            date = today,
            timezone = ZoneId.systemDefault(),
            method = profile.calculationMethod,
        )
        prayerTimesCache[profile.id] = today to result
        return result
    }

    private fun buildProfileUiState(
        profile: Profile,
        times: PrayerTimesResult,
        now: LocalTime,
        today: LocalDate,
        qazaCount: Int,
        hijriYear: Int,
        dismissedYear: Int,
    ): ProfileUiState {
        val ramadan = isRamadan(today)
        return ProfileUiState(
            profile = profile,
            ribbonRows = deriveRibbonRows(times, profile.asrMadhab, now, ramadan, timeFormatter),
            countdownText = deriveCountdown(times, profile.asrMadhab, now),
            nextPrayerName = deriveNextPrayerName(times, profile.asrMadhab, now),
            nextPrayerTime = deriveNextPrayerTime(times, profile.asrMadhab, now, timeFormatter),
            currentPhase = derivePhase(times, profile.asrMadhab, now),
            isRamadan = ramadan,
            showRamadanBanner = ramadan && dismissedYear != hijriYear,
            outstandingQazaCount = qazaCount,
        )
    }

    companion object {
        private const val KEY_RAMADAN_BANNER_YEAR = "ramadan_banner_dismissed_year"

        fun factory(app: AynamaApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(app.profileRepository, app.qazaRepository, app.prefs) as T
            }
    }
}

// Pure functions — internal for testability

internal fun currentHijriYear(): Int =
    IslamicCalendar().get(Calendar.YEAR)

internal fun isRamadan(date: LocalDate): Boolean {
    val instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
    val ic = IslamicCalendar(java.util.Date.from(instant))
    return ic.get(Calendar.MONTH) == IslamicCalendar.RAMADAN
}

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

internal fun deriveNextPrayerName(times: PrayerTimesResult, asrMadhab: AsrMadhab, now: LocalTime): String {
    if (times.isha < times.fajr && now < times.isha) return "Isha"
    if (now >= times.fajr && now < times.sunrise) return "Sunrise"
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    val ordered = listOf(
        times.fajr to "Fajr",
        times.dhuhr to "Dhuhr",
        asr to "Asr",
        times.maghrib to "Maghrib",
        times.isha to "Isha",
    )
    return ordered.firstOrNull { (t, _) -> t > now }?.second
        ?: if (times.isha < times.fajr) "Isha" else "Fajr"
}

internal fun deriveCountdown(times: PrayerTimesResult, asrMadhab: AsrMadhab, now: LocalTime): String {
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    val ordered = listOf(times.fajr, times.dhuhr, asr, times.maghrib, times.isha)
    val next = if (times.isha < times.fajr && now < times.isha) times.isha
               else if (now >= times.fajr && now < times.sunrise) times.sunrise
               else ordered.firstOrNull { it > now }
    val totalSeconds = if (next != null) {
        ChronoUnit.SECONDS.between(now, next)
    } else if (times.isha < times.fajr) {
        ChronoUnit.SECONDS.between(now, LocalTime.MAX) + 1 +
            ChronoUnit.SECONDS.between(LocalTime.MIDNIGHT, times.isha)
    } else {
        ChronoUnit.SECONDS.between(now, LocalTime.MAX) + 1 +
            ChronoUnit.SECONDS.between(LocalTime.MIDNIGHT, times.fajr)
    }
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

internal fun deriveNextPrayerTime(
    times: PrayerTimesResult,
    asrMadhab: AsrMadhab,
    now: LocalTime,
    formatter: DateTimeFormatter,
): String {
    if (times.isha < times.fajr && now < times.isha) return times.isha.format(formatter)
    if (now >= times.fajr && now < times.sunrise) return times.sunrise.format(formatter)
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    val ordered = listOf(times.fajr, times.dhuhr, asr, times.maghrib, times.isha)
    val fallback = if (times.isha < times.fajr) times.isha else times.fajr
    return (ordered.firstOrNull { it > now } ?: fallback).format(formatter)
}

internal fun deriveRibbonRows(
    times: PrayerTimesResult,
    asrMadhab: AsrMadhab,
    now: LocalTime,
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
        add(RibbonRow.PrayerEntry(Prayer.FAJR, times.fajr.format(formatter), stateAt(0)))
        add(RibbonRow.SunriseEntry(times.sunrise.format(formatter)))
        add(RibbonRow.PrayerEntry(Prayer.DHUHR, times.dhuhr.format(formatter), stateAt(1)))
        add(RibbonRow.PrayerEntry(Prayer.ASR, asr.format(formatter), stateAt(2)))
        add(RibbonRow.PrayerEntry(Prayer.MAGHRIB, times.maghrib.format(formatter), stateAt(3)))
        add(RibbonRow.PrayerEntry(Prayer.ISHA, times.isha.format(formatter), stateAt(4)))
    }
}
