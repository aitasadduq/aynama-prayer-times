package com.aynama.prayertimes.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.Saffron
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
