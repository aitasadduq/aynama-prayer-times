package com.aynama.prayertimes.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.color.ColorProvider
import androidx.glance.text.TextStyle
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.MainActivity
import com.aynama.prayertimes.R
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZonedDateTime

// Theme tokens (mirror ui.theme.AynamaTheme) with day/night providers so the widget
// follows the system surface. Saffron accent reads on both parchment and ink.
private val Parchment = Color(0xFFF2EAD8)
private val Ink = Color(0xFF1C1A17)
private val Saffron = Color(0xFFB87A2E)

private val surface = ColorProvider(day = Parchment, night = Ink)
private val onSurface = ColorProvider(day = Ink, night = Parchment)
private val onSurfaceMuted = ColorProvider(day = Color(0xFF6B6560), night = Color(0xFFD4C9B1))
private val accent = ColorProvider(day = Saffron, night = Saffron)

// Three discrete, non-resizable widgets. Each provider is locked to one size in its
// appwidget-provider XML and renders one fixed layout (no SizeMode.Responsive branching).
abstract class AynamaBaseWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as AynamaApplication
        val profile = app.profileRepository.observeDefaultProfile().first()
        val state = profile?.let { computeState(it) }
        provideContent { Render(profile, state) }
    }

    @Composable
    abstract fun Render(profile: Profile?, state: WidgetPrayerState?)
}

class AynamaWidgetSmall : AynamaBaseWidget() {
    @Composable
    override fun Render(profile: Profile?, state: WidgetPrayerState?) =
        WidgetScaffold(profile, state) { _, s, m -> SmallContent(s, m) }
}

class AynamaWidgetMedium : AynamaBaseWidget() {
    @Composable
    override fun Render(profile: Profile?, state: WidgetPrayerState?) =
        WidgetScaffold(profile, state) { p, s, m -> MediumContent(p, s, m) }
}

class AynamaWidgetLarge : AynamaBaseWidget() {
    @Composable
    override fun Render(profile: Profile?, state: WidgetPrayerState?) =
        WidgetScaffold(profile, state) { p, s, m -> LargeContent(p, s, m) }
}

private fun computeState(profile: Profile): WidgetPrayerState {
    val adhan = AdhanWrapper()
    val zone = profile.effectiveZoneId()
    val today = LocalDate.now(zone)
    val times = adhan.getPrayerTimes(
        profile.latitude, profile.longitude, today, zone, profile.calculationMethod,
    )
    val tomorrow = adhan.getPrayerTimes(
        profile.latitude, profile.longitude, today.plusDays(1), zone, profile.calculationMethod,
    )
    return buildWidgetState(times, tomorrow.fajr, profile.asrMadhab, ZonedDateTime.now(zone))
}

@Composable
private fun WidgetScaffold(
    profile: Profile?,
    state: WidgetPrayerState?,
    content: @Composable (Profile, WidgetPrayerState, GlanceModifier) -> Unit,
) {
    val openApp = actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))
    val root = GlanceModifier
        .fillMaxSize()
        .background(surface)
        .padding(12.dp)
        .clickable(openApp)

    if (profile == null || state == null) {
        Column(
            modifier = root,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                LocalContext.current.getString(R.string.widget_empty),
                style = TextStyle(color = onSurfaceMuted, fontSize = 13.sp),
            )
        }
        return
    }
    content(profile, state, root)
}

@Composable
private fun SmallContent(state: WidgetPrayerState, modifier: GlanceModifier) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            state.nextAbbrev,
            style = TextStyle(color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            state.nextTime.format(WIDGET_TIME_FORMATTER),
            style = TextStyle(color = onSurface, fontSize = 16.sp),
        )
    }
}

@Composable
private fun MediumContent(profile: Profile, state: WidgetPrayerState, modifier: GlanceModifier) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            state.nextName,
            style = TextStyle(color = onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            state.nextTime.format(WIDGET_TIME_FORMATTER),
            style = TextStyle(color = onSurfaceMuted, fontSize = 13.sp),
            modifier = GlanceModifier.padding(top = 2.dp),
        )
        CountdownChronometer(
            state.nextEpochMs,
            GlanceModifier.height(40.dp).padding(top = 6.dp),
        )
        Text(
            profile.name,
            style = TextStyle(color = onSurfaceMuted, fontSize = 12.sp),
            modifier = GlanceModifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LargeContent(profile: Profile, state: WidgetPrayerState, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.nextName,
                style = TextStyle(color = onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                "  ·  ${profile.name}",
                style = TextStyle(color = onSurfaceMuted, fontSize = 12.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            CountdownChronometer(state.nextEpochMs, GlanceModifier)
        }
        state.schedule.forEach { row ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.abbrev,
                    style = TextStyle(color = onSurfaceMuted, fontSize = 13.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    row.time.format(WIDGET_TIME_FORMATTER),
                    style = TextStyle(color = onSurface, fontSize = 13.sp),
                )
            }
        }
    }
}

@Composable
private fun CountdownChronometer(epochMs: Long, modifier: GlanceModifier) {
    val context = LocalContext.current
    val remoteViews = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        val base = SystemClock.elapsedRealtime() + (epochMs - System.currentTimeMillis())
        setChronometer(R.id.widget_chronometer, base, null, true)
        setChronometerCountDown(R.id.widget_chronometer, true)
    }
    AndroidRemoteViews(remoteViews = remoteViews, modifier = modifier)
}
