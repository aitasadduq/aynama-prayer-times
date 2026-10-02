package com.aynama.prayertimes.wear.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyles
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.compose.ui.graphics.toArgb
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.aynama.prayertimes.wear.WearInk
import com.aynama.prayertimes.wear.WearParchment
import com.aynama.prayertimes.wear.WearSaffron
import com.aynama.prayertimes.wear.complications.PrayerComplicationData
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import java.time.Duration
import java.time.Instant

/**
 * The prayer tile.
 *
 * DESIGN.md §7's tile scale: the prayer name spelled out, its clock time under it, and the
 * profile above — the same three facts the watch's home screen leads with, sized for a glance
 * from a swipe rather than from an opened app.
 *
 * ### Why the countdown is not on the tile
 *
 * A tile is a static layout refreshed on an interval; it has no equivalent of the widget's
 * Chronometer or the complication's `TimeDifferenceComplicationText`. A countdown here would
 * be a number frozen at whatever it was when the tile was last built — right for a second and
 * quietly wrong afterwards, which is worse than not showing one. The tile therefore shows
 * *when* the prayer is; the complication and the app show *how long*.
 *
 * Freshness is set from the countdown's own next transition, so the tile re-renders when the
 * prayer changes rather than on a fixed clock.
 */
class PrayerTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val device = requestParams.deviceConfiguration
        val now = Instant.now()
        val state = PrayerComplicationData.current(applicationContext, now)
        val nextChange = PrayerComplicationData.nextChangeAt(applicationContext, now)

        val layout = if (state == null) {
            message(device, "No prayer times", "Open aynama on your phone")
        } else {
            message(
                device = device,
                title = state.prayerName,
                body = if (state.isElapsed) "began ${state.clockTime}" else "at ${state.clockTime}",
                caption = state.profileName,
                titleColor = if (state.isElapsed) WearSaffron.toArgbInt() else WearParchment.toArgbInt(),
            )
        }

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Re-render when the prayer state actually changes. A fixed interval would either
            // wake the watch for nothing or leave a finished prayer on screen.
            .setFreshnessIntervalMillis(freshnessFor(nextChange, now))
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder().setLayout(
                            LayoutElementBuilders.Layout.Builder().setRoot(layout).build(),
                        ).build(),
                    )
                    .build(),
            )
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = Futures.immediateFuture(
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
    )

    private fun message(
        device: androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters,
        title: String,
        body: String,
        caption: String? = null,
        titleColor: Int = WearParchment.toArgbInt(),
    ): LayoutElementBuilders.LayoutElement =
        Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(WearInk.toArgbInt()))
                            .build(),
                    )
                    .build(),
            )
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .apply {
                if (caption != null) {
                    addContent(
                        Text.Builder()
                            .setText(caption)
                            .setFontStyle(
                                FontStyles.caption1(device)
                                    .setColor(argb(WearParchment.copyAlpha(0.7f)))
                                    .build(),
                            )
                            .build(),
                    )
                    addContent(Spacer.Builder().setHeight(dp(4f)).build())
                }
            }
            .addContent(
                Text.Builder()
                    .setText(title)
                    .setFontStyle(
                        FontStyles.display2(device)
                            .setColor(argb(titleColor))
                            .build(),
                    )
                    .build(),
            )
            .addContent(Spacer.Builder().setHeight(dp(2f)).build())
            .addContent(
                Text.Builder()
                    .setText(body)
                    .setFontStyle(
                        FontStyles.body2(device)
                            .setColor(argb(WearParchment.toArgbInt()))
                            .build(),
                    )
                    .build(),
            )
            .build()

    internal companion object {
        const val RESOURCES_VERSION = "1"

        /**
         * Never below a minute, never above an hour.
         *
         * The floor keeps a tile that has just missed a boundary from spinning; the ceiling is
         * a safety net for a transition the alarm chain lost, so the tile eventually corrects
         * itself instead of showing yesterday's Isha forever.
         */
        val MIN_FRESHNESS: Duration = Duration.ofMinutes(1)
        val MAX_FRESHNESS: Duration = Duration.ofHours(1)

        fun freshnessFor(nextChange: Instant?, now: Instant): Long {
            val until = nextChange?.let { Duration.between(now, it) } ?: MAX_FRESHNESS
            return until.coerceIn(MIN_FRESHNESS, MAX_FRESHNESS).toMillis()
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = toArgb()

private fun androidx.compose.ui.graphics.Color.copyAlpha(alpha: Float): Int =
    copy(alpha = alpha).toArgb()
