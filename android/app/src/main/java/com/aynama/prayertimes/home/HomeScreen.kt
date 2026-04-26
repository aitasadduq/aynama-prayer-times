package com.aynama.prayertimes.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.ui.theme.IbmPlexSans
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.InkMuted
import com.aynama.prayertimes.ui.theme.Parchment
import com.aynama.prayertimes.ui.theme.ParchmentMuted
import com.aynama.prayertimes.ui.theme.Saffron

@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as AynamaApplication
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(app))
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        HomeUiState.Loading -> LoadingContent()
        HomeUiState.Empty -> EmptyContent(onCreateProfile = onNavigateToSettings)
        is HomeUiState.Error -> ErrorContent(cause = state.cause)
        is HomeUiState.Loaded -> LoadedContent(
            state = state,
            onNavigateToSettings = onNavigateToSettings,
            onDismissRamadanBanner = vm::dismissRamadanBanner,
        )
    }
}

@Composable
private fun LoadedContent(
    state: HomeUiState.Loaded,
    onNavigateToSettings: () -> Unit,
    onDismissRamadanBanner: () -> Unit,
) {
    val pageCount = state.profiles.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val activePage = pagerState.currentPage
    val activeProfile = state.profiles.getOrNull(activePage)

    val currentPhase = activeProfile?.currentPhase ?: PrayerPhase.ISHA
    val (gradTop, gradBottom) = gradientColorsFor(currentPhase)
    val isLightPhase = currentPhase == PrayerPhase.DHUHR || currentPhase == PrayerPhase.ASR ||
        currentPhase == PrayerPhase.SUNRISE_TRANSITION
    val contentColor = if (isLightPhase) Ink else Parchment

    val animTop by animateColorAsState(gradTop, animationSpec = tween(3000), label = "gradTop")
    val animBottom by animateColorAsState(gradBottom, animationSpec = tween(3000), label = "gradBottom")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(animTop, animBottom))),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    if (page < state.profiles.size) {
                        ProfilePage(
                            profileState = state.profiles[page],
                            pageIndex = page,
                            pageCount = state.profiles.size,
                        )
                    } else {
                        AddProfilePage(onNavigateToSettings = onNavigateToSettings)
                    }
                }

                DotsIndicator(
                    pageCount = pageCount,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp),
                )
            }

            if (activeProfile?.showRamadanBanner == true) {
                RamadanBanner(
                    onDismiss = onDismissRamadanBanner,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfilePage(
    profileState: ProfileUiState,
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .semantics {
                contentDescription = "Profile page ${pageIndex + 1} of $pageCount: ${profileState.profile.name}"
            },
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Home · ${profileState.profile.name}",
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = profileState.countdownText,
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.semantics {
                contentDescription = "Countdown to ${profileState.nextPrayerName}: ${profileState.countdownText}"
            },
        )

        Text(
            text = "${profileState.nextPrayerName} · ${profileState.nextPrayerTime}",
            style = MaterialTheme.typography.displaySmall,
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            profileState.ribbonRows.forEach { row ->
                RibbonRowItem(row = row)
            }
        }

        if (profileState.outstandingQazaCount > 0) {
            Text(
                text = "${profileState.outstandingQazaCount} outstanding Qaḍā",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RibbonRowItem(row: RibbonRow, modifier: Modifier = Modifier) {
    when (row) {
        is RibbonRow.PrayerEntry -> PrayerRibbonRow(row = row, modifier = modifier)
        is RibbonRow.SunriseEntry -> SunriseRibbonRow(row = row, modifier = modifier)
        is RibbonRow.ImsakEntry -> ImsakRibbonRow(row = row, modifier = modifier)
    }
}

@Composable
private fun PrayerRibbonRow(row: RibbonRow.PrayerEntry, modifier: Modifier = Modifier) {
    // On light phases LocalContentColor is Ink; on dark phases it is Parchment.
    val isLightBackground = LocalContentColor.current == Ink
    // Use the corresponding muted token directly (no alpha) so passed rows are readable
    // regardless of where they fall in the gradient.
    val mutedColor = if (isLightBackground) InkMuted else ParchmentMuted
    // Saffron accent is unreadable on light-phase backgrounds (the Asr gradient bottom IS
    // saffron). Use Ink on light phases so the current prayer always has contrast.
    val currentColor = if (isLightBackground) Ink else Saffron
    val textColor = when (row.ribbonState) {
        RibbonState.PASSED -> mutedColor
        RibbonState.CURRENT -> currentColor
        RibbonState.UPCOMING -> LocalContentColor.current
    }
    val prayerName = row.prayer.displayName()
    val stateLabel = when (row.ribbonState) {
        RibbonState.PASSED -> "passed"
        RibbonState.CURRENT -> "current"
        RibbonState.UPCOMING -> "upcoming"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$prayerName ${row.displayTime}, $stateLabel"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (row.ribbonState) {
                RibbonState.PASSED -> Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedColor,
                )
                RibbonState.CURRENT -> Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(currentColor),
                )
                RibbonState.UPCOMING -> {}
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = prayerName,
            style = MaterialTheme.typography.headlineMedium,
            color = textColor,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = row.displayTime,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = IbmPlexSans,
                fontFeatureSettings = "tnum",
            ),
            color = textColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SunriseRibbonRow(row: RibbonRow.SunriseEntry, modifier: Modifier = Modifier) {
    val mutedColor = if (LocalContentColor.current == Ink) InkMuted else ParchmentMuted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Sunrise ${row.displayTime}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))

        Text(
            text = "Sunrise",
            style = MaterialTheme.typography.headlineMedium,
            color = mutedColor,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = row.displayTime,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = IbmPlexSans,
                fontFeatureSettings = "tnum",
            ),
            color = mutedColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ImsakRibbonRow(row: RibbonRow.ImsakEntry, modifier: Modifier = Modifier) {
    val mutedColor = if (LocalContentColor.current == Ink) InkMuted else ParchmentMuted
    val textColor = if (row.isPast) mutedColor else LocalContentColor.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Imsak ${row.displayTime}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))

        Text(
            text = "Imsak",
            style = MaterialTheme.typography.headlineMedium,
            color = textColor,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = row.displayTime,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = IbmPlexSans,
                fontFeatureSettings = "tnum",
            ),
            color = textColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AddProfilePage(onNavigateToSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onNavigateToSettings)
            .semantics { contentDescription = "Add a new profile" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "+",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add profile",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DotsIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (isSelected) 8.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Saffron else LocalContentColor.current.copy(alpha = 0.3f),
                    )
                    .semantics { contentDescription = "Page ${index + 1}" },
            )
        }
    }
}

