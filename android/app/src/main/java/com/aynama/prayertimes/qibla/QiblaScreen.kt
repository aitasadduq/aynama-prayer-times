package com.aynama.prayertimes.qibla

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.home.PrayerPhase
import com.aynama.prayertimes.home.gradientColorsFor
import com.aynama.prayertimes.home.isLightPhase
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.Parchment
import com.aynama.prayertimes.ui.theme.Saffron
import com.aynama.prayertimes.ui.theme.SaffronInk
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

// Set to false to hide debug overlay before shipping
private const val SHOW_DEBUG = true

@Composable
fun QiblaScreen() {
    val app = LocalContext.current.applicationContext as AynamaApplication
    val vm: QiblaViewModel = viewModel(factory = QiblaViewModel.factory(app))
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.start()
                Lifecycle.Event.ON_PAUSE -> vm.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stop()
        }
    }

    android.util.Log.d("Qibla", "compose state=${uiState::class.simpleName}")
    when (val state = uiState) {
        QiblaUiState.Loading -> LoadingContent()
        QiblaUiState.NoProfile -> NoProfileContent()
        QiblaUiState.NoSensor -> NoSensorContent()
        is QiblaUiState.Ready -> ReadyContent(state)
    }
}

@Composable
private fun ReadyContent(state: QiblaUiState.Ready) {
    val context = LocalContext.current

    val (gradTop, gradBottom) = gradientColorsFor(state.phase)
    val contentColor = if (isLightPhase(state.phase)) Ink else Parchment

    val animTop by animateColorAsState(gradTop, animationSpec = tween(3000), label = "gradTop")
    val animBottom by animateColorAsState(gradBottom, animationSpec = tween(3000), label = "gradBottom")

    val targetArrowAngle = state.qiblaBearing - state.unwrappedAzimuth
    val normalizedArrow = ((targetArrowAngle % 360f) + 360f) % 360f
    val isAligned = normalizedArrow < 5f || normalizedArrow > 355f

    val animatedAngle by animateFloatAsState(
        targetValue = targetArrowAngle,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 100f),
        label = "arrowRotation",
    )

    // Haptic pulse on alignment
    LaunchedEffect(isAligned) {
        if (isAligned) {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    // Accessibility — throttle live-region announcements to every 15° of device rotation
    var lastAnnouncedAzimuth by remember { mutableFloatStateOf(-1f) }
    var announcedA11y by remember { mutableStateOf("") }
    val azimuthDelta = abs(state.azimuth - lastAnnouncedAzimuth).let { if (it > 180) 360 - it else it }
    if (lastAnnouncedAzimuth < 0 || azimuthDelta >= 15f) {
        lastAnnouncedAzimuth = state.azimuth
        announcedA11y = buildA11yDescription(state.azimuth, state.qiblaBearing)
    }

    val formattedDistance = NumberFormat.getNumberInstance().format(state.distanceKm.roundToInt())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(animTop, animBottom))),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .semantics {
                        contentDescription = announcedA11y
                        liveRegion = LiveRegionMode.Polite
                    },
                contentAlignment = Alignment.Center,
            ) {
                QiblaArrow(
                    rotationDegrees = animatedAngle,
                    contentColor = contentColor,
                    modifier = Modifier.fillMaxSize(),
                )
                NorthIndicator(
                    azimuth = state.azimuth,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "${state.qiblaDegrees}°",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFeatureSettings = "tnum",
                ),
                color = contentColor,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "$formattedDistance km",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(48.dp))
        }

        if (state.accuracy != SensorAccuracy.HIGH) {
            CalibrationBanner(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }

        if (SHOW_DEBUG) {
            DebugOverlay(
                state = state,
                arrowAngle = targetArrowAngle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun NorthIndicator(azimuth: Float, modifier: Modifier = Modifier) {
    // Rotates opposite to the device heading so "N" always points to magnetic north
    Canvas(modifier = modifier.rotate(-azimuth)) {
        val cx = size.width / 2f
        val r = size.width / 2f - 4.dp.toPx()
        val tipY = r * 0.08f
        val arrowHalf = size.width * 0.06f

        // Red north arrow tip
        val arrowPath = Path().apply {
            moveTo(cx, tipY)
            lineTo(cx + arrowHalf, tipY + arrowHalf * 1.5f)
            lineTo(cx - arrowHalf, tipY + arrowHalf * 1.5f)
            close()
        }
        drawPath(arrowPath, color = Color(0xFFFF3B30))

        // Thin stem from arrowhead to center
        drawLine(
            color = Color(0xFFFF3B30).copy(alpha = 0.6f),
            start = Offset(cx, tipY + arrowHalf * 1.5f),
            end = Offset(cx, r * 0.55f),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
private fun DebugOverlay(
    state: QiblaUiState.Ready,
    arrowAngle: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DebugRow("RAW  ", "%.1f°".format(state.rawAzimuth))
            DebugRow("SMTH ", "%.1f°".format(state.azimuth))
            DebugRow("QIBLA", "%.1f°".format(state.qiblaBearing))
            DebugRow("ARROW", "%.1f°".format(arrowAngle))
            DebugRow("PITCH", "%.1f°".format(state.pitch))
            DebugRow("ROLL ", "%.1f°".format(state.roll))
            DebugRow("ACC  ", state.accuracy.name)
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFAAAAAA),
        )
        Text(
            text = value,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
        )
    }
}

@Composable
private fun QiblaArrow(
    rotationDegrees: Float,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val arrowColor = Saffron
    val outlineColor = if (contentColor == Ink) Ink else SaffronInk

    Canvas(modifier = modifier.rotate(rotationDegrees)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val headBottom = h * 0.44f
        val shaftHalf = w * 0.14f
        val headHalf = w * 0.40f

        val path = Path().apply {
            moveTo(cx, 0f)
            lineTo(cx + headHalf, headBottom)
            lineTo(cx + shaftHalf, headBottom)
            lineTo(cx + shaftHalf, h)
            lineTo(cx - shaftHalf, h)
            lineTo(cx - shaftHalf, headBottom)
            lineTo(cx - headHalf, headBottom)
            close()
        }

        drawPath(path, color = arrowColor, style = Fill)
        drawPath(path, color = outlineColor, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun CalibrationBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF7A5800).copy(alpha = 0.9f),
    ) {
        Text(
            text = "Hold phone flat and move in a figure-8 to calibrate",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFF3CD),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize().background(Ink))
}

@Composable
private fun NoProfileContent() {
    Box(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Set up a prayer profile to find Qibla direction",
            style = MaterialTheme.typography.bodyMedium,
            color = Parchment,
        )
    }
}

@Composable
private fun NoSensorContent() {
    Box(
        modifier = Modifier.fillMaxSize().background(Parchment),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Compass not available on this device",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
        )
    }
}

private fun buildA11yDescription(azimuth: Float, qiblaBearing: Float): String {
    val facing = bearingToCardinal(azimuth.toDouble())
    val qiblaDir = bearingToCardinal(qiblaBearing.toDouble())
    val diff = ((qiblaBearing - azimuth + 360f) % 360f)
    val relDir = when {
        diff < 5f || diff > 355f -> "straight ahead"
        diff <= 180f -> "turn right"
        else -> "turn left"
    }
    return "Facing $facing. Qibla is to the $qiblaDir — $relDir"
}

private fun bearingToCardinal(degrees: Double): String {
    val normalized = ((degrees % 360) + 360) % 360
    return when (normalized.toInt()) {
        in 338..360, in 0..22 -> "north"
        in 23..67 -> "northeast"
        in 68..112 -> "east"
        in 113..157 -> "southeast"
        in 158..202 -> "south"
        in 203..247 -> "southwest"
        in 248..292 -> "west"
        in 293..337 -> "northwest"
        else -> "north"
    }
}
