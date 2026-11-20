package cz.honza.mycalendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cz.honza.mycalendar.data.CalendarRepository
import cz.honza.mycalendar.ui.CalendarScreen
import cz.honza.mycalendar.ui.theme.MyCalendarTheme
import cz.honza.mycalendar.widget.CalendarWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var widgetRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = CalendarRepository.get(this)
        if (savedInstanceState == null && intent.action == CalendarWidget.OPEN_TODAY) widgetRequest++

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CalendarWidget.refresh(this@MainActivity, repository.data.value)
                while (true) {
                    repository.sync()
                    delay(12_000)
                }
            }
        }

        setContent {
            MyCalendarTheme {
                val data by repository.data.collectAsState()
                CalendarScreen(data, repository, widgetRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == CalendarWidget.OPEN_TODAY) widgetRequest++
    }
}
