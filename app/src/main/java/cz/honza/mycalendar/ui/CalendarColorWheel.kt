package cz.honza.mycalendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cz.honza.mycalendar.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private data class Hsv(val hue: Float, val saturation: Float, val value: Float) {
    fun rgb(): Int = Color.hsv(hue, saturation, value).toArgb() and 0xFFFFFF
}

private fun Int.toHsv(): Hsv {
    val channels = FloatArray(3)
    android.graphics.Color.colorToHSV(this or 0xFF000000.toInt(), channels)
    return Hsv(channels[0], channels[1], channels[2])
}

private fun Int.toHex(): String = String.format(Locale.ROOT, "%06X", this)

@Composable
internal fun CalendarColorWheel(selected: Int?, default: Int, onValidityChange: (Boolean) -> Unit, onChange: (Int?) -> Unit) {
    val rgb = selected ?: default
    val initial = remember(rgb) { rgb.toHsv() }
    var hue by rememberSaveable { mutableFloatStateOf(initial.hue) }
    var saturation by rememberSaveable { mutableFloatStateOf(initial.saturation) }
    var brightness by rememberSaveable { mutableFloatStateOf(initial.value) }
    var hex by rememberSaveable { mutableStateOf(rgb.toHex()) }
    val validHex = hex.length == 6
    val style = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme

    fun pick(hsv: Hsv) {
        hue = hsv.hue
        saturation = hsv.saturation
        brightness = hsv.value
        val custom = hsv.rgb()
        hex = custom.toHex()
        onValidityChange(true)
        onChange(custom)
    }

    val selectPosition by rememberUpdatedState<(Offset, Offset, Float) -> Unit> { position, center, radius ->
        val delta = position - center
        val angle = (atan2(delta.y, delta.x) * 180f / PI.toFloat() + 360f) % 360f
        pick(Hsv(angle, (delta.getDistance() / radius).coerceIn(0f, 1f), brightness))
    }
    val spectrum = remember(brightness) {
        (0..12).map { Color.hsv((it * 30f) % 360f, 1f, brightness) }
    }
    val wheelLabel = stringResource(R.string.calendar_color_wheel)
    val brightnessLabel = stringResource(R.string.calendar_color_brightness)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(
            Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp).size(240.dp)
                .semantics { contentDescription = wheelLabel }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(center.x, center.y)
                        if ((down.position - center).getDistance() > radius) return@awaitEachGesture
                        selectPosition(down.position, center, radius)
                        down.consume()
                        do {
                            val pointer = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (pointer.pressed) {
                                selectPosition(pointer.position, center, radius)
                                pointer.consume()
                            }
                        } while (pointer.pressed)
                    }
                }
        ) {
            val radius = size.minDimension / 2f
            drawCircle(Brush.sweepGradient(spectrum, center), radius)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.hsv(0f, 0f, brightness), Color.hsv(0f, 0f, brightness, alpha = 0f)),
                    center, radius
                ),
                radius
            )
            val angle = hue * PI.toFloat() / 180f
            val marker = center + Offset(cos(angle), sin(angle)) * (saturation * (radius - 5.dp.toPx()))
            drawCircle(colors.inverseOnSurface, 7.dp.toPx(), marker, style = Stroke(4.dp.toPx()))
            drawCircle(colors.inverseSurface, 7.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
        }
        Text(brightnessLabel, style = style.labelLarge)
        Slider(
            value = brightness,
            onValueChange = { pick(Hsv(hue, saturation, it)) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = brightnessLabel }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(44.dp)
                .background(Color(rgb or 0xFF000000.toInt()), MaterialTheme.shapes.medium)
                .border(1.dp, colors.outline, MaterialTheme.shapes.medium))
            OutlinedTextField(
                value = hex,
                onValueChange = { entered ->
                    val code = entered.removePrefix("#").uppercase(Locale.ROOT)
                    if (code.length <= 6 && code.all { it in "0123456789ABCDEF" }) {
                        hex = code
                        onValidityChange(code.length == 6)
                        if (code.length == 6) {
                            val value = code.toInt(16)
                            val hsv = value.toHsv()
                            hue = hsv.hue
                            saturation = hsv.saturation
                            brightness = hsv.value
                            onChange(value)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.calendar_color_hex)) },
                prefix = { Text("#") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                isError = !validHex,
                singleLine = true
            )
        }
        if (selected != null) {
            TextButton(onClick = {
                val hsv = default.toHsv()
                hue = hsv.hue
                saturation = hsv.saturation
                brightness = hsv.value
                hex = default.toHex()
                onValidityChange(true)
                onChange(null)
            }) { Text(stringResource(R.string.calendar_color_reset)) }
        }
    }
}
