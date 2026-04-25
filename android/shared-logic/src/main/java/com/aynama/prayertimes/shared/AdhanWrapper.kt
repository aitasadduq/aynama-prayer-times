package com.aynama.prayertimes.shared

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.data.DateComponents
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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

        return PrayerTimesResult(
            fajr = timesShafii.fajr.toInstant().atZone(timezone).toLocalTime(),
            sunrise = timesShafii.sunrise.toInstant().atZone(timezone).toLocalTime(),
            dhuhr = timesShafii.dhuhr.toInstant().atZone(timezone).toLocalTime(),
            asrShafii = timesShafii.asr.toInstant().atZone(timezone).toLocalTime(),
            asrHanafi = timesHanafi.asr.toInstant().atZone(timezone).toLocalTime(),
            maghrib = timesShafii.maghrib.toInstant().atZone(timezone).toLocalTime(),
            isha = timesShafii.isha.toInstant().atZone(timezone).toLocalTime(),
        )
    }

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
