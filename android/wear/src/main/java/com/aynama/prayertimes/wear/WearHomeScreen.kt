package com.aynama.prayertimes.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * The watch face of the app.
 *
 * DESIGN.md §7: three tokens only, a solid ink field rather than the phone's time-of-day
 * gradient (power, and legibility in sunlight), and no glyph under 10pt.
 */
@Composable
fun WearHomeScreen() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as WearApplication
    val vm: WearHomeViewModel = viewModel(factory = WearHomeViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearInk),
    ) {
        when (val s = state) {
            WearHomeState.Loading -> Unit
            is WearHomeState.WaitingForPhone -> Message(
                title = "No profiles yet",
                body = if (s.everSynced) {
                    "The phone has no prayer profiles. Add one there and it will appear here."
                } else {
                    "Open aynama on your phone to send your prayer profiles across."
                },
            )
            is WearHomeState.NoTimes -> Message(
                title = "No times today",
                body = "The sun doesn't fully rise or set at ${s.profileName} today.",
            )
            is WearHomeState.Ready -> Pages(s)
        }
    }
}

@Composable
private fun Pages(state: WearHomeState.Ready) {
    val pagerState = rememberPagerState(
        initialPage = state.initialPage.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0)),
        pageCount = { state.pages.size },
    )
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
        ProfilePage(page = state.pages[index], staleness = state.staleness)
    }
}

@Composable
private fun ProfilePage(page: WearProfilePage, staleness: WearHomeState.Staleness) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = page.profileName,
                color = WearParchment.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            // The signed countdown, verbatim from the shared rule. Saffron while a prayer is
            // under way, parchment while counting towards one — the same distinction the
            // phone draws with the sign.
            Text(
                text = page.countdownText,
                color = if (page.countdownIsElapsed) WearSaffron else WearParchment,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = if (page.countdownIsElapsed) {
                            "${page.countdownPrayerName} began ${page.countdownText} ago"
                        } else {
                            "${page.countdownPrayerName} in ${page.countdownText.removePrefix("-")}"
                        }
                    },
            )
        }
        item {
            Text(
                text = "${page.countdownPrayerName} · ${page.countdownPrayerTime}",
                color = WearParchment,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (staleness == WearHomeState.Staleness.STALE) {
            item { StaleNote() }
        }
        item { Spacer(Modifier.height(4.dp)) }
        items(page.rows) { row -> PrayerRow(row) }
    }
}

@Composable
private fun PrayerRow(row: WearPrayerRow) {
    val color = when {
        row.isCurrent -> WearSaffron
        row.isPast -> WearParchment.copy(alpha = 0.5f)
        else -> WearParchment
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = "${row.name} ${row.time}" +
                    if (row.isCurrent) ", current" else if (row.isPast) ", passed" else ""
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = row.name, color = color, fontSize = 14.sp, maxLines = 1)
        Text(text = row.time, color = color, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun StaleNote() {
    // Not an error banner: the times are still correct, because the watch calculates them.
    // What may have drifted is the profile set. Kept to one line — on a 384px round screen a
    // two-line note pushes the prayer list off the bottom, and this is the least important
    // thing here.
    Text(
        text = "Phone not seen recently",
        color = WearParchment.copy(alpha = 0.6f),
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = WearParchment,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            color = WearParchment.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Wear Compose still wants a MaterialTheme in scope; the three tokens do the actual work. */
@Composable
fun AynamaWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = androidx.wear.compose.material.Colors(
            primary = WearSaffron,
            onPrimary = WearInk,
            surface = WearInk,
            onSurface = WearParchment,
            background = WearInk,
            onBackground = WearParchment,
        ),
        content = content,
    )
}
