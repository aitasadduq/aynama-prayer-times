package com.aynama.prayertimes.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.MainActivity
import com.aynama.prayertimes.R
import com.aynama.prayertimes.notifications.NotificationPreferences
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

class PrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerGlanceWidget()
}

class PrayerGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(80.dp, 80.dp),
            DpSize(160.dp, 160.dp),
            DpSize(320.dp, 160.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadPrayerWidgetState(context)
        provideContent {
            PrayerWidgetContent(context, state)
        }
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
private fun PrayerWidgetContent(context: Context, state: PrayerWidgetState) {
    val remoteViews = PrayerWidgetRemoteViews.create(
        context = context,
        state = state,
        size = WidgetSize.from(LocalSize.current),
    )
    AndroidRemoteViews(remoteViews, GlanceModifier.fillMaxSize())
}

private suspend fun loadPrayerWidgetState(context: Context): PrayerWidgetState {
    val app = context.applicationContext as AynamaApplication
    val profiles = app.profileRepository.observeAll().first()
    val prefs = NotificationPreferences(app.prefs)
    val profile = resolveNotificationProfile(prefs.notificationProfileId, profiles)
        ?: return PrayerWidgetState.empty()

    val zone = profile.effectiveZoneId()
    val now = ZonedDateTime.now(zone)
    val adhan = AdhanWrapper()
    val todayTimes = adhan.getPrayerTimes(
        latitude = profile.latitude,
        longitude = profile.longitude,
        date = now.toLocalDate(),
        timezone = zone,
        method = profile.calculationMethod,
    )
    val tomorrowTimes = adhan.getPrayerTimes(
        latitude = profile.latitude,
        longitude = profile.longitude,
        date = now.toLocalDate().plusDays(1),
        timezone = zone,
        method = profile.calculationMethod,
    )
    return buildPrayerWidgetState(
        profile = profile,
        todayTimes = todayTimes,
        tomorrowTimes = tomorrowTimes,
        now = now,
        elapsedRealtime = SystemClock.elapsedRealtime(),
    )
}

internal enum class WidgetSize {
    SMALL,
    MEDIUM,
    LARGE;

    companion object {
        fun from(size: DpSize): WidgetSize = when {
            size.width < 120.dp || size.height < 120.dp -> SMALL
            size.width >= 260.dp -> LARGE
            else -> MEDIUM
        }
    }
}

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
    val countdownBaseElapsedRealtime: Long,
    val schedule: List<WidgetScheduleRow>,
) {
    companion object {
        fun empty() = PrayerWidgetState(
            profileName = "Open aynama",
            nextPrayerName = "Set up profile",
            nextPrayerAbbreviation = "SET",
            nextPrayerDisplayTime = "--:--",
            countdownBaseElapsedRealtime = SystemClock.elapsedRealtime(),
            schedule = emptyList(),
        )
    }
}

private data class PrayerMoment(
    val name: String,
    val abbreviation: String,
    val time: LocalTime,
    val date: LocalDate,
)

internal fun buildPrayerWidgetState(
    profile: Profile,
    todayTimes: PrayerTimesResult,
    tomorrowTimes: PrayerTimesResult,
    now: ZonedDateTime,
    elapsedRealtime: Long,
): PrayerWidgetState {
    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    val today = now.toLocalDate()
    val todayPrayers = prayerMoments(today, todayTimes, profile.asrMadhab)
    val tomorrowPrayers = prayerMoments(today.plusDays(1), tomorrowTimes, profile.asrMadhab)
    val next = (todayPrayers + tomorrowPrayers).first { moment ->
        moment.date.atTime(moment.time).atZone(now.zone).toInstant() > now.toInstant()
    }
    val nextDateTime = next.date.atTime(next.time).atZone(now.zone)
    val millisUntilNext = Duration.between(now, nextDateTime).toMillis().coerceAtLeast(0L)

    return PrayerWidgetState(
        profileName = profile.name,
        nextPrayerName = next.name,
        nextPrayerAbbreviation = next.abbreviation,
        nextPrayerDisplayTime = next.time.format(formatter),
        countdownBaseElapsedRealtime = elapsedRealtime + millisUntilNext,
        schedule = scheduleRows(todayTimes, profile.asrMadhab, formatter),
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

private fun prayerMoments(date: LocalDate, times: PrayerTimesResult, asrMadhab: AsrMadhab): List<PrayerMoment> {
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    val ishaDate = if (times.isha < times.fajr) date.plusDays(1) else date
    return listOf(
        PrayerMoment("Fajr", "FAJ", times.fajr, date),
        PrayerMoment("Dhuhr", "DHU", times.dhuhr, date),
        PrayerMoment("Asr", "ASR", asr, date),
        PrayerMoment("Maghrib", "MAG", times.maghrib, date),
        PrayerMoment("Isha", "ISH", times.isha, ishaDate),
    )
}

private object PrayerWidgetRemoteViews {
    private val rowNameIds = intArrayOf(
        R.id.widget_row_1_name,
        R.id.widget_row_2_name,
        R.id.widget_row_3_name,
        R.id.widget_row_4_name,
        R.id.widget_row_5_name,
        R.id.widget_row_6_name,
    )
    private val rowTimeIds = intArrayOf(
        R.id.widget_row_1_time,
        R.id.widget_row_2_time,
        R.id.widget_row_3_time,
        R.id.widget_row_4_time,
        R.id.widget_row_5_time,
        R.id.widget_row_6_time,
    )

    fun create(context: Context, state: PrayerWidgetState, size: WidgetSize): RemoteViews =
        when (size) {
            WidgetSize.SMALL -> small(context, state)
            WidgetSize.MEDIUM -> medium(context, state)
            WidgetSize.LARGE -> large(context, state)
        }.apply {
            setOnClickPendingIntent(R.id.widget_root, openHomePendingIntent(context))
        }

    private fun small(context: Context, state: PrayerWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_1x1).apply {
            setTextViewText(R.id.widget_abbreviation, state.nextPrayerAbbreviation.take(3).uppercase(Locale.US))
            setTextViewText(R.id.widget_time, state.nextPrayerDisplayTime)
        }

    private fun medium(context: Context, state: PrayerWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_2x2).apply {
            setTextViewText(R.id.widget_next_name, state.nextPrayerName)
            setTextViewText(R.id.widget_profile, state.profileName)
            setCountdown(state)
        }

    private fun large(context: Context, state: PrayerWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_4x2).apply {
            setTextViewText(R.id.widget_next_name, state.nextPrayerName)
            setCountdown(state)
            state.schedule.take(6).forEachIndexed { index, row ->
                setTextViewText(rowNameIds[index], row.abbreviation)
                setTextViewText(rowTimeIds[index], row.displayTime)
            }
        }

    private fun RemoteViews.setCountdown(state: PrayerWidgetState) {
        setChronometer(R.id.widget_countdown, state.countdownBaseElapsedRealtime, null, true)
        setChronometerCountDown(R.id.widget_countdown, true)
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
