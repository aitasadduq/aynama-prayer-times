package com.aynama.prayertimes.wear.complications

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.aynama.prayertimes.wear.R
import com.aynama.prayertimes.wear.WearMainActivity

/**
 * The prayer complication.
 *
 * ### Which families are supported, and why not the others
 *
 * `SHORT_TEXT`, `LONG_TEXT` and `MONOCHROMATIC_IMAGE`. These are the families a watch face
 * renders as text or as an icon — exactly what DESIGN.md §7 specifies.
 *
 * `RANGED_VALUE` and `GOAL_PROGRESS` are **deliberately declined**, not overlooked. Watch faces
 * render them as circular progress arcs, and DESIGN.md §10 forbids circular progress rings
 * anywhere in this app; §9 names the absence of one as a deliberate differentiator. The arc
 * would be drawn by the watch face rather than by us, but the user would still be looking at a
 * ring. `WEIGHTED_ELEMENTS`, `SMALL_IMAGE` and `PHOTO_IMAGE` are declined too: prayer state is
 * a name and a time, and there is no honest image of it.
 *
 * ### The countdown
 *
 * [TimeDifferenceComplicationText] hands the tick to the system, so the complication stays
 * live between refreshes without anything of ours running — the watch equivalent of the
 * widget's Chronometer and the notification's `when`. Counting down uses a
 * [CountDownTimeReference] to the prayer's instant, counting up a [CountUpTimeReference] from
 * it, which is the shared rule (DESIGN.md §19) expressed in the platform's own vocabulary.
 *
 * Its format is the platform's — "2h 18m", no sign and no seconds. As on the widget and the
 * notification, the direction is carried by the words beside it rather than by a `-`.
 */
class PrayerComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val preview = PrayerComplicationState(
            prayerName = "Dhuhr",
            initial = "D",
            clockTime = "13:00",
            reference = java.time.Instant.now().plusSeconds(2 * 3600 + 18 * 60),
            isElapsed = false,
            profileName = "London",
        )
        return complicationFor(type, preview)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val state = PrayerComplicationData.current(applicationContext)
            ?: return unavailable(request.complicationType)
        // The next refresh is armed here rather than on a timer: the content changes when the
        // prayer state changes, which is not on any regular interval.
        ComplicationUpdateScheduler.arm(
            applicationContext,
            PrayerComplicationData.nextChangeAt(applicationContext),
        )
        return complicationFor(request.complicationType, state)
    }

    private fun complicationFor(
        type: ComplicationType,
        state: PrayerComplicationState,
    ): ComplicationData? = when (type) {
        // "● 04:21 F" at 40–60px: the countdown carries the urgency, the initial says which
        // prayer. Title, not text, because a watch face renders the title smaller.
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = countdownText(state),
            contentDescription = plain(describe(state)),
        )
            .setTitle(plain(state.initial))
            .setTapAction(openApp())
            .build()

        // Room for the name spelled out, per §7's tile/watch-face scale.
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            text = countdownText(state),
            contentDescription = plain(describe(state)),
        )
            .setTitle(plain(state.prayerName))
            .setTapAction(openApp())
            .build()

        // No room for prayer state at all. It is a shortcut, and pretending otherwise would
        // mean inventing a glyph per prayer that nobody could read at this size.
        ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
            monochromaticImage = MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_complication),
            ).build(),
            contentDescription = plain("aynama prayer times"),
        )
            .setTapAction(openApp())
            .build()

        else -> null
    }

    /**
     * The live number.
     *
     * Counting up from a prayer that has started, or down towards one that has not — the same
     * distinction the phone draws with the sign, in the only vocabulary this API has.
     */
    private fun countdownText(state: PrayerComplicationState) = if (state.isElapsed) {
        TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.SHORT_DUAL_UNIT,
            CountUpTimeReference(state.reference),
        ).build()
    } else {
        TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.SHORT_DUAL_UNIT,
            CountDownTimeReference(state.reference),
        ).build()
    }

    private fun describe(state: PrayerComplicationState) = if (state.isElapsed) {
        "${state.prayerName} began at ${state.clockTime}"
    } else {
        "${state.prayerName} at ${state.clockTime}"
    }

    /**
     * Nothing to show: no profiles synced, or a location with no computable times.
     *
     * Returning null would leave the last value frozen on the watch face, which is worse than
     * an honest blank — a stale prayer time is indistinguishable from a current one.
     */
    private fun unavailable(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = plain("--:--"),
            contentDescription = plain("No prayer times yet"),
        ).setTapAction(openApp()).build()

        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            text = plain("Open aynama to set up"),
            contentDescription = plain("No prayer times yet"),
        ).setTapAction(openApp()).build()

        ComplicationType.MONOCHROMATIC_IMAGE -> complicationFor(type, EMPTY_STATE)
        else -> null
    }

    private fun plain(text: String) = PlainComplicationText.Builder(text).build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, WearMainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        val EMPTY_STATE = PrayerComplicationState(
            prayerName = "",
            initial = "",
            clockTime = "",
            reference = java.time.Instant.EPOCH,
            isElapsed = false,
            profileName = "",
        )
    }
}
