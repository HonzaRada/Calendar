package cz.honza.mycalendar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import cz.honza.mycalendar.data.CalendarColor
import cz.honza.mycalendar.data.CalendarGroup

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

@Composable
fun MyCalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pinkCloud: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        pinkCloud -> pinkCloudColorScheme()
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (pinkCloud) PinkCloudTypography else Typography,
        shapes = if (pinkCloud) PinkCloudShapes else MaterialTheme.shapes,
        content = content
    )
}

internal data class CalendarColors(val accent: Color, val container: Color, val onContainer: Color)

internal fun ColorScheme.forCalendar(group: CalendarGroup): CalendarColors {
    group.customColor?.let {
        val accent = Color(it or 0xFF000000.toInt())
        return CalendarColors(accent, accent.copy(alpha = 0.18f).compositeOver(surface), onSurface)
    }
    return when (group.color) {
        CalendarColor.PRIMARY -> CalendarColors(primary, primaryContainer, onPrimaryContainer)
        CalendarColor.SECONDARY -> CalendarColors(secondary, secondaryContainer, onSecondaryContainer)
        CalendarColor.TERTIARY -> CalendarColors(tertiary, tertiaryContainer, onTertiaryContainer)
        CalendarColor.ERROR -> CalendarColors(error, errorContainer, onErrorContainer)
    }
}
