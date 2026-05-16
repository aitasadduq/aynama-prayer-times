package com.aynama.prayertimes.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.Saffron

@Composable
fun SettingsScreen() {
    val app = LocalContext.current.applicationContext as AynamaApplication
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app))
    val profiles by vm.profiles.collectAsStateWithLifecycle()

    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProfile = null
                    showSheet = true
                },
                containerColor = Saffron,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add profile", tint = Ink)
            }
        },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    text = "Profiles",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp, end = 24.dp),
                )
            }

            items(profiles, key = { it.id }) { profile ->
                val dismissState = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                        vm.delete(profile)
                    }
                }
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(end = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    },
                ) {
                    ProfileRow(
                        profile = profile,
                        onClick = {
                            editingProfile = profile
                            showSheet = true
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }

    if (showSheet) {
        ProfileFormSheet(
            initial = editingProfile,
            onSave = { profile ->
                vm.save(profile)
                showSheet = false
            },
            onDelete = { profile ->
                vm.delete(profile)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun ProfileRow(profile: Profile, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = profile.name,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${profile.latitude.formatCoord()}, ${profile.longitude.formatCoord()} · ${profile.calculationMethod.displayName()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileFormSheet(
    initial: Profile?,
    onSave: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var lat by remember { mutableStateOf(initial?.latitude?.toString() ?: "") }
    var lng by remember { mutableStateOf(initial?.longitude?.toString() ?: "") }
    var method by remember { mutableStateOf(initial?.calculationMethod ?: CalculationMethodKey.MWL) }
    var madhab by remember { mutableStateOf(initial?.asrMadhab ?: AsrMadhab.SHAFII) }

    val latDouble = lat.toDoubleOrNull()
    val lngDouble = lng.toDoubleOrNull()
    val valid = name.isNotBlank()
        && latDouble != null && latDouble in -90.0..90.0
        && lngDouble != null && lngDouble in -180.0..180.0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
        ) {
            Text(
                text = if (initial == null) "New profile" else "Edit profile",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = lat.isNotEmpty() && (latDouble == null || latDouble !in -90.0..90.0),
                )
                OutlinedTextField(
                    value = lng,
                    onValueChange = { lng = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = lng.isNotEmpty() && (lngDouble == null || lngDouble !in -180.0..180.0),
                )
            }

            Spacer(Modifier.height(12.dp))

            CalculationMethodPicker(selected = method, onSelect = { method = it })

            Spacer(Modifier.height(12.dp))

            AsrMadhabSelector(selected = madhab, onSelect = { madhab = it })

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val base = initial ?: Profile(
                        name = "",
                        latitude = 0.0,
                        longitude = 0.0,
                        calculationMethod = CalculationMethodKey.MWL,
                        asrMadhab = AsrMadhab.SHAFII,
                        isGps = false,
                        sortOrder = 0,
                    )
                    onSave(base.copy(
                        name = name.trim(),
                        latitude = latDouble!!,
                        longitude = lngDouble!!,
                        calculationMethod = method,
                        asrMadhab = madhab,
                    ))
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Saffron, contentColor = Ink),
            ) {
                Text("Save")
            }

            if (initial != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDelete(initial) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete profile")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculationMethodPicker(
    selected: CalculationMethodKey,
    onSelect: (CalculationMethodKey) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.displayName(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Calculation method") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            CalculationMethodKey.entries.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key.displayName()) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun AsrMadhabSelector(
    selected: AsrMadhab,
    onSelect: (AsrMadhab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsrMadhab.entries.forEach { madhab ->
            val isSelected = madhab == selected
            if (isSelected) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Saffron, contentColor = Ink),
                ) {
                    Text(madhab.displayName())
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(madhab) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(madhab.displayName())
                }
            }
        }
    }
}

private fun Double.formatCoord(): String = "%.4f".format(this)

private fun CalculationMethodKey.displayName(): String = when (this) {
    CalculationMethodKey.MWL -> "Muslim World League"
    CalculationMethodKey.ISNA -> "ISNA"
    CalculationMethodKey.UMM_AL_QURA -> "Umm al-Qurā"
    CalculationMethodKey.EGYPTIAN -> "Egyptian"
    CalculationMethodKey.KARACHI -> "Karachi"
    CalculationMethodKey.DUBAI -> "Dubai"
    CalculationMethodKey.MOON_SIGHTING_COMMITTEE -> "Moon Sighting Committee"
    CalculationMethodKey.KUWAIT -> "Kuwait"
    CalculationMethodKey.QATAR -> "Qatar"
    CalculationMethodKey.SINGAPORE -> "Singapore"
}

private fun AsrMadhab.displayName(): String = when (this) {
    AsrMadhab.SHAFII -> "Shāfiʻī"
    AsrMadhab.HANAFI -> "Ḥanafī"
}
