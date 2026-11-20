package cz.honza.mycalendar.data

import cz.honza.mycalendar.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import org.json.JSONObject

internal enum class CalendarColor(val label: Int) {
    PRIMARY(R.string.color_primary),
    SECONDARY(R.string.color_secondary),
    TERTIARY(R.string.color_tertiary),
    ERROR(R.string.color_error)
}

internal data class CalendarGroup(
    val id: String,
    val name: String?,
    val color: CalendarColor,
    val defaultName: Int? = null
)

internal val defaultGroups = listOf(
    CalendarGroup("work", null, CalendarColor.PRIMARY, R.string.calendar_work),
    CalendarGroup("school", null, CalendarColor.TERTIARY, R.string.calendar_school),
    CalendarGroup("other", null, CalendarColor.SECONDARY, R.string.calendar_other)
)

internal val eventStickers = listOf("🐱", "🐻", "🌷", "🌈", "✨", "🍰", "🌿", "🎈")

internal data class CalendarEvent(
    val id: String,
    val date: LocalDate,
    val start: Int,
    val end: Int,
    val title: String,
    val groupId: String,
    val sticker: String = ""
) {
    val displayTitle: String
        get() = if (sticker.isEmpty()) title else "$sticker $title"
}

internal data class CalendarData(
    val notes: Map<LocalDate, String> = emptyMap(),
    val groups: List<CalendarGroup> = defaultGroups,
    val events: List<CalendarEvent> = emptyList()
) {
    fun group(id: String): CalendarGroup = groups.firstOrNull { it.id == id } ?: groups.first { it.id == "other" }
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

internal fun eventFromJson(id: String, value: JSONObject): CalendarEvent {
    val date = LocalDate.parse(value.getString("date"))
    val start = value.getInt("start")
    val end = value.getInt("end")
    require(start in 0..1439 && end in (start + 1)..1440)
    return CalendarEvent(id, date, start, end, value.getString("title"), value.getString("group"), value.optString("sticker"))
}
