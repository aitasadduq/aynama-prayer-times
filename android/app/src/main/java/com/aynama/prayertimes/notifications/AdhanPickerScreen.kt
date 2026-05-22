package com.aynama.prayertimes.notifications

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.InkMuted
import com.aynama.prayertimes.ui.theme.ParchmentMuted
import com.aynama.prayertimes.ui.theme.Saffron

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhanPickerScreen(onNavigateBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as AynamaApplication
    val vm: NotificationSettingsViewModel = viewModel(factory = NotificationSettingsViewModel.factory(app))
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Adhan voice",
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
            items(AdhanVoice.entries) { voice ->
                AdhanVoiceRow(
                    voice = voice,
                    selected = state.adhanVoice == voice,
                    onSelect = { vm.setAdhanVoice(voice) },
                )
                HorizontalDivider(
                    color = ParchmentMuted,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun AdhanVoiceRow(
    voice: AdhanVoice,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onSelect)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioIndicator(selected = selected)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = voice.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            if (voice.caption.isNotEmpty()) {
                Text(
                    text = voice.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
        IconButton(
            onClick = {
                Toast.makeText(context, "Adhan assets coming soon", Toast.LENGTH_SHORT).show()
            }
        ) {
            Icon(
                Icons.Default.PlayCircleOutline,
                contentDescription = "Preview ${voice.displayName} adhan",
                tint = InkMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun RadioIndicator(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .drawBehind {
                val strokePx = 1.5.dp.toPx()
                val radius = (size.minDimension - strokePx) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = Ink,
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx),
                )
                if (selected) {
                    drawCircle(
                        color = Saffron,
                        radius = radius * 0.5f,
                        center = center,
                    )
                }
            },
    )
}
