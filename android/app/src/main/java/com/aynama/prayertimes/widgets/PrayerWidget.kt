package com.aynama.prayertimes.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.StyleSpan
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.fillMaxSize
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.MainActivity
import com.aynama.prayertimes.R
import com.aynama.prayertimes.notifications.NotificationPreferences
import com.aynama.prayertimes.notifications.RamadanDetector
import com.aynama.prayertimes.notifications.resolveNotificationProfile
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Base receiver ----------------------------------------------------------
// Cleans up per-widget profile preference when a widget instance is deleted.

abstract class PrayerWidgetReceiverBase : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = (context.applicationContext as AynamaApplication).prefs
        appWidgetIds.forEach { WidgetProfilePreferences.clear(prefs, it) }
    }
}

// --- Glance widgets ---------------------------------------------------------
// Four independent widgets; SizeMode.Single so the layout only stretches.

class NextPrayerWidgetReceiver : PrayerWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerGlanceWidget()
}

class NextPrayerDatedWidgetReceiver : PrayerWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerDatedGlanceWidget()
}

class ScheduleWidgetReceiver : PrayerWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleGlanceWidget()
}

class FullWidgetReceiver : PrayerWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = FullGlanceWidget()
}

class NextPrayerGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadPrayerWidgetState(context, appWidgetId)
        provideContent { RemoteViewsContent(PrayerWidgetRemoteViews.nextPrayer(context, state)) }
    }
}

class NextPrayerDatedGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadPrayerWidgetState(context, appWidgetId)
        provideContent { RemoteViewsContent(PrayerWidgetRemoteViews.nextPrayerDated(context, state)) }
    }
}

class ScheduleGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadPrayerWidgetState(context, appWidgetId)
        provideContent { RemoteViewsContent(PrayerWidgetRemoteViews.schedule(context, state)) }
    }
}

class FullGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadPrayerWidgetState(context, appWidgetId)
        provideContent { RemoteViewsContent(PrayerWidgetRemoteViews.full(context, state)) }
    }
}

/** Refresh every widget category. */
suspend fun updateAllPrayerWidgets(context: Context) {
    NextPrayerGlanceWidget().updateAll(context)
    NextPrayerDatedGlanceWidget().updateAll(context)
    ScheduleGlanceWidget().updateAll(context)
    FullGlanceWidget().updateAll(context)
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
private fun RemoteViewsContent(remoteViews: RemoteViews) {
    AndroidRemoteViews(remoteViews, GlanceModifier.fillMaxSize())
}

// --- State loading ----------------------------------------------------------

private suspend fun loadPrayerWidgetState(context: Context, appWidgetId: Int): PrayerWidgetState {
    val app = context.applicationContext as AynamaApplication
    val profiles = app.profileRepository.observeAll().first()

    // Per-widget profile takes priority; fall back to the global notification profile.
    val widgetProfileId = WidgetProfilePreferences.getProfileId(app.prefs, appWidgetId)
    val profile = if (widgetProfileId != -1L) {
        profiles.find { it.id == widgetProfileId }
    } else {
        val prefs = NotificationPreferences(app.prefs)
        resolveNotificationProfile(prefs.notificationProfileId, profiles)
    } ?: return PrayerWidgetState.empty()

    val zone = profile.effectiveZoneId()
    val now = ZonedDateTime.now(zone)
    val today = now.toLocalDate()
    val adhan = AdhanWrapper()
    val todayTimes = adhan.getPrayerTimes(
        latitude = profile.latitude,
        longitude = profile.longitude,
        date = today,
        timezone = zone,
        method = profile.calculationMethod,
    )
    val tomorrowTimes = adhan.getPrayerTimes(
        latitude = profile.latitude,
        longitude = profile.longitude,
        date = today.plusDays(1),
        timezone = zone,
        method = profile.calculationMethod,
    )
    val offset = RamadanDetector.effectiveHijriOffset(
        profile.hijriOffset, profile.hijriOffsetMonthKey, today, zone,
    )
    val timeFormatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        Locale.getDefault(),
    )
    return buildPrayerWidgetState(
        profile = profile,
        todayTimes = todayTimes,
        tomorrowTimes = tomorrowTimes,
        now = now,
        elapsedRealtime = SystemClock.elapsedRealtime(),
        timeFormatter = timeFormatter,
        hijriDateText = RamadanDetector.hijriDateWithOffset(today, offset, zone),
    )
}

// --- State model ------------------------------------------------------------

internal data class WidgetScheduleRow(
    val name: String,
    val abbreviation: String,
    val time: LocalTime,
    val displayTime: String,
)

