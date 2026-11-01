package dev.draftingroom5.wear

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.sin

/** A short confirmation shown after tapping the stand complication. */
class StandResetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetStandTimer(this)
        @Suppress("DEPRECATION")
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(VibrationEffect.createOneShot(30, 80))
        setContent { StandStretch { finish() } }
    }
}

@Composable
private fun StandStretch(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1100))
        delay(200)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF05090B)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val scale = size.minDimension / 200f
            val mint = Color(0xFF69E6CA)
            val phase = progress.value
            val step = sin(phase * 4f * Math.PI).toFloat() * 5f * scale
            val reach = sin((phase.coerceIn(0f, .7f) / .7f) * Math.PI).toFloat().coerceAtLeast(0f)
            val person = Offset(center.x + step, center.y - 4f * scale)
            val line = 4f * scale
            val radius = 62f * scale
            drawArc(Color(0xFF243734), -90f, 360f, false,
                topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(6f * scale, cap = StrokeCap.Round))
            drawArc(mint, -90f, 360f * phase, false,
                topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(6f * scale, cap = StrokeCap.Round))
            drawCircle(mint, 8f * scale, Offset(person.x, person.y - 30f * scale))
            drawLine(mint, Offset(person.x, person.y - 19f * scale), Offset(person.x, person.y + 20f * scale), line, StrokeCap.Round)
            val armY = person.y - (9f + 18f * reach) * scale
            drawLine(mint, Offset(person.x, person.y - 12f * scale), Offset(person.x - 21f * scale, armY), line, StrokeCap.Round)
            drawLine(mint, Offset(person.x, person.y - 12f * scale), Offset(person.x + 21f * scale, armY), line, StrokeCap.Round)
            drawLine(mint, Offset(person.x, person.y + 20f * scale), Offset(person.x - 15f * scale, person.y + 42f * scale), line, StrokeCap.Round)
            drawLine(mint, Offset(person.x, person.y + 20f * scale), Offset(person.x + 15f * scale, person.y + 42f * scale), line, StrokeCap.Round)
        }
    }
}
