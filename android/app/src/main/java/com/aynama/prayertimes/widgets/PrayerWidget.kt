package com.aynama.prayertimes.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.StyleSpan
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.longPreferencesKey
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.MainActivity
import com.aynama.prayertimes.R
import com.aynama.prayertimes.notifications.NotificationPreferences
import com.aynama.prayertimes.notifications.RamadanDetector
import com.aynama.prayertimes.notifications.resolveNotificationProfile
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.PrayerTimesUnavailableException
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.shared.timeline.PrayerCountdown
import com.aynama.prayertimes.shared.timeline.TimelineEntry
import com.aynama.prayertimes.shared.timeline.TimelineEvent
import com.aynama.prayertimes.shared.timeline.buildTimeline
import com.aynama.prayertimes.shared.timeline.countdownAt
import com.aynama.prayertimes.shared.timeline.currentEntry
import com.aynama.prayertimes.shared.timeline.displayName
import com.aynama.prayertimes.shared.timeline.prayerDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Per-widget profile state ----------------------------------------------
// The chosen profile is stored in each widget's own Glance state, keyed by GlanceId.
// This is read directly in provideGlance(id) — no appWidgetId reverse-lookup — so a
// freshly placed widget always renders with the profile the configure screen wrote.
// Glance clears this state automatically when the widget is removed.

private val WIDGET_PROFILE_KEY = longPreferencesKey("widget_profile_id")
private const val NO_PROFILE = -1L

/** Read the profile chosen for a specific widget instance, or NO_PROFILE if unset. */
private suspend fun widgetProfileId(context: Context, id: GlanceId): Long =
    getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[WIDGET_PROFILE_KEY] ?: NO_PROFILE

/**
 * Persist the chosen profile for [appWidgetId] and render it immediately.
 *
 * The render is a direct AppWidgetManager.updateAppWidget pushed on the MAIN thread. Glance's
 * updateAll() does not reliably re-render on some OEM launchers (the widget only refreshes later
 * when the process restarts) — a main-thread direct push, applied as the final write, is reliable.
 * The Glance state is also written so later system-initiated renders stay correct.
 */
suspend fun setWidgetProfile(context: Context, appWidgetId: Int, profileId: Long) {
    // Resolve the provider first. This activity is exported (APPWIDGET_CONFIGURE requires it),
    // so any app can launch it with an appWidgetId we do not own — and getGlanceIdBy throws on
    // those. Matching the provider up front rejects foreign ids before anything can throw into
    // appScope, where nothing catches it.
    val className = AppWidgetManager.getInstance(context)
        .getAppWidgetInfo(appWidgetId)?.provider?.className
    val build: (Context, PrayerWidgetState) -> RemoteViews = when (className) {
        NextPrayerWidgetReceiver::class.java.name -> PrayerWidgetRemoteViews::nextPrayer
        NextPrayerDatedWidgetReceiver::class.java.name -> PrayerWidgetRemoteViews::nextPrayerDated
        ScheduleWidgetReceiver::class.java.name -> PrayerWidgetRemoteViews::schedule
        FullWidgetReceiver::class.java.name -> PrayerWidgetRemoteViews::full
        else -> return
    }

    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[WIDGET_PROFILE_KEY] = profileId
    }
    val rv = build(context, loadPrayerWidgetState(context, profileId))
    withContext(Dispatchers.Main) {
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, rv)
    }

    // This widget's profile just changed, so the armed rollover chains no longer match
    // what is on screen. Re-arm before returning.
    val app = context.applicationContext as AynamaApplication
    PrayerWidgetScheduler.scheduleForBoundProfiles(context, app.profileRepository.observeAll().first())
}

private val WIDGET_RECEIVERS = listOf(
    NextPrayerWidgetReceiver::class.java,
    NextPrayerDatedWidgetReceiver::class.java,
    ScheduleWidgetReceiver::class.java,
    FullWidgetReceiver::class.java,
)

/**
 * The distinct profiles that currently-placed widgets actually render.
 *
 * Mirrors loadPrayerWidgetState's resolution exactly, including its fallback to the
 * global notification profile for widgets with no per-instance choice. Sorted by id so
 * slot assignment is deterministic across calls. Empty when no widgets are placed.
 */
