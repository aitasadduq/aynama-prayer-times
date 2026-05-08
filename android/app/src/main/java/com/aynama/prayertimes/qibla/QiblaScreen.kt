package com.aynama.prayertimes.qibla

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.home.PrayerPhase
import com.aynama.prayertimes.home.gradientColorsFor
import com.aynama.prayertimes.home.isLightPhase
import com.aynama.prayertimes.shared.SensorAccuracy
import com.aynama.prayertimes.ui.theme.IbmPlexSans
import com.aynama.prayertimes.ui.theme.Ink
import com.aynama.prayertimes.ui.theme.InkMuted
import com.aynama.prayertimes.ui.theme.Parchment
import com.aynama.prayertimes.ui.theme.Saffron
import com.aynama.prayertimes.ui.theme.SaffronInk
import com.aynama.prayertimes.ui.theme.frauncesFamily
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SHOW_DEBUG = false
// Hysteresis: enter the aligned state at ≤5°, only exit when drift exceeds 7°.
// Prevents haptic spam when the smoothed azimuth jitters across a single threshold.
private const val QIBLA_ALIGN_ENTER_DEG = 5f
private const val QIBLA_ALIGN_EXIT_DEG = 7f
private const val A11Y_ANNOUNCE_THRESHOLD_DEG = 15f

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
    val isDark = !isLightPhase(state.phase)

    val boxBg = if (isDark) Ink else Parchment
    val boxFg = if (isDark) Parchment else Ink
    val boxFgMuted = if (isDark) Parchment.copy(alpha = 0.65f) else InkMuted
    val boxFgQuiet = if (isDark) Parchment.copy(alpha = 0.4f) else Ink.copy(alpha = 0.4f)
    val boxAccent = if (isDark) Saffron else SaffronInk

    val (gradTop, gradBottom) = gradientColorsFor(state.phase)
    val animTop by animateColorAsState(gradTop, animationSpec = tween(3000), label = "gradTop")
    val animBottom by animateColorAsState(gradBottom, animationSpec = tween(3000), label = "gradBottom")

    val animatedRoseAngle by animateFloatAsState(
        targetValue = -state.unwrappedAzimuth,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 100f),
        label = "roseAngle",
    )

    val delta = ((state.qiblaBearing - state.azimuth + 540f) % 360f) - 180f
    val absDelta = abs(delta)
    var isAligned by remember { mutableStateOf(false) }
    LaunchedEffect(absDelta) {
        val next = if (isAligned) absDelta < QIBLA_ALIGN_EXIT_DEG else absDelta < QIBLA_ALIGN_ENTER_DEG
        if (next != isAligned) isAligned = next
    }
    val turnHint = when {
        isAligned -> "— Aligned —"
        delta > 0 -> "Turn right ${absDelta.roundToInt()}°"
        else -> "Turn left ${absDelta.roundToInt()}°"
    }
    val hintColor by animateColorAsState(
        targetValue = if (isAligned) boxAccent else boxFgMuted,
        animationSpec = tween(300),
        label = "hintColor",
    )

    var hapticReady by remember { mutableStateOf(false) }
    LaunchedEffect(isAligned) {
        if (!hapticReady) { hapticReady = true; return@LaunchedEffect }
        if (isAligned) {
            // getSystemService(Vibrator::class.java) is @Nullable on devices without
            // a vibrator service (some tablets, emulators, AAOS). Skip silently.
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    val quantizedAzimuth = (state.azimuth / A11Y_ANNOUNCE_THRESHOLD_DEG).roundToInt()
    val announcedA11y = remember(quantizedAzimuth, state.qiblaBearing) {
        buildA11yDescription(state.azimuth, state.qiblaBearing)
    }

    val numberFormat = remember { NumberFormat.getNumberInstance() }
    val formattedDistance = numberFormat.format(state.distanceKm.roundToInt())

    val frauncesTitle = remember { frauncesFamily(32f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(animTop, animBottom))),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .background(boxBg, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Qibla",
                    style = TextStyle(
                        fontFamily = frauncesTitle,
                        fontWeight = FontWeight.Medium,
                        fontSize = 28.sp,
                        lineHeight = 28.sp,
                        letterSpacing = (-0.01f).em,
                    ),
                    color = boxFg,
                )
                Text(
                    text = state.phase.displayName().uppercase(),
                    style = TextStyle(
                        fontFamily = IbmPlexSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        letterSpacing = 0.08f.em,
                    ),
                    color = boxAccent,
                )
            }

            // Compass region
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Rose: rotates with device heading
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .rotate(animatedRoseAngle)
                        .semantics {
                            contentDescription = announcedA11y
                            liveRegion = LiveRegionMode.Polite
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f - 14.dp.toPx()
                        drawCircle(
                            color = boxBg,
                            radius = r,
                            style = Stroke(width = 28.dp.toPx()),
                        )
                    }

                    // Qibla arrow — points to qiblaBearing within rose space
                    Box(
                        modifier = Modifier
                            .size(width = 140.dp, height = 200.dp)
                            .rotate(state.qiblaBearing),
                        contentAlignment = Alignment.Center,
                    ) {
                        QiblaArrow(isDark = isDark, modifier = Modifier.fillMaxSize())
                    }

                    // N satellite — counter-rotates to stay upright on screen
                    Box(
                        modifier = Modifier
                            .offset(y = (-126).dp)
                            .rotate(-animatedRoseAngle),
                    ) {
                        NorthGlyph(fg = boxFg, fgMuted = boxFgMuted)
                    }
                }

                // Alignment hint — fixed above rose
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = boxBg,
                ) {
                    Text(
                        text = turnHint,
                        style = TextStyle(
                            fontFamily = IbmPlexSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            letterSpacing = 0.1f.em,
                        ),
                        color = hintColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // Readout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BearingReadout(
                    degrees = state.qiblaDegrees,
                    distanceLabel = "$formattedDistance km to the Kaaba",
                    boxBg = boxBg,
                    boxFg = boxFg,
                    boxFgMuted = boxFgMuted,
                )
                BearingChipsRow(
                    qiblaDeg = state.qiblaDegrees,
                    boxBg = boxBg,
                    boxFg = boxFg,
                    boxFgQuiet = boxFgQuiet,
                    boxAccent = boxAccent,
                )
            }
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
                arrowAngle = state.qiblaBearing - state.unwrappedAzimuth,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun BearingReadout(
    degrees: Int,
    distanceLabel: String,
    boxBg: Color,
    boxFg: Color,
    boxFgMuted: Color,
) {
    val frauncesLarge = remember { frauncesFamily(96f) }
    Box(
        modifier = Modifier
            .background(boxBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$degrees°",
                style = TextStyle(
                    fontFamily = frauncesLarge,
                    fontWeight = FontWeight.Normal,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    letterSpacing = (-0.025f).em,
                    fontFeatureSettings = "tnum",
                ),
                color = boxFg,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = distanceLabel,
                style = TextStyle(
                    fontFamily = IbmPlexSans,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                ),
                color = boxFgMuted,
            )
        }
    }
}

