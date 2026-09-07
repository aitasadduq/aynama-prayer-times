package com.aynama.prayertimes.shared

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.data.DateComponents
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

enum class CalculationMethodKey {
    MWL, ISNA, UMM_AL_QURA, EGYPTIAN, KARACHI,
    DUBAI, MOON_SIGHTING_COMMITTEE, KUWAIT, QATAR, SINGAPORE
}

data class PrayerTimesResult(
    val fajr: LocalTime,
    val sunrise: LocalTime,
    val dhuhr: LocalTime,
    val asrShafii: LocalTime,
    val asrHanafi: LocalTime,
    val maghrib: LocalTime,
    val isha: LocalTime,
)

/**
 * No prayer times exist for this location and date.
 *
 * Inside the polar circles the sun can stay continuously above or below the horizon, leaving
 * sunrise and sunset undefined; adhan then returns null for every time, not just the twilight
 * ones. Measured with adhan 1.2.1 and MWL, this begins between 65.5°N and 65.75°N around the
 * June solstice and near 69°N around the December solstice, mirrored in the south.
 *
 * The library's [com.batoulapps.adhan.HighLatitudeRule] settings do not help — they reshape Fajr
 * and Isha when twilight is not reached, but cannot invent a sunrise that never happens. Choosing
 * what to display instead (nearest latitude, nearest day, fixed day proportions) is a convention
 * decision this app has not made yet, so callers must handle absence rather than assume times.
 */
class PrayerTimesUnavailableException(
    val latitude: Double,
    val date: LocalDate,
) : Exception(
    "No prayer times at latitude $latitude on $date: " +
        "the sun does not both rise and set at this location on this date",
)

class AdhanWrapper {

    fun getPrayerTimes(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        timezone: ZoneId,
        method: CalculationMethodKey,
    ): PrayerTimesResult {
        require(latitude in -90.0..90.0) { "latitude must be in [-90, 90]" }
        require(longitude in -180.0..180.0) { "longitude must be in [-180, 180]" }

        val coords = Coordinates(latitude, longitude)
        val dateComponents = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        val adhanMethod = mapMethod(method)

        val paramsShafii = adhanMethod.parameters.also { it.madhab = Madhab.SHAFI }
        val timesShafii = PrayerTimes(coords, dateComponents, paramsShafii)

        val paramsHanafi = adhanMethod.parameters.also { it.madhab = Madhab.HANAFI }
        val timesHanafi = PrayerTimes(coords, dateComponents, paramsHanafi)

        // adhan returns null for every time when the sun does not both rise and set. These are
        // Java platform types, so reading them straight into PrayerTimesResult throws a bare
        // NullPointerException from deep inside the mapping; bind them first and fail with
        // something a caller can catch and explain. See PrayerTimesUnavailableException.
        val fajr = timesShafii.fajr
        val sunrise = timesShafii.sunrise
        val dhuhr = timesShafii.dhuhr
        val asrShafii = timesShafii.asr
        val maghrib = timesShafii.maghrib
        val isha = timesShafii.isha
        val asrHanafi = timesHanafi.asr
        if (fajr == null || sunrise == null || dhuhr == null || asrShafii == null ||
            maghrib == null || isha == null || asrHanafi == null
        ) {
            throw PrayerTimesUnavailableException(latitude, date)
        }

        return PrayerTimesResult(
            fajr = fajr.toLocalTime(timezone),
            sunrise = sunrise.toLocalTime(timezone),
            dhuhr = dhuhr.toLocalTime(timezone),
            asrShafii = asrShafii.toLocalTime(timezone),
            asrHanafi = asrHanafi.toLocalTime(timezone),
            maghrib = maghrib.toLocalTime(timezone),
            isha = isha.toLocalTime(timezone),
        )
    }

    // adhan-java builds its Dates from Calendar.getInstance() and never clears the
    // MILLISECOND field, so every result carries the wall-clock millisecond of the
    // call. Two calls for the same day disagree by a few ms, which is enough to make
    // an alarm armed at one call's prayer instant recompute to the *same* prayer when
    // it fires. Seconds are always :00, so truncating drops only that noise.
    private fun Date.toLocalTime(timezone: ZoneId): LocalTime =
        toInstant().atZone(timezone).toLocalTime().truncatedTo(ChronoUnit.SECONDS)

    private fun mapMethod(key: CalculationMethodKey): CalculationMethod = when (key) {
        CalculationMethodKey.MWL -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        CalculationMethodKey.ISNA -> CalculationMethod.NORTH_AMERICA
        CalculationMethodKey.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
        CalculationMethodKey.EGYPTIAN -> CalculationMethod.EGYPTIAN
        CalculationMethodKey.KARACHI -> CalculationMethod.KARACHI
        CalculationMethodKey.DUBAI -> CalculationMethod.DUBAI
        CalculationMethodKey.MOON_SIGHTING_COMMITTEE -> CalculationMethod.MOON_SIGHTING_COMMITTEE
        CalculationMethodKey.KUWAIT -> CalculationMethod.KUWAIT
        CalculationMethodKey.QATAR -> CalculationMethod.QATAR
        CalculationMethodKey.SINGAPORE -> CalculationMethod.SINGAPORE
    }
}