internal data class PrayerWidgetState(
    val profileName: String,
    val nextPrayerName: String,
    val nextPrayerAbbreviation: String,
    val nextPrayerDisplayTime: String,
    val currentPrayerName: String,
    val countdownBaseElapsedRealtime: Long,
    val gregorianDateText: String,
    val hijriDateText: String,
    val sunriseDisplayTime: String,
    val schedule: List<WidgetScheduleRow>,
) {
    companion object {
        fun empty() = PrayerWidgetState(
            profileName = "Open aynama",
            nextPrayerName = "Set up profile",
            nextPrayerAbbreviation = "SET",
            nextPrayerDisplayTime = "--:--",
            currentPrayerName = "",
            countdownBaseElapsedRealtime = SystemClock.elapsedRealtime(),
            gregorianDateText = LocalDate.now().format(gregorianFormatter()),
            hijriDateText = "",
            sunriseDisplayTime = "--:--",
            schedule = emptyList(),
        )
    }
}

private data class TimelineEvent(
    val name: String,
    val abbreviation: String,
    val time: LocalTime,
    val obligatory: Boolean,
)

private fun gregorianFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

internal fun buildPrayerWidgetState(
    profile: Profile,
    todayTimes: PrayerTimesResult,
    tomorrowTimes: PrayerTimesResult,
    now: ZonedDateTime,
    elapsedRealtime: Long,
    timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US),
    hijriDateText: String = "",
): PrayerWidgetState {
    val today = now.toLocalDate()
    val nowInstant = now.toInstant()
    fun instantOf(date: LocalDate, time: LocalTime) = date.atTime(time).atZone(now.zone).toInstant()

    // Next event drives the countdown. It is the chronologically-soonest event strictly after now,
    // including Sunrise — determined by actual instants so it is correct regardless of whether the
    // displayed times happen to fall in canonical clock order (e.g. a far-from-device timezone).
    val nextCandidates =
        timelineEvents(todayTimes, profile.asrMadhab).map { it to instantOf(today, it.time) } +
            timelineEvents(tomorrowTimes, profile.asrMadhab).map { it to instantOf(today.plusDays(1), it.time) }
    val (next, nextInstant) = nextCandidates
        .filter { it.second.isAfter(nowInstant) }
        .minByOrNull { it.second }!!
    val millisUntilNext = Duration.between(nowInstant, nextInstant).toMillis().coerceAtLeast(0L)

    // Current (active) prayer = the most recently started obligatory prayer. Searched over yesterday
    // and today (yesterday handles the pre-Fajr window where last night's Isha is still current).
    val obligatory = timelineEvents(todayTimes, profile.asrMadhab).filter { it.obligatory }
    val currentCandidates =
        obligatory.map { it to instantOf(today.minusDays(1), it.time) } +
            obligatory.map { it to instantOf(today, it.time) }
    val currentPrayerName = currentCandidates
        .filter { !it.second.isAfter(nowInstant) }
        .maxByOrNull { it.second }
        ?.first?.name ?: ""

    return PrayerWidgetState(
        profileName = profile.name,
        nextPrayerName = next.name,
        nextPrayerAbbreviation = next.abbreviation,
        nextPrayerDisplayTime = next.time.format(timeFormatter),
        currentPrayerName = currentPrayerName,
        countdownBaseElapsedRealtime = elapsedRealtime + millisUntilNext,
        gregorianDateText = today.format(gregorianFormatter()),
        hijriDateText = hijriDateText,
        sunriseDisplayTime = todayTimes.sunrise.format(timeFormatter),
        schedule = scheduleRows(todayTimes, profile.asrMadhab, timeFormatter),
    )
}

internal fun scheduleRows(
    times: PrayerTimesResult,
    asrMadhab: AsrMadhab,
    formatter: DateTimeFormatter,
): List<WidgetScheduleRow> {
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    return listOf(
        WidgetScheduleRow("Fajr", "FAJ", times.fajr, times.fajr.format(formatter)),
        WidgetScheduleRow("Sunrise", "SUN", times.sunrise, times.sunrise.format(formatter)),
        WidgetScheduleRow("Dhuhr", "DHU", times.dhuhr, times.dhuhr.format(formatter)),
        WidgetScheduleRow("Asr", "ASR", asr, asr.format(formatter)),
        WidgetScheduleRow("Maghrib", "MAG", times.maghrib, times.maghrib.format(formatter)),
        WidgetScheduleRow("Isha", "ISH", times.isha, times.isha.format(formatter)),
    )
}

// The day's events in canonical order. Sunrise is included (it is a valid countdown target between
// Fajr and sunrise) but flagged non-obligatory so it is never marked as the "current prayer".
private fun timelineEvents(times: PrayerTimesResult, asrMadhab: AsrMadhab): List<TimelineEvent> {
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    return listOf(
        TimelineEvent("Fajr", "FAJ", times.fajr, obligatory = true),
        TimelineEvent("Sunrise", "SUN", times.sunrise, obligatory = false),
        TimelineEvent("Dhuhr", "DHU", times.dhuhr, obligatory = true),
        TimelineEvent("Asr", "ASR", asr, obligatory = true),
        TimelineEvent("Maghrib", "MAG", times.maghrib, obligatory = true),
        TimelineEvent("Isha", "ISH", times.isha, obligatory = true),
    )
}

