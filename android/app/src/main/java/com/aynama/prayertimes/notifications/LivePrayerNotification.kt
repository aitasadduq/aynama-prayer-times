package com.aynama.prayertimes.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.MainActivity
import com.aynama.prayertimes.R
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.PrayerTimesUnavailableException
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.shared.timeline.PrayerCountdown
import com.aynama.prayertimes.shared.timeline.buildTimeline
import com.aynama.prayertimes.shared.timeline.countdownAt
import com.aynama.prayertimes.shared.timeline.displayName
import com.aynama.prayertimes.shared.timeline.nextTransition
import com.aynama.prayertimes.widgets.EXTRA_WIDGET_PROFILE_ID
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The optional always-on notification: which prayer it is, and a live countdown.
 *
 * The number ticks in the system's own chronometer — [NotificationCompat.Builder.setWhen] plus
 * `setUsesChronometer`, the notification-shade equivalent of the widget's `Chronometer`. Nothing
 * of ours runs per second, and the count stays correct while the app is dead. What *is* ours is
 * the state: the notification is rebuilt at each transition of the shared countdown rule
 * (DESIGN.md §19) by [LiveNotificationScheduler]'s alarm, not on a polling interval.
 *
 * As with the widget, the chronometer's format belongs to the platform and carries no sign, so
 * the direction is carried in words instead: "At 1:00 PM" while counting down, "Began at
 * 1:00 PM" while counting up. The rule, the subject and the direction match every other
 * surface exactly; only the ± glyph is unavailable here.
 */
object LivePrayerNotification {

    const val NOTIFICATION_ID = 2001

    /**
     * Rebuild the notification from the current state, or take it down.
     *
     * Safe to call from anywhere: it cancels rather than throws when the feature is off, when
     * there is no profile, or when the location has no computable times.
     */
    suspend fun refresh(context: Context, now: Instant = Instant.now()) {
        val app = context.applicationContext as AynamaApplication
        val prefs = NotificationPreferences(app.prefs)
        if (!prefs.isLive) {
            cancel(context)
            return
        }
        val profiles = app.profileRepository.observeAll().first()
        val profile = resolveNotificationProfile(prefs.notificationProfileId, profiles)
        if (profile == null) {
            cancel(context)
            return
        }
        val content = buildContent(profile, now)
        if (content == null) {
            Log.w(TAG, "no live notification for profile ${profile.id} (${profile.name})")
            cancel(context)
            return
        }
        post(context, profile, content)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /**
     * The instant this notification's content stops being true, or null when it has none.
     *
     * [LiveNotificationScheduler] arms the next refresh here. It is the same
     * `nextTransition` every other surface uses, so the notification flips from counting up to
     * counting down at the same moment the app and the widgets do.
     */
    suspend fun nextRefreshAt(context: Context, now: Instant = Instant.now()): Instant? {
        val app = context.applicationContext as AynamaApplication
        val prefs = NotificationPreferences(app.prefs)
        if (!prefs.isLive) return null
        val profiles = app.profileRepository.observeAll().first()
        val profile = resolveNotificationProfile(prefs.notificationProfileId, profiles) ?: return null
        return nextTransition(timelineFor(profile, now), now)
    }

    private fun post(context: Context, profile: Profile, content: LiveContent) {
        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_LIVE_PRAYER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(profile.name)
            // The live number. setWhen is the target instant while counting down and the prayer's
            // own instant while counting up; countDown decides which way the chronometer runs.
            .setWhen(content.chronometerBase.toEpochMilli())
            .setUsesChronometer(true)
            .setChronometerCountDown(content.countingDown)
            .setShowWhen(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openProfileIntent(context, profile.id))
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    /** Tapping opens the app on the profile the notification is about, like a widget tap does. */
    private fun openProfileIntent(context: Context, profileId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_FROM_LIVE_NOTIFICATION
            data = "aynama://live/profile/$profileId".toUri()
            putExtra(EXTRA_WIDGET_PROFILE_ID, profileId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            OPEN_REQUEST_CODE_BASE + profileId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Pure: the notification's content for [profile] at [now], or null if it has none. */
    internal fun buildContent(profile: Profile, now: Instant): LiveContent? {
        val countdown = countdownAt(timelineFor(profile, now), now) ?: return null
        val zone = profile.effectiveZoneId()
        val at = countdown.entry.time.format(timeFormatter(zone))
        return LiveContent(
            title = countdown.entry.displayName(),
            text = when (countdown) {
                is PrayerCountdown.Elapsed -> "Began at $at"
                is PrayerCountdown.Remaining -> "At $at"
            },
            chronometerBase = countdown.entry.instant,
            countingDown = countdown is PrayerCountdown.Remaining,
        )
    }

    private fun timelineFor(profile: Profile, now: Instant) = buildTimeline(
        days = profileDays(profile, now.atZone(profile.effectiveZoneId()).toLocalDate()),
        asrMadhab = profile.asrMadhab,
        zone = profile.effectiveZoneId(),
    )

    // Yesterday/today/tomorrow, days with no computable times dropped. Same window the home
    // pager and the widgets build: the countdown needs an event on each side of now.
    private fun profileDays(profile: Profile, today: LocalDate): Map<LocalDate, PrayerTimesResult> {
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

    private fun timeFormatter(zone: java.time.ZoneId): DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(zone)

    /**
     * The master toggle gates this too.
     *
     * Master off means "aynama may not put prayer notifications in my shade", and an ongoing
     * one would contradict that most visibly of all. The user's own preference is untouched,
     * so turning master back on restores the notification without them re-finding the row —
     * which matters because the row lives inside the master-gated part of the screen.
     */
    private val NotificationPreferences.isLive: Boolean
        get() = masterEnabled && liveNotificationEnabled

    private const val TAG = "LivePrayerNotif"
    private const val ACTION_OPEN_FROM_LIVE_NOTIFICATION =
        "com.aynama.prayertimes.notifications.OPEN_LIVE"

    // Clear of the widget tap range (80_000+) and both alarm ranges.
    private const val OPEN_REQUEST_CODE_BASE = 90_000
}

internal data class LiveContent(
    val title: String,
    val text: String,
    val chronometerBase: Instant,
    val countingDown: Boolean,
)
