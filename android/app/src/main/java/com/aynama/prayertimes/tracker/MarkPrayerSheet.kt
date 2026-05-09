package com.aynama.prayertimes.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import com.aynama.prayertimes.ui.theme.InkMuted
import com.aynama.prayertimes.ui.theme.ParchmentMuted
import com.aynama.prayertimes.ui.theme.Saffron
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkPrayerSheet(
    prayer: Prayer,
    date: LocalDate,
    currentStatus: QazaStatus?,
    onSelect: (QazaStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = prayer.displayName(),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sheetDateLabel(date),
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))

            val isMissed = currentStatus == QazaStatus.MISSED || currentStatus == QazaStatus.INTENTION_TO_MAKEUP

            MarkOptionRow(
                label = "I prayed this",
                indicator = { PrayerStatusSquare(filled = true, color = Saffron) },
                isSelected = currentStatus == QazaStatus.PRAYED_ON_TIME,
                onClick = { onSelect(QazaStatus.PRAYED_ON_TIME); onDismiss() },
            )
            MarkOptionRow(
                label = "I prayed this later (Qada)",
                indicator = { PrayerStatusSquare(filled = true, color = InkMuted) },
                isSelected = currentStatus == QazaStatus.MADE_UP,
                onClick = { onSelect(QazaStatus.MADE_UP); onDismiss() },
            )
            MarkOptionRow(
                label = "I didn't pray this",
                indicator = { PrayerStatusSquare(filled = false, color = InkMuted) },
                isSelected = isMissed,
                onClick = { onSelect(QazaStatus.MISSED); onDismiss() },
            )
        }
    }
}

@Composable
private fun MarkOptionRow(
    label: String,
    indicator: @Composable () -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        indicator()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) Saffron else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun PrayerStatusSquare(
    filled: Boolean,
    color: Color,
    size: Int = 12,
) {
    if (filled) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(color),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .border(1.dp, ParchmentMuted),
        )
    }
}

internal fun Prayer.displayName(): String = when (this) {
    Prayer.FAJR -> "Fajr"
    Prayer.DHUHR -> "Dhuhr"
    Prayer.ASR -> "Asr"
    Prayer.MAGHRIB -> "Maghrib"
    Prayer.ISHA -> "Isha"
}

private fun sheetDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}
