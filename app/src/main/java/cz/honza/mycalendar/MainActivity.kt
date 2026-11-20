package cz.honza.mycalendar

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cz.honza.mycalendar.data.AppThemeMode
import cz.honza.mycalendar.data.CalendarRepository
import cz.honza.mycalendar.reminders.EventReminders
import cz.honza.mycalendar.ui.CalendarScreen
import cz.honza.mycalendar.ui.theme.MyCalendarTheme
import cz.honza.mycalendar.widget.CalendarWidget
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var openDay by mutableStateOf<LocalDate?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = CalendarRepository.get(this)
        if (savedInstanceState == null) receiveDay(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CalendarWidget.refresh(this@MainActivity, repository.data.value)
                EventReminders.refresh(this@MainActivity)
                while (true) {
                    repository.sync()
                    delay(12_000)
                }
            }
        }

        setContent {
            val theme by repository.themeMode.collectAsState()
            val dark = when (theme) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT, AppThemeMode.PINK_CLOUD -> false
                AppThemeMode.DARK -> true
            }
            MyCalendarTheme(darkTheme = dark, pinkCloud = theme == AppThemeMode.PINK_CLOUD) {
                val surface = MaterialTheme.colorScheme.surface.toArgb()
                LaunchedEffect(dark, surface) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(surface, surface) { dark }
                    )
                }
                val data by repository.data.collectAsState()
                CalendarScreen(data, repository, openDay, onDayOpened = { openDay = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveDay(intent)
    }

    private fun receiveDay(intent: Intent) {
        openDay = when (intent.action) {
            CalendarWidget.OPEN_TODAY -> LocalDate.now()
            EventReminders.OPEN_REMINDER -> runCatching {
                LocalDate.ofEpochDay(intent.getLongExtra(EventReminders.EXTRA_DATE, Long.MIN_VALUE))
            }.getOrNull()?.takeIf { it.year in 1..9999 }
            else -> null
        }
    }
}
