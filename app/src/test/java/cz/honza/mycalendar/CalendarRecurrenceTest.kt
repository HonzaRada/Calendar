package cz.honza.mycalendar

import cz.honza.mycalendar.data.CalendarData
import cz.honza.mycalendar.data.CalendarEvent
import cz.honza.mycalendar.data.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalendarRecurrenceTest {
    @Test
    fun weeklySeriesUsesTheOriginalWeekdayAndId() {
        val original = event("2026-11-20", RepeatRule.WEEKLY)
        val calendar = CalendarData(events = listOf(original))
        assertEquals(emptyList<CalendarEvent>(), calendar.eventsOn(day("2026-11-13")))
        assertEquals(emptyList<CalendarEvent>(), calendar.eventsOn(day("2026-11-26")))
        assertEquals(listOf(original.copy(date = day("2026-11-27"))), calendar.eventsOn(day("2026-11-27")))
        assertEquals(day("2026-11-20"), original.date)
    }

    @Test
    fun monthlySeriesSkipsMonthsWithoutTheOriginalDay() {
        val event = event("2026-01-31", RepeatRule.MONTHLY)
        assertEquals(day("2026-03-31"), event.firstOccurrenceOnOrAfter(day("2026-02-01")))
        assertEquals(day("2026-05-31"), event.firstOccurrenceOnOrAfter(day("2026-04-01")))
    }

    @Test
    fun yearlyLeapDayWaitsUntilTheNextLeapYear() {
        val event = event("2096-02-29", RepeatRule.YEARLY)
        assertEquals(day("2104-02-29"), event.firstOccurrenceOnOrAfter(day("2096-03-01")))
    }

    @Test
    fun dailyAndNonRepeatingEventsRespectTheStartDate() {
        assertNull(event("2026-11-20", RepeatRule.NONE).firstOccurrenceOnOrAfter(day("2026-11-21")))
        val daily = event("2026-11-20", RepeatRule.DAILY)
        assertEquals(day("2026-11-20"), daily.firstOccurrenceOnOrAfter(day("2026-11-19")))
        assertEquals(day("2026-11-21"), daily.firstOccurrenceOnOrAfter(day("2026-11-21")))
    }

    private fun day(value: String) = LocalDate.parse(value)

    private fun event(date: String, repeat: RepeatRule) =
        CalendarEvent("event", day(date), 540, 600, "Calendar test", "other", repeat = repeat)
}