// --- RemoteViews builders ---------------------------------------------------

private object PrayerWidgetRemoteViews {
    private val scheduleNameIds = intArrayOf(
        R.id.widget_row_1_name, R.id.widget_row_2_name, R.id.widget_row_3_name,
        R.id.widget_row_4_name, R.id.widget_row_5_name, R.id.widget_row_6_name,
    )
    private val scheduleTimeIds = intArrayOf(
        R.id.widget_row_1_time, R.id.widget_row_2_time, R.id.widget_row_3_time,
        R.id.widget_row_4_time, R.id.widget_row_5_time, R.id.widget_row_6_time,
    )
    private val columnNameIds = intArrayOf(
        R.id.widget_col_1_name, R.id.widget_col_2_name, R.id.widget_col_3_name,
        R.id.widget_col_4_name, R.id.widget_col_5_name,
    )
    private val columnTimeIds = intArrayOf(
        R.id.widget_col_1_time, R.id.widget_col_2_time, R.id.widget_col_3_time,
        R.id.widget_col_4_time, R.id.widget_col_5_time,
    )

    fun nextPrayer(context: Context, state: PrayerWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_next_prayer).apply {
            setTextViewText(R.id.widget_next_name, state.nextPrayerName)
            setTextViewText(R.id.widget_next_time, state.nextPrayerDisplayTime)
            setCountdown(state)
            setTextViewText(R.id.widget_profile, state.profileName)
            bindRoot(context)
        }

    fun nextPrayerDated(context: Context, state: PrayerWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_next_prayer_dated).apply {
            setDates(state)
            setTextViewText(R.id.widget_next_name, state.nextPrayerName)
            setTextViewText(R.id.widget_next_time, state.nextPrayerDisplayTime)
            setCountdown(state)
            setTextViewText(R.id.widget_profile, state.profileName)
            bindRoot(context)
        }

    fun schedule(context: Context, state: PrayerWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_schedule).apply {
            setDates(state)
            scheduleNameIds.forEachIndexed { index, id ->
                setTextViewText(id, state.schedule.getOrNull(index)?.name ?: "")
            }
            scheduleTimeIds.forEachIndexed { index, id ->
                setTextViewText(id, state.schedule.getOrNull(index)?.displayTime ?: "")
            }
            setTextViewText(R.id.widget_profile, state.profileName)
            bindRoot(context)
        }

    fun full(context: Context, state: PrayerWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_full).apply {
            setDates(state)
            setTextViewText(R.id.widget_sunrise_time, state.sunriseDisplayTime)

            val saffron = context.getColor(R.color.aynama_saffron)
            val ink = context.getColor(R.color.aynama_ink)
            val inkMuted = context.getColor(R.color.aynama_ink_muted)
            val obligatory = state.schedule.filter { it.name != "Sunrise" }
            columnNameIds.indices.forEach { index ->
                val row = obligatory.getOrNull(index)
                val highlighted = row != null && row.name == state.currentPrayerName
                setTextViewText(columnNameIds[index], maybeBold(row?.name ?: "", highlighted))
                setTextViewText(columnTimeIds[index], maybeBold(row?.displayTime ?: "", highlighted))
                setTextColor(columnNameIds[index], if (highlighted) saffron else ink)
                setTextColor(columnTimeIds[index], if (highlighted) saffron else inkMuted)
            }

            setCountdown(state)
            setTextViewText(R.id.widget_until, context.getString(R.string.widget_until, state.nextPrayerName))
            setTextViewText(R.id.widget_profile, state.profileName)
            bindRoot(context)
        }

    private fun maybeBold(text: String, bold: Boolean): CharSequence {
        if (!bold || text.isEmpty()) return text
        return SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun RemoteViews.setDates(state: PrayerWidgetState) {
        setTextViewText(R.id.widget_gregorian, state.gregorianDateText)
        setTextViewText(R.id.widget_hijri, state.hijriDateText)
    }

    private fun RemoteViews.setCountdown(state: PrayerWidgetState) {
        setChronometer(R.id.widget_countdown, state.countdownBaseElapsedRealtime, null, true)
        setChronometerCountDown(R.id.widget_countdown, true)
    }

    private fun RemoteViews.bindRoot(context: Context) {
        setOnClickPendingIntent(R.id.widget_root, openHomePendingIntent(context))
    }

    private fun openHomePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_HOME_FROM_WIDGET
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private const val ACTION_OPEN_HOME_FROM_WIDGET = "com.aynama.prayertimes.widgets.OPEN_HOME"