@Composable
private fun BearingChipsRow(
    qiblaDeg: Int,
    boxBg: Color,
    boxFg: Color,
    boxFgQuiet: Color,
    boxAccent: Color,
) {
    Row(
        modifier = Modifier
            .background(boxBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BearingChip(label = "Qibla", deg = qiblaDeg, color = boxAccent, dimDot = false, dimText = false)
        DotSep(color = boxFgQuiet)
        BearingChip(label = "North", deg = 0, color = boxFg, dimDot = true, dimText = true)
    }
}

@Composable
private fun BearingChip(
    label: String,
    deg: Int,
    color: Color,
    dimDot: Boolean,
    dimText: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(6.dp)
                .background(color.copy(alpha = if (dimDot) 0.6f else 1f), CircleShape),
        )
        Text(
            text = label,
            style = TextStyle(
                fontFamily = IbmPlexSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                letterSpacing = 0.1f.em,
            ),
            color = color.copy(alpha = if (dimText) 0.75f else 1f),
        )
        Text(
            text = "$deg°",
            style = TextStyle(
                fontFamily = IbmPlexSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                letterSpacing = 0.1f.em,
                fontFeatureSettings = "tnum",
            ),
            color = color.copy(alpha = if (dimText) 0.55f else 0.85f),
        )
    }
}

@Composable
private fun DotSep(color: Color) {
    Spacer(
        modifier = Modifier
            .size(3.dp)
            .background(color.copy(alpha = 0.7f), CircleShape),
    )
}

@Composable
private fun QiblaArrow(isDark: Boolean, modifier: Modifier = Modifier) {
    val arrowFill = Saffron
    val arrowOutline = if (isDark) Saffron.copy(alpha = 0.85f) else SaffronInk.copy(alpha = 0.85f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val headBottom = h * 0.42f
        val shaftHalf = w * 0.085f
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

        drawPath(path, color = arrowFill, style = Fill)
        drawPath(path, color = arrowOutline, style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
private fun NorthGlyph(fg: Color, fgMuted: Color) {
    val frauncesN = remember { frauncesFamily(20f) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Spacer(
            modifier = Modifier
                .size(width = 1.dp, height = 8.dp)
                .background(fgMuted.copy(alpha = 0.7f)),
        )
        Text(
            text = "N",
            style = TextStyle(
                fontFamily = frauncesN,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            ),
            color = fg,
        )
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
            style = TextStyle(
                fontFamily = IbmPlexSans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = (13 * 1.4f).sp,
            ),
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
            style = TextStyle(fontFamily = IbmPlexSans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
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
            style = TextStyle(fontFamily = IbmPlexSans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
            color = Ink,
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
        Text(text = label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFAAAAAA))
        Text(text = value, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
    }
}

private fun PrayerPhase.displayName(): String = when (this) {
    PrayerPhase.FAJR -> "Fajr"
    PrayerPhase.SUNRISE_TRANSITION -> "Sunrise"
    PrayerPhase.DHUHR -> "Dhuhr"
    PrayerPhase.ASR -> "Asr"
    PrayerPhase.MAGHRIB -> "Maghrib"
    PrayerPhase.ISHA -> "Isha"
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
        in 338..359, in 0..22 -> "north"
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
