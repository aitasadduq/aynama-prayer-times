package com.aynama.prayertimes.notifications

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.InkMuted
import com.aynama.prayertimes.ui.theme.Parchment
import com.aynama.prayertimes.ui.theme.ParchmentMuted
import com.aynama.prayertimes.ui.theme.Saffron
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdhan: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AynamaApplication
    val vm: NotificationSettingsViewModel = viewModel(factory = NotificationSettingsViewModel.factory(app))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshPermission()
                vm.refreshFromPrefs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showVibrationSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Master toggle row
            item {
                MasterToggleRow(
                    permissionGranted = state.permissionGranted,
                    masterEnabled = state.masterEnabled,
                    onToggle = { vm.setMaster(it) },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                )
                HorizontalDivider(color = ParchmentMuted, thickness = 0.5.dp)
            }

            if (state.masterEnabled) {
                // PRAYERS section
                item { SectionHeader(title = "PRAYERS") }

                if (state.prayerRows.isNotEmpty()) {
                    items(state.prayerRows) { row ->
                        PrayerToggleRow(
                            row = row,
                            masterEnabled = true,
                            permissionGranted = state.permissionGranted,
                            onToggle = { vm.setPrayerEnabled(row.index, it) },
                            onOpenSettings = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                        )
                        HorizontalDivider(
                            color = ParchmentMuted,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                }

                // ADHAN section
                item { SectionHeader(title = "ADHAN") }
                item {
                    AdhanVoiceRow(
                        currentVoice = state.adhanVoice,
                        onClick = onNavigateToAdhan,
                    )
                    HorizontalDivider(color = ParchmentMuted, thickness = 0.5.dp)
                }

                // OTHER section
                item { SectionHeader(title = "OTHER") }
                item {
                    ImsakRow(
                        enabled = state.imsakEnabled,
                        isRamadan = state.isRamadan,
                        onToggle = { vm.setImsakEnabled(it) },
                    )
                    HorizontalDivider(
                        color = ParchmentMuted,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
                item {
                    VibrationRow(
                        vibration = state.vibration,
                        onClick = { showVibrationSheet = true },
                    )
                }
            }
        }
    }

    if (showVibrationSheet) {
        VibrationSheet(
            current = state.vibration,
            onSelect = { mode ->
                vm.setVibration(mode)
                showVibrationSheet = false
            },
            onDismiss = { showVibrationSheet = false },
        )
    }
}

@Composable
private fun MasterToggleRow(
    permissionGranted: Boolean,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Prayer alerts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!permissionGranted) {
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = "Enable in Settings →",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Saffron,
                )
            }
        } else {
            Switch(
                checked = masterEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Saffron,
                    uncheckedTrackColor = ParchmentMuted,
                    checkedThumbColor = Parchment,
                    uncheckedThumbColor = Parchment,
                    uncheckedBorderColor = ParchmentMuted,
                ),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun PrayerToggleRow(
    row: PrayerRowData,
    masterEnabled: Boolean,
    permissionGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val nameColor = if (masterEnabled) MaterialTheme.colorScheme.onSurface else InkMuted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = !permissionGranted) { onOpenSettings() }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyMedium,
            color = nameColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.time,
            style = MaterialTheme.typography.labelMedium,
            color = InkMuted,
            modifier = Modifier.padding(end = 12.dp),
        )
        Switch(
            checked = row.enabled,
            onCheckedChange = if (permissionGranted) onToggle else { _ -> onOpenSettings() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Saffron,
                uncheckedTrackColor = ParchmentMuted,
                checkedThumbColor = Parchment,
                uncheckedThumbColor = Parchment,
                uncheckedBorderColor = ParchmentMuted,
            ),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "${row.name} notification settings",
            tint = InkMuted,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun AdhanVoiceRow(
    currentVoice: AdhanVoice,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Adhan voice",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = currentVoice.displayName,
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
}

@Composable
private fun ImsakRow(
    enabled: Boolean,
    isRamadan: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val background = if (isRamadan) ParchmentMuted else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ramadan Imsak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "10 minutes before Fajr, during Ramadan",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Saffron,
                    uncheckedTrackColor = ParchmentMuted,
                    checkedThumbColor = Parchment,
                    uncheckedThumbColor = Parchment,
                    uncheckedBorderColor = ParchmentMuted,
                ),
            )
        }
    }
}

@Composable
private fun VibrationRow(
    vibration: VibrationMode,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Vibration",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = vibration.displayName,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VibrationSheet(
    current: VibrationMode,
    onSelect: (VibrationMode) -> Unit,
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
                text = "Vibration",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 24.dp),
            )
            VibrationMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onSelect(mode)
                            }
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mode == current) Saffron else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (mode != VibrationMode.entries.last()) {
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