internal suspend fun boundWidgetProfiles(context: Context, profiles: List<Profile>): List<Profile> {
    if (profiles.isEmpty()) return emptyList()
    val mgr = AppWidgetManager.getInstance(context)
    val ids = WIDGET_RECEIVERS.flatMap { mgr.getAppWidgetIds(ComponentName(context, it)).toList() }
    if (ids.isEmpty()) return emptyList()

    val app = context.applicationContext as AynamaApplication
    val fallback = resolveNotificationProfile(NotificationPreferences(app.prefs).notificationProfileId, profiles)
    val resolved = LinkedHashSet<Profile>()
    for (id in ids) {
        val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(id) }.getOrNull()
            ?: continue
        val chosen = widgetProfileId(context, glanceId)
        val profile = profiles.firstOrNull { chosen != NO_PROFILE && it.id == chosen } ?: fallback ?: continue
        resolved += profile
    }
    return resolved.sortedBy { it.id }
}

/** Read the currently chosen profile for [appWidgetId] (for the configure screen), or NO_PROFILE. */
suspend fun widgetProfileIdFor(context: Context, appWidgetId: Int): Long =
    runCatching {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        widgetProfileId(context, glanceId)
    }.getOrDefault(NO_PROFILE)

// --- Glance widgets ---------------------------------------------------------
// Four independent widgets; SizeMode.Single so the layout only stretches.

class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerGlanceWidget()
}

class NextPrayerDatedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerDatedGlanceWidget()
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleGlanceWidget()
}

class FullWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FullGlanceWidget()
}

class NextPrayerGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadPrayerWidgetState(context, widgetProfileId(context, id))
        provideContent { RemoteViewsContent(PrayerWidgetRemoteViews.nextPrayer(context, state)) }
    }
}

class NextPrayerDatedGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadPrayerWidgetState(context, widgetProfileId(context, id))
        provideContent { RemoteViewsContent(PrayerWidgetRemoteViews.nextPrayerDated(context, state)) }
    }
}

class ScheduleGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadPrayerWidgetState(context, widgetProfileId(context, id))
        provideContent { RemoteViewsContent(PrayerWidgetRemoteViews.schedule(context, state)) }
    }
}

class FullGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadPrayerWidgetState(context, widgetProfileId(context, id))
        provideContent { RemoteViewsContent(PrayerWidgetRemoteViews.full(context, state)) }
    }
}

/**
 * Refresh every placed widget by pushing fresh RemoteViews directly (on the main thread).
 * Glance's updateAll() is unreliable on some OEM launchers; a direct updateAppWidget() is not, and
 * each widget keeps showing its own per-instance profile because the state is read per appWidgetId.
 */
suspend fun updateAllPrayerWidgets(context: Context) {
    val mgr = AppWidgetManager.getInstance(context)
    pushProvider(context, mgr, NextPrayerWidgetReceiver::class.java, PrayerWidgetRemoteViews::nextPrayer)
    pushProvider(context, mgr, NextPrayerDatedWidgetReceiver::class.java, PrayerWidgetRemoteViews::nextPrayerDated)
    pushProvider(context, mgr, ScheduleWidgetReceiver::class.java, PrayerWidgetRemoteViews::schedule)
    pushProvider(context, mgr, FullWidgetReceiver::class.java, PrayerWidgetRemoteViews::full)
}

