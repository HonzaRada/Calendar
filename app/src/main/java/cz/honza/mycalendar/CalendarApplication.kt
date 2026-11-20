package cz.honza.mycalendar

import android.app.Application
import cz.honza.mycalendar.data.CalendarRepository

internal class CalendarApplication : Application() {
    val calendarRepository by lazy { CalendarRepository(this) }
}
