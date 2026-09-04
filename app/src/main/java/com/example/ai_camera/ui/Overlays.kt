package com.example.ai_camera.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val lineColor = Color.White.copy(alpha = 0.35f)
        val stroke = 1.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

@Composable
fun HistogramOverlay(bins: IntArray?, modifier: Modifier = Modifier) {
    if (bins == null) return
    Box(
        modifier = modifier
            .width(132.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val peak = bins.max().coerceAtLeast(1)
            val barWidth = size.width / bins.size
            bins.forEachIndexed { index, count ->
                val barHeight = (count.toFloat() / peak) * size.height
                drawRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = Offset(index * barWidth, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                )
            }
        }
    }
}

/** Artificial horizon: turns green when the device is within half a degree of level. */
@Composable
fun LevelOverlay(rollDegrees: Float, modifier: Modifier = Modifier) {
    val isLevel = abs(rollDegrees) < 0.5f
    Canvas(modifier = modifier.fillMaxSize()) {
        val color = if (isLevel) Color(0xFF4CD964) else Color.White.copy(alpha = 0.7f)
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val halfLength = size.width * 0.22f
        val radians = (-rollDegrees * Math.PI / 180.0).toFloat()
        val dx = halfLength * cos(radians)
        val dy = halfLength * sin(radians)
        drawLine(
            color = color,
            start = Offset(centerX - dx, centerY - dy),
            end = Offset(centerX + dx, centerY + dy),
            strokeWidth = 2.dp.toPx(),
        )
        // Fixed reference marks either side of the rotating horizon line.
        val gap = size.width * 0.28f
        val tick = size.width * 0.05f
        drawLine(color, Offset(centerX - gap - tick, centerY), Offset(centerX - gap, centerY), 2.dp.toPx())
        drawLine(color, Offset(centerX + gap, centerY), Offset(centerX + gap + tick, centerY), 2.dp.toPx())
    }
}

@Composable
fun FocusRing(position: Offset, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = 36.dp.toPx()
        drawCircle(
            color = Color(0xFFFFD60A),
            radius = radius,
            center = position,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )
    }
}

/** Device roll in degrees (0 = upright portrait), from the gravity/accelerometer sensor. */
@Composable
fun rememberDeviceRoll(): Float {
    val context = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                // Low-pass filtered so the horizon line does not jitter.
                val target = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                roll += (target - roll) * 0.15f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    return roll
}

@Composable
fun ShutterFlashOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
    )
}
