package com.aynama.prayertimes.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import com.aynama.prayertimes.ui.theme.IbmPlexSans
import com.aynama.prayertimes.ui.theme.InkMuted
import com.aynama.prayertimes.ui.theme.ParchmentMuted
import com.aynama.prayertimes.ui.theme.Saffron
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayRowDateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
private val todayDateFmt = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun TrackerScreen() {
    val app = LocalContext.current.applicationContext as AynamaApplication
    val vm: TrackerViewModel = viewModel(factory = TrackerViewModel.factory(app))
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        TrackerUiState.Loading -> Box(Modifier.fillMaxSize())
        TrackerUiState.Empty -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Create a profile to track prayers",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        }
        is TrackerUiState.Loaded -> LoadedTracker(state = state, vm = vm)
    }
}

@Composable
private fun LoadedTracker(state: TrackerUiState.Loaded, vm: TrackerViewModel) {
    var sheetTarget by remember { mutableStateOf<Pair<Prayer, LocalDate>?>(null) }
    val today = remember { LocalDate.now() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Text(
                text = "Today",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
        }

        items(state.todayRows, key = { it.prayer.name }) { row ->
            TodayPrayerRow(
                row = row,
                onTap = { sheetTarget = row.prayer to today },
            )
        }

        if (state.outstandingCount > 0) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${state.outstandingCount} prayers outstanding",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    modifier = Modifier.semantics {
                        contentDescription = "${state.outstandingCount} prayers outstanding"
                    },
                )
            }
        }

        if (state.weeks.isNotEmpty()) {
            item { Spacer(Modifier.height(24.dp)) }

            item { HistoryColumnHeader() }

            state.weeks.forEach { week ->
                item(key = "week-${week.label}") {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = week.label,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }

                week.aggregate?.let { agg ->
                    item(key = "agg-${week.label}") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = agg,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }

                items(week.days, key = { it.date.toEpochDay() }) { day ->
                    DayRow(
                        day = day,
                        onToggle = { vm.toggleExpansion(day.date) },
                        onMarkPrayer = { prayer -> sheetTarget = prayer to day.date },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    sheetTarget?.let { (prayer, date) ->
        val currentStatus = when {
            date == today -> state.todayRows.find { it.prayer == prayer }?.status
            else -> {
                state.weeks.flatMap { it.days }
                    .find { it.date == date }
                    ?.prayers?.get(prayer)
            }
        }
        MarkPrayerSheet(
            prayer = prayer,
            date = date,
            currentStatus = currentStatus,
            onSelect = { status ->
                vm.markPrayer(state.profileId, prayer, date, status)
            },
            onDismiss = { sheetTarget = null },
        )
    }
}

@Composable
private fun TodayPrayerRow(
    row: TrackerPrayerRow,
    onTap: () -> Unit,
) {
    val prayerName = row.prayer.displayName()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onTap)
            .semantics { contentDescription = "Mark $prayerName prayer" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrayerStatusSquare(
            filled = row.status == QazaStatus.PRAYED_ON_TIME || row.status == QazaStatus.MADE_UP,
            color = if (row.status == QazaStatus.PRAYED_ON_TIME) Saffron else InkMuted,
            size = 12,
        )
        Text(
            text = prayerName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.scheduledTime,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = IbmPlexSans,
                fontFeatureSettings = "tnum",
            ),
            color = InkMuted,
        )
    }
}

@Composable
private fun HistoryColumnHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(historyDateWidth))
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(squareSpacing)) {
            Prayer.entries.forEach { prayer ->
                Box(
                    modifier = Modifier.size((squareSizePx).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = prayer.abbrev(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = IbmPlexSans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = InkMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.width(28.dp))
    }
}

@Composable
private fun DayRow(
    day: DayState,
    onToggle: () -> Unit,
    onMarkPrayer: (Prayer) -> Unit,
) {
    val today = LocalDate.now()
    val isToday = day.date == today
    val dateLabel = if (isToday) {
        "Today · ${day.date.format(todayDateFmt)}"
    } else {
        day.date.format(dayRowDateFmt)
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onToggle)
                .semantics { contentDescription = "$dateLabel, ${day.prayedCount} of 5 prayers" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isToday) Saffron else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(historyDateWidth),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(squareSpacing)) {
                Prayer.entries.forEach { prayer ->
                    val status = day.prayers[prayer]
                    PrayerStatusSquare(
                        filled = status == QazaStatus.PRAYED_ON_TIME || status == QazaStatus.MADE_UP,
                        color = squareColor(status),
                        size = squareSizePx,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${day.prayedCount}/5",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = IbmPlexSans),
                color = InkMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.width(28.dp),
            )
        }

        if (day.isExpanded) {
            day.expandedRows.forEach { row ->
                ExpandedPrayerRow(row = row, onTap = { onMarkPrayer(row.prayer) })
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ExpandedPrayerRow(row: TrackerPrayerRow, onTap: () -> Unit) {
    val prayerName = row.prayer.displayName()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onTap)
            .padding(start = 16.dp)
            .semantics { contentDescription = "Mark $prayerName prayer" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrayerStatusSquare(
            filled = row.status == QazaStatus.PRAYED_ON_TIME || row.status == QazaStatus.MADE_UP,
            color = squareColor(row.status),
            size = 10,
        )
        Text(
            text = prayerName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.scheduledTime,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = IbmPlexSans,
                fontFeatureSettings = "tnum",
            ),
            color = InkMuted,
        )
    }
}

private val historyDateWidth = 130.dp
private val squareSize = 16.dp
private val squareSizePx = 15
private val squareSpacing = 4.dp

private fun squareColor(status: QazaStatus?): Color = when (status) {
    null -> ParchmentMuted
    QazaStatus.PRAYED_ON_TIME -> Saffron
    QazaStatus.MADE_UP -> InkMuted
    QazaStatus.MISSED -> ParchmentMuted
    QazaStatus.INTENTION_TO_MAKEUP -> ParchmentMuted
}

private fun Prayer.abbrev(): String = when (this) {
    Prayer.FAJR -> "F"
    Prayer.DHUHR -> "D"
    Prayer.ASR -> "A"
    Prayer.MAGHRIB -> "M"
    Prayer.ISHA -> "I"
}
