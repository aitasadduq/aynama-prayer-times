package com.aynama.prayertimes.settings

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.InkMuted
import com.aynama.prayertimes.ui.theme.Parchment
import com.aynama.prayertimes.ui.theme.ParchmentMuted
import com.aynama.prayertimes.ui.theme.Saffron
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(onNavigateToNotifications: () -> Unit = {}) {
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
                NotificationsEntryRow(onClick = onNavigateToNotifications)
                HorizontalDivider()
            }

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
private fun NotificationsEntryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var method by remember { mutableStateOf(initial?.calculationMethod ?: CalculationMethodKey.MWL) }
    var madhab by remember { mutableStateOf(initial?.asrMadhab ?: AsrMadhab.SHAFII) }

    var locationLat by remember { mutableStateOf(initial?.latitude) }
    var locationLng by remember { mutableStateOf(initial?.longitude) }
    var locationLabel by remember { mutableStateOf("") }
    var locationTimezone by remember { mutableStateOf(initial?.timezone ?: "") }
    var useLocationTimezone by remember { mutableStateOf(initial?.useLocationTimezone ?: true) }

    LaunchedEffect(Unit) {
        if (initial != null) {
            withContext(Dispatchers.IO) {
                val address = reverseGeocodeAddress(context, initial.latitude, initial.longitude)
                val label = address?.let { buildCityLabel(it) }
                    ?: "${initial.latitude.formatCoord()}, ${initial.longitude.formatCoord()}"
                val tz = when {
                    initial.timezone.isNotBlank() -> initial.timezone
                    initial.isGps -> ZoneId.systemDefault().id
                    address != null -> detectTimezoneForLocation(address.countryCode, initial.longitude)
                    else -> ""
                }
                label to tz
            }.also { (label, tz) ->
                locationLabel = label
                if (locationTimezone.isBlank()) locationTimezone = tz
            }
        }
    }

    val valid = name.isNotBlank() && locationLat != null && locationLng != null

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

            LocationSection(
                label = locationLabel,
                hasSelection = locationLat != null,
                onLocationSelected = { lat, lng, label, tz ->
                    locationLat = lat
                    locationLng = lng
                    locationLabel = label
                    locationTimezone = tz
                    if (tz.isBlank()) useLocationTimezone = false
                },
                onGpsRequested = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { getGpsLocation(context) }
                        if (result != null) {
                            locationLat = result.first
                            locationLng = result.second
                            locationLabel = result.third
                            locationTimezone = ZoneId.systemDefault().id
                        }
                    }
                },
            )

            if (locationTimezone.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                LocationTimezoneToggle(
                    timezone = locationTimezone,
                    checked = useLocationTimezone,
                    onCheckedChange = { useLocationTimezone = it },
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
                        latitude = locationLat!!,
                        longitude = locationLng!!,
                        calculationMethod = method,
                        asrMadhab = madhab,
                        timezone = locationTimezone,
                        useLocationTimezone = useLocationTimezone,
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

@Composable
private fun LocationSection(
    label: String,
    hasSelection: Boolean,
    onLocationSelected: (Double, Double, String, String) -> Unit,
    onGpsRequested: () -> Unit,
) {
    val context = LocalContext.current
    var isSearching by remember { mutableStateOf(!hasSelection) }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Address>>(emptyList()) }

    val permLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) onGpsRequested()
    }

    LaunchedEffect(query) {
        if (query.length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        suggestions = withContext(Dispatchers.IO) { searchCity(context, query) }
    }

    if (!isSearching && hasSelection) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            TextButton(onClick = {
                isSearching = true
                query = ""
                suggestions = emptyList()
            }) {
                Text("Change")
            }
        }
    } else {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("City or location") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (suggestions.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                suggestions.forEach { address ->
                    val cityLabel = buildCityLabel(address)
                    Text(
                        text = cityLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val tz = detectTimezoneForLocation(address.countryCode, address.longitude)
                                onLocationSelected(address.latitude, address.longitude, cityLabel, tz)
                                isSearching = false
                                query = ""
                                suggestions = emptyList()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    onGpsRequested()
                    isSearching = false
                } else {
                    permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("Use current location")
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

@Composable
private fun LocationTimezoneToggle(
    timezone: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val displayName = remember(timezone) {
        runCatching { ZoneId.of(timezone).getDisplayName(TextStyle.FULL, Locale.getDefault()) }
            .getOrDefault(timezone)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Use location time zone", style = MaterialTheme.typography.bodyMedium)
            Text(displayName, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Saffron,
                checkedThumbColor = Parchment,
                uncheckedTrackColor = ParchmentMuted,
                uncheckedThumbColor = Parchment,
            ),
        )
    }
}

private fun detectTimezoneForLocation(countryCode: String?, longitude: Double): String {
    if (countryCode.isNullOrBlank()) return ""
    return try {
        val ids = android.icu.util.TimeZone.getAvailableIDs(countryCode)
        if (ids.isEmpty()) return ""
        if (ids.size == 1) return ids[0]
        // Longitude gives an approximate raw UTC offset. Within the country's timezone list
        // this is accurate enough to resolve the correct zone for nearly all major cities.
        val approxOffsetMs = (longitude / 15.0 * 3_600_000).toInt()
        ids.minByOrNull { id ->
            kotlin.math.abs(android.icu.util.TimeZone.getTimeZone(id).rawOffset - approxOffsetMs)
        } ?: ids[0]
    } catch (_: Exception) {
        ""
    }
}

private fun buildCityLabel(address: Address): String {
    val city = address.locality ?: address.subAdminArea ?: address.adminArea
    val country = address.countryName
    return listOfNotNull(city, country).joinToString(", ").ifBlank {
        address.getAddressLine(0) ?: "${address.latitude.formatCoord()}, ${address.longitude.formatCoord()}"
    }
}

@Suppress("DEPRECATION")
private fun reverseGeocodeAddress(context: android.content.Context, lat: Double, lng: Double): Address? {
    if (!Geocoder.isPresent()) return null
    return try {
        val geocoder = Geocoder(context)
        if (Build.VERSION.SDK_INT >= 33) {
            val latch = java.util.concurrent.CountDownLatch(1)
            var addr: Address? = null
            geocoder.getFromLocation(lat, lng, 1) { list ->
                addr = list.firstOrNull()
                latch.countDown()
            }
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            addr
        } else {
            geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
        }
    } catch (_: Exception) {
        null
    }
}

private fun reverseGeocode(context: android.content.Context, lat: Double, lng: Double): String? =
    reverseGeocodeAddress(context, lat, lng)?.let { buildCityLabel(it) }

@Suppress("DEPRECATION")
private fun searchCity(context: android.content.Context, query: String): List<Address> {
    if (!Geocoder.isPresent()) return emptyList()
    return try {
        val geocoder = Geocoder(context)
        if (Build.VERSION.SDK_INT >= 33) {
            val latch = java.util.concurrent.CountDownLatch(1)
            val results = mutableListOf<Address>()
            geocoder.getFromLocationName(query, 5) { list ->
                results.addAll(list)
                latch.countDown()
            }
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            results
        } else {
            geocoder.getFromLocationName(query, 5) ?: emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun getGpsLocation(context: android.content.Context): Triple<Double, Double, String>? {
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    val location = providers.firstNotNullOfOrNull { provider ->
        try {
            if (lm.isProviderEnabled(provider)) {
                @Suppress("MissingPermission")
                lm.getLastKnownLocation(provider)
            } else null
        } catch (_: Exception) { null }
    } ?: return null
    val label = reverseGeocode(context, location.latitude, location.longitude)
        ?: "${location.latitude.formatCoord()}, ${location.longitude.formatCoord()}"
    return Triple(location.latitude, location.longitude, label)
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
