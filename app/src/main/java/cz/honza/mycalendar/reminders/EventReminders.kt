package cz.honza.mycalendar.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import cz.honza.mycalendar.MainActivity
import cz.honza.mycalendar.R
import cz.honza.mycalendar.data.CalendarEvent
import cz.honza.mycalendar.data.CalendarRepository
import cz.honza.mycalendar.data.clockText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            EventReminders.ALARM -> EventReminders.refresh(context, alarmDelivery = true)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> EventReminders.refresh(context)
        }
    }
}

internal object EventReminders {
    const val OPEN_REMINDER = "cz.honza.mycalendar.OPEN_REMINDER"
    const val ALARM = "cz.honza.mycalendar.DELIVER_REMINDERS"
    const val EXTRA_DATE = "event_date"
    private const val CHANNEL = "event_reminders"

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    @Synchronized
    fun refresh(context: Context, alarmDelivery: Boolean = false) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java).setAction(ALARM), PendingIntent.FLAG_IMMUTABLE
        )
        if (!canNotify(context)) {
            alarms.cancel(pending)
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT
        ))
        val history = context.getSharedPreferences("calendar_reminder_history", Context.MODE_PRIVATE)
        val now = Instant.now()
        val cutoff = now.minusSeconds(4 * 86_400L).toEpochMilli()
        val delivered = history.all.filterValues { it is Long && it >= cutoff }
        val sent = mutableMapOf<String, Long>()
        val events = CalendarRepository.get(context).data.value.events
        var next: Instant? = null

        for (event in events) {
            val zone = event.zone?.let(ZoneId::of) ?: ZoneId.systemDefault()
            val firstDay = now.atZone(zone).toLocalDate().minusDays(1)
            for (lead in event.reminders) {
                var day = event.firstOccurrenceOnOrAfter(firstDay)
                while (day != null) {
                    val starts = day.atStartOfDay().plusMinutes(event.start.toLong()).atZone(zone).toInstant()
                    val ends = day.atStartOfDay().plusMinutes(event.end.toLong()).atZone(zone).toInstant()
                    val due = starts.minusSeconds(lead * 60L)
                    val tag = "${event.id}:$day:${event.start}:$lead"
                    val expires = maxOf(ends, starts.plusSeconds(60))
                    val relevantUntil = if (alarmDelivery) expires.plusSeconds(3600) else expires
                    if (now < relevantUntil && tag !in delivered) {
                        if (due > now) {
                            next = next?.let { minOf(it, due) } ?: due
                            break
                        }
                        // Late sync can still remind the device while the occurrence is relevant.
                        if (showNotification(context, manager, event, day, tag)) sent[tag] = now.toEpochMilli()
                    }
                    day = event.firstOccurrenceOnOrAfter(day.plusDays(1))
                }
            }
        }

        history.edit {
            for ((tag, time) in history.all) if (time is Long && time < cutoff) remove(tag)
            for ((tag, time) in sent) putLong(tag, time)
        }
        if (next == null) {
            alarms.cancel(pending)
        } else {
            val triggerAt = next.toEpochMilli()
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                try {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    return
                } catch (_: SecurityException) {
                    // Exact access can be revoked between checking and scheduling.
                }
            }
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun showNotification(
        context: Context,
        manager: NotificationManager,
        event: CalendarEvent,
        day: LocalDate,
        tag: String
    ): Boolean {
        val locale = context.resources.configuration.locales[0]
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day)
        val open = Intent(context, MainActivity::class.java).setAction(OPEN_REMINDER)
            .setData(Uri.Builder().scheme("mycalendar").authority("reminder").appendPath(tag).build())
            .putExtra(EXTRA_DATE, day.toEpochDay())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val intent = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_calendar_notification)
            .setContentTitle(event.displayTitle)
            .setContentText(context.getString(R.string.reminder_notification_time, date, clockText(event.start)))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        return try {
            manager.notify(tag, 0, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
