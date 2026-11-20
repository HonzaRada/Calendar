package cz.honza.mycalendar.data

import cz.honza.mycalendar.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray

internal enum class CalendarColor { PRIMARY, SECONDARY, TERTIARY, ERROR }

internal enum class AppThemeMode(val label: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    PINK_CLOUD(R.string.theme_pink_cloud)
}

internal data class CalendarGroup(
    val id: String,
    val name: String?,
    val color: CalendarColor,
    val defaultName: Int? = null,
    val customColor: Int? = null
)

internal val defaultGroups = listOf(
    CalendarGroup("work", null, CalendarColor.PRIMARY, R.string.calendar_work),
    CalendarGroup("school", null, CalendarColor.TERTIARY, R.string.calendar_school),
    CalendarGroup("other", null, CalendarColor.SECONDARY, R.string.calendar_other)
)

internal val eventStickers = listOf("🐱", "🐻", "🌷", "🌈", "✨", "🍰", "🌿", "🎈")

internal enum class RepeatRule(val label: Int) {
    NONE(R.string.repeat_none), DAILY(R.string.repeat_daily), WEEKLY(R.string.repeat_weekly),
    MONTHLY(R.string.repeat_monthly), YEARLY(R.string.repeat_yearly)
}

internal val reminderOffsets = listOf(0, 5, 10, 15, 30, 60, 1440)

internal data class CalendarEvent(
    val id: String,
    val date: LocalDate,
    val start: Int,
    val end: Int,
    val title: String,
    val groupId: String,
    val sticker: String = "",
    val repeat: RepeatRule = RepeatRule.NONE,
    val reminders: List<Int> = emptyList(),
    val zone: String? = null
) {
    val displayTitle: String
        get() = if (sticker.isEmpty()) title else "$sticker $title"

    fun firstOccurrenceOnOrAfter(from: LocalDate): LocalDate? {
        val day = maxOf(date, from)
        return when (repeat) {
            RepeatRule.NONE -> date.takeIf { it >= from }
            RepeatRule.DAILY -> day
            RepeatRule.WEEKLY -> day.plusDays(Math.floorMod(date.dayOfWeek.value - day.dayOfWeek.value, 7).toLong())
            RepeatRule.MONTHLY -> {
                val dayOfMonth = date.dayOfMonth
                var month = YearMonth.from(day)
                while (!month.isValidDay(dayOfMonth) || month.atDay(dayOfMonth) < day) month = month.plusMonths(1)
                month.atDay(dayOfMonth)
            }
            RepeatRule.YEARLY -> {
                val monthDay = MonthDay.from(date)
                var year = day.year
                while (!monthDay.isValidYear(year) || monthDay.atYear(year) < day) year++
                monthDay.atYear(year)
            }
        }
    }
}

internal data class CalendarData(
    val notes: Map<LocalDate, String> = emptyMap(),
    val groups: List<CalendarGroup> = defaultGroups,
    val events: List<CalendarEvent> = emptyList()
) {
    fun group(id: String): CalendarGroup = groups.firstOrNull { it.id == id } ?: groups.first { it.id == "other" }

    fun eventsOn(day: LocalDate): List<CalendarEvent> = events.asSequence()
        .filter { it.firstOccurrenceOnOrAfter(day) == day }
        .map { if (it.date == day) it else it.copy(date = day) }
        .sortedWith(compareBy(CalendarEvent::start, CalendarEvent::id))
        .toList()
}

internal fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal fun clockText(minutes: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

internal fun parseClock(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2 || parts[0].length !in 1..2 || parts[1].length != 2) return null
    if (parts.any { part -> part.any { !it.isDigit() } }) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59 || hour == 24 && minute == 0) hour * 60 + minute else null
}

internal fun CalendarEvent.toJson(): JSONObject = JSONObject()
    .put("date", date.toString()).put("start", start).put("end", end)
    .put("title", title).put("group", groupId).put("sticker", sticker)
    .put("repeat", repeat.name).put("reminders", JSONArray(reminders))
    .put("zone", zone ?: JSONObject.NULL)

internal fun eventFromJson(id: String, value: JSONObject): CalendarEvent {
    val date = LocalDate.parse(value.getString("date"))
    val start = value.getInt("start")
    val end = value.getInt("end")
    require(start in 0..1439 && end in (start + 1)..1440)
    val repeat = RepeatRule.entries.firstOrNull { it.name == value.optString("repeat") } ?: RepeatRule.NONE
    val reminders = value.optJSONArray("reminders")?.let { array ->
        List(array.length()) { array.optInt(it, -1) }.filter { it in reminderOffsets }.distinct().sorted()
    }.orEmpty()
    val zone = value.optString("zone").takeIf { runCatching { ZoneId.of(it) }.isSuccess }
    return CalendarEvent(id, date, start, end, value.getString("title"), value.getString("group"),
        value.optString("sticker"), repeat, reminders, zone)
}
