package com.aynama.prayertimes.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import com.aynama.prayertimes.shared.data.repository.QazaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class TrackerPrayerRow(
    val prayer: Prayer,
    val scheduledTime: String,
    val status: QazaStatus?,
)

data class DayState(
    val date: LocalDate,
    val prayers: Map<Prayer, QazaStatus?>,
    val prayedCount: Int,
    val isExpanded: Boolean,
    val expandedRows: List<TrackerPrayerRow>,
)

data class WeekSection(
    val label: String,
    val aggregate: String?,
    val days: List<DayState>,
)

sealed interface TrackerUiState {
    data object Loading : TrackerUiState
    data object Empty : TrackerUiState
    data class Loaded(
        val profileId: Long,
        val profileName: String,
        val todayRows: List<TrackerPrayerRow>,
        val outstandingCount: Int,
        val weeks: List<WeekSection>,
    ) : TrackerUiState
}

@Suppress("OPT_IN_USAGE")
class TrackerViewModel(
    private val profileRepository: ProfileRepository,
    private val qazaRepository: QazaRepository,
) : ViewModel() {

    private val adhan = AdhanWrapper()
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val prayerTimesCache = mutableMapOf<Pair<Long, LocalDate>, PrayerTimesResult>()

    private val expandedDays = MutableStateFlow<Set<LocalDate>>(emptySet())

    private val _uiState = MutableStateFlow<TrackerUiState>(TrackerUiState.Loading)
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    init {
        profileRepository.observeAll()
            .flatMapLatest { profiles ->
                val profile = profiles.firstOrNull()
                    ?: return@flatMapLatest flowOf(TrackerUiState.Empty)
                val today = LocalDate.now()
                val historyStart = today
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .minusWeeks(3)
                combine(
                    qazaRepository.observeByDateRange(profile.id, historyStart, today),
                    qazaRepository.observeOutstandingCount(profile.id),
                    expandedDays,
                ) { entries, outstandingCount, expanded ->
                    buildUiState(profile, today, historyStart, entries, outstandingCount, expanded)
                }
            }
            .catch { e -> _uiState.value = TrackerUiState.Empty }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun toggleExpansion(date: LocalDate) {
        expandedDays.update { if (date in it) it - date else it + date }
    }

    fun markPrayer(profileId: Long, prayer: Prayer, date: LocalDate, status: QazaStatus) {
        viewModelScope.launch {
            qazaRepository.markPrayer(profileId, prayer, date, status)
        }
    }

    private fun buildUiState(
        profile: Profile,
        today: LocalDate,
        historyStart: LocalDate,
        allEntries: List<com.aynama.prayertimes.shared.data.entity.QazaEntry>,
        outstandingCount: Int,
        expandedDays: Set<LocalDate>,
    ): TrackerUiState.Loaded {
        val byDate = allEntries.groupBy { it.date }
            .mapValues { (_, list) -> list.associate { it.prayer to it.status } }

        val todayTimes = cachedPrayerTimes(profile, today)
        val todayEntries = byDate[today] ?: emptyMap()
        val todayRows = Prayer.entries.map { prayer ->
            TrackerPrayerRow(
                prayer = prayer,
                scheduledTime = todayTimes.timeFor(prayer, profile.asrMadhab).format(timeFormatter),
                status = todayEntries[prayer],
            )
        }

        val weeks = buildWeekSections(profile, today, historyStart, byDate, expandedDays)

        return TrackerUiState.Loaded(
            profileId = profile.id,
            profileName = profile.name,
            todayRows = todayRows,
            outstandingCount = outstandingCount,
            weeks = weeks,
        )
    }

    private fun buildWeekSections(
        profile: Profile,
        today: LocalDate,
        historyStart: LocalDate,
        byDate: Map<LocalDate, Map<Prayer, QazaStatus>>,
        expandedDays: Set<LocalDate>,
    ): List<WeekSection> {
        val yesterday = today.minusDays(1)
        if (yesterday < historyStart) return emptyList()

        val thisWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val allHistoryDays = generateSequence(historyStart) { it.plusDays(1) }
            .takeWhile { it <= yesterday }
            .toList()
            .reversed()

        return allHistoryDays
            .groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .entries
            .sortedByDescending { it.key }
            .map { (weekMonday, days) ->
                val isCurrentWeek = weekMonday == thisWeekMonday
                val dayStates = days.map { date ->
                    buildDayState(profile, date, byDate[date] ?: emptyMap(), date in expandedDays)
                }
                val aggregate = if (isCurrentWeek) buildAggregate(dayStates) else null
                WeekSection(
                    label = weekLabel(weekMonday, thisWeekMonday),
                    aggregate = aggregate,
                    days = dayStates,
                )
            }
    }

    private fun buildDayState(
        profile: Profile,
        date: LocalDate,
        entries: Map<Prayer, QazaStatus>,
        isExpanded: Boolean,
    ): DayState {
        val prayers: Map<Prayer, QazaStatus?> = Prayer.entries.associateWith { entries[it] }
        val prayedCount = prayers.values.count { status ->
            status == QazaStatus.PRAYED_ON_TIME || status == QazaStatus.MADE_UP
        }
        val expandedRows = if (isExpanded) {
            val times = cachedPrayerTimes(profile, date)
            Prayer.entries.map { prayer ->
                TrackerPrayerRow(
                    prayer = prayer,
                    scheduledTime = times.timeFor(prayer, profile.asrMadhab).format(timeFormatter),
                    status = entries[prayer],
                )
            }
        } else emptyList()

        return DayState(
            date = date,
            prayers = prayers,
            prayedCount = prayedCount,
            isExpanded = isExpanded,
            expandedRows = expandedRows,
        )
    }

    private fun buildAggregate(days: List<DayState>): String {
        val onTimeCount = days.sumOf { day ->
            day.prayers.values.count { it == QazaStatus.PRAYED_ON_TIME }
        }
        val total = days.size * 5
        return "$onTimeCount of $total prayers on time this week"
    }

    private fun cachedPrayerTimes(profile: Profile, date: LocalDate): PrayerTimesResult =
        prayerTimesCache.getOrPut(profile.id to date) {
            adhan.getPrayerTimes(
                latitude = profile.latitude,
                longitude = profile.longitude,
                date = date,
                timezone = ZoneId.systemDefault(),
                method = profile.calculationMethod,
            )
        }

    companion object {
        fun factory(app: AynamaApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TrackerViewModel(app.profileRepository, app.qazaRepository) as T
            }
    }
}

private fun PrayerTimesResult.timeFor(prayer: Prayer, asrMadhab: AsrMadhab) = when (prayer) {
    Prayer.FAJR -> fajr
    Prayer.DHUHR -> dhuhr
    Prayer.ASR -> if (asrMadhab == AsrMadhab.HANAFI) asrHanafi else asrShafii
    Prayer.MAGHRIB -> maghrib
    Prayer.ISHA -> isha
}

internal fun weekLabel(weekMonday: LocalDate, thisWeekMonday: LocalDate): String {
    val lastWeekMonday = thisWeekMonday.minusWeeks(1)
    return when (weekMonday) {
        thisWeekMonday -> "This week"
        lastWeekMonday -> "Last week"
        else -> {
            val weekEnd = weekMonday.plusDays(6)
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            "${weekMonday.format(fmt)}–${weekEnd.format(fmt)}"
        }
    }
}