@Composable
private fun RamadanBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF6B2E2A).copy(alpha = 0.9f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ramaḍān Mubārak",
                style = MaterialTheme.typography.bodyMedium,
                color = Parchment,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Dismiss",
                    style = MaterialTheme.typography.labelMedium,
                    color = Parchment.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun EmptyContent(onCreateProfile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "🕋",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Set up your first prayer profile",
            style = MaterialTheme.typography.headlineMedium,
            color = Parchment,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Add a location to see accurate prayer times.",
            style = MaterialTheme.typography.bodyMedium,
            color = Parchment.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onCreateProfile,
            colors = ButtonDefaults.buttonColors(containerColor = Saffron),
        ) {
            Text(
                text = "Create profile",
                color = Ink,
            )
        }
    }
}

@Composable
private fun ErrorContent(cause: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineMedium,
            color = Parchment,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = cause,
            style = MaterialTheme.typography.bodySmall,
            color = Parchment.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    )
}

private fun gradientColorsFor(phase: PrayerPhase): Pair<Color, Color> = when (phase) {
    PrayerPhase.FAJR -> Color(0xFF1C1A17) to Color(0xFF3A3530)
    PrayerPhase.SUNRISE_TRANSITION -> Color(0xFF3A3530) to Color(0xFFE8C89A)
    PrayerPhase.DHUHR -> Color(0xFFF2EAD8) to Color(0xFFEDE1C5)
    PrayerPhase.ASR -> Color(0xFFE8C89A) to Color(0xFFB87A2E)
    PrayerPhase.MAGHRIB -> Color(0xFF6B2E2A) to Color(0xFF1C1A17)
    PrayerPhase.ISHA -> Color(0xFF0F1419) to Color(0xFF1C1A17)
}

private fun Prayer.displayName(): String = when (this) {
    Prayer.FAJR -> "Fajr"
    Prayer.DHUHR -> "Dhuhr"
    Prayer.ASR -> "Asr"
    Prayer.MAGHRIB -> "Maghrib"
    Prayer.ISHA -> "Isha"
}
