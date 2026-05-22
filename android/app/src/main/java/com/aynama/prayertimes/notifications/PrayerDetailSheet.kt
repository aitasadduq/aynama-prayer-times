package com.aynama.prayertimes.notifications

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.InkMuted
import com.aynama.prayertimes.ui.theme.Parchment
import com.aynama.prayertimes.ui.theme.ParchmentMuted
import com.aynama.prayertimes.ui.theme.Saffron
import kotlinx.coroutines.launch

private val OFFSET_OPTIONS = listOf(-15, -10, -5, 0, 5, 10, 15)
private val EARLY_REMINDER_OPTIONS = listOf(0, 5, 10, 15)

fun formatOffset(minutes: Int): String = when {
    minutes == 0 -> "On time"
    minutes < 0 -> "−${-minutes} min"
    else -> "+$minutes min"
}

fun formatEarlyReminder(minutes: Int): String = when (minutes) {
    0 -> "Off"
    else -> "$minutes min before"
}

fun formatFixedTime(minutesOfDay: Int): String {
    if (minutesOfDay < 0) return "Not set"
    val h = minutesOfDay / 60
    val m = minutesOfDay % 60
    val ampm = if (h < 12) "AM" else "PM"
    val displayH = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "%d:%02d %s".format(displayH, m, ampm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerDetailSheet(
    row: PrayerRowData,
    onSetEnabled: (Boolean) -> Unit,
    onSetOffset: (Int) -> Unit,
    onSetEarlyReminder: (Int) -> Unit,
    onSetAlertMode: (AlertTimeMode) -> Unit,
    onSetFixedTime: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var showOffsetPicker by remember { mutableStateOf(false) }
    var showEarlyReminderPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.displaySmall,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Today · ${row.time}",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            HorizontalDivider(color = ParchmentMuted, thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Send alert",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = row.enabled,
                    onCheckedChange = onSetEnabled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Saffron,
                        uncheckedTrackColor = ParchmentMuted,
                        checkedThumbColor = Parchment,
                        uncheckedThumbColor = Parchment,
                        uncheckedBorderColor = ParchmentMuted,
                    ),
                )
            }

            HorizontalDivider(color = ParchmentMuted, thickness = 0.5.dp)

            // ALERT TIME section header
            Text(
                text = "ALERT TIME",
                style = MaterialTheme.typography.headlineMedium,
                color = InkMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
            )

            // Offset row
            AlertTimeModeRow(
                label = "Offset",
                value = formatOffset(row.offset),
                isActive = row.alertMode == AlertTimeMode.OFFSET,
                onClick = { showOffsetPicker = true },
            )

            HorizontalDivider(
                color = ParchmentMuted,
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 60.dp),
            )

            // Fixed time row
            AlertTimeModeRow(
                label = "Fixed time",
                value = formatFixedTime(row.fixedTimeMinutes),
                isActive = row.alertMode == AlertTimeMode.FIXED,
                onClick = { showTimePicker = true },
            )

            HorizontalDivider(
                color = ParchmentMuted,
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 24.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { showEarlyReminderPicker = true }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Early reminder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatEarlyReminder(row.earlyReminder),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = InkMuted,
                )
            }

            HorizontalDivider(color = ParchmentMuted, thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        Toast.makeText(context, "Adhan assets coming soon", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Preview adhan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Saffron,
                )
            }
        }
    }

    if (showOffsetPicker) {
        OffsetPickerSheet(
            current = row.offset,
            onSelect = { minutes ->
                onSetOffset(minutes)
                onSetAlertMode(AlertTimeMode.OFFSET)
                showOffsetPicker = false
            },
            onDismiss = { showOffsetPicker = false },
        )
    }

    if (showEarlyReminderPicker) {
        EarlyReminderPickerSheet(
            current = row.earlyReminder,
            onSelect = { onSetEarlyReminder(it); showEarlyReminderPicker = false },
            onDismiss = { showEarlyReminderPicker = false },
        )
    }

    if (showTimePicker) {
        val initialH = if (row.fixedTimeMinutes >= 0) row.fixedTimeMinutes / 60 else 6
        val initialM = if (row.fixedTimeMinutes >= 0) row.fixedTimeMinutes % 60 else 0
        TimePickerDialog(
            initialHour = initialH,
            initialMinute = initialM,
            onConfirm = { state ->
                onSetFixedTime(state.hour * 60 + state.minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun AlertTimeModeRow(
    label: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val radioColor = if (isActive) Saffron else InkMuted
    val labelColor = if (isActive) MaterialTheme.colorScheme.onSurface else InkMuted
    val valueColor = if (isActive) InkMuted else InkMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Radio indicator
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(20.dp)
                .padding(end = 0.dp),
        ) {
            val r = size.minDimension / 2f
            if (isActive) {
                drawCircle(color = radioColor, radius = r * 0.35f)
            }
            drawCircle(
                color = radioColor,
                radius = r - 1.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            modifier = Modifier.padding(end = 4.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = radioColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (TimePickerState) -> Unit,
    onDismiss: () -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState) }) {
                Text("OK", color = Saffron)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = InkMuted)
            }
        },
        text = {
            TimePicker(state = timePickerState)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffsetPickerSheet(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
        ) {
            Text(
                text = "Time offset",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 24.dp),
            )
            OFFSET_OPTIONS.forEach { minutes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onSelect(minutes) }
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatOffset(minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (minutes == current) Saffron else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (minutes != OFFSET_OPTIONS.last()) {
                    HorizontalDivider(
                        color = ParchmentMuted,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EarlyReminderPickerSheet(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
        ) {
            Text(
                text = "Early reminder",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 24.dp),
            )
            EARLY_REMINDER_OPTIONS.forEach { minutes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onSelect(minutes) }
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatEarlyReminder(minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (minutes == current) Saffron else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (minutes != EARLY_REMINDER_OPTIONS.last()) {
                    HorizontalDivider(
                        color = ParchmentMuted,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }
        }
    }
}
