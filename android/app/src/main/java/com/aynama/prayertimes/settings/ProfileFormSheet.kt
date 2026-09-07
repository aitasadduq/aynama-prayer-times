package com.aynama.prayertimes.settings

/*
 * The profile create/edit form.
 *
 * Lifted out of SettingsScreen so the Prayers screen can open the same sheet from its FAB
 * (IMPLEMENTATION_PLAN.md Phase 1). One form, so a field added here cannot appear on one
 * entry point and not the other.
 */

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aynama.prayertimes.notifications.RamadanDetector
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.InkMuted
import com.aynama.prayertimes.ui.theme.Parchment
import com.aynama.prayertimes.ui.theme.ParchmentMuted
import com.aynama.prayertimes.ui.theme.Saffron
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
@Composable
private fun HijriOffsetSelector(selected: Int, onSelect: (Int) -> Unit) {
    val options = (-2..2).map { value ->
        value to when {
            value > 0 -> "+$value"
            value < 0 -> "−${-value}"
            else -> "0"
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            if (value == selected) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Saffron, contentColor = Ink),
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileFormSheet(
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
    var hijriOffset by remember {
        val effective = initial?.let {
            val zone = it.effectiveZoneId()
            RamadanDetector.effectiveHijriOffset(it.hijriOffset, it.hijriOffsetMonthKey, LocalDate.now(zone), zone)
        } ?: 0
        mutableStateOf(effective)
    }

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

    // Open fully expanded so the whole form — Save button included — is visible at once,
    // instead of the half-height sheet the user had to drag up. On screens too small for
    // the full form, the fields scroll while Save (and Delete) stay pinned below them.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
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

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Hijri date adjustment",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 2.dp),
                )

                Text(
                    text = "Shifts the Hijri date and Ramadan for local moon sighting. 0 keeps the calculated date; + starts the month earlier, − later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                HijriOffsetSelector(selected = hijriOffset, onSelect = { hijriOffset = it })
            }

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
                    val saved = base.copy(
                        name = name.trim(),
                        latitude = locationLat!!,
                        longitude = locationLng!!,
                        calculationMethod = method,
                        asrMadhab = madhab,
                        timezone = locationTimezone,
                        useLocationTimezone = useLocationTimezone,
                        hijriOffset = hijriOffset,
                    )
                    val zone = saved.effectiveZoneId()
                    val monthKey = if (hijriOffset == 0) 0
                        else RamadanDetector.hijriMonthKey(LocalDate.now(zone).plusDays(hijriOffset.toLong()), zone)
                    onSave(saved.copy(hijriOffsetMonthKey = monthKey))
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

internal fun Double.formatCoord(): String = "%.4f".format(this)

internal fun CalculationMethodKey.displayName(): String = when (this) {
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

internal fun AsrMadhab.displayName(): String = when (this) {
    AsrMadhab.SHAFII -> "Shāfiʻī"
    AsrMadhab.HANAFI -> "Ḥanafī"
}
