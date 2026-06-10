package com.aynama.prayertimes.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.R
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.ui.theme.AynamaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default to CANCELED — back-press must not add the widget
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }


        val app = applicationContext as AynamaApplication

        enableEdgeToEdge()
        setContent {
            AynamaTheme {
                WidgetConfigureScreen(
                    app = app,
                    appWidgetId = appWidgetId,
                    onProfileSelected = { profile -> saveAndFinish(profile) },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun saveAndFinish(profile: Profile) {
        val app = application as AynamaApplication
        val id = appWidgetId
        // Run the persist+render on the app scope so it completes even if the launcher tears this
        // activity down; join it before returning OK so the state is durable before the launcher
        // binds/renders the (possibly new) widget. This avoids the place/edit races.
        lifecycleScope.launch {
            app.appScope.launch { setWidgetProfile(applicationContext, id, profile.id) }.join()
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
    }
}

@Composable
private fun WidgetConfigureScreen(
    app: AynamaApplication,
    appWidgetId: Int,
    onProfileSelected: (Profile) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var currentProfileId by remember { mutableStateOf(-1L) }

    LaunchedEffect(Unit) {
        profiles = app.profileRepository.observeAll().first()
        currentProfileId = widgetProfileIdFor(context, appWidgetId)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeContent)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.widget_configure_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.widget_configure_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.widget_configure_no_profiles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileChoiceRow(
                            profile = profile,
                            selected = profile.id == currentProfileId,
                            onClick = { onProfileSelected(profile) },
                        )
                        HorizontalDivider()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.widget_configure_cancel))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfileChoiceRow(
    profile: Profile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
