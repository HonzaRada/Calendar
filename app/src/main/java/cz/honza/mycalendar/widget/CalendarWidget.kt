package cz.honza.mycalendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import cz.honza.mycalendar.MainActivity
import cz.honza.mycalendar.R
import cz.honza.mycalendar.data.CalendarData
import cz.honza.mycalendar.data.CalendarRepository
import cz.honza.mycalendar.data.clockText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class CalendarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val data = CalendarRepository.get(context).data.value
        manager.updateAppWidget(ids, views(context, data))
    }

    companion object {
        const val OPEN_TODAY = "cz.honza.mycalendar.OPEN_TODAY"

        fun refresh(context: Context, data: CalendarData) {
            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, CalendarWidget::class.java)
            if (manager.getAppWidgetIds(provider).isNotEmpty()) {
                manager.updateAppWidget(provider, views(context, data))
            }
        }

        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, CalendarWidget::class.java), null, null)
        }

        private fun views(context: Context, data: CalendarData): RemoteViews {
            val date = LocalDate.now()
            val locale = context.resources.configuration.locales[0]
            val dateText = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
            val events = data.eventsOn(date)
            val agenda = if (events.isEmpty()) context.getString(R.string.widget_no_events) else {
                events.take(3).joinToString("\n") { "${clockText(it.start)}  ${it.displayTitle}" }
            }
            val note = data.notes[date].orEmpty().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            val openCalendar = Intent(context, MainActivity::class.java).setAction(OPEN_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, 0, openCalendar, PendingIntent.FLAG_IMMUTABLE)

            return RemoteViews(context.packageName, R.layout.calendar_widget).apply {
                setTextViewText(R.id.widget_date, context.getString(R.string.widget_date, dateText))
                setTextViewText(R.id.widget_agenda, agenda)
                setTextViewText(R.id.widget_note, note)
                setViewVisibility(R.id.widget_note, if (note.isBlank()) View.GONE else View.VISIBLE)
                setOnClickPendingIntent(android.R.id.background, pending)
            }
        }
    }
}