private suspend fun pushProvider(
    context: Context,
    mgr: AppWidgetManager,
    receiver: Class<*>,
    build: (Context, PrayerWidgetState) -> RemoteViews,
) {
    val ids = mgr.getAppWidgetIds(ComponentName(context, receiver))
    if (ids.isEmpty()) return
    // getGlanceIdBy throws for ids Glance does not know, and there is a window between
    // getAppWidgetIds and this lookup (widget removed, restored from backup, state not yet
    // materialised). This runs from TimezoneReceiver and app startup on scopes with no
    // exception handler, so one stale id would take the process down on every TIME_SET.
    val updates = buildList {
        for (id in ids) {
            val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(id) }
                .getOrNull() ?: continue
            add(id to build(context, loadPrayerWidgetState(context, widgetProfileId(context, glanceId))))
        }
    }
    withContext(Dispatchers.Main) {
        updates.forEach { (id, rv) -> mgr.updateAppWidget(id, rv) }
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
private fun RemoteViewsContent(remoteViews: RemoteViews) {
    AndroidRemoteViews(remoteViews, GlanceModifier.fillMaxSize())
}

// --- State loading ----------------------------------------------------------

private suspend fun loadPrayerWidgetState(context: Context, profileId: Long): PrayerWidgetState {
    val app = context.applicationContext as AynamaApplication
    val profiles = app.profileRepository.observeAll().first()

    // The widget's chosen profile takes priority; fall back to the global notification profile.
    val profile = profiles.find { profileId != NO_PROFILE && it.id == profileId }
        ?: resolveNotificationProfile(NotificationPreferences(app.prefs).notificationProfileId, profiles)
        ?: return PrayerWidgetState.empty(SystemClock.elapsedRealtime())

    val zone = profile.effectiveZoneId()
    val now = ZonedDateTime.now(zone)
    val today = now.toLocalDate()
    // A widget bound to a location with no computable times must render a message, not throw:
    // this runs inside Glance's render and inside PrayerWidgetUpdateReceiver, and an escaping
    // throw from the receiver kills the process on every rollover alarm.
    val days = profileDays(profile, today)
    val todayTimes = days[today] ?: run {
        Log.w("PrayerWidget", "no times today for profile ${profile.id} (${profile.name})")
        return PrayerWidgetState.unavailable(SystemClock.elapsedRealtime(), profile.id, profile.name)
    }
    val offset = RamadanDetector.effectiveHijriOffset(
        profile.hijriOffset, profile.hijriOffsetMonthKey, today, zone,
    )
    val timeFormatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        Locale.getDefault(),
    )
    return buildPrayerWidgetState(
        profile = profile,
        days = days,
        todayTimes = todayTimes,
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
    /**
     * The profile this widget renders. Carried into the tap intent so the app opens *this*
     * widget's profile rather than whichever one the pager happens to start on.
     * [NO_WIDGET_PROFILE] when no profile could be resolved.
     */
    val profileId: Long,
    val profileName: String,
    /**
     * The prayer the countdown refers to: the one that just started while counting up, the
     * one coming next while counting down. DESIGN.md §19.
     */
    val countdownPrayerName: String,
    val countdownPrayerAbbreviation: String,
    val countdownPrayerDisplayTime: String,
    /** True while counting up from a prayer that has started, false while counting down. */
    val countdownIsElapsed: Boolean,
    val currentPrayerName: String,
    /**
     * Chronometer base. In the future while counting down, in the past while counting up —
     * the direction is carried by [countdownIsElapsed], which also flips the Chronometer.
     */
    val countdownBaseElapsedRealtime: Long,
    val gregorianDateText: String,
    val hijriDateText: String,
    val sunriseDisplayTime: String,
    val sunriseHasPassed: Boolean,
    val schedule: List<WidgetScheduleRow>,
) {
    companion object {
        fun empty(elapsedRealtime: Long) = PrayerWidgetState(
            profileId = NO_WIDGET_PROFILE,
            profileName = "Open aynama",
            countdownPrayerName = "Set up profile",
            countdownPrayerAbbreviation = "SET",
            countdownPrayerDisplayTime = "--:--",
            countdownIsElapsed = false,
            currentPrayerName = "",
            countdownBaseElapsedRealtime = elapsedRealtime,
            gregorianDateText = LocalDate.now().format(gregorianFormatter()),
            hijriDateText = "",
            sunriseDisplayTime = "--:--",
            sunriseHasPassed = false,
            schedule = emptyList(),
        )

        /**
         * Shown when the bound profile's location has no computable times for today.
         *
         * Takes the clock rather than reading it: `SystemClock` is a platform call, and a
         * fallback state that reaches for one cannot be built in a plain JVM test — which is
         * exactly where the cross-surface checks need to build it.
         * The countdown base is "now", so the Chronometer sits at zero instead of counting
         * towards a prayer that was never resolved.
         */
        fun unavailable(elapsedRealtime: Long, profileId: Long, profileName: String) = PrayerWidgetState(
            profileId = profileId,
            profileName = profileName,
            countdownPrayerName = "No times here",
            countdownPrayerAbbreviation = "—",
            countdownPrayerDisplayTime = "--:--",
            countdownIsElapsed = false,
            currentPrayerName = "",
            countdownBaseElapsedRealtime = elapsedRealtime,
            gregorianDateText = LocalDate.now().format(gregorianFormatter()),
            hijriDateText = "Midnight sun or polar night",
            sunriseDisplayTime = "--:--",
            sunriseHasPassed = false,
            schedule = emptyList(),
        )
    }
}

private fun gregorianFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

/**
 * Yesterday, today and tomorrow for [profile], skipping days with no computable times.
 *
 * The countdown timeline needs an event on each side of now: before Fajr the current event
 * is last night's Isha, after Isha the next one is tomorrow's Fajr. Near the polar circles a
 * single day can be undefined while its neighbours are fine, so days are dropped individually
 * rather than failing the set.
 */
internal fun profileDays(profile: Profile, today: LocalDate): Map<LocalDate, PrayerTimesResult> {
    val adhan = AdhanWrapper()
    return (-1L..1L).mapNotNull { offset ->
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
}

/**
 * Three-letter widget abbreviation, derived from the day-aware name so a Friday Dhuhr
 * abbreviates to JUM. Widget-only: nothing else has this little room.
 */
internal fun abbreviate(displayName: String): String = displayName.take(3).uppercase(Locale.ROOT)

internal fun buildPrayerWidgetState(
    profile: Profile,
    days: Map<LocalDate, PrayerTimesResult>,
    todayTimes: PrayerTimesResult,
    now: ZonedDateTime,
    elapsedRealtime: Long,
    timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US),
    hijriDateText: String = "",
): PrayerWidgetState {
    val today = now.toLocalDate()
    val nowInstant = now.toInstant()
    val timeline = buildTimeline(days, profile.asrMadhab, now.zone)

    // One rule for every surface — see DESIGN.md §19 and PrayerTimeline.countdownAt.
    val countdown = countdownAt(timeline, nowInstant)
        ?: return PrayerWidgetState.unavailable(elapsedRealtime, profile.id, profile.name)
    val elapsed = countdown is PrayerCountdown.Elapsed
    val millis = countdown.duration.toMillis().coerceAtLeast(0L)

    // Current event = the most recently started timeline event, Sunrise included. Between
    // sunrise and Dhuhr that is Sunrise, so the 4x2 widget highlights its sunrise block
    // rather than an already-finished Fajr. Distinct from the countdown's subject, which
    // moves on to the next prayer once the count-up window closes.
    val current: TimelineEntry? = currentEntry(timeline, nowInstant)
    val sunriseToday = timeline.firstOrNull { it.event == TimelineEvent.SUNRISE && it.date == today }

    return PrayerWidgetState(
        profileId = profile.id,
        profileName = profile.name,
        countdownPrayerName = countdown.entry.displayName(),
        countdownPrayerAbbreviation = abbreviate(countdown.entry.displayName()),
        countdownPrayerDisplayTime = countdown.entry.time.format(timeFormatter),
        countdownIsElapsed = elapsed,
        currentPrayerName = current?.displayName() ?: "",
        // Counting up anchors the Chronometer in the past; counting down, in the future.
        countdownBaseElapsedRealtime = if (elapsed) elapsedRealtime - millis else elapsedRealtime + millis,
        gregorianDateText = today.format(gregorianFormatter()),
        hijriDateText = hijriDateText,
        sunriseDisplayTime = todayTimes.sunrise.format(timeFormatter),
        sunriseHasPassed = sunriseToday != null && !sunriseToday.instant.isAfter(nowInstant),
        schedule = scheduleRows(todayTimes, profile.asrMadhab, today, timeFormatter),
    )
}

// --- Render decisions -------------------------------------------------------
// Pulled out of the RemoteViews builder so they can be tested without an Android Context.
// full() must call these rather than re-deriving the same conditions inline, otherwise the
// logic under test stops being the logic that renders.

/**
 * Whether the 4x2 widget highlights its sunrise block.
 *
 * Requires today's sunrise to have actually passed, not just that Sunrise won the
 * most-recently-started comparison. The block always displays *today's* sunrise time, while the
 * current-event search also considers yesterday-dated events — at extreme latitudes, where the
 * day's events are not in canonical clock order, those can disagree and the widget would
 * otherwise highlight a sunrise that has not happened yet.
 */
internal fun isSunriseHighlighted(state: PrayerWidgetState): Boolean =
    state.currentPrayerName == SUNRISE_NAME && state.sunriseHasPassed

/** The five prayer columns of the 4x2 widget, in order. Sunrise has its own block above them. */
internal fun columnRows(state: PrayerWidgetState): List<WidgetScheduleRow> =
    state.schedule.filter { it.name != SUNRISE_NAME }

/** Index of the highlighted prayer column, or null when none is current (e.g. while Sunrise is). */
internal fun highlightedColumnIndex(state: PrayerWidgetState): Int? =
    columnRows(state).indexOfFirst { it.name == state.currentPrayerName }.takeIf { it >= 0 }

// Sunrise is matched by name in the schedule rows and in the 4x2 renderer, which both
// highlights it and drops it from the prayer columns. Naming it once keeps those in step —
// a rename now fails to compile instead of silently breaking the highlight. It must stay
// equal to TimelineEntry.displayName() for TimelineEvent.SUNRISE.
internal const val SUNRISE_NAME = "Sunrise"

internal fun scheduleRows(
    times: PrayerTimesResult,
    asrMadhab: AsrMadhab,
    date: LocalDate,
    formatter: DateTimeFormatter,
): List<WidgetScheduleRow> {
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    fun row(event: TimelineEvent, time: LocalTime): WidgetScheduleRow {
        val name = prayerDisplayName(event, date)
        return WidgetScheduleRow(name, abbreviate(name), time, time.format(formatter))
    }
    return listOf(
        row(TimelineEvent.FAJR, times.fajr),
        row(TimelineEvent.SUNRISE, times.sunrise),
        row(TimelineEvent.DHUHR, times.dhuhr),
        row(TimelineEvent.ASR, asr),
        row(TimelineEvent.MAGHRIB, times.maghrib),
        row(TimelineEvent.ISHA, times.isha),
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
            setTextViewText(R.id.widget_next_name, state.countdownPrayerName)
            setTextViewText(R.id.widget_next_time, state.countdownPrayerDisplayTime)
            setCountdown(state)
            setTextViewText(R.id.widget_profile, state.profileName)
            bindRoot(context, state)
        }

    fun nextPrayerDated(context: Context, state: PrayerWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_next_prayer_dated).apply {
            setDates(state)
            setTextViewText(R.id.widget_next_name, state.countdownPrayerName)
            setTextViewText(R.id.widget_next_time, state.countdownPrayerDisplayTime)
            setCountdown(state)
            setTextViewText(R.id.widget_profile, state.profileName)
            bindRoot(context, state)
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
            bindRoot(context, state)
        }

    fun full(context: Context, state: PrayerWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_full).apply {
            setDates(state)

            val saffron = context.getColor(R.color.aynama_saffron)
            val ink = context.getColor(R.color.aynama_ink)
            val inkMuted = context.getColor(R.color.aynama_ink_muted)
            val parchment = context.getColor(R.color.aynama_parchment)
            val parchmentMuted = context.getColor(R.color.aynama_parchment_muted)

            val sunriseHighlighted = isSunriseHighlighted(state)
            setTextViewText(R.id.widget_sunrise_time, maybeBold(state.sunriseDisplayTime, sunriseHighlighted))
            setTextColor(R.id.widget_sunrise_label, if (sunriseHighlighted) saffron else parchmentMuted)
            setTextColor(R.id.widget_sunrise_time, if (sunriseHighlighted) saffron else parchment)
            setContentDescription(
                R.id.widget_sunrise_time,
                a11yLabel(context, SUNRISE_NAME, state.sunriseDisplayTime, sunriseHighlighted),
            )

            val rows = columnRows(state)
            val highlightedColumn = highlightedColumnIndex(state)
            columnNameIds.indices.forEach { index ->
                val row = rows.getOrNull(index)
                val highlighted = index == highlightedColumn
                setTextViewText(columnNameIds[index], maybeBold(row?.name ?: "", highlighted))
                setTextViewText(columnTimeIds[index], maybeBold(row?.displayTime ?: "", highlighted))
                setTextColor(columnNameIds[index], if (highlighted) saffron else ink)
                setTextColor(columnTimeIds[index], if (highlighted) saffron else inkMuted)
                setContentDescription(
                    columnTimeIds[index],
                    row?.let { a11yLabel(context, it.name, it.displayTime, highlighted) } ?: "",
                )
            }

            setCountdown(state)
            setTextViewText(
                R.id.widget_until,
                context.getString(
                    if (state.countdownIsElapsed) R.string.widget_since else R.string.widget_until,
                    state.countdownPrayerName,
                ),
            )
            setTextViewText(R.id.widget_profile, state.profileName)
            bindRoot(context, state)
        }

    // TalkBack reads the time views; the name sits in a sibling view it would otherwise
    // announce separately, so the label carries both plus the current-prayer state that
    // saffron and bold convey visually.
    private fun a11yLabel(context: Context, name: String, time: String, current: Boolean): CharSequence =
        context.getString(
            if (current) R.string.widget_a11y_prayer_current else R.string.widget_a11y_prayer,
            name,
            time,
        )

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

    // The launcher's own Chronometer ticks this — no per-second update job. Its format is the
    // platform's (MM:SS under an hour, H:MM:SS above) and cannot be zero-padded, so widgets read
    // `-12:35` where the app reads `-00:12:35`. The sign is ours, via the format string. State
    // and direction match the app exactly; only the padding differs. DESIGN.md §19.
    private fun RemoteViews.setCountdown(state: PrayerWidgetState) {
        val format = if (state.countdownIsElapsed) null else "-%s"
        setChronometer(R.id.widget_countdown, state.countdownBaseElapsedRealtime, format, true)
        setChronometerCountDown(R.id.widget_countdown, !state.countdownIsElapsed)
    }

    private fun RemoteViews.bindRoot(context: Context, state: PrayerWidgetState) {
        setOnClickPendingIntent(R.id.widget_root, openHomePendingIntent(context, state.profileId))
    }

    /**
     * Open the app on [profileId]'s page.
     *
     * Every widget used to share request code 0 and an identical intent, which is one
     * PendingIntent: extras are not part of PendingIntent equality, so whichever widget was
     * rendered last silently overwrote the target of every other one. The profile is therefore
     * in the request code *and* in the intent's data — either alone would keep them distinct,
     * and the data URI also makes the target readable in `dumpsys activity intents`.
     */
    private fun openHomePendingIntent(context: Context, profileId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_HOME_FROM_WIDGET
            data = widgetOpenDataUri(profileId).toUri()
            putExtra(EXTRA_WIDGET_PROFILE_ID, profileId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            widgetOpenRequestCode(profileId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private const val ACTION_OPEN_HOME_FROM_WIDGET = "com.aynama.prayertimes.widgets.OPEN_HOME"

/** Profile id a widget tap asks the app to show. Absent or [NO_WIDGET_PROFILE] means "no preference". */
const val EXTRA_WIDGET_PROFILE_ID = "com.aynama.prayertimes.widgets.PROFILE_ID"

const val NO_WIDGET_PROFILE = -1L

// Kept clear of the alarm ranges: notifications use profileId * 20, widget rollovers 70_000+.
internal const val WIDGET_OPEN_REQUEST_CODE_BASE = 80_000

/**
 * PendingIntent request code for a tap that opens [profileId].
 *
 * Two widgets on the same profile may share one — they open the same page. Two widgets on
 * different profiles must not, which is the whole point: extras are not part of PendingIntent
 * equality, so a shared code let the last-rendered widget silently retarget the others.
 */
internal fun widgetOpenRequestCode(profileId: Long): Int =
    WIDGET_OPEN_REQUEST_CODE_BASE + profileId.toInt()

/** Distinguishes the tap intents themselves, and makes the target readable in `dumpsys`. */
internal fun widgetOpenDataUri(profileId: Long): String = "aynama://widget/profile/$profileId"
